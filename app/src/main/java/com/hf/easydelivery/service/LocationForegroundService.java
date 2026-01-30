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

import androidx.annotation.Nullable;
import androidx.core.app.ActivityCompat;
import androidx.core.app.NotificationCompat;

import com.google.android.gms.location.LocationCallback;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationResult;
import com.google.android.gms.location.Priority;
import com.hf.courierservice.apihelper.FileLog;
import com.hf.easydelivery.R;
import com.hf.easydelivery.core.source.ForegroundServiceLocationSource;
import com.hf.easydelivery.core.source.LocationSource;
import com.hf.easydelivery.core.strategy.StrategyConfig;
import com.hf.easydelivery.core.SmartLocationManager;
import com.hf.easydelivery.core.facade.DefaultLocationFacadeProvider;
import com.hf.easydelivery.core.facade.ForegroundLocationConsumer;
import com.hf.easydelivery.core.facade.LocationFacadeProvider;
import com.hf.easydelivery.map.config.ProfileManager;

public class LocationForegroundService extends Service {
    public static final String ACTION_START = "com.hf.easydelivery.action.FG_LOC_START";
    public static final String ACTION_STOP = "com.hf.easydelivery.action.FG_LOC_STOP";
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
    private final Handler retryHandler = new Handler(Looper.getMainLooper());
    private final Runnable retryRunnable = new Runnable() {
        @Override
        public void run() {
            if (destroyed) {
                return;
            }
            FileLog.getInstance().warning(TAG, "retry startForegroundTracking after failure, attempt=" + retryAttempts);
            startForegroundTracking();
        }
    };
    private final LocationFacadeProvider locationFacadeProvider =
            DefaultLocationFacadeProvider.getInstance();

    @Override
    public void onCreate() {
        super.onCreate();
        SmartLocationManager manager = SmartLocationManager.getInstance(this);
        if (manager != null && manager.getFusedLocationClient() != null) {
            locationSource = new ForegroundServiceLocationSource(manager.getFusedLocationClient());
            FileLog.getInstance().debug(TAG, "onCreate: reuse fusedLocationClient from SmartLocationManager");
        } else {
            locationSource = new ForegroundServiceLocationSource(this);
            FileLog.getInstance().warning(TAG, "onCreate: fallback to new fusedLocationClient");
        }
        createNotificationChannel();
        destroyed = false;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        String action = intent != null ? intent.getAction() : null;
        FileLog.getInstance().debug(TAG, "onStartCommand: action=" + action + ", startId=" + startId + ", flags=" + flags + ", restarted=" + (intent == null));
        if (ACTION_STOP.equals(action)) {
            stopForegroundTracking();
            stopForeground(true);
            stopSelf();
            return START_NOT_STICKY;
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIFICATION_ID, buildNotification(), ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION);
        } else {
            startForeground(NOTIFICATION_ID, buildNotification());
        }
        FileLog.getInstance().debug(TAG, "startForeground completed, sdkInt=" + Build.VERSION.SDK_INT);
        startForegroundTracking();
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        FileLog.getInstance().debug(TAG, "onDestroy: callbackActive=" + (locationCallback != null) + ", thread=" + Thread.currentThread().getName());
        stopForegroundTracking();
        stopForeground(true);
        destroyed = true;
        super.onDestroy();
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    private void startForegroundTracking() {
        if (locationCallback != null) {
            return;
        }
        if (ActivityCompat.checkSelfPermission(this,
                android.Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            FileLog.getInstance().warning(TAG, "startForegroundTracking skipped: location permission missing");
            return;
        }
        locationCallback = new LocationCallback() {
            @Override
            public void onLocationResult(LocationResult locationResult) {
                if (locationResult == null) {
                    return;
                }
                int batchSize = locationResult.getLocations().size();
                if (locationResult.getLocations().isEmpty()) {
                    return;
                }
                Location last = locationResult.getLastLocation();
                String provider = last != null ? last.getProvider() : "unknown";
                long nowUptime = android.os.SystemClock.uptimeMillis();
                long deltaMs = lastCallbackUptimeMs == 0L ? -1L : (nowUptime - lastCallbackUptimeMs);
                lastCallbackUptimeMs = nowUptime;
                long elapsedMs = -1L;
                try {
                    elapsedMs = last.getElapsedRealtimeNanos() / 1_000_000L;
                } catch (Throwable ignore) {
                }
                long ageMs = elapsedMs > 0 ? (android.os.SystemClock.elapsedRealtime() - elapsedMs) : -1L;
                float dLast = -1f;
                if (lastElapsedMs > 0 && elapsedMs > 0 && lastElapsedMs != elapsedMs) {
                    dLast = 0f;
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

        ProfileManager profileManager = ProfileManager.get(this);
        boolean powerSave = profileManager.isPowerSaver();
        long intervalMs = powerSave ? StrategyConfig.getFgPowerSaveIntervalMs()
                                    : StrategyConfig.getFgRealtimeIntervalMs();
        long minIntervalMs = powerSave ? StrategyConfig.getFgPowerSaveMinIntervalMs()
                                       : StrategyConfig.getFgRealtimeMinIntervalMs();
        float minDistanceM = powerSave ? StrategyConfig.getFgPowerSaveMinDistanceM()
                                       : StrategyConfig.getFgRealtimeMinDistanceM();
        long maxDelayMs = 0L;
        FileLog.getInstance().debug(TAG, "startForegroundTracking: powerSave=" + powerSave
                + " intervalMs=" + intervalMs
                + " minIntervalMs=" + minIntervalMs
                + " minDistanceM=" + minDistanceM
                + " maxDelayMs=" + maxDelayMs
                + " priority=HIGH_ACCURACY");

        LocationRequest locationRequest = new LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, intervalMs)
                .setMinUpdateIntervalMillis(minIntervalMs)
                .setMinUpdateDistanceMeters(minDistanceM)
                .setMaxUpdateDelayMillis(maxDelayMs)
                .setGranularity(GRANULARITY_FINE)
                .setWaitForAccurateLocation(false)
                .build();

        locationSource.requestLocationUpdates(locationRequest,
                locationCallback,
                Looper.getMainLooper())
                .addOnSuccessListener(unused -> {
                    retryAttempts = 0;
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
