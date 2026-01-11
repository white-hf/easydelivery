package com.hf.easydelivery.core.pipeline;

import android.location.Location;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

public final class DefaultFallbackProvider implements FallbackProvider {
    private static final long RECENT_PREDICTION_WINDOW_MS = 2_000L;

    private final FallbackStateProvider stateProvider;
    private final PredictionProvider predictionProvider;

    public DefaultFallbackProvider(@NonNull FallbackStateProvider stateProvider,
            @NonNull PredictionProvider predictionProvider) {
        this.stateProvider = stateProvider;
        this.predictionProvider = predictionProvider;
    }

    @Override
    @Nullable
    public Location buildFallback(@NonNull Location rawLocation, long nowMillis) {
        Location lastPredicted = stateProvider.getLastPredictedLocation();
        if (lastPredicted != null) {
            long age = nowMillis - lastPredicted.getTime();
            if (age >= 0 && age <= RECENT_PREDICTION_WINDOW_MS) {
                Location p = new Location(lastPredicted);
                p.setTime(nowMillis);
                return p;
            }
        }

        Location base = stateProvider.getLastDispatchedLocation();
        if (base == null) {
            base = stateProvider.getLastSmoothedLocation();
        }
        if (base == null) {
            base = stateProvider.getLastGoodLocation();
        }
        if (base == null) {
            return null;
        }

        float bearingInput = rawLocation.hasBearing() ? rawLocation.getBearing() : Float.NaN;
        Location predicted = predictionProvider.predict(base, stateProvider.getSpeedMps(), bearingInput);
        if (predicted != null) {
            predicted.setTime(nowMillis);
            return predicted;
        }

        Location reuse = new Location(base);
        reuse.setTime(nowMillis);
        return reuse;
    }
}
