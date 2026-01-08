package com.hf.easydelivery.core.strategy;

import android.content.Context;
import android.location.Location;
import android.os.SystemClock;

import androidx.annotation.NonNull;

import com.hf.courierservice.apihelper.FileLog;
import com.hf.easydelivery.core.SmartLocationManager;
import com.hf.easydelivery.core.policy.LocationRequestParams;
import com.hf.easydelivery.map.config.ProfileManager;

public final class StrategyManager implements ProfileManager.Listener {
    private static final String TAG = "StrategyManager";

    private final SmartLocationManager smartLocationManager;
    private final ProfileManager profileManager;
    private final LocationStrategy realtimeStrategy = new RealTimeStrategy();
    private final LocationStrategy powerSaveStrategy = new PowerSaveStrategy();

    private volatile StrategyMode currentMode = StrategyMode.REALTIME;
    private volatile boolean appForeground = true;
    private volatile Location lastDispatchLocation;
    private volatile long lastRequestUptimeMs = 0L;

    public StrategyManager(@NonNull Context context, @NonNull SmartLocationManager smartLocationManager) {
        this.smartLocationManager = smartLocationManager;
        this.profileManager = ProfileManager.get(context);
        switchMode(profileManager.isPowerSaver() ? StrategyMode.POWERSAVE : StrategyMode.REALTIME);
        profileManager.addListener(this);
    }

    @Override
    public void onProfileChanged(@NonNull ProfileManager.AppProfile newProfile) {
        StrategyMode mode = newProfile == ProfileManager.AppProfile.POWERSAVER
                ? StrategyMode.POWERSAVE
                : StrategyMode.REALTIME;
        switchMode(mode);
    }

    public void switchMode(@NonNull StrategyMode mode) {
        if (mode == currentMode) {
            return;
        }
        getStrategy(currentMode).onModeExit();
        currentMode = mode;
        getStrategy(currentMode).onModeEnter();
        if (currentMode == StrategyMode.REALTIME) {
            smartLocationManager.forceExitBurstForStrategy("mode_switch");
        }
        applyRequest("switch_mode");
    }

    public void suggestRefresh(@NonNull String reason) {
        if (currentMode == StrategyMode.REALTIME) {
            return;
        }
        if (!appForeground) {
            return;
        }
        if (!isDisplacementLargeEnough()) {
            return;
        }
        long now = SystemClock.uptimeMillis();
        if (now - lastRequestUptimeMs < StrategyConfig.getPowerSaveCooldownMs()) {
            return;
        }
        applyRequest(reason);
    }

    public void suggestBoost(@NonNull BoostReason reason, long durationMs, boolean force) {
        if (currentMode != StrategyMode.POWERSAVE) {
            return;
        }
        if (!appForeground) {
            return;
        }
        if (!isDisplacementLargeEnough() && !force) {
            return;
        }
        long now = SystemClock.uptimeMillis();
        if (now - lastRequestUptimeMs < StrategyConfig.getPowerSaveCooldownMs()) {
            return;
        }
        long dur = durationMs > 0 ? durationMs : StrategyConfig.getBurstDurationMs();
        smartLocationManager.startBurstWindowForStrategy(dur, force, reason);
    }

    public void onLocationDispatched(@NonNull Location loc) {
        lastDispatchLocation = loc;
    }

    public void updateAppForeground(boolean foreground) {
        appForeground = foreground;
    }

    public void onLocationEngineReady() {
        applyRequest("engine_ready");
    }

    private void applyRequest(@NonNull String reason) {
        LocationContext ctx = buildContext();
        LocationRequestParams params;
        if (currentMode == StrategyMode.POWERSAVE && ctx.inBurst) {
            params = realtimeStrategy.getParams(ctx);
        } else {
            params = getStrategy(currentMode).getParams(ctx);
        }
        lastRequestUptimeMs = SystemClock.uptimeMillis();
        smartLocationManager.applyLocationRequest(params, reason);
        FileLog.getInstance().debug(TAG, "applyRequest mode=" + currentMode + " reason=" + reason);
    }

    private LocationStrategy getStrategy(StrategyMode mode) {
        return mode == StrategyMode.POWERSAVE ? powerSaveStrategy : realtimeStrategy;
    }

    private boolean isDisplacementLargeEnough() {
        Location last = lastDispatchLocation;
        Location current = smartLocationManager.getLastLocation();
        if (last == null || current == null) {
            return true;
        }
        return last.distanceTo(current) >= 20.0f;
    }

    private LocationContext buildContext() {
        return new LocationContext(
                smartLocationManager.getCurrentState(),
                appForeground,
                0f,
                smartLocationManager.isInBurstMode()
        );
    }
}
