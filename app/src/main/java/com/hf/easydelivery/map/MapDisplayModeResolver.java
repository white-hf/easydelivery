package com.hf.easydelivery.map;

import androidx.annotation.NonNull;

import com.hf.easydelivery.core.facade.MovementState;
import com.hf.easydelivery.map.config.ProfileManager;

/**
 * Resolves the current map display mode from high-level product state.
 * 根据上层产品状态解析当前地图展示模式。
 */
final class MapDisplayModeResolver {

    @NonNull
    MapDisplayMode resolve(@NonNull ProfileManager.AppProfile profile,
            @NonNull MovementState movementState,
            boolean navigationModeEnabled,
            boolean autoFollowPaused,
            boolean userInteracting) {
        if (profile == ProfileManager.AppProfile.POWERSAVER
                && !navigationModeEnabled
                && !autoFollowPaused
                && !userInteracting
                && (movementState == MovementState.SLOW_DRIVING
                        || movementState == MovementState.NORMAL_DRIVING
                        || movementState == MovementState.WALKING)) {
            return MapDisplayMode.POWER_SAVER_BROWSE;
        }
        return MapDisplayMode.FOLLOW;
    }
}
