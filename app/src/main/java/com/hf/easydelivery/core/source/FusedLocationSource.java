package com.hf.easydelivery.core.source;

import android.os.Looper;

import androidx.annotation.NonNull;

import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationCallback;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.tasks.Task;

public final class FusedLocationSource implements LocationSource {
    private final FusedLocationProviderClient client;

    public FusedLocationSource(@NonNull FusedLocationProviderClient client) {
        this.client = client;
    }

    @Override
    @NonNull
    public Task<Void> requestLocationUpdates(@NonNull LocationRequest request,
            @NonNull LocationCallback callback,
            @NonNull Looper looper) {
        return client.requestLocationUpdates(request, callback, looper);
    }

    @Override
    public void removeLocationUpdates(@NonNull LocationCallback callback) {
        client.removeLocationUpdates(callback);
    }
}
