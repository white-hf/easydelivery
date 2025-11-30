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

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;

import com.hf.easydelivery.R;
import com.hf.easydelivery.dao.DeliveryInfo;
import com.hf.easydelivery.view.MainActivity;
import com.hf.courierservice.apihelper.FileLog;

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
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        // Start as foreground service with initial notification
        startForeground(NOTIFICATION_ID, buildNotification());
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
    public void updateDelivery(DeliveryInfo delivery, float distanceMeters, int count) {
        this.currentDelivery = delivery;
        this.currentDistance = distanceMeters;
        this.packageCount = count;

        if (notificationManager != null) {
            notificationManager.notify(NOTIFICATION_ID, buildNotification());
            FileLog.getInstance().debug(TAG, "Notification updated: " + delivery.getRouteNumber()
                    + ", distance=" + distanceMeters + "m, count=" + count);
        }
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
        // Create intent to open app when notification is tapped
        Intent intent = new Intent(this, MainActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent pendingIntent = PendingIntent.getActivity(
                this,
                0,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        // Build notification content
        String title = buildTitle();
        String content = buildContent();

        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_nav_mode_on)
                .setContentTitle(title)
                .setContentText(content)
                .setStyle(new NotificationCompat.BigTextStyle().bigText(content))
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setCategory(NotificationCompat.CATEGORY_NAVIGATION)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .setOngoing(true)
                .setContentIntent(pendingIntent)
                .setAutoCancel(false);

        return builder.build();
    }

    private String buildTitle() {
        if (currentDelivery == null) {
            return "配送导航";
        }

        String routeNumber = currentDelivery.getRouteNumber();
        if (!TextUtils.isEmpty(routeNumber)) {
            return "包裹 #" + routeNumber;
        }

        String orderSn = currentDelivery.getOrderSn();
        if (!TextUtils.isEmpty(orderSn)) {
            return "包裹 #" + orderSn;
        }

        return "配送导航";
    }

    private String buildContent() {
        if (currentDelivery == null) {
            return "准备中...";
        }

        StringBuilder sb = new StringBuilder();

        // Street address
        String address = currentDelivery.getAddress();
        if (!TextUtils.isEmpty(address)) {
            sb.append(address);
        }

        // Unit number
        String unitNumber = currentDelivery.getUnitNumber();
        if (!TextUtils.isEmpty(unitNumber)) {
            if (sb.length() > 0) {
                sb.append(", ");
            }
            sb.append(unitNumber).append("单元");
        }

        // Distance
        if (currentDistance >= 0) {
            if (sb.length() > 0) {
                sb.append(" • ");
            }
            if (currentDistance < 1000) {
                sb.append(String.format("%.0fm", currentDistance));
            } else {
                sb.append(String.format("%.1fkm", currentDistance / 1000));
            }
        }

        // Package count
        if (packageCount > 1) {
            if (sb.length() > 0) {
                sb.append(" • ");
            }
            sb.append(packageCount).append("个包裹");
        }

        return sb.length() > 0 ? sb.toString() : "准备中...";
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        FileLog.getInstance().debug(TAG, "Service destroyed");
    }
}
