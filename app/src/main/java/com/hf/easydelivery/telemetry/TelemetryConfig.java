package com.hf.easydelivery.telemetry;

public final class TelemetryConfig {
    public final boolean enabled;
    public final boolean enableStats;
    public final boolean enableLogs;
    public final int sampleRate;
    public final long windowMs;
    public final boolean debugOnly;

    public TelemetryConfig(boolean enabled,
                           boolean enableStats,
                           boolean enableLogs,
                           int sampleRate,
                           long windowMs,
                           boolean debugOnly) {
        this.enabled = enabled;
        this.enableStats = enableStats;
        this.enableLogs = enableLogs;
        this.sampleRate = clampRate(sampleRate);
        this.windowMs = Math.max(1L, windowMs);
        this.debugOnly = debugOnly;
    }

    public static TelemetryConfig disabled() {
        return new TelemetryConfig(false, false, false, 0, 5000L, false);
    }

    public static TelemetryConfig debugDefaults() {
        return new TelemetryConfig(true, true, false, 100, 5000L, true);
    }

    private static int clampRate(int rate) {
        if (rate < 0) {
            return 0;
        }
        if (rate > 100) {
            return 100;
        }
        return rate;
    }
}
