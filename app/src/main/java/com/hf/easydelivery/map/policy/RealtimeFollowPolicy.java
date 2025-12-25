package com.hf.easydelivery.map.policy;

import com.hf.easydelivery.map.CameraFollowController;

public final class RealtimeFollowPolicy implements FollowPolicy {
    @Override
    public long getUiTickMs() {
        return 250L;
    }

    @Override
    public long getModeSwitchCooldownMs() {
        return 8000L;
    }

    @Override
    public long getListMinHoldMs() {
        return 4000L;
    }

    @Override
    public long getFollowMinHoldMs() {
        return 3000L;
    }

    @Override
    public long getListEntryStationaryMs() {
        return 6000L;
    }

    @Override
    public CameraFollowController.FollowConfig getFollowConfig() {
        CameraFollowController.FollowConfig cfg = new CameraFollowController.FollowConfig();
        cfg.stdIntervalMs = 600L;
        cfg.stdDistM = 3.0f;
        cfg.stdHeadingDeg = 8f;
        cfg.stdDistWalkingM = 1.0f;
        cfg.basicIntervalMs = 800L;
        cfg.basicDistM = 6.0f;
        cfg.basicHeadingDeg = 12f;
        return cfg;
    }
}
