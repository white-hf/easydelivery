package com.hf.easydelivery.core.dispatch;

import android.location.Location;

import androidx.annotation.Nullable;

import com.hf.easydelivery.core.strategy.StrategyConfig;

public final class DispatchGate {
    public boolean shouldDispatch(@Nullable Location newLocation,
            @Nullable Location lastSmoothedLocation,
            long prevUpdateTime,
            long lastDrivingUptimeMs,
            float speedMps,
            long nowUptimeMs,
            long newElapsedMs,
            long lastSmoothedElapsedMs) {
        if (newLocation == null || lastSmoothedLocation == null) {
            return true;
        }

        long timeDiff = prevUpdateTime <= 0L ? Long.MAX_VALUE : newLocation.getTime() - prevUpdateTime;
        float distance = lastSmoothedLocation.distanceTo(newLocation);

        if (timeDiff < 200L || distance < 0.8f) {
            return false;
        }
        if (newElapsedMs > 0 && newElapsedMs == lastSmoothedElapsedMs) {
            return false;
        }
        if (speedMps < 0.5f
                && (nowUptimeMs - lastDrivingUptimeMs) > StrategyConfig.getDrivingDowngradeGraceMs()
                && timeDiff < 2000L
                && distance < 2.0f) {
            return false;
        }
        return true;
    }
}
