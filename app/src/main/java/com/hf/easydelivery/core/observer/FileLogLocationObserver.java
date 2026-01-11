package com.hf.easydelivery.core.observer;

import androidx.annotation.Nullable;

import com.hf.courierservice.apihelper.FileLog;

public final class FileLogLocationObserver implements LocationEventObserver {
    private static final String TAG = "SmartLocationManager";

    @Override
    public void onBoostRequested(@Nullable String reason, boolean force) {
        FileLog.getInstance().debug(TAG, "requestBoost reason=" + reason + " force=" + force);
    }

    @Override
    public void onBurstEnter() {
        FileLog.getInstance().debug(TAG, "enterBurstMode");
    }

    @Override
    public void onBurstExit() {
        FileLog.getInstance().debug(TAG, "exitBurstMode");
    }

    @Override
    public void onDispatch(long seq, int listenerCount, long elapsedMs, String state) {
        FileLog.getInstance().debug(TAG,
                String.format("dispatch #%d listeners=%d elapsedMs=%d mv=%s",
                        seq,
                        listenerCount,
                        elapsedMs,
                        state));
    }
}
