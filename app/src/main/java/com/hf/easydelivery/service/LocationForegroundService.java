package com.hf.easydelivery.service;

import static com.google.android.gms.location.Granularity.GRANULARITY_FINE;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ServiceInfo;
import android.location.Location;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.app.ActivityCompat;
import androidx.core.app.NotificationCompat;

import com.google.android.gms.location.LocationCallback;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationResult;
import com.google.android.gms.location.Priority;
import com.hf.courierservice.apihelper.FileLog;
import com.hf.easydelivery.R;
import com.hf.easydelivery.core.SmartLocationManager;
import com.hf.easydelivery.core.facade.DefaultLocationFacadeProvider;
import com.hf.easydelivery.core.facade.ForegroundLocationConsumer;
import com.hf.easydelivery.core.facade.LocationFacadeProvider;
import com.hf.easydelivery.core.source.ForegroundServiceLocationSource;
import com.hf.easydelivery.core.source.LocationSource;
import com.hf.easydelivery.core.strategy.StrategyConfig;
import com.hf.easydelivery.map.config.ProfileManager;

public class LocationForegroundService extends Service {
    public static final String ACTION_START = "com.hf.easydelivery.action.FG_LOC_START";
    public static final String ACTION_STOP = "com.hf.easydelivery.action.FG_LOC_STOP";
    public static final String EXTRA_POWER_SAVE = "extra_power_save";
    public static final String EXTRA_INTERVAL_MS = "extra_interval_ms";
    public static final String EXTRA_MIN_INTERVAL_MS = "extra_min_interval_ms";
    public static final String EXTRA_MIN_DISTANCE_M = "extra_min_distance_m";
    public static final String EXTRA_PRIORITY = "extra_priority";
    public static final String EXTRA_MAX_DELAY_MS = "extra_max_delay_ms";

    private static final String CHANNEL_ID = "fg_location_channel";
    private static final int NOTIFICATION_ID = 4102;
    private static final String TAG = "LocationFgService";
    private static final long RETRY_BASE_DELAY_MS = 2_000L;
    private static final long RETRY_MAX_DELAY_MS = 5_000L;

    private LocationSource locationSource;
    private LocationCallback locationCallback;
    private long lastCallbackUptimeMs = 0L;
    private long lastElapsedMs = -1L;
    private boolean destroyed = false;
    private int retryAttempts = 0;

    @NonNull
    private TrackingConfig requestedConfig = defaultConfig(false);
    @Nullable
    private TrackingConfig activeConfig;

    private final Handler retryHandler = new Handler(Looper.getMainLooper());
    private final Runnable retryRunnable = new Runnable() {
        @Override
        public void run() {
            if (destroyed) {
                return;
            }
            FileLog.getInstance().warning(TAG, "retry startForegroundTracking after failure, attempt=" + retryAttempts);
            startForegroundTracking(requestedConfig, false);
        }
    };
    private final LocationFacadeProvider locationFacadeProvider =
            DefaultLocationFacadeProvider.getInstance();
    @Nullable
    private SmartLocationManager smartLocationManager;

    private static final class TrackingConfig {
        final boolean powerSave;
        final long intervalMs;
        final long minIntervalMs;
        final float minDistanceM;
        final int priority;
        final long maxDelayMs;

        TrackingConfig(boolean powerSave,
                       long intervalMs,
                       long minIntervalMs,
                       float minDistanceM,
                       int priority,
                       long maxDelayMs) {
            this.powerSave = powerSave;
            this.intervalMs = intervalMs;
            this.minIntervalMs = minIntervalMs;
            this.minDistanceM = minDistanceM;
            this.priority = priority;
            this.maxDelayMs = maxDelayMs;
        }

        boolean sameAs(@Nullable TrackingConfig other) {
            if (other == null) {
                return false;
            }
            return powerSave == other.powerSave
                    && intervalMs == other.intervalMs
                    && minIntervalMs == other.minIntervalMs
                    && Float.compare(minDistanceM, other.minDistanceM) == 0
                    && priority == other.priority
                    && maxDelayMs == other.maxDelayMs;
        }

        @NonNull
        String asLogString() {
            return "powerSave=" + powerSave
                    + " intervalMs=" + intervalMs
                    + " minIntervalMs=" + minIntervalMs
                    + " minDistanceM=" + minDistanceM
                    + " maxDelayMs=" + maxDelayMs
                    + " priority=" + priority;
        }
    }

    @Override
    public void onCreate() {
        super.onCreate();
        SmartLocationManager manager = SmartLocationManager.getInstance(this);
        smartLocationManager = manager;
        if (manager != null && manager.getFusedLocationClient() != null) {
            locationSource = new ForegroundServiceLocationSource(manager.getFusedLocationClient());
            FileLog.getInstance().debug(TAG, "onCreate: reuse fusedLocationClient from SmartLocationManager");
        } else {
            locationSource = new ForegroundServiceLocationSource(this);
            FileLog.getInstance().warning(TAG, "onCreate: fallback to new fusedLocationClient");
        }
        if (smartLocationManager != null) {
            smartLocationManager.onForegroundServiceStateChanged(true);
        }
        createNotificationChannel();
        destroyed = false;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        String action = intent != null ? intent.getAction() : null;
        FileLog.getInstance().debug(TAG,
                "onStartCommand: action=" + action + ", startId=" + startId + ", flags=" + flags
                        + ", restarted=" + (intent == null));

        if (ACTION_STOP.equals(action)) {
            stopForegroundTracking();
            if (smartLocationManager != null) {
                smartLocationManager.onForegroundServiceStateChanged(false);
            }
            stopForeground(true);
            stopSelfResult(startId);
            return START_NOT_STICKY;
        }

        requestedConfig = resolveTrackingConfig(intent);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIFICATION_ID, buildNotification(), ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION);
        } else {
            startForeground(NOTIFICATION_ID, buildNotification());
        }

        startForegroundTracking(requestedConfig, false);
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        FileLog.getInstance().debug(TAG,
                "onDestroy: callbackActive=" + (locationCallback != null)
                        + ", thread=" + Thread.currentThread().getName());
        stopForegroundTracking();
        if (smartLocationManager != null) {
            smartLocationManager.onForegroundServiceStateChanged(false);
        }
        stopForeground(true);
        destroyed = true;
        super.onDestroy();
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    private void startForegroundTracking(@NonNull TrackingConfig config, boolean forceRestart) {
        if (locationCallback != null && !forceRestart && config.sameAs(activeConfig)) {
            FileLog.getInstance().debug(TAG, "startForegroundTracking skipped: unchanged config " + config.asLogString());
            return;
        }

        if (locationCallback != null) {
            locationSource.removeLocationUpdates(locationCallback);
            locationCallback = null;
            FileLog.getInstance().debug(TAG, "startForegroundTracking: apply new config -> restart callbacks");
        }

        if (ActivityCompat.checkSelfPermission(this,
                android.Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            FileLog.getInstance().warning(TAG, "startForegroundTracking skipped: location permission missing");
            return;
        }

        locationCallback = new LocationCallback() {
            @Override
            public void onLocationResult(LocationResult locationResult) {
                if (locationResult == null || locationResult.getLocations().isEmpty()) {
                    return;
                }
                int batchSize = locationResult.getLocations().size();
                Location last = locationResult.getLastLocation();
                if (last == null) {
                    return;
                }
                String provider = last.getProvider();
                long nowUptime = android.os.SystemClock.uptimeMillis();
                long deltaMs = lastCallbackUptimeMs == 0L ? -1L : (nowUptime - lastCallbackUptimeMs);
                lastCallbackUptimeMs = nowUptime;
                long elapsedMs = -1L;
                try {
                    elapsedMs = last.getElapsedRealtimeNanos() / 1_000_000L;
                } catch (Throwable ignore) {
                }
                long ageMs = elapsedMs > 0 ? (android.os.SystemClock.elapsedRealtime() - elapsedMs) : -1L;
                if (lastElapsedMs > 0 && elapsedMs > 0 && lastElapsedMs != elapsedMs) {
                    // keep lightweight lineage marker for debug consistency
                }
                lastElapsedMs = elapsedMs;
                FileLog.getInstance().debug(TAG,
                        String.format("onLocationResult: deltaMs=%d acc=%.1fm ageMs=%d elapsedMs=%d lat=%.6f lng=%.6f batch=%d provider=%s speed=%.2f bearing=%.1f",
                                deltaMs,
                                last.getAccuracy(),
                                ageMs,
                                elapsedMs,
                                last.getLatitude(),
                                last.getLongitude(),
                                batchSize,
                                provider,
                                last.getSpeed(),
                                last.getBearing()));
                ForegroundLocationConsumer consumer =
                        locationFacadeProvider.getForegroundLocationConsumer(getApplicationContext());
                if (consumer != null) {
                    consumer.onForegroundLocation(last);
                }
            }
        };

        FileLog.getInstance().debug(TAG, "startForegroundTracking: " + config.asLogString());

        LocationRequest locationRequest = new LocationRequest.Builder(config.priority, config.intervalMs)
                .setMinUpdateIntervalMillis(config.minIntervalMs)
                .setMinUpdateDistanceMeters(config.minDistanceM)
                .setMaxUpdateDelayMillis(config.maxDelayMs)
                .setGranularity(GRANULARITY_FINE)
                .setWaitForAccurateLocation(false)
                .build();

        TrackingConfig startConfig = config;
        locationSource.requestLocationUpdates(locationRequest,
                        locationCallback,
                        Looper.getMainLooper())
                .addOnSuccessListener(unused -> {
                    retryAttempts = 0;
                    activeConfig = startConfig;
                    FileLog.getInstance().debug(TAG, "requestLocationUpdates success");
                })
                .addOnFailureListener(e -> {
                    FileLog.getInstance().error(TAG, "requestLocationUpdates failed", e);
                    locationCallback = null;
                    scheduleRetry();
                });
    }

    private void stopForegroundTracking() {
        boolean hadCallback = locationCallback != null;
        if (locationSource != null && locationCallback != null) {
            locationSource.removeLocationUpdates(locationCallback);
            FileLog.getInstance().debug(TAG, "removeLocationUpdates requested");
        }
        retryHandler.removeCallbacks(retryRunnable);
        locationCallback = null;
        activeConfig = null;
        if (hadCallback) {
            FileLog.getInstance().debug(TAG, "stopForegroundTracking completed");
        }
    }

    private void scheduleRetry() {
        if (destroyed) {
            return;
        }
        retryAttempts += 1;
        long delayMs = Math.min(RETRY_MAX_DELAY_MS, RETRY_BASE_DELAY_MS * retryAttempts);
        retryHandler.removeCallbacks(retryRunnable);
        retryHandler.postDelayed(retryRunnable, delayMs);
    }

    @NonNull
    private TrackingConfig resolveTrackingConfig(@Nullable Intent intent) {
        if (intent == null) {
            ProfileManager profileManager = ProfileManager.get(this);
            return defaultConfig(profileManager.isPowerSaver());
        }

        boolean hasInterval = intent.hasExtra(EXTRA_INTERVAL_MS);
        boolean hasMinInterval = intent.hasExtra(EXTRA_MIN_INTERVAL_MS);
        boolean hasMinDistance = intent.hasExtra(EXTRA_MIN_DISTANCE_M);
        boolean hasPriority = intent.hasExtra(EXTRA_PRIORITY);
        boolean hasMaxDelay = intent.hasExtra(EXTRA_MAX_DELAY_MS);
        boolean powerSave = intent.getBooleanExtra(EXTRA_POWER_SAVE, false);

        TrackingConfig fallback = defaultConfig(powerSave);
        if (!hasInterval || !hasMinInterval || !hasMinDistance) {
            return fallback;
        }

        long intervalMs = Math.max(500L, intent.getLongExtra(EXTRA_INTERVAL_MS, fallback.intervalMs));
        long minIntervalMs = Math.max(250L, intent.getLongExtra(EXTRA_MIN_INTERVAL_MS, fallback.minIntervalMs));
        float minDistanceM = Math.max(0f, intent.getFloatExtra(EXTRA_MIN_DISTANCE_M, fallback.minDistanceM));
        int priority = hasPriority ? intent.getIntExtra(EXTRA_PRIORITY, fallback.priority) : fallback.priority;
        long maxDelayMs = hasMaxDelay ? Math.max(0L, intent.getLongExtra(EXTRA_MAX_DELAY_MS, fallback.maxDelayMs)) : fallback.maxDelayMs;
        return new TrackingConfig(powerSave, intervalMs, minIntervalMs, minDistanceM, priority, maxDelayMs);
    }

    @NonNull
    private static TrackingConfig defaultConfig(boolean powerSave) {
        long intervalMs = powerSave ? StrategyConfig.getFgPowerSaveIntervalMs() : StrategyConfig.getFgRealtimeIntervalMs();
        long minIntervalMs = powerSave ? StrategyConfig.getFgPowerSaveMinIntervalMs() : StrategyConfig.getFgRealtimeMinIntervalMs();
        float minDistanceM = powerSave ? StrategyConfig.getFgPowerSaveMinDistanceM() : StrategyConfig.getFgRealtimeMinDistanceM();
        int priority = powerSave ? Priority.PRIORITY_BALANCED_POWER_ACCURACY : Priority.PRIORITY_HIGH_ACCURACY;
        return new TrackingConfig(powerSave, intervalMs, minIntervalMs, minDistanceM, priority, 0L);
    }

    private Notification buildNotification() {
        return new NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_logo)
                .setContentTitle(getString(R.string.fg_location_title))
                .setContentText(getString(R.string.fg_location_text))
                .setOngoing(true)
                .setCategory(NotificationCompat.CATEGORY_NAVIGATION)
                .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .build();
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            return;
        }
        NotificationManager manager = getSystemService(NotificationManager.class);
        if (manager == null) {
            return;
        }
        NotificationChannel channel = new NotificationChannel(
                CHANNEL_ID,
                getString(R.string.fg_location_channel_name),
                NotificationManager.IMPORTANCE_LOW);
        channel.setDescription(getString(R.string.fg_location_channel_desc));
        channel.setShowBadge(false);
        manager.createNotificationChannel(channel);
    }
}
