package com.hf.easydelivery.service;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.app.KeyguardManager;
import android.content.Context;
import android.content.Intent;
import android.os.Binder;
import android.os.Build;
import android.os.IBinder;
import android.text.TextUtils;
import android.widget.RemoteViews;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;

import com.hf.easydelivery.R;
import com.hf.easydelivery.dao.DeliveryInfo;
import com.hf.easydelivery.view.MainActivity;
import com.hf.easydelivery.view.CameraActivity;
import com.hf.courierservice.apihelper.FileLog;

import java.util.Locale;

import android.content.BroadcastReceiver;
import android.content.IntentFilter;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.SystemClock;

/**
 * Foreground service to display delivery information on lock screen
 * Similar to Google Maps navigation notifications
 */
public class LockScreenNotificationService extends Service {
    private static final String TAG = "LockScreenNotificationService";
    private static final String CHANNEL_ID = "delivery_navigation";
    private static final int NOTIFICATION_ID = 1001;

    // 单一静音渠道（锁屏提示）

    private final IBinder binder = new LocalBinder();
    private NotificationManager notificationManager;
    @Nullable
    private volatile FocusState currentFocus;
    // Dedup fields
    private String lastShownStableId = null;
    private Float lastShownDistanceMeters = null;
    private String lastShownAddress = null;

    // Only track foreground state for lockscreen notification
    private boolean isForeground = false;

    // Worker thread to build RemoteViews/Notification off the main thread
    private HandlerThread workerThread;
    private Handler workerHandler;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    // Debounce notification updates to avoid spamming NotificationManager / RemoteViews on the main thread
    private static final long NOTIFY_DEBOUNCE_MS = 1_000L;
    private long lastNotifyUptimeMs = 0L;
    private Runnable pendingNotify;

    // Debounce screen on/off receiver events (some ROMs are noisy)
    private static final long SCREEN_EVENT_DEBOUNCE_MS = 1_500L;
    private long lastScreenEventUptimeMs = 0L;

    // Action request codes
    private static final int REQUEST_CODE_CAMERA = 100;
    private static final int REQUEST_CODE_NAVIGATION = 101;

    public class LocalBinder extends Binder {
        public LockScreenNotificationService getService() {
            return LockScreenNotificationService.this;
        }
    }

    @Override
    public void onCreate() {
        super.onCreate();
        notificationManager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        createNotificationChannel();
        FileLog.getInstance().debug(TAG, "Service created");
        // Start worker thread for notification building
        workerThread = new HandlerThread("LockScreenNotifyWorker");
        workerThread.start();
        workerHandler = new Handler(workerThread.getLooper());
        IntentFilter filter = new IntentFilter();
        filter.addAction(Intent.ACTION_USER_PRESENT);
        filter.addAction(Intent.ACTION_SCREEN_OFF);
        filter.addAction(Intent.ACTION_SCREEN_ON);
        registerReceiver(lockStateReceiver, filter);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        // IMPORTANT: If started via startForegroundService(), we MUST call startForeground(...) quickly.
        // Lockscreen notification only; do NOT use LOCATION FGS type (Android 14+ restrictions).
        if (!isForeground) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(NOTIFICATION_ID, buildMinimalNotification(),
                        android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC);
            } else {
                startForeground(NOTIFICATION_ID, buildMinimalNotification());
            }
            isForeground = true;
        }

        if (isDeviceLocked()) {
            // Build and update the full RemoteViews notification off the main thread
            scheduleNotify("onStartCommand");
            return START_NOT_STICKY;
        } else {
            stopForegroundAndRemoveNotification();
            stopSelf();
            return START_NOT_STICKY;
        }
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return binder;
    }

    /**
     * Update lockscreen UI from the single source of truth (InfoPill decision pipeline).
     * No calculations and only shows when locked.
     */
    public void updateFocus(@NonNull final FocusState focus) {
        // Ensure state mutations happen on the main thread to avoid races with worker thread.
        if (Looper.myLooper() != Looper.getMainLooper()) {
            mainHandler.post(new Runnable() {
                @Override
                public void run() {
                    updateFocusInternal(focus);
                }
            });
            return;
        }
        updateFocusInternal(focus);
    }

    /**
     * Main-thread only implementation.
     */
    private void updateFocusInternal(@NonNull FocusState focus) {
        // ✅ LOCKSCREEN-ONLY: If device is not locked, never show any notification.
        if (!isDeviceLocked()) {
            stopForegroundAndRemoveNotification();
            stopSelf();
            return;
        }

        if (focus == null || focus.delivery == null || TextUtils.isEmpty(focus.stableId)) {
            clearNotification();
            return;
        }

        boolean sameId = focus.stableId.equals(lastShownStableId);
        Float newDist = focus.distanceMeters;
        boolean distanceChanged = false;
        if (sameId && newDist != null && lastShownDistanceMeters != null) {
            distanceChanged = Math.abs(newDist - lastShownDistanceMeters) > 1f;
        }
        String newAddress = focus.delivery.getAddress();
        boolean addressChanged = sameId && !TextUtils.equals(newAddress, lastShownAddress);

        if (sameId && !distanceChanged && !addressChanged) {
            return;
        }
        lastShownStableId = focus.stableId;
        lastShownDistanceMeters = newDist;
        lastShownAddress = newAddress;
        currentFocus = focus;

        // Promote fast (minimal) to satisfy foreground timing; heavy notify is debounced on worker.
        if (!isForeground) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(NOTIFICATION_ID, buildMinimalNotification(),
                        android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC);
            } else {
                startForeground(NOTIFICATION_ID, buildMinimalNotification());
            }
            isForeground = true;
        }
        scheduleNotify("updateFocus");
    }
    /**
     * Build a lightweight minimal notification for fast foreground promotion (no RemoteViews)
     */
    private Notification buildMinimalNotification() {
        return new NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_nav_mode_on)
                .setContentTitle(getString(R.string.lockscreen_title))
                .setContentText(getString(R.string.lockscreen_preparing))
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setCategory(NotificationCompat.CATEGORY_NAVIGATION)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .setOngoing(true)
                .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
                .build();
    }

    /**
     * Debounce and build notification off the main thread, then update on the main thread.
     */
    private void scheduleNotify(final String reason) {
        if (workerHandler == null) {
            // Avoid building RemoteViews on the main thread. Keep a minimal foreground notification only.
            mainHandler.post(new Runnable() {
                @Override
                public void run() {
                    if (!isDeviceLocked()) {
                        stopForegroundAndRemoveNotification();
                        return;
                    }
                    if (!isForeground) {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                            startForeground(NOTIFICATION_ID, buildMinimalNotification(),
                                    android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC);
                        } else {
                            startForeground(NOTIFICATION_ID, buildMinimalNotification());
                        }
                        isForeground = true;
                    }
                    if (notificationManager != null) {
                        notificationManager.notify(NOTIFICATION_ID, buildMinimalNotification());
                    }
                }
            });
            return;
        }

        if (pendingNotify != null) {
            workerHandler.removeCallbacks(pendingNotify);
        }

        pendingNotify = new Runnable() {
            @Override
            public void run() {
                // Coalesce bursts
                final long now = SystemClock.uptimeMillis();
                long dt = now - lastNotifyUptimeMs;
                if (dt < NOTIFY_DEBOUNCE_MS) {
                    workerHandler.postDelayed(this, NOTIFY_DEBOUNCE_MS - dt);
                    return;
                }
                lastNotifyUptimeMs = now;

                if (!isDeviceLocked()) {
                    return;
                }

                // Build the heavy notification off main thread using a snapshot
                final FocusState snapshot = currentFocus;
                final Notification n = buildNotification(snapshot);

                // Apply/startForeground + notify on the main thread
                mainHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        if (!isDeviceLocked()) {
                            stopForegroundAndRemoveNotification();
                            return;
                        }
                        if (!isForeground) {
                            // Promote with the already-built notification
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                                startForeground(NOTIFICATION_ID, n,
                                        android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC);
                            } else {
                                startForeground(NOTIFICATION_ID, n);
                            }
                            isForeground = true;
                        }
                        if (notificationManager != null) {
                            notificationManager.notify(NOTIFICATION_ID, n);
                        }
                    }
                });
            }
        };

        long now = SystemClock.uptimeMillis();
        long delay = Math.max(0, NOTIFY_DEBOUNCE_MS - (now - lastNotifyUptimeMs));
        workerHandler.postDelayed(pendingNotify, delay);
    }


    /**
     * Clear the notification and stop the service
     */
    public void clearNotification() {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            mainHandler.post(new Runnable() {
                @Override
                public void run() {
                    clearNotification();
                }
            });
            return;
        }
        FileLog.getInstance().debug(TAG, "Clearing notification and stopping service");
        stopForegroundAndRemoveNotification();
        lastShownStableId = null;
        lastShownDistanceMeters = null;
        lastShownAddress = null;
        currentFocus = null;
        stopSelf();
    }

    /**
     * Ensure we are in foreground (showing notification) only when device is locked.
     */
    private void ensureForegroundIfNeeded() {
        // Only show when device is locked.
        if (!isDeviceLocked()) {
            return;
        }
        if (isForeground) {
            return;
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIFICATION_ID, buildNotification(),
                    android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC);
        } else {
            startForeground(NOTIFICATION_ID, buildNotification());
        }
        isForeground = true;
    }

    /**
     * Remove foreground notification (idempotent).
     */
    private void stopForegroundAndRemoveNotification() {
        if (!isForeground && notificationManager == null) {
            return;
        }
        try {
            stopForeground(true); // remove notification
        } catch (Exception ignore) {
        }
        isForeground = false;
        if (notificationManager != null) {
            try {
                notificationManager.cancel(NOTIFICATION_ID);
            } catch (Exception ignore) {
            }
        }
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    getString(R.string.lockscreen_channel_name),
                    NotificationManager.IMPORTANCE_DEFAULT);
            channel.setDescription(getString(R.string.lockscreen_channel_description));
            channel.setLockscreenVisibility(Notification.VISIBILITY_PUBLIC);
            channel.setShowBadge(false);
            channel.enableVibration(false);
            channel.setSound(null, null);
            if (notificationManager != null) {
                notificationManager.createNotificationChannel(channel);
                FileLog.getInstance().debug(TAG, "Notification channel created");
            }
        }
    }

    private Notification buildNotification() {
        return buildNotification(currentFocus);
    }

    private Notification buildNotification(@Nullable FocusState focusSnapshot) {
        // Create RemoteViews for custom layout
        RemoteViews customView = buildCustomNotificationView(focusSnapshot);

        // Create PendingIntents for actions
        PendingIntent cameraIntent = createCameraIntent(focusSnapshot);
        PendingIntent homeIntent = createHomeIntent();

        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_nav_mode_on)
                .setContentTitle(buildSimpleTitle(focusSnapshot))
                .setContentText(buildSimpleContent(focusSnapshot))
                .setCustomContentView(customView) // Collapsed view
                .setCustomBigContentView(customView) // Expanded view (same layout)
                .setStyle(new NotificationCompat.DecoratedCustomViewStyle())
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setCategory(NotificationCompat.CATEGORY_NAVIGATION)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .setOngoing(true)
                .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
                // Tapping notification opens主页
                .setContentIntent(homeIntent)
                .setAutoCancel(false);

        // Add action buttons
        builder.addAction(R.drawable.ic_shutter, getString(R.string.lockscreen_action_camera), cameraIntent)
               .addAction(R.drawable.ic_nav_mode_on, getString(R.string.lockscreen_action_home), homeIntent);

        return builder.build();
    }

    // BroadcastReceiver to handle lockscreen and screen state changes
    private final BroadcastReceiver lockStateReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent != null ? intent.getAction() : null;
            if (Intent.ACTION_USER_PRESENT.equals(action)) {
                // User unlocked the device
                stopForegroundAndRemoveNotification();
                stopSelf();
                return;
            }
            if (Intent.ACTION_SCREEN_ON.equals(action) || Intent.ACTION_SCREEN_OFF.equals(action)) {
                long now = SystemClock.uptimeMillis();
                if (now - lastScreenEventUptimeMs < SCREEN_EVENT_DEBOUNCE_MS) {
                    return;
                }
                lastScreenEventUptimeMs = now;
                // When screen toggles, if we are on keyguard and have delivery data, ensure the notification is present.
                if (isDeviceLocked() && currentFocus != null && currentFocus.delivery != null) {
                    // Ensure foreground quickly and update later via debounce
                    if (!isForeground) {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                            startForeground(NOTIFICATION_ID, buildMinimalNotification(),
                                    android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC);
                        } else {
                            startForeground(NOTIFICATION_ID, buildMinimalNotification());
                        }
                        isForeground = true;
                    }
                    scheduleNotify("screen_toggle");
                } else {
                    // If not locked, keep it clean.
                    stopForegroundAndRemoveNotification();
                    stopSelf();
                }
            }
        }
    };

    private boolean isDeviceLocked() {
        try {
            KeyguardManager km = (KeyguardManager) getSystemService(Context.KEYGUARD_SERVICE);
            return km != null && km.isKeyguardLocked();
        } catch (Exception ignore) {
            return false;
        }
    }


    /**
     * Build custom notification view matching InfoPill layout
     */
    private RemoteViews buildCustomNotificationView(@Nullable FocusState focusSnapshot) {
        RemoteViews views = new RemoteViews(getPackageName(), R.layout.notification_lockscreen);

        if (focusSnapshot == null || focusSnapshot.delivery == null) {
            views.setTextViewText(R.id.notification_route_text, getString(R.string.lockscreen_title));
            views.setTextViewText(R.id.notification_address_text, "");
            views.setTextViewText(R.id.notification_recipient_text, "");
            return views;
        }

        DeliveryInfo d = focusSnapshot.delivery;
        String routeNumber = d.getRouteNumber();
        String title = !TextUtils.isEmpty(routeNumber)
                ? getString(R.string.lockscreen_package_format, routeNumber)
                : getString(R.string.lockscreen_next_stop);
        views.setTextViewText(R.id.notification_route_text, title);
        // Clear extra lines to keep UI minimal
        views.setTextViewText(R.id.notification_address_text, "");
        views.setTextViewText(R.id.notification_recipient_text, "");

        return views;
    }

    /**
     * Build simple title for fallback
     */
    private String buildSimpleTitle(@Nullable FocusState focusSnapshot) {
        if (focusSnapshot == null || focusSnapshot.delivery == null) {
            return getString(R.string.lockscreen_title);
        }
        String routeNumber = focusSnapshot.delivery.getRouteNumber();
        return !TextUtils.isEmpty(routeNumber)
                ? getString(R.string.lockscreen_package_format, routeNumber)
                : getString(R.string.lockscreen_title);
    }

    /**
     * Build simple content for fallback
     */
    private String buildSimpleContent(@Nullable FocusState focusSnapshot) {
        if (focusSnapshot == null || focusSnapshot.delivery == null) {
            return getString(R.string.lockscreen_preparing);
        }

        String routeNumber = focusSnapshot.delivery.getRouteNumber();
        return !TextUtils.isEmpty(routeNumber)
                ? getString(R.string.lockscreen_package_format, routeNumber)
                : getString(R.string.lockscreen_preparing);
    }


    private PendingIntent createCameraIntent(@Nullable FocusState focusSnapshot) {
        Intent intent = new Intent(this, CameraActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        if (focusSnapshot != null && focusSnapshot.delivery != null) {
            DeliveryInfo d = focusSnapshot.delivery;
            intent.putExtra("order_id", d.getOrderId());
            intent.putExtra("latitude", d.getLatitude());
            intent.putExtra("longitude", d.getLongitude());
        }
        return PendingIntent.getActivity(
                this,
                REQUEST_CODE_CAMERA,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    private PendingIntent createHomeIntent() {
        Intent intent = new Intent(this, MainActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        return PendingIntent.getActivity(
                this,
                REQUEST_CODE_NAVIGATION,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    @Override
    public void onDestroy() {
        if (workerHandler != null && pendingNotify != null) {
            workerHandler.removeCallbacks(pendingNotify);
        }
        if (workerThread != null) {
            try {
                workerThread.quitSafely();
            } catch (Exception ignore) {
            }
            workerThread = null;
            workerHandler = null;
        }
        try {
            unregisterReceiver(lockStateReceiver);
        } catch (Exception ignore) {
        }
        stopForegroundAndRemoveNotification();
        super.onDestroy();
        FileLog.getInstance().debug(TAG, "Service destroyed");
    }
}
