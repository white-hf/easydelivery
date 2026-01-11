package com.hf.easydelivery.core.observer;

import androidx.annotation.Nullable;

public interface LocationEventObserver {
    default void onBoostRequested(@Nullable String reason, boolean force) {
    }

    default void onBurstEnter() {
    }

    default void onBurstExit() {
    }

    default void onLocationResult() {
    }

    default void onDispatch(long seq, int listenerCount, long elapsedMs, String state) {
    }
}
