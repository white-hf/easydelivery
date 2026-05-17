package com.hf.easydelivery.map;

import android.location.Location;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.android.gms.maps.model.CameraPosition;

interface DrivingCameraBehavior {
    @Nullable
    CameraPosition buildCamera(@NonNull CameraFollowController controller,
            @NonNull Location location,
            float preferredFollowZoom);
}
