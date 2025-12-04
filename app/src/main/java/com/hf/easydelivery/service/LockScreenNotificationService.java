package com.hf.easydelivery.service;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.os.Binder;
import android.os.Build;
import android.os.IBinder;
import android.text.TextUtils;
import android.widget.RemoteViews;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;
import androidx.media.app.NotificationCompat.MediaStyle;
import android.support.v4.media.session.MediaSessionCompat;
import android.support.v4.media.session.PlaybackStateCompat;

import com.hf.easydelivery.R;
import com.hf.easydelivery.dao.DeliveryInfo;
import com.hf.easydelivery.view.MainActivity;
import com.hf.easydelivery.view.CameraActivity;
import com.hf.courierservice.apihelper.FileLog;

import java.util.Locale;
import java.util.ArrayList;
import java.util.List;

/**
 * Foreground service to display delivery information on lock screen
 * Similar to Google Maps navigation notifications
 */
public class LockScreenNotificationService extends Service {
    private static final String TAG = "LockScreenNotificationService";
    private static final String CHANNEL_ID = "delivery_navigation";
    private static final int NOTIFICATION_ID = 1001;

    private final IBinder binder = new LocalBinder();
    private NotificationManager notificationManager;
    private DeliveryInfo currentDelivery;
    private float currentDistance = -1f;
    private int packageCount = 1;
    private int nearbyCount = 0;
    private int sameAddressCount = 1;

    // ✅ 通知去重配置
    private static final float MIN_DISTANCE_CHANGE_METERS = 5.0f;
    private static final long MIN_UPDATE_INTERVAL_MS = 5000; // 5秒
    private long lastNotificationTime = 0;

    // ✅ MediaSession for persistent lock screen display
    private MediaSessionCompat mediaSession;

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
        initMediaSession();
        FileLog.getInstance().debug(TAG, "Service created with MediaSession");
    }

    /**
     * Initialize MediaSession for persistent lock screen notification
     */
    private void initMediaSession() {
        mediaSession = new MediaSessionCompat(this, "DeliveryNavigation");
        mediaSession.setFlags(
                MediaSessionCompat.FLAG_HANDLES_MEDIA_BUTTONS |
                        MediaSessionCompat.FLAG_HANDLES_TRANSPORT_CONTROLS);

        // Set initial playback state (required for MediaStyle)
        PlaybackStateCompat.Builder stateBuilder = new PlaybackStateCompat.Builder()
                .setActions(PlaybackStateCompat.ACTION_PLAY | PlaybackStateCompat.ACTION_PAUSE)
                .setState(PlaybackStateCompat.STATE_PLAYING, 0, 1.0f);
        mediaSession.setPlaybackState(stateBuilder.build());
        mediaSession.setActive(true);

        FileLog.getInstance().debug(TAG, "MediaSession initialized");
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        // Start as foreground service with initial notification
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIFICATION_ID, buildNotification(),
                    android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION);
        } else {
            startForeground(NOTIFICATION_ID, buildNotification());
        }
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
        // ✅ 检查是否需要更新
        if (!shouldUpdateNotification(delivery, distanceMeters, count)) {
            return;
        }

        this.currentDelivery = delivery;
        this.currentDistance = distanceMeters;
        this.packageCount = count;
        this.nearbyCount = nearbyCount;
        this.sameAddressCount = sameAddressCount;
        lastNotificationTime = System.currentTimeMillis();

        if (notificationManager != null) {
            notificationManager.notify(NOTIFICATION_ID, buildNotification());
            FileLog.getInstance().debug(TAG,
                    String.format("Notification updated: distance=%.1fm, count=%d, nearby=%d",
                            distanceMeters, count, nearbyCount));
        }
    }

    /**
     * 判断是否需要更新通知
     * 
     * @param delivery       新的配送信息
     * @param distanceMeters 新的距离
     * @param count          新的包裹数量
     * @return true=需要更新, false=跳过
     */
    private boolean shouldUpdateNotification(DeliveryInfo delivery,
            float distanceMeters, int count) {
        long now = System.currentTimeMillis();

        // 时间间隔检查（5秒内不重复更新）
        if (now - lastNotificationTime < MIN_UPDATE_INTERVAL_MS) {
            return false;
        }

        // 目标包裹变化 - 立即更新
        if (currentDelivery != null && delivery != null) {
            String currentSn = currentDelivery.getOrderSn();
            String newSn = delivery.getOrderSn();
            if (currentSn != null && newSn != null && !currentSn.equals(newSn)) {
                return true;
            }
        }

        // 包裹数量变化 - 立即更新
        if (count != packageCount) {
            return true;
        }

        // 距离变化检查（>5米才更新）
        if (Math.abs(distanceMeters - currentDistance) > MIN_DISTANCE_CHANGE_METERS) {
            return true;
        }

        return false;
    }

    /**
     * Clear the notification and stop the service
     */
    public void clearNotification() {
        FileLog.getInstance().debug(TAG, "Clearing notification and stopping service");
        stopForeground(true);
        stopSelf();
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    "配送导航",
                    NotificationManager.IMPORTANCE_HIGH);
            channel.setDescription("显示附近配送包裹信息");
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
        // Create RemoteViews for custom layout
        RemoteViews customView = buildCustomNotificationView();

        // Create PendingIntents for actions
        PendingIntent mainIntent = createMainActivityIntent();
        PendingIntent cameraIntent = createCameraIntent();
        PendingIntent navigationIntent = createNavigationIntent();

        // Build notification with MediaStyle
        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_nav_mode_on)
                .setContentTitle(buildSimpleTitle())
                .setContentText(buildSimpleContent())
                .setCustomContentView(customView) // Collapsed view
                .setCustomBigContentView(customView) // Expanded view (same layout)
                .setStyle(new MediaStyle()
                        .setMediaSession(mediaSession.getSessionToken())
                        .setShowActionsInCompactView(0, 1)) // Show first 2 actions in compact view
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setCategory(NotificationCompat.CATEGORY_NAVIGATION)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .setOngoing(true)
                .setContentIntent(mainIntent)
                .setAutoCancel(false)

                // Add action buttons
                .addAction(R.drawable.ic_shutter, "拍照", cameraIntent)
                .addAction(R.drawable.ic_nav_mode_on, "导航", navigationIntent);

        return builder.build();
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
        super.onDestroy();
        if (mediaSession != null) {
            mediaSession.release();
            mediaSession = null;
        }
        FileLog.getInstance().debug(TAG, "Service destroyed and MediaSession released");
    }
}
