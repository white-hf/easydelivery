package com.hf.easydelivery.core.facade;

import android.location.Location;

public interface LocationUpdateListener {
    void onLocationUpdate(Location location, MovementState state);
}
