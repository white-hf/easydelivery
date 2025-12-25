package com.hf.easydelivery.map.policy;

import com.hf.easydelivery.map.CameraFollowController;

public final class EcoFollowPolicy implements FollowPolicy {
    @Override
    public long getUiTickMs() {
        return 500L;
    }

    @Override
    public long getModeSwitchCooldownMs() {
        return 15000L;
    }

    @Override
    public long getListMinHoldMs() {
        return 6000L;
    }

    @Override
    public long getFollowMinHoldMs() {
        return 5000L;
    }

    @Override
    public long getListEntryStationaryMs() {
        return 12000L;
    }

    @Override
    public CameraFollowController.FollowConfig getFollowConfig() {
        CameraFollowController.FollowConfig cfg = new CameraFollowController.FollowConfig();
        cfg.stdIntervalMs = 1200L;
        cfg.stdDistM = 8.0f;
        cfg.stdHeadingDeg = 15f;
        cfg.stdDistWalkingM = 2.0f;
        cfg.basicIntervalMs = 1500L;
        cfg.basicDistM = 12.0f;
        cfg.basicHeadingDeg = 18f;
        return cfg;
    }
}
