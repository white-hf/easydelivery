package com.hf.easydelivery.map;

import static org.junit.Assert.assertEquals;

import com.hf.easydelivery.core.facade.MovementState;
import com.hf.easydelivery.map.config.ProfileManager;

import org.junit.Test;

public class MapDisplayModeResolverTest {

    private final MapDisplayModeResolver resolver = new MapDisplayModeResolver();

    @Test
    public void powersaverDrivingResolvesBrowseMode() {
        MapDisplayMode mode = resolver.resolve(
                ProfileManager.AppProfile.POWERSAVER,
                MovementState.NORMAL_DRIVING,
                false,
                false,
                false);
        assertEquals(MapDisplayMode.POWER_SAVER_BROWSE, mode);
    }

    @Test
    public void powersaverPausedStaysFollowMode() {
        MapDisplayMode mode = resolver.resolve(
                ProfileManager.AppProfile.POWERSAVER,
                MovementState.NORMAL_DRIVING,
                false,
                true,
                false);
        assertEquals(MapDisplayMode.FOLLOW, mode);
    }

    @Test
    public void advancedModeStaysFollowMode() {
        MapDisplayMode mode = resolver.resolve(
                ProfileManager.AppProfile.ADVANCED,
                MovementState.NORMAL_DRIVING,
                false,
                false,
                false);
        assertEquals(MapDisplayMode.FOLLOW, mode);
    }
}
