package com.hf.easydelivery.map;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.hf.easydelivery.dao.DeliveryInfo;

final class DefaultMarkerStylePolicy implements MarkerStylePolicy {
    @NonNull
    @Override
    public MarkerStyleDecision styleFor(@NonNull DeliveryInfo info,
            @Nullable DeliveryInfo currentPrimaryDelivery,
            boolean isLargeParcel,
            @NonNull MarkerColorTone colorTone) {
        boolean highlighted = currentPrimaryDelivery != null
                && info.getStableKey().equals(currentPrimaryDelivery.getStableKey());
        return new MarkerStyleDecision(colorTone, false, highlighted, isLargeParcel);
    }
}
