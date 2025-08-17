package com.hf.easydelivery.common;

import android.os.Handler;
import android.os.Looper;

public class TokenRefresher {
    private final Handler handler;
    private final long intervalMs;
    private Runnable userTask;
    private Runnable internalRunnable;
    private boolean isRunning = false;

    /**
     * 构造方法
     * @param intervalMs      刷新周期（毫秒）
     */
    public TokenRefresher(long intervalMs) {
        this.handler = new Handler(Looper.getMainLooper());
        this.intervalMs = intervalMs;
    }

    /**
     * 开始周期性执行刷token任务
     * @param userTask 你的实际刷新代码，如 () -> api.refreshToken()
     */
    public void start(Runnable userTask) {
        if (isRunning) return;
        if (userTask == null) throw new IllegalArgumentException("userTask不可为null");
        this.userTask = userTask;
        isRunning = true;
        internalRunnable = new Runnable() {
            @Override
            public void run() {
                if (!isRunning) return;
                try {
                    TokenRefresher.this.userTask.run();
                } catch (Exception ignored) {}
                handler.postDelayed(this, intervalMs);
            }
        };
        handler.postDelayed(internalRunnable, intervalMs);
    }

    /**
     * 停止定时任务，释放资源
     */
    public void stop() {
        isRunning = false;
        if (internalRunnable != null) {
            handler.removeCallbacks(internalRunnable);
            internalRunnable = null;
        }
        userTask = null;
    }
}
