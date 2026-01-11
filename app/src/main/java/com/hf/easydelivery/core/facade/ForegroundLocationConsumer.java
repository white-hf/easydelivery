package com.hf.easydelivery.core.facade;

import android.location.Location;

import androidx.annotation.NonNull;

public interface ForegroundLocationConsumer {
    void onForegroundLocation(@NonNull Location location);
}
