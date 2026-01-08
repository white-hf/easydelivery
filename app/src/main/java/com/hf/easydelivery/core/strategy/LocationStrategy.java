package com.hf.easydelivery.core.strategy;

import com.hf.easydelivery.core.policy.LocationRequestParams;

public interface LocationStrategy {
    LocationRequestParams getParams(LocationContext context);
    boolean supportsBurst();
    void onModeEnter();
    void onModeExit();
    void onSuggestBoost(BoostReason reason);
}
