package com.hf.easydelivery.core.policy;

import com.hf.easydelivery.core.SmartLocationManager.MovementState;

public interface PolicyContextStateProvider {
    MovementState getMovementState();

    boolean isInBurstMode();

    boolean isDeliveringAndIdle();

    boolean isUiFollowActive();

    boolean isMovingFlag();
}
