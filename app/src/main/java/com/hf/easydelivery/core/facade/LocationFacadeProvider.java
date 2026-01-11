package com.hf.easydelivery.core.facade;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

public interface LocationFacadeProvider {
    @Nullable
    LocationFacade getLocationFacade(@NonNull Context context);

    @Nullable
    LocationControls getLocationControls(@NonNull Context context);

    @Nullable
    ForegroundLocationConsumer getForegroundLocationConsumer(@NonNull Context context);
}
