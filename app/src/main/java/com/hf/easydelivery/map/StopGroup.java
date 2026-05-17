package com.hf.easydelivery.map;

import androidx.annotation.NonNull;

import com.google.android.gms.maps.model.LatLng;
import com.hf.easydelivery.dao.DeliveryInfo;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

final class StopGroup {
    private final List<DeliveryInfo> deliveries = new ArrayList<>();
    private double centroidLat;
    private double centroidLng;

    StopGroup(@NonNull DeliveryInfo seed) {
        deliveries.add(seed);
        centroidLat = seed.getLatitude();
        centroidLng = seed.getLongitude();
    }

    void add(@NonNull DeliveryInfo info) {
        int nextSize = deliveries.size() + 1;
        centroidLat = (centroidLat * deliveries.size() + info.getLatitude()) / nextSize;
        centroidLng = (centroidLng * deliveries.size() + info.getLongitude()) / nextSize;
        deliveries.add(info);
    }

    int size() {
        return deliveries.size();
    }

    @NonNull
    List<DeliveryInfo> getDeliveries() {
        return Collections.unmodifiableList(deliveries);
    }

    @NonNull
    LatLng getCenter() {
        return new LatLng(centroidLat, centroidLng);
    }

    boolean contains(@NonNull DeliveryInfo info) {
        for (DeliveryInfo delivery : deliveries) {
            if (delivery.getStableKey().equals(info.getStableKey())) {
                return true;
            }
        }
        return false;
    }
}
