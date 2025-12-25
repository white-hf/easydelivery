package com.hf.easydelivery.core.policy;

import com.google.android.gms.location.Priority;
import com.hf.easydelivery.core.SmartLocationManager;

public final class EcoLocationPolicy implements LocationPolicy {
    @Override
    public LocationRequestParams getRequestParams(LocationPolicyContext context) {
        if (context.inBurst) {
            return new LocationRequestParams(
                    1000L,
                    800L,
                    Priority.PRIORITY_HIGH_ACCURACY,
                    0.8f,
                    800L,
                    500L);
        }

        long interval;
        long minInterval;
        float minDistance;
        long minDispatch;
        int priority = Priority.PRIORITY_BALANCED_POWER_ACCURACY;

        if (context.deliveringIdle) {
            interval = 15000L;
            minInterval = 8000L;
            minDistance = 8.0f;
            minDispatch = 1200L;
            return new LocationRequestParams(interval, minInterval, priority, minDistance, 1200L, minDispatch);
        }

        SmartLocationManager.MovementState state = context.state;
        switch (state) {
            case NORMAL_DRIVING:
                interval = 4000L;
                minInterval = 2000L;
                minDistance = 2.5f;
                minDispatch = 800L;
                break;
            case SLOW_DRIVING:
                interval = 4500L;
                minInterval = 2200L;
                minDistance = 2.5f;
                minDispatch = 900L;
                break;
            case WALKING:
                interval = 5000L;
                minInterval = 2500L;
                minDistance = 3.0f;
                minDispatch = 1000L;
                break;
            case STATIONARY:
            default:
                interval = 6000L;
                minInterval = 3000L;
                minDistance = 6.0f;
                minDispatch = 1200L;
                break;
        }

        return new LocationRequestParams(interval, minInterval, priority, minDistance, 1200L, minDispatch);
    }
}
