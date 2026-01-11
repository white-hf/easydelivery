package com.hf.easydelivery.core.pipeline;

public final class StaleFilterProcessor implements LocationProcessor {
    @Override
    public void process(ProcessingContext context) {
        boolean stale = context.getAgeMs() > context.getStaleThresholdMs();
        if (stale) {
            context.setQuality(true, false, false, true, 0f, 0f);
        }
    }
}
