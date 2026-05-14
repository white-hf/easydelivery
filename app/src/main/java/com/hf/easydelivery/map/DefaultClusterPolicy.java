package com.hf.easydelivery.map;

import androidx.annotation.NonNull;

final class DefaultClusterPolicy implements ClusterPolicy {
    @NonNull
    @Override
    public ClusterPolicyDecision evaluate() {
        return new ClusterPolicyDecision(true, Integer.MAX_VALUE);
    }
}
