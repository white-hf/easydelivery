package com.hf.easydelivery.core.pipeline;

import androidx.annotation.NonNull;

import com.hf.easydelivery.core.SmartLocationManager.MovementState;
import com.hf.easydelivery.core.quality.FixQualityClassifier;

import java.util.function.Supplier;

public final class QualityGateProcessor implements LocationProcessor {
    private final FixQualityClassifier classifier;
    private final Supplier<MovementState> stateProvider;

    public QualityGateProcessor(@NonNull FixQualityClassifier classifier,
            @NonNull Supplier<MovementState> stateProvider) {
        this.classifier = classifier;
        this.stateProvider = stateProvider;
    }

    @Override
    public void process(ProcessingContext context) {
        FixQualityClassifier.Result result = classifier.classify(
                context.getRawLocation(),
                context.getReferenceLocation(),
                context.getSpeedMps(),
                stateProvider.get(),
                context.getAgeMs(),
                context.getStaleThresholdMs());
        context.setQuality(result.staleFix,
                result.goodFix,
                result.okFix,
                result.poorFix,
                result.goodThreshold,
                result.okThreshold);
    }
}
