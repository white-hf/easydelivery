package com.hf.easydelivery.core.policy;

public final class LocationRequestParams {
    public final long intervalMs;
    public final long minIntervalMs;
    public final int priority;
    public final float minDistanceMeters;
    public final long maxUpdateDelayMs;
    public final long minDispatchIntervalMs;

    public LocationRequestParams(long intervalMs,
                                 long minIntervalMs,
                                 int priority,
                                 float minDistanceMeters,
                                 long maxUpdateDelayMs,
                                 long minDispatchIntervalMs) {
        this.intervalMs = intervalMs;
        this.minIntervalMs = minIntervalMs;
        this.priority = priority;
        this.minDistanceMeters = minDistanceMeters;
        this.maxUpdateDelayMs = maxUpdateDelayMs;
        this.minDispatchIntervalMs = minDispatchIntervalMs;
    }
}
