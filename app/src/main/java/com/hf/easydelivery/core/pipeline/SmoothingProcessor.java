package com.hf.easydelivery.core.pipeline;

import android.location.Location;
import android.os.SystemClock;

import androidx.annotation.NonNull;

public final class SmoothingProcessor implements LocationProcessor {
    @Override
    public void process(ProcessingContext context) {
        if (context.isPoorFix()) {
            return;
        }
        Location lastSmoothed = context.getLastSmoothedLocation();
        Location raw = context.getRawLocation();
        if (lastSmoothed == null) {
            context.setOutputLocation(raw);
            return;
        }
        double alpha = context.getSmoothingFactor();
        double lat = lastSmoothed.getLatitude() + alpha * (raw.getLatitude() - lastSmoothed.getLatitude());
        double lon = lastSmoothed.getLongitude() + alpha * (raw.getLongitude() - lastSmoothed.getLongitude());
        Location out = new Location(raw);
        out.setLatitude(lat);
        out.setLongitude(lon);
        stampElapsedRealtimeNow(out);
        context.setOutputLocation(out);
    }

    private void stampElapsedRealtimeNow(@NonNull Location loc) {
        try {
            loc.setElapsedRealtimeNanos(SystemClock.elapsedRealtimeNanos());
        } catch (Throwable ignore) {
            // Keep best-effort only.
        }
    }
}
