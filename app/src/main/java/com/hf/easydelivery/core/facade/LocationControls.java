package com.hf.easydelivery.core.facade;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.hf.easydelivery.core.policy.LocationPolicy;

public interface LocationControls {
    void requestBoost(long durationMs, @NonNull String reason);

    void requestBoostForce(long durationMs, @NonNull String reason);

    void requestBoostIfEdgeRisk(float offsetMeters, float speedMps);

    void requestSingleHighAccuracyFix();

    void setUiFollowActive(boolean active);

    void setLocationPolicy(@Nullable LocationPolicy policy);

    boolean isForegroundTrackingActive();

    void startForegroundTracking();

    void stopForegroundTracking(boolean resumeNormal);
}
