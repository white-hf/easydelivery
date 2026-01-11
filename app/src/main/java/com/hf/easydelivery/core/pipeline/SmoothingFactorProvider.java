package com.hf.easydelivery.core.pipeline;

import com.hf.easydelivery.core.SmartLocationManager.MovementState;

public interface SmoothingFactorProvider {
    double getSmoothingFactor(float speedMps, MovementState state);
}
