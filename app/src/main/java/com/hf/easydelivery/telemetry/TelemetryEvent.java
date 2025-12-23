package com.hf.easydelivery.telemetry;

import android.os.SystemClock;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

public final class TelemetryEvent {
    public final TelemetryEventType type;
    public final String name;
    public final String reason;
    public final String state;
    public final float value;
    public final long tsUptimeMs;
    public final Map<String, String> tags;

    private TelemetryEvent(TelemetryEventType type,
                           String name,
                           String reason,
                           String state,
                           float value,
                           long tsUptimeMs,
                           Map<String, String> tags) {
        this.type = type;
        this.name = name;
        this.reason = reason;
        this.state = state;
        this.value = value;
        this.tsUptimeMs = tsUptimeMs;
        this.tags = tags == null ? Collections.emptyMap() : Collections.unmodifiableMap(tags);
    }

    public static TelemetryEvent counter(String name) {
        return new TelemetryEvent(TelemetryEventType.COUNTER, name, null, null, 1f,
                SystemClock.uptimeMillis(), null);
    }

    public static TelemetryEvent gauge(String name, float value) {
        return new TelemetryEvent(TelemetryEventType.GAUGE, name, null, null, value,
                SystemClock.uptimeMillis(), null);
    }

    public static TelemetryEvent event(String name, String reason, String state, float value) {
        return new TelemetryEvent(TelemetryEventType.EVENT, name, reason, state, value,
                SystemClock.uptimeMillis(), null);
    }

    public static TelemetryEvent state(String name, boolean active) {
        return new TelemetryEvent(TelemetryEventType.STATE, name, null, null, active ? 1f : 0f,
                SystemClock.uptimeMillis(), null);
    }

    public static TelemetryEvent stats(long windowMs, Map<String, String> tags) {
        return new TelemetryEvent(TelemetryEventType.STATS, "stats", null, null, windowMs,
                SystemClock.uptimeMillis(), tags);
    }

    public TelemetryEvent withTag(String key, String tagValue) {
        Map<String, String> copy = new HashMap<>(tags);
        copy.put(key, tagValue);
        return new TelemetryEvent(type, name, reason, state, this.value, tsUptimeMs, copy);
    }
}
