package com.hf.easydelivery.map;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.hf.easydelivery.dao.DeliveryInfo;

interface MarkerStylePolicy {
    @NonNull
    MarkerStyleDecision styleFor(@NonNull DeliveryInfo info,
            @Nullable DeliveryInfo currentPrimaryDelivery,
            boolean isLargeParcel,
            @NonNull MarkerColorTone colorTone);
}
