package com.hf.easydelivery.core.dispatch;

import android.location.Location;
import android.os.SystemClock;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.hf.easydelivery.core.facade.LocationUpdateListener;
import com.hf.easydelivery.core.facade.MovementState;
import com.hf.easydelivery.core.observer.LocationEventBus;
import com.hf.easydelivery.core.strategy.StrategyManager;

import java.util.Set;

public final class LocationDispatcher {
    private static final String TAG = "LocationDispatcher";

    private final Set<LocationUpdateListener> listeners;
    private final StrategyManager strategyManager;
    private final LocationEventBus eventBus;
    private long lastDispatchUptimeMs = 0L;
    private long lastDispatchElapsedMs = -1L;
    private long dispatchSeq = 0L;

    public LocationDispatcher(@NonNull Set<LocationUpdateListener> listeners,
            @Nullable StrategyManager strategyManager,
            @NonNull LocationEventBus eventBus) {
        this.listeners = listeners;
        this.strategyManager = strategyManager;
        this.eventBus = eventBus;
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
            return false;
        }
        lastDispatchElapsedMs = elapsedMs;
        lastDispatchUptimeMs = nowUptime;
        dispatchSeq++;
        eventBus.emitDispatch(dispatchSeq, listeners.size(), elapsedMs, String.valueOf(state));
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
