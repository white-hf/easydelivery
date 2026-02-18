package com.hf.easydelivery.core.strategy;

public final class StrategyConfig {
    private StrategyConfig() {
    }

    private static final long REALTIME_INTERVAL_MIN_MS = 1_000L;
    private static final long REALTIME_INTERVAL_MAX_MS = 2_000L;
    private static final long REALTIME_MIN_INTERVAL_MIN_MS = 500L;
    private static final long REALTIME_MIN_INTERVAL_MAX_MS = 1_200L;
    private static final float REALTIME_MIN_DISTANCE_MIN_M = 0.3f;
    private static final float REALTIME_MIN_DISTANCE_MAX_M = 1.0f;

    private static final long POWERSAVE_COOLDOWN_MIN_MS = 20_000L;
    private static final long POWERSAVE_COOLDOWN_MAX_MS = 60_000L;
    private static final long POWERSAVE_MOVING_INTERVAL_MIN_MS = 5_000L;
    private static final long POWERSAVE_MOVING_INTERVAL_MAX_MS = 12_000L;
    private static final long POWERSAVE_STATIONARY_INTERVAL_MIN_MS = 5_000L;
    private static final long POWERSAVE_STATIONARY_INTERVAL_MAX_MS = 15_000L;
    private static final long POWERSAVE_MIN_INTERVAL_MIN_MS = 3_000L;
    private static final long POWERSAVE_MIN_INTERVAL_MAX_MS = 10_000L;
    private static final float POWERSAVE_MIN_DISTANCE_MOVING_MIN_M = 1.0f;
    private static final float POWERSAVE_MIN_DISTANCE_MOVING_MAX_M = 4.0f;
    private static final float POWERSAVE_MIN_DISTANCE_STATIONARY_MIN_M = 1.5f;
    private static final float POWERSAVE_MIN_DISTANCE_STATIONARY_MAX_M = 6.0f;
    private static final long BURST_DURATION_MIN_MS = 8_000L;
    private static final long BURST_DURATION_MAX_MS = 15_000L;

    private static final long MIN_DISPATCH_INTERVAL_MOVING_MS = 250L;
    private static final long MIN_DISPATCH_INTERVAL_STATIONARY_MS = 800L;
    private static final long DELIVERING_IDLE_THRESHOLD_MS = 180_000L;
    private static final long INTERVAL_DRIVING_NORMAL_MS = 1_500L;
    private static final long INTERVAL_DRIVING_SLOW_MS = 2_500L;
    private static final long INTERVAL_WALKING_MS = 2_000L;
    private static final long INTERVAL_DELIVERING_MS = 45_000L;
    private static final long MIN_INTERVAL_DRIVING_NORMAL_MS = 800L;
    private static final long MIN_INTERVAL_DRIVING_SLOW_MS = 1_500L;
    private static final long MIN_INTERVAL_WALKING_MS = 1_000L;
    private static final long MIN_INTERVAL_DELIVERING_MS = 30_000L;
    private static final long DRIVING_DOWNGRADE_GRACE_MS = 5_000L;
    private static final long IN_VEHICLE_GRACE_MS = 15_000L;
    private static final float DISPLACEMENT_DRIVING_OVERRIDE_M = 12f;
    private static final float MIN_PREDICTION_SPEED_MPS = 0.8f;
    private static final double EARTH_RADIUS_METERS = 6378137.0;

    private static volatile long realtimeIntervalMs = 1500L;
    private static volatile long realtimeMinIntervalMs = 800L;
    private static volatile float realtimeMinDistanceM = 0.5f;
    private static volatile long fgRealtimeIntervalMs = realtimeIntervalMs;
    private static volatile long fgRealtimeMinIntervalMs = realtimeMinIntervalMs;
    private static volatile float fgRealtimeMinDistanceM = realtimeMinDistanceM;
    private static volatile long burstIntervalMs = 1000L;
    private static volatile long burstMinIntervalMs = 1000L;
    private static volatile float burstMinDistanceM = 0.5f;
    private static volatile long burstMaxDelayMs = 800L;

    private static volatile long powerSaveCooldownMs = 30_000L;
    private static volatile long powerSaveIntervalMovingMs = 5_000L;
    private static volatile long powerSaveIntervalStationaryMs = 5_000L;
    private static volatile long powerSaveMinIntervalMs = 3_000L;
    private static volatile float powerSaveMinDistanceMovingM = 1.0f;
    private static volatile float powerSaveMinDistanceStationaryM = 2.0f;
    private static volatile long fgPowerSaveIntervalMs = powerSaveIntervalMovingMs;
    private static volatile long fgPowerSaveMinIntervalMs = powerSaveMinIntervalMs;
    private static volatile float fgPowerSaveMinDistanceM = powerSaveMinDistanceMovingM;

    private static volatile long burstDurationMs = 12_000L;

    public static void applyPerfBalance(float balance) {
        float t = clamp01(balance);
        setRealtimeIntervalMs(lerpLong(REALTIME_INTERVAL_MIN_MS, REALTIME_INTERVAL_MAX_MS, t));
        setRealtimeMinIntervalMs(lerpLong(REALTIME_MIN_INTERVAL_MIN_MS, REALTIME_MIN_INTERVAL_MAX_MS, t));
        setRealtimeMinDistanceM(lerpFloat(REALTIME_MIN_DISTANCE_MIN_M, REALTIME_MIN_DISTANCE_MAX_M, t));

        setPowerSaveCooldownMs(lerpLong(POWERSAVE_COOLDOWN_MIN_MS, POWERSAVE_COOLDOWN_MAX_MS, t));
        setPowerSaveIntervalMovingMs(lerpLong(POWERSAVE_MOVING_INTERVAL_MIN_MS, POWERSAVE_MOVING_INTERVAL_MAX_MS, t));
        setPowerSaveIntervalStationaryMs(
                lerpLong(POWERSAVE_STATIONARY_INTERVAL_MIN_MS, POWERSAVE_STATIONARY_INTERVAL_MAX_MS, t));
        setPowerSaveMinIntervalMs(lerpLong(POWERSAVE_MIN_INTERVAL_MIN_MS, POWERSAVE_MIN_INTERVAL_MAX_MS, t));
        setPowerSaveMinDistanceMovingM(
                lerpFloat(POWERSAVE_MIN_DISTANCE_MOVING_MIN_M, POWERSAVE_MIN_DISTANCE_MOVING_MAX_M, t));
        setPowerSaveMinDistanceStationaryM(
                lerpFloat(POWERSAVE_MIN_DISTANCE_STATIONARY_MIN_M, POWERSAVE_MIN_DISTANCE_STATIONARY_MAX_M, t));
        setBurstDurationMs(lerpLong(BURST_DURATION_MIN_MS, BURST_DURATION_MAX_MS, t));
    }

    private static long lerpLong(long min, long max, float t) {
        return Math.round(min + (max - min) * t);
    }

    private static float lerpFloat(float min, float max, float t) {
        return min + (max - min) * t;
    }

    private static float clamp01(float v) {
        if (v < 0f)
            return 0f;
        if (v > 1f)
            return 1f;
        return v;
    }

    public static long getRealtimeIntervalMs() {
        return realtimeIntervalMs;
    }

    public static void setRealtimeIntervalMs(long value) {
        realtimeIntervalMs = Math.max(500L, value);
    }

    public static long getRealtimeMinIntervalMs() {
        return realtimeMinIntervalMs;
    }

    public static void setRealtimeMinIntervalMs(long value) {
        realtimeMinIntervalMs = Math.max(250L, value);
    }

    public static float getRealtimeMinDistanceM() {
        return realtimeMinDistanceM;
    }

    public static void setRealtimeMinDistanceM(float value) {
        realtimeMinDistanceM = Math.max(0f, value);
    }

    public static long getFgRealtimeIntervalMs() {
        return fgRealtimeIntervalMs;
    }

    public static void setFgRealtimeIntervalMs(long value) {
        fgRealtimeIntervalMs = Math.max(500L, value);
    }

    public static long getFgRealtimeMinIntervalMs() {
        return fgRealtimeMinIntervalMs;
    }

    public static void setFgRealtimeMinIntervalMs(long value) {
        fgRealtimeMinIntervalMs = Math.max(250L, value);
    }

    public static float getFgRealtimeMinDistanceM() {
        return fgRealtimeMinDistanceM;
    }

    public static void setFgRealtimeMinDistanceM(float value) {
        fgRealtimeMinDistanceM = Math.max(0f, value);
    }

    public static long getPowerSaveCooldownMs() {
        return powerSaveCooldownMs;
    }

    public static void setPowerSaveCooldownMs(long value) {
        powerSaveCooldownMs = Math.max(5_000L, value);
    }

    public static long getPowerSaveIntervalMovingMs() {
        return powerSaveIntervalMovingMs;
    }

    public static void setPowerSaveIntervalMovingMs(long value) {
        powerSaveIntervalMovingMs = Math.max(3_000L, value);
    }

    public static long getPowerSaveIntervalStationaryMs() {
        return powerSaveIntervalStationaryMs;
    }

    public static void setPowerSaveIntervalStationaryMs(long value) {
        powerSaveIntervalStationaryMs = Math.max(3_000L, value);
    }

    public static long getPowerSaveMinIntervalMs() {
        return powerSaveMinIntervalMs;
    }

    public static void setPowerSaveMinIntervalMs(long value) {
        powerSaveMinIntervalMs = Math.max(2_000L, value);
    }

    public static float getPowerSaveMinDistanceMovingM() {
        return powerSaveMinDistanceMovingM;
    }

    public static void setPowerSaveMinDistanceMovingM(float value) {
        powerSaveMinDistanceMovingM = Math.max(1.0f, value);
    }

    public static float getPowerSaveMinDistanceStationaryM() {
        return powerSaveMinDistanceStationaryM;
    }

    public static void setPowerSaveMinDistanceStationaryM(float value) {
        powerSaveMinDistanceStationaryM = Math.max(2.0f, value);
    }

    public static long getFgPowerSaveIntervalMs() {
        return fgPowerSaveIntervalMs;
    }

    public static void setFgPowerSaveIntervalMs(long value) {
        fgPowerSaveIntervalMs = Math.max(3_000L, value);
    }

    public static long getFgPowerSaveMinIntervalMs() {
        return fgPowerSaveMinIntervalMs;
    }

    public static void setFgPowerSaveMinIntervalMs(long value) {
        fgPowerSaveMinIntervalMs = Math.max(2_000L, value);
    }

    public static float getFgPowerSaveMinDistanceM() {
        return fgPowerSaveMinDistanceM;
    }

    public static void setFgPowerSaveMinDistanceM(float value) {
        fgPowerSaveMinDistanceM = Math.max(0f, value);
    }

    public static long getBurstDurationMs() {
        return burstDurationMs;
    }

    public static void setBurstDurationMs(long value) {
        burstDurationMs = Math.max(5_000L, value);
    }

    public static long getBurstIntervalMs() {
        return burstIntervalMs;
    }

    public static void setBurstIntervalMs(long value) {
        burstIntervalMs = Math.max(500L, value);
    }

    public static long getBurstMinIntervalMs() {
        return burstMinIntervalMs;
    }

    public static void setBurstMinIntervalMs(long value) {
        burstMinIntervalMs = Math.max(250L, value);
    }

    public static float getBurstMinDistanceM() {
        return burstMinDistanceM;
    }

    public static void setBurstMinDistanceM(float value) {
        burstMinDistanceM = Math.max(0f, value);
    }

    public static long getBurstMaxDelayMs() {
        return burstMaxDelayMs;
    }

    public static void setBurstMaxDelayMs(long value) {
        burstMaxDelayMs = Math.max(0L, value);
    }

    public static long getMinDispatchIntervalMovingMs() {
        return MIN_DISPATCH_INTERVAL_MOVING_MS;
    }

    public static long getMinDispatchIntervalStationaryMs() {
        return MIN_DISPATCH_INTERVAL_STATIONARY_MS;
    }

    public static long getDeliveringIdleThresholdMs() {
        return DELIVERING_IDLE_THRESHOLD_MS;
    }

    public static long getIntervalDrivingNormalMs() {
        return INTERVAL_DRIVING_NORMAL_MS;
    }

    public static long getIntervalDrivingSlowMs() {
        return INTERVAL_DRIVING_SLOW_MS;
    }

    public static long getIntervalWalkingMs() {
        return INTERVAL_WALKING_MS;
    }

    public static long getIntervalDeliveringMs() {
        return INTERVAL_DELIVERING_MS;
    }

    public static long getMinIntervalDrivingNormalMs() {
        return MIN_INTERVAL_DRIVING_NORMAL_MS;
    }

    public static long getMinIntervalDrivingSlowMs() {
        return MIN_INTERVAL_DRIVING_SLOW_MS;
    }

    public static long getMinIntervalWalkingMs() {
        return MIN_INTERVAL_WALKING_MS;
    }

    public static long getMinIntervalDeliveringMs() {
        return MIN_INTERVAL_DELIVERING_MS;
    }

    public static long getDrivingDowngradeGraceMs() {
        return DRIVING_DOWNGRADE_GRACE_MS;
    }

    public static long getInVehicleGraceMs() {
        return IN_VEHICLE_GRACE_MS;
    }

    public static float getDisplacementDrivingOverrideM() {
        return DISPLACEMENT_DRIVING_OVERRIDE_M;
    }

    public static float getMinPredictionSpeedMps() {
        return MIN_PREDICTION_SPEED_MPS;
    }

    public static double getEarthRadiusMeters() {
        return EARTH_RADIUS_METERS;
    }
}
