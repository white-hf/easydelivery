package com.hf.easydelivery.map;

import android.location.Location;
import android.text.TextUtils;
import android.os.SystemClock;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.hf.easydelivery.dao.DeliveryInfo;
import com.hf.courierservice.apihelper.FileLog;

import com.hf.easydelivery.core.SmartLocationManager;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Map;
import java.util.HashMap;
import java.util.List;

import com.hf.easydelivery.map.config.ProfileManager;

/*
核心目的
这个类的唯一职责是：当被告知司机当前的位置和所有待派送的包裹时，它能立即回答以下两个关键问题：
1. “该关注谁？”：哪些包裹离司机最近？它们是否在同一个地点可以形成一个“派送组”？
2. “地图该怎么看？”：根据离下一个包裹的距离，地图应该放大到什么级别才能提供最佳导航视角？

---

执行流程 (Execution Flow)
这个流程通常在地图界面接收到一次新的GPS位置更新时被完整地触发一次。

第一步：筛选并排序 (The "Who")
外部调用者（例如 MapInnerFragment）会调用 sortByDistance() 方法。
- 输入: 司机当前最新的 Location、所有待派送包裹的完整 List<DeliveryInfo>、一个数量上限 limit (例如，只关心最近的20个)。
- 执行过程:
  1. 过滤: 方法首先会遍历所有包裹，调用 isCoordinateValid() 丢弃那些没有有效GPS坐标的包裹。
  2. 排序: 然后，它使用 Comparator 对所有有效包裹进行排序。排序的依据是每个包裹与司机当前位置的直线距离。
  3. 截断: 最后，如果排序后的列表超过了 limit，它只返回最近的 limit 个结果。
- 输出: 一个按距离从近到远排序的、数量有限的包裹列表。

第二步：聚合附近包裹 (The "Where")
拿到上一步返回的已排序列表后，外部调用者会立刻调用 collectWithinRadius() 方法。
- 输入: 上一步产出的、已按距离排好序的包裹列表、一个半径值 radiusMeters (例如，50米)。
- 执行过程:
  1. 选定锚点: 方法将列表中的第一个包裹（也就是离司机最近的那个）作为“锚点”。
  2. 聚合: 它从锚点开始遍历列表，计算后续每个包裹与锚点包裹之间的距离。
  3. 如果距离小于等于指定的半径，就将这个包裹加入结果集。一旦遇到一个距离大于半径的包裹，它会立即停止遍历。
- 输出: 一个包含离司机最近的、且彼此之间距离很近（例如在同一栋楼）的“包裹组”。

第三步：计算最佳缩放级别 (The "How to Look")
外部调用者在计算出与最近包裹的实际距离后，会调用 computeZoomForDistance() 方法来获取地图的推荐缩放级别。
- 输入: 一个浮点数 distanceMeters (司机与最近包裹的距离)。
- 执行过程: 这是一个基于分段函数的决策逻辑：
  1. 极近距离 (<= 90m): 返回一个非常高的固定缩放级别 18.7f。
  2. 接近阶段 (<= 260m): 根据距离进行线性插值，平滑地将缩放级别从 18.7f 过渡到 17.5f。
  3. 离开阶段 (<= 360m): 再次进行线性插值，平滑地将缩放级别从 17.5f 过渡回默认的 17f。
  4. 巡航距离 (> 360m): 返回一个固定的默认缩放级别 17f。
- 输出: 一个具体的 float 类型的地图缩放级别。

---

总结
DeliveryFocusManager 的执行流程可以概括为：输入 -> 计算 -> 输出。
它像一个纯粹的“顾问”或“计算器”，被动地等待外部调用。每当司机位置更新，地图界面就会拿着新位置和所有包裹来“咨询”它。
这种无状态的设计非常健壮，彻底解决了之前“信息卡片卡在旧订单上”的问题。
*/
public class DeliveryFocusManager {

    private static final String TAG = "DeliveryFocusManager";

    private static void logI(String msg) {
        try {
            FileLog.i(TAG, msg);
        } catch (Throwable ignore) {
        }
    }

    // Pretty printer for distance logs: hide sentinel/absurd values
    private static String prettyDist(float d) {
        if (Float.isNaN(d) || d <= 0f || d >= 1e7f)
            return "none";
        float capped = Math.min(d, 9_999_999f);
        return String.format(java.util.Locale.US, "%.1f", capped);
    }

    // Throttle zoom logs to only print when it meaningfully changes
    private float lastZoomLogged = Float.NaN;

    private void maybeLogZoom(String ctx, float distanceMeters, float zoom) {
        if (Float.isNaN(lastZoomLogged) || Math.abs(zoom - lastZoomLogged) >= 0.2f) {
            logI("zoom[" + ctx + "] d=" + prettyDist(distanceMeters) + " -> " + zoom);
            lastZoomLogged = zoom;
        }
    }

    public static final class InfoGroup {
        public final List<DeliveryInfo> sameAddress;
        public final int nearbyCount;

        InfoGroup(List<DeliveryInfo> sameAddress, int nearbyCount) {
            this.sameAddress = sameAddress;
            this.nearbyCount = nearbyCount;
        }
    }

    private static final Comparator<DeliveryInfo> ADDRESS_COMPARATOR = (a, b) -> compareDeliveriesForAddress(a, b);

    public static final float DEFAULT_FOLLOW_ZOOM = 17f;
    private static final float CLOSE_DISTANCE_METERS = 90f; // CLOSE_DISTANCE_METERS: 强贴近（站点前/楼下）
    private static final float APPROACH_DISTANCE_METERS = 260f; // APPROACH_DISTANCE_METERS: 逐步接近（最后一段）
    private static final float LEAVE_DISTANCE_METERS = 360f; // LEAVE_DISTANCE_METERS: 离开回到巡航视角

    public enum ZoomProfile {
        STANDARD,
        BASIC,
        ADVANCED
    }

    // ==== Zoom Config (Phase 1 externalization) ====
    public static final class ZoomConfig {
        public float closeMeters;
        public float approachMeters;
        public float leaveMeters;
        public float defaultZoom;
        public float closeZoom;
        public float approachZoom;

        public ZoomConfig() {
        }

        public ZoomConfig(float closeMeters, float approachMeters, float leaveMeters,
                float defaultZoom, float closeZoom, float approachZoom) {
            this.closeMeters = closeMeters;
            this.approachMeters = approachMeters;
            this.leaveMeters = leaveMeters;
            this.defaultZoom = defaultZoom;
            this.closeZoom = closeZoom;
            this.approachZoom = approachZoom;
        }
    }

    // Backing config instance with defaults mirroring current constants
    private static final ZoomConfig ZOOM_CONFIG = new ZoomConfig(
            CLOSE_DISTANCE_METERS,
            APPROACH_DISTANCE_METERS,
            LEAVE_DISTANCE_METERS,
            DEFAULT_FOLLOW_ZOOM,
            18.7f,
            17.5f);

    // Map fit utility: meters-per-pixel at zoom level 0 (equator)
    private static final double METERS_PER_PIXEL_AT_ZOOM_0 = 156543.03392;

    /** Returns a live view of the current zoom config (for diagnostics/UI). */
    @NonNull
    public static ZoomConfig getZoomConfig() {
        logI("getZoomConfig() close=" + ZOOM_CONFIG.closeMeters + ", approach=" + ZOOM_CONFIG.approachMeters
                + ", leave=" + ZOOM_CONFIG.leaveMeters + ", defZoom=" + ZOOM_CONFIG.defaultZoom);
        return ZOOM_CONFIG;
    }

    /**
     * Apply a new config at runtime; any nulls are ignored (not applicable for
     * primitives).
     */
    public static void applyZoomConfig(@NonNull ZoomConfig cfg) {
        logI("applyZoomConfig(...) before -> close=" + ZOOM_CONFIG.closeMeters + ", approach="
                + ZOOM_CONFIG.approachMeters + ", leave=" + ZOOM_CONFIG.leaveMeters + ", defZoom="
                + ZOOM_CONFIG.defaultZoom);
        if (cfg == null)
            return;
        ZOOM_CONFIG.closeMeters = cfg.closeMeters;
        ZOOM_CONFIG.approachMeters = cfg.approachMeters;
        ZOOM_CONFIG.leaveMeters = cfg.leaveMeters;
        ZOOM_CONFIG.defaultZoom = cfg.defaultZoom;
        ZOOM_CONFIG.closeZoom = cfg.closeZoom;
        ZOOM_CONFIG.approachZoom = cfg.approachZoom;
        logI("applyZoomConfig(...) after  -> close=" + ZOOM_CONFIG.closeMeters + ", approach="
                + ZOOM_CONFIG.approachMeters + ", leave=" + ZOOM_CONFIG.leaveMeters + ", defZoom="
                + ZOOM_CONFIG.defaultZoom);
    }

    // ==== Proximity Config (Top-3 scheduling: sampling / lock / commute) ====
    public static final class ProximityConfig {
        // Distance threshold to consider we are in "far commute" mode
        public float farDistMeters = 3000f; // 3km
        // Min re-evaluation intervals when far, by movement state
        public long farSampleDrivingMs = 6000L; // 6s
        public long farSampleWalkingMs = 10000L; // 10s
        public long farSampleStationaryMs = 12000L; // 12s
        // Default min interval when near
        public long nearSampleDefaultMs = 1500L; // 1.5s
        // Lock / hysteresis around the primary focus to reduce jitter
        public float lockEnterMeters = 300f; // lock when <= 300m
        public float lockExitHysteresisMeters = 150f; // unlock when > 300m + 150m
        // Area commute suppression to skip evaluations while cruising between zones
        public float commuteSwitchMeters = 1200f; // switch zone if moved >= 1.2km
        public long commuteSuppressMs = 20000L; // suppress evaluations for 20s
    }

    // Backing proximity config instance with sane defaults
    private static final ProximityConfig PROXIMITY_CONFIG = new ProximityConfig();

    // Preset profiles for proximity/scheduling (used by ProfileManager bridge)
    private static final ProximityConfig PROXIMITY_PRESET_ADVANCED;
    private static final ProximityConfig PROXIMITY_PRESET_POWERSAVER;
    static {
        // Clone current defaults as ADVANCED baseline
        ProximityConfig adv = new ProximityConfig();
        adv.farDistMeters = 3000f;
        adv.farSampleDrivingMs = 6000L;
        adv.farSampleWalkingMs = 10000L;
        adv.farSampleStationaryMs = 12000L;
        adv.nearSampleDefaultMs = 1500L;
        adv.lockEnterMeters = 300f;
        adv.lockExitHysteresisMeters = 150f;
        adv.commuteSwitchMeters = 1200f;
        adv.commuteSuppressMs = 20000L;
        PROXIMITY_PRESET_ADVANCED = adv;

        // PowerSaver：更远才视为“远距”，评估频度更低，通勤抑制更积极
        ProximityConfig ps = new ProximityConfig();
        ps.farDistMeters = 5000f; // 5km 才认为远距，减少频繁切换
        ps.farSampleDrivingMs = 10000L; // 驾车 10s/次
        ps.farSampleWalkingMs = 15000L; // 步行 15s/次
        ps.farSampleStationaryMs = 20000L; // 静止 20s/次
        ps.nearSampleDefaultMs = 2500L; // 近距也放宽到 2.5s/次
        ps.lockEnterMeters = 350f; // 锁定阈值略放大，减抖动
        ps.lockExitHysteresisMeters = 200f; // 回退更宽
        ps.commuteSwitchMeters = 1500f; // 切区位移更大
        ps.commuteSuppressMs = 30000L; // 抑制窗口更长 30s
        PROXIMITY_PRESET_POWERSAVER = ps;
    }

    /** Copy fields from src → dst (internal util). */
    private static void copyProximityConfig(@NonNull ProximityConfig src, @NonNull ProximityConfig dst) {
        dst.farDistMeters = src.farDistMeters;
        dst.farSampleDrivingMs = src.farSampleDrivingMs;
        dst.farSampleWalkingMs = src.farSampleWalkingMs;
        dst.farSampleStationaryMs = src.farSampleStationaryMs;
        dst.nearSampleDefaultMs = src.nearSampleDefaultMs;
        dst.lockEnterMeters = src.lockEnterMeters;
        dst.lockExitHysteresisMeters = src.lockExitHysteresisMeters;
        dst.commuteSwitchMeters = src.commuteSwitchMeters;
        dst.commuteSuppressMs = src.commuteSuppressMs;
    }

    /**
     * Returns the current proximity (Top-3 scheduling) configuration for
     * diagnostics/UI.
     */
    @NonNull
    public static ProximityConfig getProximityConfig() {
        logI("getProximityConfig() farDist=" + PROXIMITY_CONFIG.farDistMeters
                + ", intervals(far d/w/s)=" + PROXIMITY_CONFIG.farSampleDrivingMs + "/"
                + PROXIMITY_CONFIG.farSampleWalkingMs + "/" + PROXIMITY_CONFIG.farSampleStationaryMs
                + ", nearInterval=" + PROXIMITY_CONFIG.nearSampleDefaultMs
                + ", lock=" + PROXIMITY_CONFIG.lockEnterMeters + "+" + PROXIMITY_CONFIG.lockExitHysteresisMeters
                + ", commute(switch/suppress)=" + PROXIMITY_CONFIG.commuteSwitchMeters + "/"
                + PROXIMITY_CONFIG.commuteSuppressMs);
        return PROXIMITY_CONFIG;
    }

    /** Apply a new proximity config at runtime (all fields copied). */
    public static void applyProximityConfig(@NonNull ProximityConfig cfg) {
        if (cfg == null)
            return;
        logI("applyProximityConfig(...) before -> farDist=" + PROXIMITY_CONFIG.farDistMeters
                + ", nearInterval=" + PROXIMITY_CONFIG.nearSampleDefaultMs);
        PROXIMITY_CONFIG.farDistMeters = cfg.farDistMeters;
        PROXIMITY_CONFIG.farSampleDrivingMs = cfg.farSampleDrivingMs;
        PROXIMITY_CONFIG.farSampleWalkingMs = cfg.farSampleWalkingMs;
        PROXIMITY_CONFIG.farSampleStationaryMs = cfg.farSampleStationaryMs;
        PROXIMITY_CONFIG.nearSampleDefaultMs = cfg.nearSampleDefaultMs;
        PROXIMITY_CONFIG.lockEnterMeters = cfg.lockEnterMeters;
        PROXIMITY_CONFIG.lockExitHysteresisMeters = cfg.lockExitHysteresisMeters;
        PROXIMITY_CONFIG.commuteSwitchMeters = cfg.commuteSwitchMeters;
        PROXIMITY_CONFIG.commuteSuppressMs = cfg.commuteSuppressMs;
        logI("applyProximityConfig(...) after  -> farDist=" + PROXIMITY_CONFIG.farDistMeters
                + ", nearInterval=" + PROXIMITY_CONFIG.nearSampleDefaultMs);
    }

    /**
     * Bridge for external ProfileManager:
     * - ADVANCED → 恢复/应用高级预设（当前默认）
     * - POWERSAVER → 应用更省电的阈值与采样节流
     *
     * 可多次调用（开发者面板热切换），立即覆盖运行时配置。
     */
    public void applyAppProfile(@NonNull ProfileManager.AppProfile profile) {
        logI("applyAppProfile(" + profile + ") - before farDist=" + PROXIMITY_CONFIG.farDistMeters
                + ", nearInterval=" + PROXIMITY_CONFIG.nearSampleDefaultMs);
        if (profile == ProfileManager.AppProfile.POWERSAVER) {
            copyProximityConfig(PROXIMITY_PRESET_POWERSAVER, PROXIMITY_CONFIG);
        } else {
            copyProximityConfig(PROXIMITY_PRESET_ADVANCED, PROXIMITY_CONFIG);
        }
        logI("applyAppProfile(" + profile + ") - after  farDist=" + PROXIMITY_CONFIG.farDistMeters
                + ", nearInterval=" + PROXIMITY_CONFIG.nearSampleDefaultMs);
    }

    // Strategy interface for zoom computation (no if/else branching per profile)
    public interface ZoomStrategy {
        float compute(float distanceMeters);
    }

    // STANDARD strategy: mirrors the existing three-segment curve
    private static final class StandardZoomStrategy implements ZoomStrategy {
        @Override
        public float compute(float distanceMeters) {
            final float close = ZOOM_CONFIG.closeMeters;
            final float approach = ZOOM_CONFIG.approachMeters;
            final float leave = ZOOM_CONFIG.leaveMeters;
            final float defZoom = ZOOM_CONFIG.defaultZoom;
            final float closeZoom = ZOOM_CONFIG.closeZoom; // was 18.7f
            final float approachZoom = ZOOM_CONFIG.approachZoom; // was 17.5f

            if (distanceMeters <= close)
                return closeZoom;
            if (distanceMeters <= approach) {
                float ratio = (distanceMeters - close) / (approach - close);
                return closeZoom + clamp(ratio, 0f, 1f) * (approachZoom - closeZoom);
            }
            if (distanceMeters <= leave) {
                float ratio = (distanceMeters - approach) / (leave - approach);
                return approachZoom + clamp(ratio, 0f, 1f) * (defZoom - approachZoom);
            }
            return defZoom;
        }
    }

    // BASIC strategy: currently identical to STANDARD (safe rollout). Can diverge
    // later.
    private static final class BasicZoomStrategy implements ZoomStrategy {
        private final ZoomStrategy delegate = new StandardZoomStrategy();

        @Override
        public float compute(float distanceMeters) {
            return delegate.compute(distanceMeters);
        }
    }

    // Registry of strategies (no switch/case needed to extend)
    private static final Map<ZoomProfile, ZoomStrategy> ZOOM_STRATEGIES = new HashMap<>();
    static {
        ZOOM_STRATEGIES.put(ZoomProfile.STANDARD, new StandardZoomStrategy());
        ZOOM_STRATEGIES.put(ZoomProfile.BASIC, new BasicZoomStrategy());
        ZOOM_STRATEGIES.put(ZoomProfile.ADVANCED, new StandardZoomStrategy());
        logI("ZOOM_STRATEGIES initialized: " + ZOOM_STRATEGIES.keySet());
    }

    // Default strategy maintains backward compatibility
    private static final ZoomStrategy DEFAULT_ZOOM_STRATEGY = ZOOM_STRATEGIES.get(ZoomProfile.STANDARD);

    // ==== Distance ranking strategy (Phase 2 optional) ====
    public enum DistanceRankProfile {
        SIMPLE, BALANCED, ADVANCED
    }

    public interface DistanceRankStrategy {
        @NonNull
        List<DeliveryInfo> rank(@NonNull Location location,
                @NonNull List<DeliveryInfo> deliveries,
                int limit);
    }

    private static final Map<DistanceRankProfile, DistanceRankStrategy> DISTANCE_RANK_STRATEGIES = new HashMap<>();
    private static DistanceRankProfile distanceRankProfile = DistanceRankProfile.SIMPLE; // default preserves current
                                                                                         // behavior
    private static DistanceRankStrategy distanceRankStrategy;

    /** Region thresholds for InfoPill/Proximity logic. */
    public static final class RegionConfig {
        public float showRadiusMeters = 200f;
        public float hideRadiusMeters = 260f;
        public float clusterRadiusMeters = 800f;
        public float clusterHopMeters = 2_000f;
        public long completionHintWindowMs = 120_000L; // 2 min window after completion
        public long etaGateSeconds = 120L;
    }

    private static final RegionConfig REGION_CONFIG = new RegionConfig();

    public static void setDistanceRankProfile(@NonNull DistanceRankProfile profile) {
        logI("setDistanceRankProfile(" + profile + ")");
        distanceRankProfile = profile;
        DistanceRankStrategy s = DISTANCE_RANK_STRATEGIES.get(profile);
        if (s == null)
            s = new SimpleDistanceRankStrategy();
        distanceRankStrategy = s;
    }

    static {
        DISTANCE_RANK_STRATEGIES.put(DistanceRankProfile.SIMPLE, new SimpleDistanceRankStrategy());
        DISTANCE_RANK_STRATEGIES.put(DistanceRankProfile.BALANCED, new TwoStageTopKDistanceRankStrategy());
        DISTANCE_RANK_STRATEGIES.put(DistanceRankProfile.ADVANCED, new TwoStageTopKDistanceRankStrategy()); // alias for
                                                                                                            // now
        distanceRankStrategy = DISTANCE_RANK_STRATEGIES.get(DistanceRankProfile.SIMPLE);
        logI("DISTANCE_RANK_STRATEGIES initialized: " + DISTANCE_RANK_STRATEGIES.keySet());
    }

    public void applyRegionConfig(@NonNull RegionConfig cfg) {
        if (cfg == null)
            return;
        REGION_CONFIG.showRadiusMeters = cfg.showRadiusMeters;
        REGION_CONFIG.hideRadiusMeters = Math.max(cfg.hideRadiusMeters, REGION_CONFIG.showRadiusMeters);
        REGION_CONFIG.clusterRadiusMeters = Math.max(cfg.clusterRadiusMeters, REGION_CONFIG.hideRadiusMeters);
        REGION_CONFIG.clusterHopMeters = Math.max(500f, cfg.clusterHopMeters);
        REGION_CONFIG.completionHintWindowMs = Math.max(30_000L, cfg.completionHintWindowMs);
        REGION_CONFIG.etaGateSeconds = Math.max(30L, cfg.etaGateSeconds);
    }

    public RegionConfig getRegionConfig() {
        return REGION_CONFIG;
    }

    // Lightweight holder for precise ranking
    public static final class DeliveryWithDistance {
        final DeliveryInfo info;
        final double distanceMeters;

        DeliveryWithDistance(DeliveryInfo info, double distanceMeters) {
            this.info = info;
            this.distanceMeters = distanceMeters;
        }
    }

    public static final class NearestResult {
        public final DeliveryInfo delivery;
        public final float distanceMeters;

        private NearestResult(@Nullable DeliveryInfo delivery, float distanceMeters) {
            this.delivery = delivery;
            this.distanceMeters = distanceMeters;
        }

        public static NearestResult empty() {
            return new NearestResult(null, Float.MAX_VALUE);
        }

        public static NearestResult of(@NonNull DeliveryInfo delivery, float distanceMeters) {
            return new NearestResult(delivery, distanceMeters);
        }
    }

    // Strategy 1: SIMPLE — current precise-all then sort behavior
    private static final class SimpleDistanceRankStrategy implements DistanceRankStrategy {
        @NonNull
        @Override
        public List<DeliveryInfo> rank(@NonNull Location location,
                @NonNull List<DeliveryInfo> deliveries,
                int limit) {
            logI("[SIMPLE] rank() start: in=" + deliveries.size() + ", limit=" + limit);
            if (deliveries.isEmpty() || limit <= 0) {
                logI("[SIMPLE] rank() done: out=0");
                return Collections.emptyList();
            }
            List<DeliveryInfo> filtered = new ArrayList<>();
            for (DeliveryInfo info : deliveries) {
                if (info == null || !isCoordinateValid(info))
                    continue;
                filtered.add(info);
            }
            if (filtered.isEmpty()) {
                logI("[SIMPLE] rank() done: out=0");
                return Collections.emptyList();
            }
            filtered.sort(
                    Comparator.comparingDouble(info -> distanceMeters(location.getLatitude(), location.getLongitude(),
                            info.getLatitude(), info.getLongitude())));
            if (filtered.size() > limit) {
                logI("[SIMPLE] rank() done: out=" + limit);
                return new ArrayList<>(filtered.subList(0, limit));
            }
            logI("[SIMPLE] rank() done: out=" + filtered.size());
            return filtered;
        }
    }

    // Strategy 2: BALANCED — approximate preselect (top-K by planar approx) then
    // precise sort
    private static final class TwoStageTopKDistanceRankStrategy implements DistanceRankStrategy {
        private static final int DEFAULT_K_APPROX = 100; // preselect size before precise

        @NonNull
        @Override
        public List<DeliveryInfo> rank(@NonNull Location location,
                @NonNull List<DeliveryInfo> deliveries,
                int limit) {
            logI("[BALANCED] rank() start: in=" + deliveries.size() + ", limit=" + limit);
            if (deliveries.isEmpty() || limit <= 0) {
                logI("[BALANCED] approx empty -> out=0");
                return Collections.emptyList();
            }

            // Parameters for planar approximation (meters per degree)
            final double lat0Rad = Math.toRadians(location.getLatitude());
            final double cosLat0 = Math.cos(lat0Rad);
            final double baseLat = location.getLatitude();
            final double baseLon = location.getLongitude();

            class ApproxItem {
                final DeliveryInfo info;
                final double d2;

                ApproxItem(DeliveryInfo i, double d2) {
                    this.info = i;
                    this.d2 = d2;
                }
            }
            ArrayList<ApproxItem> approx = new ArrayList<>();
            for (DeliveryInfo info : deliveries) {
                if (info == null || !isCoordinateValid(info))
                    continue;
                double dy = (info.getLatitude() - baseLat) * 110_540.0; // meters/°lat
                double dx = (info.getLongitude() - baseLon) * (111_320.0 * cosLat0); // meters/°lon adjusted by cos(lat)
                double d2 = dx * dx + dy * dy;
                approx.add(new ApproxItem(info, d2));
            }
            // continue to emptiness check
            if (approx.isEmpty()) {
                logI("[BALANCED] approx empty -> out=0");
                return Collections.emptyList();
            }

            approx.sort(Comparator.comparingDouble(a -> a.d2));
            final int k = Math.max(limit * 2, DEFAULT_K_APPROX);
            if (approx.size() > k)
                approx.subList(k, approx.size()).clear();
            logI("[BALANCED] approx topK k=" + Math.max(limit * 2, DEFAULT_K_APPROX) + ", kept=" + approx.size());

            // Precise pass for preselected candidates
            ArrayList<DeliveryInfo> precise = new ArrayList<>(approx.size());
            approx.sort(Comparator.comparingDouble(a -> a.d2)); // keep stable order (optional)
            ArrayList<DeliveryWithDistance> scored = new ArrayList<>(approx.size());
            for (ApproxItem a : approx) {
                float d = distanceMeters(location.getLatitude(), location.getLongitude(),
                        a.info.getLatitude(), a.info.getLongitude());
                if (d == Float.MAX_VALUE)
                    continue;
                scored.add(new DeliveryWithDistance(a.info, d));
            }
            if (scored.isEmpty()) {
                logI("[BALANCED] scored empty -> out=0");
                return Collections.emptyList();
            }
            scored.sort(Comparator.comparingDouble(o -> o.distanceMeters));
            int cut = (limit > 0 && scored.size() > limit) ? limit : scored.size();
            for (int i = 0; i < cut; i++)
                precise.add(scored.get(i).info);
            logI("[BALANCED] rank() done: preciseOut=" + (cut));
            return precise;
        }
    }

    /**
     * Returns up to {@code limit} deliveries sorted by their straight-line distance
     * from the given
     * location. Null/invalid coordinates are ignored.
     */
    @NonNull
    public List<DeliveryInfo> sortByDistance(@Nullable Location location,
            @Nullable List<DeliveryInfo> deliveries,
            int limit) {
        logI("sortByDistance() start: deliveries=" + (deliveries == null ? 0 : deliveries.size()) + ", limit=" + limit
                + ", loc=" + (location == null ? "null" : (location.getLatitude() + "," + location.getLongitude())));
        if (location == null || deliveries == null || deliveries.isEmpty() || limit <= 0) {
            return Collections.emptyList();
        }
        List<DeliveryInfo> out = distanceRankStrategy.rank(location, deliveries, limit);
        logI("sortByDistance() done: resultSize=" + (out == null ? 0 : out.size()) + ", strategy="
                + distanceRankProfile);
        return out == null ? Collections.emptyList() : out;
    }

    public NearestResult findNearest(@Nullable Location location,
            @Nullable List<DeliveryInfo> deliveries) {
        if (location == null || deliveries == null || deliveries.isEmpty()) {
            return NearestResult.empty();
        }
        DeliveryInfo nearest = null;
        float nearestDist = Float.MAX_VALUE;
        for (DeliveryInfo info : deliveries) {
            if (info == null || !isCoordinateValid(info))
                continue;
            float d = distanceMeters(location.getLatitude(), location.getLongitude(),
                    info.getLatitude(), info.getLongitude());
            if (Float.isNaN(d))
                continue;
            if (d < nearestDist) {
                nearestDist = d;
                nearest = info;
            }
        }
        if (nearest == null) {
            return NearestResult.empty();
        }
        return NearestResult.of(nearest, nearestDist);
    }

    /**
     * Returns the sublist of {@code sorted} deliveries that fall within
     * {@code radiusMeters} of the
     * first (nearest) delivery. The input is expected to already be sorted by
     * distance.
     */
    @NonNull
    public List<DeliveryInfo> collectWithinRadius(@Nullable List<DeliveryInfo> sorted,
            float radiusMeters) {
        logI("collectWithinRadius() start: sorted=" + (sorted == null ? 0 : sorted.size()) + ", radius="
                + radiusMeters);
        if (sorted == null || sorted.isEmpty()) {
            return Collections.emptyList();
        }
        DeliveryInfo anchor = sorted.get(0);
        List<DeliveryInfo> result = new ArrayList<>();
        for (DeliveryInfo candidate : sorted) {
            if (candidate == null)
                continue;
            float dist = distanceBetween(anchor, candidate);
            if (dist <= radiusMeters) {
                result.add(candidate);
            } else {
                break; // list is sorted, so we can stop once outside the radius
            }
        }
        logI("collectWithinRadius() done: out=" + result.size());
        return result;
    }

    public InfoGroup buildInfoGroup(@Nullable DeliveryInfo anchor,
            @Nullable List<DeliveryInfo> nearby) {
        int nearbyCount = (nearby == null) ? 0 : nearby.size();
        List<DeliveryInfo> sameAddress = new ArrayList<>();
        if (anchor != null) {
            List<DeliveryInfo> candidates = (nearby == null || nearby.isEmpty())
                    ? Collections.singletonList(anchor)
                    : nearby;
            for (DeliveryInfo candidate : candidates) {
                if (candidate == null)
                    continue;
                if (isSameAddress(anchor, candidate)) {
                    sameAddress.add(candidate);
                }
            }
            if (sameAddress.isEmpty()) {
                sameAddress.add(anchor);
            }
        }
        return new InfoGroup(sameAddress, nearbyCount);
    }

    public static Comparator<DeliveryInfo> getAddressComparator() {
        return ADDRESS_COMPARATOR;
    }

    public float distanceTo(@Nullable DeliveryInfo info, @NonNull Location location) {
        if (info == null || !isCoordinateValid(info)) {
            logI("distanceTo(): invalid coord -> INF(far) for info=" + (info == null ? "null" : info.getOrderId()));
            return Float.MAX_VALUE;
        }
        return distanceMeters(location.getLatitude(), location.getLongitude(),
                info.getLatitude(), info.getLongitude());
    }

    /**
     * Computes a camera zoom recommendation based purely on the nearest distance.
     * The curve mirrors
     * the previous behaviour (tight zoom up close, gentle pull-back as distance
     * grows) but remains
     * stateless.
     */
    public float computeZoomForDistance(float distanceMeters) {
        float z = DEFAULT_ZOOM_STRATEGY.compute(distanceMeters);
        maybeLogZoom("default", distanceMeters, z);
        return z;
    }

    /**
     * Computes the zoom level required to fit a given distance within the screen
     * height.
     * Uses the Web Mercator projection formula:
     * Resolution (meters/pixel) = 156543.03392 * cos(lat) / 2^zoom
     * We want: Resolution * (ScreenDimension * Padding) >= Distance
     *
     * @param distanceMeters        Distance to the target.
     * @param latitude              Current latitude (affects scale).
     * @param screenDimensionPixels Screen dimension (usually height) in pixels.
     * @param paddingFactor         Fraction of screen to use (e.g., 0.5 for half
     *                              screen).
     * @return The calculated zoom level, clamped to [14, 19].
     */
    public static float computeZoomToFit(double distanceMeters, double latitude, int screenDimensionPixels,
            float paddingFactor) {
        if (distanceMeters <= 10)
            return 19f; // Too close, just max zoom
        if (screenDimensionPixels <= 0)
            return DEFAULT_FOLLOW_ZOOM;

        // Earth circumference / 256 pixels
        final double EQUATOR_METERS_PER_PIXEL = 156543.03392;
        double metersPerPixel = distanceMeters / (screenDimensionPixels * paddingFactor);
        double cosLat = Math.cos(Math.toRadians(latitude));

        // 2^zoom = (EQUATOR * cosLat) / metersPerPixel
        // zoom = log2( (EQUATOR * cosLat) / metersPerPixel )
        double zoom = Math.log((EQUATOR_METERS_PER_PIXEL * cosLat) / metersPerPixel) / Math.log(2);

        // Clamp to reasonable bounds
        return (float) Math.max(14.0, Math.min(zoom, 19.0));
    }

    /**
     * Computes the "Smart Zoom" level for stationary/walking modes.
     * Applies a safety margin (1.15x) to the distance and calculates zoom to fit
     * within the visible map height.
     * 
     * @param distanceMeters     Distance to the target package
     * @param latitude           Current latitude (for projection calculation)
     * @param visibleMapHeightPx Visible height of the map in pixels
     * @return The calculated zoom level
     */
    public static float computeSmartZoom(float distanceMeters, double latitude, int visibleMapHeightPx) {
        float adjustedDistance = distanceMeters * 1.15f;
        return computeZoomToFit(
                adjustedDistance,
                latitude,
                visibleMapHeightPx,
                0.80f); // 0.80f padding factor as per original logic
    }

    public float computeZoomForDistance(float distanceMeters, @NonNull ZoomProfile profile) {
        ZoomStrategy s = ZOOM_STRATEGIES.get(profile);
        if (s == null)
            s = DEFAULT_ZOOM_STRATEGY;
        float z = s.compute(distanceMeters);
        maybeLogZoom("profile=" + profile, distanceMeters, z);
        return z;
    }

    public float computeZoomForDistance(float distanceMeters, @NonNull ZoomStrategy strategy) {
        float z = strategy.compute(distanceMeters);
        maybeLogZoom("strategy=" + strategy.getClass().getSimpleName(), distanceMeters, z);
        return z;
    }

    private static boolean isCoordinateValid(@NonNull DeliveryInfo info) {
        return info.getLatitude() != 0d && info.getLongitude() != 0d;
    }

    private float distanceBetween(@NonNull DeliveryInfo a, @NonNull DeliveryInfo b) {
        return distanceMeters(a.getLatitude(), a.getLongitude(), b.getLatitude(), b.getLongitude());
    }

    private static float distanceMeters(double lat1, double lon1, double lat2, double lon2) {
        float[] results = new float[1];
        Location.distanceBetween(lat1, lon1, lat2, lon2, results);
        return results[0];
    }

    private static boolean isSameAddress(DeliveryInfo a, DeliveryInfo b) {
        if (a == null || b == null)
            return false;
        AddressKey keyA = buildAddressKey(a);
        AddressKey keyB = buildAddressKey(b);
        return keyA.street.equals(keyB.street)
                && keyA.civil == keyB.civil
                && keyA.unit.emptyFlag == keyB.unit.emptyFlag
                && keyA.unit.numeric == keyB.unit.numeric
                && keyA.unit.raw.equals(keyB.unit.raw);
    }

    private static int compareDeliveriesForAddress(DeliveryInfo a, DeliveryInfo b) {
        AddressKey keyA = buildAddressKey(a);
        AddressKey keyB = buildAddressKey(b);
        int cmp = keyA.street.compareTo(keyB.street);
        if (cmp != 0)
            return cmp;
        cmp = Integer.compare(keyA.civil, keyB.civil);
        if (cmp != 0)
            return cmp;
        cmp = Integer.compare(keyA.unit.emptyFlag, keyB.unit.emptyFlag);
        if (cmp != 0)
            return cmp;
        cmp = Integer.compare(keyA.unit.numeric, keyB.unit.numeric);
        if (cmp != 0)
            return cmp;
        cmp = keyA.unit.raw.compareTo(keyB.unit.raw);
        if (cmp != 0)
            return cmp;
        String routeA = safeString(a.getRouteNumber());
        String routeB = safeString(b.getRouteNumber());
        return routeA.compareTo(routeB);
    }

    private static AddressKey buildAddressKey(DeliveryInfo info) {
        if (info == null) {
            return new AddressKey("", Integer.MAX_VALUE, UnitKey.EMPTY);
        }
        String street = streetNameKey(info);
        int civil = safeCivilNumber(info);
        UnitKey unit = buildUnitKey(info);
        return new AddressKey(street, civil, unit);
    }

    private static String streetNameKey(DeliveryInfo info) {
        String street = info.getStreetName();
        if (TextUtils.isEmpty(street) && info.getAddress() != null) {
            street = info.getAddress();
        }
        if (street == null)
            street = "";
        return street.toLowerCase(java.util.Locale.US)
                .replaceAll("[^a-z0-9]", " ")
                .replaceAll("\\s+", " ")
                .trim();
    }

    private static int safeCivilNumber(DeliveryInfo info) {
        Integer civil = info.getCivilNumber();
        if (civil == null || civil <= 0) {
            return Integer.MAX_VALUE;
        }
        return civil;
    }

    private static UnitKey buildUnitKey(DeliveryInfo info) {
        String raw = info.getUnitNumber();
        if (TextUtils.isEmpty(raw)) {
            raw = "";
        }
        String trimmed = raw.trim();
        if (trimmed.isEmpty()) {
            return UnitKey.EMPTY;
        }
        String lower = trimmed.toLowerCase(java.util.Locale.US);
        int numeric = parseFirstNumber(lower);
        return new UnitKey(0, numeric, lower);
    }

    private static int parseFirstNumber(String text) {
        if (TextUtils.isEmpty(text))
            return Integer.MAX_VALUE;
        String digits = text.replaceAll("[^0-9]", "");
        if (digits.isEmpty())
            return Integer.MAX_VALUE;
        try {
            return Integer.parseInt(digits);
        } catch (NumberFormatException ex) {
            return Integer.MAX_VALUE;
        }
    }

    private static String safeString(String value) {
        return value == null ? "" : value.toLowerCase(java.util.Locale.US);
    }

    private static final class UnitKey {
        static final UnitKey EMPTY = new UnitKey(1, Integer.MAX_VALUE, "");
        final int emptyFlag;
        final int numeric;
        final String raw;

        UnitKey(int emptyFlag, int numeric, String raw) {
            this.emptyFlag = emptyFlag;
            this.numeric = numeric;
            this.raw = raw;
        }
    }

    private static final class AddressKey {
        final String street;
        final int civil;
        final UnitKey unit;

        AddressKey(String street, int civil, UnitKey unit) {
            this.street = street;
            this.civil = civil;
            this.unit = unit;
        }
    }

    private static float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }

    // ===== Phase 3 runtime (minimal state for decisions) =====
    private List<DeliveryInfo> currentDeliveries = Collections.emptyList();
    private DeliveryInfo currentFocus = null;
    private List<DeliveryInfo> currentFocusGroup = Collections.emptyList();
    private float lastDistanceToFocus = Float.NaN;
    private boolean infoPillDismissedByUser = false;

    // ==== Top-3 runtime state ====
    private long lastEvalMs = 0L; // 上次评估时间（节流）
    private long suppressUntilMs = 0L; // 通勤抑制窗口截止
    private Double commuteAnchorLat = null; // 通勤锚点（跨区判断）
    private Double commuteAnchorLon = null;
    private String lockedPrimaryKey = null; // 焦点锁定键（<= lockEnterMeters 锁定）
    private float lastNearestDistanceMeters = Float.NaN; // 上次最近距离（用于节流和zoom提示）

    // --- SyncResult: for updateDeliveries
    public static final class SyncResult {
        private final boolean focusCleared;

        public SyncResult(boolean cleared) {
            this.focusCleared = cleared;
        }

        public boolean isFocusCleared() {
            return focusCleared;
        }
    }

    @NonNull
    public SyncResult updateDeliveries(@Nullable List<DeliveryInfo> deliveries) {
        List<DeliveryInfo> safe = (deliveries == null) ? Collections.emptyList() : deliveries;
        this.currentDeliveries = safe;
        boolean stillExists = false;
        if (currentFocus != null && safe != null) {
            for (DeliveryInfo di : safe) {
                if (di == null)
                    continue;
                if (equalsId(di, currentFocus)) {
                    stillExists = true;
                    break;
                }
            }
        }
        if (!stillExists) {
            currentFocus = null;
            currentFocusGroup = Collections.emptyList();
            lastDistanceToFocus = Float.NaN;
            // reset Top-3 runtime when focus disappears
            lockedPrimaryKey = null;
            suppressUntilMs = 0L;
            commuteAnchorLat = null;
            commuteAnchorLon = null;
            lastNearestDistanceMeters = Float.NaN;
            return new SyncResult(true);
        }
        return new SyncResult(false);
    }

    public static final class FocusDecision {
        private boolean show;
        private boolean hide;
        private boolean update;
        private boolean forceCameraFollow;
        private boolean focusChanged;
        private float preferredZoom = DEFAULT_FOLLOW_ZOOM;
        private DeliveryInfo focus;
        private List<DeliveryInfo> focusGroup = Collections.emptyList();

        public boolean shouldShowInfoPill() {
            return show;
        }

        public boolean shouldHideInfoPill() {
            return hide;
        }

        public boolean shouldUpdateInfoPill() {
            return update;
        }

        public boolean shouldForceCameraFollow() {
            return forceCameraFollow;
        }

        public boolean isFocusChanged() {
            return focusChanged;
        }

        public float getPreferredZoom() {
            return preferredZoom;
        }

        @Nullable
        public DeliveryInfo getFocus() {
            return focus;
        }

        @NonNull
        public List<DeliveryInfo> getFocusGroup() {
            return focusGroup == null ? Collections.emptyList() : focusGroup;
        }
    }

    @NonNull
    public FocusDecision onLocationUpdate(@NonNull Location location,
            @NonNull SmartLocationManager.MovementState state) {
        FocusDecision d = new FocusDecision();
        if (currentDeliveries == null || currentDeliveries.isEmpty()) {
            d.hide = true;
            d.forceCameraFollow = false;
            d.preferredZoom = DEFAULT_FOLLOW_ZOOM;
            return d;
        }

        // 使用上次最近距离作为节流提示；未知则按远距处理
        final float nearestHint = Float.isNaN(lastNearestDistanceMeters) ? (PROXIMITY_CONFIG.farDistMeters + 1f)
                : lastNearestDistanceMeters;
        if (!shouldEvaluate(location, state, nearestHint)) {
            d.preferredZoom = Float.isNaN(lastNearestDistanceMeters)
                    ? DEFAULT_FOLLOW_ZOOM
                    : computeZoomForDistance(lastNearestDistanceMeters);
            return d;
        }

        // ——— 实际评估：排序 + 近邻聚合 ———
        List<DeliveryInfo> nearest = sortByDistance(location, currentDeliveries, 50);
        if (nearest.isEmpty()) {
            d.hide = true;
            d.preferredZoom = DEFAULT_FOLLOW_ZOOM;
            return d;
        }

        DeliveryInfo primary = nearest.get(0);
        float distance = distanceTo(primary, location);
        List<DeliveryInfo> group = collectWithinRadius(nearest, 50f);

        // Preferred zoom based on distance
        d.preferredZoom = computeZoomForDistance(distance);

        // Focus change detection
        d.focusChanged = (currentFocus == null) || !equalsId(currentFocus, primary);
        d.focus = primary;
        d.focusGroup = group;

        // Info-pill gating
        float showThreshold = ZOOM_CONFIG.approachMeters; // ~260m 默认
        float hideThreshold = ZOOM_CONFIG.leaveMeters; // ~360m 默认

        if (distance <= showThreshold && !infoPillDismissedByUser) {
            d.show = true;
            d.update = !d.focusChanged;
        } else if (distance > hideThreshold) {
            d.hide = true;
        }

        // Camera follow policy
        d.forceCameraFollow = d.focusChanged || (distance <= showThreshold);

        // —— Top-3：锁定-解锁 ——
        if (distance <= PROXIMITY_CONFIG.lockEnterMeters) {
            lockedPrimaryKey = keyOf(primary);
        }

        // Persist new focus runtime
        currentFocus = primary;
        currentFocusGroup = group;
        lastDistanceToFocus = distance;
        lastNearestDistanceMeters = distance; // 记录最近距离用于下一次节流判定
        return d;
    }

    @Nullable
    public DeliveryInfo getCurrentFocus() {
        return currentFocus;
    }

    @NonNull
    public List<DeliveryInfo> getCurrentFocusGroupInfos() {
        return currentFocusGroup == null ? Collections.emptyList() : currentFocusGroup;
    }

    public float getPreferredFollowZoom() {
        if (Float.isNaN(lastDistanceToFocus))
            return DEFAULT_FOLLOW_ZOOM;
        return computeZoomForDistance(lastDistanceToFocus);
    }

    public void onInfoPillDismissedByUser() {
        infoPillDismissedByUser = true;
    }

    public void resetAll() {
        currentDeliveries = Collections.emptyList();
        currentFocus = null;
        currentFocusGroup = Collections.emptyList();
        lastDistanceToFocus = Float.NaN;
        infoPillDismissedByUser = false;
        lastEvalMs = 0L;
        suppressUntilMs = 0L;
        commuteAnchorLat = null;
        commuteAnchorLon = null;
        lockedPrimaryKey = null;
        lastNearestDistanceMeters = Float.NaN;
    }

    private boolean equalsId(@NonNull DeliveryInfo a, @NonNull DeliveryInfo b) {
        if (a == b)
            return true;
        Long idA = a.getOrderId();
        Long idB = b.getOrderId();
        if (idA != null && idB != null)
            return idA.equals(idB);
        String snA = a.getOrderSn();
        String snB = b.getOrderSn();
        return snA != null && snA.equals(snB);
    }

    // ==== Top-3 helpers for proximity gating ====
    private static String keyOf(@NonNull DeliveryInfo info) {
        Long id = info.getOrderId();
        if (id != null)
            return "ID:" + id;
        String sn = info.getOrderSn();
        if (sn != null)
            return "SN:" + sn;
        return "@" + System.identityHashCode(info);
    }

    private static float distanceMeters(@NonNull Location a, @NonNull DeliveryInfo b) {
        if (b == null || !isCoordinateValid(b))
            return Float.MAX_VALUE;
        return distanceMeters(a.getLatitude(), a.getLongitude(), b.getLatitude(), b.getLongitude());
    }

    private long minIntervalFor(@NonNull SmartLocationManager.MovementState mv, float nearestMeters) {
        final boolean far = nearestMeters > PROXIMITY_CONFIG.farDistMeters;
        switch (mv) {
            case NORMAL_DRIVING:
            case SLOW_DRIVING:
                return far ? PROXIMITY_CONFIG.farSampleDrivingMs : PROXIMITY_CONFIG.nearSampleDefaultMs;
            case WALKING:
                return far ? PROXIMITY_CONFIG.farSampleWalkingMs : PROXIMITY_CONFIG.nearSampleDefaultMs;
            case STATIONARY:
            default:
                return far ? PROXIMITY_CONFIG.farSampleStationaryMs : PROXIMITY_CONFIG.nearSampleDefaultMs;
        }
    }

    /**
     * Top-3：是否需要在本次 tick 中执行 proximity 评估（远距降采样 + 区域通勤极简 + 锁定/解锁）。
     * nearestHintMeters 可为空（NaN）则按远距处理。
     */
    private boolean shouldEvaluate(@NonNull Location loc,
            @NonNull SmartLocationManager.MovementState mv,
            float nearestHintMeters) {
        final long now = SystemClock.uptimeMillis();

        // 1) 通勤抑制窗口
        if (suppressUntilMs > 0 && now < suppressUntilMs) {
            logI("shouldEvaluate(): suppressed by commute window");
            return false;
        }

        // 2) 最小评估周期（基于远/近与运动状态）
        long minInterval = minIntervalFor(mv, nearestHintMeters);
        if ((now - lastEvalMs) < minInterval) {
            logI("shouldEvaluate(): minInterval guard " + (now - lastEvalMs) + "ms < " + minInterval + "ms");
            return false;
        }

        // 3) 区域通勤极简：远距且速度较高，若未达切区位移则进入抑制窗口
        final boolean far = nearestHintMeters > PROXIMITY_CONFIG.farDistMeters;
        float speedMps = loc.hasSpeed() ? loc.getSpeed() : 0f;
        if (far && speedMps >= 8.0f) { // ~28.8 km/h
            if (commuteAnchorLat == null || commuteAnchorLon == null) {
                commuteAnchorLat = loc.getLatitude();
                commuteAnchorLon = loc.getLongitude();
            } else {
                float moved = distanceMeters(
                        loc.getLatitude(), loc.getLongitude(),
                        commuteAnchorLat, commuteAnchorLon);
                if (moved < PROXIMITY_CONFIG.commuteSwitchMeters) {
                    suppressUntilMs = now + PROXIMITY_CONFIG.commuteSuppressMs;
                    lastEvalMs = now; // 记一次尝试
                    logI("shouldEvaluate(): start commute suppress, moved=" + moved);
                    return false;
                } else {
                    // 切换到新片区
                    commuteAnchorLat = loc.getLatitude();
                    commuteAnchorLon = loc.getLongitude();
                }
            }
        }

        // 4) 锁定解锁：若已锁定且与当前 focus 距离显著回退，则释放锁
        if (lockedPrimaryKey != null && currentFocus != null) {
            float d = distanceMeters(loc, currentFocus);
            if (d > (PROXIMITY_CONFIG.lockEnterMeters + PROXIMITY_CONFIG.lockExitHysteresisMeters)) {
                logI("shouldEvaluate(): release lock due to distance d=" + d);
                lockedPrimaryKey = null;
            }
        }

        lastEvalMs = now;
        return true;
    }

    /**
     * Compute a zoom level that fits the given distance (diameter) within a
     * vertical slice of the
     * visible map.
     *
     * @param distanceMeters    target distance to fit (meters, interpreted as
     *                          diameter)
     * @param latitude          current latitude (for meters-per-pixel)
     * @param availableHeightPx available map height in pixels
     * @param heightFraction    fraction of the height to use (0-1)
     * @return suggested zoom level
     */
    public static float computeZoomToFit(float distanceMeters,
            double latitude,
            int availableHeightPx,
            float heightFraction) {
        if (Float.isNaN(distanceMeters) || distanceMeters <= 0f) {
            return ZOOM_CONFIG.defaultZoom;
        }
        int h = Math.max(1, availableHeightPx);
        float fraction = Math.max(0.1f, Math.min(1f, heightFraction));
        double metersPerPixel = (distanceMeters * 2.0) / (h * fraction);
        double latRad = Math.toRadians(latitude);
        double denom = metersPerPixel <= 0 ? 1 : metersPerPixel;
        double zoom = Math.log(METERS_PER_PIXEL_AT_ZOOM_0 * Math.cos(latRad) / denom) / Math.log(2);
        return (float) zoom;
    }
}
