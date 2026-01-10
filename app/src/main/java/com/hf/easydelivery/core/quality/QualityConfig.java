package com.hf.easydelivery.core.quality;

public final class QualityConfig {
    private QualityConfig() {}

    private static volatile float accuracyThresholdPoor = 50f;
    private static volatile float accuracyThresholdStateMax = 120f;
    private static volatile long poorSignalGracePeriodMs = 15_000L;
    private static volatile long emergencyBoostThresholdMs = 15_000L;
    private static volatile long emergencyBoostCooldownMs = 15_000L;
    private static volatile float maxPlausibleSpeedMps = 45f;
    private static volatile float maxPlausibleSpeedMpsGood = 60f;

    public static float getAccuracyThresholdPoor() {
        return accuracyThresholdPoor;
    }

    public static float getAccuracyThresholdStateMax() {
        return accuracyThresholdStateMax;
    }

    public static long getPoorSignalGracePeriodMs() {
        return poorSignalGracePeriodMs;
    }

    public static long getEmergencyBoostThresholdMs() {
        return emergencyBoostThresholdMs;
    }

    public static long getEmergencyBoostCooldownMs() {
        return emergencyBoostCooldownMs;
    }

    public static float getMaxPlausibleSpeedMps() {
        return maxPlausibleSpeedMps;
    }

    public static float getMaxPlausibleSpeedMpsGood() {
        return maxPlausibleSpeedMpsGood;
    }
}
