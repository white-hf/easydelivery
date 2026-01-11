package com.hf.easydelivery.map;

import com.hf.easydelivery.map.config.ProfileManager;

import android.location.Location;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.hf.easydelivery.core.facade.MovementState;
import com.hf.easydelivery.dao.DeliveryInfo;
import com.hf.courierservice.apihelper.FileLog;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Info Pill proximity decision controller.
 * Pure decision module (no UI / no map calls). It receives location/state and a snapshot of
 * pending deliveries, and returns a ProximityDecision. Uses a Strategy profile so adding new
 * behaviors requires no if/else branching.
 * Phase 3: Supports optional ETA gating and update suppression for pixel-consistency via Advanced strategy.
 *
 * Top-3 主案接入（锁定-解锁 + 远距降采样 + 区域通勤极简）：
 *  - 在 STANDARD/ADVANCED 策略中增加通勤抑制窗口、锁定/解锁与远距锚点逻辑；
 *  - 扩展 ProximityConfig（不与 ZoomConfig 混用，职责清晰）；
 *  - 扩展 InternalState 以保持锁定键、抑制窗口和通勤锚点；
 *  - 不改变任何对外 API，零回归接入。
 */
public class InfoPillProximityController {

    private static final String TAG = "InfoPillProximityController";
    // Silence noisy proximity logging (InfoPill stable)
    private static void logI(String msg) { }
    private static void logD(String msg) { }
    private static void logDecision(String prefix, ProximityDecision d) { }

    @NonNull
    public ProximityProfile getProfile() { return profile; }

    /**
     * Bridge for external profile manager:
     * AppProfile.POWERSAVER → BASIC (lowest-cost)
     * AppProfile.ADVANCED  → ADVANCED
     * (If future needs: map to STANDARD by caller.)
     */
    public void applyAppProfile(@NonNull ProfileManager.AppProfile appProfile) {
        ProximityProfile p = (appProfile == ProfileManager.AppProfile.POWERSAVER)
                ? ProximityProfile.BASIC
                : ProximityProfile.ADVANCED;
        setProfile(p);
    }

    // ==== Profiles ====
    public enum ProximityProfile { BASIC, STANDARD, ADVANCED }

    // ==== Proximity Config (Phase 1 externalization) ====
    public static final class ProximityConfig {
        // BASIC
        public long basicThrottleMs = 1000L;
        public float basicMinMoveM = 20f;

        // STANDARD（统一使用驾驶阈值，避免强耦合 MovementState）
        public long stdThrottleDrivingMs = 800L;
        public long stdThrottleOnFootMs = 2000L;     // 保留字段（不使用）
        public float stdMinMoveDrivingM = 15f;
        public float stdMinMoveOnFootM = 25f;        // 保留字段（不使用）

        // 进入/退出与冷却
        public float enterRadiusM = 500f;            // IN
        public float exitRadiusM  = 700f;            // OUT
        public long  showHideCooldownMs = 6000L;     // 6s
        public long  defaultBoostMs = 8000L;         // 8s（请求定位短促提升）

        // 远距带与降采样
        public float farBandM = 1200f;               // 视为“远距”的阈值（隐藏时生效）
        public long  farBandThrottleMs = 12000L;     // 远距降采样节流

        // Advanced (Phase 3) — ETA 与像素一致性抑制（可缺省）
        public int   advEtaGateSeconds = 45;         // 仅当 ETA <= 门槛时允许 SHOW；-1/0 关闭
        public float advMinUpdateDeltaM = 8f;        // UPDATE 时的最小距离变化量
        /** Simple local ETA model speed (km/h) used when no EtaProvider is injected. */
        public double simpleEtaSpeedKmh = 25.0;      // 城区默认 25 km/h，可在开发者面板调整

        // === Top‑3：锁定/解锁 + 区域通勤极简（与 UI enter/exit 解耦，减少抖动） ===
        public float lockEnterM = 300f;              // <=300m 锁定最近目标
        public float lockExitHysteresisM = 150f;     // >300+150=450m 释放锁
        public float commuteSwitchM = 1200f;         // 片区切换阈值（位移达到则重置锚点）
        public long  commuteSuppressMs = 20000L;     // 远距通勤抑制窗口（跳过评估）
    }

    private static final ProximityConfig PROX_CONFIG = new ProximityConfig();

    @NonNull public static ProximityConfig getProximityConfig() { return PROX_CONFIG; }

    public static void applyProximityConfig(@NonNull ProximityConfig cfg) {
        PROX_CONFIG.basicThrottleMs = cfg.basicThrottleMs;
        PROX_CONFIG.basicMinMoveM = cfg.basicMinMoveM;
        PROX_CONFIG.stdThrottleDrivingMs = cfg.stdThrottleDrivingMs;
        PROX_CONFIG.stdThrottleOnFootMs = cfg.stdThrottleOnFootMs;
        PROX_CONFIG.stdMinMoveDrivingM = cfg.stdMinMoveDrivingM;
        PROX_CONFIG.stdMinMoveOnFootM = cfg.stdMinMoveOnFootM;
        PROX_CONFIG.enterRadiusM = cfg.enterRadiusM;
        PROX_CONFIG.exitRadiusM = cfg.exitRadiusM;
        PROX_CONFIG.showHideCooldownMs = cfg.showHideCooldownMs;
        PROX_CONFIG.defaultBoostMs = cfg.defaultBoostMs;
        PROX_CONFIG.farBandM = cfg.farBandM;
        PROX_CONFIG.farBandThrottleMs = cfg.farBandThrottleMs;
        PROX_CONFIG.lockEnterM = cfg.lockEnterM;
        PROX_CONFIG.lockExitHysteresisM = cfg.lockExitHysteresisM;
        PROX_CONFIG.commuteSwitchM = cfg.commuteSwitchM;
        PROX_CONFIG.commuteSuppressMs = cfg.commuteSuppressMs;
        PROX_CONFIG.advEtaGateSeconds = cfg.advEtaGateSeconds;
        PROX_CONFIG.advMinUpdateDeltaM = cfg.advMinUpdateDeltaM;
        PROX_CONFIG.simpleEtaSpeedKmh = cfg.simpleEtaSpeedKmh;
        logI("Applied ProximityConfig: basicThrottle=" + PROX_CONFIG.basicThrottleMs
                + ", stdThrottleDriving=" + PROX_CONFIG.stdThrottleDrivingMs
                + ", enterRadius=" + PROX_CONFIG.enterRadiusM
                + ", exitRadius=" + PROX_CONFIG.exitRadiusM
                + ", showHideCooldown=" + PROX_CONFIG.showHideCooldownMs
                + ", farBand=" + PROX_CONFIG.farBandM
                + ", farBandThrottle=" + PROX_CONFIG.farBandThrottleMs
                + ", lock=" + PROX_CONFIG.lockEnterM + "+" + PROX_CONFIG.lockExitHysteresisM
                + ", commute(switch/suppress)=" + PROX_CONFIG.commuteSwitchM + "/" + PROX_CONFIG.commuteSuppressMs
                + ", etaGate=" + PROX_CONFIG.advEtaGateSeconds
                + ", minUpdM=" + PROX_CONFIG.advMinUpdateDeltaM
                + ", simpleEtaSpeedKmh=" + PROX_CONFIG.simpleEtaSpeedKmh);
    }

    /** Optional ETA provider for Advanced profile. */
    public interface EtaProvider {
        /** @return ETA seconds from current location to target delivery; return a negative value if unavailable. */
        int computeEtaSeconds(@NonNull Location from, @NonNull DeliveryInfo target);
    }
    @Nullable private EtaProvider etaProvider = null;
    public void setEtaProvider(@Nullable EtaProvider p){
        logI("setEtaProvider(" + (p==null?"null":"non-null") + ")");
        this.etaProvider = p;
    }

    /** Simple ETA (s) = straight-line distance / fixed speed; -1 if invalid. */
    private long estimateEtaSeconds(@NonNull Location from, @NonNull DeliveryInfo target){
        double lat = target.getLatitude();
        double lon = target.getLongitude();
        if (Double.isNaN(lat) || Double.isNaN(lon)) return -1L;
        if (lat == 0d && lon == 0d) return -1L;

        Location to = new Location("target");
        to.setLatitude(lat);
        to.setLongitude(lon);
        float meters = from.distanceTo(to);
        if (meters < 0.1f) return 0L;

        double mps = (PROX_CONFIG.simpleEtaSpeedKmh * 1000d) / 3600d;
        if (mps <= 0.0) return -1L;

        long sec = (long) Math.ceil(meters / mps);
        return Math.max(sec, 0L);
    }

    // ==== Decision output ====
    public static final class ProximityDecision {
        public final boolean show;           // show (enter proximity)
        public final boolean hide;           // hide (exit proximity)
        public final boolean update;         // update distance/contents while staying shown
        @Nullable public final DeliveryInfo target; // chosen delivery
        public final float distanceMeters;   // computed distance to target (if any)
        public final boolean requestBoost;   // ask location layer for a short boost
        public final long boostDurationMs;   // boost window

        private ProximityDecision(boolean show, boolean hide, boolean update,
                                  @Nullable DeliveryInfo target, float distanceMeters,
                                  boolean requestBoost, long boostDurationMs) {
            this.show = show; this.hide = hide; this.update = update;
            this.target = target; this.distanceMeters = distanceMeters;
            this.requestBoost = requestBoost; this.boostDurationMs = boostDurationMs;
        }

        public static ProximityDecision none() {
            return new ProximityDecision(false, false, false, null, Float.MAX_VALUE, false, 0L);
        }
    }

    // ==== Strategy interface ====
    public interface ProximityStrategy {
        @NonNull ProximityDecision evaluate(@NonNull Location loc,
                                            @NonNull MovementState state,
                                            @NonNull List<DeliveryInfo> pending,
                                            @NonNull InternalState s,
                                            @NonNull DeliveryFocusManager focusMgr);
    }

    /** Internal mutable state to support gating, hysteresis and caching. */
    public static final class InternalState {
        long lastEvalAtMs = 0L;
        @Nullable Location lastLoc = null;
        boolean pillShowing = false;
        long lastShowHideAtMs = 0L;
        @Nullable DeliveryInfo lastNearest = null;
        float lastNearestDist = Float.MAX_VALUE;

        // Top‑3 runtime
        @Nullable String lockedKey = null;      // key of locked target
        long suppressUntilMs = 0L;              // commute suppression window end
        @Nullable Double anchorLat = null;      // commute anchor
        @Nullable Double anchorLon = null;
        RegionState regionState = RegionState.IN_TRANSIT;
        double odometerMeters = 0d;
        double lastTransitAnchorMeters = 0d;
        long completionHintUntilMs = 0L;
        @Nullable String completionAnchorKey = null;
        boolean forceShowAfterCompletion = false;
    }

    public enum RegionState { IN_TRANSIT, APPROACH, INSIDE }

    // Strategy registry (no if/else for profiles)
    private final Map<ProximityProfile, ProximityStrategy> strategies = new EnumMap<>(ProximityProfile.class);
    private ProximityProfile profile = ProximityProfile.STANDARD; // default keeps current behavior
    private final InternalState state = new InternalState();
    private final DeliveryFocusManager focusMgr = new DeliveryFocusManager();
    private SimpleEtaEstimator simpleEtaEstimator = new SimpleEtaEstimator();

    public InfoPillProximityController() {
        strategies.put(ProximityProfile.STANDARD, new StandardProximityStrategy());
        strategies.put(ProximityProfile.BASIC, new BasicProximityStrategy());
        strategies.put(ProximityProfile.ADVANCED, new AdvancedProximityStrategy());
    }

    public void setProfile(@NonNull ProximityProfile p) {
        logI("setProfile(" + p + ")");
        this.profile = p;
    }

    public RegionState getRegionState() {
        return state.regionState;
    }

    public void setEtaEstimator(@Nullable SimpleEtaEstimator estimator) {
        if (estimator != null) {
            this.simpleEtaEstimator = estimator;
        }
    }

    public void setRegionConfig(@NonNull DeliveryFocusManager.RegionConfig regionConfig) {
        focusMgr.applyRegionConfig(regionConfig);
    }

    /** Main entry. Stateless in inputs, but holds InternalState for gating/ hysteresis. */
    @NonNull
    public ProximityDecision evaluate(@NonNull Location loc,
                                      @NonNull MovementState movement,
                                      @NonNull List<DeliveryInfo> pending) {
        logD("evaluate enter profile=" + profile + ", mv=" + movement
                + ", pending=" + (pending==null?0:pending.size())
                + ", lastNearest=" + (state.lastNearest==null?"null":state.lastNearest.getOrderId())
                + ", lastDist=" + state.lastNearestDist);
        ProximityStrategy strat = strategies.get(profile);
        if (strat == null) strat = strategies.get(ProximityProfile.STANDARD);
        ProximityDecision d = strat.evaluate(loc, movement, pending, state, focusMgr);
        logD("evaluate exit show=" + d.show + ", update=" + d.update + ", hide=" + d.hide
                + ", dist=" + d.distanceMeters + ", boost=" + d.requestBoost + "/" + d.boostDurationMs + "ms");
        return d;
    }

    /**
     * PowerSaver integration point:
     * When CameraActivity finishes a delivery and computes a candidate list (same-address priority),
     * we can request InfoPill to SHOW without running GPS-interval proximity computation.
     * This keeps controller's state consistent (pillShowing, lastNearest, distances).
     *
     * @param loc       current (or fallback) location; can be null to skip distance calc
     * @param suggested ordered candidates (first one is preferred to show)
     * @return          synthesized decision to SHOW, or NONE if inputs invalid
     */
    @NonNull
    public ProximityDecision onCameraSuggestion(@Nullable Location loc,
                                                @Nullable List<DeliveryInfo> suggested) {
        if (suggested == null || suggested.isEmpty()) return ProximityDecision.none();
        DeliveryInfo target = suggested.get(0);
        if (target == null) return ProximityDecision.none();

        float dist = Float.MAX_VALUE;
        if (loc != null) {
            double nlat = target.getLatitude();
            double nlon = target.getLongitude();
            if (!(Double.isNaN(nlat) || Double.isNaN(nlon) || (nlat == 0d && nlon == 0d))) {
                float[] r = new float[1];
                Location.distanceBetween(loc.getLatitude(), loc.getLongitude(), nlat, nlon, r);
                dist = r[0];
            }
        }

        long now = System.currentTimeMillis();
        state.pillShowing = true;
        state.lastShowHideAtMs = now;
        state.lastNearest = target;
        state.lastNearestDist = dist;
        state.lockedKey = keyOf(target); // align with Top-3 lock

        ProximityDecision d = new ProximityDecision(true, false, false, target, dist, false, 0L);
        logDecision("[SUGGEST] SHOW", d);
        return d;
    }

    public void onDeliveryCompleted(@Nullable DeliveryInfo delivered) {
        long now = System.currentTimeMillis();
        state.regionState = RegionState.INSIDE;
        state.completionHintUntilMs = now + focusMgr.getRegionConfig().completionHintWindowMs;
        state.completionAnchorKey = delivered == null ? null : keyOf(delivered);
        if (delivered != null) {
            state.lastNearest = delivered;
            state.lastNearestDist = 0f;
        }
        state.forceShowAfterCompletion = true;
        state.suppressUntilMs = 0L;
        state.anchorLat = null;
        state.anchorLon = null;
        DeliveryFocusManager.RegionConfig cfg = focusMgr.getRegionConfig();
        state.lastTransitAnchorMeters = Math.max(0d, state.odometerMeters - Math.max(1d, cfg.clusterHopMeters));
        logD("onDeliveryCompleted: region=INSIDE hintUntil=" + state.completionHintUntilMs);
    }

    // ==== BASIC strategy ====
    private static final class BasicProximityStrategy implements ProximityStrategy {
        @NonNull @Override
        public ProximityDecision evaluate(@NonNull Location loc,
                                          @NonNull MovementState state,
                                          @NonNull List<DeliveryInfo> pending,
                                          @NonNull InternalState s,
                                          @NonNull DeliveryFocusManager focusMgr) {
            logD("[BASIC] start pending=" + (pending==null?0:pending.size()));
            long now = System.currentTimeMillis();
            boolean bypassGates = s.forceShowAfterCompletion;
            if (!bypassGates && !movedEnough(s.lastLoc, loc, PROX_CONFIG.basicMinMoveM)
                    && !elapsed(now, s.lastEvalAtMs, PROX_CONFIG.basicThrottleMs)) {
                return ProximityDecision.none();
            }

            s.lastEvalAtMs = now; s.lastLoc = cloneOf(loc);

            // Find nearest once (simple & stable)
            List<DeliveryInfo> top1 = focusMgr.sortByDistance(loc, pending, 1);
            DeliveryInfo nearest = top1.isEmpty() ? null : top1.get(0);
            if (nearest == null) return maybeHideOnNoData(s);

            float[] dist = new float[1];
            Location.distanceBetween(loc.getLatitude(), loc.getLongitude(),
                    nearest.getLatitude(), nearest.getLongitude(), dist);
            float d = dist[0];

            DeliveryFocusManager.RegionConfig cfg = focusMgr.getRegionConfig();
            maybeExpireCompletionHint(s, d, now, cfg);
            ProximityDecision forced = forceShowAfterCompletionIfNeeded(s, nearest, d, now, cfg);
            if (forced != null) return forced;

            boolean cooled = elapsed(now, s.lastShowHideAtMs, PROX_CONFIG.showHideCooldownMs);

            if (!s.pillShowing) {
                if (d <= PROX_CONFIG.enterRadiusM && cooled) {
                    s.pillShowing = true; s.lastShowHideAtMs = now; s.lastNearest = nearest; s.lastNearestDist = d;
                    return new ProximityDecision(true, false, false, nearest, d, true, PROX_CONFIG.defaultBoostMs);
                }
                // keep hidden
                return ProximityDecision.none();
            } else {
                // Already showing: hysteresis + lightweight updates inside stable zone
                if (d >= PROX_CONFIG.exitRadiusM && cooled) {
                    s.pillShowing = false; s.lastShowHideAtMs = now; s.lastNearest = nearest; s.lastNearestDist = d;
                    return new ProximityDecision(false, true, false, nearest, d, false, 0L);
                }
                s.lastNearest = nearest; s.lastNearestDist = d;
                return new ProximityDecision(false, false, true, nearest, d, false, 0L);
            }
        }
    }

    // ==== STANDARD strategy ====
    private static final class StandardProximityStrategy implements ProximityStrategy {
        @NonNull @Override
        public ProximityDecision evaluate(@NonNull Location loc,
                                          @NonNull MovementState state,
                                          @NonNull List<DeliveryInfo> pending,
                                          @NonNull InternalState s,
                                          @NonNull DeliveryFocusManager focusMgr) {
            logD("[STD] start pending=" + (pending==null?0:pending.size()));
            long now = System.currentTimeMillis();
            boolean bypassGates = s.forceShowAfterCompletion;

            // Use STANDARD driving thresholds (reduce enum coupling)
            final long throttle = PROX_CONFIG.stdThrottleDrivingMs;
            final float minMove = PROX_CONFIG.stdMinMoveDrivingM;

            if (!bypassGates && !movedEnough(s.lastLoc, loc, minMove) && !elapsed(now, s.lastEvalAtMs, throttle))
                return ProximityDecision.none();

            // If currently hidden and we are likely far, down-sample evaluations
            if (!bypassGates && !s.pillShowing && s.lastNearestDist > PROX_CONFIG.farBandM
                    && !elapsed(now, s.lastEvalAtMs, PROX_CONFIG.farBandThrottleMs))
                return ProximityDecision.none();

            // Commute suppression window active: skip full evaluation
            if (!bypassGates && !s.pillShowing && s.lastNearestDist > PROX_CONFIG.farBandM
                    && s.suppressUntilMs > 0 && now < s.suppressUntilMs) {
                logD("[STD] commute suppress window active");
                return ProximityDecision.none();
            }

            s.lastEvalAtMs = now; s.lastLoc = cloneOf(loc);

            // Prefer nearest based on current location (fallback safe & stateless)
            List<DeliveryInfo> top = focusMgr.sortByDistance(loc, pending, 1);
            if (top.isEmpty()) return maybeHideOnNoData(s);
            DeliveryInfo nearest = top.get(0);
            // Guard: nearest may be null or contain invalid coordinates from upstream
            if (nearest == null) {
                logD("[STD] skip: nearest is null");
                return maybeHideOnNoData(s);
            }
            double nlat = nearest.getLatitude();
            double nlon = nearest.getLongitude();
            if (Double.isNaN(nlat) || Double.isNaN(nlon) || (nlat == 0d && nlon == 0d)) {
                logD("[STD] skip: nearest invalid coords lat=" + nlat + ", lon=" + nlon);
                return maybeHideOnNoData(s);
            }
            float[] dist = new float[1];
            Location.distanceBetween(loc.getLatitude(), loc.getLongitude(),
                    nearest.getLatitude(), nearest.getLongitude(), dist);
            float d = dist[0];

            DeliveryFocusManager.RegionConfig cfg = focusMgr.getRegionConfig();
            maybeExpireCompletionHint(s, d, now, cfg);
            ProximityDecision forced = forceShowAfterCompletionIfNeeded(s, nearest, d, now, cfg);
            if (forced != null) return forced;

            // Lock release if drifted beyond lockEnter + hysteresis
            if (s.lockedKey != null) {
                float unlockD = PROX_CONFIG.lockEnterM + PROX_CONFIG.lockExitHysteresisM;
                if (d > unlockD) {
                    logD("[STD] unlock by distance d=" + d + " > " + unlockD);
                    s.lockedKey = null;
                }
            }

            boolean cooled = elapsed(now, s.lastShowHideAtMs, PROX_CONFIG.showHideCooldownMs);

            if (!s.pillShowing) {
                if (d <= PROX_CONFIG.enterRadiusM && cooled) {
                    s.pillShowing = true; s.lastShowHideAtMs = now; s.lastNearest = nearest; s.lastNearestDist = d;
                    s.lockedKey = keyOf(nearest); // Top-3: 锁定
                    return new ProximityDecision(true, false, false, nearest, d, true, PROX_CONFIG.defaultBoostMs);
                }
                // hidden & far → remember distance to gate future evals
                s.lastNearest = nearest; s.lastNearestDist = d;

                // Hidden & far: update commute anchor and maybe start suppression
                if (s.lastNearestDist > PROX_CONFIG.farBandM) {
                    float speed = loc.hasSpeed() ? loc.getSpeed() : 0f;
                    if (speed >= 8.0f) { // ~28.8 km/h
                        if (s.anchorLat == null || s.anchorLon == null) {
                            s.anchorLat = loc.getLatitude();
                            s.anchorLon = loc.getLongitude();
                        } else {
                            float moved = distanceBetweenMeters(loc.getLatitude(), loc.getLongitude(), s.anchorLat, s.anchorLon);
                            if (moved < PROX_CONFIG.commuteSwitchM) {
                                s.suppressUntilMs = now + PROX_CONFIG.commuteSuppressMs;
                                logD("[STD] start commute suppress moved=" + moved);
                            } else {
                                // switched to a new area, reset anchor
                                s.anchorLat = loc.getLatitude();
                                s.anchorLon = loc.getLongitude();
                            }
                        }
                    }
                }
                return ProximityDecision.none();
            } else {
                if (d >= PROX_CONFIG.exitRadiusM && cooled) {
                    s.pillShowing = false; s.lastShowHideAtMs = now; s.lastNearest = nearest; s.lastNearestDist = d;
                    return new ProximityDecision(false, true, false, nearest, d, false, 0L);
                }
                // stay shown, do lightweight update only
                s.lastNearest = nearest; s.lastNearestDist = d;
                return new ProximityDecision(false, false, true, nearest, d, false, 0L);
            }
        }
    }

    // ==== ADVANCED strategy (RegionState + ETA gating) ====
    private final class AdvancedProximityStrategy implements ProximityStrategy {
        @NonNull @Override
        public ProximityDecision evaluate(@NonNull Location loc,
                                          @NonNull MovementState mv,
                                          @NonNull List<DeliveryInfo> pending,
                                          @NonNull InternalState s,
                                          @NonNull DeliveryFocusManager focusMgr) {
            logD("[ADV] evaluate start");
            long now = System.currentTimeMillis();

            DeliveryFocusManager.NearestResult nearest = focusMgr.findNearest(loc, pending);
            if (nearest.delivery == null) {
                return maybeHideOnNoData(s);
            }

            updateOdometer(s, loc);
            s.lastEvalAtMs = now;
            s.lastLoc = cloneOf(loc);
            s.lastNearest = nearest.delivery;
            s.lastNearestDist = nearest.distanceMeters;

            DeliveryFocusManager.RegionConfig cfg = focusMgr.getRegionConfig();
            maybeExpireCompletionHint(s, s.lastNearestDist, now, cfg);
            ProximityDecision forced = forceShowAfterCompletionIfNeeded(s, s.lastNearest, s.lastNearestDist, now, cfg);
            if (forced != null) return forced;

            RegionState derived = deriveRegionState(s.lastNearestDist, cfg);
            derived = overrideRegionStateAfterCompletion(derived, s, now);
            updateRegionState(s, derived);

            switch (s.regionState) {
                case IN_TRANSIT:
                    if (s.pillShowing) {
                        s.pillShowing = false;
                        s.lastShowHideAtMs = now;
                        return new ProximityDecision(false, true, false, s.lastNearest, s.lastNearestDist, false, 0L);
                    }
                    if ((s.odometerMeters - s.lastTransitAnchorMeters) < cfg.clusterHopMeters) {
                        logD("[ADV] still within transit hop window, skip");
                    }
                    return ProximityDecision.none();

                case APPROACH:
                    if (s.pillShowing) {
                        if (s.lastNearestDist > cfg.hideRadiusMeters) {
                            s.pillShowing = false;
                            s.lastShowHideAtMs = now;
                            return new ProximityDecision(false, true, false, s.lastNearest, s.lastNearestDist, false, 0L);
                        }
                        return new ProximityDecision(false, false, true, s.lastNearest, s.lastNearestDist, false, 0L);
                    }
                    return ProximityDecision.none();

                case INSIDE:
                    boolean completionBoost = now < s.completionHintUntilMs;
                    if (!s.pillShowing) {
                        if (s.lastNearestDist <= cfg.showRadiusMeters) {
                            boolean slowMovement = isSlowMovement(mv);
                            boolean etaPass = completionBoost || etaGatePass(loc, s.lastNearest, mv, cfg);
                            if (completionBoost || slowMovement || etaPass) {
                                s.pillShowing = true;
                                s.lastShowHideAtMs = now;
                                s.lockedKey = keyOf(s.lastNearest);
                                return new ProximityDecision(true, false, false, s.lastNearest, s.lastNearestDist, true, PROX_CONFIG.defaultBoostMs);
                            }
                        }
                        return ProximityDecision.none();
                    } else {
                        if (s.lastNearestDist <= cfg.hideRadiusMeters) {
                            return new ProximityDecision(false, false, true, s.lastNearest, s.lastNearestDist, false, 0L);
                        }
                        s.pillShowing = false;
                        s.lastShowHideAtMs = now;
                        return new ProximityDecision(false, true, false, s.lastNearest, s.lastNearestDist, false, 0L);
                    }
            }

            return ProximityDecision.none();
        }
    }

    private void updateOdometer(@NonNull InternalState s, @NonNull Location loc) {
        if (s.lastLoc == null) {
            s.lastLoc = cloneOf(loc);
            return;
        }
        try {
            float delta = s.lastLoc.distanceTo(loc);
            if (!Float.isNaN(delta) && delta > 0f && delta < 5_000f) {
                s.odometerMeters += delta;
            }
        } catch (Throwable ignore) {}
    }

    private RegionState deriveRegionState(float distanceMeters, @NonNull DeliveryFocusManager.RegionConfig cfg) {
        if (Float.isNaN(distanceMeters) || distanceMeters == Float.MAX_VALUE) {
            return RegionState.IN_TRANSIT;
        }
        if (distanceMeters > cfg.clusterRadiusMeters) return RegionState.IN_TRANSIT;
        if (distanceMeters > cfg.showRadiusMeters) return RegionState.APPROACH;
        return RegionState.INSIDE;
    }

    private RegionState overrideRegionStateAfterCompletion(@NonNull RegionState derived,
                                                           @NonNull InternalState s,
                                                           long nowMs) {
        if (derived == RegionState.IN_TRANSIT && nowMs < s.completionHintUntilMs) {
            return RegionState.INSIDE;
        }
        return derived;
    }

    private void updateRegionState(@NonNull InternalState s, @NonNull RegionState newState) {
        if (s.regionState == newState) return;
        s.regionState = newState;
        if (newState == RegionState.IN_TRANSIT) {
            s.lastTransitAnchorMeters = s.odometerMeters;
            s.completionHintUntilMs = 0L;
        }
    }

    private boolean isSlowMovement(@NonNull MovementState mv) {
        switch (mv) {
            case STATIONARY:
            case WALKING:
            case SLOW_DRIVING:
                return true;
            default:
                return false;
        }
    }

    private boolean etaGatePass(@NonNull Location loc,
                                @NonNull DeliveryInfo target,
                                @NonNull MovementState mv,
                                @NonNull DeliveryFocusManager.RegionConfig cfg) {
        long etaSec = -1L;
        if (etaProvider != null) {
            try { etaSec = etaProvider.computeEtaSeconds(loc, target); } catch (Throwable ignore) { etaSec = -1L; }
        }
        if (etaSec < 0L && simpleEtaEstimator != null) {
            etaSec = simpleEtaEstimator.estimateSeconds(loc, target, mv);
        }
        if (etaSec < 0L || cfg.etaGateSeconds <= 0L) {
            return true;
        }
        return etaSec <= cfg.etaGateSeconds;
    }

    private static boolean elapsed(long now, long last, long winMs) { return (now - last) >= winMs; }

    private static boolean movedEnough(@Nullable Location a, @NonNull Location b, float minMeters) {
        if (a == null) return true;
        float[] r = new float[1];
        Location.distanceBetween(a.getLatitude(), a.getLongitude(), b.getLatitude(), b.getLongitude(), r);
        return r[0] >= minMeters;
    }

    private static Location cloneOf(@NonNull Location src) { return new Location(src); }

    private static float distanceBetweenMeters(double lat1, double lon1, double lat2, double lon2){
        float[] r = new float[1];
        Location.distanceBetween(lat1, lon1, lat2, lon2, r);
        return r[0];
    }

    private static void maybeExpireCompletionHint(@NonNull InternalState s,
                                                  float distanceMeters,
                                                  long nowMs,
                                                  @Nullable DeliveryFocusManager.RegionConfig cfg) {
        if (s.completionHintUntilMs <= 0L) return;
        if (nowMs >= s.completionHintUntilMs) {
            s.completionHintUntilMs = 0L;
            s.completionAnchorKey = null;
            s.forceShowAfterCompletion = false;
            return;
        }
        if (cfg == null) return;
        float clusterLimit = Math.max(cfg.clusterRadiusMeters, cfg.hideRadiusMeters);
        if (Float.isNaN(distanceMeters)) return;
        if (distanceMeters > clusterLimit) {
            s.completionHintUntilMs = 0L;
            s.completionAnchorKey = null;
            s.forceShowAfterCompletion = false;
        }
    }

    @Nullable
    private static ProximityDecision forceShowAfterCompletionIfNeeded(@NonNull InternalState s,
                                                                      @Nullable DeliveryInfo candidate,
                                                                      float distanceMeters,
                                                                      long nowMs,
                                                                      @Nullable DeliveryFocusManager.RegionConfig cfg) {
        if (!s.forceShowAfterCompletion) return null;
        if (candidate == null) {
            s.forceShowAfterCompletion = false;
            return null;
        }
        if (nowMs >= s.completionHintUntilMs) {
            s.forceShowAfterCompletion = false;
            return null;
        }
        float maxRadius = Float.MAX_VALUE;
        if (cfg != null) {
            maxRadius = Math.max(cfg.hideRadiusMeters, cfg.clusterRadiusMeters);
        }
        float safeDistance = Float.isNaN(distanceMeters) ? Float.MAX_VALUE : distanceMeters;
        if (safeDistance > maxRadius) {
            s.forceShowAfterCompletion = false;
            s.completionHintUntilMs = 0L;
            s.completionAnchorKey = null;
            return null;
        }
        s.forceShowAfterCompletion = false;
        s.pillShowing = true;
        s.lastShowHideAtMs = nowMs;
        s.lastNearest = candidate;
        s.lastNearestDist = safeDistance;
        s.lockedKey = keyOf(candidate);
        return new ProximityDecision(true, false, false, candidate, safeDistance, true, PROX_CONFIG.defaultBoostMs);
    }

    @Nullable
    private static String keyOf(@Nullable DeliveryInfo d){
        if (d == null) return null;
        Long id = d.getOrderId();
        if (id != null) return "ID:" + id;
        String sn = d.getOrderSn();
        if (sn != null) return "SN:" + sn;
        return "@" + System.identityHashCode(d);
    }

    private static ProximityDecision maybeHideOnNoData(@NonNull InternalState s) {
        if (s.pillShowing && elapsed(System.currentTimeMillis(), s.lastShowHideAtMs, PROX_CONFIG.showHideCooldownMs)) {
            s.pillShowing = false; s.lastShowHideAtMs = System.currentTimeMillis();
            s.lastNearest = null; s.lastNearestDist = Float.MAX_VALUE;
            s.forceShowAfterCompletion = false;
            s.completionHintUntilMs = 0L;
            s.completionAnchorKey = null;
            return new ProximityDecision(false, true, false, null, Float.MAX_VALUE, false, 0L);
        }
        return ProximityDecision.none();
    }

}
