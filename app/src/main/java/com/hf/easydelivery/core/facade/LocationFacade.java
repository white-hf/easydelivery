package com.hf.easydelivery.core.facade;

public interface LocationFacade {
    void startLocationUpdates();

    void stopLocationUpdates();

    void startForegroundTracking();

    void stopForegroundTracking();

    void setLocationUpdateListener(LocationUpdateListener listener);

    void addLocationUpdateListener(LocationUpdateListener listener);

    void removeLocationUpdateListener(LocationUpdateListener listener);

    LocationSnapshot getSnapshot();
}
