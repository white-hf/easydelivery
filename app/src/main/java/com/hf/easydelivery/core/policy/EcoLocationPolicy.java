package com.hf.easydelivery.core.policy;

import com.google.android.gms.location.Priority;

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

        if (context.deliveringIdle) {
            return new LocationRequestParams(
                    15000L,
                    8000L,
                    Priority.PRIORITY_BALANCED_POWER_ACCURACY,
                    8.0f,
                    1200L,
                    1200L);
        }

        return new LocationRequestParams(
                4000L,
                2000L,
                Priority.PRIORITY_BALANCED_POWER_ACCURACY,
                2.5f,
                1200L,
                800L);
    }
}
