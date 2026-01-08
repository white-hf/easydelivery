package com.hf.easydelivery.core.strategy;

import com.hf.easydelivery.core.SmartLocationManager;

public final class LocationContext {
    public final SmartLocationManager.MovementState state;
    public final boolean foreground;
    public final float distanceSinceLastDispatchM;
    public final boolean inBurst;

    public LocationContext(SmartLocationManager.MovementState state,
            boolean foreground,
            float distanceSinceLastDispatchM,
            boolean inBurst) {
        this.state = state;
        this.foreground = foreground;
        this.distanceSinceLastDispatchM = distanceSinceLastDispatchM;
        this.inBurst = inBurst;
    }
}
