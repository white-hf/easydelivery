package com.hf.easydelivery.map;

import androidx.annotation.NonNull;

import com.google.android.gms.maps.model.LatLng;
import com.hf.easydelivery.dao.DeliveryInfo;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

final class StopGroupBuilder {
    static final double DEFAULT_GROUP_RADIUS_METERS = 20d;
    private static final double EARTH_RADIUS_METERS = 6_371_000d;

    @NonNull
    List<StopGroup> build(@NonNull List<DeliveryInfo> deliveries) {
        if (deliveries.isEmpty()) {
            return Collections.emptyList();
        }
        List<StopGroup> groups = new ArrayList<>();
        for (DeliveryInfo delivery : deliveries) {
            StopGroup match = findCompatibleGroup(groups, delivery);
            if (match == null) {
                groups.add(new StopGroup(delivery));
            } else {
                match.add(delivery);
            }
        }
        return groups;
    }

    private StopGroup findCompatibleGroup(@NonNull List<StopGroup> groups, @NonNull DeliveryInfo delivery) {
        StopGroup best = null;
        double bestDistance = Double.MAX_VALUE;
        for (StopGroup group : groups) {
            double distance = maxDistanceToGroup(group, delivery);
            if (distance <= DEFAULT_GROUP_RADIUS_METERS && distance < bestDistance) {
                best = group;
                bestDistance = distance;
            }
        }
        return best;
    }

    private double maxDistanceToGroup(@NonNull StopGroup group, @NonNull DeliveryInfo delivery) {
        double maxDistance = 0d;
        for (DeliveryInfo existing : group.getDeliveries()) {
            double distance = haversineMeters(existing.getLatitude(), existing.getLongitude(),
                    delivery.getLatitude(), delivery.getLongitude());
            if (distance > maxDistance) {
                maxDistance = distance;
            }
        }
        return maxDistance;
    }

    private double haversineMeters(double lat1, double lng1, double lat2, double lng2) {
        double dLat = Math.toRadians(lat2 - lat1);
        double dLng = Math.toRadians(lng2 - lng1);
        double a = Math.sin(dLat / 2d) * Math.sin(dLat / 2d)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(dLng / 2d) * Math.sin(dLng / 2d);
        double c = 2d * Math.atan2(Math.sqrt(a), Math.sqrt(1d - a));
        return EARTH_RADIUS_METERS * c;
    }
}
