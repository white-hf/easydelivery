package com.hf.easydelivery.telemetry;

public interface TelemetrySink {
    void onEvent(TelemetryEvent event);
}
