package com.hf.easydelivery.telemetry;

public final class Telemetry {
    private static volatile TelemetryRouter router = TelemetryRouter.noop();

    private Telemetry() {
    }

    public static void init(TelemetryConfig config) {
        router = new TelemetryRouter(config);
    }

    public static void emit(TelemetryEvent event) {
        router.onEvent(event);
    }

    public static void counter(String name) {
        router.onEvent(TelemetryEvent.counter(name));
    }

    public static void gauge(String name, float value) {
        router.onEvent(TelemetryEvent.gauge(name, value));
    }

    public static void state(String name, boolean active) {
        router.onEvent(TelemetryEvent.state(name, active));
    }
}
