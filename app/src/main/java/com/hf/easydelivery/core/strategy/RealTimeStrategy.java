package com.hf.easydelivery.core.strategy;

import com.hf.easydelivery.core.policy.LocationRequestParams;
import com.google.android.gms.location.Priority;

public final class RealTimeStrategy implements LocationStrategy {
    @Override
    public LocationRequestParams getParams(LocationContext context) {
        return new LocationRequestParams(
                StrategyConfig.getRealtimeIntervalMs(),
                StrategyConfig.getRealtimeMinIntervalMs(),
                200,
                StrategyConfig.getRealtimeMinDistanceM(),
                Priority.PRIORITY_HIGH_ACCURACY,
                200L
        );
    }

    @Override
    public boolean supportsBurst() {
        return false;
    }

    @Override
    public void onModeEnter() {
        // no-op
    }

    @Override
    public void onModeExit() {
        // no-op
    }

    @Override
    public void onSuggestBoost(BoostReason reason) {
        // no-op: realtime mode ignores boost
    }
}
