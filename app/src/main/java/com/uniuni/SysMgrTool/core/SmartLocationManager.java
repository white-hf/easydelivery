package com.uniuni.SysMgrTool.core;

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

import java.util.ArrayList;
import java.util.List;

public class SmartLocationManager {
    private static final long BURST_MODE_DURATION_MS = 60 * 1000; // 1 minute
    private static SmartLocationManager instance;

    private Context context;
    private FusedLocationProviderClient fusedLocationClient;
    private LocationCallback locationCallback;
    private Location lastLocation;
    private float speed;
    private long lastUpdateTime;
    private MovementState currentState = MovementState.STATIONARY;
    private LocationUpdateListener listener;
    private Handler handler;
    private boolean inBurstMode = false;
    private Runnable burstModeRunnable;
    private ActivityRecognitionClient activityRecognitionClient;
    private PendingIntent activityRecognitionPendingIntent;

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
        requestLocationUpdates();
    }

    private void requestLocationUpdates() {
        if (ActivityCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            // Handle the case where permission is not granted
            return;
        }

        long interval = inBurstMode ? getBurstModeInterval() : getRecommendedUpdateInterval();
        long minInterval = inBurstMode ? getBurstModeInterval() : getMinUpdateInterval();

        LocationRequest locationRequest = new LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY)
                .setIntervalMillis(interval)
                .setMinUpdateIntervalMillis(minInterval)
                .build();

        fusedLocationClient.requestLocationUpdates(locationRequest,
                locationCallback,
                Looper.getMainLooper());
    }

    private void updateLocation(Location newLocation) {
        if (lastLocation != null) {
            float distance = lastLocation.distanceTo(newLocation);
            long timeDiff = newLocation.getTime() - lastUpdateTime;

            if (timeDiff > 0) {
                speed = (distance / timeDiff) * 1000; // Convert to meters per second
            } else {
                speed = 0; // Assume zero speed if time difference is zero
            }
        } else {
            speed = 0; // Cannot calculate speed on first update
        }

        lastLocation = newLocation;
        lastUpdateTime = newLocation.getTime();

        boolean stateChanged = updateMovementState();

        if (listener != null) {
            listener.onLocationUpdate(newLocation, currentState);
        }

        // If state changed or in burst mode, update location request
        if (stateChanged || inBurstMode) {
            requestLocationUpdates();
        }

        // If state changed to non-stationary, exit burst mode
        if (stateChanged && currentState != MovementState.STATIONARY) {
            exitBurstMode();
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
            if (currentState == MovementState.STATIONARY) {
                enterBurstMode();
            }
            return true; // State has changed
        }
        return false; // State has not changed
    }

    private void enterBurstMode() {
        inBurstMode = true;
        requestLocationUpdates();
        if (burstModeRunnable != null) {
            handler.removeCallbacks(burstModeRunnable);
        }
        burstModeRunnable = this::exitBurstMode;
        handler.postDelayed(burstModeRunnable, BURST_MODE_DURATION_MS);
    }

    private void exitBurstMode() {
        inBurstMode = false;
        handler.removeCallbacksAndMessages(null);
        requestLocationUpdates();
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

    public void stopLocationUpdates() {
        if (fusedLocationClient != null && locationCallback != null) {
            fusedLocationClient.removeLocationUpdates(locationCallback);
        }
        handler.removeCallbacksAndMessages(null);
    }

    public Location getLastLocation() {
        return lastLocation;
    }

    public MovementState getCurrentState() {
        return currentState;
    }

    public static void updateActivityState(int activityType, int transitionType) {
        MovementState newState = MovementState.STATIONARY;
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

        // Assuming you have a singleton or static reference to SmartLocationManager instance
        SmartLocationManager instance = getInstance(null);
        if (instance != null) {
            instance.updateStateFromActivity(newState);
        }
    }

    private void updateStateFromActivity(MovementState newState) {
        if (newState != currentState) {
            currentState = newState;
            requestLocationUpdates();
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
                    handleActivityTransition(event.getActivityType(), event.getTransitionType());
                }
            }
        }

        private void handleActivityTransition(int activityType, int transitionType) {
            String activityName = getActivityName(activityType);
            String transitionName = getTransitionName(transitionType);
            Log.d("ActivityTransition", "Activity: " + activityName + ", Transition: " + transitionName);
            // Implement state update logic based on activity transitions here
            SmartLocationManager.updateActivityState(activityType, transitionType);
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
}
