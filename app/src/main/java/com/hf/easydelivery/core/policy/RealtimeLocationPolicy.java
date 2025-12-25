package com.hf.easydelivery.core.policy;

import com.google.android.gms.location.Priority;
import com.hf.easydelivery.core.SmartLocationManager;

public final class RealtimeLocationPolicy implements LocationPolicy {
    @Override
    public LocationRequestParams getRequestParams(LocationPolicyContext context) {
        if (context.inBurst) {
            return new LocationRequestParams(
                    1000L,
                    500L,
                    Priority.PRIORITY_HIGH_ACCURACY,
                    0.5f,
                    200L,
                    200L);
        }

        long interval;
        long minInterval;
        float minDistance;
        long minDispatch;
        int priority = Priority.PRIORITY_HIGH_ACCURACY;

        if (context.deliveringIdle) {
            interval = 6000L;
            minInterval = 3000L;
            minDistance = 5.0f;
            minDispatch = 800L;
            priority = Priority.PRIORITY_BALANCED_POWER_ACCURACY;
            return new LocationRequestParams(interval, minInterval, priority, minDistance, 400L, minDispatch);
        }

        SmartLocationManager.MovementState state = context.state;
        switch (state) {
            case NORMAL_DRIVING:
                interval = 1500L;
                minInterval = 800L;
                minDistance = 0.8f;
                minDispatch = 200L;
                break;
            case SLOW_DRIVING:
                interval = 2000L;
                minInterval = 1200L;
                minDistance = 1.0f;
                minDispatch = 250L;
                break;
            case WALKING:
                interval = 2000L;
                minInterval = 1200L;
                minDistance = 1.2f;
                minDispatch = 300L;
                priority = Priority.PRIORITY_BALANCED_POWER_ACCURACY;
                break;
            case STATIONARY:
            default:
                interval = 3000L;
                minInterval = 1500L;
                minDistance = 2.0f;
                minDispatch = 500L;
                priority = Priority.PRIORITY_BALANCED_POWER_ACCURACY;
                break;
        }

        return new LocationRequestParams(interval, minInterval, priority, minDistance, 200L, minDispatch);
    }
}
