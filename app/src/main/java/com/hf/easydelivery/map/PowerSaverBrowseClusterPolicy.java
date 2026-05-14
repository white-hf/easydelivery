package com.hf.easydelivery.map;

import androidx.annotation.NonNull;

final class PowerSaverBrowseClusterPolicy implements ClusterPolicy {
    @NonNull
    @Override
    public ClusterPolicyDecision evaluate() {
        return new ClusterPolicyDecision(false, PowerSaverBrowseParcelPresentationPolicy.MAX_VISIBLE_MARKERS);
    }
}
