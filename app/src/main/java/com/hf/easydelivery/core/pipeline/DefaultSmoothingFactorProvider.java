package com.hf.easydelivery.core.pipeline;

import com.hf.easydelivery.core.SmartLocationManager.MovementState;

public final class DefaultSmoothingFactorProvider implements SmoothingFactorProvider {
    @Override
    public double getSmoothingFactor(float speedMps, MovementState state) {
        switch (state) {
            case NORMAL_DRIVING:
            case SLOW_DRIVING:
                if (speedMps > 8f) {
                    return 0.8;
                }
                if (speedMps > 5f) {
                    return 0.65;
                }
                if (speedMps > 2f) {
                    return 0.55;
                }
                return 0.2;
            case WALKING:
                return 0.2;
            case STATIONARY:
            default:
                return 0.15;
        }
    }
}
