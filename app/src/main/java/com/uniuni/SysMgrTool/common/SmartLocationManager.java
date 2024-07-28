package com.uniuni.SysMgrTool.common;

import static com.google.android.gms.location.Priority.PRIORITY_HIGH_ACCURACY;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.location.Location;
import android.os.Handler;
import android.os.Looper;
import androidx.core.app.ActivityCompat;

import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationCallback;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationResult;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.Priority;

public class SmartLocationManager {
    private static final long BURST_MODE_DURATION_MS = 60 * 1000; // 1 minute
    private static final long MIN_STATE_CHANGE_INTERVAL_MS = 5 * 60 * 1000; // 5 minutes

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

    public enum MovementState {
        STATIONARY,
        WALKING,
        SLOW_DRIVING,
        NORMAL_DRIVING
    }

    public interface LocationUpdateListener {
        void onLocationUpdate(Location location, MovementState state);
    }

    public SmartLocationManager(Context context) {
        this.context = context;
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(context);
        handler = new Handler(Looper.getMainLooper());
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
                .setIntervalMillis(getRecommendedUpdateInterval())
                .setMinUpdateIntervalMillis(getMinUpdateInterval())
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
        burstModeRunnable = () -> exitBurstMode();
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
}