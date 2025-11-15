package com.hf.easydelivery.core;

import android.content.Context;
import android.location.Location;

import com.hf.courierservice.apihelper.FileLog;
import com.hf.easydelivery.ResourceMgr;
import com.hf.easydelivery.domain.WorkStatsRepository;

public class DrivingDistanceTracker {

    private static final float MIN_DISTANCE_METERS = 1f;
    private static final float MAX_DISTANCE_METERS = 2000f;
    private static final float MAX_ACCURACY_METERS = 50f;
    private static final long LOG_INTERVAL_MS = 60_000L;


    private static DrivingDistanceTracker instance;
    private final WorkStatsRepository repository;
    private Location lastLocation;
    private SmartLocationManager.MovementState lastState = SmartLocationManager.MovementState.STATIONARY;
    private float pendingLogDistance = 0f;
    private long lastLogWallTimeMs = 0L;

    private DrivingDistanceTracker(Context context) {
        repository = new WorkStatsRepository(context.getApplicationContext());
    }

    public static synchronized DrivingDistanceTracker getInstance(Context context) {
        if (instance == null) {
            instance = new DrivingDistanceTracker(context);
        }
        return instance;
    }

    public void onLocationUpdate(Location location, SmartLocationManager.MovementState state) {
        if (location == null) return;
        if (location.hasAccuracy() && location.getAccuracy() > MAX_ACCURACY_METERS) {
            return;
        }
        if (state == SmartLocationManager.MovementState.STATIONARY) {
            flushPending(location.getTime(), true);
            lastLocation = location;
            lastState = state;
            return;
        }
        if (lastLocation == null) {
            lastLocation = new Location(location);
            lastState = state;
            return;
        }
        float distance = location.distanceTo(lastLocation);
        if (distance < MIN_DISTANCE_METERS || distance > MAX_DISTANCE_METERS) {
            lastLocation = new Location(location);
            lastState = state;
            return;
        }
        accumulateDistance(distance, location.getTime());

        lastLocation = new Location(location);
        lastState = state;
    }

    private void accumulateDistance(float distanceMeters, long locationTimeMs) {
        if (distanceMeters <= 0f) {
            return;
        }
        pendingLogDistance += distanceMeters;
        if (lastLogWallTimeMs == 0L) {
            lastLogWallTimeMs = System.currentTimeMillis();
        }
        flushPending(locationTimeMs, false);
    }

    private void flushPending(long locationTimeMs, boolean force) {
        if (pendingLogDistance <= 0f) {
            return;
        }
        long now = System.currentTimeMillis();
        long elapsed = now - lastLogWallTimeMs;
        if (!force && elapsed < LOG_INTERVAL_MS) {
            return;
        }
        repository.addDistanceMeters(pendingLogDistance, locationTimeMs);
        pendingLogDistance = 0f;
        lastLogWallTimeMs = now;
    }


}
