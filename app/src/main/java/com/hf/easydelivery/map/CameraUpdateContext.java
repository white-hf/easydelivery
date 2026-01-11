package com.hf.easydelivery.map;

import android.location.Location;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import com.hf.easydelivery.core.facade.MovementState;
import com.hf.easydelivery.dao.DeliveryInfo;
import java.util.List;

/**
 * Data carrier for camera update context.
 * Encapsulates all state required for CameraFollowController to make decisions.
 */
public class CameraUpdateContext {
    public enum LocationSource {
        RAW,
        LAST_GOOD,
        PREDICTED,
        UNKNOWN
    }

    @NonNull
    public final Location location;
    @NonNull
    public final MovementState movementState;
    public final float accuracyMeters;
    public final long locationAgeMs;
    public final LocationSource locationSource;
    public final float speedMps;
    public final float bearingDeg;
    public final float headingDeg;
    public final float nearestPackageDistanceMeters;
    public final long stationaryDurationMs;
    @Nullable
    public final List<DeliveryInfo> nearbyDeliveries;
    public final boolean insideDeliveryZone;
    public final int visibleMapHeightPx;
    public final boolean isUserInteracting;
    public final boolean isAutoFollowPaused;
    public final boolean isManualCenterHold;
    public final boolean isNavigationMode;

    public CameraUpdateContext(
            @NonNull Location location,
            @NonNull MovementState movementState,
            float accuracyMeters,
            long locationAgeMs,
            @NonNull LocationSource locationSource,
            float speedMps,
            float bearingDeg,
            float headingDeg,
            float nearestPackageDistanceMeters,
            long stationaryDurationMs,
            @Nullable List<DeliveryInfo> nearbyDeliveries,
            boolean insideDeliveryZone,
            int visibleMapHeightPx,
            boolean isUserInteracting,
            boolean isAutoFollowPaused,
            boolean isManualCenterHold,
            boolean isNavigationMode) {
        this.location = location;
        this.movementState = movementState;
        this.accuracyMeters = accuracyMeters;
        this.locationAgeMs = locationAgeMs;
        this.locationSource = locationSource;
        this.speedMps = speedMps;
        this.bearingDeg = bearingDeg;
        this.headingDeg = headingDeg;
        this.nearestPackageDistanceMeters = nearestPackageDistanceMeters;
        this.stationaryDurationMs = stationaryDurationMs;
        this.nearbyDeliveries = nearbyDeliveries;
        this.insideDeliveryZone = insideDeliveryZone;
        this.visibleMapHeightPx = visibleMapHeightPx;
        this.isUserInteracting = isUserInteracting;
        this.isAutoFollowPaused = isAutoFollowPaused;
        this.isManualCenterHold = isManualCenterHold;
        this.isNavigationMode = isNavigationMode;
    }

    public boolean isDriving() {
        return movementState == MovementState.SLOW_DRIVING ||
                movementState == MovementState.NORMAL_DRIVING;
    }

    public boolean isMovingBySpeed() {
        return !Float.isNaN(speedMps) && speedMps >= 1.5f;
    }

    public boolean isDrivingLikely() {
        return isDriving() || isMovingBySpeed();
    }

    public boolean isStationaryOrWalking() {
        return movementState == MovementState.STATIONARY ||
                movementState == MovementState.WALKING;
    }

    public boolean isLowSpeedInsideDeliveryZone() {
        return insideDeliveryZone && (movementState == MovementState.WALKING
                || movementState == MovementState.SLOW_DRIVING
                || movementState == MovementState.STATIONARY);
    }
}
