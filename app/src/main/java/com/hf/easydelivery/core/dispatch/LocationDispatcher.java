package com.hf.easydelivery.core.dispatch;

import android.location.Location;
import android.os.SystemClock;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.hf.courierservice.apihelper.FileLog;
import com.hf.easydelivery.core.SmartLocationManager.LocationUpdateListener;
import com.hf.easydelivery.core.SmartLocationManager.MovementState;
import com.hf.easydelivery.core.strategy.StrategyManager;
import com.hf.easydelivery.telemetry.Telemetry;

import java.util.Set;

public final class LocationDispatcher {
    private static final String TAG = "LocationDispatcher";

    private final Set<LocationUpdateListener> listeners;
    private final StrategyManager strategyManager;
    private long lastDispatchUptimeMs = 0L;
    private long lastDispatchElapsedMs = -1L;
    private long dispatchSeq = 0L;

    public LocationDispatcher(@NonNull Set<LocationUpdateListener> listeners,
            @Nullable StrategyManager strategyManager) {
        this.listeners = listeners;
        this.strategyManager = strategyManager;
    }

    public boolean dispatch(@NonNull Location loc,
            @NonNull MovementState state,
            long minIntervalMs) {
        if (listeners.isEmpty()) {
            return false;
        }
        long nowUptime = SystemClock.uptimeMillis();
        if (nowUptime - lastDispatchUptimeMs < minIntervalMs) {
            return false;
        }
        long elapsedMs = getElapsedRealtimeMsSafe(loc);
        if (elapsedMs > 0 && elapsedMs == lastDispatchElapsedMs) {
            FileLog.getInstance().debug(TAG,
                    String.format("dispatch dedup: same elapsedMs=%d skip", elapsedMs));
            return false;
        }
        lastDispatchElapsedMs = elapsedMs;
        lastDispatchUptimeMs = nowUptime;
        dispatchSeq++;
        FileLog.getInstance().debug(TAG, String.format(
                "dispatch #%d listeners=%d elapsedMs=%d mv=%s",
                dispatchSeq,
                listeners.size(),
                getElapsedRealtimeMsSafe(loc),
                state));
        Telemetry.counter("dispatch");
        if (strategyManager != null) {
            strategyManager.onLocationDispatched(loc);
        }
        for (LocationUpdateListener l : listeners) {
            try {
                l.onLocationUpdate(loc, state);
            } catch (Exception ignored) {
            }
        }
        return true;
    }

    public long getLastDispatchUptimeMs() {
        return lastDispatchUptimeMs;
    }

    private long getElapsedRealtimeMsSafe(@NonNull Location loc) {
        try {
            return loc.getElapsedRealtimeNanos() / 1_000_000L;
        } catch (Throwable t) {
            return -1L;
        }
    }
}
