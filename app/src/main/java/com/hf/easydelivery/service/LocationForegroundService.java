package com.hf.easydelivery.service;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
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
import com.hf.easydelivery.core.SmartLocationManager;
import com.hf.easydelivery.core.source.ForegroundServiceLocationSource;
import com.hf.easydelivery.core.source.LocationSource;
import com.hf.easydelivery.core.strategy.StrategyConfig;

public class LocationForegroundService extends Service {
    public static final String ACTION_START = "com.hf.easydelivery.action.FG_LOC_START";
    public static final String ACTION_STOP = "com.hf.easydelivery.action.FG_LOC_STOP";
    private static final String CHANNEL_ID = "fg_location_channel";
    private static final int NOTIFICATION_ID = 4102;
    private static final String TAG = "LocationFgService";

    private LocationSource locationSource;
    private LocationCallback locationCallback;

    @Override
    public void onCreate() {
        super.onCreate();
        locationSource = new ForegroundServiceLocationSource(this);
        createNotificationChannel();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        String action = intent != null ? intent.getAction() : null;
        if (ACTION_STOP.equals(action)) {
            stopForegroundTracking();
            FileLog.getInstance().debug(TAG, "stopForegroundTracking completed");
            stopSelf();
            return START_NOT_STICKY;
        }
        startForeground(NOTIFICATION_ID, buildNotification());
        FileLog.getInstance().debug(TAG, "startForeground completed");
        startForegroundTracking();
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        stopForegroundTracking();
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
                if (locationResult.getLocations().isEmpty()) {
                    return;
                }
                SmartLocationManager mgr = SmartLocationManager.getInstance(getApplicationContext());
                if (mgr != null) {
                    mgr.onForegroundLocation(locationResult.getLastLocation());
                }
            }
        };

        LocationRequest locationRequest = new LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY)
                .setIntervalMillis(StrategyConfig.getRealtimeIntervalMs())
                .setMinUpdateIntervalMillis(StrategyConfig.getRealtimeMinIntervalMs())
                .setMinUpdateDistanceMeters(StrategyConfig.getRealtimeMinDistanceM())
                .setMaxUpdateDelayMillis(0L)
                .setWaitForAccurateLocation(false)
                .build();

        locationSource.requestLocationUpdates(locationRequest,
                locationCallback,
                Looper.getMainLooper())
                .addOnSuccessListener(unused -> FileLog.getInstance().debug(TAG, "requestLocationUpdates success"))
                .addOnFailureListener(e -> FileLog.getInstance().error(TAG, "requestLocationUpdates failed", e));
    }

    private void stopForegroundTracking() {
        if (locationSource != null && locationCallback != null) {
            locationSource.removeLocationUpdates(locationCallback);
        }
        locationCallback = null;
    }

    private Notification buildNotification() {
        return new NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_logo)
                .setContentTitle(getString(R.string.fg_location_title))
                .setContentText(getString(R.string.fg_location_text))
                .setOngoing(true)
                .setPriority(NotificationCompat.PRIORITY_LOW)
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
