package com.hf.easydelivery.core.pipeline;

import android.location.Location;

import androidx.annotation.NonNull;

public final class FallbackBuilderProcessor implements LocationProcessor {
    private final FallbackProcessor fallbackProcessor;

    public FallbackBuilderProcessor(@NonNull FallbackProcessor fallbackProcessor) {
        this.fallbackProcessor = fallbackProcessor;
    }

    @Override
    public void process(ProcessingContext context) {
        if (!context.isPoorFix()) {
            return;
        }
        if (context.getOutputLocation() != null) {
            return;
        }
        Location fallback = fallbackProcessor.buildFallback(
                context.getRawLocation(),
                context.getNowMillis());
        context.setOutputLocation(fallback);
    }
}
