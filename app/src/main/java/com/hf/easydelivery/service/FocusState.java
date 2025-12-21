package com.hf.easydelivery.service;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.hf.easydelivery.dao.DeliveryInfo;

/**
 * Single Source of Truth payload for lockscreen notification.
 * Produced by InfoPill decision pipeline; consumed by lockscreen module.
 */
public final class FocusState {
    @NonNull
    public final String stableId;
    @NonNull
    public final DeliveryInfo delivery;
    @Nullable
    public final Float distanceMeters;

    public FocusState(@NonNull String stableId,
            @NonNull DeliveryInfo delivery,
            @Nullable Float distanceMeters) {
        this.stableId = stableId;
        this.delivery = delivery;
        this.distanceMeters = distanceMeters;
    }
}

