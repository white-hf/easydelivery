package com.hf.easydelivery.map;

import android.location.Location;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.hf.easydelivery.core.facade.MovementState;
import com.hf.easydelivery.dao.DeliveryInfo;
import com.hf.easydelivery.map.config.ProfileManager;

import java.util.ArrayList;
import java.util.List;

/**
 * Facade that resolves display mode and presentation policy into one map result.
 * 对外统一输出地图展示模式和包裹展示策略，避免页面控制器堆叠条件分支。
 */
final class MapExperienceCoordinator {
    private final MapDisplayModeResolver modeResolver = new MapDisplayModeResolver();
    private final ParcelPresentationPolicy defaultParcelPresentationPolicy = new DefaultParcelPresentationPolicy();
    private final ParcelPresentationPolicy powerSaverBrowseParcelPresentationPolicy = new PowerSaverBrowseParcelPresentationPolicy();
    private final ClusterPolicy defaultClusterPolicy = new DefaultClusterPolicy();
    private final ClusterPolicy powerSaverBrowseClusterPolicy = new PowerSaverBrowseClusterPolicy();
    private final MarkerStylePolicy defaultMarkerStylePolicy = new DefaultMarkerStylePolicy();
    private final MarkerStylePolicy powerSaverBrowseMarkerStylePolicy = new PowerSaverBrowseMarkerStylePolicy();

    @NonNull
    MapExperience evaluate(@NonNull ProfileManager.AppProfile profile,
            @NonNull MovementState movementState,
            boolean navigationModeEnabled,
            boolean autoFollowPaused,
            boolean userInteracting,
            @Nullable Location driverLocation,
            @NonNull List<DeliveryInfo> deliveries,
            @Nullable DeliveryInfo currentPrimaryDelivery) {
        MapDisplayMode displayMode = modeResolver.resolve(
                profile,
                movementState,
                navigationModeEnabled,
                autoFollowPaused,
                userInteracting);
        DriverLocationSnapshot driverLocationSnapshot = DriverLocationSnapshot.from(driverLocation);

        ParcelPresentationPolicy parcelPolicy = displayMode == MapDisplayMode.POWER_SAVER_BROWSE
                ? powerSaverBrowseParcelPresentationPolicy
                : defaultParcelPresentationPolicy;
        ClusterPolicy clusterPolicy = displayMode == MapDisplayMode.POWER_SAVER_BROWSE
                ? powerSaverBrowseClusterPolicy
                : defaultClusterPolicy;
        MarkerStylePolicy markerStylePolicy = displayMode == MapDisplayMode.POWER_SAVER_BROWSE
                ? powerSaverBrowseMarkerStylePolicy
                : defaultMarkerStylePolicy;

        List<DeliveryInfo> visibleDeliveries = parcelPolicy.selectVisibleParcels(
                deliveries,
                driverLocationSnapshot,
                currentPrimaryDelivery);
        ClusterPolicyDecision clusterDecision = clusterPolicy.evaluate();

        if (visibleDeliveries.size() > clusterDecision.maxVisibleMarkers) {
            visibleDeliveries = new ArrayList<>(
                    visibleDeliveries.subList(0, clusterDecision.maxVisibleMarkers));
        }

        return new MapExperience(displayMode, visibleDeliveries, clusterDecision, markerStylePolicy);
    }
}
