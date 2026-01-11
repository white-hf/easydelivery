package com.hf.easydelivery.core.facade;

import android.location.Location;

public final class LocationSnapshot {
    public final MovementState movementState;
    public final boolean inBurstMode;
    public final boolean uiFollowActive;
    public final boolean movingFlag;
    public final boolean foregroundTrackingActive;
    public final float speedMps;
    public final long lastUpdateTimeMs;
    public final long lastGoodFixTimeMs;
    public final long stationaryDurationMs;
    public final float currentHeadingDeg;
    public final Location lastLocation;
    public final Location lastSmoothedLocation;
    public final Location lastPredictedLocation;

    public LocationSnapshot(MovementState movementState,
            boolean inBurstMode,
            boolean uiFollowActive,
            boolean movingFlag,
            boolean foregroundTrackingActive,
            float speedMps,
            long lastUpdateTimeMs,
            long lastGoodFixTimeMs,
            long stationaryDurationMs,
            float currentHeadingDeg,
            Location lastLocation,
            Location lastSmoothedLocation,
            Location lastPredictedLocation) {
        this.movementState = movementState;
        this.inBurstMode = inBurstMode;
        this.uiFollowActive = uiFollowActive;
        this.movingFlag = movingFlag;
        this.foregroundTrackingActive = foregroundTrackingActive;
        this.speedMps = speedMps;
        this.lastUpdateTimeMs = lastUpdateTimeMs;
        this.lastGoodFixTimeMs = lastGoodFixTimeMs;
        this.stationaryDurationMs = stationaryDurationMs;
        this.currentHeadingDeg = currentHeadingDeg;
        this.lastLocation = lastLocation;
        this.lastSmoothedLocation = lastSmoothedLocation;
        this.lastPredictedLocation = lastPredictedLocation;
    }
}
