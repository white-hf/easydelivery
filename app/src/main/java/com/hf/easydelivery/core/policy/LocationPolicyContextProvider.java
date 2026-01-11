package com.hf.easydelivery.core.policy;

import androidx.annotation.NonNull;

public final class LocationPolicyContextProvider {
    private final PolicyContextStateProvider stateProvider;

    public LocationPolicyContextProvider(@NonNull PolicyContextStateProvider stateProvider) {
        this.stateProvider = stateProvider;
    }

    public LocationPolicyContext build() {
        return new LocationPolicyContext(
                stateProvider.getMovementState(),
                stateProvider.isInBurstMode(),
                stateProvider.isDeliveringAndIdle(),
                stateProvider.isUiFollowActive(),
                stateProvider.isMovingFlag());
    }
}
