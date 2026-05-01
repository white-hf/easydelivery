package com.hf.easydelivery.map;

import android.graphics.Point;
import android.graphics.RectF;
import android.location.Location;
import android.os.SystemClock;
import android.text.TextUtils;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.MapView;
import com.google.android.gms.maps.model.LatLng;
import com.hf.easydelivery.core.facade.MovementState;
import com.hf.easydelivery.dao.DeliveryInfo;

import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * One-shot empty-screen rescue coordinator.
 * 空屏一次性补偿协调器。
 *
 * Keeps the trigger rules and candidate selection out of the fragment so
 * map-screen orchestration stays small and predictable.
 * 把触发规则和候选包裹筛选从 Fragment 中抽离，避免地图页继续堆积流程细节。
 */
final class EmptyScreenRescueCoordinator {

    private static final float CORE_MARGIN_X = 0.12f;
    private static final float CORE_MARGIN_Y = 0.14f;
    private static final float EXPAND_MARGIN_X = 0.15f;
    private static final float EXPAND_MARGIN_TOP = 0.10f;
    private static final float EXPAND_MARGIN_BOTTOM = -0.02f;
    private static final float MAX_DISTANCE_METERS = 250f;
    private static final float MAX_SPEED_MPS = 9f;
    private static final long MIN_INTERVAL_MS = 2_500L;

    private final Set<String> consumedTargetKeys = new HashSet<>();
    private long lastTriggerUptimeMs = 0L;

    /**
     * Try a single rescue nudge when the core visible area is empty.
     * 当核心可视区没有包裹时，尝试执行一次轻量镜头补偿。
     */
    boolean maybeTrigger(@NonNull GoogleMap googleMap,
            @NonNull MapView mapView,
            @NonNull CameraFollowController cameraController,
            @NonNull Location driverLocation,
            @NonNull MovementState movementState,
            @Nullable List<DeliveryInfo> deliveries,
            @Nullable DeliveryInfo primaryDelivery,
            boolean navigationModeEnabled,
            boolean autoFollowPaused,
            boolean userInteracting,
            boolean manualCenterHold) {
        if (navigationModeEnabled || autoFollowPaused || userInteracting || manualCenterHold) {
            return false;
        }
        if (movementState != MovementState.SLOW_DRIVING && movementState != MovementState.NORMAL_DRIVING) {
            return false;
        }
        float speedMps = driverLocation.hasSpeed() ? Math.max(0f, driverLocation.getSpeed()) : 0f;
        if (speedMps > MAX_SPEED_MPS) {
            return false;
        }
        long now = SystemClock.uptimeMillis();
        if ((now - lastTriggerUptimeMs) < MIN_INTERVAL_MS) {
            return false;
        }

        int width = mapView.getWidth();
        int height = mapView.getHeight();
        if (width <= 0 || height <= 0) {
            return false;
        }

        RectF coreRect = new RectF(
                width * CORE_MARGIN_X,
                height * CORE_MARGIN_Y,
                width * (1f - CORE_MARGIN_X),
                height * (1f - CORE_MARGIN_Y));
        List<DeliveryInfo> safeDeliveries = deliveries != null ? deliveries : Collections.emptyList();
        if (hasParcelInRect(googleMap, coreRect, safeDeliveries)) {
            return false;
        }

        DeliveryInfo candidate = chooseCandidate(driverLocation, safeDeliveries, primaryDelivery);
        if (candidate == null) {
            return false;
        }
        String key = buildTargetKey(candidate);
        if (TextUtils.isEmpty(key) || consumedTargetKeys.contains(key)) {
            return false;
        }

        float distanceToCandidate = distanceToDeliveryMeters(driverLocation, candidate);
        if (distanceToCandidate <= 0f || distanceToCandidate > MAX_DISTANCE_METERS) {
            return false;
        }

        Point candidatePoint;
        try {
            candidatePoint = googleMap.getProjection()
                    .toScreenLocation(new LatLng(candidate.getLatitude(), candidate.getLongitude()));
        } catch (Throwable t) {
            return false;
        }
        if (coreRect.contains(candidatePoint.x, candidatePoint.y)) {
            return false;
        }

        RectF expandedRect = new RectF(
                -width * EXPAND_MARGIN_X,
                -height * EXPAND_MARGIN_TOP,
                width * (1f + EXPAND_MARGIN_X),
                height * (1f - EXPAND_MARGIN_BOTTOM));
        if (!expandedRect.contains(candidatePoint.x, candidatePoint.y)) {
            return false;
        }

        boolean nudged = cameraController.nudgeCameraTowardOffscreenParcel(
                new LatLng(candidate.getLatitude(), candidate.getLongitude()));
        if (nudged) {
            consumedTargetKeys.add(key);
            lastTriggerUptimeMs = now;
        }
        return nudged;
    }

    @Nullable
    // Prefer the current focus parcel, then fall back to the nearest eligible one.
    // 优先当前 focus 包裹，其次回退到最近的可用候选。
    private DeliveryInfo chooseCandidate(@NonNull Location driverLocation,
            @NonNull List<DeliveryInfo> deliveries,
            @Nullable DeliveryInfo primaryDelivery) {
        DeliveryInfo best = eligibleCandidate(driverLocation, primaryDelivery);
        if (best != null) {
            return best;
        }

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
    // Keep the first version intentionally narrow: only nearby, valid coordinates.
    // 第一版保持收敛：只接受距离近且坐标有效的候选。
    private DeliveryInfo eligibleCandidate(@NonNull Location driverLocation,
            @Nullable DeliveryInfo candidate) {
        if (candidate == null) {
            return null;
        }
        if (Double.isNaN(candidate.getLatitude()) || Double.isNaN(candidate.getLongitude())) {
            return null;
        }
        float distance = distanceToDeliveryMeters(driverLocation, candidate);
        if (distance <= 0f || distance > MAX_DISTANCE_METERS) {
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

    @NonNull
    private String buildTargetKey(@Nullable DeliveryInfo info) {
        if (info == null) {
            return "";
        }
        if (info.getOrderId() != null) {
            return "ID-" + info.getOrderId();
        }
        if (!TextUtils.isEmpty(info.getOrderSn())) {
            return "SN-" + info.getOrderSn();
        }
        if (!TextUtils.isEmpty(info.getRouteNumber())) {
            return "ROUTE-" + info.getRouteNumber();
        }
        return String.valueOf(info.hashCode());
    }
}
