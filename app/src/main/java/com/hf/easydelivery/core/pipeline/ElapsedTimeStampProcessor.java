package com.hf.easydelivery.core.pipeline;

import android.location.Location;
import android.os.SystemClock;

public final class ElapsedTimeStampProcessor implements LocationProcessor {
    @Override
    public void process(ProcessingContext context) {
        Location out = context.getOutputLocation();
        if (out == null) {
            return;
        }
        try {
            out.setElapsedRealtimeNanos(SystemClock.elapsedRealtimeNanos());
        } catch (Throwable ignore) {
            // Best-effort only.
        }
    }
}
