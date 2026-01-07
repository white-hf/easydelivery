package com.hf.easydelivery.core.policy;

import com.google.android.gms.location.Priority;

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

        if (context.deliveringIdle) {
            return new LocationRequestParams(
                    6000L,
                    3000L,
                    Priority.PRIORITY_BALANCED_POWER_ACCURACY,
                    5.0f,
                    400L,
                    800L);
        }

        return new LocationRequestParams(
                1500L,
                800L,
                Priority.PRIORITY_HIGH_ACCURACY,
                0.8f,
                200L,
                200L);
    }
}
