package com.hf.easydelivery.map;

import androidx.annotation.NonNull;

import com.hf.easydelivery.dao.DeliveryInfo;

import java.util.List;

final class MapExperience {
    final MapDisplayMode displayMode;
    final List<DeliveryInfo> visibleDeliveries;
    final ClusterPolicyDecision clusterDecision;
    final MarkerStylePolicy markerStylePolicy;

    MapExperience(@NonNull MapDisplayMode displayMode,
            @NonNull List<DeliveryInfo> visibleDeliveries,
            @NonNull ClusterPolicyDecision clusterDecision,
            @NonNull MarkerStylePolicy markerStylePolicy) {
        this.displayMode = displayMode;
        this.visibleDeliveries = visibleDeliveries;
        this.clusterDecision = clusterDecision;
        this.markerStylePolicy = markerStylePolicy;
    }
}
