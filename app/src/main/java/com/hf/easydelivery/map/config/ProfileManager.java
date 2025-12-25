package com.hf.easydelivery.map.config;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;

import androidx.annotation.MainThread;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * ProfileManager
 *
 * 统一管理 App 的性能/策略档位：
 *  - POWERSAVER：极简省电（CameraActivity 送达后一次性候选；InfoPill 不做 GPS 轮询）
 *  - ADVANCED  ：高级策略（接近判定/更积极的焦点与相机策略）
 *
 * 作用：
 *  - 持久化当前档位（SharedPreferences）
 *  - 提供运行时监听，支持“开发者面板”热切换，无需重启
 *
 * 使用：
 *  ProfileManager pm = ProfileManager.get(context);
 *  AppProfile p = pm.getCurrent();
 *  pm.addListener(new ProfileManager.Listener() { ... });
 *  pm.setCurrent(AppProfile.POWERSAVER);
 */
public final class ProfileManager {

    /** 策略档位（作为外部可见的类型暴露） */
    public enum AppProfile {
        POWERSAVER,
        ADVANCED;

        public static @NonNull AppProfile fromString(@Nullable String s, @NonNull AppProfile def) {
            if (s == null) return def;
            try {
                return AppProfile.valueOf(s);
            } catch (IllegalArgumentException ex) {
                return def;
            }
        }
    }

    /** 监听接口：档位发生变化时回调（主线程） */
    public interface Listener {
        void onProfileChanged(@NonNull AppProfile newProfile);
    }

    public interface PerfBalanceListener {
        void onPerfBalanceChanged(float newBalance);
    }

    private static final String SP_NAME = "dev_flags";
    private static final String KEY_PROFILE = "profile";
    private static final String KEY_PERF_BALANCE = "perf_balance";

    private static volatile ProfileManager sInstance;

    private final Context appContext;
    private final SharedPreferences sp;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final List<Listener> listeners = new CopyOnWriteArrayList<>();
    private final List<PerfBalanceListener> perfBalanceListeners = new CopyOnWriteArrayList<>();

    // 当前内存态（减少反复读取 SP）
    private volatile AppProfile current = AppProfile.ADVANCED; // 默认使用现有高级策略
    private volatile float perfBalance = 0f; // 0=realtime, 1=power saver

    private ProfileManager(@NonNull Context context) {
        this.appContext = context.getApplicationContext();
        this.sp = appContext.getSharedPreferences(SP_NAME, Context.MODE_PRIVATE);
        // 读取持久化值
        String saved = sp.getString(KEY_PROFILE, null);
        current = AppProfile.fromString(saved, current);
        perfBalance = clamp01(sp.getFloat(KEY_PERF_BALANCE, perfBalance));
    }

    /** 单例入口 */
    public static @NonNull ProfileManager get(@NonNull Context context) {
        if (sInstance == null) {
            synchronized (ProfileManager.class) {
                if (sInstance == null) {
                    sInstance = new ProfileManager(context);
                }
            }
        }
        return sInstance;
    }

    /** 当前生效档位（线程安全，可在任意线程调用） */
    public @NonNull AppProfile getCurrent() {
        return current;
    }

    /** 是否处于省电档 */
    public boolean isPowerSaver() {
        return current == AppProfile.POWERSAVER;
    }

    public float getPerfBalance() {
        return perfBalance;
    }

    public void setPerfBalance(float balance) {
        float clamped = clamp01(balance);
        if (clamped == perfBalance) return;
        perfBalance = clamped;
        sp.edit().putFloat(KEY_PERF_BALANCE, perfBalance).apply();
        mainHandler.post(() -> {
            for (PerfBalanceListener l : perfBalanceListeners) {
                try {
                    l.onPerfBalanceChanged(perfBalance);
                } catch (Throwable ignore) {
                    // keep resilient
                }
            }
        });
    }

    /** 设置档位（会持久化，并在主线程通知监听者；重复设置同值将被忽略） */
    public void setCurrent(@NonNull AppProfile profile) {
        if (profile == current) return;
        current = profile;
        sp.edit().putString(KEY_PROFILE, profile.name()).apply();
        // 广播到主线程
        mainHandler.post(() -> {
            for (Listener l : listeners) {
                try {
                    l.onProfileChanged(profile);
                } catch (Throwable ignore) {
                    // 保持健壮，不因单个监听异常中断广播
                }
            }
        });
    }

    /** 注册监听（重复添加将被忽略；回调始终在主线程） */
    @MainThread
    public void addListener(@NonNull Listener l) {
        if (!listeners.contains(l)) {
            listeners.add(l);
        }
    }

    @MainThread
    public void addPerfBalanceListener(@NonNull PerfBalanceListener l) {
        if (!perfBalanceListeners.contains(l)) {
            perfBalanceListeners.add(l);
        }
    }

    /** 取消监听 */
    @MainThread
    public void removeListener(@NonNull Listener l) {
        listeners.remove(l);
    }

    @MainThread
    public void removePerfBalanceListener(@NonNull PerfBalanceListener l) {
        perfBalanceListeners.remove(l);
    }

    private static float clamp01(float v) {
        if (v < 0f) return 0f;
        if (v > 1f) return 1f;
        return v;
    }
}
