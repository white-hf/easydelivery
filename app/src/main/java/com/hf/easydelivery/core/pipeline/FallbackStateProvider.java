package com.hf.easydelivery.core.pipeline;

import android.location.Location;

import androidx.annotation.Nullable;

public interface FallbackStateProvider {
    @Nullable
    Location getLastPredictedLocation();

    @Nullable
    Location getLastDispatchedLocation();

    @Nullable
    Location getLastSmoothedLocation();

    @Nullable
    Location getLastGoodLocation();

    float getSpeedMps();
}
