package com.hf.easydelivery.map;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.hf.easydelivery.dao.DeliveryInfo;

/**
 * Browse mode keeps markers lighter, but preserves strong cues for focus and large parcels.
 * Browse 模式下 marker 更轻，但保持 focus 和大件的明显辨识。
 */
final class PowerSaverBrowseMarkerStylePolicy implements MarkerStylePolicy {
    @NonNull
    @Override
    public MarkerStyleDecision styleFor(@NonNull DeliveryInfo info,
            @Nullable DeliveryInfo currentPrimaryDelivery,
            boolean isLargeParcel,
            @NonNull MarkerColorTone colorTone) {
        boolean highlighted = currentPrimaryDelivery != null
                && info.getStableKey().equals(currentPrimaryDelivery.getStableKey());
        return new MarkerStyleDecision(colorTone, true, highlighted, isLargeParcel);
    }
}
