package com.hf.easydelivery.core.policy;

import com.hf.easydelivery.core.SmartLocationManager;

public final class LocationPolicyContext {
    public final SmartLocationManager.MovementState state;
    public final boolean inBurst;
    public final boolean deliveringIdle;
    public final boolean uiFollowActive;
    public final boolean movingLikely;

    public LocationPolicyContext(SmartLocationManager.MovementState state,
                                 boolean inBurst,
                                 boolean deliveringIdle,
                                 boolean uiFollowActive,
                                 boolean movingLikely) {
        this.state = state;
        this.inBurst = inBurst;
        this.deliveringIdle = deliveringIdle;
        this.uiFollowActive = uiFollowActive;
        this.movingLikely = movingLikely;
    }
}
