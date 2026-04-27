package com.hf.easydelivery.map.config;

import android.content.Context;

import androidx.annotation.NonNull;

import com.hf.easydelivery.core.profile.LocationProfileSource;

import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;

public final class ProfileManagerLocationProfileSource
        implements LocationProfileSource, ProfileManager.Listener {
    private static volatile ProfileManagerLocationProfileSource instance;

    private final ProfileManager profileManager;
    private final Set<Listener> listeners = new CopyOnWriteArraySet<>();

    private ProfileManagerLocationProfileSource(@NonNull Context context) {
        profileManager = ProfileManager.get(context.getApplicationContext());
        profileManager.addListener(this);
    }

    @NonNull
    public static ProfileManagerLocationProfileSource get(@NonNull Context context) {
        if (instance == null) {
            synchronized (ProfileManagerLocationProfileSource.class) {
                if (instance == null) {
                    instance = new ProfileManagerLocationProfileSource(context);
                }
            }
        }
        return instance;
    }

    @Override
    public boolean isPowerSaver() {
        return profileManager.isPowerSaver();
    }

    @Override
    public void addListener(@NonNull Listener listener) {
        listeners.add(listener);
    }

    @Override
    public void removeListener(@NonNull Listener listener) {
        listeners.remove(listener);
    }

    @Override
    public void onProfileChanged(@NonNull ProfileManager.AppProfile newProfile) {
        boolean powerSaver = newProfile == ProfileManager.AppProfile.POWERSAVER;
        for (Listener listener : listeners) {
            try {
                listener.onPowerSaverChanged(powerSaver);
            } catch (Throwable ignore) {
                // keep notifying remaining listeners
            }
        }
    }
}
