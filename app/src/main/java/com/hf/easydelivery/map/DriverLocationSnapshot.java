package com.hf.easydelivery.map;

import android.location.Location;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

final class DriverLocationSnapshot {
    final double latitude;
    final double longitude;

    DriverLocationSnapshot(double latitude, double longitude) {
        this.latitude = latitude;
        this.longitude = longitude;
    }

    @Nullable
    static DriverLocationSnapshot from(@Nullable Location location) {
        if (location == null) {
            return null;
        }
        return new DriverLocationSnapshot(location.getLatitude(), location.getLongitude());
    }

    @NonNull
    static DriverLocationSnapshot of(double latitude, double longitude) {
        return new DriverLocationSnapshot(latitude, longitude);
    }
}
