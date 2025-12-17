package com.hf.easydelivery.service;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.app.KeyguardManager;
import android.content.Context;
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
// import com.hf.easydelivery.view.MainActivity;
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

    private static final String CHANNEL_ID_ALERT = "delivery_navigation_alert";

    // ✅ Lockscreen strong visibility compat toggles
    // Full-screen intent is intrusive. Enable ONLY for lockscreen use.
    private static final boolean ENABLE_FULL_SCREEN_INTENT = false;
    // Heads-up needs a channel with sound/vibration to reliably appear on Pixel devices.
    // Keep OFF by default; full-screen already covers the lockscreen UX.
    private static final boolean ENABLE_HEADS_UP = false;

    private final IBinder binder = new LocalBinder();
    private NotificationManager notificationManager;
    @Nullable
    private FocusState currentFocus;
    // After showing once, do not update again for the same stableId
    private String lastShownStableId = null;

    // Only track foreground state for lockscreen notification
    private boolean isForeground = false;

    // Worker thread to build RemoteViews/Notification off the main thread
    private HandlerThread workerThread;
    private Handler workerHandler;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    // Debounce notification updates to avoid spamming NotificationManager / RemoteViews on the main thread
    private static final long NOTIFY_DEBOUNCE_MS = 5_000L;
    private long lastNotifyUptimeMs = 0L;
    private Runnable pendingNotify;

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
        registerReceiver(lockStateReceiver, new IntentFilter(Intent.ACTION_USER_PRESENT));
        IntentFilter screen = new IntentFilter();
        screen.addAction(Intent.ACTION_SCREEN_OFF);
        screen.addAction(Intent.ACTION_SCREEN_ON);
        registerReceiver(lockStateReceiver, screen);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        // IMPORTANT: If this service is started via startForegroundService(), the system requires
        // that we call startForeground(...) within a short time window, otherwise the app will crash
        // with ForegroundServiceDidNotStartInTimeException.
        //
        // This service is lockscreen-only: when the device is locked we promote immediately to
        // foreground (showing a placeholder if needed). When unlocked, we stop and keep it clean.
        if (isDeviceLocked()) {
            // Fast foreground promotion to avoid ForegroundServiceDidNotStartInTimeException
            if (!isForeground) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    startForeground(NOTIFICATION_ID, buildMinimalNotification(),
                            android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION);
                } else {
                    startForeground(NOTIFICATION_ID, buildMinimalNotification());
                }
                isForeground = true;
            }
            // Build and update the full RemoteViews notification off the main thread
            scheduleNotify("onStartCommand");
            return START_STICKY;
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
    public void updateFocus(@NonNull FocusState focus) {
        // ✅ LOCKSCREEN-ONLY: If device is not locked, never show any notification.
        if (!isDeviceLocked()) {
            stopForegroundAndRemoveNotification();
            return;
        }

        if (focus == null || focus.delivery == null || TextUtils.isEmpty(focus.stableId)) {
            clearNotification();
            return;
        }

        // Dedup by stableId
        if (focus.stableId.equals(lastShownStableId)) {
            return;
        }
        lastShownStableId = focus.stableId;
        currentFocus = focus;

        // Promote fast (minimal) to satisfy foreground timing; heavy notify is debounced on worker.
        if (!isForeground) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(NOTIFICATION_ID, buildMinimalNotification(),
                        android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION);
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
        final String channelId = ENABLE_HEADS_UP ? CHANNEL_ID_ALERT : CHANNEL_ID;
        return new NotificationCompat.Builder(this, channelId)
                .setSmallIcon(R.drawable.ic_nav_mode_on)
                .setContentTitle("配送导航")
                .setContentText("准备中...")
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
            // Fallback: run on main if worker isn't ready
            if (notificationManager != null) {
                notificationManager.notify(NOTIFICATION_ID, buildNotification());
            }
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
                if (now - lastNotifyUptimeMs < NOTIFY_DEBOUNCE_MS) {
                    workerHandler.postDelayed(this, NOTIFY_DEBOUNCE_MS);
                    return;
                }
                lastNotifyUptimeMs = now;

                if (!isDeviceLocked()) {
                    return;
                }

                // Build the heavy notification off main thread
                final Notification n = buildNotification();

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
                                        android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION);
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

        workerHandler.postDelayed(pendingNotify, NOTIFY_DEBOUNCE_MS);
    }


    /**
     * Clear the notification and stop the service
     */
    public void clearNotification() {
        FileLog.getInstance().debug(TAG, "Clearing notification and stopping service");
        stopForegroundAndRemoveNotification();
        lastShownStableId = null;
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
                    android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION);
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
            // 1) Default channel: lockscreen-visible but silent (no heads-up)
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    "配送导航",
                    NotificationManager.IMPORTANCE_HIGH);
            channel.setDescription("显示附近配送包裹信息");
            channel.setLockscreenVisibility(Notification.VISIBILITY_PUBLIC);
            channel.setShowBadge(false);
            channel.enableVibration(false);
            channel.setSound(null, null);

            // 2) Alert channel: optional heads-up (sound/vibration)
            NotificationChannel alert = new NotificationChannel(
                    CHANNEL_ID_ALERT,
                    "配送导航(弹窗)",
                    NotificationManager.IMPORTANCE_HIGH);
            alert.setDescription("显示附近配送包裹信息(弹窗提示)\n仅在低速/步行时使用");
            alert.setLockscreenVisibility(Notification.VISIBILITY_PUBLIC);
            alert.setShowBadge(false);
            alert.enableVibration(true);
            alert.setSound(android.provider.Settings.System.DEFAULT_NOTIFICATION_URI, null);

            if (notificationManager != null) {
                notificationManager.createNotificationChannel(channel);
                notificationManager.createNotificationChannel(alert);
                FileLog.getInstance().debug(TAG, "Notification channels created");
            }
        }
    }

    private Notification buildNotification() {
        // Create RemoteViews for custom layout
        RemoteViews customView = buildCustomNotificationView();

        // Create PendingIntents for actions
        PendingIntent cameraIntent = createCameraIntent();
        PendingIntent navigationIntent = createNavigationIntent();

        final String channelId = ENABLE_HEADS_UP ? CHANNEL_ID_ALERT : CHANNEL_ID;
        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, channelId)
                .setSmallIcon(R.drawable.ic_nav_mode_on)
                .setContentTitle(buildSimpleTitle())
                .setContentText(buildSimpleContent())
                .setCustomContentView(customView) // Collapsed view
                .setCustomBigContentView(customView) // Expanded view (same layout)
                .setStyle(new NotificationCompat.DecoratedCustomViewStyle())
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setCategory(NotificationCompat.CATEGORY_NAVIGATION)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .setOngoing(true)
                .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
                // No MainActivity dependency; tapping notification opens camera flow.
                .setContentIntent(cameraIntent)
                .setAutoCancel(false)
                ;

        // ✅ Optional: heads-up tuning for Android < O (channels control this on O+)
        if (ENABLE_HEADS_UP && Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            builder.setPriority(NotificationCompat.PRIORITY_MAX);
            builder.setDefaults(NotificationCompat.DEFAULT_ALL);
        }

        // Add action buttons
        builder.addAction(R.drawable.ic_shutter, "拍照", cameraIntent)
               .addAction(R.drawable.ic_nav_mode_on, "导航", navigationIntent);

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
                return;
            }
            if (Intent.ACTION_SCREEN_ON.equals(action) || Intent.ACTION_SCREEN_OFF.equals(action)) {
                // When screen toggles, if we are on keyguard and have delivery data, ensure the notification is present.
                if (isDeviceLocked() && currentFocus != null && currentFocus.delivery != null) {
                    // Ensure foreground quickly and update later via debounce
                    if (!isForeground) {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                            startForeground(NOTIFICATION_ID, buildMinimalNotification(),
                                    android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION);
                        } else {
                            startForeground(NOTIFICATION_ID, buildMinimalNotification());
                        }
                        isForeground = true;
                    }
                    scheduleNotify("screen_toggle");
                } else {
                    // If not locked, keep it clean.
                    stopForegroundAndRemoveNotification();
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
    private RemoteViews buildCustomNotificationView() {
        RemoteViews views = new RemoteViews(getPackageName(), R.layout.notification_lockscreen);

        if (currentFocus == null || currentFocus.delivery == null) {
            views.setTextViewText(R.id.notification_route_text, "配送导航");
            views.setTextViewText(R.id.notification_address_text, "准备中...");
            views.setTextViewText(R.id.notification_recipient_text, "等待包裹信息");
            return views;
        }

        DeliveryInfo d = currentFocus.delivery;
        String routeNumber = d.getRouteNumber();
        String title = !TextUtils.isEmpty(routeNumber) ? ("下一单 · #" + routeNumber) : "下一单";
        views.setTextViewText(R.id.notification_route_text, title);

        // Line 2: Address
        String address = d.getAddress();
        views.setTextViewText(R.id.notification_address_text,
                TextUtils.isEmpty(address) ? "地址未知" : address);

        // Line 3: distance (optional) + recipient (optional)
        StringBuilder line3 = new StringBuilder();
        String recipient = d.getName();
        if (!TextUtils.isEmpty(recipient)) {
            line3.append("收件人: ").append(recipient);
        }
        if (currentFocus.distanceMeters != null && currentFocus.distanceMeters >= 0f) {
            float dist = currentFocus.distanceMeters;
            if (line3.length() > 0)
                line3.append("\n");
            line3.append("距离: ");
            if (dist < 1000f) {
                line3.append(String.format(Locale.getDefault(), "%.0fm", dist));
            } else {
                line3.append(String.format(Locale.getDefault(), "%.1fkm", dist / 1000f));
            }
        }

        views.setTextViewText(R.id.notification_recipient_text,
                line3.length() > 0 ? line3.toString() : "—");

        return views;
    }

    /**
     * Build simple title for fallback
     */
    private String buildSimpleTitle() {
        if (currentFocus == null || currentFocus.delivery == null) {
            return "配送导航";
        }
        String routeNumber = currentFocus.delivery.getRouteNumber();
        return !TextUtils.isEmpty(routeNumber) ? "包裹 #" + routeNumber : "配送导航";
    }

    /**
     * Build simple content for fallback
     */
    private String buildSimpleContent() {
        if (currentFocus == null || currentFocus.delivery == null) {
            return "准备中...";
        }

        StringBuilder sb = new StringBuilder();
        String address = currentFocus.delivery.getAddress();
        if (!TextUtils.isEmpty(address)) {
            sb.append(address);
        }

        if (currentFocus.distanceMeters != null && currentFocus.distanceMeters >= 0f) {
            if (sb.length() > 0)
                sb.append(" • ");
            float dist = currentFocus.distanceMeters;
            if (dist < 1000f) {
                sb.append(String.format(Locale.getDefault(), "%.0fm", dist));
            } else {
                sb.append(String.format(Locale.getDefault(), "%.1fkm", dist / 1000f));
            }
        }

        return sb.length() > 0 ? sb.toString() : "准备中...";
    }


    private PendingIntent createCameraIntent() {
        Intent intent = new Intent(this, CameraActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        if (currentFocus != null && currentFocus.delivery != null) {
            DeliveryInfo d = currentFocus.delivery;
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

    private PendingIntent createNavigationIntent() {
        if (currentFocus == null || currentFocus.delivery == null) {
            // No MainActivity dependency; open camera flow as a safe fallback.
            return createCameraIntent();
        }

        DeliveryInfo currentDelivery = currentFocus.delivery;
        // Create navigation intent to external app
        String uriString = String.format(Locale.getDefault(),
                "google.navigation:q=%f,%f",
                currentDelivery.getLatitude(),
                currentDelivery.getLongitude());
        Intent intent = new Intent(Intent.ACTION_VIEW, android.net.Uri.parse(uriString));
        intent.setPackage("com.google.android.apps.maps");

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
