package com.hf.easydelivery.core.quality;

import android.location.Location;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.hf.easydelivery.core.SmartLocationManager.MovementState;

public final class FixQualityClassifier {
    public static final class Result {
        public final boolean staleFix;
        public final boolean goodFix;
        public final boolean okFix;
        public final boolean poorFix;
        public final float goodThreshold;
        public final float okThreshold;

        public Result(boolean staleFix,
                boolean goodFix,
                boolean okFix,
                boolean poorFix,
                float goodThreshold,
                float okThreshold) {
            this.staleFix = staleFix;
            this.goodFix = goodFix;
            this.okFix = okFix;
            this.poorFix = poorFix;
            this.goodThreshold = goodThreshold;
            this.okThreshold = okThreshold;
        }
    }

    @NonNull
    public Result classify(@NonNull Location current,
            @Nullable Location reference,
            float speedMps,
            @NonNull MovementState state,
            long ageMs,
            long staleThresholdMs) {
        boolean staleFix = ageMs > staleThresholdMs;
        float goodThreshold = getGoodAccuracyThreshold(speedMps, state);
        float okThreshold = getOkAccuracyThreshold(speedMps, state);
        boolean plausible = isPlausibleFix(current, reference, QualityConfig.getMaxPlausibleSpeedMps());
        boolean plausibleGood = isPlausibleFix(current, reference, QualityConfig.getMaxPlausibleSpeedMpsGood());
        boolean okFix = !staleFix
                && current.getAccuracy() > goodThreshold
                && current.getAccuracy() <= okThreshold
                && plausible;
        boolean goodFix = !staleFix && current.getAccuracy() <= goodThreshold && plausibleGood;
        boolean poorFix = !goodFix && !okFix;
        return new Result(staleFix, goodFix, okFix, poorFix, goodThreshold, okThreshold);
    }

    private float getGoodAccuracyThreshold(float speedMps, @NonNull MovementState state) {
        boolean driving = state == MovementState.SLOW_DRIVING
                || state == MovementState.NORMAL_DRIVING
                || speedMps > 5f;
        if (driving) {
            return 60f;
        }
        if (state == MovementState.WALKING) {
            return 45f;
        }
        return 35f;
    }

    private float getOkAccuracyThreshold(float speedMps, @NonNull MovementState state) {
        boolean driving = state == MovementState.SLOW_DRIVING
                || state == MovementState.NORMAL_DRIVING
                || speedMps > 5f;
        if (driving) {
            return 90f;
        }
        if (state == MovementState.WALKING) {
            return 70f;
        }
        return 60f;
    }

    private boolean isPlausibleFix(@NonNull Location current, @Nullable Location reference, float maxSpeedMps) {
        if (reference == null) {
            return true;
        }
        long curElapsed = getElapsedRealtimeMsSafe(current);
        long refElapsed = getElapsedRealtimeMsSafe(reference);
        long dtMs = (curElapsed > 0 && refElapsed > 0) ? (curElapsed - refElapsed)
                : (current.getTime() - reference.getTime());
        if (dtMs <= 0) {
            return false;
        }
        float distance = reference.distanceTo(current);
        float speedMps = distance / (dtMs / 1000f);
        return speedMps <= maxSpeedMps;
    }

    private long getElapsedRealtimeMsSafe(@NonNull Location loc) {
        try {
            return loc.getElapsedRealtimeNanos() / 1_000_000L;
        } catch (Throwable ignore) {
            return -1L;
        }
    }
}
