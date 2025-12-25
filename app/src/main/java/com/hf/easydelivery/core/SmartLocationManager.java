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
import androidx.annotation.Nullable;
import androidx.annotation.NonNull;
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
import com.hf.easydelivery.telemetry.Telemetry;
import com.hf.easydelivery.core.policy.LocationPolicy;
import com.hf.easydelivery.core.policy.LocationPolicyContext;
import com.hf.easydelivery.core.policy.LocationRequestParams;
import com.hf.easydelivery.core.policy.RealtimeLocationPolicy;

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
    // --- Split caches to avoid low-accuracy pollution / UI stalls ---
    // lastRawLocation: always the most recent fix (even if poor)
    // lastLocation: last GOOD fix used for speed/state logic (kept for backward
    // compatibility)
    private Location lastRawLocation;
    private Location lastDispatchedLocation;
    private long lastGoodLocationUptimeMs = 0L;
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
    private static final double SMOOTHING_FACTOR = 0.2; // legacy fallback; now dynamic via computeSmoothingFactor
    private static final float WEAK_SIGNAL_THRESHOLD = 100f;
    private static final double EARTH_RADIUS_METERS = 6378137.0;
    private static final float MIN_PREDICTION_SPEED_MPS = 0.8f;
    private static final long MIN_DISPATCH_INTERVAL_MOVING_MS = 250L;
    private static final long MIN_DISPATCH_INTERVAL_STATIONARY_MS = 800L;
    private static final String BOOST_REASON_UNKNOWN = "unknown";
    private static final String BOOST_REASON_FORCE = "force";
    private static final String BOOST_REASON_EDGE_RISK = "edge_risk";
    private static final String BOOST_REASON_POOR_FIX = "poor_fix";
    private static final String BOOST_REASON_EMERGENCY = "emergency";
    private static final String BOOST_REASON_DISPLACEMENT = "displacement_wake";
    private static final String BOOST_REASON_JUMP = "jump_risk";
    private static final String BOOST_REASON_MOTION = "motion_wake";
    private static final String BOOST_REASON_INSIDE = "inside_zone";
    private static final String BOOST_REASON_PROXIMITY = "proximity";
    private static final String BOOST_REASON_MANUAL = "manual";
    // ✅ PREDICTION_HORIZON_SEC removed - now dynamic based on speed

    // === Adaptive boost (temporary high-frequency updates) ===
    private static final long BOOST_MIN_INTERVAL_MS = 20_000L; // 冷却收敛
    private static final long LOW_PRIORITY_BOOST_COOLDOWN_MS = 25_000L;
    private static final long DISPLACEMENT_WAKE_WINDOW_MS = 5_000L;
    private static final int DISPLACEMENT_WAKE_REQUIRED_HITS = 2;
    private static final float DISPLACEMENT_WAKE_THRESHOLD_M = 25.0f;
    private static final long EDGE_RISK_WINDOW_MS = 4_000L;
    private static final int EDGE_RISK_REQUIRED_HITS = 2;
    private static final long JUMP_RISK_WINDOW_MS = 5_000L;
    private static final int JUMP_RISK_REQUIRED_HITS = 2;
    private long boostHoldUntilMs = 0L;
    private long lastBoostChangeMs = 0L;
    private long lastLowPriorityBoostMs = 0L;
    private int displacementWakeHits = 0;
    private long lastDisplacementWakeMs = 0L;
    private int edgeRiskHits = 0;
    private long lastEdgeRiskMs = 0L;
    private int jumpRiskHits = 0;
    private long lastJumpRiskMs = 0L;

    // === 架构师建议：3分钟真静止检测 ===
    private long lastMovingTimeMs = 0L;
    private long continuousStationaryStartMs = 0L; // ✅ Bug#3: 跟踪连续静止开始时间
    private MovementState previousState = MovementState.STATIONARY;
    private long lastGoodFixTime = 0L;
    private long lastDrivingUptimeMs = 0L;
    private long lastInVehicleUptimeMs = 0L;
    private float lastDisplacementMeters = 0f;
    private boolean movingFlag = false;
    private long movingHoldUntilMs = 0L;
    private long lastUiDispatchUptimeMs = 0L;
    private Location lastUiLocation = null;
    private boolean uiFollowActive = false;
    private boolean sensorsActive = false;
    private static final float ACCURACY_THRESHOLD_GOOD = 35f;
    private static final float ACCURACY_THRESHOLD_GOOD_DRIVING = 50f;
    private static final float ACCURACY_THRESHOLD_POOR = 50f;
    private static final float ACCURACY_THRESHOLD_STATE_MAX = 120f;
    private static final long POOR_SIGNAL_GRACE_PERIOD = 15_000L;
    private static final long EMERGENCY_BOOST_THRESHOLD_MS = 15_000L;
    private static final long DRIVING_DOWNGRADE_GRACE_MS = 5_000L;
    private static final long IN_VEHICLE_GRACE_MS = 15_000L;
    private static final float DISPLACEMENT_DRIVING_OVERRIDE_M = 12f;
    private static final long MOVING_HOLD_MS = 15_000L;
    private static final long UI_FORCE_DISPATCH_MS = 1_200L;

    // === Heading (bearing) support via sensors ===
    private SensorManager sensorManager;
    private Sensor rotationVectorSensor;
    private Sensor linearAccelerationSensor;
    private Sensor accelerometerSensor; // ✅ Fallback
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
    private float lastRequestedMinDistanceM = -1f;
    private long lastRequestedMaxDelayMs = -1L;
    private long dispatchSeq = 0L;
    private long lastDispatchElapsedMs = -1L;
    private long currentMinDispatchIntervalMs = MIN_DISPATCH_INTERVAL_MOVING_MS;

    private LocationPolicy locationPolicy = new RealtimeLocationPolicy();

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
    private static final long DELIVERING_IDLE_THRESHOLD_MS = 180_000L; // 3min: avoid red-light/traffic mis-downgrade
    private static final long INTERVAL_DRIVING_NORMAL_MS = 1_500L;
    private static final long INTERVAL_DRIVING_SLOW_MS = 2_500L;
    private static final long INTERVAL_WALKING_MS = 2_000L;
    private static final long INTERVAL_DELIVERING_MS = 45_000L;

    private static final long MIN_INTERVAL_DRIVING_NORMAL_MS = 800L;
    private static final long MIN_INTERVAL_DRIVING_SLOW_MS = 1_500L;
    private static final long MIN_INTERVAL_WALKING_MS = 1_000L;
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
        requestBoostInternal(durationMs, false, BOOST_REASON_UNKNOWN);
    }

    /** 用户手势/强制提频，绕过静止>3分钟限制。 */
    public void requestBoostForce(long durationMs) {
        requestBoostInternal(durationMs, true, BOOST_REASON_FORCE);
    }

    public void requestBoost(long durationMs, @NonNull String reason) {
        requestBoostInternal(durationMs, false, reason);
    }

    public void requestBoostForce(long durationMs, @NonNull String reason) {
        requestBoostInternal(durationMs, true, reason);
    }

    private void requestBoostInternal(long durationMs, boolean force, @NonNull String reason) {
        long now = System.currentTimeMillis();

        // ✅ 架构师建议#9: 2秒内防止Boost叠加
        // 防止updateMovementState + displacement wake + edge risk同时触发时叠加
        if (inBurstMode && (now - lastBoostChangeMs < 2_000L)) {
            FileLog.getInstance().debug(TAG, "requestBoost debounced: already boosted recently");
            return;
        }

        // ✅ 架构师建议：只有真·静止3分钟以上才拒绝提频
        // 允许红灯、塞车时仍保持高频
        // 启动后还没有任何有效移动时，允许一次提频获取首 fix
        if (lastMovingTimeMs == 0L) {
            lastMovingTimeMs = now;
        } else if (!force && speed < 0.3f && (now - lastMovingTimeMs > 180_000L)) {
            long sinceMotionWake = SystemClock.uptimeMillis() - lastMotionWakeUptime;
            if (sinceMotionWake > 15_000L) {
                FileLog.getInstance().debug(TAG,
                        "requestBoost refused: truly stationary for " +
                                (now - lastMovingTimeMs) / 1000 + "s");
                return;
            } else {
                FileLog.getInstance().debug(TAG,
                        "requestBoost allowed after motion wake, stationary for "
                                + (now - lastMovingTimeMs) / 1000 + "s");
            }
        } else if (force) {
            FileLog.getInstance().debug(TAG, "requestBoost forced by user/gesture");
        }

        String reasonKey = reason == null ? BOOST_REASON_UNKNOWN : reason;
        Telemetry.counter("boost.request");
        Telemetry.counter("boost." + reasonKey);
        if (force) {
            Telemetry.counter("boost.force");
        }
        FileLog.getInstance().debug(TAG, "requestBoost reason=" + reason + " force=" + force);
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
        if (now - lastEdgeRiskMs > EDGE_RISK_WINDOW_MS) {
            edgeRiskHits = 0;
        }
        if (offsetMeters > 25f && speedMps > 5f) {
            edgeRiskHits++;
            lastEdgeRiskMs = now;
        }
        if (edgeRiskHits >= EDGE_RISK_REQUIRED_HITS
                && now - lastBoostChangeMs >= BOOST_MIN_INTERVAL_MS) {
            edgeRiskHits = 0;
            requestBoost(10_000L, BOOST_REASON_EDGE_RISK);
        }
    }

    private void maybeTriggerDisplacementBoost(float displacement, long nowMs, boolean requestSingleFix) {
        if (displacement < DISPLACEMENT_WAKE_THRESHOLD_M) {
            return;
        }
        if (nowMs - lastDisplacementWakeMs > DISPLACEMENT_WAKE_WINDOW_MS) {
            displacementWakeHits = 0;
        }
        displacementWakeHits++;
        lastDisplacementWakeMs = nowMs;
        if (displacementWakeHits < DISPLACEMENT_WAKE_REQUIRED_HITS) {
            return;
        }
        if (nowMs - lastLowPriorityBoostMs < LOW_PRIORITY_BOOST_COOLDOWN_MS) {
            return;
        }
        if (nowMs - lastBoostChangeMs < BOOST_MIN_INTERVAL_MS) {
            return;
        }
        displacementWakeHits = 0;
        lastLowPriorityBoostMs = nowMs;
        FileLog.getInstance().debug(TAG,
                String.format("Displacement wake: %.1fm -> boost", displacement));
        requestBoost(8_000L, BOOST_REASON_DISPLACEMENT);
        if (requestSingleFix) {
            requestSingleHighAccuracyFix();
        }
    }

    private void maybeTriggerJumpBoost(float jumpMeters, long nowMs) {
        if (nowMs - lastJumpRiskMs > JUMP_RISK_WINDOW_MS) {
            jumpRiskHits = 0;
        }
        jumpRiskHits++;
        lastJumpRiskMs = nowMs;
        if (jumpRiskHits < JUMP_RISK_REQUIRED_HITS) {
            return;
        }
        if (nowMs - lastLowPriorityBoostMs < LOW_PRIORITY_BOOST_COOLDOWN_MS) {
            return;
        }
        if (nowMs - lastBoostChangeMs < BOOST_MIN_INTERVAL_MS) {
            return;
        }
        jumpRiskHits = 0;
        lastLowPriorityBoostMs = nowMs;
        requestBoost(8_000L, BOOST_REASON_JUMP);
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
            if (linearAccelerationSensor == null) {
                accelerometerSensor = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
                FileLog.i(TAG, "Linear acceleration sensor missing, using basic accelerometer as fallback");
            }
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

    public void setLocationPolicy(@Nullable LocationPolicy policy) {
        if (policy != null) {
            this.locationPolicy = policy;
            updateLocationParametersForState();
        }
    }

    private LocationPolicyContext buildLocationPolicyContext() {
        return new LocationPolicyContext(
                currentState,
                inBurstMode,
                isDeliveringAndIdle(),
                uiFollowActive,
                movingFlag);
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

        // 启动时重置静止计时，避免继承上次会话的“长时间静止”状态
        lastMovingTimeMs = System.currentTimeMillis();

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
                Location last = locations.get(locations.size() - 1);
                FileLog.getInstance().debug(TAG,
                        String.format(
                                "onLocationResult: provider=%s acc=%.1fm speed=%.2f hasSpeed=%s mock=%s elapsedMs=%d",
                                last.getProvider(),
                                last.getAccuracy(),
                                last.hasSpeed() ? last.getSpeed() : 0f,
                                last.hasSpeed(),
                                last.isFromMockProvider(),
                                getElapsedRealtimeMsSafe(last)));
                Telemetry.counter("onLocationResult");
                updateLocation(last);
            }
        };

        // Set initial update request
        updateLocationParametersForState();
        updateSensorState();
    }

    private void requestLocationUpdates(long interval, long minInterval, int priority, float minDistanceMeters,
            long maxUpdateDelayMs) {
        if (ActivityCompat.checkSelfPermission(context,
                Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            return;
        }
        if (locationCallback == null) {
            FileLog.getInstance().warning(TAG, "requestLocationUpdates skipped: locationCallback not ready");
            return;
        }

        LocationRequest locationRequest = new LocationRequest.Builder(priority)
                .setIntervalMillis(interval)
                .setMinUpdateIntervalMillis(minInterval)
                .setMaxUpdateDelayMillis(maxUpdateDelayMs)
                .setMinUpdateDistanceMeters(minDistanceMeters)
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
                    lastRequestedMinDistanceM = -1f;
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

        Location prevGood = lastLocation;
        long nowMillis = System.currentTimeMillis();
        long nowUptime = SystemClock.elapsedRealtime();
        boolean recentMoving = movingHoldUntilMs > 0 && nowUptime <= movingHoldUntilMs;
        movingFlag = recentMoving;

        // Always keep raw (even if poor) so we can diagnose/repair without UI stalls
        lastRawLocation = newLocation;

        // Treat very old fixes as poor (can happen with batched / cached results)
        // Prefer monotonic elapsedRealtime to avoid wall-clock skew and cached
        // timestamps.
        long ageMs;
        long locElapsedMs = getElapsedRealtimeMsSafe(newLocation);
        if (locElapsedMs > 0L) {
            ageMs = SystemClock.elapsedRealtime() - locElapsedMs;
        } else {
            ageMs = nowMillis - newLocation.getTime();
        }
        boolean staleFix = ageMs > 5_000L;

        // ✅ Bug#4修复：动态精度检查（但不立即return）
        long timeSinceGoodFix = nowMillis - lastGoodFixTime;
        boolean drivingState = currentState == MovementState.SLOW_DRIVING
                || currentState == MovementState.NORMAL_DRIVING;
        float goodThreshold = drivingState ? ACCURACY_THRESHOLD_GOOD_DRIVING : ACCURACY_THRESHOLD_GOOD;
        float accuracyThreshold = timeSinceGoodFix > POOR_SIGNAL_GRACE_PERIOD
                ? ACCURACY_THRESHOLD_POOR
                : goodThreshold;

        boolean lowAccuracy = newLocation.getAccuracy() > accuracyThreshold;
        boolean poorFix = staleFix || lowAccuracy;

        if (poorFix) {
            FileLog.getInstance().debug(TAG,
                    String.format("Poor fix: acc=%.1fm(thr=%.1fm) stale=%s age=%dms",
                            newLocation.getAccuracy(), accuracyThreshold, String.valueOf(staleFix), ageMs));

            // ✅ Bugfix: 即使点很旧，如果位移很大且精度尚可，也预研判为“运动开始”触发提频
            if (staleFix && lastLocation != null && newLocation.getAccuracy() <= ACCURACY_THRESHOLD_POOR) {
                float displacement = lastLocation.distanceTo(newLocation);
                maybeTriggerDisplacementBoost(displacement, nowMillis, true);
            }
            FileLog.getInstance().debug(TAG, "dispatch fallback (limited motion update)");
        }

        // Record last good fix time using strict GOOD threshold
        if (!staleFix && newLocation.getAccuracy() <= goodThreshold) {
            lastGoodFixTime = nowMillis;
        }

        // ✅ Bug#4修复：Emergency Boost基于 last GOOD fix 的时间，避免UI断流
        if (prevGood != null) {
            long timeSinceLastGood = nowMillis - prevGood.getTime();
            if (timeSinceLastGood > EMERGENCY_BOOST_THRESHOLD_MS && !inBurstMode) {
                FileLog.getInstance().debug(TAG,
                        String.format("Emergency boost: no good fix for %ds", timeSinceLastGood / 1000));
                requestBoost(8_000L, BOOST_REASON_EMERGENCY);
                // ✅ Emergency Boost 时，强拉一次定位，强制跳过 hardware 内部 sleep
                requestSingleHighAccuracyFix();
            }
        }

        // =========================================================
        // POOR FIX PATH: never stop dispatching, but DO NOT pollute state/speed
        // =========================================================
        if (poorFix) {
            // If we still don't have any good fix, try single high accuracy fix to
            // bootstrap
            if (lastGoodFixTime == 0L && !singleUpdateInFlight) {
                requestSingleHighAccuracyFix();
            }
            // Stale fix even after having good ones: try to pull a fresh high-accuracy fix
            // once
            if (staleFix && !singleUpdateInFlight) {
                requestSingleHighAccuracyFix();
            }

            // Use poor-fix motion evidence to avoid freezing driving state
            if (!staleFix && lastLocation != null && newLocation.getAccuracy() <= ACCURACY_THRESHOLD_STATE_MAX) {
                float displacement = lastLocation.distanceTo(newLocation);
                long newElapsed = getElapsedRealtimeMsSafe(newLocation);
                long lastElapsed = getElapsedRealtimeMsSafe(lastLocation);
                long dtMs = (newElapsed > 0 && lastElapsed > 0) ? (newElapsed - lastElapsed)
                        : (newLocation.getTime() - lastLocation.getTime());
                float speedFromDisp = 0f;
                if (dtMs > 0 && displacement > 2.0f) {
                    speedFromDisp = displacement / (dtMs / 1000f);
                }
                float speedFromGps = newLocation.hasSpeed() ? newLocation.getSpeed() : 0f;
                float motionSpeed = Math.max(speedFromGps, speedFromDisp);
                lastDisplacementMeters = displacement;
                if (motionSpeed >= 2.0f || displacement >= DISPLACEMENT_DRIVING_OVERRIDE_M) {
                    lastMovingTimeMs = nowMillis;
                    lastDrivingUptimeMs = SystemClock.elapsedRealtime();
                    movingHoldUntilMs = nowUptime + MOVING_HOLD_MS;
                    movingFlag = true;
                    if (currentState == MovementState.STATIONARY || currentState == MovementState.WALKING) {
                        currentState = (motionSpeed >= 8.0f)
                                ? MovementState.NORMAL_DRIVING
                                : MovementState.SLOW_DRIVING;
                        updateLocationParametersForState();
                    }
                }
            }

            // Build a fallback location to keep UI moving smoothly
            Location fallback = buildFallbackForDispatch(newLocation, nowMillis);
            if (fallback == null) {
                // Use the raw fix as-is if we couldn't build a synthetic fallback
                fallback = new Location(newLocation);
            } else {
                // Synthetic fallback: ensure monotonic time advances so dispatch de-dup doesn't
                // drop it
                stampElapsedRealtimeNow(fallback);
            }

            // Update lastDispatchedLocation for continuity
            lastDispatchedLocation = fallback;

            // If we have never had a good fix, keep lastLocation as something usable
            if (lastLocation == null) {
                lastLocation = fallback;
                lastUpdateTime = fallback.getTime();
            }

            // Dispatch to listeners (do not update movement state from poor fix)
            dispatchToListeners(fallback);
            lastDispatchUptimeMs = SystemClock.uptimeMillis();
            lastUiDispatchUptimeMs = lastDispatchUptimeMs;
            lastUiLocation = fallback;

            // Do NOT forward poor fixes to distance tracker
            return;
        }

        // =========================================================
        // GOOD FIX PATH: update speed/state and normal smoothing/prediction
        // =========================================================

        // ✅ 速度兜底策略（仅在 good fix 时更新，避免 low-accuracy 污染）
        float gpsSpeed = newLocation.hasSpeed() ? newLocation.getSpeed() : 0f;
        float displacementSpeed = 0f;
        float distance = 0f;
        if (lastLocation != null) {
            distance = lastLocation.distanceTo(newLocation);
            long timeDiff = newLocation.getTime() - lastUpdateTime;
            if (timeDiff > 0 && distance > 2.0f) {
                float dtSec = timeDiff / 1000f;
                float distCap;
                if (timeDiff < 5_000L) {
                    distCap = 15.0f; // 短周期防跳点
                } else {
                    // 长周期用合理速度上限（约126km/h）约束，避免被低频更新压成“龟速”
                    distCap = 35.0f * dtSec;
                }
                float distForSpeed = Math.min(distance, distCap);
                displacementSpeed = distForSpeed / dtSec;
            }
        }
        lastDisplacementMeters = distance;
        speed = Math.max(gpsSpeed, displacementSpeed);

        // ✅ lastMovingTimeMs更新（good fix）
        if (speed > 0.3f || distance > 2.0f) {
            lastMovingTimeMs = nowMillis;
            movingHoldUntilMs = nowUptime + MOVING_HOLD_MS;
            movingFlag = true;
        }
        if (speed >= 2.0f) {
            lastDrivingUptimeMs = SystemClock.elapsedRealtime();
        }

        // ✅ 架构师建议：位移>8米立即唤醒（仅 good fix 可信）
        if (lastLocation != null) {
            float displacement = lastLocation.distanceTo(newLocation);
            maybeTriggerDisplacementBoost(displacement, nowMillis, false);
        }

        // Jump risk：两次点位跨度较大且在快速移动 → 临时提频以避免"到边再跳回"的突兀
        if (!inBurstMode && prevGood != null) {
            float jumpMeters = prevGood.distanceTo(newLocation);
            boolean movingFast = speed > 10f;
            if (movingFast && jumpMeters > 30f) {
                maybeTriggerJumpBoost(jumpMeters, nowMillis);
            }
        }

        // Commit GOOD fix as lastLocation (used for state/speed)
        lastLocation = newLocation;
        lastGoodLocationUptimeMs = SystemClock.uptimeMillis();
        long prevUpdateTime = lastUpdateTime;

        boolean stateChanged = updateMovementState();

        // ✅ 全状态防抖：同一个 fix 或极短间隔/微小位移不分发，避免动画/CPU 被刷屏
        boolean shouldDispatch = true;
        if (lastSmoothedLocation != null) {
            long timeDiff = prevUpdateTime <= 0L ? Long.MAX_VALUE : newLocation.getTime() - prevUpdateTime;
            float d = lastSmoothedLocation.distanceTo(newLocation);
            long elapsedMs = getElapsedRealtimeMsSafe(newLocation);
            if (timeDiff < 200L || d < 0.8f) {
                shouldDispatch = false;
            }
            // 同一个 elapsed（同一 fix 被多次回调）直接跳过
            if (elapsedMs > 0 && elapsedMs == getElapsedRealtimeMsSafe(lastSmoothedLocation)) {
                shouldDispatch = false;
            }
            // 静止去抖：低速 + 低位移 + 最近未处于驾驶
            if (shouldDispatch
                    && speed < 0.5f
                    && (SystemClock.elapsedRealtime() - lastDrivingUptimeMs) > DRIVING_DOWNGRADE_GRACE_MS
                    && timeDiff < 2000L && d < 2.0f) {
                shouldDispatch = false;
            }
        }

        if (!shouldDispatch) {
            if (!movingFlag || nowUptime - lastUiDispatchUptimeMs < UI_FORCE_DISPATCH_MS) {
                return;
            }
        }

        Location outputLoc;
        double smoothingFactor = computeSmoothingFactor(speed, currentState);
        if (lastSmoothedLocation != null) {
            double lat = lastSmoothedLocation.getLatitude()
                    + smoothingFactor * (newLocation.getLatitude() - lastSmoothedLocation.getLatitude());
            double lon = lastSmoothedLocation.getLongitude()
                    + smoothingFactor * (newLocation.getLongitude() - lastSmoothedLocation.getLongitude());
            outputLoc = new Location(newLocation);
            outputLoc.setLatitude(lat);
            outputLoc.setLongitude(lon);
            // Synthetic point: stamp monotonic time so downstream de-dup doesn't treat it
            // as old
            stampElapsedRealtimeNow(outputLoc);
        } else {
            outputLoc = newLocation;
        }

        lastSmoothedLocation = outputLoc;
        float bearingInput = newLocation.hasBearing() ? newLocation.getBearing() : Float.NaN;
        lastPredictedLocation = predictFutureLocation(newLocation, speed, bearingInput);

        // Dispatch
        lastDispatchedLocation = outputLoc;
        dispatchToListeners(outputLoc);
        lastUiDispatchUptimeMs = SystemClock.uptimeMillis();
        lastUiLocation = outputLoc;
        lastUpdateTime = newLocation.getTime();

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

        lastDispatchUptimeMs = lastUiDispatchUptimeMs;

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

        long nowUptime = SystemClock.elapsedRealtime();
        boolean wasDriving = currentState == MovementState.SLOW_DRIVING
                || currentState == MovementState.NORMAL_DRIVING;
        if (newState == MovementState.STATIONARY && wasDriving) {
            boolean recentDriving = nowUptime - lastDrivingUptimeMs < DRIVING_DOWNGRADE_GRACE_MS;
            boolean recentVehicle = nowUptime - lastInVehicleUptimeMs < IN_VEHICLE_GRACE_MS;
            boolean displacementOverride = lastDisplacementMeters >= DISPLACEMENT_DRIVING_OVERRIDE_M;
            if (recentDriving || recentVehicle || displacementOverride) {
                newState = currentState;
            }
        }

        if (newState != currentState) {
            boolean isDrivingNow = newState == MovementState.SLOW_DRIVING || newState == MovementState.NORMAL_DRIVING;
            boolean wasNotDriving = currentState == MovementState.STATIONARY || currentState == MovementState.WALKING;

            if (wasNotDriving && newState != MovementState.STATIONARY) {
                requestBoost(10_000L, BOOST_REASON_MOTION);
                // ✅ 从静止状态离开时（不管是 WALKING 还是 DRIVING），都强刷一次定位
                requestSingleHighAccuracyFix();
            }

            FileLog.getInstance().debug(TAG, "movement state change: "
                    + currentState + " -> " + newState + ", speed=" + speed);

            // ✅ Bug#3: 跟踪连续静止状态
            if (newState == MovementState.STATIONARY || newState == MovementState.WALKING) {
                if (currentState != newState) {
                    continuousStationaryStartMs = System.currentTimeMillis();
                }
            } else {
                continuousStationaryStartMs = 0L;
            }

            previousState = currentState;
            currentState = newState;
            updateSensorState();
            return true;
        }
        return false;
    }

    public long getStationaryDurationMs() {
        if (currentState != MovementState.STATIONARY && currentState != MovementState.WALKING) {
            return 0L;
        }
        if (continuousStationaryStartMs <= 0L) {
            return 0L;
        }
        return Math.max(0L, System.currentTimeMillis() - continuousStationaryStartMs);
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
        Telemetry.counter("burst.enter");
        Telemetry.state("burst", true);
        boostHoldUntilMs = Math.max(boostHoldUntilMs, now + Math.max(1_000L, durationMs));
        FileLog.getInstance().debug(TAG,
                String.format("enterBurstMode durationMs=%d holdUntil=%d", durationMs, boostHoldUntilMs));
        updateLocationParametersForState();
        updateSensorState();
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
        Telemetry.counter("burst.exit");
        Telemetry.state("burst", false);
        FileLog.getInstance().debug(TAG,
                String.format("exitBurstMode fromTimer=%s", String.valueOf(fromTimer)));
        updateSensorState();
        if (burstModeRunnableRef != null) {
            handler.removeCallbacks(burstModeRunnableRef);
            burstModeRunnableRef = null;
        }
        lastRequestedIntervalMs = -1L; // force reconfigure
        lastRequestedMinIntervalMs = -1L;
        updateLocationParametersForState();
    }
    private void updateLocationParametersForState() {
        if (locationCallback == null) {
            return;
        }
        LocationRequestParams params = locationPolicy != null
                ? locationPolicy.getRequestParams(buildLocationPolicyContext())
                : null;
        long interval = params != null ? params.intervalMs
                : (inBurstMode ? getBurstModeInterval() : getRecommendedUpdateInterval());
        long minInterval = params != null ? params.minIntervalMs
                : (inBurstMode ? getBurstModeInterval() : getMinUpdateInterval());
        int priority = params != null ? params.priority : getRecommendedPriority();
        float minDistance = params != null ? params.minDistanceMeters : getMinUpdateDistanceMeters();
        long maxDelay = params != null ? params.maxUpdateDelayMs : 800L;
        currentMinDispatchIntervalMs = params != null ? params.minDispatchIntervalMs
                : (currentState == MovementState.STATIONARY && !movingFlag
                        ? MIN_DISPATCH_INTERVAL_STATIONARY_MS
                        : MIN_DISPATCH_INTERVAL_MOVING_MS);

        if (interval == lastRequestedIntervalMs && minInterval == lastRequestedMinIntervalMs
                && priority == lastRequestedPriority && minDistance == lastRequestedMinDistanceM
                && maxDelay == lastRequestedMaxDelayMs) {
            return;
        }

        lastRequestedIntervalMs = interval;
        lastRequestedMinIntervalMs = minInterval;
        lastRequestedPriority = priority;
        lastRequestedMinDistanceM = minDistance;
        lastRequestedMaxDelayMs = maxDelay;

        requestLocationUpdates(interval, minInterval, priority, minDistance, maxDelay);
    }


    /**
     * 判断司机是否处于“送件且不看地图”状态
     */
    private boolean isDeliveringAndIdle() {
        long timeSinceLastMovement = System.currentTimeMillis() - lastMovingTimeMs;
        // ✅ 优化闲置判定：只有 STATIONARY 且长时间未动才降频；WALKING 状态不进入极低频模式
        return (currentState == MovementState.STATIONARY)
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

        // 非 Burst 统一降到跟随级精度
        return Priority.PRIORITY_BALANCED_POWER_ACCURACY;
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

    private float getMinUpdateDistanceMeters() {
        if (inBurstMode) {
            return 0.5f;
        }
        if (isDeliveringAndIdle()) {
            return 8.0f;
        }
        switch (currentState) {
            case STATIONARY:
                return 6.0f;
            case WALKING:
                return 2.5f;
            case SLOW_DRIVING:
            case NORMAL_DRIVING:
            default:
                return 2.0f;
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

    public boolean isMovingLikely() {
        long nowUptime = SystemClock.elapsedRealtime();
        return movingFlag || (movingHoldUntilMs > 0 && nowUptime <= movingHoldUntilMs);
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
        if (newState == MovementState.SLOW_DRIVING || newState == MovementState.NORMAL_DRIVING) {
            lastInVehicleUptimeMs = SystemClock.elapsedRealtime();
            movingHoldUntilMs = lastInVehicleUptimeMs + MOVING_HOLD_MS;
            movingFlag = true;
        }
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
    public void setUiFollowActive(boolean active) {
        uiFollowActive = active;
        updateSensorState();
        updateLocationParametersForState();
    }

    private void updateSensorState() {
        boolean shouldEnable = uiFollowActive
                && (inBurstMode
                        || currentState == MovementState.SLOW_DRIVING
                        || currentState == MovementState.NORMAL_DRIVING);
        if (shouldEnable == sensorsActive) {
            return;
        }
        sensorsActive = shouldEnable;
        if (sensorsActive) {
            startHeadingUpdates();
            startMotionWakeMonitoring();
        } else {
            stopHeadingUpdates();
            stopMotionWakeMonitoring();
        }
    }

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
        if (sensorManager != null) {
            if (linearAccelerationSensor != null) {
                sensorManager.registerListener(accelListener, linearAccelerationSensor,
                        SensorManager.SENSOR_DELAY_UI);
            } else if (accelerometerSensor != null) {
                sensorManager.registerListener(accelListener, accelerometerSensor, SensorManager.SENSOR_DELAY_UI);
            }
        }
    }

    private void stopMotionWakeMonitoring() {
        if (sensorManager != null) {
            sensorManager.unregisterListener(accelListener);
        }
        accelConsecutiveHits = 0;
    }

    private final SensorEventListener accelListener = new SensorEventListener() {
        @Override
        public void onSensorChanged(SensorEvent event) {
            int type = event.sensor.getType();
            if (type != Sensor.TYPE_LINEAR_ACCELERATION && type != Sensor.TYPE_ACCELEROMETER)
                return;

            float ax = event.values[0];
            float ay = event.values[1];
            float az = event.values[2];
            float magnitude = (float) Math.sqrt(ax * ax + ay * ay + az * az);

            // ✅ 如果是基础加速度计，减去重力近似值 (9.8)，只保留波动部分
            if (type == Sensor.TYPE_ACCELEROMETER) {
                magnitude = Math.abs(magnitude - 9.81f);
            }

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
        // 上报运动唤醒，确保不再被“长期静止”挡住
        lastMovingTimeMs = System.currentTimeMillis();
        FileLog.getInstance().debug(TAG, "motion wake detected -> boost + single fix");
        try {
            // 起步瞬间直接拉高频窗口，避免等待 GPS 速度上升
            requestBoostForce(10_000L, BOOST_REASON_MOTION);
        } catch (Throwable ignore) {
        }
        requestSingleHighAccuracyFix();
    }

    public void requestSingleHighAccuracyFix() {
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

    private double computeSmoothingFactor(float speedMps, @NonNull MovementState state) {
        switch (state) {
            case NORMAL_DRIVING:
            case SLOW_DRIVING:
                // Higher alpha in driving to avoid 5–10s visual lag (urban speeds are often
                // 5–11 m/s)
                if (speedMps > 8f)
                    return 0.8;
                if (speedMps > 5f)
                    return 0.65;
                if (speedMps > 2f)
                    return 0.55;
                return 0.2;
            case WALKING:
                return 0.2;
            case STATIONARY:
            default:
                return 0.15;
        }
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

        // ✅ 架构师建议#8: 动态预测horizon + 驾驶态轻量前视
        float horizon = Math.max(0.3f, Math.min(1.2f, speedMps * 0.2f));
        if (speedMps > 5f) {
            horizon = Math.min(1.5f, horizon + 0.3f);
        }
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
        stampElapsedRealtimeNow(predicted);
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

    @Nullable
    private Location buildFallbackForDispatch(@NonNull Location raw, long nowMillis) {
        // 1) Prefer a very recent prediction
        if (lastPredictedLocation != null) {
            long age = nowMillis - lastPredictedLocation.getTime();
            if (age >= 0 && age <= 2_000L) {
                Location p = new Location(lastPredictedLocation);
                p.setTime(nowMillis);
                stampElapsedRealtimeNow(p);
                return p;
            }
        }

        // 2) Short-horizon predict from last dispatched / smoothed location
        Location base = null;
        if (lastDispatchedLocation != null) {
            base = lastDispatchedLocation;
        } else if (lastSmoothedLocation != null) {
            base = lastSmoothedLocation;
        } else if (lastLocation != null) {
            base = lastLocation;
        }

        if (base == null)
            return null;

        float bearingInput = raw.hasBearing() ? raw.getBearing() : Float.NaN;
        Location predicted = predictFutureLocation(base, speed, bearingInput);
        if (predicted != null) {
            predicted.setTime(nowMillis);
            stampElapsedRealtimeNow(predicted);
            return predicted;
        }

        // 3) As a last resort, reuse base to keep UI stable
        Location reuse = new Location(base);
        reuse.setTime(nowMillis);
        stampElapsedRealtimeNow(reuse);
        return reuse;
    }

    /**
     * For synthetic locations (smoothed/predicted/fallback), stamp monotonic time
     * so downstream
     * de-duplication based on elapsedRealtime does not accidentally drop updates.
     */
    private void stampElapsedRealtimeNow(@NonNull Location loc) {
        try {
            loc.setElapsedRealtimeNanos(SystemClock.elapsedRealtimeNanos());
        } catch (Throwable ignored) {
        }
    }

    private long getElapsedRealtimeMsSafe(@NonNull Location loc) {
        try {
            return loc.getElapsedRealtimeNanos() / 1_000_000L;
        } catch (Throwable t) {
            return -1L;
        }
    }

    private void dispatchToListeners(@NonNull Location loc) {
        if (listeners.isEmpty())
            return;
        long nowUptime = SystemClock.uptimeMillis();
        long minInterval = currentMinDispatchIntervalMs;
        if (locationPolicy != null) {
            LocationRequestParams params = locationPolicy.getRequestParams(buildLocationPolicyContext());
            if (params != null) {
                minInterval = params.minDispatchIntervalMs;
            }
        }
        if (nowUptime - lastDispatchUptimeMs < minInterval) {
            return;
        }
        long elapsedMs = getElapsedRealtimeMsSafe(loc);
        if (elapsedMs > 0 && elapsedMs == lastDispatchElapsedMs) {
            FileLog.getInstance().debug(TAG,
                    String.format("dispatch dedup: same elapsedMs=%d skip", elapsedMs));
            return;
        }
        lastDispatchElapsedMs = elapsedMs;
        lastDispatchUptimeMs = nowUptime;
        dispatchSeq++;
        FileLog.getInstance().debug(TAG, String.format(
                "dispatch #%d listeners=%d elapsedMs=%d mv=%s",
                dispatchSeq,
                listeners.size(),
                getElapsedRealtimeMsSafe(loc),
                currentState));
        Telemetry.counter("dispatch");
        for (LocationUpdateListener l : listeners) {
            try {
                l.onLocationUpdate(loc, currentState);
            } catch (Exception ignored) {
            }
        }
    }
}
