package com.hf.easydelivery.telemetry;

final class NoopSink implements TelemetrySink {
    @Override
    public void onEvent(TelemetryEvent event) {
        // no-op
    }
}
