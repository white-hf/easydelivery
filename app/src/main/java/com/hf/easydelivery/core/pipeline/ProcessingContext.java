package com.hf.easydelivery.core.pipeline;

import android.location.Location;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

public final class ProcessingContext {
    private final Location rawLocation;
    private final Location referenceLocation;
    private final Location lastSmoothedLocation;
    private final float speedMps;
    private final double smoothingFactor;
    private final float bearingInput;
    private final long nowMillis;
    private final long ageMs;
    private final long staleThresholdMs;

    private Location outputLocation;
    private Location predictedLocation;
    private boolean staleFix;
    private boolean goodFix;
    private boolean okFix;
    private boolean poorFix;
    private float goodThreshold;
    private float okThreshold;

    public ProcessingContext(@NonNull Location rawLocation,
            @Nullable Location referenceLocation,
            @Nullable Location lastSmoothedLocation,
            float speedMps,
            double smoothingFactor,
            float bearingInput,
            long nowMillis,
            long ageMs,
            long staleThresholdMs) {
        this.rawLocation = rawLocation;
        this.referenceLocation = referenceLocation;
        this.lastSmoothedLocation = lastSmoothedLocation;
        this.speedMps = speedMps;
        this.smoothingFactor = smoothingFactor;
        this.bearingInput = bearingInput;
        this.nowMillis = nowMillis;
        this.ageMs = ageMs;
        this.staleThresholdMs = staleThresholdMs;
    }

    @NonNull
    public Location getRawLocation() {
        return rawLocation;
    }

    @Nullable
    public Location getLastSmoothedLocation() {
        return lastSmoothedLocation;
    }

    @Nullable
    public Location getReferenceLocation() {
        return referenceLocation;
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

    public long getNowMillis() {
        return nowMillis;
    }

    public long getAgeMs() {
        return ageMs;
    }

    public long getStaleThresholdMs() {
        return staleThresholdMs;
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

    public boolean isStaleFix() {
        return staleFix;
    }

    public boolean isGoodFix() {
        return goodFix;
    }

    public boolean isOkFix() {
        return okFix;
    }

    public boolean isPoorFix() {
        return poorFix;
    }

    public float getGoodThreshold() {
        return goodThreshold;
    }

    public float getOkThreshold() {
        return okThreshold;
    }

    public void setQuality(boolean staleFix,
            boolean goodFix,
            boolean okFix,
            boolean poorFix,
            float goodThreshold,
            float okThreshold) {
        this.staleFix = staleFix;
        this.goodFix = goodFix;
        this.okFix = okFix;
        this.poorFix = poorFix;
        this.goodThreshold = goodThreshold;
        this.okThreshold = okThreshold;
    }
}
