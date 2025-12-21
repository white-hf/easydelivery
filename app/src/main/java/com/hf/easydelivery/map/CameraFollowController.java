package com.hf.easydelivery.map;

import android.animation.ValueAnimator;
import android.graphics.Point;
import android.location.Location;
import android.os.SystemClock;
import android.view.animation.DecelerateInterpolator;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.MapView;
import com.google.android.gms.maps.model.CameraPosition;
import com.google.android.gms.maps.model.LatLng;
import com.google.maps.android.SphericalUtil;
import com.hf.courierservice.apihelper.FileLog;
import com.google.android.gms.maps.CameraUpdate;
import com.google.android.gms.maps.model.LatLngBounds;
import com.hf.easydelivery.dao.DeliveryInfo;
import java.util.List;
import com.hf.easydelivery.map.config.ProfileManager;
import com.hf.easydelivery.core.SmartLocationManager;

/**
 * 智能相机跟随控制器
 * <p>
 * 核心职责 / Key responsibilities:
 * </p>
 * <ul>
 * <li>根据 {@link SmartLocationManager} 提供的定位/速度状态，生成驾驶视角的相机动画（蓝点居于屏幕下方、平滑
 * zoom/bearing）。</li>
 * <li>接管“自动跟随”开关，处理手势打断、恢复操作以及“当前位置”按钮强制回中心的场景。</li>
 * <li>监测蓝点相对视窗的偏移，触发
 * {@link SmartLocationManager#requestBoostIfEdgeRisk(float, float)}
 * 以提升定位频率。</li>
 * <li>输出日志记录每次跟随指令，便于定位“蓝点走出屏幕”或跟随失效的问题。</li>
 * </ul>
 * <p>
 * 关键策略 / Implementation highlights:
 * </p>
 * <ul>
 * <li>Driving camera placement：利用地图投影把驾驶点下移到 75% screen height，结合 tilt=45°、最小
 * zoom=16。</li>
 * <li>Update throttling：通过上次更新时间、距离与航向变化阈值控制相机刷新，不响应高频抖动。</li>
 * <li>User interaction：手势开始时暂停自动跟随并记录 `autoFollowDisabled`，按钮点击/超时后再调用
 * `follow(...)` 强制回归。</li>
 * <li>Edge boost：估算蓝点和目标视窗点的像素距离，换算成米后请求临时高频定位，确保高速行驶时地图平滑。</li>
 * </ul>
 */
public class CameraFollowController {

    private static final String TAG = "CameraFollowController";

    private void logD(String msg) {
        // silence verbose logs
    }

    private static final float DRIVING_MIN_ZOOM = 16.5f;
    private static final float DRIVING_TARGET_SCREEN_FRACTION_Y = 0.65f;
    private static final float NAVIGATION_TARGET_SCREEN_FRACTION_Y = 0.86f;
    private static final float DEFAULT_TILT = 45f;
    private static final float DRIVING_TILT_DEGREES = 55f;
    private static final float NAVIGATION_TILT_DEGREES = 60f;
    private static final float BROWSE_TILT_DEGREES = 35f;
    private static final float SPEED_ZOOM_NEAR = 18.8f;
    private static final float SPEED_ZOOM_CITY = 17.5f;
    private static final float SPEED_ZOOM_SUBURB = 16.5f;
    private static final float SPEED_ZOOM_HIGHWAY = 15.5f;
    private static final float EDGE_FORCE_METERS = 25f;
    private static final float SMART_ZOOM_NEAR_METERS = 800f;
    private static final float LIST_VIEW_NEAR_METERS = 800f;
    private static final int LIST_VIEW_MIN_ITEMS = 1;

    // Adaptive animation + lookAhead smoothing
    private static final long CAMERA_ANIM_MIN_MS = 120L;
    private static final long CAMERA_ANIM_MAX_MS = 350L;
    private static final long CAMERA_ANIM_STALE_FAST_MS = 160L;

    private static final long LOOKAHEAD_BASE_DT_MS = 800L;
    private static final double LOOKAHEAD_MAX_DELTA_PER_BASE = 10d; // 10m per 800ms baseline
    private static final double LOOKAHEAD_MAX_DELTA_MIN = 10d;
    private static final double LOOKAHEAD_MAX_DELTA_MAX = 30d;

    // If camera hasn't moved for too long, force an update to avoid "stuck"
    // feeling.
    private static final long DRIVING_FORCE_UPDATE_STALE_MS = 1500L;

    private long suppressFollowUntilMs = 0L;

    // ==== Auto-Follow Strategy (Basic/Standard/Advanced) ====
    public enum FollowProfile {
        BASIC, STANDARD, ADVANCED
    }

    // ==== Follow Config (Phase 1 externalization) ====
    public static final class FollowConfig {
        public long stdIntervalMs = 800L; // STANDARD base interval
        public float stdDistM = 5f; // STANDARD base distance
        public float stdHeadingDeg = 10f; // STANDARD base heading delta

        // Walking sensitivity: when not driving, require only a small move to refresh
        public float stdDistWalkingM = 1.5f;

        public long basicIntervalMs = 1000L; // BASIC base interval
        public float basicDistM = 10f; // BASIC base distance
        public float basicHeadingDeg = 15f; // BASIC base heading delta
    }

    private static final FollowConfig FOLLOW_CONFIG = new FollowConfig();

    @NonNull
    public static FollowConfig getFollowConfig() {
        return FOLLOW_CONFIG;
    }

    public static void applyFollowConfig(@NonNull FollowConfig cfg) {
        if (cfg == null)
            return;
        FOLLOW_CONFIG.stdIntervalMs = cfg.stdIntervalMs;
        FOLLOW_CONFIG.stdDistM = cfg.stdDistM;
        FOLLOW_CONFIG.stdHeadingDeg = cfg.stdHeadingDeg;
        FOLLOW_CONFIG.basicIntervalMs = cfg.basicIntervalMs;
        FOLLOW_CONFIG.basicDistM = cfg.basicDistM;
        FOLLOW_CONFIG.basicHeadingDeg = cfg.basicHeadingDeg;
    }

    private interface FollowStrategy {
        boolean allowCameraMove(boolean force,
                boolean autoFollowEnabled,
                boolean isUserInteracting,
                boolean driving,
                boolean insideZoneLowSpeed);

        boolean shouldUpdateCamera(@NonNull Location location,
                @NonNull SmartLocationManager.MovementState state,
                long lastUpdateUptime,
                @Nullable LatLng lastTarget,
                float lastBearing,
                boolean hasCentered);
    }

    // STANDARD strategy: mirrors current behavior, now uses FOLLOW_CONFIG
    private final FollowStrategy standardFollow = new FollowStrategy() {
        @Override
        public boolean allowCameraMove(boolean force,
                boolean autoFollowEnabled,
                boolean isUserInteracting,
                boolean driving,
                boolean insideZoneLowSpeed) {
            if (force)
                return true;
            if (!autoFollowEnabled)
                return false;
            return !isUserInteracting;
        }

        @Override
        public boolean shouldUpdateCamera(@NonNull Location location,
                @NonNull SmartLocationManager.MovementState state,
                long lastUpdateUptime,
                @Nullable LatLng lastTarget,
                float lastBearing,
                boolean hasCentered) {
            long now = SystemClock.uptimeMillis();
            long dt = (lastUpdateUptime <= 0L) ? Long.MAX_VALUE : (now - lastUpdateUptime);
            // If camera hasn't moved for too long, force an update to avoid "stuck"
            // feeling.
            boolean staleForce = dt > DRIVING_FORCE_UPDATE_STALE_MS;
            boolean timeOk = staleForce || (dt > FOLLOW_CONFIG.stdIntervalMs);

            float distance = 0f;
            if (lastTarget != null) {
                Location.distanceBetween(lastTarget.latitude, lastTarget.longitude,
                        location.getLatitude(), location.getLongitude(), distanceResults);
                distance = distanceResults[0];
            }
            boolean distanceOk = distance > FOLLOW_CONFIG.stdDistM;
            if (!isDrivingState(state)) {
                distanceOk = distance > FOLLOW_CONFIG.stdDistWalkingM;
            }

            boolean headingOk = false;
            if (location.hasBearing() && !Float.isNaN(lastBearing)) {
                float delta = Math.abs(location.getBearing() - lastBearing);
                if (delta > 180f)
                    delta = 360f - delta;
                headingOk = delta > FOLLOW_CONFIG.stdHeadingDeg;
            }
            if (!isDrivingState(state) && distanceOk) {
                headingOk = true;
            }

            FileLog.getInstance().debug(TAG,
                    "[STD] gates timeOk=" + timeOk + ", distOk=" + distanceOk + ", headOk=" + headingOk);

            boolean driving = isDrivingState(state);
            if (!timeOk && !distanceOk && !headingOk)
                return false;
            if (driving) {
                if (staleForce)
                    return true;
                return timeOk && (distanceOk || headingOk);
            }
            return !hasCentered || (timeOk && distanceOk);
        }
    };

    // BASIC strategy: more conservative thresholds (battery friendly), now uses
    // FOLLOW_CONFIG
    private final FollowStrategy basicFollow = new FollowStrategy() {
        @Override
        public boolean allowCameraMove(boolean force,
                boolean autoFollowEnabled,
                boolean isUserInteracting,
                boolean driving,
                boolean insideZoneLowSpeed) {
            if (force)
                return true;
            if (!autoFollowEnabled)
                return false;
            return !isUserInteracting;
        }

        @Override
        public boolean shouldUpdateCamera(@NonNull Location location,
                @NonNull SmartLocationManager.MovementState state,
                long lastUpdateUptime,
                @Nullable LatLng lastTarget,
                float lastBearing,
                boolean hasCentered) {
            long now = SystemClock.uptimeMillis();
            long dt = (lastUpdateUptime <= 0L) ? Long.MAX_VALUE : (now - lastUpdateUptime);
            boolean staleForce = dt > DRIVING_FORCE_UPDATE_STALE_MS;
            boolean timeOk = staleForce || (dt > FOLLOW_CONFIG.basicIntervalMs);

            float distance = 0f;
            if (lastTarget != null) {
                Location.distanceBetween(lastTarget.latitude, lastTarget.longitude,
                        location.getLatitude(), location.getLongitude(), distanceResults);
                distance = distanceResults[0];
            }
            boolean distanceOk = distance > FOLLOW_CONFIG.basicDistM;

            boolean headingOk = false;
            if (location.hasBearing() && !Float.isNaN(lastBearing)) {
                float delta = Math.abs(location.getBearing() - lastBearing);
                if (delta > 180f)
                    delta = 360f - delta;
                headingOk = delta > FOLLOW_CONFIG.basicHeadingDeg;
            }
            if (!isDrivingState(state) && distanceOk) {
                headingOk = true;
            }

            FileLog.getInstance().debug(TAG,
                    "[BASIC] gates timeOk=" + timeOk + ", distOk=" + distanceOk + ", headOk=" + headingOk);

            boolean driving = isDrivingState(state);
            if (!timeOk && !distanceOk && !headingOk)
                return false;
            if (driving) {
                if (staleForce)
                    return true;
                return timeOk && (distanceOk || headingOk);
            }
            return !hasCentered || (timeOk && distanceOk);
        }
    };

    // Registry + default profile
    private FollowProfile followProfile = FollowProfile.STANDARD; // default keeps behavior unchanged
    private FollowStrategy followStrategy = standardFollow;

    public void setFollowProfile(@NonNull FollowProfile profile) {
        logD("setFollowProfile(" + profile + ")");
        this.followProfile = profile;
        switch (profile) {
            case BASIC:
                this.followStrategy = basicFollow;
                break;
            case STANDARD:
            case ADVANCED: // Phase 1: alias to STANDARD
            default:
                this.followStrategy = standardFollow;
                break;
        }
        logD("followStrategy applied = " + this.followStrategy.getClass().getSimpleName());
    }

    /**
     * Bridge for external ProfileManager:
     * POWERSAVER → BASIC (battery friendly)
     * ADVANCED → STANDARD (current default behavior; can be mapped to ADVANCED in
     * future)
     */
    public void applyAppProfile(@NonNull ProfileManager.AppProfile appProfile) {
        FollowProfile mapped = (appProfile == ProfileManager.AppProfile.POWERSAVER)
                ? FollowProfile.BASIC
                : FollowProfile.STANDARD;
        logD("applyAppProfile(" + appProfile + ") -> followProfile=" + mapped);
        setFollowProfile(mapped);
    }

    private final GoogleMap googleMap;
    private final MapView mapView;
    private final FileLog logger = FileLog.getInstance();

    @Nullable
    private SmartLocationManager smartLocationManager;

    @Nullable
    private ValueAnimator cameraAnimator;

    // Latest context quality/sensor fields (populated in updateCamera)
    private float currentAccuracyMeters = Float.NaN;
    private long currentLocationAgeMs = 0L;
    private CameraUpdateContext.LocationSource currentLocationSource = CameraUpdateContext.LocationSource.UNKNOWN;
    private float currentSpeedMps = Float.NaN;
    private float currentBearingDeg = Float.NaN;
    private float currentHeadingDeg = Float.NaN;
    // 是否曾经进入过“真实驾驶”模式（速度超过阈值），用于控制居中策略
    private boolean hasEverEnteredDrivingMode = false;
    private GateSnapshot lastGateSnapshot;
    // Paused-by-user state (map gestures or light intervention)
    private boolean pausedByUser = false;
    private long lastCameraUpdateUptime = 0L;
    private long lastCameraAnimStartUptime = 0L;
    @Nullable
    private LatLng lastCameraTargetLatLng = null;
    @Nullable
    private LatLng lastLocationLatLng = null;
    private float lastCameraBearing = Float.NaN;
    private float userPreferredBearing = Float.NaN;
    private boolean capturingUserBearing = false;
    private final float[] distanceResults = new float[1];
    private boolean navigationModeEnabled = false;
    // Smooth lookAhead transition to avoid camera jitter
    private double lastLookAheadMeters = 35d;
    // Smooth speed→zoom transitions
    private float lastSpeedZoom = Float.NaN;
    private int lastSpeedBand = -1; // 0:near,1:city,2:suburb,3:highway
    // Dedup keys for camera updates
    private String lastCameraKey = null;
    private long lastCameraKeyTimeMs = 0L;

    // ✅ 修复进入驾驶时zoom突变：跟踪驾驶模式开始时间
    private long drivingModeStartTime = 0;

    // --- Phase 3: jitter gating ---
    private static final float MIN_BEARING_DELTA_DEG = 2f; // skip tiny bearing changes
    private static final float MIN_PIXEL_DELTA = 2f; // skip tiny pixel drifts

    public CameraFollowController(@NonNull GoogleMap googleMap, @NonNull MapView mapView) {
        this.googleMap = googleMap;
        this.mapView = mapView;
    }

    public void setSmartLocationManager(@Nullable SmartLocationManager manager) {
        this.smartLocationManager = manager;
    }

    public void setNavigationModeEnabled(boolean enabled) {
        navigationModeEnabled = enabled;
        if (enabled) {
            pausedByUser = false;
            userPreferredBearing = Float.NaN;
        }
    }

    /**
     * ✅ 修复进入驾驶时zoom突变：记录驾驶模式开始时间
     */
    public void setDrivingModeStartTime(long timestamp) {
        this.drivingModeStartTime = timestamp;
        logD("setDrivingModeStartTime: " + timestamp);
    }

    public void beginBearingCapture() {
        capturingUserBearing = true;
    }

    public void endBearingCapture() {
        capturingUserBearing = false;
    }

    public boolean isCapturingUserBearing() {
        return capturingUserBearing;
    }

    public void onCameraMove(@NonNull CameraPosition position) {
        if (capturingUserBearing) {
            userPreferredBearing = normalizeBearing(position.bearing);
        }
    }

    public void onCameraIdle() {
        capturingUserBearing = false;
    }

    // 兼容旧接口：返回是否曾进入驾驶模式（对应旧 hasCenteredOnUser 语义）
    public boolean hasCenteredOnUser() {
        return hasEverEnteredDrivingMode;
    }

    public void resetHasCenteredOnUser() {
        hasEverEnteredDrivingMode = false;
    }

    /** Called when user performs a map gesture (pan/zoom/rotate). */
    public void onUserGesture() {
        pausedByUser = true;
        logD("onUserGesture(): auto-follow paused by user");
    }

    /**
     * Called when user taps on map or performs a light intervention that should
     * pause auto-follow.
     */
    public void onMapClickIntervene() {
        pausedByUser = true;
        logD("onMapClickIntervene(): auto-follow paused by user");
    }

    /** Whether auto-follow is currently paused due to user interaction. */
    public boolean isPausedByUser() {
        return pausedByUser;
    }

    /** Resume auto-follow and recenter the camera. */
    public void resumeFollow(@NonNull Location location,
            @NonNull SmartLocationManager.MovementState state,
            @Nullable Float preferredZoom) {
        float zoom = (preferredZoom != null) ? preferredZoom : DRIVING_MIN_ZOOM;
        pausedByUser = false;
        hasEverEnteredDrivingMode = false;
        logD("resumeFollow(): clearing paused flag and forcing recenter, zoom=" + zoom
                + ", reset hasEverEnteredDrivingMode=false");
        // Force a recenter with allowAutoFollow=true and interacting=false
        follow(location, state, true, true, false, false, zoom);
    }

    /** Expose current follow profile for logging/diagnostics. */
    @NonNull
    public FollowProfile getFollowProfile() {
        return followProfile;
    }

    public void cancelAnimations() {
        if (cameraAnimator != null) {
            cameraAnimator.cancel();
            cameraAnimator = null;
        }
    }

    public void resetRuntimeState() {
        cancelAnimations();
        lastCameraTargetLatLng = null;
        lastCameraBearing = Float.NaN;
        lastCameraUpdateUptime = 0L;
        lastCameraAnimStartUptime = 0L;
        hasEverEnteredDrivingMode = false;
        pausedByUser = false;
    }

    /**
     * Updates the camera based on the provided context.
     * Fully restored old follow() behavior + keeps the new architecture.
     */
    public boolean updateCamera(@NonNull CameraUpdateContext context) {
        // noisy: logD("updateCamera() enter: " + context);

        // Cache quality fields for downstream decisions
        currentAccuracyMeters = context.accuracyMeters;
        currentLocationAgeMs = context.locationAgeMs;
        currentLocationSource = context.locationSource != null ? context.locationSource
                : CameraUpdateContext.LocationSource.UNKNOWN;
        currentSpeedMps = context.speedMps;
        currentBearingDeg = context.bearingDeg;
        currentHeadingDeg = context.headingDeg;

        // --- Reset driving mode if idle too long ---
        if (lastCameraUpdateUptime > 0) {
            long idleMs = SystemClock.uptimeMillis() - lastCameraUpdateUptime;
            if (idleMs > 15_000L) {
                hasEverEnteredDrivingMode = false;
                logD("updateCamera(): idle " + idleMs + "ms -> reset hasEverEnteredDrivingMode=false");
            }
        }

        if (SystemClock.uptimeMillis() < suppressFollowUntilMs) {
            logD("updateCamera() blocked: suppressed until " + suppressFollowUntilMs);
            return false;
        }

        boolean navMode = context.isNavigationMode;

        // ============================================================
        // [PATCH #1] —— 恢复旧 follow()：导航模式必须覆盖用户交互
        // ============================================================
        boolean isUserInteracting = context.isUserInteracting;
        boolean isAutoFollowPaused = context.isAutoFollowPaused;

        if (navMode) {
            isUserInteracting = false; // 旧行为：导航模式下强制取消交互
            isAutoFollowPaused = false; // 旧行为：导航模式强制启用 auto follow
            hasEverEnteredDrivingMode = false;
        }

        if (pausedByUser && !navMode) {
            logD("updateCamera() blocked: pausedByUser=true");
            return false;
        }

        // ============================================================
        // 1. List View Strategy
        // ============================================================
        boolean drivingLikely = context.isDrivingLikely();
        boolean stationaryOrWalking = !drivingLikely;
        boolean allowSmartZoom = !drivingLikely
                || (context.nearestPackageDistanceMeters > 0
                        && context.nearestPackageDistanceMeters <= SMART_ZOOM_NEAR_METERS);
        boolean smartZoomApplicable = allowSmartZoom &&
                context.nearestPackageDistanceMeters > 0;

        int nearbyCount = context.nearbyDeliveries == null ? 0 : context.nearbyDeliveries.size();
        boolean allowListView = stationaryOrWalking
                || (drivingLikely
                        && context.nearestPackageDistanceMeters > 0
                        && context.nearestPackageDistanceMeters <= LIST_VIEW_NEAR_METERS
                        && nearbyCount >= LIST_VIEW_MIN_ITEMS);

        if (allowListView
                && !smartZoomApplicable
                && context.nearbyDeliveries != null
                && !context.nearbyDeliveries.isEmpty()) {

            CameraUpdate listUpdate = buildListViewCamera(context);
            if (listUpdate != null) {
                logD("updateCamera: applying list view update");
                googleMap.animateCamera(listUpdate);
                return true;
            }
        }

        // ============================================================
        // 2. Preferred Zoom
        // ============================================================
        float preferredZoom = computePreferredZoom(context);

        // ============================================================
        // 3. Allow Auto Follow?
        // ============================================================
        boolean allowAutoFollow = !isAutoFollowPaused || navMode;

        // ============================================================
        // 4. Force-Follow（恢复旧 follow() 完整逻辑）
        // ============================================================
        boolean shouldForce = false;

        // --- Navigation mode always forces ---
        if (navMode) {
            shouldForce = true;
        }

        // --- Driving always forces (old behavior) ---
        if (!shouldForce && drivingLikely && allowAutoFollow) {
            resetHasCenteredOnUser();
            shouldForce = true;
            // noisy
        }

        // --- First time -> force ---
        if (!shouldForce && !hasCenteredOnUser() && allowAutoFollow) {
            shouldForce = true;
            // noisy
        }

        // ============================================================
        // [PATCH #2] —— 恢复 edge-force（不能放在后面）
        // ============================================================
        boolean lowSpeedInside = context.isLowSpeedInsideDeliveryZone() || navMode;
        if (!shouldForce && lowSpeedInside && allowAutoFollow) {
            LatLng driverLL = new LatLng(context.location.getLatitude(), context.location.getLongitude());
            LatLng edgeRef = (context.isDriving() && lastCameraTargetLatLng != null) ? lastCameraTargetLatLng
                    : driverLL;
            float offsetMeters = estimateEdgeOffsetMeters(edgeRef);
            if (offsetMeters > EDGE_FORCE_METERS) {
                shouldForce = true;
                // noisy
            }
        }

        // ============================================================
        // 5. Determine AllowCameraMove（必须根据 force 先算）
        // ============================================================
        boolean driving = drivingLikely || navMode;

        GateSnapshot gateSnapshot = new GateSnapshot();
        gateSnapshot.movementState = context.movementState;
        gateSnapshot.navMode = navMode;
        gateSnapshot.driving = driving;
        gateSnapshot.allowAutoFollow = allowAutoFollow;
        gateSnapshot.shouldForce = shouldForce;
        gateSnapshot.isUserInteracting = isUserInteracting;
        gateSnapshot.isAutoFollowPaused = isAutoFollowPaused;
        gateSnapshot.hasEverEnteredDrivingMode = hasEverEnteredDrivingMode;
        logGateChanges(gateSnapshot);

        boolean allowCameraMove = followStrategy.allowCameraMove(
                shouldForce,
                allowAutoFollow,
                isUserInteracting,
                driving,
                lowSpeedInside);

        if (!allowCameraMove) {
            logD("updateCamera(): allowCameraMove=false");
            return false;
        }

        // ============================================================
        // 6. shouldUpdateCamera（保持旧 follow() 的判断）
        // ============================================================
        boolean canUpdateCamera = shouldForce ||
                followStrategy.shouldUpdateCamera(
                        context.location,
                        context.movementState,
                        lastCameraUpdateUptime,
                        lastCameraTargetLatLng,
                        lastCameraBearing,
                        hasEverEnteredDrivingMode);

        if (lowSpeedInside) {
            canUpdateCamera = true; // old behavior
        }

        if (!canUpdateCamera) {
            logD("updateCamera(): canUpdateCamera=false");
            return false;
        }

        // ============================================================
        // 7. Build Target Camera（完全按原逻辑分支）
        // ============================================================
        CameraPosition targetCamera;

        if (navMode) {
            targetCamera = buildDrivingCamera(context.location, preferredZoom);
        } else if (lowSpeedInside) {
            targetCamera = buildCenteredCamera(context.location, preferredZoom);
        } else if (driving) {
            targetCamera = buildDrivingCamera(context.location, preferredZoom);
        } else if (!hasEverEnteredDrivingMode || shouldForce) {
            targetCamera = buildCenteredCamera(context.location, preferredZoom);
        } else if (!isUserInteracting &&
                followStrategy.shouldUpdateCamera(context.location,
                        context.movementState,
                        lastCameraUpdateUptime,
                        lastCameraTargetLatLng,
                        lastCameraBearing,
                        hasEverEnteredDrivingMode)) {
            targetCamera = buildCenteredCamera(context.location, preferredZoom);
        } else {
            return false;
        }

        if (targetCamera == null)
            return false;

        if (isDuplicateCameraRequest(targetCamera, context)) {
            logD("updateCamera(): duplicate request skipped");
            return false;
        }

        // ============================================================
        // 8. Micro-update skip（保留 + 静止去抖）
        // ============================================================
        if (lastCameraTargetLatLng != null) {
            float px = 0f;
            try {
                Point pA = googleMap.getProjection().toScreenLocation(lastCameraTargetLatLng);
                Point pB = googleMap.getProjection().toScreenLocation(targetCamera.target);
                px = (float) Math.hypot(pA.x - pB.x, pA.y - pB.y);
            } catch (Exception ignore) {
            }

            float bearingDelta = Math.abs(targetCamera.bearing -
                    (Float.isNaN(lastCameraBearing) ? targetCamera.bearing : lastCameraBearing));
            if (bearingDelta > 180)
                bearingDelta = 360 - bearingDelta;

            float zoomDelta = Math.abs(targetCamera.zoom - googleMap.getCameraPosition().zoom);

            // 静止/步行额外去抖：1s内且移动很小则跳过
            long now = SystemClock.uptimeMillis();
            boolean stationaryOrWalk = context.movementState == SmartLocationManager.MovementState.STATIONARY
                    || context.movementState == SmartLocationManager.MovementState.WALKING;
            boolean shortInterval = now - lastCameraUpdateUptime < 1000L;

            if (px < MIN_PIXEL_DELTA && bearingDelta < MIN_BEARING_DELTA_DEG && zoomDelta < 0.01f && !shouldForce) {
                logD("updateCamera(): micro-update skipped");
                return false;
            }
            // 仅静止/步行时做额外去抖，驾驶态不屏蔽更新
            if (!drivingLikely && stationaryOrWalk && shortInterval
                    && px < MIN_PIXEL_DELTA && zoomDelta < 0.02f && !shouldForce) {
                logD("updateCamera(): stationary debounce skipped");
                return false;
            }
        }

        // ============================================================
        // 9. Apply Camera
        // ============================================================
        animateCameraTo(targetCamera);

        // --- Update state ---
        lastCameraUpdateUptime = SystemClock.uptimeMillis();
        lastCameraTargetLatLng = targetCamera.target;
        lastCameraBearing = targetCamera.bearing;
        lastLocationLatLng = new LatLng(context.location.getLatitude(), context.location.getLongitude());
        rememberCameraKey(targetCamera, context);

        // --- Enter driving mode (>10km/h) ---
        if (drivingLikely
                && context.location.hasSpeed()
                && context.location.getSpeed() * 3.6f >= 10f) {
            hasEverEnteredDrivingMode = true;
            logD("updateCamera(): entered driving mode");
        }

        // --- Edge Boost for location manager ---
        if (smartLocationManager != null && driving) {
            LatLng driverLL2 = new LatLng(context.location.getLatitude(), context.location.getLongitude());
            LatLng edgeRef2 = (context.isDriving() && lastCameraTargetLatLng != null) ? lastCameraTargetLatLng
                    : driverLL2;
            float offset = estimateEdgeOffsetMeters(edgeRef2);
            smartLocationManager.requestBoostIfEdgeRisk(offset, context.location.getSpeed());
        }

        return true;
    }

    private boolean isDuplicateCameraRequest(@NonNull CameraPosition targetCamera,
            @NonNull CameraUpdateContext context) {
        long now = SystemClock.uptimeMillis();
        String key = buildCameraKey(targetCamera, context);
        if (key != null && key.equals(lastCameraKey) && (now - lastCameraKeyTimeMs) < 300L) {
            return true;
        }
        return false;
    }

    private void rememberCameraKey(@NonNull CameraPosition cam, @NonNull CameraUpdateContext ctx) {
        lastCameraKey = buildCameraKey(cam, ctx);
        lastCameraKeyTimeMs = SystemClock.uptimeMillis();
    }

    private String buildCameraKey(@NonNull CameraPosition cam, @NonNull CameraUpdateContext ctx) {
        try {
            double lat = Math.round(cam.target.latitude * 1_000_000d) / 1_000_000d;
            double lng = Math.round(cam.target.longitude * 1_000_000d) / 1_000_000d;
            float zoom = Math.round(cam.zoom * 100f) / 100f;
            float bearing = Math.round(cam.bearing);
            float tilt = Math.round(cam.tilt);
            boolean driving = ctx.isDriving();
            return lat + "," + lng + "|z=" + zoom + "|b=" + bearing + "|t=" + tilt + "|d=" + driving;
        } catch (Throwable t) {
            return null;
        }
    }

    private CameraUpdate buildListViewCamera(CameraUpdateContext context) {
        LatLngBounds.Builder builder = new LatLngBounds.Builder();
        int count = 0;
        for (DeliveryInfo info : context.nearbyDeliveries) {
            if (info == null)
                continue;
            builder.include(new LatLng(info.getLatitude(), info.getLongitude()));
            count++;
            if (count >= 5)
                break;
        }
        if (count > 0) {
            try {
                builder.include(new LatLng(context.location.getLatitude(), context.location.getLongitude()));
                int paddingPx = (int) (48 * mapView.getResources().getDisplayMetrics().density);
                logD("buildListViewCamera: fitting " + count + " items");
                return CameraUpdateFactory.newLatLngBounds(builder.build(), paddingPx);
            } catch (Exception e) {
                logD("buildListViewCamera failed: " + e.getMessage());
            }
        }
        return null;
    }

    private float computePreferredZoom(CameraUpdateContext context) {
        if (context.nearestPackageDistanceMeters <= 0) {
            return DRIVING_MIN_ZOOM;
        }

        boolean allowSmartZoom = !context.isDrivingLikely()
                || (context.nearestPackageDistanceMeters > 0
                        && context.nearestPackageDistanceMeters <= SMART_ZOOM_NEAR_METERS);
        if (allowSmartZoom) {
            // ✅ Fix: Only use "Smart Zoom" (fit-to-package) when close to the target.
            // When far away (e.g. 20km commute), stay in cruise zoom even when stationary
            // to prevent annoying zoom jumps (e.g. from 14.9 to 16.5) when stopping at
            // lights.
            if (context.nearestPackageDistanceMeters > 1500f) {
                return DRIVING_MIN_ZOOM;
            }

            float smartZoom = DeliveryFocusManager.computeSmartZoom(
                    context.nearestPackageDistanceMeters,
                    context.location.getLatitude(),
                    context.visibleMapHeightPx);

            if (smartZoom < 18.9f) {
                float result = Math.max(14.9f, smartZoom);
                return result;
            }
        }

        float result = computeSpeedZoom(context.location, DRIVING_MIN_ZOOM);
        return result;
    }

    public float computePreferredZoomForManualCenter(@NonNull Location location,
            @NonNull SmartLocationManager.MovementState state,
            float nearestPackageDistanceMeters,
            int visibleMapHeightPx) {
        if (nearestPackageDistanceMeters <= 0) {
            return DRIVING_MIN_ZOOM;
        }

        boolean stationaryOrWalking = state == SmartLocationManager.MovementState.STATIONARY
                || state == SmartLocationManager.MovementState.WALKING;
        if (stationaryOrWalking) {
            if (nearestPackageDistanceMeters > 1500f) {
                return DRIVING_MIN_ZOOM;
            }

            float smartZoom = DeliveryFocusManager.computeSmartZoom(
                    nearestPackageDistanceMeters,
                    location.getLatitude(),
                    visibleMapHeightPx);

            if (smartZoom < 18.9f) {
                return Math.max(14.9f, smartZoom);
            }
        }

        return computeSpeedZoom(location, DRIVING_MIN_ZOOM);
    }

    private boolean shouldAllowAutoFollow(CameraUpdateContext context) {
        if (context.isNavigationMode) {
            return true;
        }
        return !context.isAutoFollowPaused && !context.isManualCenterHold;
    }

    private boolean shouldForceFollow(CameraUpdateContext context, boolean allowAutoFollow) {
        if (context.isNavigationMode) {
            return true;
        }

        if (context.isDriving() && allowAutoFollow) {
            resetHasCenteredOnUser();
            return true;
        }

        if (!hasCenteredOnUser() && allowAutoFollow) {
            logD("shouldForceFollow: first time -> force=true");
            return true;
        }

        return false;
    }

    public boolean follow(@NonNull Location location,
            @NonNull SmartLocationManager.MovementState state,
            boolean force,
            boolean autoFollowEnabled,
            boolean isUserInteracting,
            boolean insideDeliveryZone,
            float preferredFollowZoom) {

        logD("follow() enter force=" + force + ", auto=" + autoFollowEnabled + ", interacting=" + isUserInteracting
                + ", prefZoom=" + preferredFollowZoom
                + ", state=" + state
                + ", speedKmh=" + (location.hasSpeed() ? location.getSpeed() * 3.6f : 0f));

        // 长时间未更新相机，重置驾驶标记，避免长时间停车后不再回中
        if (lastCameraUpdateUptime > 0) {
            long idleMs = SystemClock.uptimeMillis() - lastCameraUpdateUptime;
            if (idleMs > 15_000L) {
                hasEverEnteredDrivingMode = false;
                logD("follow(): idle " + idleMs + "ms -> reset hasEverEnteredDrivingMode=false");
            }
        }
        if (SystemClock.uptimeMillis() < suppressFollowUntilMs)
            return true;

        boolean navMode = navigationModeEnabled;
        if (navMode) {
            autoFollowEnabled = true;
            isUserInteracting = false;
            force = true;
            hasEverEnteredDrivingMode = false; // 导航模式下保持强制跟随
        }

        // If user has paused auto-follow, ignore unless forced or navigation mode is on
        if (pausedByUser && !force && !navMode) {
            logD("follow() blocked: pausedByUser=true and not forced");
            return false;
        }

        boolean driving = navMode || isDrivingState(state);
        boolean lowSpeedInside = navMode || (insideDeliveryZone && (state == SmartLocationManager.MovementState.WALKING
                || state == SmartLocationManager.MovementState.SLOW_DRIVING
                || state == SmartLocationManager.MovementState.STATIONARY));
        boolean poorQuality = (!Float.isNaN(currentAccuracyMeters) && currentAccuracyMeters > 50f)
                || currentLocationAgeMs > 3_000L;
        if (lowSpeedInside && autoFollowEnabled && !force) {
            LatLng driverLL = new LatLng(location.getLatitude(), location.getLongitude());
            LatLng edgeRef = (driving && lastCameraTargetLatLng != null) ? lastCameraTargetLatLng : driverLL;
            float offsetMeters = poorQuality ? 0f : estimateEdgeOffsetMeters(edgeRef);
            if (offsetMeters > EDGE_FORCE_METERS) {
                logD("follow() forcing recenter due to edge offset=" + offsetMeters);
                force = true;
            }
        }
        boolean allowCameraMove = followStrategy.allowCameraMove(force, autoFollowEnabled, isUserInteracting, driving,
                lowSpeedInside);
        boolean canUpdateCamera = force || followStrategy.shouldUpdateCamera(location, state, lastCameraUpdateUptime,
                lastCameraTargetLatLng, lastCameraBearing, hasEverEnteredDrivingMode);

        if (lowSpeedInside) {
            canUpdateCamera = true;
        }

        if (!allowCameraMove) {
            logD("follow() blocked: allowCameraMove=false");
            return false;
        }
        if (!canUpdateCamera && !force) {
            logD("follow() blocked: canUpdateCamera=false & not forced");
            return false;
        }
        // if (!navMode && !driving && !lowSpeedInside && !force) { logD("follow()
        // blocked: not driving/inside and not forced"); return false; }

        CameraPosition targetCamera;
        if (navMode) {
            targetCamera = buildDrivingCamera(location, preferredFollowZoom);
        } else if (lowSpeedInside) {
            targetCamera = buildCenteredCamera(location, preferredFollowZoom);
        } else if (driving) {
            targetCamera = buildDrivingCamera(location, preferredFollowZoom);
        } else if (!hasEverEnteredDrivingMode || force) {
            targetCamera = buildCenteredCamera(location, preferredFollowZoom);
        } else if (!isUserInteracting && followStrategy.shouldUpdateCamera(location, state, lastCameraUpdateUptime,
                lastCameraTargetLatLng, lastCameraBearing, hasEverEnteredDrivingMode)) {
            targetCamera = buildCenteredCamera(location, preferredFollowZoom);
        } else {
            return false;
        }

        logD("follow() targetCamera computed zoom=" + targetCamera.zoom + ", bearing=" + targetCamera.bearing);

        if (targetCamera == null)
            return false;

        logD("followLocation driving=" + driving + " force=" + force
                + " zoom->" + targetCamera.zoom + " bearing->" + targetCamera.bearing
                + " autoFollowEnabled=" + autoFollowEnabled + " interacting=" + isUserInteracting);

        // Skip micro-updates: small pixel drift & tiny bearing delta & near-identical
        // zoom
        if (lastCameraTargetLatLng != null) {
            float px = 0f;
            try {
                Point pA = googleMap.getProjection().toScreenLocation(lastCameraTargetLatLng);
                Point pB = googleMap.getProjection().toScreenLocation(targetCamera.target);
                px = (float) Math.hypot(pA.x - pB.x, pA.y - pB.y);
            } catch (Exception ignore) {
            }
            float bearingDelta = Math.abs(
                    targetCamera.bearing - (Float.isNaN(lastCameraBearing) ? targetCamera.bearing : lastCameraBearing));
            if (bearingDelta > 180f)
                bearingDelta = 360f - bearingDelta;
            float zoomDelta = Math.abs(targetCamera.zoom - googleMap.getCameraPosition().zoom);
            if (px < MIN_PIXEL_DELTA && bearingDelta < MIN_BEARING_DELTA_DEG && zoomDelta < 0.01f && !force) {
                logD("follow() micro update skipped: px=" + px + ", bearingΔ=" + bearingDelta + ", zoomΔ=" + zoomDelta);
                return false;
            }
        }
        animateCameraTo(targetCamera);
        lastCameraUpdateUptime = SystemClock.uptimeMillis();
        lastCameraTargetLatLng = targetCamera.target;
        lastCameraBearing = targetCamera.bearing;
        // 只有真实驾驶且速度超过 10km/h 时才认为进入“驾驶模式”
        if (isDrivingState(state) && location.hasSpeed() && location.getSpeed() * 3.6f >= 10f) {
            hasEverEnteredDrivingMode = true;
            logD("follow(): entered driving mode (speed>=10km/h)");
        }
        lastLocationLatLng = new LatLng(location.getLatitude(), location.getLongitude());
        if (smartLocationManager != null && driving) {
            if (!poorQuality) {
                LatLng driverLL2 = new LatLng(location.getLatitude(), location.getLongitude());
                LatLng edgeRef2 = (driving && lastCameraTargetLatLng != null) ? lastCameraTargetLatLng : driverLL2;
                float offset = estimateEdgeOffsetMeters(edgeRef2);
                logD("edgeBoost offsetM=" + offset + ", speed=" + location.getSpeed());
                smartLocationManager.requestBoostIfEdgeRisk(offset, location.getSpeed());
            }
        }
        return true;
    }

    public void centerOn(@NonNull Location location,
            @NonNull SmartLocationManager.MovementState state,
            float preferredFollowZoom) {
        hasEverEnteredDrivingMode = false;
        follow(location, state, true, true, false, false, preferredFollowZoom);
    }

    @Nullable
    private CameraPosition buildDrivingCamera(@NonNull Location location, float preferredFollowZoom) {
        LatLng driverLatLng = new LatLng(location.getLatitude(), location.getLongitude());
        CameraPosition current = googleMap.getCameraPosition();
        float bearing = resolveBearing(location, current);
        double lookAheadMeters = computeLookAheadMeters(location);
        LatLng targetLatLng;
        try {
            targetLatLng = SphericalUtil.computeOffset(driverLatLng, lookAheadMeters, bearing);
        } catch (Exception ignore) {
            targetLatLng = driverLatLng;
        }

        float zoom;
        if (navigationModeEnabled && preferredFollowZoom > 10f) {
            zoom = preferredFollowZoom;
            logD("buildDrivingCamera: NAV mode using preferredFollowZoom=" + zoom);
        } else {
            zoom = computeSpeedZoom(location, preferredFollowZoom);
        }
        if (location.getSpeed() < 1.5f) {
            zoom = Math.min(zoom, 18.3f);
        }
        float tilt = navigationModeEnabled ? NAVIGATION_TILT_DEGREES : DRIVING_TILT_DEGREES;
        if (current.tilt > tilt) {
            tilt = current.tilt; // avoid abrupt tilt drops mid animation
        }

        logD("buildDrivingCamera nav=" + navigationModeEnabled
                + " lookAhead=" + lookAheadMeters
                + "m target=" + targetLatLng.latitude + "," + targetLatLng.longitude
                + " bearing=" + bearing + " tilt=" + tilt + " zoom=" + zoom);

        return new CameraPosition.Builder(current)
                .target(targetLatLng)
                .zoom(zoom)
                .tilt(tilt)
                .bearing(bearing)
                .build();
    }

    @Nullable
    private CameraPosition buildCenteredCamera(@NonNull Location location) {
        return buildCenteredCamera(location, -1f);
    }

    @Nullable
    private CameraPosition buildCenteredCamera(@NonNull Location location, float preferredZoom) {
        CameraPosition current = googleMap.getCameraPosition();
        LatLng target = new LatLng(location.getLatitude(), location.getLongitude());

        // Use preferredZoom if provided (calculated by MapInnerFragment's smart zoom
        // logic)
        // This ensures packages are visible after delivery completion
        float zoom;
        if (preferredZoom > 0) {
            zoom = preferredZoom;
            logD("buildCenteredCamera: using preferredZoom=" + zoom);
        } else {
            zoom = current.zoom < 15f ? 15f : current.zoom;
            logD("buildCenteredCamera: keeping current zoom=" + zoom);
        }

        float bearing = (!navigationModeEnabled && !Float.isNaN(userPreferredBearing))
                ? userPreferredBearing
                : current.bearing;
        float tilt = Math.max(current.tilt, BROWSE_TILT_DEGREES);

        return new CameraPosition.Builder(current)
                .target(target)
                .zoom(zoom)
                .tilt(tilt)
                .bearing(bearing)
                .build();
    }

    private float computeSpeedZoom(@NonNull Location location, float preferredFollowZoom) {
        // ✅ 修复进入驾驶时zoom突变：驾驶开始2秒内保持上次zoom
        long now = System.currentTimeMillis();
        if (drivingModeStartTime > 0) {
            long timeSinceEnteringDriving = now - drivingModeStartTime;
            if (timeSinceEnteringDriving < 2000) {
                // 使用上次的zoom，避免speedZoom在第一帧造成突变
                if (!Float.isNaN(lastSpeedZoom)) {
                    logD("computeSpeedZoom: delaying speedZoom, using lastSpeedZoom=" + lastSpeedZoom);
                    return lastSpeedZoom;
                }
            }
        }

        float kmh = location.hasSpeed() ? (location.getSpeed() * 3.6f) : 0f;
        // 滞回分档，减少在阈值附近来回切换
        if (lastSpeedBand < 0)
            lastSpeedBand = 0;
        switch (lastSpeedBand) {
            case 0: // near -> city
                if (kmh > 22f)
                    lastSpeedBand = 1;
                break;
            case 1: // city <-> near/suburb
                if (kmh < 18f)
                    lastSpeedBand = 0;
                else if (kmh > 55f)
                    lastSpeedBand = 2;
                break;
            case 2: // suburb <-> city/highway
                if (kmh < 45f)
                    lastSpeedBand = 1;
                else if (kmh > 85f)
                    lastSpeedBand = 3;
                break;
            case 3: // highway -> suburb
                if (kmh < 75f)
                    lastSpeedBand = 2;
                break;
        }

        float baseZoom;
        switch (lastSpeedBand) {
            case 0:
                baseZoom = SPEED_ZOOM_NEAR;
                break;
            case 1:
                baseZoom = SPEED_ZOOM_CITY;
                break;
            case 2:
                baseZoom = SPEED_ZOOM_SUBURB;
                break;
            default:
                baseZoom = SPEED_ZOOM_HIGHWAY;
                break;
        }

        float targetZoom = Math.max(baseZoom, Math.max(preferredFollowZoom, DRIVING_MIN_ZOOM));
        if (Math.abs(targetZoom - lastSpeedZoom) < 0.25f) {
            targetZoom = lastSpeedZoom; // 保持当前 zoom
        }

        // 低通：单次调整不超过 0.2，避免上下跳变
        if (Float.isNaN(lastSpeedZoom)) {
            lastSpeedZoom = targetZoom;
        } else {
            float delta = targetZoom - lastSpeedZoom;
            if (delta > 0.2f)
                delta = 0.2f;
            if (delta < -0.2f)
                delta = -0.2f;
            lastSpeedZoom += delta;
        }
        return lastSpeedZoom;
    }

    private double computeLookAheadMeters(@NonNull Location location) {
        float speedMps = !Float.isNaN(currentSpeedMps) ? currentSpeedMps
                : (location.hasSpeed() ? location.getSpeed() : 0f);
        float kmh = speedMps * 3.6f;
        double targetLookAhead;
        if (kmh < 10f)
            targetLookAhead = 35d;
        else if (kmh < 30f)
            targetLookAhead = 55d;
        else if (kmh < 60f)
            targetLookAhead = 85d;
        else if (kmh < 90f)
            targetLookAhead = 110d;
        else
            targetLookAhead = 140d;

        // Smooth transition (dt-adaptive): allow larger change when updates are sparse
        long nowUptime = SystemClock.uptimeMillis();
        long dtUptime = (lastCameraAnimStartUptime <= 0L) ? LOOKAHEAD_BASE_DT_MS
                : (nowUptime - lastCameraAnimStartUptime);
        if (dtUptime < 0L)
            dtUptime = LOOKAHEAD_BASE_DT_MS;

        double maxDelta = LOOKAHEAD_MAX_DELTA_PER_BASE * (dtUptime / (double) LOOKAHEAD_BASE_DT_MS);
        if (maxDelta < LOOKAHEAD_MAX_DELTA_MIN)
            maxDelta = LOOKAHEAD_MAX_DELTA_MIN;
        if (maxDelta > LOOKAHEAD_MAX_DELTA_MAX)
            maxDelta = LOOKAHEAD_MAX_DELTA_MAX;

        double delta = targetLookAhead - lastLookAheadMeters;
        if (Math.abs(delta) > maxDelta) {
            delta = Math.signum(delta) * maxDelta;
        }
        lastLookAheadMeters += delta;
        logD("computeLookAhead: speed=" + kmh + "km/h, target=" + targetLookAhead + "m, actual=" + lastLookAheadMeters
                + "m");
        return lastLookAheadMeters;
    }

    private void animateCameraTo(@NonNull CameraPosition targetCamera) {
        CameraPosition start = googleMap.getCameraPosition();
        long nowUptime = SystemClock.uptimeMillis();
        long dt = (lastCameraAnimStartUptime <= 0L) ? LOOKAHEAD_BASE_DT_MS : (nowUptime - lastCameraAnimStartUptime);
        if (dt < 0L)
            dt = LOOKAHEAD_BASE_DT_MS;
        lastCameraAnimStartUptime = nowUptime;

        // Adaptive duration: if updates are sparse, animate faster to avoid "trailing"
        // feeling.
        long duration;
        if (dt > 1200L) {
            duration = CAMERA_ANIM_STALE_FAST_MS;
        } else {
            long clamped = Math.max(300L, Math.min(1200L, dt));
            double ratio = (clamped - 300d) / (1200d - 300d); // 0..1
            duration = (long) Math.round(CAMERA_ANIM_MAX_MS - ratio * (CAMERA_ANIM_MAX_MS - 180L));
        }
        duration = Math.max(CAMERA_ANIM_MIN_MS, Math.min(CAMERA_ANIM_MAX_MS, duration));

        cameraAnimator = ValueAnimator.ofFloat(0f, 1f);
        cameraAnimator.setDuration(duration);
        cameraAnimator.setInterpolator(new DecelerateInterpolator());
        cameraAnimator.addUpdateListener(anim -> {
            float t = (float) anim.getAnimatedValue();
            CameraPosition interpolated = interpolateCamera(start, targetCamera, t);
            googleMap.moveCamera(CameraUpdateFactory.newCameraPosition(interpolated));
        });
        // silence verbose logs
        cameraAnimator.start();
    }

    private CameraPosition interpolateCamera(@NonNull CameraPosition start,
            @NonNull CameraPosition end,
            float t) {
        t = Math.max(0f, Math.min(1f, t));
        double startLat = start.target.latitude;
        double startLng = start.target.longitude;
        double endLat = end.target.latitude;
        double endLng = end.target.longitude;
        double deltaLng = endLng - startLng;
        if (Math.abs(deltaLng) > 180) {
            deltaLng -= Math.signum(deltaLng) * 360;
        }

        double lat = startLat + (endLat - startLat) * t;
        double lng = startLng + deltaLng * t;

        float zoom = start.zoom + (end.zoom - start.zoom) * t;
        float tilt = start.tilt + (end.tilt - start.tilt) * t;

        float bearingStart = start.bearing;
        float bearingEnd = end.bearing;
        float deltaBearing = bearingEnd - bearingStart;
        if (Math.abs(deltaBearing) > 180f) {
            deltaBearing -= Math.signum(deltaBearing) * 360f;
        }
        float bearing = bearingStart + deltaBearing * t;
        if (bearing < 0f)
            bearing += 360f;

        return new CameraPosition(new LatLng(lat, lng), zoom, tilt, bearing);
    }

    private float estimateEdgeOffsetMeters(@NonNull LatLng latLng) {
        if (mapView.getWidth() == 0 || mapView.getHeight() == 0) {
            return 0f;
        }
        try {
            Point point = googleMap.getProjection().toScreenLocation(latLng);
            int width = mapView.getWidth();
            int height = mapView.getHeight();
            float fraction = navigationModeEnabled ? NAVIGATION_TARGET_SCREEN_FRACTION_Y
                    : DRIVING_TARGET_SCREEN_FRACTION_Y;
            Point targetPoint = new Point(width / 2, (int) (height * fraction));
            float dx = point.x - targetPoint.x;
            float dy = point.y - targetPoint.y;
            float pixelDistance = (float) Math.hypot(dx, dy);
            if (pixelDistance <= 0f)
                return 0f;

            Point refA = new Point(targetPoint.x, targetPoint.y);
            Point refB = new Point(targetPoint.x + 100, targetPoint.y);
            LatLng llA = googleMap.getProjection().fromScreenLocation(refA);
            LatLng llB = googleMap.getProjection().fromScreenLocation(refB);
            Location.distanceBetween(llA.latitude, llA.longitude, llB.latitude, llB.longitude, distanceResults);
            float metersPerPx = (distanceResults[0] <= 0f) ? 0f : distanceResults[0] / 100f;
            return metersPerPx * pixelDistance;
        } catch (Exception ignore) {
            return 0f;
        }
    }

    private void logGateChanges(@NonNull GateSnapshot current) {
        boolean changed = false;
        StringBuilder sb = new StringBuilder();
        if (lastGateSnapshot == null) {
            changed = true;
            sb.append("gates init: state=").append(current.movementState)
                    .append(", navMode=").append(current.navMode)
                    .append(", driving=").append(current.driving)
                    .append(", allowAutoFollow=").append(current.allowAutoFollow)
                    .append(", shouldForce=").append(current.shouldForce)
                    .append(", interacting=").append(current.isUserInteracting)
                    .append(", autoFollowPaused=").append(current.isAutoFollowPaused)
                    .append(", hasEverEnteredDrivingMode=").append(current.hasEverEnteredDrivingMode);
        } else {
            if (lastGateSnapshot.movementState != current.movementState) {
                changed = true;
                sb.append("state ").append(lastGateSnapshot.movementState).append("->").append(current.movementState);
            }
            if (lastGateSnapshot.navMode != current.navMode) {
                appendChange(sb, "navMode", lastGateSnapshot.navMode, current.navMode);
                changed = true;
            }
            if (lastGateSnapshot.driving != current.driving) {
                appendChange(sb, "driving", lastGateSnapshot.driving, current.driving);
                changed = true;
            }
            if (lastGateSnapshot.allowAutoFollow != current.allowAutoFollow) {
                appendChange(sb, "allowAutoFollow", lastGateSnapshot.allowAutoFollow, current.allowAutoFollow);
                changed = true;
            }
            if (lastGateSnapshot.shouldForce != current.shouldForce) {
                appendChange(sb, "shouldForce", lastGateSnapshot.shouldForce, current.shouldForce);
                changed = true;
            }
            if (lastGateSnapshot.isUserInteracting != current.isUserInteracting) {
                appendChange(sb, "interacting", lastGateSnapshot.isUserInteracting, current.isUserInteracting);
                changed = true;
            }
            if (lastGateSnapshot.isAutoFollowPaused != current.isAutoFollowPaused) {
                appendChange(sb, "autoFollowPaused", lastGateSnapshot.isAutoFollowPaused, current.isAutoFollowPaused);
                changed = true;
            }
            if (lastGateSnapshot.hasEverEnteredDrivingMode != current.hasEverEnteredDrivingMode) {
                appendChange(sb, "hasEverEnteredDrivingMode", lastGateSnapshot.hasEverEnteredDrivingMode,
                        current.hasEverEnteredDrivingMode);
                changed = true;
            }
        }
        if (changed && sb.length() > 0) {
            logD("gates change: " + sb);
        }
        lastGateSnapshot = current;
    }

    private void appendChange(StringBuilder sb, String label, boolean oldVal, boolean newVal) {
        if (sb.length() > 0) {
            sb.append(", ");
        }
        sb.append(label).append(" ").append(oldVal).append("->").append(newVal);
    }

    private static final class GateSnapshot {
        private SmartLocationManager.MovementState movementState;
        private boolean navMode;
        private boolean driving;
        private boolean allowAutoFollow;
        private boolean shouldForce;
        private boolean isUserInteracting;
        private boolean isAutoFollowPaused;
        private boolean hasEverEnteredDrivingMode;
    }

    private boolean isDrivingState(@NonNull SmartLocationManager.MovementState state) {
        return state == SmartLocationManager.MovementState.SLOW_DRIVING
                || state == SmartLocationManager.MovementState.NORMAL_DRIVING;
    }

    private float normalizeBearing(float bearing) {
        float normalized = bearing % 360f;
        if (normalized < 0f)
            normalized += 360f;
        return normalized;
    }

    private float resolveBearing(@NonNull Location location, @NonNull CameraPosition current) {
        if (!navigationModeEnabled && !Float.isNaN(userPreferredBearing)) {
            return userPreferredBearing;
        }
        float speed = !Float.isNaN(currentSpeedMps) ? currentSpeedMps
                : (location.hasSpeed() ? location.getSpeed() : 0f);
        if (!Float.isNaN(currentBearingDeg) && speed > 0.5f) {
            return normalizeBearing(currentBearingDeg);
        }
        if (location.hasBearing() && speed > 0.5f) {
            return normalizeBearing(location.getBearing());
        }
        if (!Float.isNaN(currentHeadingDeg)) {
            return normalizeBearing(currentHeadingDeg);
        }
        if (lastLocationLatLng != null) {
            double heading = SphericalUtil.computeHeading(lastLocationLatLng,
                    new LatLng(location.getLatitude(), location.getLongitude()));
            if (!Double.isNaN(heading)) {
                return normalizeBearing((float) heading);
            }
        }
        if (smartLocationManager != null) {
            float heading = smartLocationManager.getCurrentHeading();
            if (!Float.isNaN(heading)) {
                return normalizeBearing(heading);
            }
        }
        if (!Float.isNaN(lastCameraBearing)) {
            return normalizeBearing(lastCameraBearing);
        }
        return normalizeBearing(current.bearing);
    }

    /** Phase 3 convenience: apply unified focus decision to camera. */
    public void applyDecision(@NonNull Location loc,
            @NonNull SmartLocationManager.MovementState mv,
            boolean interacting,
            boolean allowAutoFollow,
            @NonNull DeliveryFocusManager.FocusDecision decision) {
        boolean shouldForce = (decision.shouldForceCameraFollow() && allowAutoFollow)
                || (!hasCenteredOnUser() && allowAutoFollow)
                || (decision.isFocusChanged() && allowAutoFollow);
        // noisy removed
        follow(loc, mv, shouldForce, allowAutoFollow || decision.shouldForceCameraFollow(), interacting, false,
                decision.getPreferredZoom());
    }

    public void resumeFollowNow(@NonNull Location loc,
            @NonNull SmartLocationManager.MovementState state,
            float preferredZoom,
            boolean alignToCenter,
            boolean insideDeliveryZone) {
        try {
            googleMap.stopAnimation();
        } catch (Throwable ignore) {
        }
        boolean useCentered = alignToCenter
                || !isDrivingState(state)
                || (insideDeliveryZone && state != SmartLocationManager.MovementState.NORMAL_DRIVING);
        CameraPosition targetCamera = useCentered
                ? buildCenteredCamera(loc)
                : buildDrivingCamera(loc, preferredZoom);
        if (targetCamera == null) {
            LatLng target = new LatLng(loc.getLatitude(), loc.getLongitude());
            try {
                googleMap.moveCamera(CameraUpdateFactory.newLatLngZoom(target, preferredZoom));
            } catch (Throwable t) {
                try {
                    googleMap.moveCamera(CameraUpdateFactory.newLatLng(target));
                } catch (Throwable ignore) {
                }
            }
        } else {
            try {
                googleMap.moveCamera(CameraUpdateFactory.newCameraPosition(targetCamera));
            } catch (Throwable t) {
                LatLng fallback = new LatLng(loc.getLatitude(), loc.getLongitude());
                try {
                    googleMap.moveCamera(CameraUpdateFactory.newLatLngZoom(fallback, targetCamera.zoom));
                } catch (Throwable ignore) {
                }
            }
        }
        hasEverEnteredDrivingMode = false;

        // 改成永不抑制 + 强制重置时间戳
        suppressFollowUntilMs = 0L;
        lastCameraUpdateUptime = 0L; // 让下一帧一定能过 timeOk 判定
    }

}
