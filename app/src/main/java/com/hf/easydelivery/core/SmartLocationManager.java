/**
 * SmartLocationManager 主要逻辑：
 * 1. 连续/高精度定位：根据司机运动状态（静止、步行、慢车、快车）动态调整定位间隔与精度；
 * 2. Burst 模式：当车辆靠近未完成派送包裹时，自动进入高频定位模式，以保证精确度，持续一定时长后恢复常规定位；
 * 3. 位置平滑：使用指数平滑算法减少 GPS 抖动，提升定位稳定性；
 * 4. 弱信号检测：当连续多次定位精度差（超出阈值）时，触发 onWeakSignal 回调提醒；
 * 5. 省电策略：静止时切换到 Significant Location Change 更新模式，避免持续高耗电。
 *
 * 本类目标：在不影响司机拍照和派送核心操作的前提下，通过动态策略兼顾定位精度与电量消耗，
 * 并结合运动状态和包裹位置智能切换定位模式，提升驾驶体验与里程效率。
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
        if (!inBurstMode) {
            inBurstMode = true;
            updateLocationParametersForState();
            if (burstModeRunnableRef != null) {
                handler.removeCallbacks(burstModeRunnableRef);
            }
            burstModeRunnableRef = this::exitBurstMode;
            handler.postDelayed(burstModeRunnableRef, BURST_MODE_DURATION_MS);
        }
    }

    private void exitBurstMode() {
        if (inBurstMode) {
            inBurstMode = false;
            if (burstModeRunnableRef != null) {
                handler.removeCallbacks(burstModeRunnableRef);
                burstModeRunnableRef = null;
            }
            updateLocationParametersForState();
        }
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