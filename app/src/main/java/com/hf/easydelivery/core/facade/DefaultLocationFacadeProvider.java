package com.hf.easydelivery.core.facade;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.hf.easydelivery.core.SmartLocationManager;

public final class DefaultLocationFacadeProvider implements LocationFacadeProvider {
    private static final DefaultLocationFacadeProvider INSTANCE = new DefaultLocationFacadeProvider();

    private DefaultLocationFacadeProvider() {
    }

    @NonNull
    public static DefaultLocationFacadeProvider getInstance() {
        return INSTANCE;
    }

    @Override
    @Nullable
    public LocationFacade getLocationFacade(@NonNull Context context) {
        return SmartLocationManager.getInstance(context);
    }

    @Override
    @Nullable
    public LocationControls getLocationControls(@NonNull Context context) {
        return SmartLocationManager.getInstance(context);
    }

    @Override
    @Nullable
    public ForegroundLocationConsumer getForegroundLocationConsumer(@NonNull Context context) {
        return SmartLocationManager.getInstance(context);
    }
}
