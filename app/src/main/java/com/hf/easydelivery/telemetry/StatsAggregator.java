package com.hf.easydelivery.telemetry;

import java.util.HashMap;
import java.util.Map;
import java.util.TreeMap;

final class StatsAggregator {
    private final long windowMs;
    private final TelemetrySink sink;
    private long windowStartMs = -1L;
    private final Map<String, Integer> counters = new HashMap<>();
    private final Map<String, Float> gauges = new HashMap<>();
    private final Map<String, Long> stateActiveStart = new HashMap<>();
    private final Map<String, Long> stateActiveMs = new HashMap<>();

    StatsAggregator(long windowMs, TelemetrySink sink) {
        this.windowMs = Math.max(1L, windowMs);
        this.sink = sink;
    }

    void onEvent(TelemetryEvent event) {
        if (event == null) {
            return;
        }
        long now = event.tsUptimeMs;
        if (windowStartMs < 0L) {
            windowStartMs = now;
        }
        switch (event.type) {
            case COUNTER:
                counters.merge(event.name, 1, Integer::sum);
                break;
            case GAUGE:
                gauges.put(event.name, event.value);
                break;
            case STATE:
                handleState(event.name, event.value > 0f, now);
                break;
            default:
                break;
        }
        if (now - windowStartMs >= windowMs) {
            flush(now);
        }
    }

    private void handleState(String name, boolean active, long now) {
        if (active) {
            if (!stateActiveStart.containsKey(name)) {
                stateActiveStart.put(name, now);
            }
            return;
        }
        Long start = stateActiveStart.remove(name);
        if (start != null) {
            long acc = stateActiveMs.getOrDefault(name, 0L);
            acc += Math.max(0L, now - start);
            stateActiveMs.put(name, acc);
        }
    }

    private void flush(long now) {
        long windowDurationMs = Math.max(1L, now - windowStartMs);
        Map<String, String> tags = new TreeMap<>();
        tags.put("windowMs", String.valueOf(windowDurationMs));
        for (Map.Entry<String, Integer> entry : counters.entrySet()) {
            tags.put(entry.getKey(), String.valueOf(entry.getValue()));
        }
        for (Map.Entry<String, Float> entry : gauges.entrySet()) {
            tags.put(entry.getKey(), String.format("%.2f", entry.getValue()));
        }
        if (!stateActiveStart.isEmpty()) {
            for (Map.Entry<String, Long> entry : stateActiveStart.entrySet()) {
                String name = entry.getKey();
                long acc = stateActiveMs.getOrDefault(name, 0L);
                long start = entry.getValue();
                acc += Math.max(0L, now - Math.max(windowStartMs, start));
                stateActiveMs.put(name, acc);
            }
        }
        for (Map.Entry<String, Long> entry : stateActiveMs.entrySet()) {
            String name = entry.getKey();
            long activeMs = entry.getValue();
            float pct = (activeMs * 100f) / windowDurationMs;
            tags.put("state." + name + ".activeMs", String.valueOf(activeMs));
            tags.put("state." + name + ".activePct", String.format("%.1f", pct));
        }
        sink.onEvent(TelemetryEvent.stats(windowDurationMs, tags));
        counters.clear();
        gauges.clear();
        stateActiveMs.clear();
        windowStartMs = now;
    }
}
