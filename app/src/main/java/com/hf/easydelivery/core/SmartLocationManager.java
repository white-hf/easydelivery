package com.hf.easydelivery.core;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.location.Location;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;

import androidx.core.app.ActivityCompat;

import androidx.annotation.Nullable;
import androidx.annotation.NonNull;

import com.google.android.gms.location.DetectedActivity;
import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationCallback;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationResult;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.Priority;
import com.google.android.gms.tasks.CancellationTokenSource;
import com.hf.courierservice.apihelper.FileLog;
import com.hf.easydelivery.core.policy.LocationPolicy;
import com.hf.easydelivery.core.policy.LocationPolicyContext;
import com.hf.easydelivery.core.policy.LocationPolicyContextProvider;
import com.hf.easydelivery.core.policy.LocationRequestParams;
import com.hf.easydelivery.core.policy.PolicyContextStateProvider;
import com.hf.easydelivery.core.policy.RealtimeLocationPolicy;
import com.hf.easydelivery.core.engine.RequestScheduler;
import com.hf.easydelivery.core.strategy.BoostReason;
import com.hf.easydelivery.core.strategy.StrategyConfig;
import com.hf.easydelivery.core.strategy.StrategyManager;
import com.hf.easydelivery.core.quality.QualityConfig;
import com.hf.easydelivery.core.quality.FixQualityClassifier;
import com.hf.easydelivery.core.quality.WeakSignalMonitor;
import com.hf.easydelivery.core.source.FusedLocationSource;
import com.hf.easydelivery.core.state.MovementStateMachine;
import com.hf.easydelivery.core.pipeline.LocationPipeline;
import com.hf.easydelivery.core.pipeline.HeadingProvider;
import com.hf.easydelivery.core.pipeline.ProcessingContext;
import com.hf.easydelivery.core.pipeline.SmoothingProcessor;
import com.hf.easydelivery.core.pipeline.PredictionProcessor;
import com.hf.easydelivery.core.pipeline.StaleFilterProcessor;
import com.hf.easydelivery.core.pipeline.QualityGateProcessor;
import com.hf.easydelivery.core.pipeline.FallbackBuilderProcessor;
import com.hf.easydelivery.core.pipeline.ElapsedTimeStampProcessor;
import com.hf.easydelivery.core.pipeline.FallbackProcessor;
import com.hf.easydelivery.core.pipeline.DefaultPredictionProvider;
import com.hf.easydelivery.core.pipeline.PredictionProvider;
import com.hf.easydelivery.core.pipeline.DefaultFallbackProvider;
import com.hf.easydelivery.core.pipeline.FallbackStateProvider;
import com.hf.easydelivery.core.pipeline.DefaultSmoothingFactorProvider;
import com.hf.easydelivery.core.pipeline.SmoothingFactorProvider;
import com.hf.easydelivery.core.dispatch.LocationDispatcher;
import com.hf.easydelivery.core.dispatch.DispatchGate;
import com.hf.easydelivery.core.facade.LocationControls;
import com.hf.easydelivery.core.facade.ForegroundLocationConsumer;
import com.hf.easydelivery.core.facade.LocationFacade;
import com.hf.easydelivery.core.facade.LocationSnapshot;
import com.hf.easydelivery.core.burst.BurstConfig;
import com.hf.easydelivery.core.burst.BurstController;
import com.hf.easydelivery.core.activity.ActivityTransitionMonitor;
import com.hf.easydelivery.core.sensors.HeadingSensorController;
import com.hf.easydelivery.core.observer.LocationEventBus;
import com.hf.easydelivery.core.observer.FileLogLocationObserver;
import com.hf.easydelivery.core.observer.TelemetryLocationObserver;

import java.util.List;

/**
 * Central manager for location collection, quality evaluation, and delivery-friendly
 * dispatching. It coordinates policy, burst/boost behavior, smoothing/prediction,
 * and listener notifications while balancing responsiveness and power usage.
 */
public class SmartLocationManager implements LocationFacade, LocationControls, ForegroundLocationConsumer {
    private static SmartLocationManager instance;
    private static final String TAG = "SmartLocationManager";

    private Context context;
    private FusedLocationProviderClient fusedLocationClient;
    private LocationCallback locationCallback;
    private Location lastLocation;
    private Location lastSmoothedLocation;
    private Location lastPredictedLocation;
    // --- Split caches to avoid low-accuracy pollution / UI stalls ---
    // lastRawLocation: always the most recent fix (even if poor)
    // lastLocation: last GOOD fix used for speed/state logic (kept for backward
    // compatibility)
    private Location lastRawLocation;
    private Location lastDispatchedLocation;
    private long lastGoodLocationUptimeMs = 0L;
    private float speed;
    private long lastUpdateTime;
    private final MovementStateMachine movementStateMachine = new MovementStateMachine();
    private final FixQualityClassifier fixQualityClassifier = new FixQualityClassifier();
    private final PredictionProvider predictionProvider;
    private final SmoothingFactorProvider smoothingFactorProvider;
    private final LocationPipeline locationPipeline;
    private final FallbackProcessor fallbackProcessor;
    private final BurstController burstController;
    private final DispatchGate dispatchGate = new DispatchGate();
    private final java.util.Set<com.hf.easydelivery.core.facade.LocationUpdateListener> listeners = new java.util.concurrent.CopyOnWriteArraySet<>();
    private Handler handler;
    private boolean inBurstMode = false;
    private ActivityTransitionMonitor activityTransitionMonitor;
    private final WeakSignalMonitor weakSignalMonitor = new WeakSignalMonitor(
            QualityConfig.getWeakSignalRequiredHits(),
            QualityConfig.getWeakSignalDurationMs());
    // legacy smoothing constant removed; smoothing factor comes from provider

    private static BoostReason mapBoostReason(@Nullable String reason) {
        BoostReason parsed = BoostReason.fromKey(reason);
        if (parsed == BoostReason.EDGE_RISK) {
            return BoostReason.EDGE_FALLBACK;
        }
        if (parsed == BoostReason.INSIDE) {
            return BoostReason.PROXIMITY;
        }
        return parsed;
    }

    // === Adaptive boost (temporary high-frequency updates) ===
    private long lastLowPriorityBoostMs = 0L;
    private int displacementWakeHits = 0;
    private long lastDisplacementWakeMs = 0L;
    private int edgeRiskHits = 0;
    private long lastEdgeRiskMs = 0L;
    private int jumpRiskHits = 0;
    private long lastJumpRiskMs = 0L;

    private long lastMovingTimeMs = 0L;
    private long lastGoodFixTime = 0L;
    private boolean lastDeliveringIdle = false;
    private long lastDrivingUptimeMs = 0L;
    private long lastInVehicleUptimeMs = 0L;
    private float lastDisplacementMeters = 0f;
    private boolean movingFlag = false;
    private long movingHoldUntilMs = 0L;
    private long lastUiDispatchUptimeMs = 0L;
    private Location lastUiLocation = null;
    private boolean uiFollowActive = false;
    private HeadingSensorController headingSensorController;

    private RequestScheduler requestScheduler;
    private final LocationDispatcher locationDispatcher;
    private long currentMinDispatchIntervalMs = StrategyConfig.getMinDispatchIntervalMovingMs();

    private LocationPolicy locationPolicy = new RealtimeLocationPolicy();
    private final LocationPolicyContextProvider policyContextProvider;
    private StrategyManager strategyManager;
    private final LocationEventBus eventBus = new LocationEventBus();

    private boolean singleUpdateInFlight = false;
    private long lastSingleFixUptimeMs = 0L;
    private long lastEmergencyBoostUptimeMs = 0L;
    private long singleFixBackoffMs = BurstConfig.getSingleFixBackoffBaseMs();
    private volatile boolean foregroundTrackingActive = false;
    private volatile long foregroundServiceHeartbeatUptimeMs = 0L;
    private volatile long lastForegroundLocationUptimeMs = 0L;
    private static final long FG_TRACKING_HEARTBEAT_TIMEOUT_MS = 20_000L;

    public interface WeakSignalListener extends LocationUpdateListener {
        void onWeakSignal();
    }

    public enum MovementState {
        STATIONARY,
        WALKING,
        SLOW_DRIVING,
        NORMAL_DRIVING
    }

    public static synchronized SmartLocationManager getInstance(Context context) {
        if (instance != null)
            return instance;
        else {
            if (context == null)
                return null;
            instance = new SmartLocationManager(context.getApplicationContext());
        }
        return instance;
    }

    @Nullable
    public FusedLocationProviderClient getFusedLocationClient() {
        return fusedLocationClient;
    }

    public interface LocationUpdateListener extends com.hf.easydelivery.core.facade.LocationUpdateListener {
        void onLocationUpdate(Location location, MovementState state);
    }

    /**
     *
     */
    public void requestBoost(long durationMs) {
        requestBoostInternal(durationMs, false, BoostReason.UNKNOWN.getKey());
    }

    public void requestBoostForce(long durationMs) {
        requestBoostInternal(durationMs, true, BoostReason.FORCE.getKey());
    }

    public void requestBoost(long durationMs, @NonNull String reason) {
        requestBoostInternal(durationMs, false, reason);
    }

    public void requestBoostForce(long durationMs, @NonNull String reason) {
        requestBoostInternal(durationMs, true, reason);
    }

    private void requestBoostInternal(long durationMs, boolean force, @NonNull String reason) {
        if (strategyManager != null) {
            String reasonKey = reason == null ? BoostReason.UNKNOWN.getKey() : reason;
            eventBus.emitBoostRequested(reasonKey, force);
            // logged via FileLogLocationObserver
            strategyManager.suggestBoost(mapBoostReason(reasonKey), durationMs, force);
            return;
        }
        long now = System.currentTimeMillis();

        if (inBurstMode && (now - burstController.getLastChangeMs() < 2_000L)) {
            FileLog.getInstance().debug(TAG, "requestBoost debounced: already boosted recently");
            return;
        }

        if (lastMovingTimeMs == 0L) {
            lastMovingTimeMs = now;
        } else if (!force && speed < 0.3f && (now - lastMovingTimeMs > 180_000L)) {
            long lastMotionWake = headingSensorController != null
                    ? headingSensorController.getLastMotionWakeUptime()
                    : 0L;
            long sinceMotionWake = SystemClock.uptimeMillis() - lastMotionWake;
            if (sinceMotionWake > 15_000L) {
                FileLog.getInstance().debug(TAG,
                        "requestBoost refused: truly stationary for " +
                                (now - lastMovingTimeMs) / 1000 + "s");
                return;
            } else {
                FileLog.getInstance().debug(TAG,
                        "requestBoost allowed after motion wake, stationary for "
                                + (now - lastMovingTimeMs) / 1000 + "s");
            }
        } else if (force) {
            FileLog.getInstance().debug(TAG, "requestBoost forced by user/gesture");
        }

        String reasonKey = reason == null ? BoostReason.UNKNOWN.getKey() : reason;
        eventBus.emitBoostRequested(reasonKey, force);
        // logged via FileLogLocationObserver
        if (durationMs <= 0)
            durationMs = 5_000L;
        if (inBurstMode) {
            burstController.extend(durationMs);
            return;
        }
        burstController.requestBurst(durationMs, force);
    }

    /**
     *
     */

    public void requestBoostIfEdgeRisk(float offsetMeters, float speedMps) {
        MovementState state = getCurrentState();
        if (speedMps < 2.0f || state == MovementState.STATIONARY
                || state == MovementState.WALKING) {
            return;
        }

        long now = System.currentTimeMillis();
        if (now - lastEdgeRiskMs > BurstConfig.getEdgeRiskWindowMs()) {
            edgeRiskHits = 0;
        }
        if (offsetMeters > 25f && speedMps > 5f) {
            edgeRiskHits++;
            lastEdgeRiskMs = now;
        }
        if (edgeRiskHits >= BurstConfig.getEdgeRiskRequiredHits()
                && now - burstController.getLastChangeMs() >= BurstConfig.getBoostMinIntervalMs()) {
            edgeRiskHits = 0;
            requestBoost(10_000L, BoostReason.EDGE_RISK.getKey());
        }
    }

    private void maybeTriggerDisplacementBoost(float displacement, long nowMs, boolean requestSingleFix) {
        if (displacement < BurstConfig.getDisplacementWakeThresholdM()) {
            return;
        }
        if (nowMs - lastDisplacementWakeMs > BurstConfig.getDisplacementWakeWindowMs()) {
            displacementWakeHits = 0;
        }
        displacementWakeHits++;
        lastDisplacementWakeMs = nowMs;
        if (displacementWakeHits < BurstConfig.getDisplacementWakeRequiredHits()) {
            return;
        }
        if (nowMs - lastLowPriorityBoostMs < BurstConfig.getLowPriorityBoostCooldownMs()) {
            return;
        }
        if (nowMs - burstController.getLastChangeMs() < BurstConfig.getBoostMinIntervalMs()) {
            return;
        }
        displacementWakeHits = 0;
        lastLowPriorityBoostMs = nowMs;
        FileLog.getInstance().debug(TAG,
                String.format("Displacement wake: %.1fm -> boost", displacement));
        requestBoost(8_000L, BoostReason.DISPLACEMENT.getKey());
        if (requestSingleFix) {
            requestSingleHighAccuracyFix();
        }
    }

    private void maybeTriggerJumpBoost(float jumpMeters, long nowMs) {
        if (nowMs - lastJumpRiskMs > BurstConfig.getJumpRiskWindowMs()) {
            jumpRiskHits = 0;
        }
        jumpRiskHits++;
        lastJumpRiskMs = nowMs;
        if (jumpRiskHits < BurstConfig.getJumpRiskRequiredHits()) {
            return;
        }
        if (nowMs - lastLowPriorityBoostMs < BurstConfig.getLowPriorityBoostCooldownMs()) {
            return;
        }
        if (nowMs - burstController.getLastChangeMs() < BurstConfig.getBoostMinIntervalMs()) {
            return;
        }
        jumpRiskHits = 0;
        lastLowPriorityBoostMs = nowMs;
        requestBoost(8_000L, BoostReason.JUMP.getKey());
    }

    private SmartLocationManager(Context context) {
        this.context = context.getApplicationContext();
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this.context);
        handler = new Handler(Looper.getMainLooper());
        requestScheduler = new RequestScheduler(this.context,
                new FusedLocationSource(fusedLocationClient),
                handler);
        strategyManager = new StrategyManager(this.context, this);
        locationDispatcher = new LocationDispatcher(listeners, strategyManager, eventBus);
        burstController = new BurstController(handler, new BurstController.Listener() {
            @Override
            public void onBurstEnter(long durationMs) {
                inBurstMode = true;
                eventBus.emitBurstEnter();
                if (strategyManager != null) {
                    strategyManager.onLocationEngineReady();
                } else {
                    updateLocationParametersForState();
                }
                updateSensorState();
            }

            @Override
            public void onBurstExit(boolean fromTimer) {
                inBurstMode = false;
                eventBus.emitBurstExit();
                updateSensorState();
                if (requestScheduler != null) {
                    requestScheduler.resetLastRequested();
                }
                if (strategyManager != null) {
                    strategyManager.onLocationEngineReady();
                } else {
                    updateLocationParametersForState();
                }
            }
        }, BurstConfig.getBoostMinIntervalMs());

        activityTransitionMonitor = new ActivityTransitionMonitor(this.context, this::handleActivityTransition);
        eventBus.addObserver(new TelemetryLocationObserver());
        eventBus.addObserver(new FileLogLocationObserver());

        headingSensorController = new HeadingSensorController(this.context, this::handleMotionWake);
        predictionProvider = new DefaultPredictionProvider(new HeadingProvider() {
            @Override
            public boolean hasReliableHeading() {
                return SmartLocationManager.this.hasReliableHeading();
            }

            @Override
            public float getHeadingDegrees() {
                return SmartLocationManager.this.getCurrentHeading();
            }
        });
        smoothingFactorProvider = new DefaultSmoothingFactorProvider();
        FallbackStateProvider fallbackStateProvider = new FallbackStateProvider() {
            @Override
            public Location getLastPredictedLocation() {
                return lastPredictedLocation;
            }

            @Override
            public Location getLastDispatchedLocation() {
                return lastDispatchedLocation;
            }

            @Override
            public Location getLastSmoothedLocation() {
                return lastSmoothedLocation;
            }

            @Override
            public Location getLastGoodLocation() {
                return lastLocation;
            }

            @Override
            public float getSpeedMps() {
                return speed;
            }
        };
        fallbackProcessor = new FallbackProcessor(new DefaultFallbackProvider(fallbackStateProvider, predictionProvider));
        locationPipeline = new LocationPipeline()
                .addProcessor(new StaleFilterProcessor())
                .addProcessor(new QualityGateProcessor(fixQualityClassifier, this::getCurrentState))
                .addProcessor(new SmoothingProcessor())
                .addProcessor(new PredictionProcessor(predictionProvider))
                .addProcessor(new FallbackBuilderProcessor(fallbackProcessor))
                .addProcessor(new ElapsedTimeStampProcessor());
        policyContextProvider = new LocationPolicyContextProvider(new PolicyContextStateProvider() {
            @Override
            public MovementState getMovementState() {
                return getCurrentState();
            }

            @Override
            public boolean isInBurstMode() {
                return inBurstMode;
            }

            @Override
            public boolean isDeliveringAndIdle() {
                return isDeliveringAndIdle();
            }

            @Override
            public boolean isUiFollowActive() {
                return uiFollowActive;
            }

            @Override
            public boolean isMovingFlag() {
                return movingFlag;
            }
        });
    }

    public void setLocationUpdateListener(com.hf.easydelivery.core.facade.LocationUpdateListener listener) {
        listeners.clear();
        if (listener != null) {
            listeners.add(listener);
        }
    }

    /** Add an additional listener without removing existing ones. */
    public void addLocationUpdateListener(com.hf.easydelivery.core.facade.LocationUpdateListener listener) {
        if (listener != null) {
            listeners.add(listener);
        }
    }

    public void setLocationPolicy(@Nullable LocationPolicy policy) {
        if (policy != null) {
            this.locationPolicy = policy;
            FileLog.getInstance().debug(TAG, "setLocationPolicy -> " + policy.getClass().getSimpleName());
            updateLocationParametersForState();
        }
    }

    private LocationPolicyContext buildLocationPolicyContext() {
        return policyContextProvider.build();
    }

    private static com.hf.easydelivery.core.facade.MovementState toFacadeState(MovementState state) {
        if (state == null) {
            return com.hf.easydelivery.core.facade.MovementState.STATIONARY;
        }
        switch (state) {
            case WALKING:
                return com.hf.easydelivery.core.facade.MovementState.WALKING;
            case SLOW_DRIVING:
                return com.hf.easydelivery.core.facade.MovementState.SLOW_DRIVING;
            case NORMAL_DRIVING:
                return com.hf.easydelivery.core.facade.MovementState.NORMAL_DRIVING;
            case STATIONARY:
            default:
                return com.hf.easydelivery.core.facade.MovementState.STATIONARY;
        }
    }

    public LocationSnapshot getSnapshot() {
        return new LocationSnapshot(
                toFacadeState(getCurrentState()),
                inBurstMode,
                uiFollowActive,
                movingFlag,
                foregroundTrackingActive,
                speed,
                lastUpdateTime,
                lastGoodFixTime,
                movementStateMachine.getStationaryDurationMs(System.currentTimeMillis()),
                getCurrentHeading(),
                lastLocation == null ? null : new Location(lastLocation),
                lastSmoothedLocation == null ? null : new Location(lastSmoothedLocation),
                lastPredictedLocation == null ? null : new Location(lastPredictedLocation));
    }
    public void removeLocationUpdateListener(com.hf.easydelivery.core.facade.LocationUpdateListener listener) {
        if (listener != null) {
            listeners.remove(listener);
        }
    }

    public void startLocationUpdates() {
        if (ActivityCompat.checkSelfPermission(context,
                Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            // Handle the case where permission is not granted
            return;
        }
        if (foregroundTrackingActive) {
            long nowUptime = SystemClock.elapsedRealtime();
            boolean heartbeatStale = foregroundServiceHeartbeatUptimeMs > 0
                    && nowUptime - foregroundServiceHeartbeatUptimeMs > FG_TRACKING_HEARTBEAT_TIMEOUT_MS;
            boolean locationStale = lastForegroundLocationUptimeMs > 0
                    && nowUptime - lastForegroundLocationUptimeMs > FG_TRACKING_HEARTBEAT_TIMEOUT_MS;
            if (heartbeatStale && locationStale) {
                FileLog.getInstance().warning(TAG,
                        "foreground tracking stale -> self-heal to normal updates, hbAgeMs="
                                + (nowUptime - foregroundServiceHeartbeatUptimeMs)
                                + " locAgeMs=" + (nowUptime - lastForegroundLocationUptimeMs));
                foregroundTrackingActive = false;
            } else {
                FileLog.getInstance().debug(TAG, "startLocationUpdates skipped: foreground tracking active");
                return;
            }
        }
        lastMovingTimeMs = System.currentTimeMillis();
        lastGoodFixTime = 0L;
        lastEmergencyBoostUptimeMs = 0L;
        lastSingleFixUptimeMs = 0L;
        singleFixBackoffMs = BurstConfig.getSingleFixBackoffBaseMs();
        lastLocation = null;
        lastDispatchedLocation = null;

        if (locationCallback == null) {
            locationCallback = new LocationCallback() {
            @Override
            public void onLocationResult(LocationResult locationResult) {
                if (locationResult == null) {
                    return;
                }

                List<Location> locations = locationResult.getLocations();
                if (locations.isEmpty()) {
                    return;
                }

                Location last = locations.get(locations.size() - 1);
                  long locElapsedMs = getElapsedRealtimeMsSafe(last);
                  long ageMs = locElapsedMs > 0 ? (SystemClock.elapsedRealtime() - locElapsedMs) : -1L;
                  Location prev = lastLocation;
                  float dLast = prev != null ? prev.distanceTo(last) : -1f;
                  FileLog.getInstance().debug(TAG,
                          String.format(
                                  "onLocationResult: provider=%s acc=%.1fm speed=%.2f hasSpeed=%s mock=%s elapsedMs=%d lat=%.6f lng=%.6f ageMs=%d dLast=%.1f",
                                  last.getProvider(),
                                  last.getAccuracy(),
                                  last.hasSpeed() ? last.getSpeed() : 0f,
                                  last.hasSpeed(),
                                  last.isFromMockProvider(),
                                  locElapsedMs,
                                  last.getLatitude(),
                                  last.getLongitude(),
                                  ageMs,
                                  dLast));
                eventBus.emitLocationResult();
                updateLocation(last);
            }
        };
        }

        // Set initial update request (strategy decides actual params)
        if (strategyManager != null) {
            strategyManager.onLocationEngineReady();
        }
        if (activityTransitionMonitor != null) {
            activityTransitionMonitor.register();
        }
        updateSensorState();
    }

    private void switchToSignificantChanges() {
        if (ActivityCompat.checkSelfPermission(context,
                Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            // Handle the case where permission is not granted
            return;
        }
        LocationRequest locationRequest = new LocationRequest.Builder(Priority.PRIORITY_PASSIVE)
                .setIntervalMillis(60 * 1000) // 1 minute interval or as preferred
                .build();

        fusedLocationClient.requestLocationUpdates(locationRequest,
                locationCallback,
                Looper.getMainLooper());
    }

    private void updateLocation(Location newLocation) {

        Location prevGood = lastLocation;
        long nowMillis = System.currentTimeMillis();
        long nowUptime = SystemClock.elapsedRealtime();
        boolean recentMoving = movingHoldUntilMs > 0 && nowUptime <= movingHoldUntilMs;
        movingFlag = recentMoving;

        lastRawLocation = newLocation;

        long locElapsedMs = getElapsedRealtimeMsSafe(newLocation);
        long ageMs = locElapsedMs > 0L
                ? (SystemClock.elapsedRealtime() - locElapsedMs)
                : (nowMillis - newLocation.getTime());

        float computedSpeed = newLocation.hasSpeed() ? newLocation.getSpeed() : 0f;
        if (!newLocation.hasSpeed() && prevGood != null) {
            long prevElapsedMs = getElapsedRealtimeMsSafe(prevGood);
            long dtMs = (locElapsedMs > 0 && prevElapsedMs > 0)
                    ? (locElapsedMs - prevElapsedMs)
                    : (newLocation.getTime() - prevGood.getTime());
            if (dtMs > 0) {
                float dist = prevGood.distanceTo(newLocation);
                computedSpeed = dist / (dtMs / 1000f);
            }
        }

        double smoothingFactor = smoothingFactorProvider.getSmoothingFactor(computedSpeed, getCurrentState());
        float bearingInput = newLocation.hasBearing() ? newLocation.getBearing() : Float.NaN;
        ProcessingContext processingContext = new ProcessingContext(
                newLocation,
                prevGood,
                lastSmoothedLocation,
                computedSpeed,
                smoothingFactor,
                bearingInput,
                nowMillis,
                ageMs,
                getStaleThresholdMs());
        locationPipeline.process(processingContext);

        boolean okFix = processingContext.isOkFix();
        boolean poorFix = processingContext.isPoorFix();
        boolean stateChanged = false;
        boolean deliveringIdleChanged = false;

        if (poorFix) {
            FileLog.getInstance().debug(TAG,
                    String.format("Poor fix: acc=%.1fm(thr=%.1fm) stale=%s age=%dms",
                            newLocation.getAccuracy(),
                            processingContext.getOkThreshold(),
                            String.valueOf(processingContext.isStaleFix()),
                            ageMs));
            if (lastGoodFixTime == 0L) {
                lastGoodFixTime = nowMillis;
            }
            long sinceGood = nowMillis - lastGoodFixTime;
            if (sinceGood > QualityConfig.getPoorSignalGracePeriodMs()) {
                if (shouldRequestSingleFix()) {
                    requestSingleHighAccuracyFix();
                }
            }
            if (sinceGood > QualityConfig.getEmergencyBoostThresholdMs()
                    && (nowUptime - lastEmergencyBoostUptimeMs) > QualityConfig.getEmergencyBoostCooldownMs()) {
                lastEmergencyBoostUptimeMs = nowUptime;
                FileLog.getInstance().debug(TAG,
                        "Emergency boost: no good fix for " + (sinceGood / 1000) + "s");
                requestBoost(10_000L, BoostReason.EMERGENCY.getKey());
            }

            Location fallback = processingContext.getOutputLocation();
            if (fallback == null) {
                fallback = fallbackProcessor.buildFallback(newLocation, nowMillis);
            }
            if (fallback != null) {
                FileLog.getInstance().debug(TAG, "dispatch fallback (limited motion update)");
                lastDispatchedLocation = fallback;
                locationDispatcher.dispatch(fallback, toFacadeState(getCurrentState()), currentMinDispatchIntervalMs);
                lastUiDispatchUptimeMs = locationDispatcher.getLastDispatchUptimeMs();
                lastUiLocation = fallback;
            }

            if (weakSignalMonitor.update(newLocation.getAccuracy(),
                    QualityConfig.getWeakSignalThreshold(),
                    nowMillis)) {
                for (com.hf.easydelivery.core.facade.LocationUpdateListener l : listeners) {
                    if (l instanceof WeakSignalListener) {
                        try {
                            ((WeakSignalListener) l).onWeakSignal();
                        } catch (Exception ignored) {
                        }
                    }
                }
            }
            if (stateChanged || deliveringIdleChanged) {
                updateLocationParametersForState();
            }
            return;
        }

        speed = computedSpeed;
        float displacement = prevGood != null ? prevGood.distanceTo(newLocation) : 0f;
        lastDisplacementMeters = displacement;
        if (speed > 0.5f || displacement > 1.0f) {
            lastMovingTimeMs = nowMillis;
            movingHoldUntilMs = nowUptime + BurstConfig.getMovingHoldMs();
            movingFlag = true;
        }

        stateChanged = updateMovementState();
        MovementState state = getCurrentState();
        if (state == MovementState.SLOW_DRIVING || state == MovementState.NORMAL_DRIVING) {
            lastDrivingUptimeMs = nowUptime;
        }

        lastGoodFixTime = nowMillis;
        lastGoodLocationUptimeMs = nowUptime;
        lastLocation = newLocation;

        boolean requestSingleFix = okFix && newLocation.getAccuracy() > QualityConfig.getAccuracyThresholdPoor();
        maybeTriggerDisplacementBoost(displacement, nowMillis, requestSingleFix);
        if (displacement > 30f && speed > 10f) {
            maybeTriggerJumpBoost(displacement, nowMillis);
        }

        boolean deliveringIdleNow = isDeliveringAndIdle();
        deliveringIdleChanged = deliveringIdleNow != lastDeliveringIdle;
        lastDeliveringIdle = deliveringIdleNow;

        long newElapsedMs = getElapsedRealtimeMsSafe(newLocation);
        long lastSmoothedElapsedMs = lastSmoothedLocation != null
                ? getElapsedRealtimeMsSafe(lastSmoothedLocation)
                : -1L;
        boolean shouldDispatch = dispatchGate.shouldDispatch(
                newLocation,
                lastSmoothedLocation,
                lastUpdateTime,
                lastDrivingUptimeMs,
                speed,
                nowUptime,
                newElapsedMs,
                lastSmoothedElapsedMs);
        if (!shouldDispatch) {
            if (!movingFlag || nowUptime - lastUiDispatchUptimeMs < BurstConfig.getUiForceDispatchMs()) {
                if (stateChanged || deliveringIdleChanged) {
                    updateLocationParametersForState();
                }
                return;
            }
        }

        Location outputLoc = processingContext.getOutputLocation();
        if (outputLoc == null) {
            outputLoc = newLocation;
        }

        lastSmoothedLocation = outputLoc;
        lastPredictedLocation = processingContext.getPredictedLocation();

        lastDispatchedLocation = outputLoc;
        locationDispatcher.dispatch(outputLoc, toFacadeState(state), currentMinDispatchIntervalMs);
        lastUiDispatchUptimeMs = locationDispatcher.getLastDispatchUptimeMs();
        lastUiLocation = outputLoc;
        lastUpdateTime = newLocation.getTime();

        if (weakSignalMonitor.update(newLocation.getAccuracy(),
                QualityConfig.getWeakSignalThreshold(),
                nowMillis)) {
            for (com.hf.easydelivery.core.facade.LocationUpdateListener l : listeners) {
                if (l instanceof WeakSignalListener) {
                    try {
                        ((WeakSignalListener) l).onWeakSignal();
                    } catch (Exception ignored) {
                    }
                }
            }
        }

        if (stateChanged || deliveringIdleChanged) {
            updateLocationParametersForState();
        }

        this.forwardToDrivingDistanceTracker(outputLoc, state);
    }

    private boolean updateMovementState() {
        long nowUptime = SystemClock.elapsedRealtime();
        long nowMillis = System.currentTimeMillis();
        MovementStateMachine.Result result = movementStateMachine.updateBySpeed(
                speed,
                nowUptime,
                nowMillis,
                lastDrivingUptimeMs,
                lastInVehicleUptimeMs,
                lastDisplacementMeters,
                StrategyConfig.getDrivingDowngradeGraceMs(),
                StrategyConfig.getInVehicleGraceMs(),
                StrategyConfig.getDisplacementDrivingOverrideM());

        if (!result.changed) {
            return false;
        }

        if (result.wasNotDriving && result.newState != MovementState.STATIONARY) {
            requestBoost(10_000L, BoostReason.MOTION.getKey());
            // ????????????WALKING??DRIVING?????????
            requestSingleHighAccuracyFix();
        }

        FileLog.getInstance().debug(TAG, "movement state change: "
                + result.oldState + " -> " + result.newState + ", speed=" + speed);

        updateSensorState();
        return true;
    }

    public long getStationaryDurationMs() {
        return movementStateMachine.getStationaryDurationMs(System.currentTimeMillis());
    }

    private void updateLocationParametersForState() {
        if (strategyManager != null) {
            strategyManager.suggestRefresh("state");
            return;
        }
        if (locationCallback == null) {
            return;
        }
        LocationRequestParams params = locationPolicy != null
                ? locationPolicy.getRequestParams(buildLocationPolicyContext())
                : null;
        long interval = params != null ? params.intervalMs
                : (inBurstMode ? StrategyConfig.getBurstIntervalMs() : getRecommendedUpdateInterval());
        long minInterval = params != null ? params.minIntervalMs
                : (inBurstMode ? StrategyConfig.getBurstMinIntervalMs() : getMinUpdateInterval());
        int priority = params != null ? params.priority : getRecommendedPriority();
        float minDistance = params != null ? params.minDistanceMeters : getMinUpdateDistanceMeters();
        long maxDelay = params != null ? params.maxUpdateDelayMs
                : (inBurstMode ? StrategyConfig.getBurstMaxDelayMs() : 800L);
        currentMinDispatchIntervalMs = params != null ? params.minDispatchIntervalMs
                : (getCurrentState() == MovementState.STATIONARY && !movingFlag
                        ? StrategyConfig.getMinDispatchIntervalStationaryMs()
                        : StrategyConfig.getMinDispatchIntervalMovingMs());
        requestScheduler.applyRequest(interval,
                minInterval,
                priority,
                minDistance,
                maxDelay,
                locationCallback,
                inBurstMode);
    }


    /**
     */
    private boolean isDeliveringAndIdle() {
        long timeSinceLastMovement = System.currentTimeMillis() - lastMovingTimeMs;
        return (getCurrentState() == MovementState.STATIONARY)
                && timeSinceLastMovement > StrategyConfig.getDeliveringIdleThresholdMs();
    }

    /**
     */
    private int getRecommendedPriority() {
        if (inBurstMode) {
            return Priority.PRIORITY_HIGH_ACCURACY;
        }

        if (getCurrentState() == MovementState.WALKING
                || getCurrentState() == MovementState.SLOW_DRIVING
                || getCurrentState() == MovementState.NORMAL_DRIVING) {
            return Priority.PRIORITY_HIGH_ACCURACY; // Realtime: walking/driving prefer high accuracy
        }

        if (isDeliveringAndIdle()) {
            return Priority.PRIORITY_BALANCED_POWER_ACCURACY;
        }

        return Priority.PRIORITY_BALANCED_POWER_ACCURACY;
    }

    /**
     */
    private long getRecommendedUpdateInterval() {
        if (inBurstMode) {
            return StrategyConfig.getBurstIntervalMs();
        }

        if (isDeliveringAndIdle()) {
            return StrategyConfig.getIntervalDeliveringMs();
        }

        switch (getCurrentState()) {
            case STATIONARY:
            case WALKING:
                return StrategyConfig.getIntervalWalkingMs();
            case SLOW_DRIVING:
                return StrategyConfig.getIntervalDrivingSlowMs();
            case NORMAL_DRIVING:
                return StrategyConfig.getIntervalDrivingNormalMs();
            default:
                return 4_000L;
        }
    }

    /**
     */
    private long getMinUpdateInterval() {
        if (inBurstMode) {
            return StrategyConfig.getBurstMinIntervalMs();
        }

        if (isDeliveringAndIdle()) {
            return StrategyConfig.getMinIntervalDeliveringMs();
        }

        switch (getCurrentState()) {
            case STATIONARY:
            case WALKING:
                return StrategyConfig.getMinIntervalWalkingMs();
            case SLOW_DRIVING:
                return StrategyConfig.getMinIntervalDrivingSlowMs();
            case NORMAL_DRIVING:
                return StrategyConfig.getMinIntervalDrivingNormalMs();
            default:
                return 2_000L;
        }
    }

    private float getMinUpdateDistanceMeters() {
        if (inBurstMode) {
            return StrategyConfig.getBurstMinDistanceM();
        }
        if (isDeliveringAndIdle()) {
            return 8.0f;
        }
        switch (getCurrentState()) {
            case STATIONARY:
                return 6.0f;
            case WALKING:
                return 2.5f;
            case SLOW_DRIVING:
            case NORMAL_DRIVING:
            default:
                return 2.0f;
        }
    }

    private long getBurstModeInterval() {
        return StrategyConfig.getBurstIntervalMs();
    }

    private long getStaleThresholdMs() {
        long maxDelay = requestScheduler != null ? requestScheduler.getLastRequestedMaxDelayMs() : -1L;
        if (maxDelay > 0L) {
            return Math.max(5_000L, maxDelay + 2_000L);
        }
        return 5_000L;
    }

    public void stopLocationUpdates() {
        if (fusedLocationClient != null && locationCallback != null) {
            fusedLocationClient.removeLocationUpdates(locationCallback);
        }
        locationCallback = null;
        if (requestScheduler != null) {
            requestScheduler.reset();
        }
        if (headingSensorController != null) {
            headingSensorController.setActive(false);
        }
        if (activityTransitionMonitor != null) {
            activityTransitionMonitor.unregister();
        }
        if (burstController != null) {
            burstController.cancelTimer();
        }
    }

    public Location getLastLocation() {
        return lastLocation;
    }

    public boolean isInBurstMode() {
        return inBurstMode;
    }

    public void applyLocationRequest(@Nullable LocationRequestParams params, @NonNull String reason) {
        if (params == null) {
            return;
        }
        currentMinDispatchIntervalMs = params.minDispatchIntervalMs;
        if (foregroundTrackingActive) {
            FileLog.getInstance().debug(TAG, "applyLocationRequest skipped: foreground tracking active reason=" + reason);
            return;
        }
        FileLog.getInstance().debug(TAG,
                String.format("applyLocationRequest reason=%s interval=%dms minInterval=%dms minDistance=%.1fm",
                        reason,
                        params.intervalMs,
                        params.minIntervalMs,
                        params.minDistanceMeters));
        requestScheduler.applyRequest(params.intervalMs,
                params.minIntervalMs,
                params.priority,
                params.minDistanceMeters,
                params.maxUpdateDelayMs,
                locationCallback,
                inBurstMode);
    }

    public void startBurstWindowForStrategy(long durationMs, boolean force, @NonNull BoostReason reason) {
        long duration = durationMs > 0 ? durationMs : 5_000L;
        FileLog.getInstance().debug(TAG,
                "startBurstWindowForStrategy reason=" + String.valueOf(reason)
                        + " force=" + force + " durationMs=" + duration);
        if (inBurstMode) {
            burstController.extend(duration);
            return;
        }
        burstController.requestBurst(duration, force);
    }

    public void forceExitBurstForStrategy(@NonNull String reason) {
        if (!inBurstMode) {
            return;
        }
        FileLog.getInstance().debug(TAG, "forceExitBurstForStrategy reason=" + reason);
        burstController.forceExit(true);
    }

    public void startForegroundTracking() {
        if (foregroundTrackingActive) {
            return;
        }
        foregroundTrackingActive = true;
        foregroundServiceHeartbeatUptimeMs = SystemClock.elapsedRealtime();
        stopLocationUpdates();
        try {
            Intent intent = new Intent(context, com.hf.easydelivery.service.LocationForegroundService.class);
            intent.setAction(com.hf.easydelivery.service.LocationForegroundService.ACTION_START);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent);
            } else {
                context.startService(intent);
            }
            FileLog.getInstance().debug(TAG, "startForegroundTracking requested");
        } catch (Throwable t) {
            foregroundTrackingActive = false;
            startLocationUpdates();
            FileLog.getInstance().error(TAG, "startForegroundTracking failed", t);
        }
    }

    public void stopForegroundTracking() {
        // Do not resume normal fused updates when FG stops.
        stopForegroundTracking(false);
    }

    public void stopForegroundTracking(boolean resumeNormal) {
        if (!foregroundTrackingActive) {
            return;
        }
        foregroundTrackingActive = false;
        foregroundServiceHeartbeatUptimeMs = 0L;
        lastForegroundLocationUptimeMs = 0L;
        try {
            Intent intent = new Intent(context, com.hf.easydelivery.service.LocationForegroundService.class);
            intent.setAction(com.hf.easydelivery.service.LocationForegroundService.ACTION_STOP);
            context.startService(intent);
            FileLog.getInstance().debug(TAG, "stopForegroundTracking requested");
        } catch (Throwable t) {
            FileLog.getInstance().error(TAG, "stopForegroundTracking failed", t);
        }
        if (resumeNormal) {
            startLocationUpdates();
        }
    }

    public boolean isForegroundTrackingActive() {
        return foregroundTrackingActive;
    }

    public void onForegroundLocation(@NonNull Location location) {
        foregroundServiceHeartbeatUptimeMs = SystemClock.elapsedRealtime();
        lastForegroundLocationUptimeMs = foregroundServiceHeartbeatUptimeMs;
        updateLocation(location);
    }

    public void onForegroundServiceStateChanged(boolean active) {
        long nowUptime = SystemClock.elapsedRealtime();
        if (active) {
            foregroundTrackingActive = true;
            foregroundServiceHeartbeatUptimeMs = nowUptime;
            return;
        }
        boolean wasActive = foregroundTrackingActive;
        foregroundTrackingActive = false;
        foregroundServiceHeartbeatUptimeMs = 0L;
        lastForegroundLocationUptimeMs = 0L;
        if (wasActive && uiFollowActive) {
            FileLog.getInstance().warning(TAG, "foreground service stopped -> resume normal fused updates");
            startLocationUpdates();
        }
    }

    public MovementState getCurrentState() {
        return movementStateMachine.getCurrentState();
    }

    public boolean isMovingLikely() {
        long nowUptime = SystemClock.elapsedRealtime();
        return movingFlag || (movingHoldUntilMs > 0 && nowUptime <= movingHoldUntilMs);
    }

    private void handleActivityTransition(int activityType, int transitionType) {
        MovementState newState;
        switch (activityType) {
            case DetectedActivity.IN_VEHICLE:
                newState = MovementState.NORMAL_DRIVING;
                break;
            case DetectedActivity.WALKING:
                newState = MovementState.WALKING;
                break;
            case DetectedActivity.RUNNING:
                newState = MovementState.SLOW_DRIVING;
                break;
            default:
                newState = MovementState.STATIONARY;
                break;
        }
        updateStateFromActivity(newState);
    }

    private void updateStateFromActivity(MovementState newState) {
        if (newState == MovementState.SLOW_DRIVING || newState == MovementState.NORMAL_DRIVING) {
            long nowUptime = SystemClock.elapsedRealtime();
            lastInVehicleUptimeMs = nowUptime;
            movingHoldUntilMs = lastInVehicleUptimeMs + BurstConfig.getMovingHoldMs();
            movingFlag = true;
            if (!foregroundTrackingActive) {
                startForegroundTracking();
            }
            requestBoost(StrategyConfig.getBurstDurationMs(), BoostReason.MOTION.getKey());
        }
        if (movementStateMachine.applyExternalState(newState, System.currentTimeMillis())) {
            updateLocationParametersForState();
        }
    }

    

    // === Heading lifecycle ===
    public void setUiFollowActive(boolean active) {
        uiFollowActive = active;
        updateSensorState();
        updateLocationParametersForState();
    }

    private void updateSensorState() {
        boolean shouldEnable = uiFollowActive
                && (inBurstMode
                        || getCurrentState() == MovementState.SLOW_DRIVING
                        || getCurrentState() == MovementState.NORMAL_DRIVING);
        if (headingSensorController != null) {
            headingSensorController.setActive(shouldEnable);
        }
    }

    private void handleMotionWake(long nowUptime) {
        lastMovingTimeMs = System.currentTimeMillis();
        FileLog.getInstance().debug(TAG, "motion wake detected -> boost + single fix");
        try {
            requestBoostForce(10_000L, BoostReason.MOTION.getKey());
        } catch (Throwable ignore) {
        }
        requestSingleHighAccuracyFix();
    }

    public void requestSingleHighAccuracyFix() {
        if (singleUpdateInFlight)
            return;
        if (fusedLocationClient == null)
            return;
        if (ActivityCompat.checkSelfPermission(context,
                Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            return;
        }
        long nowUptime = SystemClock.elapsedRealtime();
        if (nowUptime - lastSingleFixUptimeMs < singleFixBackoffMs) {
            return;
        }
        lastSingleFixUptimeMs = nowUptime;
        singleFixBackoffMs = Math.min(singleFixBackoffMs * 2L, BurstConfig.getSingleFixBackoffMaxMs());
        singleUpdateInFlight = true;
        CancellationTokenSource tokenSource = new CancellationTokenSource();
        try {
            fusedLocationClient.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, tokenSource.getToken())
                    .addOnSuccessListener(location -> {
                        singleUpdateInFlight = false;
                        if (location != null) {
                            updateLocation(location);
                        }
                    })
                    .addOnFailureListener(error -> singleUpdateInFlight = false);
        } catch (SecurityException se) {
            singleUpdateInFlight = false;
        }
    }

    public boolean hasReliableHeading() {
        return headingSensorController != null && headingSensorController.hasReliableHeading();
    }

    /** Returns current heading in degrees [0,360), or NaN if unavailable. */
    public float getCurrentHeading() {
        return headingSensorController != null
                ? headingSensorController.getCurrentHeadingDegrees()
                : Float.NaN;
    }

    /** Returns last smoothed location (may be null). */
    public Location getLastSmoothedLocation() {
        return lastSmoothedLocation;
    }

    /** Returns last predicted location (may be null). */
    public Location getPredictedLocation() {
        if (lastPredictedLocation == null)
            return null;
        return new Location(lastPredictedLocation);
    }

    private boolean shouldRequestSingleFix() {
        long nowUptime = SystemClock.elapsedRealtime();
        return !singleUpdateInFlight
                && nowUptime - lastSingleFixUptimeMs >= singleFixBackoffMs;
    }

    /**
     * Forward location to DrivingDistanceTracker (persisted odometer).
     * Direct call (no reflection).
     */
    private void forwardToDrivingDistanceTracker(Location loc, MovementState state) {
        if (loc == null)
            return;
        try {
            DrivingDistanceTracker
                    .getInstance(context.getApplicationContext())
                    .onLocationUpdate(loc, state);
        } catch (Throwable ignore) {
        }
    }

    private long getElapsedRealtimeMsSafe(@NonNull Location loc) {
        try {
            return loc.getElapsedRealtimeNanos() / 1_000_000L;
        } catch (Throwable t) {
            return -1L;
        }
    }


}
