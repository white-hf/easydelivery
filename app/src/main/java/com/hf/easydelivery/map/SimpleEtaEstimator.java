package com.hf.easydelivery.map;

import android.location.Location;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.hf.easydelivery.core.SmartLocationManager;
import com.hf.easydelivery.dao.DeliveryInfo;

/**
 * SimpleEtaEstimator
 * ------------------
 * Lightweight ETA helper based purely on straight-line distance and a configurable
 * speed table keyed by movement state. It purposely avoids any network or routing
 * dependency so it can run entirely on-device.
 */
public final class SimpleEtaEstimator {

    public static final class Config {
        public float walkingSpeedMps = 1.3f;        // ~4.7 km/h
        public float slowDrivingMinMps = 5f;        // ~18 km/h
        public float slowDrivingMaxMps = 8f;        // ~29 km/h
        public float normalDrivingMinMps = 12f;     // ~43 km/h
        public float normalDrivingMaxMps = 20f;     // ~72 km/h
        public long minEtaSeconds = 60L;            // lower bound (door finding, parking, etc.)
        public long maxEtaSeconds = 900L;           // upper clamp (fail-safe)
        public float disableDistanceMeters = 3_000f; // beyond this distance we skip ETA gating
    }

    private final Config config;

    public SimpleEtaEstimator() {
        this(new Config());
    }

    public SimpleEtaEstimator(@NonNull Config config) {
        this.config = config;
    }

    /**
     * Estimate ETA seconds from current location/state to the target delivery.
     * Returns -1 when ETA should be ignored (missing coordinates, excessive distance, etc.).
     */
    public long estimateSeconds(@Nullable Location from,
                                @Nullable DeliveryInfo target,
                                @Nullable SmartLocationManager.MovementState movementState) {
        if (from == null || target == null) return -1L;
        double lat = target.getLatitude();
        double lon = target.getLongitude();
        if (Double.isNaN(lat) || Double.isNaN(lon)) return -1L;
        if (Math.abs(lat) < 1e-6 && Math.abs(lon) < 1e-6) return -1L;

        float[] meters = new float[1];
        Location.distanceBetween(from.getLatitude(), from.getLongitude(), lat, lon, meters);
        float distance = meters[0];
        if (distance <= 0f) return 0L;
        if (distance >= config.disableDistanceMeters) return -1L;

        float speedMps = pickSpeed(movementState, distance);
        if (speedMps <= 0f) return -1L;
        long eta = (long) Math.ceil(distance / speedMps);
        if (config.minEtaSeconds > 0) {
            eta = Math.max(eta, config.minEtaSeconds);
        }
        if (config.maxEtaSeconds > 0) {
            eta = Math.min(eta, config.maxEtaSeconds);
        }
        return Math.max(0L, eta);
    }

    private float pickSpeed(@Nullable SmartLocationManager.MovementState mv, float distanceMeters) {
        SmartLocationManager.MovementState state = mv == null
                ? SmartLocationManager.MovementState.SLOW_DRIVING
                : mv;
        switch (state) {
            case STATIONARY:
            case WALKING:
                return config.walkingSpeedMps;
            case SLOW_DRIVING:
                return lerp(config.slowDrivingMinMps, config.slowDrivingMaxMps, distanceMeters);
            case NORMAL_DRIVING:
            default:
                return lerp(config.normalDrivingMinMps, config.normalDrivingMaxMps, distanceMeters);
        }
    }

    private float lerp(float min, float max, float distanceMeters) {
        if (distanceMeters <= 0f) return min;
        float clamped = Math.min(distanceMeters, config.disableDistanceMeters);
        float ratio = clamped / config.disableDistanceMeters;
        return min + (max - min) * ratio;
    }
}
