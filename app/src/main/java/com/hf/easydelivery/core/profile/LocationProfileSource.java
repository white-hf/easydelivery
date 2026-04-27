package com.hf.easydelivery.core.profile;

import androidx.annotation.NonNull;

public interface LocationProfileSource {
    interface Listener {
        void onPowerSaverChanged(boolean powerSaver);
    }

    LocationProfileSource NONE = new LocationProfileSource() {
        @Override
        public boolean isPowerSaver() {
            return false;
        }

        @Override
        public void addListener(@NonNull Listener listener) {
            // no-op
        }

        @Override
        public void removeListener(@NonNull Listener listener) {
            // no-op
        }
    };

    boolean isPowerSaver();

    void addListener(@NonNull Listener listener);

    void removeListener(@NonNull Listener listener);
}
