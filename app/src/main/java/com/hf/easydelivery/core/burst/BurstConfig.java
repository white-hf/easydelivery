package com.hf.easydelivery.core.burst;

public final class BurstConfig {
    private BurstConfig() {}

    private static final long BOOST_MIN_INTERVAL_MS = 20_000L;
    private static final long LOW_PRIORITY_BOOST_COOLDOWN_MS = 25_000L;
    private static final long DISPLACEMENT_WAKE_WINDOW_MS = 5_000L;
    private static final int DISPLACEMENT_WAKE_REQUIRED_HITS = 2;
    private static final float DISPLACEMENT_WAKE_THRESHOLD_M = 25.0f;
    private static final long EDGE_RISK_WINDOW_MS = 4_000L;
    private static final int EDGE_RISK_REQUIRED_HITS = 2;
    private static final long JUMP_RISK_WINDOW_MS = 5_000L;
    private static final int JUMP_RISK_REQUIRED_HITS = 2;
    private static final long MOVING_HOLD_MS = 15_000L;
    private static final long UI_FORCE_DISPATCH_MS = 1_200L;
    private static final long SINGLE_FIX_BACKOFF_BASE_MS = 8_000L;
    private static final long SINGLE_FIX_BACKOFF_MAX_MS = 60_000L;
    private static final long BURST_MODE_DURATION_MS = 60_000L;

    public static long getBoostMinIntervalMs() {
        return BOOST_MIN_INTERVAL_MS;
    }

    public static long getLowPriorityBoostCooldownMs() {
        return LOW_PRIORITY_BOOST_COOLDOWN_MS;
    }

    public static long getDisplacementWakeWindowMs() {
        return DISPLACEMENT_WAKE_WINDOW_MS;
    }

    public static int getDisplacementWakeRequiredHits() {
        return DISPLACEMENT_WAKE_REQUIRED_HITS;
    }

    public static float getDisplacementWakeThresholdM() {
        return DISPLACEMENT_WAKE_THRESHOLD_M;
    }

    public static long getEdgeRiskWindowMs() {
        return EDGE_RISK_WINDOW_MS;
    }

    public static int getEdgeRiskRequiredHits() {
        return EDGE_RISK_REQUIRED_HITS;
    }

    public static long getJumpRiskWindowMs() {
        return JUMP_RISK_WINDOW_MS;
    }

    public static int getJumpRiskRequiredHits() {
        return JUMP_RISK_REQUIRED_HITS;
    }

    public static long getMovingHoldMs() {
        return MOVING_HOLD_MS;
    }

    public static long getUiForceDispatchMs() {
        return UI_FORCE_DISPATCH_MS;
    }

    public static long getSingleFixBackoffBaseMs() {
        return SINGLE_FIX_BACKOFF_BASE_MS;
    }

    public static long getSingleFixBackoffMaxMs() {
        return SINGLE_FIX_BACKOFF_MAX_MS;
    }

    public static long getBurstModeDurationMs() {
        return BURST_MODE_DURATION_MS;
    }
}
