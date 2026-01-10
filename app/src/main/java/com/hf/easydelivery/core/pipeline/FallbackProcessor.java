package com.hf.easydelivery.core.pipeline;

import android.location.Location;
import android.os.SystemClock;

import androidx.annotation.NonNull;

public final class FallbackProcessor {
    private final FallbackProvider provider;

    public FallbackProcessor(@NonNull FallbackProvider provider) {
        this.provider = provider;
    }

    @NonNull
    public Location buildFallback(@NonNull Location rawLocation, long nowMillis) {
        Location fallback = provider.buildFallback(rawLocation, nowMillis);
        if (fallback == null) {
            fallback = new Location(rawLocation);
        }
        stampElapsedRealtimeNow(fallback);
        return fallback;
    }

    private void stampElapsedRealtimeNow(@NonNull Location loc) {
        try {
            loc.setElapsedRealtimeNanos(SystemClock.elapsedRealtimeNanos());
        } catch (Throwable ignore) {
            // Best-effort only.
        }
    }
}
