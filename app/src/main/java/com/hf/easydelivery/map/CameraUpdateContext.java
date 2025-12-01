package com.hf.easydelivery.map;

import android.location.Location;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import com.hf.easydelivery.core.SmartLocationManager;
import com.hf.easydelivery.dao.DeliveryInfo;
import java.util.List;

/**
 * Data carrier for camera update context.
 * Encapsulates all state required for CameraFollowController to make decisions.
 */
public class CameraUpdateContext {
    @NonNull
    public final Location location;
    @NonNull
    public final SmartLocationManager.MovementState movementState;
    public final float nearestPackageDistanceMeters;
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
            @NonNull SmartLocationManager.MovementState movementState,
            float nearestPackageDistanceMeters,
            @Nullable List<DeliveryInfo> nearbyDeliveries,
            boolean insideDeliveryZone,
            int visibleMapHeightPx,
            boolean isUserInteracting,
            boolean isAutoFollowPaused,
            boolean isManualCenterHold,
            boolean isNavigationMode) {
        this.location = location;
        this.movementState = movementState;
        this.nearestPackageDistanceMeters = nearestPackageDistanceMeters;
        this.nearbyDeliveries = nearbyDeliveries;
        this.insideDeliveryZone = insideDeliveryZone;
        this.visibleMapHeightPx = visibleMapHeightPx;
        this.isUserInteracting = isUserInteracting;
        this.isAutoFollowPaused = isAutoFollowPaused;
        this.isManualCenterHold = isManualCenterHold;
        this.isNavigationMode = isNavigationMode;
    }

    public boolean isDriving() {
        return movementState == SmartLocationManager.MovementState.SLOW_DRIVING ||
                movementState == SmartLocationManager.MovementState.NORMAL_DRIVING;
    }

    public boolean isStationaryOrWalking() {
        return movementState == SmartLocationManager.MovementState.STATIONARY ||
                movementState == SmartLocationManager.MovementState.WALKING;
    }

    public boolean isLowSpeedInsideDeliveryZone() {
        return insideDeliveryZone && (movementState == SmartLocationManager.MovementState.WALKING
                || movementState == SmartLocationManager.MovementState.SLOW_DRIVING
                || movementState == SmartLocationManager.MovementState.STATIONARY);
    }
}
