package com.hf.easydelivery.core.source;

import android.os.Looper;

import androidx.annotation.NonNull;

import com.google.android.gms.location.LocationCallback;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.tasks.Task;

public interface LocationSource {
    @NonNull
    Task<Void> requestLocationUpdates(@NonNull LocationRequest request,
            @NonNull LocationCallback callback,
            @NonNull Looper looper);

    void removeLocationUpdates(@NonNull LocationCallback callback);
}
