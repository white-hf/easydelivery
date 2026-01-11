package com.hf.easydelivery.core.observer;

import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.List;

public final class LocationEventBus {
    private final List<LocationEventObserver> observers = new ArrayList<>();

    public void addObserver(@Nullable LocationEventObserver observer) {
        if (observer != null) {
            observers.add(observer);
        }
    }

    public void removeObserver(@Nullable LocationEventObserver observer) {
        observers.remove(observer);
    }

    public void emitBoostRequested(@Nullable String reason, boolean force) {
        for (LocationEventObserver observer : observers) {
            observer.onBoostRequested(reason, force);
        }
    }

    public void emitBurstEnter() {
        for (LocationEventObserver observer : observers) {
            observer.onBurstEnter();
        }
    }

    public void emitBurstExit() {
        for (LocationEventObserver observer : observers) {
            observer.onBurstExit();
        }
    }

    public void emitLocationResult() {
        for (LocationEventObserver observer : observers) {
            observer.onLocationResult();
        }
    }

    public void emitDispatch(long seq, int listenerCount, long elapsedMs, String state) {
        for (LocationEventObserver observer : observers) {
            observer.onDispatch(seq, listenerCount, elapsedMs, state);
        }
    }
}
