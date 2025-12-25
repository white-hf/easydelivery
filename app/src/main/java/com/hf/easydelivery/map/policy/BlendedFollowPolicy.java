package com.hf.easydelivery.map.policy;

import com.hf.easydelivery.map.CameraFollowController;

public final class BlendedFollowPolicy implements FollowPolicy {
    private final FollowPolicy realtime = new RealtimeFollowPolicy();
    private final FollowPolicy eco = new EcoFollowPolicy();
    private final float t;

    public BlendedFollowPolicy(float t) {
        this.t = clamp01(t);
    }

    @Override
    public long getUiTickMs() {
        return lerpLong(realtime.getUiTickMs(), eco.getUiTickMs(), t);
    }

    @Override
    public long getModeSwitchCooldownMs() {
        return lerpLong(realtime.getModeSwitchCooldownMs(), eco.getModeSwitchCooldownMs(), t);
    }

    @Override
    public long getListMinHoldMs() {
        return lerpLong(realtime.getListMinHoldMs(), eco.getListMinHoldMs(), t);
    }

    @Override
    public long getFollowMinHoldMs() {
        return lerpLong(realtime.getFollowMinHoldMs(), eco.getFollowMinHoldMs(), t);
    }

    @Override
    public long getListEntryStationaryMs() {
        return lerpLong(realtime.getListEntryStationaryMs(), eco.getListEntryStationaryMs(), t);
    }

    @Override
    public CameraFollowController.FollowConfig getFollowConfig() {
        CameraFollowController.FollowConfig a = realtime.getFollowConfig();
        CameraFollowController.FollowConfig b = eco.getFollowConfig();
        CameraFollowController.FollowConfig out = new CameraFollowController.FollowConfig();
        out.stdIntervalMs = lerpLong(a.stdIntervalMs, b.stdIntervalMs, t);
        out.stdDistM = lerpFloat(a.stdDistM, b.stdDistM, t);
        out.stdHeadingDeg = lerpFloat(a.stdHeadingDeg, b.stdHeadingDeg, t);
        out.stdDistWalkingM = lerpFloat(a.stdDistWalkingM, b.stdDistWalkingM, t);
        out.basicIntervalMs = lerpLong(a.basicIntervalMs, b.basicIntervalMs, t);
        out.basicDistM = lerpFloat(a.basicDistM, b.basicDistM, t);
        out.basicHeadingDeg = lerpFloat(a.basicHeadingDeg, b.basicHeadingDeg, t);
        return out;
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
