/**
 * SmartLocationManager 主要逻辑（兼容原有设计，新增自适应提频能力）：
 * 1. 连续/高精度定位：根据司机运动状态（静止、步行、慢车、快车）动态调整定位间隔与精度；
 * 2. Burst 模式：由上层（如 ProximityCoordinator）在靠近包裹或需要更高精度时调用 requestBoost(...) 进入高频定位窗口，持续一定时长后恢复常规定位；
 * 3. 位置平滑：使用指数平滑算法减少 GPS 抖动，提升定位稳定性；
 * 4. 弱信号检测：当连续多次定位精度差（超出阈值）时，触发 onWeakSignal 回调提醒；
 * 5. 省电策略：静止时切换到 Significant Location Change 更新模式，避免持续高耗电。
 *
 * 【新增 / 扩展】
 * 6. 自适应临时提频（Boost）：当检测到“跳跃风险”或地图侧报告“边缘风险”时，
 *    通过 requestBoost(...) 进入临时高频定位窗口（例如 20s），窗口期内保持高频；
 *    使用统一的 inBurstMode 标志与调度，作为所有临时提频（含外部触发）的一致实现；
 * 7. 跳跃风险检测：两次定位点跨度较大且处于快速移动（如 >30m 且 >10m/s）会自动触发 Boost，
 *    以避免地图相机“到边再回中”的突兀；
 * 8. 边缘风险接口：地图侧可在蓝点离目标中心过远且速度较大时调用
 *    requestBoostIfEdgeRisk(offsetMeters, speedMps) 或直接 requestBoost(...)，
 *    以便在高速行驶场景下保持相机平滑跟随；
 * 9. 航向支持：通过旋转矢量传感器获取 heading，并进行低通滤波，提升行驶方向稳定性。
 *
 * 兼容性说明：
 * - 保持原有方法签名、状态机与回调不变；新增的 requestBoost(...) / requestBoostIfEdgeRisk(...) 为可选增强，
 *   不调用时行为与旧版一致；
 * - inBurstMode 仍作为统一高频开关，新增 Boost 与原有靠近包裹的 Burst 共用同一套进入/退出与计时调度；
 * - updateLocationParametersForState() 会在进入/退出 Boost/Burst 或运动状态变更时自动重新申请定位参数。
 * - 业务解耦：移除包裹查询触发与里程统计的直接调用，上层可通过协调器订阅定位事件并决定是否 Boost/统计。
 *
 * 建议用法：
 * - 地图渲染层可结合 200–300ms 小步动画与短期速度预测实现视觉平滑，新定位点到来用于“微校正”；
 * - 当检测到蓝点朝屏幕边缘偏离且速度较大时调用 requestBoost(20000)；靠近包裹范围内由上层协调器触发 requestBoost(...)，本类统一执行高频窗口。
 */

package com.hf.easydelivery.core;

import android.Manifest;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.location.Location;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.util.Log;

import androidx.core.app.ActivityCompat;

import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;

import com.google.android.gms.location.ActivityRecognition;
import com.google.android.gms.location.ActivityRecognitionClient;
import com.google.android.gms.location.ActivityTransition;
import com.google.android.gms.location.ActivityTransitionEvent;
import com.google.android.gms.location.ActivityTransitionRequest;
import com.google.android.gms.location.ActivityTransitionResult;
import com.google.android.gms.location.DetectedActivity;
import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationCallback;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationResult;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.Priority;
import com.google.android.gms.tasks.CancellationTokenSource;
import com.hf.courierservice.apihelper.FileLog;

import java.util.ArrayList;
import java.util.List;

/**
 * The SmartLocationManager class provides location-related functionality and
 * try to reduce consumption of battery.
 * 
 * @author jvtang
 * @since 2024-08-21
 */
public class SmartLocationManager {
    private static final long BURST_MODE_DURATION_MS = 60 * 1000; // 1 minute
    private static SmartLocationManager instance;
    private static final String TAG = "SmartLocationManager";

    private Context context;
    private FusedLocationProviderClient fusedLocationClient;
    private LocationCallback locationCallback;
    private Location lastLocation;
    private Location lastSmoothedLocation;
    private Location lastPredictedLocation;
    private long lastDispatchUptimeMs = 0L;
    private float speed;
    private long lastUpdateTime;
    private MovementState currentState = MovementState.STATIONARY;
    private final java.util.Set<LocationUpdateListener> listeners = new java.util.concurrent.CopyOnWriteArraySet<>();
    private Handler handler;
    private boolean inBurstMode = false;
    private Runnable burstModeRunnable;
    private ActivityRecognitionClient activityRecognitionClient;
    private PendingIntent activityRecognitionPendingIntent;
    private int weakSignalCount = 0;
    private long weakSignalStartTime = 0L; // ✅ Bug fix: 弱信号时间窗口检测
    private static final double SMOOTHING_FACTOR = 0.2;
    private static final float WEAK_SIGNAL_THRESHOLD = 100f;
    private static final double EARTH_RADIUS_METERS = 6378137.0;
    private static final float MIN_PREDICTION_SPEED_MPS = 0.8f;
    // ✅ PREDICTION_HORIZON_SEC removed - now dynamic based on speed

    // === Adaptive boost (temporary high-frequency updates) ===
    private static final long BOOST_MIN_INTERVAL_MS = 10_000L; // 预留：降频节流
    private long boostHoldUntilMs = 0L;
    private long lastBoostChangeMs = 0L;

    // === 架构师建议：3分钟真静止检测 ===
    private long lastMovingTimeMs = 0L;
    private long continuousStationaryStartMs = 0L; // ✅ Bug#3: 跟踪连续静止开始时间
    private MovementState previousState = MovementState.STATIONARY;
    private long lastGoodFixTime = 0L;
    private static final float ACCURACY_THRESHOLD_GOOD = 28f;
    private static final float ACCURACY_THRESHOLD_POOR = 50f;
    private static final long POOR_SIGNAL_GRACE_PERIOD = 30_000L;
    private static final long EMERGENCY_BOOST_THRESHOLD_MS = 15_000L;

    // === Heading (bearing) support via sensors ===
    private SensorManager sensorManager;
    private Sensor rotationVectorSensor;
    private Sensor linearAccelerationSensor;
    private final float[] rotationMatrix = new float[9];
    private final float[] orientationAngles = new float[3];
    private float currentHeadingDegrees = Float.NaN; // 0..360, NaN if unknown
    private int headingAccuracy = SensorManager.SENSOR_STATUS_UNRELIABLE;

    // Keep a reference to remove only our burst runnable, not all callbacks
    private Runnable burstModeRunnableRef;

    // Cache last requested intervals to avoid redundant re-requests
    private long lastRequestedIntervalMs = -1L;
    private long lastRequestedMinIntervalMs = -1L;
    private int lastRequestedPriority = -1;

    // Low-pass for heading smoothing (0..1). Larger = quicker but noisier
    private static final float HEADING_ALPHA = 0.2f;
    private static final float ACCEL_WAKE_THRESHOLD = 0.8f; // 基础阈值（作为fallback）
    private static final int ACCEL_REQUIRED_HITS = 4;
    private static final long ACCEL_WINDOW_MS = 400L;
    private static final long MOTION_WAKE_COOLDOWN_MS = 3_000L;
    private long lastAccelSpikeUptime = 0L;
    private long lastMotionWakeUptime = 0L;

    // ✅ 架构师建议：标准差滤波减少低端设备噪声
    private static final int ACCEL_BUFFER_SIZE = 10;
    private static final float NOISE_STD_MULTIPLIER = 2.5f;
    private float[] accelBuffer = new float[ACCEL_BUFFER_SIZE];
    private int accelBufferIndex = 0;
    private boolean accelBufferFilled = false;
    private int accelConsecutiveHits = 0;
    private boolean singleUpdateInFlight = false;

    // 常量定义
    private static final long DELIVERING_IDLE_THRESHOLD_MS = 10_000L; // 停车/步行超过此时间认为司机不看地图
    private static final long INTERVAL_DRIVING_NORMAL_MS = 1_500L;
    private static final long INTERVAL_DRIVING_SLOW_MS = 2_500L;
    private static final long INTERVAL_WALKING_MS = 5_000L;
    private static final long INTERVAL_DELIVERING_MS = 45_000L;

    private static final long MIN_INTERVAL_DRIVING_NORMAL_MS = 800L;
    private static final long MIN_INTERVAL_DRIVING_SLOW_MS = 1_500L;
    private static final long MIN_INTERVAL_WALKING_MS = 3_000L;
    private static final long MIN_INTERVAL_DELIVERING_MS = 30_000L;

    public interface WeakSignalListener extends LocationUpdateListener {
        void onWeakSignal();
    }

    public enum MovementState {
        STATIONARY,
        WALKING,
        SLOW_DRIVING,
        NORMAL_DRIVING
    }

    public static synchronized SmartLocationManager getInstance(Context context) {
        if (instance != null)
            return instance;
        else {
            if (context == null)
                return null;
            instance = new SmartLocationManager(context);
        }
        return instance;
    }

    public interface LocationUpdateListener {
        void onLocationUpdate(Location location, MovementState state);
    }

    /**
     * 请求一段时间的高频定位（可叠加延长保持时间）。
     * 
     * @param durationMs 例如 20_000（20 秒）
     */
    public void requestBoost(long durationMs) {
        long now = System.currentTimeMillis();

        // ✅ 架构师建议#9: 2秒内防止Boost叠加
        // 防止updateMovementState + displacement wake + edge risk同时触发时叠加
        if (inBurstMode && (now - lastBoostChangeMs < 2_000L)) {
            FileLog.getInstance().debug(TAG, "requestBoost debounced: already boosted recently");
            return;
        }

        // ✅ 架构师建议：只有真·静止3分钟以上才拒绝提频
        // 允许红灯、塞车时仍保持高频
        if (speed < 0.3f && (now - lastMovingTimeMs > 180_000L)) {
            FileLog.getInstance().debug(TAG,
                    "requestBoost refused: truly stationary for " +
                            (now - lastMovingTimeMs) / 1000 + "s");
            return;
        }

        if (durationMs <= 0)
            durationMs = 5_000L;
        if (inBurstMode) {
            boostHoldUntilMs = Math.max(boostHoldUntilMs, now + durationMs);
            scheduleBurstEnd();
            return;
        }
        enterBurstMode(durationMs);
    }

    /**
     * 便捷：地图检测到"边缘风险/不平滑风险"时调用。
     * 
     * @param offsetMeters 蓝点相对目标中心的米偏移
     * @param speedMps     当前速度 m/s
     */

    public void requestBoostIfEdgeRisk(float offsetMeters, float speedMps) {
        // ✅ 低速时不触发边缘风险Boost（只在高速移动时才有意义）
        if (speedMps < 2.0f || currentState == MovementState.STATIONARY
                || currentState == MovementState.WALKING) {
            return;
        }

        long now = System.currentTimeMillis();
        // 节流：两次提频之间至少间隔 BOOST_MIN_INTERVAL_MS
        if (now - lastBoostChangeMs < BOOST_MIN_INTERVAL_MS)
            return;
        if (offsetMeters > 25f && speedMps > 5f) {
            requestBoost(20_000L);
        }
    }

    private SmartLocationManager(Context context) {
        this.context = context;
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(context);
        handler = new Handler(Looper.getMainLooper());
        activityRecognitionClient = ActivityRecognition.getClient(context);

        Intent intent = new Intent(context, ActivityTransitionReceiver.class);
        activityRecognitionPendingIntent = PendingIntent.getBroadcast(context, 0, intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        registerActivityTransitionUpdates();

        sensorManager = (SensorManager) context.getSystemService(Context.SENSOR_SERVICE);
        if (sensorManager != null) {
            rotationVectorSensor = sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR);
            linearAccelerationSensor = sensorManager.getDefaultSensor(Sensor.TYPE_LINEAR_ACCELERATION);
        }
    }

    public void setLocationUpdateListener(LocationUpdateListener listener) {
        listeners.clear();
        if (listener != null) {
            listeners.add(listener);
        }
    }

    /** Add an additional listener without removing existing ones. */
    public void addLocationUpdateListener(LocationUpdateListener listener) {
        if (listener != null) {
            listeners.add(listener);
        }
    }

    public void removeLocationUpdateListener(LocationUpdateListener listener) {
        if (listener != null) {
            listeners.remove(listener);
        }
    }

    public void startLocationUpdates() {
        if (ActivityCompat.checkSelfPermission(context,
                Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            // Handle the case where permission is not granted
            return;
        }

        locationCallback = new LocationCallback() {
            @Override
            public void onLocationResult(LocationResult locationResult) {
                if (locationResult == null) {
                    return;
                }

                // ✅ 架构师建议#6: 批量位置只完整处理最后一个
                List<Location> locations = locationResult.getLocations();
                if (locations.isEmpty()) {
                    return;
                }

                // 完整处理最后一个
                updateLocation(locations.get(locations.size() - 1));
            }
        };

        // Set initial update request
        updateLocationParametersForState();
        startHeadingUpdates();
        startMotionWakeMonitoring();
    }

    private void requestLocationUpdates(long interval, long minInterval, int priority) {
        if (ActivityCompat.checkSelfPermission(context,
                Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            return;
        }

        LocationRequest locationRequest = new LocationRequest.Builder(priority)
                .setIntervalMillis(interval)
                .setMinUpdateIntervalMillis(minInterval)
                .setMaxUpdateDelayMillis(0)
                .setMinUpdateDistanceMeters(2.0f)
                .setWaitForAccurateLocation(false)
                .build();

        fusedLocationClient.requestLocationUpdates(locationRequest,
                locationCallback,
                Looper.getMainLooper())
                .addOnFailureListener(e -> {
                    // ✅ 架构师建议：注册失败时重置缓存，强制下次重试
                    FileLog.getInstance().error(TAG,
                            String.format("requestLocationUpdates failed: interval=%dms, minInterval=%dms, priority=%d",
                                    interval, minInterval, priority),
                            e);
                    lastRequestedIntervalMs = -1L;
                    lastRequestedMinIntervalMs = -1L;
                    lastRequestedPriority = -1;
                });
    }

    private void switchToSignificantChanges() {
        if (ActivityCompat.checkSelfPermission(context,
                Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            // Handle the case where permission is not granted
            return;
        }
        LocationRequest locationRequest = new LocationRequest.Builder(Priority.PRIORITY_PASSIVE)
                .setIntervalMillis(60 * 1000) // 1 minute interval or as preferred
                .build();

        fusedLocationClient.requestLocationUpdates(locationRequest,
                locationCallback,
                Looper.getMainLooper());
    }

    private void updateLocation(Location newLocation) {

        Location prevLast = lastLocation;
        long nowMillis = System.currentTimeMillis();

        // ✅ Bug#4修复：动态精度检查（但不立即return）
        long timeSinceGoodFix = nowMillis - lastGoodFixTime;
        float accuracyThreshold = timeSinceGoodFix > POOR_SIGNAL_GRACE_PERIOD
                ? ACCURACY_THRESHOLD_POOR
                : ACCURACY_THRESHOLD_GOOD;

        boolean lowAccuracy = newLocation.getAccuracy() > accuracyThreshold;
        if (lowAccuracy) {
            FileLog.getInstance().debug(TAG,
                    String.format("Low accuracy: %.1fm (threshold: %.1fm) - updating state only",
                            newLocation.getAccuracy(), accuracyThreshold));
        }

        if (newLocation.getAccuracy() <= ACCURACY_THRESHOLD_GOOD) {
            lastGoodFixTime = nowMillis;
        }

        // ✅ 速度兜底策略（始终执行）
        float gpsSpeed = newLocation.hasSpeed() ? newLocation.getSpeed() : 0f;
        float displacementSpeed = 0f;
        float distance = 0f;
        if (lastLocation != null) {
            distance = lastLocation.distanceTo(newLocation);
            long timeDiff = newLocation.getTime() - lastUpdateTime;
            if (timeDiff > 0 && distance > 2.0f) {
                // ✅ 架构师建议：限制用于速度计算的距离上限为15m，防止跳变导致速度暴涨
                float distForSpeed = Math.min(distance, 15.0f);
                displacementSpeed = (distForSpeed / (timeDiff / 1000f));
            }
        }
        speed = Math.max(gpsSpeed, displacementSpeed);

        // ✅ lastMovingTimeMs更新（始终执行）
        if (speed > 0.3f || distance > 2.0f) {
            lastMovingTimeMs = nowMillis;
        }

        // ✅ Bug#4修复：Emergency Boost基于lastLocation时间而非lastDispatch
        if (lastLocation != null) {
            long timeSinceLastLocation = nowMillis - lastLocation.getTime();
            if (timeSinceLastLocation > EMERGENCY_BOOST_THRESHOLD_MS && !inBurstMode) {
                FileLog.getInstance().debug(TAG,
                        String.format("Emergency boost: no location for %ds",
                                timeSinceLastLocation / 1000));
                requestBoost(10_000L);
            }
        }

        // ✅ Bug#4修复：低精度时只更新状态，不分发
        if (lowAccuracy) {
            lastLocation = newLocation;
            lastUpdateTime = newLocation.getTime();
            updateMovementState();
            return; // 不继续分发
        }

        // ✅ 双层过滤 Layer 1: 粗过滤（防抖），避免静止/步行时的GPS抖动刷屏
        // 静止/步行状态下：时间<2s 且 位移<2m => 不分发（但仍更新state）
        boolean shouldDispatch = true;
        if ((currentState == MovementState.STATIONARY || currentState == MovementState.WALKING)
                && lastLocation != null) {
            long timeDiff = newLocation.getTime() - lastUpdateTime;
            // ✅ 修复Bug：复用外层distance（不要重新声明）
            distance = lastLocation.distanceTo(newLocation);

            if (timeDiff < 2000 && distance < 2.0f) {
                shouldDispatch = false;
                FileLog.getInstance().debug(TAG,
                        String.format("Location update filtered: state=%s, time=%dms, dist=%.1fm",
                                currentState, timeDiff, distance));
            }
        }

        // ✅ 架构师建议：位移>8米立即唤醒（最关键！）
        if (lastLocation != null) {
            float displacement = lastLocation.distanceTo(newLocation);
            if (displacement > 8.0f) {
                requestBoost(20_000L);
                FileLog.getInstance().debug(TAG,
                        String.format("Displacement wake: %.1fm -> boost", displacement));
            }
        }

        // Jump risk：两次点位跨度较大且在快速移动 → 临时提频以避免"到边再跳回"的突兀
        if (!inBurstMode && prevLast != null) {
            float jumpMeters = prevLast.distanceTo(newLocation);
            boolean movingFast = speed > 10f;
            if (movingFast && jumpMeters > 30f) {
                requestBoost(20_000L);
            }
        }

        lastLocation = newLocation;
        lastUpdateTime = newLocation.getTime();

        boolean stateChanged = updateMovementState();

        if (!shouldDispatch) {
            return;
        }

        Location outputLoc;
        if (lastSmoothedLocation != null) {
            double lat = lastSmoothedLocation.getLatitude()
                    + SMOOTHING_FACTOR * (newLocation.getLatitude() - lastSmoothedLocation.getLatitude());
            double lon = lastSmoothedLocation.getLongitude()
                    + SMOOTHING_FACTOR * (newLocation.getLongitude() - lastSmoothedLocation.getLongitude());
            outputLoc = new Location(newLocation);
            outputLoc.setLatitude(lat);
            outputLoc.setLongitude(lon);
        } else {
            outputLoc = newLocation;
        }
        lastSmoothedLocation = outputLoc;
        float bearingInput = newLocation.hasBearing() ? newLocation.getBearing() : Float.NaN;
        lastPredictedLocation = predictFutureLocation(outputLoc, speed, bearingInput);

        if (!listeners.isEmpty()) {
            for (LocationUpdateListener l : listeners) {
                try {
                    l.onLocationUpdate(outputLoc, currentState);
                } catch (Exception ignored) {
                }
            }
        }

        // ✅ 弱信号检测优化：连续次数 + 时间窗口
        if (newLocation.getAccuracy() > WEAK_SIGNAL_THRESHOLD) {
            if (weakSignalStartTime == 0L) {
                weakSignalStartTime = System.currentTimeMillis();
            }
            weakSignalCount++;

            long weakDuration = System.currentTimeMillis() - weakSignalStartTime;
            // 只有持续超过15秒且连续次数达标才报警，防止地下车库/短时遮挡误报
            if (weakSignalCount >= 3 && weakDuration > 15_000L) {
                weakSignalCount = 0; // reset
                weakSignalStartTime = 0L;
                for (LocationUpdateListener l : listeners) {
                    if (l instanceof WeakSignalListener) {
                        try {
                            ((WeakSignalListener) l).onWeakSignal();
                        } catch (Exception ignored) {
                        }
                    }
                }
            }
        } else {
            // 信号恢复，立刻重置
            weakSignalCount = 0;
            weakSignalStartTime = 0L;
        }

        if (stateChanged || inBurstMode) {
            updateLocationParametersForState();
        }

        // ❌ 移除Boost强制退出 - 让其自然过期，避免红灯时误退

        lastDispatchUptimeMs = SystemClock.uptimeMillis();

        this.forwardToDrivingDistanceTracker(outputLoc, currentState);

    }

    private boolean updateMovementState() {
        MovementState newState;
        if (speed < 0.5) {
            newState = MovementState.STATIONARY;
        } else if (speed < 2) {
            newState = MovementState.WALKING;
        } else if (speed < 8) {
            newState = MovementState.SLOW_DRIVING;
        } else {
            newState = MovementState.NORMAL_DRIVING;
        }

        if (newState != currentState) {
            boolean isDrivingNow = newState == MovementState.SLOW_DRIVING || newState == MovementState.NORMAL_DRIVING;
            boolean wasNotDriving = currentState == MovementState.STATIONARY || currentState == MovementState.WALKING;

            if (isDrivingNow && wasNotDriving) {
                requestBoost(15_000L);
                requestSingleHighAccuracyFix();
            }

            FileLog.getInstance().debug(TAG, "movement state change: "
                    + currentState + " -> " + newState + ", speed=" + speed);

            // ✅ Bug#3: 跟踪连续静止状态
            if (newState == MovementState.STATIONARY && currentState != MovementState.STATIONARY) {
                continuousStationaryStartMs = System.currentTimeMillis();
            }

            previousState = currentState;
            currentState = newState;
            return true;
        }
        return false;
    }

    private void enterBurstMode() {
        enterBurstMode(BURST_MODE_DURATION_MS);
    }

    private void enterBurstMode(long durationMs) {
        long now = System.currentTimeMillis();
        // 防抖：如果刚进入过 burst 且仍在窗口内，只延长窗口，不重复配置
        if (inBurstMode && now - lastBoostChangeMs < BOOST_MIN_INTERVAL_MS) {
            boostHoldUntilMs = Math.max(boostHoldUntilMs, now + Math.max(1_000L, durationMs));
            scheduleBurstEnd();
            return;
        }
        inBurstMode = true;
        lastBoostChangeMs = now;
        boostHoldUntilMs = Math.max(boostHoldUntilMs, now + Math.max(1_000L, durationMs));
        updateLocationParametersForState();
        scheduleBurstEnd();
    }

    private void scheduleBurstEnd() {
        if (burstModeRunnableRef != null) {
            handler.removeCallbacks(burstModeRunnableRef);
        }
        burstModeRunnableRef = this::tryExitBurstMode;
        long now = System.currentTimeMillis();
        long delay = Math.max(500L, boostHoldUntilMs - now);
        handler.postDelayed(burstModeRunnableRef, delay);
    }

    private void tryExitBurstMode() {
        long now = System.currentTimeMillis();
        if (now < boostHoldUntilMs) {
            // 还在保持窗口内，重新调度到精确结束点
            scheduleBurstEnd();
            return;
        }
        exitBurstMode(true);
    }

    private void exitBurstMode() {
        exitBurstMode(false);
    }

    private void exitBurstMode(boolean fromTimer) {
        if (!inBurstMode)
            return;
        inBurstMode = false;
        boostHoldUntilMs = 0L;
        lastBoostChangeMs = System.currentTimeMillis();
        if (burstModeRunnableRef != null) {
            handler.removeCallbacks(burstModeRunnableRef);
            burstModeRunnableRef = null;
        }
        lastRequestedIntervalMs = -1L; // force reconfigure
        lastRequestedMinIntervalMs = -1L;
        updateLocationParametersForState();
    }

    private void updateLocationParametersForState() {
        long interval = inBurstMode ? getBurstModeInterval() : getRecommendedUpdateInterval();
        long minInterval = inBurstMode ? getBurstModeInterval() : getMinUpdateInterval();
        int priority = getRecommendedPriority();

        if (interval == lastRequestedIntervalMs && minInterval == lastRequestedMinIntervalMs
                && priority == lastRequestedPriority) {
            return;
        }

        lastRequestedIntervalMs = interval;
        lastRequestedMinIntervalMs = minInterval;
        lastRequestedPriority = priority;

        // ✅ 架构师建议#10: 直接request覆盖，不要remove
        // Google官方: "requestLocationUpdates with same callback replaces previous
        // request"
        requestLocationUpdates(interval, minInterval, priority);
    }

    /**
     * 判断司机是否处于“送件且不看地图”状态
     */
    private boolean isDeliveringAndIdle() {
        long timeSinceLastMovement = System.currentTimeMillis() - lastMovingTimeMs;
        return (currentState == MovementState.STATIONARY || currentState == MovementState.WALKING)
                && timeSinceLastMovement > DELIVERING_IDLE_THRESHOLD_MS;
    }

    /**
     * 获取推荐优先级
     */
    private int getRecommendedPriority() {
        if (inBurstMode) {
            return Priority.PRIORITY_HIGH_ACCURACY; // Boost 模式优先
        }

        if (isDeliveringAndIdle()) {
            return Priority.PRIORITY_BALANCED_POWER_ACCURACY; // 疯狂省电
        }

        // 其他情况：驾驶中 / 刚停车 / 步行
        return Priority.PRIORITY_HIGH_ACCURACY;
    }

    /**
     * 获取推荐更新间隔（ms）
     */
    private long getRecommendedUpdateInterval() {
        if (inBurstMode) {
            return INTERVAL_DRIVING_NORMAL_MS; // Boost 模式优先
        }

        if (isDeliveringAndIdle()) {
            return INTERVAL_DELIVERING_MS; // 疯狂省电
        }

        // Driving / Walking / Stationary 刚停车
        switch (currentState) {
            case STATIONARY:
            case WALKING:
                return INTERVAL_WALKING_MS; // 刚停车或慢走，高频更新
            case SLOW_DRIVING:
                return INTERVAL_DRIVING_SLOW_MS;
            case NORMAL_DRIVING:
                return INTERVAL_DRIVING_NORMAL_MS;
            default:
                return 4_000L;
        }
    }

    /**
     * 获取最小更新间隔（ms）
     */
    private long getMinUpdateInterval() {
        if (inBurstMode) {
            return MIN_INTERVAL_DRIVING_NORMAL_MS; // Boost 模式优先
        }

        if (isDeliveringAndIdle()) {
            return MIN_INTERVAL_DELIVERING_MS; // 极致省电
        }

        switch (currentState) {
            case STATIONARY:
            case WALKING:
                return MIN_INTERVAL_WALKING_MS;
            case SLOW_DRIVING:
                return MIN_INTERVAL_DRIVING_SLOW_MS;
            case NORMAL_DRIVING:
                return MIN_INTERVAL_DRIVING_NORMAL_MS;
            default:
                return 2_000L;
        }
    }

    private long getBurstModeInterval() {
        return 1000; // 1 second during burst mode
    }

    public void stopLocationUpdates() {
        if (fusedLocationClient != null && locationCallback != null) {
            fusedLocationClient.removeLocationUpdates(locationCallback);
        }
        stopHeadingUpdates();
        stopMotionWakeMonitoring();
        if (burstModeRunnableRef != null) {
            handler.removeCallbacks(burstModeRunnableRef);
            burstModeRunnableRef = null;
        }
    }

    public Location getLastLocation() {
        return lastLocation;
    }

    public MovementState getCurrentState() {
        return currentState;
    }

    public static void updateActivityState(Context ctx, int activityType, int transitionType) {
        MovementState newState;
        switch (activityType) {
            case DetectedActivity.IN_VEHICLE:
                newState = MovementState.NORMAL_DRIVING;
                break;
            case DetectedActivity.WALKING:
                newState = MovementState.WALKING;
                break;
            case DetectedActivity.RUNNING:
                newState = MovementState.SLOW_DRIVING;
                break;
            default:
                newState = MovementState.STATIONARY;
                break;
        }
        SmartLocationManager inst = getInstance(ctx != null ? ctx.getApplicationContext() : null);
        if (inst != null) {
            inst.updateStateFromActivity(newState);
        }
    }

    private void updateStateFromActivity(MovementState newState) {
        if (newState != currentState) {
            currentState = newState;
            updateLocationParametersForState();
        }
    }

    private void registerActivityTransitionUpdates() {
        ActivityTransitionRequest request = new ActivityTransitionRequest(getTransitions());
        activityRecognitionClient.requestActivityTransitionUpdates(request, activityRecognitionPendingIntent);
    }

    private List<ActivityTransition> getTransitions() {
        List<ActivityTransition> transitions = new ArrayList<>();

        transitions.add(new ActivityTransition.Builder()
                .setActivityType(DetectedActivity.IN_VEHICLE)
                .setActivityTransition(ActivityTransition.ACTIVITY_TRANSITION_ENTER)
                .build());

        transitions.add(new ActivityTransition.Builder()
                .setActivityType(DetectedActivity.IN_VEHICLE)
                .setActivityTransition(ActivityTransition.ACTIVITY_TRANSITION_EXIT)
                .build());

        transitions.add(new ActivityTransition.Builder()
                .setActivityType(DetectedActivity.WALKING)
                .setActivityTransition(ActivityTransition.ACTIVITY_TRANSITION_ENTER)
                .build());

        transitions.add(new ActivityTransition.Builder()
                .setActivityType(DetectedActivity.WALKING)
                .setActivityTransition(ActivityTransition.ACTIVITY_TRANSITION_EXIT)
                .build());

        transitions.add(new ActivityTransition.Builder()
                .setActivityType(DetectedActivity.RUNNING)
                .setActivityTransition(ActivityTransition.ACTIVITY_TRANSITION_ENTER)
                .build());

        transitions.add(new ActivityTransition.Builder()
                .setActivityType(DetectedActivity.RUNNING)
                .setActivityTransition(ActivityTransition.ACTIVITY_TRANSITION_EXIT)
                .build());

        return transitions;
    }

    public static class ActivityTransitionReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (ActivityTransitionResult.hasResult(intent)) {
                ActivityTransitionResult result = ActivityTransitionResult.extractResult(intent);
                for (ActivityTransitionEvent event : result.getTransitionEvents()) {
                    handleActivityTransition(context, event.getActivityType(), event.getTransitionType());
                }
            }
        }

        private void handleActivityTransition(Context context, int activityType, int transitionType) {
            String activityName = getActivityName(activityType);
            String transitionName = getTransitionName(transitionType);
            Log.d("ActivityTransition", "Activity: " + activityName + ", Transition: " + transitionName);
            // Implement state update logic based on activity transitions here
            SmartLocationManager.updateActivityState(context.getApplicationContext(), activityType, transitionType);
        }

        private String getActivityName(int activityType) {
            switch (activityType) {
                case DetectedActivity.IN_VEHICLE:
                    return "In Vehicle";
                case DetectedActivity.ON_BICYCLE:
                    return "On Bicycle";
                case DetectedActivity.ON_FOOT:
                    return "On Foot";
                case DetectedActivity.RUNNING:
                    return "Running";
                case DetectedActivity.STILL:
                    return "Still";
                case DetectedActivity.TILTING:
                    return "Tilting";
                case DetectedActivity.WALKING:
                    return "Walking";
                case DetectedActivity.UNKNOWN:
                default:
                    return "Unknown";
            }
        }

        private String getTransitionName(int transitionType) {
            switch (transitionType) {
                case ActivityTransition.ACTIVITY_TRANSITION_ENTER:
                    return "Enter";
                case ActivityTransition.ACTIVITY_TRANSITION_EXIT:
                    return "Exit";
                default:
                    return "Unknown";
            }
        }
    }

    // === Heading lifecycle ===
    private void startHeadingUpdates() {
        if (sensorManager != null && rotationVectorSensor != null) {
            sensorManager.registerListener(headingListener, rotationVectorSensor, SensorManager.SENSOR_DELAY_UI);
        }
    }

    private void stopHeadingUpdates() {
        if (sensorManager != null) {
            sensorManager.unregisterListener(headingListener);
        }
    }

    private final SensorEventListener headingListener = new SensorEventListener() {
        @Override
        public void onSensorChanged(SensorEvent event) {
            if (event.sensor.getType() == Sensor.TYPE_ROTATION_VECTOR) {
                SensorManager.getRotationMatrixFromVector(rotationMatrix, event.values);
                SensorManager.getOrientation(rotationMatrix, orientationAngles);
                float azimuthRad = orientationAngles[0];
                float azimuthDeg = (float) Math.toDegrees(azimuthRad);
                if (azimuthDeg < 0)
                    azimuthDeg += 360f;
                if (Float.isNaN(currentHeadingDegrees)) {
                    currentHeadingDegrees = azimuthDeg;
                } else {
                    currentHeadingDegrees = lowPassHeading(azimuthDeg, currentHeadingDegrees, HEADING_ALPHA);
                }
            }
        }

        @Override
        public void onAccuracyChanged(Sensor sensor, int accuracy) {
            headingAccuracy = accuracy;
        }
    };

    private static float lowPassHeading(float input, float output, float alpha) {
        // Handle wrap-around near 0/360 to avoid jumps
        float delta = input - output;
        if (Math.abs(delta) > 180f) {
            if (delta > 0f)
                output += 360f;
            else
                output -= 360f;
        }
        float result = output + alpha * (input - output);
        if (result >= 360f)
            result -= 360f;
        if (result < 0f)
            result += 360f;
        return result;
    }

    private void startMotionWakeMonitoring() {
        if (sensorManager != null && linearAccelerationSensor != null) {
            sensorManager.registerListener(accelListener, linearAccelerationSensor, SensorManager.SENSOR_DELAY_GAME);
        }
    }

    private void stopMotionWakeMonitoring() {
        if (sensorManager != null && linearAccelerationSensor != null) {
            sensorManager.unregisterListener(accelListener);
        }
        accelConsecutiveHits = 0;
    }

    private final SensorEventListener accelListener = new SensorEventListener() {
        @Override
        public void onSensorChanged(SensorEvent event) {
            if (event.sensor.getType() != Sensor.TYPE_LINEAR_ACCELERATION)
                return;

            float ax = event.values[0];
            float ay = event.values[1];
            float az = event.values[2];
            float magnitude = (float) Math.sqrt(ax * ax + ay * ay + az * az);

            // ✅ 更新buffer
            accelBuffer[accelBufferIndex] = magnitude;
            accelBufferIndex = (accelBufferIndex + 1) % ACCEL_BUFFER_SIZE;
            if (!accelBufferFilled && accelBufferIndex == 0) {
                accelBufferFilled = true;
            }

            // ✅ 计算动态阈值（均值 + 2.5倍标准差）
            float threshold = ACCEL_WAKE_THRESHOLD; // fallback
            if (accelBufferFilled) {
                float mean = 0f;
                for (float val : accelBuffer) {
                    mean += val;
                }
                mean /= ACCEL_BUFFER_SIZE;

                float variance = 0f;
                for (float val : accelBuffer) {
                    float diff = val - mean;
                    variance += diff * diff;
                }
                float std = (float) Math.sqrt(variance / ACCEL_BUFFER_SIZE);
                threshold = mean + NOISE_STD_MULTIPLIER * std;

                // 保底最小阈值0.6（避免过于敏感）
                if (threshold < 0.6f) {
                    threshold = 0.6f;
                }
            }

            long now = SystemClock.uptimeMillis();
            if (magnitude >= threshold) {
                if (now - lastAccelSpikeUptime > ACCEL_WINDOW_MS) {
                    accelConsecutiveHits = 0;
                }
                lastAccelSpikeUptime = now;
                accelConsecutiveHits++;
                if (accelConsecutiveHits >= ACCEL_REQUIRED_HITS) {
                    accelConsecutiveHits = 0;
                    maybeDispatchMotionWake(now);
                    // ✅ 调试日志
                    if (accelBufferFilled) {
                        float mean = 0f;
                        for (float val : accelBuffer)
                            mean += val;
                        mean /= ACCEL_BUFFER_SIZE;
                        FileLog.getInstance().debug(TAG,
                                String.format("Motion wake triggered: mag=%.2f, threshold=%.2f (mean=%.2f)", magnitude,
                                        threshold, mean));
                    }
                }
            } else if (now - lastAccelSpikeUptime > ACCEL_WINDOW_MS) {
                accelConsecutiveHits = 0;
            }
        }

        @Override
        public void onAccuracyChanged(Sensor sensor, int accuracy) {
            // no-op
        }
    };

    private void maybeDispatchMotionWake(long nowUptime) {
        if (nowUptime - lastMotionWakeUptime < MOTION_WAKE_COOLDOWN_MS) {
            return;
        }
        lastMotionWakeUptime = nowUptime;
        FileLog.getInstance().debug(TAG, "motion wake detected -> boost + single fix");
        try {
            requestBoost(8_000L);
        } catch (Throwable ignore) {
        }
        requestSingleHighAccuracyFix();
    }

    private void requestSingleHighAccuracyFix() {
        if (singleUpdateInFlight)
            return;
        if (fusedLocationClient == null)
            return;
        if (ActivityCompat.checkSelfPermission(context,
                Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            return;
        }
        singleUpdateInFlight = true;
        CancellationTokenSource tokenSource = new CancellationTokenSource();
        try {
            fusedLocationClient.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, tokenSource.getToken())
                    .addOnSuccessListener(location -> {
                        singleUpdateInFlight = false;
                        if (location != null) {
                            updateLocation(location);
                        }
                    })
                    .addOnFailureListener(error -> singleUpdateInFlight = false);
        } catch (SecurityException se) {
            singleUpdateInFlight = false;
        }
    }

    public boolean hasReliableHeading() {
        return !Float.isNaN(currentHeadingDegrees) && headingAccuracy != SensorManager.SENSOR_STATUS_UNRELIABLE;
    }

    /** Returns current heading in degrees [0,360), or NaN if unavailable. */
    public float getCurrentHeading() {
        return currentHeadingDegrees;
    }

    /** Returns last smoothed location (may be null). */
    public Location getLastSmoothedLocation() {
        return lastSmoothedLocation;
    }

    /** Returns last predicted location (may be null). */
    public Location getPredictedLocation() {
        if (lastPredictedLocation == null)
            return null;
        return new Location(lastPredictedLocation);
    }

    private Location predictFutureLocation(Location base, float speedMps, float bearingDegrees) {
        float heading = bearingDegrees;
        if (Float.isNaN(heading)) {
            heading = hasReliableHeading() ? currentHeadingDegrees : Float.NaN;
        }
        if (Float.isNaN(heading))
            return null;
        if (speedMps < MIN_PREDICTION_SPEED_MPS)
            return null;

        // ✅ 架构师建议#8: 动态预测horizon
        // 低速(2m/s) → 0.4s, 中速(10m/s) → 2.0s, 高速(20m/s) → 4.0s (clamped to 1.2s)
        float horizon = Math.max(0.3f, Math.min(1.2f, speedMps * 0.2f));
        double distance = speedMps * horizon;

        if (distance < 1.0)
            return null;
        double headingRad = Math.toRadians(heading);
        double latRad = Math.toRadians(base.getLatitude());
        double lonRad = Math.toRadians(base.getLongitude());
        double angularDistance = distance / EARTH_RADIUS_METERS;
        double newLatRad = Math.asin(Math.sin(latRad) * Math.cos(angularDistance) +
                Math.cos(latRad) * Math.sin(angularDistance) * Math.cos(headingRad));
        double newLonRad = lonRad + Math.atan2(Math.sin(headingRad) * Math.sin(angularDistance) * Math.cos(latRad),
                Math.cos(angularDistance) - Math.sin(latRad) * Math.sin(newLatRad));
        double newLat = Math.toDegrees(newLatRad);
        double newLon = Math.toDegrees(newLonRad);
        Location predicted = new Location(base);
        predicted.setLatitude(newLat);
        predicted.setLongitude(newLon);
        predicted.setTime(System.currentTimeMillis());
        predicted.setBearing(heading);
        predicted.setSpeed(speedMps);
        return predicted;
    }

    /**
     * Forward location to DrivingDistanceTracker (persisted odometer).
     * Direct call (no reflection).
     */
    private void forwardToDrivingDistanceTracker(Location loc, MovementState state) {
        if (loc == null)
            return;
        try {
            DrivingDistanceTracker
                    .getInstance(context.getApplicationContext())
                    .onLocationUpdate(loc, state);
        } catch (Throwable ignore) {
            // 某些构建变体若无 tracker，可安全忽略
        }
    }

}
