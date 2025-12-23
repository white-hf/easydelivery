package com.hf.easydelivery.telemetry;

import com.hf.courierservice.apihelper.FileLog;

import java.util.Map;
import java.util.TreeMap;

final class FileSink implements TelemetrySink {
    private static final String TAG = "Telemetry";

    @Override
    public void onEvent(TelemetryEvent event) {
        if (event == null) {
            return;
        }
        if (event.type == TelemetryEventType.STATS) {
            logStats(event);
            return;
        }
        logEvent(event);
    }

    private void logStats(TelemetryEvent event) {
        Map<String, String> tags = event.tags == null ? new TreeMap<>() : new TreeMap<>(event.tags);
        StringBuilder sb = new StringBuilder();
        sb.append("stats(").append((long) event.value / 1000L).append("s): ");
        boolean first = true;
        for (Map.Entry<String, String> entry : tags.entrySet()) {
            if (!first) {
                sb.append(' ');
            }
            sb.append(entry.getKey()).append('=').append(entry.getValue());
            first = false;
        }
        FileLog.getInstance().debug(TAG, sb.toString());
    }

    private void logEvent(TelemetryEvent event) {
        StringBuilder sb = new StringBuilder();
        sb.append(event.type).append(' ').append(event.name);
        if (event.reason != null) {
            sb.append(" reason=").append(event.reason);
        }
        if (event.state != null) {
            sb.append(" state=").append(event.state);
        }
        if (event.value != 0f) {
            sb.append(" value=").append(event.value);
        }
        if (event.tags != null && !event.tags.isEmpty()) {
            sb.append(" tags=").append(event.tags);
        }
        FileLog.getInstance().debug(TAG, sb.toString());
    }
}
