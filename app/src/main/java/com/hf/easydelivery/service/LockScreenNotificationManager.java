package com.hf.easydelivery.service;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.location.Location;
import android.os.IBinder;

import androidx.lifecycle.Observer;

import com.hf.easydelivery.core.SmartLocationManager;
import com.hf.easydelivery.dao.DeliveryInfo;
import com.hf.easydelivery.view.model.MapViewModel;
import com.hf.courierservice.apihelper.FileLog;

import java.util.List;

/**
 * Global manager for lock screen notification service
 * Independent of Fragment lifecycle
 */
public class LockScreenNotificationManager implements SmartLocationManager.LocationUpdateListener {
    private static final String TAG = "LockScreenNotificationManager";
    private static LockScreenNotificationManager instance;

    private final Context appContext;
    private MapViewModel boundViewModel;
    private LockScreenNotificationService service;
    private boolean serviceBound = false;
    private boolean serviceStarting = false;

    private DeliveryInfo currentDelivery;
    private float currentDistance = -1f;
    private Location lastLocation;
    private List<DeliveryInfo> deliveries;

    private boolean isMonitoring = false;
    private Observer<List<DeliveryInfo>> deliveryObserver;

    private final ServiceConnection connection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder binder) {
            service = ((LockScreenNotificationService.LocalBinder) binder).getService();
            serviceBound = true;
            serviceStarting = false;
            FileLog.getInstance().debug(TAG, "Service connected (global)");
            updateServiceIfNeeded();
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            serviceBound = false;
            serviceStarting = false;
            service = null;
            FileLog.getInstance().debug(TAG, "Service disconnected (global)");
        }
    };

    public static synchronized LockScreenNotificationManager getInstance(Context context) {
        if (instance == null) {
            instance = new LockScreenNotificationManager(context.getApplicationContext());
        }
        return instance;
    }

    private LockScreenNotificationManager(Context context) {
        this.appContext = context;
    }

    /**
     * Start monitoring deliveries and location updates
     */
    public void startMonitoring(MapViewModel viewModel) {
        if (viewModel == null) {
            FileLog.getInstance().debug(TAG, "startMonitoring skipped: viewModel null");
            return;
        }

        // 如果之前绑的是另一个 ViewModel，先清理再重新绑定，避免旋转后旧 observer 泄漏
        if (boundViewModel != null && boundViewModel != viewModel) {
            stopMonitoring(boundViewModel);
        }

        if (isMonitoring && boundViewModel == viewModel) {
            FileLog.getInstance().debug(TAG, "Already monitoring (same ViewModel)");
            return;
        }

        isMonitoring = true;
        FileLog.getInstance().debug(TAG, "Starting monitoring");
        boundViewModel = viewModel;

        // Create observer
        deliveryObserver = this::onDeliveriesChanged;

        // Observe delivery list changes
        boundViewModel.getMapItemsLive().observeForever(deliveryObserver);

        // Register for location updates
        SmartLocationManager locationManager = SmartLocationManager.getInstance(appContext);
        if (locationManager != null) {
            try {
                locationManager.addLocationUpdateListener(this);
                FileLog.getInstance().debug(TAG, "Registered for location updates");
            } catch (Throwable t) {
                FileLog.getInstance().error(TAG, "Failed to register location listener", t);
            }
        }
    }

    /**
     * Stop monitoring
     */
    public void stopMonitoring(MapViewModel viewModel) {
        if (!isMonitoring) {
            return;
        }

        isMonitoring = false;
        FileLog.getInstance().debug(TAG, "Stopping monitoring");

        // Remove observer
        if (deliveryObserver != null) {
            if (viewModel != null) {
                viewModel.getMapItemsLive().removeObserver(deliveryObserver);
            } else if (boundViewModel != null) {
                boundViewModel.getMapItemsLive().removeObserver(deliveryObserver);
            }
            deliveryObserver = null;
        }
        boundViewModel = null;

        // Unregister from location updates
        SmartLocationManager locationManager = SmartLocationManager.getInstance(appContext);
        if (locationManager != null) {
            try {
                locationManager.removeLocationUpdateListener(this);
            } catch (Throwable ignore) {
            }
        }

        // Stop service
        stopService();
    }

    private void onDeliveriesChanged(List<DeliveryInfo> newDeliveries) {
        this.deliveries = newDeliveries;

        if (newDeliveries == null || newDeliveries.isEmpty()) {
            FileLog.getInstance().debug(TAG, "No deliveries, clearing notification");
            clear();
            return;
        }
        int count = newDeliveries.size();

        // Calculate nearest delivery
        if (lastLocation != null) {
            DeliveryInfo nearest = findNearest(newDeliveries, lastLocation);
            if (nearest != null) {
                float distance = calculateDistance(nearest, lastLocation);
                updateDelivery(nearest, distance, count);
                FileLog.getInstance().debug(TAG, "Updated nearest delivery: "
                        + nearest.getRouteNumber() + ", distance=" + distance + "m");
            }
        } else {
            // No location yet, just update with first delivery
            DeliveryInfo first = newDeliveries.get(0);
            updateDelivery(first, -1f, count);
            FileLog.getInstance().debug(TAG, "No location, using first delivery: "
                    + first.getRouteNumber());
        }
    }

    @Override
    public void onLocationUpdate(Location location, SmartLocationManager.MovementState state) {
        lastLocation = location;

        // Recalculate nearest delivery
        if (deliveries != null && !deliveries.isEmpty()) {
            DeliveryInfo nearest = findNearest(deliveries, location);
            if (nearest != null) {
                float distance = calculateDistance(nearest, location);
                updateDelivery(nearest, distance, deliveries.size());
            }
        }
    }

    /**
     * Manually update delivery (fallback)
     */
    public void updateDelivery(DeliveryInfo delivery, float distance, int count) {
        this.currentDelivery = delivery;
        this.currentDistance = distance;
        // count 兜底
        if (count <= 0 && deliveries != null) {
            count = deliveries.size();
        }

        if (!serviceBound && !serviceStarting) {
            startService();
        } else {
            updateServiceIfNeeded();
        }
    }

    private void startService() {
        if (serviceBound || serviceStarting) {
            return;
        }
        serviceStarting = true;
        try {
            Intent intent = new Intent(appContext, LockScreenNotificationService.class);
            appContext.startForegroundService(intent);
            appContext.bindService(intent, connection, Context.BIND_AUTO_CREATE);
            FileLog.getInstance().debug(TAG, "Service started (global)");
        } catch (Throwable t) {
            serviceStarting = false;
            FileLog.getInstance().error(TAG, "Failed to start service", t);
        }
    }

    private void updateServiceIfNeeded() {
        if (serviceBound && service != null && currentDelivery != null) {
            int count = (deliveries == null || deliveries.isEmpty()) ? 1 : deliveries.size();
            service.updateDelivery(currentDelivery, currentDistance, count);
        }
    }

    /**
     * Clear notification
     */
    public void clear() {
        stopService();
        currentDelivery = null;
        currentDistance = -1f;
    }

    private void stopService() {
        if (serviceBound) {
            try {
                appContext.unbindService(connection);
                serviceBound = false;
                FileLog.getInstance().debug(TAG, "Service unbound (global)");
            } catch (Throwable ignore) {
            }
        }
        if (service != null) {
            try {
                service.clearNotification();
            } catch (Throwable ignore) {
            }
            service = null;
        }
    }

    private DeliveryInfo findNearest(List<DeliveryInfo> deliveries, Location location) {
        if (deliveries == null || deliveries.isEmpty() || location == null) {
            return null;
        }

        DeliveryInfo nearest = null;
        float minDistance = Float.MAX_VALUE;

        for (DeliveryInfo delivery : deliveries) {
            float distance = calculateDistance(delivery, location);
            if (distance < minDistance) {
                minDistance = distance;
                nearest = delivery;
            }
        }

        return nearest;
    }

    private float calculateDistance(DeliveryInfo delivery, Location location) {
        if (delivery == null || location == null) {
            return -1f;
        }

        float[] results = new float[1];
        Location.distanceBetween(
                location.getLatitude(),
                location.getLongitude(),
                delivery.getLatitude(),
                delivery.getLongitude(),
                results);
        return results[0];
    }
}
