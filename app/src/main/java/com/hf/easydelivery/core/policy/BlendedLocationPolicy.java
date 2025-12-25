package com.hf.easydelivery.core.policy;

public final class BlendedLocationPolicy implements LocationPolicy {
    private final LocationPolicy realtime = new RealtimeLocationPolicy();
    private final LocationPolicy eco = new EcoLocationPolicy();
    private final float t;

    public BlendedLocationPolicy(float t) {
        this.t = clamp01(t);
    }

    @Override
    public LocationRequestParams getRequestParams(LocationPolicyContext context) {
        LocationRequestParams a = realtime.getRequestParams(context);
        LocationRequestParams b = eco.getRequestParams(context);
        int priority = (t >= 0.5f) ? b.priority : a.priority;
        return new LocationRequestParams(
                lerpLong(a.intervalMs, b.intervalMs, t),
                lerpLong(a.minIntervalMs, b.minIntervalMs, t),
                priority,
                lerpFloat(a.minDistanceMeters, b.minDistanceMeters, t),
                lerpLong(a.maxUpdateDelayMs, b.maxUpdateDelayMs, t),
                lerpLong(a.minDispatchIntervalMs, b.minDispatchIntervalMs, t));
    }

    private static float clamp01(float v) {
        if (v < 0f) return 0f;
        if (v > 1f) return 1f;
        return v;
    }

    private static long lerpLong(long a, long b, float t) {
        return Math.round(a + (b - a) * t);
    }

    private static float lerpFloat(float a, float b, float t) {
        return a + (b - a) * t;
    }
}
