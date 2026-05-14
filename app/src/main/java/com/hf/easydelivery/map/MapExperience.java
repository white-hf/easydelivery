package com.hf.easydelivery.map;

import androidx.annotation.NonNull;

import com.hf.easydelivery.dao.DeliveryInfo;

import java.util.List;

final class MapExperience {
    final MapDisplayMode displayMode;
    final List<DeliveryInfo> visibleDeliveries;
    final ClusterPolicyDecision clusterDecision;

    MapExperience(@NonNull MapDisplayMode displayMode,
            @NonNull List<DeliveryInfo> visibleDeliveries,
            @NonNull ClusterPolicyDecision clusterDecision) {
        this.displayMode = displayMode;
        this.visibleDeliveries = visibleDeliveries;
        this.clusterDecision = clusterDecision;
    }
}
