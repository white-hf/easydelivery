package com.hf.easydelivery.core.strategy;

import com.google.android.gms.location.Priority;
import com.hf.easydelivery.core.policy.LocationRequestParams;

public final class PowerSaveStrategy implements LocationStrategy {
    @Override
    public LocationRequestParams getParams(LocationContext context) {
        long interval;
        float minDistance;
        switch (context.state) {
            case STATIONARY:
                interval = StrategyConfig.getPowerSaveIntervalStationaryMs();
                minDistance = StrategyConfig.getPowerSaveMinDistanceStationaryM();
                break;
            default:
                interval = StrategyConfig.getPowerSaveIntervalMovingMs();
                minDistance = StrategyConfig.getPowerSaveMinDistanceMovingM();
                break;
        }
        return new LocationRequestParams(
                interval,
                StrategyConfig.getPowerSaveMinIntervalMs(),
                Priority.PRIORITY_BALANCED_POWER_ACCURACY,
                minDistance,
                2_000L,
                800L
        );
    }

    @Override
    public boolean supportsBurst() {
        return true;
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
        // handled by StrategyManager
    }
}
