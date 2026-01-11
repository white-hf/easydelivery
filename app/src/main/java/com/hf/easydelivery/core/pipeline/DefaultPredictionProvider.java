package com.hf.easydelivery.core.pipeline;

import android.location.Location;
import android.os.SystemClock;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.hf.easydelivery.core.strategy.StrategyConfig;

public final class DefaultPredictionProvider implements PredictionProvider {
    private final HeadingProvider headingProvider;

    public DefaultPredictionProvider(@NonNull HeadingProvider headingProvider) {
        this.headingProvider = headingProvider;
    }

    @Override
    @Nullable
    public Location predict(@NonNull Location baseLocation, float speedMps, float bearingInput) {
        float heading = bearingInput;
        if (Float.isNaN(heading)) {
            heading = headingProvider.hasReliableHeading()
                    ? headingProvider.getHeadingDegrees()
                    : Float.NaN;
        }
        if (Float.isNaN(heading)) {
            return null;
        }
        if (speedMps < StrategyConfig.getMinPredictionSpeedMps()) {
            return null;
        }

        float horizon = Math.max(0.3f, Math.min(1.2f, speedMps * 0.2f));
        if (speedMps > 5f) {
            horizon = Math.min(1.5f, horizon + 0.3f);
        }
        double distance = speedMps * horizon;
        if (distance < 1.0) {
            return null;
        }

        double headingRad = Math.toRadians(heading);
        double latRad = Math.toRadians(baseLocation.getLatitude());
        double lonRad = Math.toRadians(baseLocation.getLongitude());
        double angularDistance = distance / StrategyConfig.getEarthRadiusMeters();
        double newLatRad = Math.asin(Math.sin(latRad) * Math.cos(angularDistance) +
                Math.cos(latRad) * Math.sin(angularDistance) * Math.cos(headingRad));
        double newLonRad = lonRad + Math.atan2(Math.sin(headingRad) * Math.sin(angularDistance) * Math.cos(latRad),
                Math.cos(angularDistance) - Math.sin(latRad) * Math.sin(newLatRad));
        double newLat = Math.toDegrees(newLatRad);
        double newLon = Math.toDegrees(newLonRad);

        Location predicted = new Location(baseLocation);
        predicted.setLatitude(newLat);
        predicted.setLongitude(newLon);
        predicted.setTime(System.currentTimeMillis());
        stampElapsedRealtimeNow(predicted);
        predicted.setBearing(heading);
        predicted.setSpeed(speedMps);
        return predicted;
    }

    private void stampElapsedRealtimeNow(@NonNull Location loc) {
        try {
            loc.setElapsedRealtimeNanos(SystemClock.elapsedRealtimeNanos());
        } catch (Throwable ignore) {
            // Best-effort only.
        }
    }
}
