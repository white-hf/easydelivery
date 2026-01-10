package com.hf.easydelivery.core.state;

import androidx.annotation.NonNull;

import com.hf.easydelivery.core.SmartLocationManager.MovementState;

public final class MovementStateMachine {
    private MovementState currentState = MovementState.STATIONARY;
    private MovementState previousState = MovementState.STATIONARY;
    private long continuousStationaryStartMs = 0L;

    public static final class Result {
        public final boolean changed;
        public final MovementState oldState;
        public final MovementState newState;
        public final boolean wasNotDriving;
        public final boolean isDrivingNow;

        public Result(boolean changed,
                @NonNull MovementState oldState,
                @NonNull MovementState newState,
                boolean wasNotDriving,
                boolean isDrivingNow) {
            this.changed = changed;
            this.oldState = oldState;
            this.newState = newState;
            this.wasNotDriving = wasNotDriving;
            this.isDrivingNow = isDrivingNow;
        }
    }

    @NonNull
    public Result updateBySpeed(float speedMps,
            long nowUptimeMs,
            long nowMillis,
            long lastDrivingUptimeMs,
            long lastInVehicleUptimeMs,
            float lastDisplacementMeters,
            long drivingDowngradeGraceMs,
            long inVehicleGraceMs,
            float displacementOverrideM) {
        MovementState oldState = currentState;
        MovementState newState;
        if (speedMps < 0.5f) {
            newState = MovementState.STATIONARY;
        } else if (speedMps < 2f) {
            newState = MovementState.WALKING;
        } else if (speedMps < 8f) {
            newState = MovementState.SLOW_DRIVING;
        } else {
            newState = MovementState.NORMAL_DRIVING;
        }

        boolean wasDriving = oldState == MovementState.SLOW_DRIVING
                || oldState == MovementState.NORMAL_DRIVING;
        if (newState == MovementState.STATIONARY && wasDriving) {
            boolean recentDriving = nowUptimeMs - lastDrivingUptimeMs < drivingDowngradeGraceMs;
            boolean recentVehicle = nowUptimeMs - lastInVehicleUptimeMs < inVehicleGraceMs;
            boolean displacementOverride = lastDisplacementMeters >= displacementOverrideM;
            if (recentDriving || recentVehicle || displacementOverride) {
                newState = oldState;
            }
        }

        boolean changed = newState != oldState;
        boolean isDrivingNow = newState == MovementState.SLOW_DRIVING || newState == MovementState.NORMAL_DRIVING;
        boolean wasNotDriving = oldState == MovementState.STATIONARY || oldState == MovementState.WALKING;
        if (changed) {
            updateContinuousStationaryStart(newState, nowMillis);
            previousState = oldState;
            currentState = newState;
        }
        return new Result(changed, oldState, newState, wasNotDriving, isDrivingNow);
    }

    public boolean applyExternalState(@NonNull MovementState newState, long nowMillis) {
        if (newState == currentState) {
            return false;
        }
        updateContinuousStationaryStart(newState, nowMillis);
        previousState = currentState;
        currentState = newState;
        return true;
    }

    @NonNull
    public MovementState getCurrentState() {
        return currentState;
    }

    @NonNull
    public MovementState getPreviousState() {
        return previousState;
    }

    public long getStationaryDurationMs(long nowMillis) {
        if (currentState != MovementState.STATIONARY && currentState != MovementState.WALKING) {
            return 0L;
        }
        if (continuousStationaryStartMs <= 0L) {
            return 0L;
        }
        return Math.max(0L, nowMillis - continuousStationaryStartMs);
    }

    private void updateContinuousStationaryStart(@NonNull MovementState newState, long nowMillis) {
        if (newState == MovementState.STATIONARY || newState == MovementState.WALKING) {
            continuousStationaryStartMs = nowMillis;
        } else {
            continuousStationaryStartMs = 0L;
        }
    }
}
