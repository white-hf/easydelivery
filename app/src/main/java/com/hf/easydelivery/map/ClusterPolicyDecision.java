package com.hf.easydelivery.map;

final class ClusterPolicyDecision {
    final boolean clusteringEnabled;
    final int maxVisibleMarkers;

    ClusterPolicyDecision(boolean clusteringEnabled, int maxVisibleMarkers) {
        this.clusteringEnabled = clusteringEnabled;
        this.maxVisibleMarkers = maxVisibleMarkers;
    }
}
