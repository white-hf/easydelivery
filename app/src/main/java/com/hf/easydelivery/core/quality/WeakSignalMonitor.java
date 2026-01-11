package com.hf.easydelivery.core.quality;

public final class WeakSignalMonitor {
    private final int requiredHits;
    private final long durationMs;
    private int hitCount;
    private long startMs;

    public WeakSignalMonitor(int requiredHits, long durationMs) {
        this.requiredHits = requiredHits;
        this.durationMs = durationMs;
    }

    public boolean update(float accuracyMeters, float thresholdMeters, long nowMs) {
        if (accuracyMeters > thresholdMeters) {
            if (startMs == 0L) {
                startMs = nowMs;
            }
            hitCount++;
            long duration = nowMs - startMs;
            if (hitCount >= requiredHits && duration > durationMs) {
                reset();
                return true;
            }
        } else {
            reset();
        }
        return false;
    }

    public void reset() {
        hitCount = 0;
        startMs = 0L;
    }
}
