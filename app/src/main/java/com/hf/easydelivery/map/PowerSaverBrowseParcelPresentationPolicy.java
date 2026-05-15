package com.hf.easydelivery.map;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.hf.easydelivery.dao.DeliveryInfo;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * Selects a stable subset of parcels for browse mode instead of showing every marker.
 * 为 browse 模式挑选稳定的包裹子集，避免把所有 marker 一次性铺满屏幕。
 */
final class PowerSaverBrowseParcelPresentationPolicy implements ParcelPresentationPolicy {

    static final int MAX_VISIBLE_MARKERS = 24;
    private static final double EARTH_RADIUS_METERS = 6_371_000d;

    @NonNull
    @Override
    public List<DeliveryInfo> selectVisibleParcels(@NonNull List<DeliveryInfo> deliveries,
            @Nullable DriverLocationSnapshot driverLocation,
            @Nullable DeliveryInfo currentPrimaryDelivery) {
        if (deliveries.isEmpty()) {
            return Collections.emptyList();
        }

        List<DeliveryInfo> ordered = new ArrayList<>(deliveries);
        ordered.sort(Comparator
                .comparingInt((DeliveryInfo info) -> isPrimary(info, currentPrimaryDelivery) ? 0 : 1)
                .thenComparingInt(info -> isSameStop(info, currentPrimaryDelivery) ? 0 : 1)
                .thenComparingInt(info -> isNearPrimary(info, currentPrimaryDelivery) ? 0 : 1)
                .thenComparingDouble(info -> distanceScore(driverLocation, info))
                .thenComparing(info -> safeString(info.getRouteNumber()))
                .thenComparing(info -> safeString(info.getOrderSn())));

        if (ordered.size() <= MAX_VISIBLE_MARKERS) {
            return ordered;
        }
        return new ArrayList<>(ordered.subList(0, MAX_VISIBLE_MARKERS));
    }

    private boolean isPrimary(@NonNull DeliveryInfo info, @Nullable DeliveryInfo currentPrimaryDelivery) {
        if (currentPrimaryDelivery == null) {
            return false;
        }
        return info.getStableKey().equals(currentPrimaryDelivery.getStableKey());
    }

    private boolean isSameStop(@NonNull DeliveryInfo info, @Nullable DeliveryInfo currentPrimaryDelivery) {
        if (currentPrimaryDelivery == null || isPrimary(info, currentPrimaryDelivery)) {
            return false;
        }
        String currentStreet = safeString(currentPrimaryDelivery.getStreetName());
        String infoStreet = safeString(info.getStreetName());
        return !currentStreet.isEmpty() && currentStreet.equalsIgnoreCase(infoStreet)
                && currentPrimaryDelivery.getCivilNumber() != null
                && info.getCivilNumber() != null
                && currentPrimaryDelivery.getCivilNumber().intValue() == info.getCivilNumber().intValue();
    }

    private boolean isNearPrimary(@NonNull DeliveryInfo info, @Nullable DeliveryInfo currentPrimaryDelivery) {
        if (currentPrimaryDelivery == null || isPrimary(info, currentPrimaryDelivery)
                || isSameStop(info, currentPrimaryDelivery)) {
            return false;
        }
        return haversineMeters(
                currentPrimaryDelivery.getLatitude(),
                currentPrimaryDelivery.getLongitude(),
                info.getLatitude(),
                info.getLongitude()) <= 120d;
    }

    private double distanceScore(@Nullable DriverLocationSnapshot driverLocation, @NonNull DeliveryInfo info) {
        if (driverLocation == null) {
            return Double.MAX_VALUE / 2d;
        }
        return haversineMeters(
                driverLocation.latitude,
                driverLocation.longitude,
                info.getLatitude(),
                info.getLongitude());
    }

    @NonNull
    private String safeString(@Nullable String value) {
        return value == null ? "" : value;
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
