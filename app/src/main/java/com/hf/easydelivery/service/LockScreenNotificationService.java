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

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;

import com.hf.easydelivery.R;
import com.hf.easydelivery.dao.DeliveryInfo;
import com.hf.easydelivery.map.MapConfig;
import com.hf.easydelivery.view.MainActivity;
import com.hf.easydelivery.view.CameraActivity;
import com.hf.courierservice.apihelper.FileLog;

import java.util.Locale;
import java.util.ArrayList;
import java.util.List;

import android.content.BroadcastReceiver;
import android.content.IntentFilter;

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
    private static final boolean ENABLE_FULL_SCREEN_INTENT = true;
    // Heads-up needs a channel with sound/vibration to reliably appear on Pixel devices.
    // Keep OFF by default; full-screen already covers the lockscreen UX.
    private static final boolean ENABLE_HEADS_UP = false;

    private final IBinder binder = new LocalBinder();
    private NotificationManager notificationManager;
    private DeliveryInfo currentDelivery;
    // ✅ After notifying once, do not notify again for the same parcel (orderSn)
    private String lastNotifiedOrderSn = null;
    private float currentDistance = -1f;
    private int packageCount = 1;
    private int nearbyCount = 0;
    private int sameAddressCount = 1;

    // Only track foreground state for lockscreen notification
    private boolean isForeground = false;

    // Action request codes
    private static final int REQUEST_CODE_CAMERA = 100;
    private static final int REQUEST_CODE_NAVIGATION = 101;
    private static final int REQUEST_CODE_MAIN = 102;

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
        registerReceiver(lockStateReceiver, new IntentFilter(Intent.ACTION_USER_PRESENT));
        IntentFilter screen = new IntentFilter();
        screen.addAction(Intent.ACTION_SCREEN_OFF);
        screen.addAction(Intent.ACTION_SCREEN_ON);
        registerReceiver(lockStateReceiver, screen);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        // Do not show anything on start. We only show when device is locked AND we have delivery data.
        // updateDelivery(...) will promote to foreground when needed.
        return START_STICKY;
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return binder;
    }

    /**
     * Update the notification with new delivery information
     */
    /**
     * Update notification with detailed delivery info
     */
    public void updateDelivery(DeliveryInfo delivery, float distanceMeters, int count) {
        updateDelivery(delivery, distanceMeters, count, 0, 1);
    }

    /**
     * Update with full info including nearby and same-address counts
     */
    public void updateDelivery(DeliveryInfo delivery, float distanceMeters, int count,
            int nearbyCount, int sameAddressCount) {
        updateDelivery(delivery, distanceMeters, count, nearbyCount, sameAddressCount, -1f);
    }

    /**
     * Update with full info including nearby/same-address counts and current speed (m/s)
     */
    public void updateDelivery(DeliveryInfo delivery, float distanceMeters, int count,
                               int nearbyCount, int sameAddressCount, float speedMps) {
        // ✅ LOCKSCREEN-ONLY: If device is not locked, never show any notification.
        if (!isDeviceLocked()) {
            stopForegroundAndRemoveNotification();
            return;
        }

        // ✅ Do not notify again for the same parcel (orderSn)
        final String newSn = (delivery != null) ? delivery.getOrderSn() : null;
        if (!TextUtils.isEmpty(newSn) && newSn.equals(lastNotifiedOrderSn)) {
            return;
        }

        // ✅ If no delivery info, still allow showing a placeholder (optional)
        this.currentDelivery = delivery;
        this.currentDistance = distanceMeters;
        this.packageCount = count;
        this.nearbyCount = nearbyCount;
        this.sameAddressCount = sameAddressCount;

        // Record that we have notified this parcel once
        if (!TextUtils.isEmpty(newSn)) {
            lastNotifiedOrderSn = newSn;
        }

        // Promote to foreground ONLY on lockscreen.
        ensureForegroundIfNeeded();

        if (notificationManager != null) {
            notificationManager.notify(NOTIFICATION_ID, buildNotification());
        }
    }


    /**
     * Clear the notification and stop the service
     */
    public void clearNotification() {
        FileLog.getInstance().debug(TAG, "Clearing notification and stopping service");
        stopForegroundAndRemoveNotification();
        lastNotifiedOrderSn = null;
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
        PendingIntent mainIntent = createMainActivityIntent();
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
                .setContentIntent(mainIntent)
                .setAutoCancel(false)
                // ✅ Optional: full-screen intent (very intrusive; only enable if you must)
                ;

        if (ENABLE_FULL_SCREEN_INTENT) {
            // Full-screen intent shows an activity over the lockscreen.
            // We pass a flag so MainActivity can route to the relevant screen.
            PendingIntent fullScreen = createFullScreenIntent();
            builder.setFullScreenIntent(fullScreen, true);
        }

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

    private PendingIntent createFullScreenIntent() {
        Intent intent = new Intent(this, MainActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        intent.putExtra("from_lockscreen", true);
        intent.putExtra("open_delivery_panel", true);
        return PendingIntent.getActivity(
                this,
                1003,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
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
                if (isDeviceLocked() && currentDelivery != null) {
                    ensureForegroundIfNeeded();
                    if (notificationManager != null) {
                        notificationManager.notify(NOTIFICATION_ID, buildNotification());
                    }
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

        if (currentDelivery == null) {
            views.setTextViewText(R.id.notification_route_text, "配送导航");
            views.setTextViewText(R.id.notification_address_text, "准备中...");
            views.setTextViewText(R.id.notification_recipient_text, "等待包裹信息");
            return views;
        }

        // Line 1: Route info (matches InfoPill pillRouteText logic)
        List<String> routeParts = new ArrayList<>();

        // Street number
        if (currentDelivery.getCivilNumber() > 0) {
            routeParts.add(currentDelivery.getCivilNumber() + "号");
        }

        // Unit number
        String unitNumber = currentDelivery.getUnitNumber();
        if (!TextUtils.isEmpty(unitNumber)) {
            routeParts.add(unitNumber + "单元");
        }

        // Route number
        String routeNumber = currentDelivery.getRouteNumber();
        if (!TextUtils.isEmpty(routeNumber)) {
            routeParts.add(routeNumber + "包裹");
        }

        // Same address count
        if (sameAddressCount > 1) {
            routeParts.add(String.format(Locale.getDefault(), "同址共%d票", sameAddressCount));
        }

        // Nearby count
        if (nearbyCount > 1) {
            routeParts.add(String.format(Locale.getDefault(), "附近共%d票", nearbyCount));
        }

        views.setTextViewText(R.id.notification_route_text,
                TextUtils.join("  ", routeParts));

        // Line 2: Address
        String address = currentDelivery.getAddress();
        views.setTextViewText(R.id.notification_address_text,
                TextUtils.isEmpty(address) ? "地址未知" : address);

        // Line 3: Recipient and distance
        StringBuilder line3 = new StringBuilder();
        String recipient = currentDelivery.getName();
        if (!TextUtils.isEmpty(recipient)) {
            line3.append("收件人: ").append(recipient);
        }

        if (currentDistance >= 0) {
            if (line3.length() > 0) {
                line3.append("\n");
            }
            line3.append("距离: ");
            if (currentDistance < 1000) {
                line3.append(String.format(Locale.getDefault(), "%.0fm", currentDistance));
            } else {
                line3.append(String.format(Locale.getDefault(), "%.1fkm", currentDistance / 1000));
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
        if (currentDelivery == null) {
            return "配送导航";
        }
        String routeNumber = currentDelivery.getRouteNumber();
        return !TextUtils.isEmpty(routeNumber) ? "包裹 #" + routeNumber : "配送导航";
    }

    /**
     * Build simple content for fallback
     */
    private String buildSimpleContent() {
        if (currentDelivery == null) {
            return "准备中...";
        }

        StringBuilder sb = new StringBuilder();
        String address = currentDelivery.getAddress();
        if (!TextUtils.isEmpty(address)) {
            sb.append(address);
        }

        if (currentDistance >= 0) {
            if (sb.length() > 0)
                sb.append(" • ");
            if (currentDistance < 1000) {
                sb.append(String.format(Locale.getDefault(), "%.0fm", currentDistance));
            } else {
                sb.append(String.format(Locale.getDefault(), "%.1fkm", currentDistance / 1000));
            }
        }

        return sb.length() > 0 ? sb.toString() : "准备中...";
    }

    private PendingIntent createMainActivityIntent() {
        Intent intent = new Intent(this, MainActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        return PendingIntent.getActivity(
                this,
                REQUEST_CODE_MAIN,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    private PendingIntent createCameraIntent() {
        Intent intent = new Intent(this, CameraActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        if (currentDelivery != null) {
            intent.putExtra("order_id", currentDelivery.getOrderId());
            intent.putExtra("latitude", currentDelivery.getLatitude());
            intent.putExtra("longitude", currentDelivery.getLongitude());
        }
        return PendingIntent.getActivity(
                this,
                REQUEST_CODE_CAMERA,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    private PendingIntent createNavigationIntent() {
        if (currentDelivery == null) {
            return createMainActivityIntent();
        }

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
        try {
            unregisterReceiver(lockStateReceiver);
        } catch (Exception ignore) {
        }
        stopForegroundAndRemoveNotification();
        super.onDestroy();
        FileLog.getInstance().debug(TAG, "Service destroyed");
    }
}
