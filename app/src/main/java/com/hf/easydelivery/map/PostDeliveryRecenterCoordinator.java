package com.hf.easydelivery.map;

import android.graphics.Point;
import android.graphics.RectF;
import android.location.Location;
import android.os.SystemClock;
import android.text.TextUtils;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.MapView;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.LatLngBounds;
import com.hf.easydelivery.core.facade.MovementState;
import com.hf.easydelivery.dao.DeliveryInfo;

import java.util.Collections;
import java.util.List;

/**
 * One-shot post-delivery recenter helper.
 * 交付完成后的一次性回看下一票补偿。
 */
final class PostDeliveryRecenterCoordinator {

    private static final float CORE_MARGIN_X = 0.12f;
    private static final float CORE_MARGIN_Y = 0.14f;
    private static final long PENDING_WINDOW_MS = 8_000L;
    private static final float MAX_TRIGGER_SPEED_MPS = 6f;
    private static final float MAX_CANDIDATE_DISTANCE_METERS = 2_000f;

    private long pendingUntilUptimeMs = 0L;
    @Nullable
    private String excludedStableKey;

    void noteDeliveryCompleted(@Nullable DeliveryInfo delivered) {
        pendingUntilUptimeMs = SystemClock.uptimeMillis() + PENDING_WINDOW_MS;
        excludedStableKey = delivered != null ? delivered.getStableKey() : null;
    }

    boolean maybeTrigger(@NonNull GoogleMap googleMap,
            @NonNull MapView mapView,
            @NonNull CameraFollowController cameraController,
            @NonNull Location driverLocation,
            @NonNull MovementState movementState,
            @Nullable List<DeliveryInfo> deliveries,
            @Nullable DeliveryInfo currentPrimaryDelivery,
            boolean navigationModeEnabled,
            boolean autoFollowPaused,
            boolean userInteracting,
            boolean manualCenterHold) {
        long now = SystemClock.uptimeMillis();
        if (pendingUntilUptimeMs <= 0L || now > pendingUntilUptimeMs) {
            clearPending();
            return false;
        }
        if (navigationModeEnabled || autoFollowPaused || userInteracting || manualCenterHold) {
            return false;
        }
        if (movementState != MovementState.STATIONARY
                && movementState != MovementState.WALKING
                && movementState != MovementState.SLOW_DRIVING) {
            return false;
        }
        float speedMps = driverLocation.hasSpeed() ? Math.max(0f, driverLocation.getSpeed()) : 0f;
        if (speedMps > MAX_TRIGGER_SPEED_MPS) {
            return false;
        }

        int width = mapView.getWidth();
        int height = mapView.getHeight();
        if (width <= 0 || height <= 0) {
            return false;
        }

        List<DeliveryInfo> safeDeliveries = deliveries != null ? deliveries : Collections.emptyList();
        RectF coreRect = new RectF(
                width * CORE_MARGIN_X,
                height * CORE_MARGIN_Y,
                width * (1f - CORE_MARGIN_X),
                height * (1f - CORE_MARGIN_Y));
        if (hasParcelInRect(googleMap, coreRect, safeDeliveries)) {
            clearPending();
            return false;
        }

        DeliveryInfo candidate = chooseCandidate(driverLocation, safeDeliveries, currentPrimaryDelivery);
        if (candidate == null) {
            clearPending();
            return false;
        }

        float distance = distanceToDeliveryMeters(driverLocation, candidate);
        if (distance <= 0f || distance > MAX_CANDIDATE_DISTANCE_METERS) {
            clearPending();
            return false;
        }

        try {
            LatLng driverLatLng = new LatLng(driverLocation.getLatitude(), driverLocation.getLongitude());
            LatLng candidateLatLng = new LatLng(candidate.getLatitude(), candidate.getLongitude());
            LatLngBounds bounds = new LatLngBounds.Builder()
                    .include(driverLatLng)
                    .include(candidateLatLng)
                    .build();
            googleMap.animateCamera(CameraUpdateFactory.newLatLngBounds(bounds, 128));
            cameraController.refreshCameraActivityLock();
            clearPending();
            return true;
        } catch (Throwable ignore) {
            return false;
        }
    }

    private void clearPending() {
        pendingUntilUptimeMs = 0L;
        excludedStableKey = null;
    }

    @Nullable
    private DeliveryInfo chooseCandidate(@NonNull Location driverLocation,
            @NonNull List<DeliveryInfo> deliveries,
            @Nullable DeliveryInfo currentPrimaryDelivery) {
        DeliveryInfo primaryCandidate = eligibleCandidate(driverLocation, currentPrimaryDelivery);
        if (primaryCandidate != null) {
            return primaryCandidate;
        }

        DeliveryInfo best = null;
        float bestDistance = Float.MAX_VALUE;
        for (DeliveryInfo info : deliveries) {
            DeliveryInfo candidate = eligibleCandidate(driverLocation, info);
            if (candidate == null) {
                continue;
            }
            float distance = distanceToDeliveryMeters(driverLocation, candidate);
            if (distance < bestDistance) {
                bestDistance = distance;
                best = candidate;
            }
        }
        return best;
    }

    @Nullable
    private DeliveryInfo eligibleCandidate(@NonNull Location driverLocation,
            @Nullable DeliveryInfo candidate) {
        if (candidate == null) {
            return null;
        }
        if (!TextUtils.isEmpty(excludedStableKey) && excludedStableKey.equals(candidate.getStableKey())) {
            return null;
        }
        if (Double.isNaN(candidate.getLatitude()) || Double.isNaN(candidate.getLongitude())) {
            return null;
        }
        float distance = distanceToDeliveryMeters(driverLocation, candidate);
        if (distance <= 0f || distance > MAX_CANDIDATE_DISTANCE_METERS) {
            return null;
        }
        return candidate;
    }

    private boolean hasParcelInRect(@NonNull GoogleMap googleMap,
            @NonNull RectF rect,
            @NonNull List<DeliveryInfo> deliveries) {
        for (DeliveryInfo info : deliveries) {
            if (info == null) {
                continue;
            }
            if (!TextUtils.isEmpty(excludedStableKey) && excludedStableKey.equals(info.getStableKey())) {
                continue;
            }
            try {
                Point point = googleMap.getProjection()
                        .toScreenLocation(new LatLng(info.getLatitude(), info.getLongitude()));
                if (rect.contains(point.x, point.y)) {
                    return true;
                }
            } catch (Throwable ignore) {
            }
        }
        return false;
    }

    private float distanceToDeliveryMeters(@NonNull Location driverLocation, @NonNull DeliveryInfo info) {
        float[] result = new float[1];
        Location.distanceBetween(driverLocation.getLatitude(), driverLocation.getLongitude(),
                info.getLatitude(), info.getLongitude(), result);
        return result[0];
    }
}
