package com.hf.easydelivery.core.observer;

import androidx.annotation.Nullable;

import com.hf.easydelivery.telemetry.Telemetry;

public final class TelemetryLocationObserver implements LocationEventObserver {
    @Override
    public void onBoostRequested(@Nullable String reason, boolean force) {
        String reasonKey = reason == null ? "unknown" : reason;
        Telemetry.counter("boost.request");
        Telemetry.counter("boost." + reasonKey);
        if (force) {
            Telemetry.counter("boost.force");
        }
    }

    @Override
    public void onBurstEnter() {
        Telemetry.counter("burst.enter");
        Telemetry.state("burst", true);
    }

    @Override
    public void onBurstExit() {
        Telemetry.counter("burst.exit");
        Telemetry.state("burst", false);
    }

    @Override
    public void onLocationResult() {
        Telemetry.counter("onLocationResult");
    }

    @Override
    public void onDispatch(long seq, int listenerCount, long elapsedMs, String state) {
        Telemetry.counter("dispatch");
    }
}
