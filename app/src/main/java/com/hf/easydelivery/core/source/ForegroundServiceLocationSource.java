package com.hf.easydelivery.core.source;

import android.content.Context;
import android.os.Looper;

import androidx.annotation.NonNull;

import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationCallback;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.tasks.Task;

public final class ForegroundServiceLocationSource implements LocationSource {
    private final FusedLocationProviderClient client;

    public ForegroundServiceLocationSource(@NonNull Context context) {
        client = LocationServices.getFusedLocationProviderClient(context.getApplicationContext());
    }

    @NonNull
    @Override
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
