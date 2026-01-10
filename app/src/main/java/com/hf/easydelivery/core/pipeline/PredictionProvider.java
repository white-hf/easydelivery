package com.hf.easydelivery.core.pipeline;

import android.location.Location;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

public interface PredictionProvider {
    @Nullable
    Location predict(@NonNull Location baseLocation, float speedMps, float bearingInput);
}
