package com.hf.easydelivery.core.pipeline;

import android.location.Location;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

public interface FallbackProvider {
    @Nullable
    Location buildFallback(@NonNull Location rawLocation, long nowMillis);
}
