package com.hf.easydelivery.telemetry;

import java.util.Random;

final class TelemetryRouter {
    private final TelemetryConfig config;
    private final TelemetrySink sink;
    private final StatsAggregator aggregator;
    private final Random random = new Random();

    static TelemetryRouter noop() {
        return new TelemetryRouter(TelemetryConfig.disabled(), new NoopSink());
    }

    TelemetryRouter(TelemetryConfig config) {
        this(config, null);
    }

    TelemetryRouter(TelemetryConfig config, TelemetrySink sinkOverride) {
        this.config = config == null ? TelemetryConfig.disabled() : config;
        TelemetrySink resolved = sinkOverride;
        if (resolved == null) {
            resolved = (this.config.enableLogs || this.config.enableStats) ? new FileSink() : new NoopSink();
        }
        this.sink = resolved;
        this.aggregator = this.config.enableStats ? new StatsAggregator(this.config.windowMs, this.sink) : null;
    }

    void onEvent(TelemetryEvent event) {
        if (!config.enabled) {
            return;
        }
        if (!sampled()) {
            return;
        }
        if (aggregator != null) {
            aggregator.onEvent(event);
        }
        if (config.enableLogs && event != null && event.type != TelemetryEventType.STATS) {
            sink.onEvent(event);
        }
    }

    private boolean sampled() {
        if (config.sampleRate >= 100) {
            return true;
        }
        if (config.sampleRate <= 0) {
            return false;
        }
        return random.nextInt(100) < config.sampleRate;
    }
}
