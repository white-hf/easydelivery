package com.hf.easydelivery.map;

/**
 * Centralized tunables for map/camera/proximity/notification to avoid scattered magic numbers.
 */
public final class MapConfig {
    private MapConfig() {}

    // Proximity / InfoPill
    public static final float NEARBY_RADIUS_METERS = 50f;
    public static final int NEARBY_LIMIT = 20;
    public static final float DISTANCE_EPSILON_M = 0.8f;

    // Zoom / smart zoom
    public static final float DEFAULT_FOLLOW_ZOOM = 15f;
    public static final float CLOSE_DISTANCE_METERS = 90f;
    public static final float APPROACH_DISTANCE_METERS = 260f;
    public static final float LEAVE_DISTANCE_METERS = 360f;

    // Camera update thresholds
    public static final long STATIONARY_TIME_MS = 1500L;
    public static final long DRIVING_TIME_MS = 1200L;
    public static final float STATIONARY_DIST_M = 2f;
    public static final float DRIVING_DIST_M = 5f;

    // Notification (lock screen) thresholds
    public static final long NOTIF_MIN_INTERVAL_MS = 5000L;
    public static final long NOTIF_MIN_INTERVAL_STATIONARY_MS = 8000L;
    public static final float NOTIF_MIN_DISTANCE_M = 5f;
}
