package com.hf.easydelivery.core.engine;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.app.ActivityCompat;

import com.google.android.gms.location.LocationCallback;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.tasks.Task;
import com.hf.courierservice.apihelper.FileLog;
import com.hf.easydelivery.core.source.LocationSource;

public final class RequestScheduler {
    private static final String TAG = "RequestScheduler";
    private static final long REQUEST_RECONFIG_DEBOUNCE_MS = 3000L;

    private final Context context;
    private final LocationSource source;
    private final Handler handler;

    private long lastRequestedIntervalMs = -1L;
    private long lastRequestedMinIntervalMs = -1L;
    private int lastRequestedPriority = -1;
    private float lastRequestedMinDistanceM = -1f;
    private long lastRequestedMaxDelayMs = -1L;
    private long lastRequestUptimeMs = 0L;

    private long pendingRequestedIntervalMs = -1L;
    private long pendingRequestedMinIntervalMs = -1L;
    private int pendingRequestedPriority = -1;
    private float pendingRequestedMinDistanceM = -1f;
    private long pendingRequestedMaxDelayMs = -1L;
    private boolean hasPendingRequest = false;
    private Runnable pendingReconfigure;

    public RequestScheduler(@NonNull Context context,
            @NonNull LocationSource source,
            @NonNull Handler handler) {
        this.context = context.getApplicationContext();
        this.source = source;
        this.handler = handler;
    }

    public void applyRequest(long interval,
            long minInterval,
            int priority,
            float minDistanceMeters,
            long maxUpdateDelayMs,
            @Nullable LocationCallback callback,
            boolean inBurstMode) {
        if (ActivityCompat.checkSelfPermission(context,
                Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            return;
        }
        if (callback == null) {
            FileLog.getInstance().warning(TAG, "requestLocationUpdates skipped: locationCallback not ready");
            return;
        }
        FileLog.getInstance().debug(TAG,
                String.format(
                        "requestLocationUpdates call: interval=%dms minInterval=%dms maxDelay=%dms minDistance=%.1fm priority=%d",
                        interval, minInterval, maxUpdateDelayMs, minDistanceMeters, priority));

        if (interval == lastRequestedIntervalMs
                && minInterval == lastRequestedMinIntervalMs
                && priority == lastRequestedPriority
                && minDistanceMeters == lastRequestedMinDistanceM
                && maxUpdateDelayMs == lastRequestedMaxDelayMs) {
            return;
        }
        if (hasPendingRequest
                && interval == pendingRequestedIntervalMs
                && minInterval == pendingRequestedMinIntervalMs
                && priority == pendingRequestedPriority
                && minDistanceMeters == pendingRequestedMinDistanceM
                && maxUpdateDelayMs == pendingRequestedMaxDelayMs) {
            return;
        }

        pendingRequestedIntervalMs = interval;
        pendingRequestedMinIntervalMs = minInterval;
        pendingRequestedPriority = priority;
        pendingRequestedMinDistanceM = minDistanceMeters;
        pendingRequestedMaxDelayMs = maxUpdateDelayMs;
        hasPendingRequest = true;

        long now = SystemClock.elapsedRealtime();
        if (!inBurstMode && lastRequestUptimeMs > 0
                && (now - lastRequestUptimeMs) < REQUEST_RECONFIG_DEBOUNCE_MS) {
            if (pendingReconfigure != null) {
                handler.removeCallbacks(pendingReconfigure);
            }
            pendingReconfigure = () -> {
                pendingReconfigure = null;
                doRequest(interval, minInterval, priority, minDistanceMeters, maxUpdateDelayMs, callback);
            };
            handler.postDelayed(pendingReconfigure, REQUEST_RECONFIG_DEBOUNCE_MS);
            return;
        }
        doRequest(interval, minInterval, priority, minDistanceMeters, maxUpdateDelayMs, callback);
    }

    private void doRequest(long interval,
            long minInterval,
            int priority,
            float minDistanceMeters,
            long maxUpdateDelayMs,
            @NonNull LocationCallback callback) {
        try {
            source.removeLocationUpdates(callback);
        } catch (Exception e) {
            FileLog.getInstance().warning(TAG, "removeLocationUpdates failed, retrying request", e.getMessage());
        }

        LocationRequest locationRequest = new LocationRequest.Builder(priority)
                .setIntervalMillis(interval)
                .setMinUpdateIntervalMillis(minInterval)
                .setMaxUpdateDelayMillis(maxUpdateDelayMs)
                .setMinUpdateDistanceMeters(minDistanceMeters)
                .setWaitForAccurateLocation(false)
                .build();

        Task<Void> task = source.requestLocationUpdates(locationRequest, callback, Looper.getMainLooper());
        task.addOnSuccessListener(aVoid -> {
            lastRequestedIntervalMs = interval;
            lastRequestedMinIntervalMs = minInterval;
            lastRequestedPriority = priority;
            lastRequestedMinDistanceM = minDistanceMeters;
            lastRequestedMaxDelayMs = maxUpdateDelayMs;
            if (hasPendingRequest
                    && pendingRequestedIntervalMs == interval
                    && pendingRequestedMinIntervalMs == minInterval
                    && pendingRequestedPriority == priority
                    && pendingRequestedMinDistanceM == minDistanceMeters
                    && pendingRequestedMaxDelayMs == maxUpdateDelayMs) {
                hasPendingRequest = false;
            }
            FileLog.getInstance().debug(TAG,
                    String.format(
                            "requestLocationUpdates success: interval=%dms minInterval=%dms maxDelay=%dms minDistance=%.1fm priority=%d",
                            interval,
                            minInterval,
                            maxUpdateDelayMs,
                            minDistanceMeters,
                            priority));
        }).addOnFailureListener(e -> {
            FileLog.getInstance().error(TAG,
                    String.format("requestLocationUpdates failed: interval=%dms, minInterval=%dms, priority=%d",
                            interval, minInterval, priority),
                    e);
            lastRequestedIntervalMs = -1L;
            lastRequestedMinIntervalMs = -1L;
            lastRequestedPriority = -1;
            lastRequestedMinDistanceM = -1f;
            lastRequestedMaxDelayMs = -1L;
            hasPendingRequest = false;
        });
        lastRequestUptimeMs = SystemClock.elapsedRealtime();
    }

    public void reset() {
        lastRequestedIntervalMs = -1L;
        lastRequestedMinIntervalMs = -1L;
        lastRequestedPriority = -1;
        lastRequestedMinDistanceM = -1f;
        lastRequestedMaxDelayMs = -1L;
        lastRequestUptimeMs = 0L;
        hasPendingRequest = false;
        if (pendingReconfigure != null) {
            handler.removeCallbacks(pendingReconfigure);
            pendingReconfigure = null;
        }
    }

    public void resetLastRequested() {
        lastRequestedIntervalMs = -1L;
        lastRequestedMinIntervalMs = -1L;
        lastRequestedPriority = -1;
        lastRequestedMinDistanceM = -1f;
        lastRequestedMaxDelayMs = -1L;
    }

    public long getLastRequestedMaxDelayMs() {
        return lastRequestedMaxDelayMs;
    }
}
