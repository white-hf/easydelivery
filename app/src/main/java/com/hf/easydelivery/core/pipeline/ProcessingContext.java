package com.hf.easydelivery.core.pipeline;

import android.location.Location;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

public final class ProcessingContext {
    private final Location rawLocation;
    private final Location lastSmoothedLocation;
    private final float speedMps;
    private final double smoothingFactor;
    private final float bearingInput;

    private Location outputLocation;
    private Location predictedLocation;

    public ProcessingContext(@NonNull Location rawLocation,
            @Nullable Location lastSmoothedLocation,
            float speedMps,
            double smoothingFactor,
            float bearingInput) {
        this.rawLocation = rawLocation;
        this.lastSmoothedLocation = lastSmoothedLocation;
        this.speedMps = speedMps;
        this.smoothingFactor = smoothingFactor;
        this.bearingInput = bearingInput;
    }

    @NonNull
    public Location getRawLocation() {
        return rawLocation;
    }

    @Nullable
    public Location getLastSmoothedLocation() {
        return lastSmoothedLocation;
    }

    public float getSpeedMps() {
        return speedMps;
    }

    public double getSmoothingFactor() {
        return smoothingFactor;
    }

    public float getBearingInput() {
        return bearingInput;
    }

    @Nullable
    public Location getOutputLocation() {
        return outputLocation;
    }

    public void setOutputLocation(@NonNull Location outputLocation) {
        this.outputLocation = outputLocation;
    }

    @Nullable
    public Location getPredictedLocation() {
        return predictedLocation;
    }

    public void setPredictedLocation(@Nullable Location predictedLocation) {
        this.predictedLocation = predictedLocation;
    }
}
