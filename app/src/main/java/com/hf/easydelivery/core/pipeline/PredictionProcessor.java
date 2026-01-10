package com.hf.easydelivery.core.pipeline;

import android.location.Location;

import androidx.annotation.NonNull;

public final class PredictionProcessor implements LocationProcessor {
    private final PredictionProvider provider;

    public PredictionProcessor(@NonNull PredictionProvider provider) {
        this.provider = provider;
    }

    @Override
    public void process(ProcessingContext context) {
        Location raw = context.getRawLocation();
        Location predicted = provider.predict(raw, context.getSpeedMps(), context.getBearingInput());
        context.setPredictedLocation(predicted);
    }
}
