package com.hf.easydelivery.map;

import android.location.Location;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.hf.easydelivery.core.SmartLocationManager;
import com.hf.easydelivery.dao.DeliveryInfo;
import com.hf.courierservice.apihelper.FileLog;

import java.util.Collections;
import java.util.List;

/**
 * ProximityCoordinator
 * --------------------
 * 协调“定位事件 → 最近包裹计算 → InfoPill 决策 → UI 回调/提频Boost”。
 *
 * 设计要点：
 * 1) 纯粹协调层：不直接操作 UI/地图；不直接依赖定位实现类；输入/输出均为纯数据。
 * 2) 可插拔：委托给 DeliveryFocusManager 做距离/集合计算；委托给 InfoPillProximityController
 * 做显隐决策。
 * 3) 可测性：核心入口 onLocation(...) 为纯函数风格（无副作用除回调），便于单测。
 * Phase 3: exposes profile & ETA hooks; centralizes nearby collection with
 * DeliveryFocusManager when available.
 */
public final class ProximityCoordinator {

    private static final String TAG = "ProximityCoordinator";

    private static void logI(String msg) {
        try {
            FileLog.i(TAG, msg);
        } catch (Throwable ignore) {
        }
    }

    private static void logD(String msg) {
        try {
            FileLog.getInstance().debug(TAG, msg);
        } catch (Throwable ignore) {
        }
    }

    // ==== Listener for UI layer ====
    public interface Listener {
        /** 进入显示态 */
        void onShow(@NonNull DeliveryInfo target,
                float distanceMeters,
                @NonNull List<DeliveryInfo> nearby);

        /** 维持显示态的轻量更新（距离/目标变更） */
        void onUpdate(@NonNull DeliveryInfo target,
                float distanceMeters,
                @NonNull List<DeliveryInfo> nearby);

        /** 退出显示态 */
        void onHide();
    }

    /**
     * 轻量 Boost 接口，避免直接依赖具体定位实现。
     * Map 层可用 SmartLocationManager 的适配器实现：
     * () -> smartLocationManager.requestBoost(ms)
     */
    public interface Boostable {
        void requestBoost(long durationMs);
    }

    // ==== Dependencies (injected) ====
    @NonNull
    private final DeliveryFocusManager focusMgr;
    @NonNull
    private final InfoPillProximityController proximityController;
    @Nullable
    private Listener listener;
    @Nullable
    private Boostable boostable;

    // ==== Tunables ====
    private float nearbyRadiusMeters = MapConfig.NEARBY_RADIUS_METERS; // 近邻聚合半径
    private int nearbyLimit = MapConfig.NEARBY_LIMIT; // 近邻候选上限（用于 UI 聚合）

    // ==== Cached UI state (Phase 3: transition de-dup & observability) ====
    @Nullable
    private String lastTargetKey = null; // last visible target key
    private boolean lastVisible = false; // whether InfoPill was visible
    private float lastDistanceMeters = Float.NaN; // last distance pushed to UI
    private int lastNearbySize = 0; // last nearby count
    private static final float UPDATE_EPSILON_M = MapConfig.DISTANCE_EPSILON_M; // ignore sub-meter oscillation

    // ✅ P2: 排序缓存（减少CPU使用）
    private static final float CACHE_INVALIDATION_DISTANCE_M = 10.0f;
    @Nullable
    private Location lastSortLocation = null;
    @Nullable
    private List<DeliveryInfo> cachedSortedList = null;
    private int lastPendingListHashCode = 0;

    /** Phase 3: set proximity profile (SIMPLE/STANDARD/ADVANCED). */
    public void setProximityProfile(@NonNull InfoPillProximityController.ProximityProfile profile) {
        logI("setProximityProfile(" + profile + ")");
        proximityController.setProfile(profile);
    }

    /** Phase 3: optional ETA gating (ADVANCED profile). */
    public void setEtaProvider(@Nullable InfoPillProximityController.EtaProvider provider) {
        logI("setEtaProvider(" + (provider == null ? "null" : "non-null") + ")");
        proximityController.setEtaProvider(provider);
    }

    public void onDeliveryCompleted(@Nullable DeliveryInfo delivered) {
        String key = "null";
        if (delivered != null) {
            if (delivered.getOrderId() != null)
                key = "ID-" + delivered.getOrderId();
            else if (delivered.getOrderSn() != null)
                key = "SN-" + delivered.getOrderSn();
            else if (delivered.getRouteNumber() != null)
                key = "ROUTE-" + delivered.getRouteNumber();
        }
        logD("onDeliveryCompleted invoked for " + key);
        try {
            proximityController.onDeliveryCompleted(delivered);
        } catch (Throwable ignore) {
        }
    }

    /** Convenience: adapt SmartLocationManager to Boostable. */
    public static Boostable asBoostable(@NonNull SmartLocationManager mgr) {
        return durationMs -> {
            try {
                mgr.requestBoost(durationMs);
            } catch (Throwable ignore) {
            }
        };
    }

    public ProximityCoordinator(@NonNull DeliveryFocusManager focusMgr,
            @NonNull InfoPillProximityController controller) {
        this.focusMgr = focusMgr;
        this.proximityController = controller;
    }

    public void setListener(@Nullable Listener l) {
        this.listener = l;
    }

    public void setBoostable(@Nullable Boostable b) {
        this.boostable = b;
    }

    public void setNearbyRadiusMeters(float r) {
        this.nearbyRadiusMeters = Math.max(0f, r);
    }

    public void setNearbyLimit(int k) {
        this.nearbyLimit = Math.max(1, k);
    }

    // Convenience getters/setters for external panels/dev tools
    public float getNearbyRadiusMeters() {
        return nearbyRadiusMeters;
    }

    public int getNearbyLimit() {
        return nearbyLimit;
    }

    public void setNearbyConfig(float radiusMeters, int limit) {
        setNearbyRadiusMeters(radiusMeters);
        setNearbyLimit(limit);
    }

    /** Expose current proximity profile for UI/debug. */
    @NonNull
    public InfoPillProximityController.ProximityProfile getProximityProfile() {
        try {
            return proximityController.getProfile();
        } catch (Throwable t) {
            // Defensive: default to STANDARD if controller doesn't expose
            return InfoPillProximityController.ProximityProfile.STANDARD;
        }
    }

    /** Reset cached UI state (e.g., when fragment destroys view). */
    public void resetState() {
        lastTargetKey = null;
        lastVisible = false;
        lastDistanceMeters = Float.NaN;
        lastNearbySize = 0;
        // ✅ P2: 清除排序缓存
        lastSortLocation = null;
        cachedSortedList = null;
        lastPendingListHashCode = 0;
    }

    /**
     * 统一入口：由 Map 层在收到定位后调用（推荐在主线程）。
     * 
     * @param location 当前定位（司机位置）
     * @param state    运动状态（DRIVING/ON_FOOT/...）
     * @param pending  待派送列表快照
     */
    public void onLocation(@NonNull Location location,
            @NonNull SmartLocationManager.MovementState state,
            @NonNull List<DeliveryInfo> pending) {
        logD("onLocation enter loc=" + location.getLatitude() + "," + location.getLongitude()
                + ", mv=" + state
                + ", pending=" + (pending == null ? 0 : pending.size()));

        if (pending == null || pending.isEmpty()) {
            logD("onLocation: empty pending -> hide");
            dispatchHide();
            return;
        }

        // 1) 交由策略引擎做显隐决策（内部含节流/迟滞/冷却）
        InfoPillProximityController.ProximityDecision d = proximityController.evaluate(location, state, pending);

        // noisy removed

        // 2) 需要提频时，转发到定位层（解耦于具体实现）
        if (d.requestBoost && boostable != null) {
            try {
                boostable.requestBoost(d.boostDurationMs);
            } catch (Throwable ignore) {
            }
        }

        // 3) 准备近邻集合（只有在 show/update 时才做，避免无谓计算）
        if (d.show || d.update) {
            if (d.target == null) { // 防御：无目标则当作无事件
                return;
            }
            // ✅ P2: 使用缓存或重新排序
            List<DeliveryInfo> sorted = getCachedOrSort(location, pending);
            List<DeliveryInfo> nearby;
            try {
                // Prefer manager's optimized radius collector if available
                nearby = focusMgr.collectWithinRadius(sorted, nearbyRadiusMeters);
            } catch (Throwable t) {
                // Fallback: manual filter by radius
                nearby = new java.util.ArrayList<>();
                for (DeliveryInfo di : sorted) {
                    if (di == null)
                        continue;
                    float[] r = new float[1];
                    android.location.Location.distanceBetween(
                            location.getLatitude(), location.getLongitude(),
                            di.getLatitude(), di.getLongitude(), r);
                    if (r[0] <= nearbyRadiusMeters)
                        nearby.add(di);
                }
            }
            // noisy: skip log

            if (nearby == null)
                nearby = Collections.emptyList();

            if (d.show) {
                dispatchShow(d.target, d.distanceMeters, nearby);
                return;
            }
            // d.update
            dispatchUpdate(d.target, d.distanceMeters, nearby);
            return;
        }

        // 4) 决策为 hide
        if (d.hide) {
            dispatchHide();
        }
    }

    /**
     * ✅ P2: 获取缓存的排序列表或重新排序
     * 
     * @param location 当前位置
     * @param pending  待配送列表
     * @return 排序后的top-K列表
     */
    private List<DeliveryInfo> getCachedOrSort(@NonNull Location location,
            @NonNull List<DeliveryInfo> pending) {
        int currentHashCode = System.identityHashCode(pending);

        // 检查缓存是否有效
        boolean canUseCache = false;
        if (cachedSortedList != null && lastSortLocation != null) {
            // 检查列表是否相同
            boolean sameList = (currentHashCode == lastPendingListHashCode);

            // 检查距离变化
            float[] distResult = new float[1];
            android.location.Location.distanceBetween(
                    lastSortLocation.getLatitude(), lastSortLocation.getLongitude(),
                    location.getLatitude(), location.getLongitude(),
                    distResult);
            boolean nearbyLocation = distResult[0] < CACHE_INVALIDATION_DISTANCE_M;

            canUseCache = sameList && nearbyLocation;

            if (canUseCache) {
                logD("getCachedOrSort: using cache (moved " + String.format("%.1f", distResult[0]) + "m)");
            }
        }

        if (canUseCache) {
            return cachedSortedList;
        }

        // 缓存无效，重新排序
        List<DeliveryInfo> sorted = focusMgr.sortByDistance(location, pending, nearbyLimit);

        // 更新缓存
        cachedSortedList = sorted;
        lastSortLocation = new Location(location); // deep copy
        lastPendingListHashCode = currentHashCode;

        logD("getCachedOrSort: re-sorted (cache invalidated)");
        return sorted;
    }

    private static String buildKey(@NonNull DeliveryInfo di) {
        if (di.getOrderId() != null)
            return "ID-" + di.getOrderId();
        if (di.getOrderSn() != null)
            return "SN-" + di.getOrderSn();
        if (di.getRouteNumber() != null)
            return "ROUTE-" + di.getRouteNumber();
        return String.valueOf(di.hashCode());
    }

    /**
     * Whether this update is effectively the same as the last one.
     * We allow the controller to be the source of truth; this is only to avoid
     * spamming UI with identical payloads due to sub-meter jitter.
     */
    private boolean isDuplicateUpdate(@NonNull DeliveryInfo target, float distance, int nearbySize) {
        if (!lastVisible)
            return false; // previously hidden -> not dup
        final String key = buildKey(target);
        if (lastTargetKey == null || !key.equals(lastTargetKey))
            return false;
        if (Float.isNaN(distance) || Float.isNaN(lastDistanceMeters))
            return false;
        if (Math.abs(distance - lastDistanceMeters) > UPDATE_EPSILON_M)
            return false;
        return nearbySize == lastNearbySize; // same size is good enough for de-dup
    }

    private void dispatchShow(@NonNull DeliveryInfo target, float distance, @NonNull List<DeliveryInfo> nearby) {
        // noisy removed
        Listener l = this.listener;
        if (l == null)
            return;
        try {
            l.onShow(target, distance, nearby);
        } catch (Throwable ignore) {
        }
        // cache state
        lastVisible = true;
        lastTargetKey = buildKey(target);
        lastDistanceMeters = distance;
        lastNearbySize = (nearby == null ? 0 : nearby.size());
    }

    private void dispatchUpdate(@NonNull DeliveryInfo target, float distance, @NonNull List<DeliveryInfo> nearby) {
        final int size = (nearby == null ? 0 : nearby.size());
        if (isDuplicateUpdate(target, distance, size)) {
            return;
        }
        Listener l = this.listener;
        if (l == null)
            return;
        try {
            l.onUpdate(target, distance, nearby);
        } catch (Throwable ignore) {
        }
        // cache state
        lastVisible = true;
        lastTargetKey = buildKey(target);
        lastDistanceMeters = distance;
        lastNearbySize = size;
    }

    private void dispatchHide() {
        logD("dispatchHide");
        Listener l = this.listener;
        if (l == null) {
            resetState();
            return;
        }
        try {
            l.onHide();
        } catch (Throwable ignore) {
        }
        resetState();
    }
}
