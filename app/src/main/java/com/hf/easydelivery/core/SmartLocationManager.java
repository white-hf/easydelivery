/**
 * SmartLocationManager 主要逻辑（兼容原有设计，新增自适应提频能力）：
 * 1. 连续/高精度定位：根据司机运动状态（静止、步行、慢车、快车）动态调整定位间隔与精度；
 * 2. Burst 模式：当车辆靠近未完成派送包裹时，自动进入高频定位模式，以保证精确度，持续一定时长后恢复常规定位；
 * 3. 位置平滑：使用指数平滑算法减少 GPS 抖动，提升定位稳定性；
 * 4. 弱信号检测：当连续多次定位精度差（超出阈值）时，触发 onWeakSignal 回调提醒；
 * 5. 省电策略：静止时切换到 Significant Location Change 更新模式，避免持续高耗电。
 *
 * 【新增 / 扩展】
 * 6. 自适应临时提频（Boost）：当检测到“跳跃风险”或地图侧报告“边缘风险”时，
 *    通过 requestBoost(...) 进入临时高频定位窗口（例如 20s），并在窗口期内保持高频；
 *    与原有靠近包裹触发的 Burst 逻辑兼容，统一使用 inBurstMode 标志与同一套调度；
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
 *
 * 建议用法：
 * - 地图渲染层可结合 200–300ms 小步动画与短期速度预测实现视觉平滑，新定位点到来用于“微校正”；
 * - 当检测到蓝点朝屏幕边缘偏离且速度较大时调用 requestBoost(20000)；靠近包裹范围内由本类自动触发 Burst。
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
import android.util.Log;

import androidx.core.app.ActivityCompat;
import android.util.Pair;

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
import com.hf.easydelivery.ResourceMgr;
import com.hf.easydelivery.dao.DeliveryInfo;

import java.util.ArrayList;
import java.util.List;

/**
 * The SmartLocationManager class provides location-related functionality and try to reduce consumption of battery.
 * @author jvtang
 * @since 2024-08-21
 */
public class SmartLocationManager {
    private static final long BURST_MODE_DURATION_MS = 60 * 1000; // 1 minute
    private static SmartLocationManager instance;

    private Context context;
    private FusedLocationProviderClient fusedLocationClient;
    private LocationCallback locationCallback;
    private Location lastLocation;
    private Location lastSmoothedLocation;
    private float speed;
    private long lastUpdateTime;
    private MovementState currentState = MovementState.STATIONARY;
    private LocationUpdateListener listener;
    private Handler handler;
    private boolean inBurstMode = false;
    private Runnable burstModeRunnable;
    private ActivityRecognitionClient activityRecognitionClient;
    private PendingIntent activityRecognitionPendingIntent;
    private int weakSignalCount = 0;
    private static final double SMOOTHING_FACTOR = 0.2;
    private static final float WEAK_SIGNAL_THRESHOLD = 100f;

    // === Adaptive boost (temporary high-frequency updates) ===
    private static final long BOOST_MIN_INTERVAL_MS = 10_000L; // 预留：降频节流
    private long boostHoldUntilMs = 0L;   // 保持高频到这个时间戳（wall clock）
    private long lastBoostChangeMs = 0L;  // 最近一次切换 boost 状态（预留）

    // === Heading (bearing) support via sensors ===
    private SensorManager sensorManager;
    private Sensor rotationVectorSensor;
    private final float[] rotationMatrix = new float[9];
    private final float[] orientationAngles = new float[3];
    private float currentHeadingDegrees = Float.NaN; // 0..360, NaN if unknown
    private int headingAccuracy = SensorManager.SENSOR_STATUS_UNRELIABLE;

    // Keep a reference to remove only our burst runnable, not all callbacks
    private Runnable burstModeRunnableRef;

    // Low-pass for heading smoothing (0..1). Larger = quicker but noisier
    private static final float HEADING_ALPHA = 0.2f;

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
        else
        {
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
     * @param durationMs 例如 20_000（20 秒）
     */
    public void requestBoost(long durationMs) {
        if (durationMs <= 0) durationMs = 5_000L;
        if (inBurstMode) {
            long now = System.currentTimeMillis();
            boostHoldUntilMs = Math.max(boostHoldUntilMs, now + durationMs);
            scheduleBurstEnd();
            return;
        }
        enterBurstMode(durationMs);
    }

    /**
     * 便捷：地图检测到“边缘风险/不平滑风险”时调用。
     * @param offsetMeters 蓝点相对目标中心的米偏移
     * @param speedMps     当前速度 m/s
     */
    public void requestBoostIfEdgeRisk(float offsetMeters, float speedMps) {
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
        activityRecognitionPendingIntent = PendingIntent.getBroadcast(context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        registerActivityTransitionUpdates();

        sensorManager = (SensorManager) context.getSystemService(Context.SENSOR_SERVICE);
        if (sensorManager != null) {
            rotationVectorSensor = sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR);
        }
    }

    public void setLocationUpdateListener(LocationUpdateListener listener) {
        this.listener = listener;
    }

    public void startLocationUpdates() {
        if (ActivityCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            // Handle the case where permission is not granted
            return;
        }

        locationCallback = new LocationCallback() {
            @Override
            public void onLocationResult(LocationResult locationResult) {
                if (locationResult == null) {
                    return;
                }
                for (Location location : locationResult.getLocations()) {
                    updateLocation(location);
                }
            }
        };

        // Set initial update request
        updateLocationParametersForState();
        startHeadingUpdates();
    }

    private void requestLocationUpdates(long interval, long minInterval) {
        if (ActivityCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            // Handle the case where permission is not granted
            return;
        }

        LocationRequest locationRequest = new LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY)
                .setIntervalMillis(interval)
                .setMinUpdateIntervalMillis(minInterval)
                .build();

        fusedLocationClient.requestLocationUpdates(locationRequest,
                locationCallback,
                Looper.getMainLooper());
    }

    private void switchToSignificantChanges() {
        if (ActivityCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
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

        Location prevLast = lastLocation; // keep previous for jump computation

        // Prefer device-provided speed (m/s) if available; otherwise compute from distance/time
        if (newLocation.hasSpeed()) {
            speed = newLocation.getSpeed();
        } else if (lastLocation != null) {
            float distance = lastLocation.distanceTo(newLocation);
            long timeDiff = newLocation.getTime() - lastUpdateTime;
            speed = (timeDiff > 0) ? (distance / (float) timeDiff) * 1000f : 0f; // m/s
        } else {
            speed = 0f;
        }

        // Jump risk：两次点位跨度较大且在快速移动 → 临时提频以避免“到边再跳回”的突兀
        if (!inBurstMode && prevLast != null) {
            float jumpMeters = prevLast.distanceTo(newLocation);
            boolean movingFast = speed > 10f; // ~36 km/h
            if (movingFast && jumpMeters > 30f) {
                requestBoost(20_000L); // 提频20秒
            }
        }

        lastLocation = newLocation;
        lastUpdateTime = newLocation.getTime();

        boolean stateChanged = updateMovementState();

        Location outputLoc;
        if (lastSmoothedLocation != null) {
            double lat = lastSmoothedLocation.getLatitude() + SMOOTHING_FACTOR * (newLocation.getLatitude() - lastSmoothedLocation.getLatitude());
            double lon = lastSmoothedLocation.getLongitude() + SMOOTHING_FACTOR * (newLocation.getLongitude() - lastSmoothedLocation.getLongitude());
            outputLoc = new Location(newLocation);
            outputLoc.setLatitude(lat);
            outputLoc.setLongitude(lon);
        } else {
            outputLoc = newLocation;
        }
        lastSmoothedLocation = outputLoc;

        if (listener != null) {
            listener.onLocationUpdate(outputLoc, currentState);
        }

        if (newLocation.getAccuracy() > WEAK_SIGNAL_THRESHOLD) {
            weakSignalCount++;
            if (weakSignalCount >= 3) {
                weakSignalCount = 0;
                if (listener instanceof WeakSignalListener) {
                    ((WeakSignalListener) listener).onWeakSignal();
                }
            }
        } else {
            weakSignalCount = 0;
        }

        // If state changed or in burst mode, update location parameters
        if (stateChanged || inBurstMode) {
            updateLocationParametersForState();
        }

        // If state changed to non-stationary, exit burst mode
        if (stateChanged && currentState != MovementState.STATIONARY) {
            exitBurstMode();
        }

        if (lastSmoothedLocation != null) {
            checkNearestPackageDistanceForBurst();
        }
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
            currentState = newState;
            // Removed automatic enterBurstMode on STATIONARY to allow explicit burst mode or distance-based triggers
            return true; // State has changed
        }
        return false; // State has not changed
    }

    private void enterBurstMode() {
        enterBurstMode(BURST_MODE_DURATION_MS);
    }

    private void enterBurstMode(long durationMs) {
        long now = System.currentTimeMillis();
        inBurstMode = true;
        boostHoldUntilMs = Math.max(boostHoldUntilMs, now + Math.max(1_000L, durationMs));
        lastBoostChangeMs = now;
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
        if (!inBurstMode) return;
        inBurstMode = false;
        boostHoldUntilMs = 0L;
        lastBoostChangeMs = System.currentTimeMillis();
        if (burstModeRunnableRef != null) {
            handler.removeCallbacks(burstModeRunnableRef);
            burstModeRunnableRef = null;
        }
        updateLocationParametersForState();
    }

    private void updateLocationParametersForState() {
        if (currentState == MovementState.STATIONARY && !inBurstMode) {
            switchToSignificantChanges();
            return;
        }
        long interval = inBurstMode ? getBurstModeInterval() : getRecommendedUpdateInterval();
        long minInterval = inBurstMode ? getBurstModeInterval() : getMinUpdateInterval();
        if (fusedLocationClient != null && locationCallback != null) {
            fusedLocationClient.removeLocationUpdates(locationCallback);
        }
        requestLocationUpdates(interval, minInterval);
    }

    private long getRecommendedUpdateInterval() {
        switch (currentState) {
            case STATIONARY:
                return 60 * 1000; // 1 minute (shorter initial interval)
            case WALKING:
                return 30 * 1000; // 30 seconds
            case SLOW_DRIVING:
                return 15 * 1000; // 15 seconds
            case NORMAL_DRIVING:
                return 5 * 1000; // 5 seconds
            default:
                return 30 * 1000; // Default 30 seconds
        }
    }


    private long getMinUpdateInterval() {
        switch (currentState) {
            case STATIONARY:
                return 30 * 1000; // 30 seconds (shorter initial interval)
            case WALKING:
                return 10 * 1000; // 10 seconds
            case SLOW_DRIVING:
                return 5 * 1000; // 5 seconds
            case NORMAL_DRIVING:
                return 3 * 1000; // 3 seconds
            default:
                return 10 * 1000; // Default 10 seconds
        }
    }

    private long getBurstModeInterval() {
        return 1000; // 1 second during burst mode
    }

    private void checkNearestPackageDistanceForBurst() {
        if (lastSmoothedLocation == null) return;
        Pair<DeliveryInfo, Double> nearest = ResourceMgr.getInstance()
                .getDeliveryinfoMgr()
                .findNearestPackage(lastSmoothedLocation, lastSmoothedLocation, 200);
        if (!inBurstMode && nearest.second != null && nearest.second < 200 && speed < 2.22f) {
            enterBurstMode();
        }
    }

    public void stopLocationUpdates() {
        if (fusedLocationClient != null && locationCallback != null) {
            fusedLocationClient.removeLocationUpdates(locationCallback);
        }
        stopHeadingUpdates();
        handler.removeCallbacksAndMessages(null);
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
                if (azimuthDeg < 0) azimuthDeg += 360f;
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
            if (delta > 0f) output += 360f; else output -= 360f;
        }
        float result = output + alpha * (input - output);
        if (result >= 360f) result -= 360f;
        if (result < 0f) result += 360f;
        return result;
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
}