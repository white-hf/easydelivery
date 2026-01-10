package com.hf.easydelivery.core.burst;

import android.os.Handler;

import androidx.annotation.NonNull;

public final class BurstController {
    public interface Listener {
        void onBurstEnter(long durationMs);
        void onBurstExit(boolean fromTimer);
    }

    private final Handler handler;
    private final Listener listener;
    private final long minIntervalMs;
    private boolean inBurst = false;
    private long holdUntilMs = 0L;
    private long lastChangeMs = 0L;
    private Runnable timer;

    public BurstController(@NonNull Handler handler,
            @NonNull Listener listener,
            long minIntervalMs) {
        this.handler = handler;
        this.listener = listener;
        this.minIntervalMs = minIntervalMs;
    }

    public boolean isInBurst() {
        return inBurst;
    }

    public long getLastChangeMs() {
        return lastChangeMs;
    }

    public void requestBurst(long durationMs) {
        requestBurst(durationMs, false);
    }

    public void requestBurst(long durationMs, boolean force) {
        long now = System.currentTimeMillis();
        if (inBurst && now - lastChangeMs < minIntervalMs) {
            extend(durationMs);
            return;
        }
        if (inBurst) {
            extend(durationMs);
            return;
        }
        enter(durationMs);
    }

    public void extend(long durationMs) {
        if (!inBurst) {
            return;
        }
        long now = System.currentTimeMillis();
        holdUntilMs = Math.max(holdUntilMs, now + Math.max(1_000L, durationMs));
        schedule();
    }

    public void forceExit(boolean fromTimer) {
        if (!inBurst) {
            return;
        }
        exit(fromTimer);
    }

    public void cancelTimer() {
        if (timer != null) {
            handler.removeCallbacks(timer);
            timer = null;
        }
    }

    private void enter(long durationMs) {
        long now = System.currentTimeMillis();
        inBurst = true;
        lastChangeMs = now;
        holdUntilMs = Math.max(holdUntilMs, now + Math.max(1_000L, durationMs));
        listener.onBurstEnter(durationMs);
        schedule();
    }

    private void schedule() {
        cancelTimer();
        timer = this::tryExit;
        long now = System.currentTimeMillis();
        long delay = Math.max(500L, holdUntilMs - now);
        handler.postDelayed(timer, delay);
    }

    private void tryExit() {
        long now = System.currentTimeMillis();
        if (now < holdUntilMs) {
            schedule();
            return;
        }
        exit(true);
    }

    private void exit(boolean fromTimer) {
        inBurst = false;
        holdUntilMs = 0L;
        lastChangeMs = System.currentTimeMillis();
        listener.onBurstExit(fromTimer);
        cancelTimer();
    }
}
