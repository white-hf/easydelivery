package com.hf.easydelivery.core.config;

public final class SmartLocationTuning {
    private SmartLocationTuning() {
    }

    public static final float WEAK_SIGNAL_THRESHOLD = 100f;
    public static final int WEAK_SIGNAL_REQUIRED_HITS = 3;
    public static final long WEAK_SIGNAL_DURATION_MS = 15_000L;
    public static final long MIN_DISPATCH_INTERVAL_MOVING_MS = 250L;
    public static final long MIN_DISPATCH_INTERVAL_STATIONARY_MS = 800L;

    public static final long BOOST_MIN_INTERVAL_MS = 20_000L;
    public static final long LOW_PRIORITY_BOOST_COOLDOWN_MS = 25_000L;
    public static final long DISPLACEMENT_WAKE_WINDOW_MS = 5_000L;
    public static final int DISPLACEMENT_WAKE_REQUIRED_HITS = 2;
    public static final float DISPLACEMENT_WAKE_THRESHOLD_M = 25.0f;
    public static final long EDGE_RISK_WINDOW_MS = 4_000L;
    public static final int EDGE_RISK_REQUIRED_HITS = 2;
    public static final long JUMP_RISK_WINDOW_MS = 5_000L;
    public static final int JUMP_RISK_REQUIRED_HITS = 2;

    public static final long DRIVING_DOWNGRADE_GRACE_MS = 5_000L;
    public static final long IN_VEHICLE_GRACE_MS = 15_000L;
    public static final float DISPLACEMENT_DRIVING_OVERRIDE_M = 12f;
    public static final long MOVING_HOLD_MS = 15_000L;
    public static final long UI_FORCE_DISPATCH_MS = 1_200L;
    public static final long SINGLE_FIX_BACKOFF_BASE_MS = 8_000L;
    public static final long SINGLE_FIX_BACKOFF_MAX_MS = 60_000L;

    public static final long DELIVERING_IDLE_THRESHOLD_MS = 180_000L;
    public static final long INTERVAL_DRIVING_NORMAL_MS = 1_500L;
    public static final long INTERVAL_DRIVING_SLOW_MS = 2_500L;
    public static final long INTERVAL_WALKING_MS = 2_000L;
    public static final long INTERVAL_DELIVERING_MS = 45_000L;

    public static final long MIN_INTERVAL_DRIVING_NORMAL_MS = 800L;
    public static final long MIN_INTERVAL_DRIVING_SLOW_MS = 1_500L;
    public static final long MIN_INTERVAL_WALKING_MS = 1_000L;
    public static final long MIN_INTERVAL_DELIVERING_MS = 30_000L;

    public static final float MIN_PREDICTION_SPEED_MPS = 0.8f;
    public static final double EARTH_RADIUS_METERS = 6378137.0;
}
