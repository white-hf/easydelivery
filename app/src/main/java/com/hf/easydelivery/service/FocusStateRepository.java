package com.hf.easydelivery.service;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.concurrent.CopyOnWriteArraySet;

/**
 * Process-wide single source of truth for the current focused delivery.
 * Lightweight: no location, no delivery scanning, no persistence.
 */
public final class FocusStateRepository {
    public interface Listener {
        void onFocusChanged(@Nullable FocusState focusState);
    }

    private static volatile FocusStateRepository instance;

    @NonNull
    private final Context appContext;
    @NonNull
    private final CopyOnWriteArraySet<Listener> listeners = new CopyOnWriteArraySet<>();

    @Nullable
    private volatile FocusState latest;

    private FocusStateRepository(@NonNull Context context) {
        this.appContext = context.getApplicationContext();
    }

    public static void init(@NonNull Context context) {
        if (instance == null) {
            synchronized (FocusStateRepository.class) {
                if (instance == null) {
                    instance = new FocusStateRepository(context);
                }
            }
        }
    }

    @NonNull
    public static FocusStateRepository get() {
        FocusStateRepository inst = instance;
        if (inst == null) {
            throw new IllegalStateException("FocusStateRepository not initialized. Call init() in Application.");
        }
        return inst;
    }

    @Nullable
    public FocusState getLatest() {
        return latest;
    }

    public void setLatest(@Nullable FocusState focusState) {
        latest = focusState;
        for (Listener l : listeners) {
            try {
                l.onFocusChanged(focusState);
            } catch (Throwable ignore) {
            }
        }
    }

    public void addListener(@NonNull Listener listener) {
        listeners.add(listener);
    }

    public void removeListener(@NonNull Listener listener) {
        listeners.remove(listener);
    }

    @NonNull
    public Context getAppContext() {
        return appContext;
    }
}

