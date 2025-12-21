package com.hf.easydelivery.service;

import android.app.KeyguardManager;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.os.Build;
import android.os.IBinder;
import android.os.SystemClock;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.hf.courierservice.apihelper.FileLog;

/**
 * App-level controller that gates lockscreen notification by device lock state.
 * No calculations: only consumes FocusState (single source of truth) and shows it on lockscreen.
 */
public final class LockScreenFocusController implements FocusStateRepository.Listener {
    private static final String TAG = "LockScreenFocusController";
    private static volatile LockScreenFocusController instance;

    private static final long UPDATE_DEBOUNCE_MS = 400L;
    // Prevent tight loops of startForegroundService/bindService when the system rejects or delays startup.
    private static final long START_ATTEMPT_COOLDOWN_MS = 3_000L;

    @NonNull
    private final Context appContext;
    @NonNull
    private final FocusStateRepository repo;

    @Nullable
    private volatile FocusState latest;

    @Nullable
    private String lastShownStableId;

    private long lastUpdateUptimeMs = 0L;
    private long lastStartAttemptUptimeMs = 0L;

    @Nullable
    private LockScreenNotificationService service;
    private boolean bound = false;
    private boolean binding = false;

    private LockScreenFocusController(@NonNull Context context, @NonNull FocusStateRepository repo) {
        this.appContext = context.getApplicationContext();
        this.repo = repo;
    }

    public static void init(@NonNull Context context) {
        if (instance != null) return;
        synchronized (LockScreenFocusController.class) {
            if (instance != null) return;
            FocusStateRepository.init(context);
            instance = new LockScreenFocusController(context, FocusStateRepository.get());
            instance.start();
        }
    }

    @NonNull
    public static LockScreenFocusController get() {
        LockScreenFocusController inst = instance;
        if (inst == null) {
            throw new IllegalStateException("LockScreenFocusController not initialized. Call init() in Application.");
        }
        return inst;
    }

    private void start() {
        repo.addListener(this);
        // Listen to lock/unlock transitions so we can show cached focus on lock.
        IntentFilter f = new IntentFilter();
        f.addAction(Intent.ACTION_USER_PRESENT);
        f.addAction(Intent.ACTION_SCREEN_OFF);
        f.addAction(Intent.ACTION_SCREEN_ON);
        appContext.registerReceiver(lockReceiver, f);

        latest = repo.getLatest();
    }

    @Override
    public void onFocusChanged(@Nullable FocusState focusState) {
        latest = focusState;
        if (!isDeviceLocked()) {
            // Cache only; do nothing when unlocked.
            return;
        }
        // Locked: show/update if needed.
        maybeShow(false);
    }

    private final BroadcastReceiver lockReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent != null ? intent.getAction() : null;
            if (Intent.ACTION_USER_PRESENT.equals(action)) {
                // Unlock: stop & reset dedup so next lock can "补显示" same target.
                lastShownStableId = null;
                stopService();
                return;
            }

            // Screen toggles: if on keyguard, ensure we show latest once.
            if (Intent.ACTION_SCREEN_OFF.equals(action) || Intent.ACTION_SCREEN_ON.equals(action)) {
                if (isDeviceLocked()) {
                    maybeShow(true);
                } else {
                    stopService();
                }
            }
        }
    };

    private boolean isDeviceLocked() {
        try {
            KeyguardManager km = (KeyguardManager) appContext.getSystemService(Context.KEYGUARD_SERVICE);
            return km != null && km.isKeyguardLocked();
        } catch (Throwable ignore) {
            return false;
        }
    }

    private void maybeShow(boolean force) {
        FocusState focus = latest;
        if (focus == null || focus.delivery == null || focus.stableId == null || focus.stableId.isEmpty()) {
            stopService();
            return;
        }

        if (!force && focus.stableId.equals(lastShownStableId)) {
            return;
        }

        long now = SystemClock.uptimeMillis();
        if (!force && (now - lastUpdateUptimeMs) < UPDATE_DEBOUNCE_MS) {
            return;
        }
        lastUpdateUptimeMs = now;

        ensureServiceBound();
        if (service != null) {
            try {
                service.updateFocus(focus);
                lastShownStableId = focus.stableId;
            } catch (Throwable t) {
                FileLog.getInstance().error(TAG, "updateFocus failed", t);
            }
        }
    }

    private void ensureServiceBound() {
        if (bound || binding) return;

        // Cooldown to avoid repeatedly starting/binding in a short window (can cause ANR / battery drain).
        long now = SystemClock.uptimeMillis();
        if ((now - lastStartAttemptUptimeMs) < START_ATTEMPT_COOLDOWN_MS) {
            return;
        }
        lastStartAttemptUptimeMs = now;

        binding = true;
        try {
            Intent intent = new Intent(appContext, LockScreenNotificationService.class);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                appContext.startForegroundService(intent);
            } else {
                appContext.startService(intent);
            }
            appContext.bindService(intent, connection, Context.BIND_AUTO_CREATE);
        } catch (Throwable t) {
            binding = false;
            FileLog.getInstance().error(TAG, "Failed to start/bind LockScreenNotificationService", t);
            // Best-effort cleanup: if service got started but bind failed, stop it to avoid a stray foreground notification.
            try {
                appContext.stopService(new Intent(appContext, LockScreenNotificationService.class));
            } catch (Throwable ignore) {
            }
        }
    }

    private final ServiceConnection connection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder binder) {
            try {
                service = ((LockScreenNotificationService.LocalBinder) binder).getService();
            } catch (Throwable t) {
                service = null;
            }
            bound = (service != null);
            binding = false;
            if (bound) {
                // If we connected while locked, push latest immediately.
                maybeShow(true);
            }
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            bound = false;
            binding = false;
            service = null;
        }
    };

    private void stopService() {
        if (bound) {
            try {
                appContext.unbindService(connection);
            } catch (Throwable ignore) {
            }
        }
        bound = false;
        binding = false;
        service = null;
        try {
            appContext.stopService(new Intent(appContext, LockScreenNotificationService.class));
        } catch (Throwable ignore) {
        }
    }
}
