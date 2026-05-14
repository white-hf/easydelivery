package com.hf.easydelivery.map;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.hf.easydelivery.dao.DeliveryInfo;

import java.util.ArrayList;
import java.util.List;

final class DefaultParcelPresentationPolicy implements ParcelPresentationPolicy {
    @NonNull
    @Override
    public List<DeliveryInfo> selectVisibleParcels(@NonNull List<DeliveryInfo> deliveries,
            @Nullable DriverLocationSnapshot driverLocation,
            @Nullable DeliveryInfo currentPrimaryDelivery) {
        return new ArrayList<>(deliveries);
    }
}
