package com.hf.easydelivery.map;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.hf.easydelivery.dao.DeliveryInfo;

import java.util.List;

interface ParcelPresentationPolicy {
    @NonNull
    List<DeliveryInfo> selectVisibleParcels(@NonNull List<DeliveryInfo> deliveries,
            @Nullable DriverLocationSnapshot driverLocation,
            @Nullable DeliveryInfo currentPrimaryDelivery);
}
