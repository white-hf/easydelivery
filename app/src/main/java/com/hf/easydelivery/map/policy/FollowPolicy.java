package com.hf.easydelivery.map.policy;

import com.hf.easydelivery.map.CameraFollowController;

public interface FollowPolicy {
    long getUiTickMs();
    long getModeSwitchCooldownMs();
    long getListMinHoldMs();
    long getFollowMinHoldMs();
    long getListEntryStationaryMs();
    CameraFollowController.FollowConfig getFollowConfig();
}
