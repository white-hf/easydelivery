package com.hf.easydelivery.map;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.graphics.Point;
import android.os.IBinder;
import android.location.Location;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.os.VibratorManager;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.view.WindowInsets;
import android.widget.ImageButton;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;
import android.content.Intent;
import android.net.Uri;
import android.text.TextUtils;

import android.os.SystemClock;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.drawable.Drawable;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.appcompat.content.res.AppCompatResources;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.MapView;
import com.google.android.gms.maps.MapsInitializer;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.model.BitmapDescriptor;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;
import com.google.android.gms.maps.model.CameraPosition;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.LatLngBounds;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.button.MaterialButton;
import com.google.maps.android.clustering.ClusterManager;
import com.hf.courierservice.apihelper.FileLog;
import com.hf.easydelivery.R;
import com.hf.easydelivery.ResourceMgr;
import com.hf.easydelivery.common.Utils;
import com.hf.easydelivery.core.SmartLocationManager;
import com.hf.easydelivery.dao.DeliveryInfo;
import com.hf.easydelivery.view.Adapter.ClusterParcelAdapter;
import com.hf.easydelivery.view.CameraActivity;
import com.hf.easydelivery.view.SmsBottomSheetFragment;
import com.hf.easydelivery.view.DeliveredPackagesFragment;
import com.hf.easydelivery.view.model.MapViewModel;
import com.hf.easydelivery.view.model.ScanViewModel;
import com.hf.easydelivery.map.config.ProfileManager;
import com.hf.easydelivery.view.DeveloperPanelBottomSheet;
import com.hf.easydelivery.service.FocusState;
import com.hf.easydelivery.service.FocusStateRepository;
import com.hf.easydelivery.map.CameraUpdateContext;
import com.hf.easydelivery.core.policy.BlendedLocationPolicy;
import com.hf.easydelivery.core.strategy.StrategyConfig;
import com.hf.easydelivery.telemetry.Telemetry;
import com.hf.easydelivery.map.policy.BlendedFollowPolicy;

import android.animation.ValueAnimator;
import android.view.animation.LinearInterpolator;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.HashMap;
import java.util.Map;

/**
 * 地图派送界面（内层 Fragment）
 * <p>
 * 职责摘要:
 * </p>
 * <ul>
 * <li>协调 {@link DeliveryFocusManager}、{@link CameraFollowController} 完成定位→焦点→UI
 * 展示的流程。</li>
 * <li>负责 Info Pill UI 的渲染/交互（关闭、跳转拍照、显示群组列表）。</li>
 * <li>维护地图控件与 ViewModel 的绑定：包裹数据刷新、定位权限、相机与手势事件。</li>
 * </ul>
 * <p>
 * 核心流程:
 * </p>
 * <ol>
 * <li>ViewModel 推送包裹数据 → Fragment 缓存当前“派送中”快照，用于后续距离排序。</li>
 * <li>SmartLocationManager 推送定位 → `DeliveryFocusManager.sortByDistance(...)`
 * 计算最近 20 单、自动构建 50m 内聚合列表，用于 Info Pill & zoom。</li>
 * <li>UI 回调（折叠、展开、恢复跟随）仅影响 Fragment 与
 * {@link CameraFollowController}，逻辑趋于无状态。</li>
 * </ol>
 */
public class MapInnerFragment extends Fragment
        implements OnMapReadyCallback, SmartLocationManager.LocationUpdateListener {
    private static final String TAG = "MapInnerFragment";
    private static final float DEFAULT_DISTANCE_METERS = 1000f;
    private static final float INFO_PILL_PROXIMITY_THRESHOLD_METERS = 500f;
    private static final long MAX_LOCATION_AGE_MS = 60_000L;
    private static final long MANUAL_CENTER_HOLD_MS = 3_000L;
    private static final long INSIDE_MANUAL_CENTER_HOLD_MS = 500L;
    private static final long INSIDE_BOOST_DURATION_MS = 5_000L;
    private static final long INSIDE_BOOST_COOLDOWN_MS = 25_000L;
    private static final int EDGE_OUTSIDE_REQUIRED = 2;
    private static final float EDGE_MARGIN_X = 0.15f;
    private static final float EDGE_MARGIN_Y = 0.20f;
    private static final float EDGE_MARGIN_Y_DRIVING = 0.30f;

    // ===== UI location quality gating (map-layer) =====
    private static final float GOOD_ACCURACY_DRIVING_M = 35f;
    private static final float GOOD_ACCURACY_WALKING_M = 50f;
    private static final long STALE_LOCATION_MS_DRIVING = 2_000L;
    private static final long STALE_LOCATION_MS_OTHER = 5_000L;
    private static final long MAX_PREDICTED_UI_AGE_MS = 1_500L;

    private void logD(String msg) {
        try {
            FileLog.getInstance().debug(TAG, msg);
        } catch (Throwable ignore) {
        }
    }

    private MapView mapView;
    private GoogleMap googleMap;
    private ClusterManager<DeliveryInfo> clusterManager;
    private MyClusterRenderer<DeliveryInfo> myClusterRenderer;
    private LatLng savedPosition;
    private MaterialToolbar mToolbar;
    private TextView statusSummaryText;
    private ProgressBar progressMap;
    private View infoPill;
    private View collapsedInfoPill;
    private TextView collapsedInfoText;
    private TextView pillRouteText;
    private TextView pillAddressText;
    private TextView pillRecipientText;
    private MaterialButton btnPillShowList;
    private Marker myLocationMarker;
    private BitmapDescriptor myLocationIcon;
    private final DeliveryFocusManager focusManager = new DeliveryFocusManager();
    private final SimpleEtaEstimator simpleEtaEstimator = new SimpleEtaEstimator();
    private CameraFollowController cameraController;
    // Proximity (Phase 2)：InfoPill 显隐由独立策略控制
    private InfoPillProximityController proximityController;
    private ProximityCoordinator proximityCoordinator;
    // Profile (PowerSaver / Advanced)
    private ProfileManager profileManager;

    private final ProfileManager.Listener profileListener = new ProfileManager.Listener() {
        @Override
        public void onProfileChanged(@NonNull ProfileManager.AppProfile newProfile) {
            try {
                if (proximityController != null) {
                    proximityController.applyAppProfile(newProfile);
                    logD("profile changed -> proximity=" + newProfile);
                }
            } catch (Throwable ignore) {
            }
            try {
                if (cameraController != null) {
                    cameraController.applyAppProfile(newProfile);
                    logD("profile changed -> camera=" + newProfile);
                }
            } catch (Throwable ignore) {
            }
            try {
                updateUiTickInterval();
            } catch (Throwable ignore) {
            }
            try {
                if (focusManager != null) {
                    focusManager.applyAppProfile(newProfile);
                    logD("profile changed -> focus=" + newProfile);
                }
            } catch (Throwable ignore) {
            }
            try {
                applyPerfBalance(profileManager != null ? profileManager.getPerfBalance() : 0f);
            } catch (Throwable ignore) {
            }
        }
    };

    private final ProfileManager.PerfBalanceListener perfBalanceListener = new ProfileManager.PerfBalanceListener() {
        @Override
        public void onPerfBalanceChanged(float newBalance) {
            applyPerfBalance(newBalance);
        }
    };
    private List<DeliveryInfo> currentMapDeliveries = Collections.emptyList();
    private List<DeliveryInfo> nearestDeliveries = Collections.emptyList();
    private List<DeliveryInfo> currentCloseDeliveries = Collections.emptyList();
    private DeliveryInfo currentPrimaryDelivery = null;
    private String currentPrimaryKey = null;
    private float lastNearestDistanceMeters = Float.NaN;
    private float lastValidNearestDistanceMeters = Float.NaN; // 缓存最近一次有效距离，避免远距采样间隙回落
    private View btnResumeFollow;
    private boolean autoFollowPausedByGesture = false;
    private long manualCenterHoldUntilMs = 0L;
    private InfoPillProximityController.RegionState currentRegionState = InfoPillProximityController.RegionState.IN_TRANSIT;
    private boolean insideZoneBoostEnabled = false;
    private long lastInsideBoostMs = 0L;
    private boolean forceProximityEvaluation = false;
    private long autoFollowPausedAtMs = 0L;
    private long locationUpdateSeq = 0L;

    private SmartLocationManager mSmartLocationManager;
    private Location mLastLocation = null;
    private CameraUpdateContext pendingCameraContext;
    private final Handler cameraUpdateHandler = new Handler(Looper.getMainLooper());
    private final Runnable cameraUpdateRunnable = () -> {
        if (cameraController != null && pendingCameraContext != null) {
            Telemetry.counter("ui.cameraUpdate");
            cameraController.updateCamera(pendingCameraContext);
        }
    };
    private long uiTickIntervalMs = 250L;
    private final Runnable uiTickRunnable = new Runnable() {
        @Override
        public void run() {
            if (!isAdded()) {
                return;
            }
            if (cameraController != null) {
                CameraUpdateContext tickContext = buildCameraContextForTick();
                if (tickContext != null) {
                    cameraController.updateCamera(tickContext);
                }
            }
            scheduleUiTick();
        }
    };

    // ===== Map-layer location smoothing / gating state =====
    private Location mLastEffectiveUiLocation = null; // what marker/camera used last time
    private Location mLastGoodLocation = null;        // last good (accurate + not stale) raw fix
    private long mLastGoodUptimeMs = 0L;
    private CameraUpdateContext.LocationSource mLastEffectiveSource = CameraUpdateContext.LocationSource.UNKNOWN;

    // Marker smoothing
    private ValueAnimator myLocAnimator = null;
    private long lastMarkerAnimUptime = 0L;
    private LatLng lastMarkerLatLng = null;
    private float lastMarkerBearing = Float.NaN;

    // Fullscreen mode
    private ImageButton btnFullscreen;
    private boolean isFullscreenMode = false;

    private MapViewModel mapViewModel;
    // 支持两种数据源：派送中(地图) / 未扫描(扫码)
    private ScanViewModel scanViewModel;

    private enum DataMode {
        DELIVERY, UNSCANNED
    }

    private DataMode currentMode = DataMode.DELIVERY; // 默认派送中

    private ActivityResultLauncher<String> requestLocationPermissionLauncher;
    private SmartLocationManager.MovementState lastMovementState = SmartLocationManager.MovementState.STATIONARY;

    private boolean isUserInteracting = false;
    private boolean infoPillCollapsed = false;
    private int infoPillBaseBottomMarginPx = 0;
    private int lastSystemBottomInset = 0;
    private boolean navigationModeEnabled = false;

    // --- Developer panel shortcut (double-tap toolbar) ---
    private static final long DEV_DOUBLE_TAP_WINDOW_MS = 450L;
    private static final long AUTO_FOLLOW_PAUSE_MS = 8000L; // 用户手势后，约 3 秒保护窗口
    private long lastToolbarTapMs = 0L;

    // ===== Top-3 主案：Fragment 侧轻量采样/抑制配置 =====
    // 远距降采样：当最近目标很远时，降低 Proximity 评估频率
    private static final float FAR_DISTANCE_SAMPLE_THRESHOLD_M = 1500f; // >1.5km 认为“远距”
    private static final long FAR_SAMPLE_MIN_INTERVAL_MS = 2500L; // 远距评估最少间隔
    private static final long NEAR_SAMPLE_MIN_INTERVAL_MS = 800L; // 近距评估最少间隔

    // 区域通勤极简：高速穿越空区时，短时间抑制 Proximity 评估
    private static final float COMMUTE_SWITCH_M = 600f; // 未越过 600m 仍认为同一区域
    // 通勤抑制：已缩短为 15s，近距/步行时会被即时解除
    private static final long COMMUTE_SUPPRESS_MS = 15_000L;

    // UI 兜底的回差：策略层已有 lock/unlock，这里只做折叠后的最小回差
    private static final float LOCK_HYSTERESIS_EXTRA_M = 80f;

    // 运行时状态
    private long lastProximityEvalMs = 0L; // 上次执行 proximity 的时间
    @Nullable
    private LatLng commuteAnchorLatLng = null; // 区域通勤锚点
    private long commuteSuppressUntilMs = 0L; // 通勤抑制到期时间
    private long lastCommuteSuppressedKey = 0L;
    private int edgeOutsideConsecutive = 0;

    /** Expose for developer panel to apply follow config at runtime. */
    @Nullable
    public CameraFollowController getCameraController() {
        return cameraController;
    }

    /** Expose for developer panel to apply zoom/focus config at runtime. */
    @NonNull
    public DeliveryFocusManager getFocusManager() {
        return focusManager;
    }

    public boolean isInsideZoneBoostEnabled() {
        return insideZoneBoostEnabled;
    }

    public void setInsideZoneBoostEnabled(boolean enabled) {
        insideZoneBoostEnabled = enabled;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
            @Nullable Bundle savedInstanceState) {
        FileLog.i(TAG, "onCreateView: enter");
        View view = inflater.inflate(R.layout.activity_map, container, false);
        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO);

        // 初始化 ViewModel
        mapViewModel = new ViewModelProvider(requireActivity()).get(MapViewModel.class);
        // 未扫描数据由 ScanViewModel 提供（跨页面共享，用 Activity 作用域）
        scanViewModel = new ViewModelProvider(requireActivity()).get(ScanViewModel.class);

        setupViews(view);
        setupToolbar();
        setupMap(savedInstanceState);
        setupPermissions();
        // Phase 2: 初始化 InfoPill 接近判定（Proximity）
        setupProximity();

        // 初始化并应用当前 Profile 到各策略组件
        profileManager = ProfileManager.get(requireContext());
        ProfileManager.AppProfile appProfile = profileManager.getCurrent();
        try {
            if (proximityController != null) {
                proximityController.applyAppProfile(appProfile);
            }
        } catch (Throwable ignore) {
        }
        try {
            if (cameraController != null) {
                cameraController.applyAppProfile(appProfile);
            }
        } catch (Throwable ignore) {
        }
        try {
            if (focusManager != null) {
                focusManager.applyAppProfile(appProfile);
            }
        } catch (Throwable ignore) {
        }
        try {
            profileManager.addListener(profileListener);
        } catch (Throwable ignore) {
        }
        try {
            profileManager.addPerfBalanceListener(perfBalanceListener);
            applyPerfBalance(profileManager.getPerfBalance());
        } catch (Throwable ignore) {
        }

        // 核心改动：设置所有 LiveData 的观察者
        observeViewModel();

        // 首次进入时，通知 ViewModel 加载数据
        mapViewModel.init();

        FileLog.i(TAG, "onCreateView: exit");
        return view;
    }

    private void setupViews(View view) {
        mapView = view.findViewById(R.id.mapView);
        mToolbar = view.findViewById(R.id.toolbar);
        statusSummaryText = view.findViewById(R.id.tv_status_compact);
        progressMap = view.findViewById(R.id.progress_map);
        infoPill = view.findViewById(R.id.info_pill);
        collapsedInfoPill = view.findViewById(R.id.info_pill_collapsed);
        collapsedInfoText = view.findViewById(R.id.info_pill_collapsed_text);
        pillRouteText = view.findViewById(R.id.pill_route);
        pillAddressText = view.findViewById(R.id.pill_address);
        pillRecipientText = view.findViewById(R.id.pill_recipient);
        btnPillShowList = view.findViewById(R.id.btn_pill_show_list);
        ImageButton pillCloseButton = view.findViewById(R.id.btn_pill_close);
        btnResumeFollow = view.findViewById(R.id.btn_resume_follow);
        if (btnResumeFollow != null) {
            btnResumeFollow.setVisibility(View.GONE);
            btnResumeFollow.setOnClickListener(v -> onResumeFollowClicked());
            try {
                btnResumeFollow.bringToFront();
            } catch (Throwable ignore) {
            }
            try {
                btnResumeFollow.setElevation(10f);
            } catch (Throwable ignore) {
            }
        }

        if (statusSummaryText != null) {
            statusSummaryText.setOnLongClickListener(v -> {
                showDeveloperPanel();
                return true;
            });
        }

        if (pillCloseButton != null) {
            pillCloseButton.setOnClickListener(v -> collapseInfoPill());
        }
        if (collapsedInfoPill != null) {
            collapsedInfoPill.setOnClickListener(v -> expandInfoPill());
        }
        if (btnPillShowList != null) {
            btnPillShowList.setOnClickListener(v -> {
                List<DeliveryInfo> group = currentCloseDeliveries;
                if (group == null || group.isEmpty()) {
                    if (currentPrimaryDelivery != null) {
                        group = Collections.singletonList(currentPrimaryDelivery);
                    } else {
                        group = Collections.emptyList();
                    }
                }
                if (!group.isEmpty()) {
                    showClusterItemListBottomSheet(new ArrayList<>(group));
                }
            });
        }
        if (infoPill != null) {
            infoPillBaseBottomMarginPx = getResources().getDimensionPixelSize(R.dimen.info_pill_margin_bottom_base);
            updateInfoPillBottomMargin(lastSystemBottomInset);
            infoPill.setOnClickListener(v -> {
                if (currentPrimaryDelivery != null) {
                    showCamera(currentPrimaryDelivery);
                }
            });
            View root = view.findViewById(R.id.map_root);
            if (root != null) {
                ViewCompat.setOnApplyWindowInsetsListener(root, (v, insets) -> {
                    int sysBottom = insets.getInsets(WindowInsetsCompat.Type.systemBars()).bottom;
                    int gestureBottom = insets.getInsets(WindowInsetsCompat.Type.mandatorySystemGestures()).bottom;
                    lastSystemBottomInset = Math.max(sysBottom, gestureBottom);
                    updateInfoPillBottomMargin(lastSystemBottomInset);
                    return insets;
                });
                root.requestApplyInsets();
            }
            infoPill.post(() -> updateInfoPillBottomMargin(lastSystemBottomInset));
        }

        View mini = view.findViewById(R.id.include_minibar);
        if (mini != null) {
            ImageButton btnMyLoc = mini.findViewById(R.id.btn_my_loc);
            ImageButton btnMapType = mini.findViewById(R.id.btn_map_type);
            if (btnMyLoc != null)
                btnMyLoc.setOnClickListener(v -> centerOnMyLocation(false));
            if (btnMapType != null)
                btnMapType.setOnClickListener(v -> toggleMapType(btnMapType));
        }

        btnFullscreen = mini.findViewById(R.id.btn_fullscreen);
        if (btnFullscreen != null) {
            btnFullscreen.setOnClickListener(v -> toggleFullscreenMode());
        }

        if (mToolbar != null) {
            mToolbar.setOnClickListener(v -> onToolbarTapped());
        }
    }

    private void setupToolbar() {
        if (mToolbar == null)
            return;
        Menu menu = mToolbar.getMenu();
        if (menu != null)
            menu.clear();
        mToolbar.inflateMenu(R.menu.map_menu);
        updateNavigationMenuItem();
        mToolbar.setNavigationIcon(null);
        mToolbar.setOnMenuItemClickListener(item -> {
            int id = item.getItemId();
            if (id == R.id.menu_refresh) {
                currentMode = DataMode.DELIVERY;
                try {
                    mapViewModel.requestAndRefreshMarkers(true);
                } catch (Throwable ignore) {
                }
                if (getParentFragment() instanceof MapHostFragment)
                    ((MapHostFragment) getParentFragment()).onRequestInTransitList();
                Toast.makeText(requireContext(), R.string.map_refresh_toast, Toast.LENGTH_SHORT).show();
                return true;
            } else if (id == R.id.menu_unscanned) {
                currentMode = DataMode.UNSCANNED;
                if (scanViewModel != null)
                    scanViewModel.queryUnscanned();
                if (getParentFragment() instanceof MapHostFragment) {
                    ((MapHostFragment) getParentFragment()).onRequestUnscannedList();
                }
                Toast.makeText(requireContext(), R.string.map_query_unscanned_toast, Toast.LENGTH_SHORT).show();
                return true;
            } else if (id == R.id.menu_nav_mode) {
                toggleNavigationMode();
                return true;
            }
            return false;
        });
    }

    private void setupMap(Bundle savedInstanceState) {
        mapView.onCreate(savedInstanceState);
        mapView.getMapAsync(this);
        try {
            MapsInitializer.initialize(requireContext().getApplicationContext());
        } catch (Exception e) {
            FileLog.e(TAG, "Unable to initialize maps", e);
        }
    }

    private void setupPermissions() {
        requestLocationPermissionLauncher = registerForActivityResult(
                new ActivityResultContracts.RequestPermission(),
                isGranted -> {
                    if (isGranted) {
                        getLocation();
                    } else {
                        Toast.makeText(requireContext(), R.string.map_location_permission_required, Toast.LENGTH_SHORT).show();
                    }
                });

        if (ContextCompat.checkSelfPermission(requireContext(),
                Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            requestLocationPermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION);
        } else {
            getLocation();
        }
    }

    /**
     * Phase 2：初始化接近判定与协调器（Info Pill 显隐迁出定位层）
     */
    private void setupProximity() {
        if (proximityController == null) {
            proximityController = new InfoPillProximityController();
            // 默认用 ADVANCED；随后 onCreateView 会根据 ProfileManager 统一覆盖
            proximityController.setProfile(InfoPillProximityController.ProximityProfile.ADVANCED);
            DeliveryFocusManager.RegionConfig regionConfig = focusManager.getRegionConfig();
            regionConfig.showRadiusMeters = 250f;
            regionConfig.hideRadiusMeters = 320f;
            regionConfig.clusterRadiusMeters = 800f;
            focusManager.applyRegionConfig(regionConfig);
            proximityController.setRegionConfig(regionConfig);
            proximityController.setEtaEstimator(simpleEtaEstimator);
        }
        if (proximityCoordinator == null) {
            // 复用同一个 focusManager，保证排序/配置一致
            proximityCoordinator = new ProximityCoordinator(focusManager, proximityController);
            proximityCoordinator.setNearbyRadiusMeters(50f);
            proximityCoordinator.setNearbyLimit(20);
            proximityCoordinator.setBoostable(ms -> {
                if (mSmartLocationManager != null) {
                    try {
                        mSmartLocationManager.requestBoost(ms, "proximity");
                    } catch (Throwable ignore) {
                    }
                }
            });
            proximityCoordinator.setListener(new ProximityCoordinator.Listener() {
                @Override
                public void onShow(@NonNull DeliveryInfo target, float distanceMeters,
                        @NonNull List<DeliveryInfo> nearby) {
                    currentPrimaryDelivery = target;
                    currentCloseDeliveries = nearby;
                    currentPrimaryKey = buildPrimaryKey(target);
                    lastNearestDistanceMeters = distanceMeters;
                    if (!Float.isNaN(distanceMeters)) {
                        lastValidNearestDistanceMeters = distanceMeters;
                    }
                    // 进入时退出通勤抑制
                    commuteSuppressUntilMs = 0L;
                    showInfoPill(target, nearby);
                    publishLockscreenFocus(target, distanceMeters);
                }

                @Override
                public void onUpdate(@NonNull DeliveryInfo target, float distanceMeters,
                        @NonNull List<DeliveryInfo> nearby) {
                    currentPrimaryDelivery = target;
                    currentCloseDeliveries = nearby;
                    currentPrimaryKey = buildPrimaryKey(target);
                    lastNearestDistanceMeters = distanceMeters;
                    if (!Float.isNaN(distanceMeters)) {
                        lastValidNearestDistanceMeters = distanceMeters;
                    }
                    // 更新时退出通勤抑制
                    commuteSuppressUntilMs = 0L;
                    showInfoPill(target, nearby);
                    publishLockscreenFocus(target, distanceMeters);
                }

                @Override
                public void onHide() {
                    // 隐藏时清当前主键（允许下次策略自由选择）
                    currentPrimaryKey = null;
                    hideInfoPillCompletely();
                    publishLockscreenFocus(null, Float.NaN);
                }
            });

            logD("proximity ready: radius=50.0m, limit=20, profile="
                    + (proximityController == null ? "null" : proximityController.getProfile()));
        }
    }

    /**
     * 新增：集中设置所有 LiveData 的观察者
     */
    private void observeViewModel() {
        // 观察派送中地图项（仅在 DELIVERY 模式渲染）
        mapViewModel.getMapItemsLive().observe(getViewLifecycleOwner(), items -> {
            if (currentMode == DataMode.DELIVERY) {
                updateMapItems(items);
            }
        });

        // 观察未扫描项（仅在 UNSCANNED 模式渲染）
        if (scanViewModel != null) {
            scanViewModel.getUnscannedFilteredLive().observe(getViewLifecycleOwner(), unscanned -> {
                if (currentMode == DataMode.UNSCANNED) {
                    updateMapItems(unscanned);
                }
            });
        }

        // 状态与 Toast 维持由 MapViewModel 提供（不改变原功能）
        mapViewModel.getStatusLive().observe(getViewLifecycleOwner(), this::updateStatusBarUI);
        mapViewModel.getToastMessageLive().observe(getViewLifecycleOwner(), event -> {
            String msg = event.getMessage();
            if (msg != null && getContext() != null) {
                Toast.makeText(getContext(), msg, Toast.LENGTH_SHORT).show();
            }
        });
        mapViewModel.getUploadSuccessHapticLive().observe(getViewLifecycleOwner(), event -> {
            if (!isAdded())
                return;
            Boolean shouldVibrate = event.getMessage();

            logD("upload success haptic:" + shouldVibrate);
            if (shouldVibrate) {
                Utils.vibrate(requireContext(), 120);
            }
        });
        mapViewModel.getDialogMessageLive().observe(getViewLifecycleOwner(), event -> {
            String msg = event.getMessage();
            if (msg != null && isAdded()) {
                new AlertDialog.Builder(requireContext())
                        .setTitle(R.string.map_upload_failed_title)
                        .setMessage(msg)
                        .setPositiveButton(R.string.action_ok, null)
                        .show();
            }
        });
        mapViewModel.getDeliveryCompletedLive().observe(getViewLifecycleOwner(), event -> {
            DeliveryInfo info = event.getMessage();
            if (info != null && proximityCoordinator != null) {
                logD("deliveryCompletedLive -> onDeliveryCompleted for key=" + buildPrimaryKey(info));

                // Manual location injection optimization
                if (mLastLocation != null) {
                    float[] dist = new float[1];
                    android.location.Location.distanceBetween(mLastLocation.getLatitude(), mLastLocation.getLongitude(),
                            info.getLatitude(), info.getLongitude(), dist);
                    if (dist[0] <= 1000f) {
                        Location manualLoc = new Location("ManualDelivery");
                        manualLoc.setLatitude(info.getLatitude());
                        manualLoc.setLongitude(info.getLongitude());
                        manualLoc.setTime(System.currentTimeMillis());
                        // Inherit other properties to avoid nulls
                        manualLoc.setAltitude(mLastLocation.getAltitude());
                        manualLoc.setAccuracy(mLastLocation.getAccuracy());

                        mLastLocation = manualLoc;
                        mapViewModel.updateMyLocation(manualLoc);
                        logD("Manual location injected from delivery: " + info.getLatitude() + ","
                                + info.getLongitude());
                    } else {
                        logD("Manual location skipped: distance " + dist[0] + "m > 1000m");
                    }
                }

                clearCommuteSuppression();
                proximityCoordinator.onDeliveryCompleted(info);
                requestImmediateProximityRefresh();
            }
        });
    }

    private void updateMapItems(List<DeliveryInfo> items) {
        if (googleMap == null || clusterManager == null)
            return;
        clusterManager.clearItems();
        DeliveryInfo firstItem = null;
        List<DeliveryInfo> sanitized = new ArrayList<>();
        if (items != null) {
            for (DeliveryInfo info : items) {
                if (info == null)
                    continue;
                // 过滤掉正在上传队列中的包裹，保持原有口径
                try {
                    if (ResourceMgr.getInstance().getPendingPackagesMgr().exit(info.getOrderSn()))
                        continue;
                } catch (Throwable ignore) {
                }
                clusterManager.addItem(info);
                if (firstItem == null)
                    firstItem = info;
                sanitized.add(info);
            }
        }
        currentMapDeliveries = sanitized;
        logD("updateMapItems sanitized=" + sanitized.size());
        if (myClusterRenderer != null) {
            myClusterRenderer.setSpiderfyPositions(buildSpiderfyPositions(sanitized));
        }
        clusterManager.cluster();

        if (!isCurrentPrimaryStillPending() && currentPrimaryDelivery != null) {
            logD("current primary removed -> hiding pill and forcing refresh");
            hideInfoPillCompletely();
            forceProximityEvaluation = true;
            requestImmediateProximityRefresh();
        }

        if (currentMode == DataMode.DELIVERY && !sanitized.isEmpty()) {
            if (savedPosition == null) {
                LatLngBounds.Builder builder = new LatLngBounds.Builder();
                for (DeliveryInfo info : sanitized) {
                    if (info != null) {
                        builder.include(new LatLng(info.getLatitude(), info.getLongitude()));
                    }
                }
                LatLngBounds bounds = builder.build();
                double latSpan = bounds.northeast.latitude - bounds.southwest.latitude;
                double lngSpan = bounds.northeast.longitude - bounds.southwest.longitude;
                if (latSpan < 0.005 && lngSpan < 0.005) {
                    googleMap.animateCamera(CameraUpdateFactory.newLatLngZoom(
                            new LatLng(firstItem.getLatitude(), firstItem.getLongitude()), 15));
                } else {
                    int padding = 150;
                    googleMap.animateCamera(CameraUpdateFactory.newLatLngBounds(bounds, padding));
                }
            }
        }

        // 列表切换/刷新后，重置通勤抑制与锚点，避免旧状态影响新区域
        commuteSuppressUntilMs = 0L;
        commuteAnchorLatLng = null;
        lastProximityEvalMs = 0L;

        // 首次加载后定位到第一个包裹
        if (currentMode == DataMode.DELIVERY) {
            if (firstItem != null && savedPosition == null) {
                LatLng firstPosition = new LatLng(firstItem.getLatitude(), firstItem.getLongitude());
                googleMap.moveCamera(CameraUpdateFactory.newLatLngZoom(firstPosition, 14));
            } else if (sanitized.isEmpty() && savedPosition == null) {
                centerOnMyLocation(true);
            }
        }
        if (sanitized.isEmpty()) {
            hideInfoPillCompletely();
        }
    }

    private Map<String, LatLng> buildSpiderfyPositions(List<DeliveryInfo> items) {
        Map<String, List<DeliveryInfo>> groups = new HashMap<>();
        for (DeliveryInfo info : items) {
            if (info == null)
                continue;
            String key = String.format(Locale.US, "%.6f,%.6f", info.getLatitude(), info.getLongitude());
            List<DeliveryInfo> list = groups.get(key);
            if (list == null) {
                list = new ArrayList<>();
                groups.put(key, list);
            }
            list.add(info);
        }

        Map<String, LatLng> overrides = new HashMap<>();
        for (List<DeliveryInfo> group : groups.values()) {
            if (group.size() <= 1)
                continue;
            double baseLat = group.get(0).getLatitude();
            double baseLng = group.get(0).getLongitude();
            int count = group.size();
            double radiusMeters = Math.min(12.0, 4.0 + count * 1.5);
            double metersToLat = 1.0 / 111320.0;
            double cosLat = Math.cos(Math.toRadians(baseLat));
            if (Math.abs(cosLat) < 1e-6) {
                cosLat = 1e-6;
            }
            double metersToLng = 1.0 / (111320.0 * cosLat);
            for (int i = 0; i < count; i++) {
                double angle = 2 * Math.PI * i / count;
                double dLat = Math.cos(angle) * radiusMeters * metersToLat;
                double dLng = Math.sin(angle) * radiusMeters * metersToLng;
                DeliveryInfo info = group.get(i);
                overrides.put(info.getStableKey(), new LatLng(baseLat + dLat, baseLng + dLng));
            }
        }
        return overrides;
    }

    private void updateStatusBarUI(MapViewModel.MapStatus status) {
        String compact = String.format("%d/%d", status.deliveredCount, status.pendingCount);
        if (statusSummaryText != null) {
            statusSummaryText.setText(compact);
            revealStatusThenAutoFade();
        }

        if (progressMap != null) {
            progressMap.setVisibility(status.isLoading ? View.VISIBLE : View.GONE);
        }
    }

    private void revealStatusThenAutoFade() {
        if (statusSummaryText == null)
            return;
        statusSummaryText.animate().cancel();
        statusSummaryText.setAlpha(1f);
        statusSummaryText.animate()
                .alpha(0.35f)
                .setStartDelay(2500)
                .setDuration(500)
                .start();
    }

    private void showStatusLegendDialog() {
        new AlertDialog.Builder(requireActivity())
                .setTitle(R.string.status_help)
                .setMessage(R.string.map_status_legend_message)
                .setPositiveButton(R.string.action_got_it, null)
                .show();
    }

    @SuppressLint("MissingPermission")
    @Override
    public void onMapReady(GoogleMap map) {
        FileLog.i(TAG, "onMapReady: enter");
        googleMap = map;
        // 使用自定义定位标记，关闭默认蓝点
        googleMap.setMyLocationEnabled(false);
        cameraController = new CameraFollowController(googleMap, mapView);
        cameraController.setSmartLocationManager(mSmartLocationManager);
        cameraController.setNavigationModeEnabled(navigationModeEnabled);
        try {
            if (profileManager != null) {
                cameraController.applyAppProfile(profileManager.getCurrent());
                logD("onMapReady -> apply camera profile " + profileManager.getCurrent());
            }
        } catch (Throwable ignore) {
        }
        updateUiTickInterval();
        initClusterManager();
        if (savedPosition != null) {
            googleMap.moveCamera(CameraUpdateFactory.newLatLngZoom(savedPosition, googleMap.getCameraPosition().zoom));
        }
        FileLog.i(TAG, "onMapReady: exit");
    }

    private void initClusterManager() {
        clusterManager = new ClusterManager<>(requireContext(), googleMap);
        myClusterRenderer = new MyClusterRenderer<>(requireContext(), googleMap, clusterManager);
        clusterManager.setRenderer(myClusterRenderer);

        googleMap.setOnCameraIdleListener(() -> {
            clusterManager.onCameraIdle();
            if (cameraController != null) {
                cameraController.onCameraIdle();
            }
            isUserInteracting = false;
        });
        googleMap.setOnCameraMoveStartedListener(reason -> {
            boolean gesture = reason == GoogleMap.OnCameraMoveStartedListener.REASON_GESTURE;
            if (gesture && cameraController != null) {
                cameraController.beginBearingCapture();
            }
            if (!gesture && cameraController != null) {
                cameraController.endBearingCapture();
            }
            if (gesture) {
                pauseAutoFollowByGesture();
            }
            // 用户手势仅暂停自动跟随，不改动 Proximity 抑制；由 re-center 按钮清除。
            isUserInteracting = gesture && !navigationModeEnabled;
        });

        // Fallback: 某些 GMS/ROM 对轻微拖动不触发 gesture，这里用 click/long-click 兜底
        googleMap.setOnMapClickListener(latLng -> {
            logD("map click -> pauseAutoFollow (fallback)");
            pauseAutoFollowByGesture();
            isUserInteracting = !navigationModeEnabled;
        });
        googleMap.setOnMapLongClickListener(latLng -> {
            logD("map long click -> pauseAutoFollow (fallback)");
            pauseAutoFollowByGesture();
            isUserInteracting = !navigationModeEnabled;
        });

        googleMap.setOnCameraMoveListener(() -> {
            CameraPosition cameraPosition = googleMap.getCameraPosition();
            myClusterRenderer.setZoomLevel(cameraPosition.zoom);
            if (cameraController != null) {
                cameraController.onCameraMove(cameraPosition);
            }
        });

        clusterManager.setOnClusterClickListener(cluster -> {
            showClusterItemListBottomSheet(new ArrayList<>(cluster.getItems()));
            return true;
        });

        clusterManager.setOnClusterItemClickListener(item -> {
            ArrayList<DeliveryInfo> arrayList = new ArrayList<>();
            arrayList.add(item);
            showClusterItemListBottomSheet(arrayList);
            return true;
        });

        // 初次根据当前模式渲染一次（使用各自 VM 的当前快照）
        try {
            if (currentMode == DataMode.DELIVERY) {
                List<DeliveryInfo> snap = mapViewModel.getMapItemsLive().getValue();
                updateMapItems(snap);
            } else if (scanViewModel != null) {
                List<DeliveryInfo> snap = scanViewModel.getUnscannedFilteredLive().getValue();
                updateMapItems(snap);
            }
        } catch (Throwable ignore) {
        }
        // Patch 3: Start edge check
        edgeCheckHandler.postDelayed(edgeCheckRunnable, 2000);
    }

    private void showClusterItemListBottomSheet(List<DeliveryInfo> items) {
        BottomSheetDialog dialog = new BottomSheetDialog(requireActivity());
        View sheet = getLayoutInflater().inflate(R.layout.dialog_cluster_list, null, false);
        dialog.setContentView(sheet);

        androidx.recyclerview.widget.RecyclerView rv = sheet.findViewById(R.id.rv_cluster);
        rv.setLayoutManager(new LinearLayoutManager(requireActivity()));
        List<DeliveryInfo> sorted = new ArrayList<>(items);
        sorted.sort(DeliveryFocusManager.getAddressComparator());

        rv.setAdapter(new ClusterParcelAdapter(sorted, info -> {
            if (currentMode == DataMode.UNSCANNED) {
                // 未扫描模式仅在列表内展开操作，不跳转
                return;
            } else {
                dialog.dismiss();
                showCamera(info);
            }
        }, new ClusterParcelAdapter.OnActionClick() {
            @Override
            public void onCall(DeliveryInfo info) {
                if (TextUtils.isEmpty(info.getPhone()))
                    return;
                Intent intent = new Intent(Intent.ACTION_DIAL, Uri.parse("tel:" + info.getPhone()));
                startActivity(intent);
            }

            @Override
            public void onSms(DeliveryInfo info) {
                Long oid = info.getOrderId() == null ? -1L : info.getOrderId();
                SmsBottomSheetFragment sheet = SmsBottomSheetFragment.newInstance(
                        oid,
                        info.getOrderSn(),
                        info.getPhone(),
                        info.getAddress(),
                        info.getName(),
                        info.getRouteNumber());
                sheet.show(getParentFragmentManager(), "SmsBottomSheetFragment");
            }

            @Override
            public void onShare(DeliveryInfo info) {
                StringBuilder sb = new StringBuilder();
                if (!TextUtils.isEmpty(info.getOrderSn())) {
                    sb.append(getString(R.string.map_share_waybill_format, info.getOrderSn()))
                            .append("\n");
                }
                if (!TextUtils.isEmpty(info.getRouteNumber())) {
                    sb.append(getString(R.string.map_share_package_format, info.getRouteNumber()))
                            .append("\n");
                }
                if (!TextUtils.isEmpty(info.getAddress())) {
                    sb.append(getString(R.string.map_share_address_format, info.getAddress()))
                            .append("\n");
                }
                if (!TextUtils.isEmpty(info.getName())) {
                    sb.append(getString(R.string.map_share_recipient_format, info.getName()))
                            .append("\n");
                }
                Intent shareIntent = new Intent(Intent.ACTION_SEND);
                shareIntent.setType("text/plain");
                shareIntent.putExtra(Intent.EXTRA_TEXT, sb.toString());
                startActivity(Intent.createChooser(shareIntent, getString(R.string.map_share_title)));
            }

            @Override
            public void onLocate(DeliveryInfo info) {
                dialog.dismiss();
                focusOnDelivery(info);
            }
        }));
        dialog.show();
    }

    private void showInfoPill(DeliveryInfo info, List<DeliveryInfo> focusGroup) {
        if (infoPill == null || info == null)
            return;
        expandInfoPill();
        // 同步主/群组，保证折叠提示一致
        currentPrimaryDelivery = info;
        DeliveryFocusManager.InfoGroup infoGroup = focusManager.buildInfoGroup(info, focusGroup);
        List<DeliveryInfo> sameAddressGroup = infoGroup.sameAddress;
        currentPrimaryKey = buildPrimaryKey(info);

        int nearbyCount = infoGroup.nearbyCount;
        int sameAddressCount = sameAddressGroup.size();

        String streetLabel = info.getCivilNumber() > 0
                ? getString(R.string.map_street_number_format, info.getCivilNumber())
                : getString(R.string.map_street_number_unknown);
        String unitLabel = (info.getUnitNumber() == null || info.getUnitNumber().isEmpty()) ? ""
                : getString(R.string.map_unit_number_format, info.getUnitNumber());
        String parcelLabel = info.getRouteNumber() == null
                ? getString(R.string.map_parcel_unknown)
                : getString(R.string.map_parcel_label_format, info.getRouteNumber());

        List<String> primaryParts = new ArrayList<>();
        if (!TextUtils.isEmpty(streetLabel))
            primaryParts.add(streetLabel);
        if (!TextUtils.isEmpty(unitLabel))
            primaryParts.add(unitLabel);
        if (!TextUtils.isEmpty(parcelLabel))
            primaryParts.add(parcelLabel);
        if (sameAddressCount > 1) {
            primaryParts.add(getString(R.string.map_same_address_count_format, sameAddressCount));
        }
        if (nearbyCount > 1) {
            primaryParts.add(getString(R.string.map_nearby_count_format, nearbyCount));
        }
        pillRouteText.setText(TextUtils.join("  ", primaryParts).trim());

        String baseAddress = info.getAddress() == null ? "" : info.getAddress();
        pillAddressText.setText(baseAddress);

        String recipient = info.getName() == null ? getString(R.string.map_placeholder) : info.getName();
        String parcelSummary = buildParcelSummary(sameAddressGroup, info);
        String recipientLine = getString(R.string.map_recipient_format, recipient);
        if (!parcelSummary.isEmpty()) {
            recipientLine = recipientLine + "\n"
                    + getString(R.string.map_parcel_summary_format, parcelSummary);
        }
        pillRecipientText.setText(recipientLine);
        infoPill.setVisibility(View.VISIBLE);
        updateCollapsedHint();
    }

    private String buildParcelSummary(List<DeliveryInfo> focusGroup, DeliveryInfo fallback) {
        List<DeliveryInfo> source = (focusGroup == null || focusGroup.isEmpty())
                ? (fallback == null ? Collections.emptyList() : Collections.singletonList(fallback))
                : focusGroup;
        if (source.isEmpty())
            return "";

        List<String> labels = new ArrayList<>();
        for (DeliveryInfo item : source) {
            if (item == null)
                continue;
            String label = item.getRouteNumber();
            if (TextUtils.isEmpty(label)) {
                Long oid = item.getOrderId();
                label = oid == null ? "" : String.valueOf(oid);
            }
            if (TextUtils.isEmpty(label))
                continue;
            labels.add(label);
            if (labels.size() >= 5)
                break;
        }

        if (labels.isEmpty())
            return "";

        String summary = TextUtils.join(getString(R.string.map_parcel_summary_separator), labels);
        if (focusGroup != null && focusGroup.size() > labels.size()) {
            summary = summary + getString(R.string.map_parcel_summary_more);
        }
        return summary;
    }

    private void collapseInfoPill() {
        if (infoPill == null || collapsedInfoPill == null || currentPrimaryDelivery == null) {
            return;
        }
        infoPillCollapsed = true;
        infoPill.setVisibility(View.GONE);
        collapsedInfoPill.setVisibility(View.VISIBLE);
        updateCollapsedHint();
    }

    private void expandInfoPill() {
        if (infoPill == null)
            return;
        infoPillCollapsed = false;
        infoPill.setVisibility(View.VISIBLE);
        if (collapsedInfoPill != null) {
            collapsedInfoPill.setVisibility(View.GONE);
        }
    }

    private void hideInfoPillCompletely() {
        infoPillCollapsed = false;
        nearestDeliveries = Collections.emptyList();
        currentPrimaryDelivery = null;
        currentPrimaryKey = null;
        currentCloseDeliveries = Collections.emptyList();
        lastNearestDistanceMeters = Float.NaN;
        if (infoPill != null)
            infoPill.setVisibility(View.GONE);
        if (collapsedInfoPill != null)
            collapsedInfoPill.setVisibility(View.GONE);
        if (collapsedInfoText != null)
            collapsedInfoText.setText(R.string.map_no_deliveries);
    }

    private void updateCollapsedHint() {
        if (collapsedInfoText == null)
            return;
        if (currentPrimaryDelivery == null) {
            collapsedInfoText.setText(R.string.map_no_deliveries);
            return;
        }
        String label = currentPrimaryDelivery.getRouteNumber();
        if (TextUtils.isEmpty(label) && currentPrimaryDelivery.getOrderSn() != null) {
            label = currentPrimaryDelivery.getOrderSn();
        }
        if (TextUtils.isEmpty(label) && currentPrimaryDelivery.getOrderId() != null) {
            label = String.valueOf(currentPrimaryDelivery.getOrderId());
        }
        if (TextUtils.isEmpty(label)) {
            label = getString(R.string.map_current_parcel);
        }
        String address = currentPrimaryDelivery.getAddress();
        if (TextUtils.isEmpty(address)) {
            address = getString(R.string.map_location_unknown);
        }
        collapsedInfoText.setText(getString(R.string.map_collapsed_hint_format, label, address));
    }

    private String buildPrimaryKey(@Nullable DeliveryInfo info) {
        if (info == null)
            return "";
        if (info.getOrderId() != null) {
            return "ID-" + info.getOrderId();
        }
        if (!TextUtils.isEmpty(info.getOrderSn())) {
            return "SN-" + info.getOrderSn();
        }
        if (!TextUtils.isEmpty(info.getRouteNumber())) {
            return "ROUTE-" + info.getRouteNumber();
        }
        return String.valueOf(info.hashCode());
    }

    private boolean isCurrentPrimaryStillPending() {
        if (currentPrimaryKey == null || currentMapDeliveries == null)
            return false;
        for (DeliveryInfo info : currentMapDeliveries) {
            if (info == null)
                continue;
            if (currentPrimaryKey.equals(buildPrimaryKey(info))) {
                return true;
            }
        }
        return false;
    }

    private void updateInfoPillBottomMargin(int systemInsetPx) {
        applyBottomMargin(infoPill, systemInsetPx);
        applyBottomMargin(collapsedInfoPill, systemInsetPx);
    }

    private void applyBottomMargin(@Nullable View target, int systemInsetPx) {
        if (target == null)
            return;
        ViewGroup.LayoutParams params = target.getLayoutParams();
        if (!(params instanceof ViewGroup.MarginLayoutParams))
            return;
        int controlInset = estimateControlInset();
        int desiredBottom = infoPillBaseBottomMarginPx + Math.max(systemInsetPx, controlInset);
        ViewGroup.MarginLayoutParams lp = (ViewGroup.MarginLayoutParams) params;
        if (lp.bottomMargin != desiredBottom) {
            lp.bottomMargin = desiredBottom;
            target.setLayoutParams(lp);
        }
    }

    private int estimateControlInset() {
        int extra = 0;
        extra = Math.max(extra, controlInsetFor(collapsedInfoPill));
        return extra;
    }

    private void showCamera(DeliveryInfo info) {
        Intent intent = new Intent(requireActivity(), CameraActivity.class);
        intent.putExtra("order_id", info.getOrderId() == null ? -1L : info.getOrderId());
        intent.putExtra("latitude", info.getLatitude());
        intent.putExtra("longitude", info.getLongitude());
        intent.putExtra("route_number", info.getRouteNumber());
        intent.putExtra("tracking_id", info.getOrderSn());
        intent.putExtra("address", info.getAddress());
        intent.putExtra("unit_number", info.getUnitNumber());
        intent.putExtra("civil_number", info.getCivilNumber());
        intent.putExtra("customer_name", info.getName());
        intent.putExtra("phone", info.getPhone());
        startActivity(intent);
    }

    private void focusOnDelivery(DeliveryInfo info) {
        if (googleMap == null)
            return;
        LatLng target = new LatLng(info.getLatitude(), info.getLongitude());
        googleMap.animateCamera(CameraUpdateFactory.newLatLngZoom(target, 17f));
        currentPrimaryDelivery = info;
        currentPrimaryKey = buildPrimaryKey(info);
        showInfoPill(info, Collections.singletonList(info));
    }

    private void getLocation() {
        mSmartLocationManager = SmartLocationManager.getInstance(requireContext());
        if (mSmartLocationManager != null) {
            applyPerfBalance(profileManager != null ? profileManager.getPerfBalance() : 0f);
            mSmartLocationManager.addLocationUpdateListener(this);
            mSmartLocationManager.startLocationUpdates();
        }
        if (cameraController != null) {
            cameraController.setSmartLocationManager(mSmartLocationManager);
        }
    }

    private void centerOnMyLocation(boolean resumeAutoFollow) {
        if (googleMap == null)
            return;
        Location loc = getBestAvailableLocation();
        if (loc == null) {
            Toast.makeText(requireContext(), R.string.map_no_location, Toast.LENGTH_SHORT).show();
            return;
        }
        if (resumeAutoFollow) {
            clearAutoFollowPause();
        } else {
            long hold = (currentRegionState == InfoPillProximityController.RegionState.INSIDE)
                    ? INSIDE_MANUAL_CENTER_HOLD_MS
                    : MANUAL_CENTER_HOLD_MS;
            manualCenterHoldUntilMs = SystemClock.uptimeMillis() + hold;
        }
        // 用户手动回中心，强制提频一次，避免静止锁导致无首fix
        if (mSmartLocationManager != null) {
            try {
                mSmartLocationManager.requestBoostForce(8_000L, "manual_center");
            } catch (Throwable ignore) {
            }
        }
        if (cameraController != null) {
            cameraController.resetHasCenteredOnUser();
            float distanceMeters;
            if (!Float.isNaN(lastNearestDistanceMeters)) {
                distanceMeters = lastNearestDistanceMeters;
            } else if (!Float.isNaN(lastValidNearestDistanceMeters)) {
                distanceMeters = lastValidNearestDistanceMeters;
            } else {
                // Unknown distance: avoid smart-zoom jumps, keep cruising zoom.
                distanceMeters = -1f;
            }
            logD("centerOnMyLocation zoomFromDist=" + distanceMeters);
            float zoom = cameraController.computePreferredZoomForManualCenter(
                    loc,
                    lastMovementState,
                    distanceMeters,
                    getRealMapVisibleHeightPx());
            boolean alignToCenter = !resumeAutoFollow
                    || currentRegionState == InfoPillProximityController.RegionState.INSIDE;
            boolean insideZone = currentRegionState == InfoPillProximityController.RegionState.INSIDE;
            cameraController.resumeFollowNow(loc, lastMovementState, zoom, alignToCenter, insideZone);
        }
    }

    // import: CameraUpdate, CameraUpdateFactory, LatLng, SystemClock, @NonNull

    private void toggleMapType(ImageButton btn) {
        if (googleMap == null)
            return;
        int type = googleMap.getMapType();
        if (type == GoogleMap.MAP_TYPE_NORMAL) {
            googleMap.setMapType(GoogleMap.MAP_TYPE_SATELLITE);
        } else {
            googleMap.setMapType(GoogleMap.MAP_TYPE_NORMAL);
        }
    }

    private void toggleNavigationMode() {
        navigationModeEnabled = !navigationModeEnabled;
        if (navigationModeEnabled) {
            clearAutoFollowPause();
            hideResumeFollowButton();
        }
        if (cameraController != null) {
            cameraController.setNavigationModeEnabled(navigationModeEnabled);
            if (navigationModeEnabled) {
                cameraController.resetHasCenteredOnUser();
            }
        }
        updateNavigationMenuItem();
        String msg = getString(navigationModeEnabled
                ? R.string.map_nav_mode_enabled
                : R.string.map_nav_mode_disabled);
        Toast.makeText(requireContext(), msg, Toast.LENGTH_SHORT).show();
    }

    private void updateNavigationMenuItem() {
        if (mToolbar == null)
            return;
        android.view.Menu menu = mToolbar.getMenu();
        if (menu == null)
            return;
        MenuItem navItem = menu.findItem(R.id.menu_nav_mode);
        if (navItem == null)
            return;
        navItem.setIcon(navigationModeEnabled ? R.drawable.ic_nav_mode_on : R.drawable.ic_nav_mode_off);
        navItem.setTitle(navigationModeEnabled
                ? R.string.map_nav_mode_exit
                : R.string.map_nav_mode_enter);
    }

    /** Hidden developer shortcut: double-tap the toolbar to open the panel. */
    private void onToolbarTapped() {
        final long now = SystemClock.elapsedRealtime();
        if (now - lastToolbarTapMs <= DEV_DOUBLE_TAP_WINDOW_MS) {
            lastToolbarTapMs = 0L;
            try {
                showDeveloperPanel();
            } catch (Throwable t) {
                try {
                    FileLog.getInstance().error(TAG, "Open developer panel failed", t);
                } catch (Throwable ignore) {
                }
                Toast.makeText(requireContext(), R.string.dev_panel_unavailable, Toast.LENGTH_SHORT).show();
            }
        } else {
            lastToolbarTapMs = now;
        }
    }

    /**
     * Show DeveloperPanelBottomSheet; requires the Map fragment as host for runtime
     * config apply.
     */
    private void showDeveloperPanel() {
        DeveloperPanelBottomSheet sheet = DeveloperPanelBottomSheet.newInstance();
        sheet.show(getChildFragmentManager(), "DeveloperPanelBottomSheet");
    }

    @Override
    public void onLocationUpdate(Location location, SmartLocationManager.MovementState state) {
        long seq = ++locationUpdateSeq;
        long rawElapsedMs = getElapsedRealtimeMsSafe(location);
        logD("onLocationUpdate loc=" + location.getLatitude() + "," + location.getLongitude()
                + ", mv=" + state
                + ", items=" + (currentMapDeliveries == null ? 0 : currentMapDeliveries.size())
                + ", pausedByGesture=" + autoFollowPausedByGesture
                + ", seq=" + seq
                + ", rawElapsedMs=" + rawElapsedMs);
        Telemetry.counter("ui.onLocationUpdate");
        // Capture previous movement state BEFORE overwriting lastMovementState
        final SmartLocationManager.MovementState prevState = lastMovementState;
        // 通知 ViewModel 更新定位
        mapViewModel.updateMyLocation(location);
        mLastLocation = location;
        lastMovementState = state;
        updateForegroundTracking();
        if (googleMap == null || cameraController == null)
            return;
        // --- Map-layer UI effective location (quality gating + smoothing) ---
        final boolean isDrivingNow = (state == SmartLocationManager.MovementState.SLOW_DRIVING
                || state == SmartLocationManager.MovementState.NORMAL_DRIVING);

        Location predicted = null;
        if (mSmartLocationManager != null) {
            predicted = mSmartLocationManager.getPredictedLocation();
        }

        final boolean rawGood = isGoodFixForUi(location, state) && !isStaleForUi(location, state);
        if (rawGood) {
            // Cache last good raw fix for later fallback
            mLastGoodLocation = new Location(location);
            mLastGoodUptimeMs = SystemClock.uptimeMillis();
        }

        // Decide what the map UI should use for marker/camera.
        Location effective = chooseEffectiveLocationForUi(location, predicted, state);
        mLastEffectiveUiLocation = effective;
        long effElapsedMs = getElapsedRealtimeMsSafe(effective);
        logD("locUpdate#" + seq + " effective src=" + mLastEffectiveSource
                + " effElapsedMs=" + effElapsedMs
                + " rawGood=" + rawGood
                + " predicted=" + (predicted != null));

        // If raw fix is poor/stale, request a short boost to recover accuracy quickly.
        if (!rawGood && mSmartLocationManager != null) {
            try {
                mSmartLocationManager.requestBoost(8_000L, "poor_fix");
            } catch (Throwable ignore) {
            }
        }

        updateMyLocationMarker(effective);

        // 1) 交给 Proximity 决策 InfoPill 的显隐/更新（Top-3：远距降采样 + 区域通勤极简）
        try {
            if (proximityCoordinator != null) {
                if (shouldEvaluateProximity(effective, state)) {
                    Telemetry.counter("ui.proximityEval");
                    proximityCoordinator.onLocation(effective, state, currentMapDeliveries);
                    // 进入/更新后：若上次手动折叠且仍锁定同一目标，UI 侧增加一点回差以防抖（兜底）
                    if (infoPillCollapsed && currentPrimaryDelivery != null
                            && !Float.isNaN(lastNearestDistanceMeters)) {
                        float unlock = INFO_PILL_PROXIMITY_THRESHOLD_METERS + LOCK_HYSTERESIS_EXTRA_M;
                        if (lastNearestDistanceMeters > unlock) {
                            // 超过回差则允许重新弹出（交给策略层），这里仅清除折叠标记
                            infoPillCollapsed = false;
                        }
                    }
                } else {
                    // 被采样器跳过时，仍可让相机跟随逻辑独立运行（下方执行）
                }
            }
        } catch (Throwable ignore) {
        }

        if (proximityController != null) {
            currentRegionState = proximityController.getRegionState();
        } else {
            currentRegionState = InfoPillProximityController.RegionState.IN_TRANSIT;
        }

        // ✅ 修复进入驾驶时zoom突变：检测状态切换（必须使用 prevState）
        boolean enteringDriving = (prevState == SmartLocationManager.MovementState.STATIONARY
                || prevState == SmartLocationManager.MovementState.WALKING)
                && (state == SmartLocationManager.MovementState.SLOW_DRIVING
                        || state == SmartLocationManager.MovementState.NORMAL_DRIVING);

        if (enteringDriving && cameraController != null) {
            long now = System.currentTimeMillis();
            cameraController.setDrivingModeStartTime(now);
            logD("Entering driving mode - set timestamp: " + now);
        }

        maybeRequestInsideBoost(state);
        // 未扫描包裹视图下不自动恢复/拉回相机，保持用户查看列表的视角
        if (currentMode != DataMode.UNSCANNED) {
            maybeRecoverAutoFollow(state);
        }

        // 未扫描模式：仅更新标记/信息，不做自动跟随
        if (currentMode == DataMode.UNSCANNED && !navigationModeEnabled) {
            return;
        }

        // 2) 构建相机上下文并委托给 CameraFollowController
        float distanceMeters = Float.isNaN(lastNearestDistanceMeters)
                ? (Float.isNaN(lastValidNearestDistanceMeters) ? -1f : lastValidNearestDistanceMeters)
                : lastNearestDistanceMeters;
        boolean insideZone = currentRegionState == InfoPillProximityController.RegionState.INSIDE;
        boolean manualHold = isManualCenterHoldActive();

        long stationaryDurationMs = 0L;
        if (mSmartLocationManager != null) {
            stationaryDurationMs = mSmartLocationManager.getStationaryDurationMs();
        }
        CameraUpdateContext cameraContext = new CameraUpdateContext(
                effective,
                state,
                effective.hasAccuracy() ? effective.getAccuracy() : Float.NaN,
                Math.abs(System.currentTimeMillis() - effective.getTime()),
                mLastEffectiveSource,
                effective.hasSpeed() ? effective.getSpeed() : Float.NaN,
                effective.hasBearing() ? effective.getBearing() : Float.NaN,
                mSmartLocationManager != null ? mSmartLocationManager.getCurrentHeading() : Float.NaN,
                distanceMeters,
                stationaryDurationMs,
                currentMapDeliveries,
                insideZone,
                getRealMapVisibleHeightPx(),
                isUserInteracting,
                autoFollowPausedByGesture,
                manualHold,
                navigationModeEnabled);

        // 导航模式下开车时的震动反馈
        boolean isDriving = state == SmartLocationManager.MovementState.SLOW_DRIVING
                || state == SmartLocationManager.MovementState.NORMAL_DRIVING;

        if (navigationModeEnabled && isDriving && cameraController != null) {
            Utils.vibrate(requireContext(), 30);
        }

        pendingCameraContext = cameraContext;
        cameraUpdateHandler.removeCallbacks(cameraUpdateRunnable);
        cameraUpdateHandler.postDelayed(cameraUpdateRunnable, uiTickIntervalMs);
    }

    private void updateUiTickInterval() {
        if (cameraController != null) {
            uiTickIntervalMs = Math.max(200L, cameraController.getUiTickMs());
        } else {
            uiTickIntervalMs = 250L;
        }
    }

    private void scheduleUiTick() {
        cameraUpdateHandler.removeCallbacks(uiTickRunnable);
        cameraUpdateHandler.postDelayed(uiTickRunnable, uiTickIntervalMs);
    }

    @Nullable
    private CameraUpdateContext buildCameraContextForTick() {
        if (cameraController == null) {
            return null;
        }
        Location base = mLastEffectiveUiLocation != null ? mLastEffectiveUiLocation : mLastLocation;
        if (base == null) {
            return null;
        }
        Location predicted = mSmartLocationManager != null ? mSmartLocationManager.getPredictedLocation() : null;
        Location effective = predicted != null ? predicted : base;
        CameraUpdateContext.LocationSource source = predicted != null
                ? CameraUpdateContext.LocationSource.PREDICTED
                : CameraUpdateContext.LocationSource.RAW;

        float distanceMeters = Float.isNaN(lastNearestDistanceMeters)
                ? (Float.isNaN(lastValidNearestDistanceMeters) ? -1f : lastValidNearestDistanceMeters)
                : lastNearestDistanceMeters;
        boolean insideZone = currentRegionState == InfoPillProximityController.RegionState.INSIDE;
        boolean manualHold = isManualCenterHoldActive();
        long stationaryDurationMs = mSmartLocationManager != null ? mSmartLocationManager.getStationaryDurationMs() : 0L;

        return new CameraUpdateContext(
                effective,
                lastMovementState,
                effective.hasAccuracy() ? effective.getAccuracy() : Float.NaN,
                Math.abs(System.currentTimeMillis() - effective.getTime()),
                source,
                effective.hasSpeed() ? effective.getSpeed() : Float.NaN,
                effective.hasBearing() ? effective.getBearing() : Float.NaN,
                mSmartLocationManager != null ? mSmartLocationManager.getCurrentHeading() : Float.NaN,
                distanceMeters,
                stationaryDurationMs,
                currentMapDeliveries,
                insideZone,
                getRealMapVisibleHeightPx(),
                isUserInteracting,
                autoFollowPausedByGesture,
                manualHold,
                navigationModeEnabled);
    }

    private int controlInsetFor(@Nullable View control) {
        if (control == null || control.getVisibility() != View.VISIBLE)
            return 0;
        int height = control.getHeight();
        if (height <= 0) {
            control.measure(View.MeasureSpec.UNSPECIFIED, View.MeasureSpec.UNSPECIFIED);
            height = control.getMeasuredHeight();
        }
        ViewGroup.LayoutParams lp = control.getLayoutParams();
        int margin = 0;
        if (lp instanceof ViewGroup.MarginLayoutParams) {
            margin = ((ViewGroup.MarginLayoutParams) lp).bottomMargin;
        }
        return height + margin;
    }

    private void onResumeFollowClicked() {
        logD("resume-follow clicked -> clear pause & center");
        centerOnMyLocation(true); // resume auto-follow explicitly
    }

    private void pauseAutoFollowByGesture() {
        if (navigationModeEnabled) {
            logD("navigation mode active -> ignore pause gesture");
            return;
        }
        if (autoFollowPausedByGesture) {
            // 用户持续操作时刷新暂停时间，避免过早自动恢复
            autoFollowPausedAtMs = SystemClock.uptimeMillis();
            return;
        }
        autoFollowPausedByGesture = true;
        autoFollowPausedAtMs = SystemClock.uptimeMillis();
        showResumeFollowButton(); // 像 Google Maps 一样在用户干预时显示
        logD("auto-follow paused by user gesture");
        if (mSmartLocationManager != null) {
            mSmartLocationManager.setUiFollowActive(false);
        }
        updateForegroundTracking();
    }

    private void clearAutoFollowPause() {
        autoFollowPausedByGesture = false;
        isUserInteracting = false;
        manualCenterHoldUntilMs = 0L;
        autoFollowPausedAtMs = 0L;
        hideResumeFollowButton(); // 点击“重新跟随”后隐藏
        logD("auto-follow pause cleared");
        if (mSmartLocationManager != null) {
            mSmartLocationManager.setUiFollowActive(true);
        }
        updateForegroundTracking();
    }

    private void clearCommuteSuppression() {
        commuteSuppressUntilMs = 0L;
        commuteAnchorLatLng = null;
    }

    /** 精准计算地图真实可用高度（已完美适配当前布局） */
    private int getRealMapVisibleHeightPx() {
        if (mapView == null || mapView.getHeight() <= 0) {
            return (int) (800 * getResources().getDisplayMetrics().density);
        }

        int fullHeight = mapView.getHeight();

        // Toolbar 高度
        int toolbarHeight = mToolbar != null ? mToolbar.getHeight()
                : getResources().getDimensionPixelSize(
                        com.google.android.material.R.dimen.m3_appbar_expanded_title_margin_bottom);

        // InfoPill 高度（展开或收起）
        int infoPillHeight = 0;
        View currentPill = (infoPill != null && infoPill.getVisibility() == View.VISIBLE) ? infoPill
                : (collapsedInfoPill != null && collapsedInfoPill.getVisibility() == View.VISIBLE) ? collapsedInfoPill
                        : null;
        if (currentPill != null) {
            infoPillHeight = currentPill.getHeight();
            if (infoPillHeight == 0) {
                // 无具体高度资源，兜底使用已有 margin + 额外 40dp
                int extra = (int) (40 * getResources().getDisplayMetrics().density);
                infoPillHeight = infoPillBaseBottomMarginPx + extra;
            }
        }

        // BottomNavigationView 高度
        View bottomNav = requireActivity().findViewById(R.id.bottom_nav);
        int bottomNavHeight = 0;
        if (bottomNav != null && bottomNav.getVisibility() == View.VISIBLE) {
            bottomNavHeight = bottomNav.getHeight();
            if (bottomNavHeight == 0) {
                bottomNavHeight = (int) (56 * getResources().getDisplayMetrics().density);
            }
        }

        // 系统手势导航栏高度
        int navigationBarHeight = 0;
        WindowInsets insets = mapView.getRootWindowInsets();
        if (insets != null) {
            navigationBarHeight = WindowInsetsCompat.toWindowInsetsCompat(insets)
                    .getInsets(WindowInsetsCompat.Type.navigationBars()).bottom;
        }

        int available = fullHeight - toolbarHeight - infoPillHeight - bottomNavHeight - navigationBarHeight;

        if (available < fullHeight * 0.58f) {
            available = (int) (fullHeight * 0.58f);
        }
        return Math.max(available, 400);
    }

    private void showResumeFollowButton() {
        if (btnResumeFollow == null || navigationModeEnabled)
            return;
        // Ensure the button is above MapView/InfoPill and can receive clicks
        try {
            btnResumeFollow.bringToFront();
        } catch (Throwable ignore) {
        }
        try {
            btnResumeFollow.setClickable(true);
        } catch (Throwable ignore) {
        }
        try {
            btnResumeFollow.setElevation(10f);
        } catch (Throwable ignore) {
        }
        try {
            btnResumeFollow.setTranslationZ(24f);
        } catch (Throwable ignore) {
        }
        // Only animate when transitioning from GONE/INVISIBLE to VISIBLE
        if (btnResumeFollow.getVisibility() != View.VISIBLE) {
            btnResumeFollow.setAlpha(0f);
            btnResumeFollow.setVisibility(View.VISIBLE);
            btnResumeFollow.animate().alpha(1f).setDuration(180).start();
        }
    }

    private void hideResumeFollowButton() {
        if (btnResumeFollow == null || btnResumeFollow.getVisibility() != View.VISIBLE)
            return;
        btnResumeFollow.animate()
                .alpha(0f)
                .setDuration(180)
                .withEndAction(() -> {
                    if (btnResumeFollow != null) {
                        btnResumeFollow.setVisibility(View.GONE);
                        btnResumeFollow.setAlpha(1f);
                    }
                })
                .start();
    }

    @Nullable
    private Location getBestAvailableLocation() {
        Location loc = null;
        if (mSmartLocationManager != null) {
            loc = getFreshLocationCandidate(mSmartLocationManager.getPredictedLocation());
            if (loc == null) {
                loc = getFreshLocationCandidate(mSmartLocationManager.getLastSmoothedLocation());
            }
        }
        if (loc == null) {
            loc = getFreshLocationCandidate(mLastLocation);
        }
        if (loc == null) {
            requestFreshLocation();
        }
        return loc;
    }

    private boolean isManualCenterHoldActive() {
        if (manualCenterHoldUntilMs == 0L)
            return false;
        boolean active = SystemClock.uptimeMillis() < manualCenterHoldUntilMs;
        if (!active) {
            manualCenterHoldUntilMs = 0L;
        }
        return active;
    }

    private void requestFreshLocation() {
        if (mSmartLocationManager == null)
            return;
        try {
            mSmartLocationManager.requestBoost(8_000L, "resume_follow");
        } catch (Throwable ignore) {
        }
    }

    private void maybeRecoverAutoFollow(@NonNull SmartLocationManager.MovementState state) {
        if (!autoFollowPausedByGesture)
            return;
        if (navigationModeEnabled) {
            logD("navigation mode active -> auto-resume follow");
            clearAutoFollowPause();
            if (cameraController != null) {
                cameraController.resetHasCenteredOnUser();
            }
            return;
        }
        if (autoFollowPausedAtMs == 0L)
            return;
        boolean moving = state == SmartLocationManager.MovementState.SLOW_DRIVING
                || state == SmartLocationManager.MovementState.NORMAL_DRIVING
                || state == SmartLocationManager.MovementState.WALKING;
        if (!moving)
            return;
        long now = SystemClock.uptimeMillis();
        if (now - autoFollowPausedAtMs < AUTO_FOLLOW_PAUSE_MS) {
            return;
        }
        logD("auto-follow paused >" + AUTO_FOLLOW_PAUSE_MS + "ms during drive/walk -> auto-resume");
        clearAutoFollowPause(); // 内部会隐藏“重新跟随”按钮
        if (cameraController != null) {
            cameraController.resetHasCenteredOnUser();
        }
    }

    private void maybeRequestInsideBoost(@NonNull SmartLocationManager.MovementState state) {
        if (!insideZoneBoostEnabled)
            return;
        if (currentRegionState != InfoPillProximityController.RegionState.INSIDE)
            return;
        if (state == SmartLocationManager.MovementState.NORMAL_DRIVING)
            return;
        if (mSmartLocationManager == null)
            return;
        long now = SystemClock.uptimeMillis();
        if (now - lastInsideBoostMs < INSIDE_BOOST_COOLDOWN_MS)
            return;
        try {
            mSmartLocationManager.requestBoost(INSIDE_BOOST_DURATION_MS, "inside_zone");
            lastInsideBoostMs = now;
            logD("inside boost requested for " + INSIDE_BOOST_DURATION_MS + " ms");
        } catch (Throwable ignore) {
        }
    }

    private void requestImmediateProximityRefresh() {
        if (proximityCoordinator == null || mLastLocation == null)
            return;
        forceProximityEvaluation = true;
        logD("requestImmediateProximityRefresh() -> forcing evaluate at loc=" + mLastLocation.getLatitude() + ","
                + mLastLocation.getLongitude());
        proximityCoordinator.onLocation(mLastLocation, lastMovementState, currentMapDeliveries);

        // Force immediate camera update to apply new zoom based on new nearest package
        // This bypasses the GPS update interval (which might be long if stationary)
        onLocationUpdate(mLastLocation, lastMovementState);
    }

    @Nullable
    private Location getFreshLocationCandidate(@Nullable Location candidate) {
        if (candidate == null)
            return null;
        if (!isLocationFresh(candidate))
            return null;
        return new Location(candidate);
    }

    private boolean isLocationFresh(@Nullable Location loc) {
        if (loc == null)
            return false;
        long ageMs = Math.abs(System.currentTimeMillis() - loc.getTime());
        return ageMs <= MAX_LOCATION_AGE_MS;
    }

    /** 计算两点之间的直线距离（米）。 */
    private static float distanceBetweenMeters(@NonNull LatLng a, @NonNull LatLng b) {
        float[] out = new float[1];
        android.location.Location.distanceBetween(a.latitude, a.longitude, b.latitude, b.longitude, out);
        return out[0];
    }

    private void updateMyLocationMarker(@NonNull Location loc) {
        if (googleMap == null)
            return;
        if (myLocationIcon == null) {
            myLocationIcon = BitmapDescriptorFactory.fromBitmap(createMyLocationBitmap());
        }

        LatLng to = new LatLng(loc.getLatitude(), loc.getLongitude());
        float toBearing = loc.hasBearing() ? loc.getBearing() : Float.NaN;

        if (myLocationMarker == null) {
            MarkerOptions opts = new MarkerOptions()
                    .position(to)
                    .anchor(0.5f, 0.5f)
                    .flat(true)
                    .zIndex(1000f)
                    .icon(myLocationIcon);
            myLocationMarker = googleMap.addMarker(opts);
            lastMarkerLatLng = to;
            lastMarkerAnimUptime = SystemClock.uptimeMillis();
            if (!Float.isNaN(toBearing)) {
                lastMarkerBearing = toBearing;
                myLocationMarker.setRotation(toBearing);
            }
            return;
        }

        // Cancel previous animation
        try {
            if (myLocAnimator != null) {
                myLocAnimator.cancel();
                myLocAnimator = null;
            }
        } catch (Throwable ignore) {
        }

        LatLng from = myLocationMarker.getPosition();
        if (from == null) {
            myLocationMarker.setPosition(to);
            if (!Float.isNaN(toBearing)) {
                myLocationMarker.setRotation(toBearing);
            }
            lastMarkerLatLng = to;
            lastMarkerAnimUptime = SystemClock.uptimeMillis();
            return;
        }

        // Duration: follow update cadence, clamp to [220, 800] ms
        long nowUptime = SystemClock.uptimeMillis();
        long dt = (lastMarkerAnimUptime == 0L) ? 350L : (nowUptime - lastMarkerAnimUptime);
        long duration = Math.max(220L, Math.min(800L, dt));
        lastMarkerAnimUptime = nowUptime;

        // Bearing interpolation (handle wrap-around)
        final float startBearing = Float.isNaN(lastMarkerBearing)
                ? (myLocationMarker.getRotation())
                : lastMarkerBearing;
        final float endBearing = Float.isNaN(toBearing) ? startBearing : toBearing;

        myLocAnimator = ValueAnimator.ofFloat(0f, 1f);
        myLocAnimator.setInterpolator(new LinearInterpolator());
        myLocAnimator.setDuration(duration);
        myLocAnimator.addUpdateListener(anim -> {
            float t = (float) anim.getAnimatedValue();
            double lat = from.latitude + (to.latitude - from.latitude) * t;
            double lng = from.longitude + (to.longitude - from.longitude) * t;
            try {
                myLocationMarker.setPosition(new LatLng(lat, lng));
            } catch (Throwable ignore) {
            }

            // rotation
            float b0 = startBearing;
            float b1 = endBearing;
            float delta = b1 - b0;
            if (Math.abs(delta) > 180f) {
                delta -= Math.signum(delta) * 360f;
            }
            float b = b0 + delta * t;
            if (b < 0f)
                b += 360f;
            try {
                myLocationMarker.setRotation(b);
            } catch (Throwable ignore) {
            }
        });
        try {
            myLocAnimator.start();
        } catch (Throwable ignore) {
            myLocationMarker.setPosition(to);
            if (!Float.isNaN(toBearing)) {
                myLocationMarker.setRotation(toBearing);
            }
        }

        lastMarkerLatLng = to;
        if (!Float.isNaN(toBearing)) {
            lastMarkerBearing = toBearing;
        }
    }

    private boolean isGoodFixForUi(@NonNull Location loc, @NonNull SmartLocationManager.MovementState state) {
        if (!loc.hasAccuracy())
            return false;
        float acc = loc.getAccuracy();
        boolean driving = (state == SmartLocationManager.MovementState.SLOW_DRIVING
                || state == SmartLocationManager.MovementState.NORMAL_DRIVING);
        float threshold = driving ? GOOD_ACCURACY_DRIVING_M : GOOD_ACCURACY_WALKING_M;
        return acc > 0f && acc <= threshold;
    }

    private boolean isStaleForUi(@NonNull Location loc, @NonNull SmartLocationManager.MovementState state) {
        long now = System.currentTimeMillis();
        long age = Math.abs(now - loc.getTime());
        boolean driving = (state == SmartLocationManager.MovementState.SLOW_DRIVING
                || state == SmartLocationManager.MovementState.NORMAL_DRIVING);
        long threshold = driving ? STALE_LOCATION_MS_DRIVING : STALE_LOCATION_MS_OTHER;
        return age > threshold;
    }

    private boolean isPredictedUsableForUi(@Nullable Location predicted) {
        if (predicted == null)
            return false;
        long now = System.currentTimeMillis();
        long age = Math.abs(now - predicted.getTime());
        return age <= MAX_PREDICTED_UI_AGE_MS;
    }

    private long getElapsedRealtimeMsSafe(@Nullable Location loc) {
        if (loc == null)
            return -1L;
        try {
            return loc.getElapsedRealtimeNanos() / 1_000_000L;
        } catch (Throwable t) {
            return -1L;
        }
    }

    private void applyPerfBalance(float balance) {
        float clamped = balance;
        if (clamped < 0f) clamped = 0f;
        if (clamped > 1f) clamped = 1f;
        String perfMode = clamped >= 0.5f ? "ECO" : "REALTIME";
        logD("perf balance -> value=" + clamped + " mode=" + perfMode);

        StrategyConfig.applyPerfBalance(clamped);
        if (mSmartLocationManager != null) {
            mSmartLocationManager.setLocationPolicy(new BlendedLocationPolicy(clamped));
        }
        if (cameraController != null) {
            cameraController.setFollowProfile(clamped >= 0.5f
                    ? CameraFollowController.FollowProfile.BASIC
                    : CameraFollowController.FollowProfile.STANDARD);
            cameraController.applyFollowPolicy(new BlendedFollowPolicy(clamped));
        }
        updateUiTickInterval();
    }

    @NonNull
    private Location chooseEffectiveLocationForUi(@NonNull Location raw,
            @Nullable Location predicted,
            @NonNull SmartLocationManager.MovementState state) {
        final boolean driving = (state == SmartLocationManager.MovementState.SLOW_DRIVING
                || state == SmartLocationManager.MovementState.NORMAL_DRIVING);

        final boolean rawGood = isGoodFixForUi(raw, state) && !isStaleForUi(raw, state);

        if (driving) {
            if (isPredictedUsableForUi(predicted)) {
                mLastEffectiveSource = CameraUpdateContext.LocationSource.PREDICTED;
                return new Location(predicted);
            }
            if (rawGood) {
                mLastEffectiveSource = CameraUpdateContext.LocationSource.RAW;
                return new Location(raw);
            }
            if (mLastGoodLocation != null && isLocationFresh(mLastGoodLocation)) {
                mLastEffectiveSource = CameraUpdateContext.LocationSource.LAST_GOOD;
                return new Location(mLastGoodLocation);
            }
            mLastEffectiveSource = CameraUpdateContext.LocationSource.RAW;
            return new Location(raw);
        }

        // Walking/Stationary: prefer predicted when fresh (smoother), otherwise use
        // raw.
        if (isPredictedUsableForUi(predicted)) {
            mLastEffectiveSource = CameraUpdateContext.LocationSource.PREDICTED;
            return new Location(predicted);
        }
        mLastEffectiveSource = CameraUpdateContext.LocationSource.RAW;
        return new Location(raw);
    }

    private Bitmap createMyLocationBitmap() {
        Drawable d = AppCompatResources.getDrawable(requireContext(), R.drawable.ic_my_location_marker);
        int sizePx = (int) (36 * getResources().getDisplayMetrics().density);
        Bitmap b = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888);
        if (d == null)
            return b;
        Canvas c = new Canvas(b);
        d.setBounds(0, 0, sizePx, sizePx);
        d.draw(c);
        return b;
    }

    /**
     * Top-3：决定本次是否需要触发 Proximity 评估。
     * 【已修改】增加社区短途保护 + 步行立即解锁，彻底解决社区派送不跟手问题。
     */
    private boolean shouldEvaluateProximity(@NonNull Location loc, @NonNull SmartLocationManager.MovementState state) {
        final long now = System.currentTimeMillis();

        // 0. 强制评估标志（例如刚送完一单，需要立即刷新）
        if (forceProximityEvaluation) {
            forceProximityEvaluation = false;
            lastProximityEvalMs = now;
            return true;
        }

        // ==================================================================================
        // 【核心修改区 START】
        // ==================================================================================

        // 1. 社区短途保护：如果离最近的包裹很近 (< 500米)，直接允许评估，绝不抑制！
        boolean isShortDistance = !Float.isNaN(lastNearestDistanceMeters) && lastNearestDistanceMeters < 500f;

        // 2. 状态保护：如果是步行或停车，立即解锁。
        boolean isSlowOrStopped = (state == SmartLocationManager.MovementState.STATIONARY
                || state == SmartLocationManager.MovementState.WALKING);

        if (isShortDistance || isSlowOrStopped) {
            // 立即清除抑制状态，确保 InfoPill 和 Zoom 能响应
            commuteAnchorLatLng = null;
            commuteSuppressUntilMs = 0L;

            // 依然遵循最小采样间隔(800ms)，防止 UI 刷新过快闪烁
            if (now - lastProximityEvalMs < NEAR_SAMPLE_MIN_INTERVAL_MS) {
                return false;
            }
            lastProximityEvalMs = now;
            return true;
        }
        // ==================================================================================
        // 【核心修改区 END】
        // ==================================================================================

        // --- 以下是长距离驾驶(>500m)的抑制逻辑 ---

        // 3. 若处于通勤抑制窗口，跳过
        if (now < commuteSuppressUntilMs) {
            logD("proximity skip: commute-suppressed until=" + commuteSuppressUntilMs);
            return false;
        }

        // 4. 采样间隔检查
        final long minInterval = (Float.isNaN(lastNearestDistanceMeters)
                || lastNearestDistanceMeters > FAR_DISTANCE_SAMPLE_THRESHOLD_M)
                        ? FAR_SAMPLE_MIN_INTERVAL_MS
                        : NEAR_SAMPLE_MIN_INTERVAL_MS;
        if (now - lastProximityEvalMs < minInterval) {
            return false;
        }

        // 5. 区域通勤极简：当驾驶且仍未越过切换距离，则进入短时抑制窗口
        if (state == SmartLocationManager.MovementState.SLOW_DRIVING
                || state == SmartLocationManager.MovementState.NORMAL_DRIVING) {
            LatLng here = new LatLng(loc.getLatitude(), loc.getLongitude());
            if (commuteAnchorLatLng == null) {
                commuteAnchorLatLng = here;
            } else {
                float moved = distanceBetweenMeters(here, commuteAnchorLatLng);
                // 只有移动距离很小，才抑制
                if (moved < COMMUTE_SWITCH_M) {
                    if (now >= commuteSuppressUntilMs) {
                        commuteSuppressUntilMs = now + COMMUTE_SUPPRESS_MS;
                        logD("proximity commute-suppress start for " + COMMUTE_SUPPRESS_MS + " ms (moved=" + moved
                                + ")");
                    }
                    return false;
                }
                // 越过切换距离，更新锚点
                if (moved >= COMMUTE_SWITCH_M) {
                    commuteAnchorLatLng = here;
                    commuteSuppressUntilMs = 0L;
                }
            }
        }

        lastProximityEvalMs = now;
        return true;
    }

    @Override
    public void onResume() {
        super.onResume();
        if (mapView != null)
            mapView.onResume();
        requireActivity().getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        if (mSmartLocationManager != null) {
            // CRITICAL: Re-register listener to prevent CameraActivity or other components
            // from stealing updates
            mSmartLocationManager.setUiFollowActive(true);
            mSmartLocationManager.addLocationUpdateListener(this);
            mSmartLocationManager.startLocationUpdates();
        }
        updateForegroundTracking();
        updateUiTickInterval();
        scheduleUiTick();
    }

    @Override
    public void onPause() {
        super.onPause();
        if (mapView != null)
            mapView.onPause();
        if (googleMap != null)
            savedPosition = googleMap.getCameraPosition().target;
        if (cameraController != null) {
            cameraController.cancelAnimations();
        }
        if (mSmartLocationManager != null) {
            mSmartLocationManager.setUiFollowActive(false);
            mSmartLocationManager.stopLocationUpdates();
        }
        updateForegroundTracking();
        cameraUpdateHandler.removeCallbacks(uiTickRunnable);
        requireActivity().getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
    }

    private void updateForegroundTracking() {
        if (mSmartLocationManager == null || profileManager == null) {
            return;
        }
        boolean realtime = profileManager.getCurrent() != ProfileManager.AppProfile.POWERSAVER;
        boolean followActive = !autoFollowPausedByGesture;
        boolean driving = lastMovementState == SmartLocationManager.MovementState.SLOW_DRIVING
                || lastMovementState == SmartLocationManager.MovementState.NORMAL_DRIVING;
        boolean shouldEnable = isResumed() && realtime && followActive && driving;
        if (shouldEnable && !mSmartLocationManager.isForegroundTrackingActive()) {
            mSmartLocationManager.startForegroundTracking();
        } else if (!shouldEnable && mSmartLocationManager.isForegroundTrackingActive()) {
            mSmartLocationManager.stopForegroundTracking(isResumed());
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        if (profileManager != null) {
            try {
                profileManager.removeListener(profileListener);
            } catch (Throwable ignore) {
            }
            try {
                profileManager.removePerfBalanceListener(perfBalanceListener);
            } catch (Throwable ignore) {
            }
        }
        autoFollowPausedByGesture = false;
        btnResumeFollow = null;
        if (myClusterRenderer != null) {
            myClusterRenderer.onRemove();
            myClusterRenderer = null;
        }
        if (clusterManager != null) {
            clusterManager.clearItems();
            clusterManager = null;
        }
        if (myLocationMarker != null) {
            try {
                myLocationMarker.remove();
            } catch (Throwable ignore) {
            }
            myLocationMarker = null;
        }
        try {
            if (myLocAnimator != null) {
                myLocAnimator.cancel();
                myLocAnimator = null;
            }
        } catch (Throwable ignore) {
        }
        myLocationIcon = null;
        if (cameraController != null) {
            cameraController.cancelAnimations();
            cameraController.resetRuntimeState();
            cameraController = null;
        }
        cameraUpdateHandler.removeCallbacks(uiTickRunnable);
        if (proximityCoordinator != null) {
            proximityCoordinator.setListener(null);
            try {
                proximityCoordinator.setEtaProvider(null);
            } catch (Throwable ignore) {
            }
            try {
                proximityCoordinator.setBoostable(null);
            } catch (Throwable ignore) {
            }
        }
        if (mSmartLocationManager != null) {
            try {
                mSmartLocationManager.removeLocationUpdateListener(this);
            } catch (Throwable ignore) {
            }
        }
        hideInfoPillCompletely();
        currentMapDeliveries = Collections.emptyList();
        nearestDeliveries = Collections.emptyList();
        currentCloseDeliveries = Collections.emptyList();

        // Lockscreen notification is managed by a global controller; no Fragment
        // lifecycle coupling.

        if (mapView != null)
            mapView.onDestroy();
        edgeCheckHandler.removeCallbacks(edgeCheckRunnable);
    }

    @Override
    public void onLowMemory() {
        super.onLowMemory();
        if (mapView != null)
            mapView.onLowMemory();
    }

    // Patch 3: 边缘检测兜底
    private final Handler edgeCheckHandler = new Handler(Looper.getMainLooper());
    private final Runnable edgeCheckRunnable = new Runnable() {
        @Override
        public void run() {
            if (googleMap == null || mLastEffectiveUiLocation == null || navigationModeEnabled
                    || currentMode == DataMode.UNSCANNED) {
                edgeOutsideConsecutive = 0;
                edgeCheckHandler.postDelayed(this, 1000);
                return;
            }

            // 用户刚通过手势暂停自动跟随时，给一个保护窗口，避免边缘兜底立即抢回视图
            if (autoFollowPausedByGesture) {
                long now = SystemClock.uptimeMillis();
                if (autoFollowPausedAtMs != 0L && now - autoFollowPausedAtMs < AUTO_FOLLOW_PAUSE_MS) {
                    edgeOutsideConsecutive = 0;
                    edgeCheckHandler.postDelayed(this, 1000);
                    return;
                }
            }

            boolean uiFixGood = isGoodFixForUi(mLastEffectiveUiLocation, lastMovementState)
                    && !isStaleForUi(mLastEffectiveUiLocation, lastMovementState);
            if (!uiFixGood) {
                edgeOutsideConsecutive = 0;
                edgeCheckHandler.postDelayed(this, 1000);
                return;
            }

            if (mapView != null) {
                LatLng myLatLng = new LatLng(mLastEffectiveUiLocation.getLatitude(),
                        mLastEffectiveUiLocation.getLongitude());
                Point screenPoint = googleMap.getProjection().toScreenLocation(myLatLng);
                int width = mapView.getWidth();
                int height = mapView.getHeight();

                if (width > 0 && height > 0) {
                    // 安全区：左右各留 15%，上下各留 20%，超出才兜底
                    boolean driving = lastMovementState == SmartLocationManager.MovementState.SLOW_DRIVING
                            || lastMovementState == SmartLocationManager.MovementState.NORMAL_DRIVING;
                    int marginX = (int) (width * EDGE_MARGIN_X);
                    int marginY = (int) (height * (driving ? EDGE_MARGIN_Y_DRIVING : EDGE_MARGIN_Y));
                    boolean outside = screenPoint.x < marginX || screenPoint.x > (width - marginX)
                            || screenPoint.y < marginY || screenPoint.y > (height - marginY);

                    if (outside) {
                        edgeOutsideConsecutive++;
                        if (edgeOutsideConsecutive < EDGE_OUTSIDE_REQUIRED) {
                            edgeCheckHandler.postDelayed(this, 1000);
                            return;
                        }
                        edgeOutsideConsecutive = 0;
                        logD("Edge fallback triggered: blue dot left safe area, forcing centering");
                        centerOnMyLocation(true);
                        if (mSmartLocationManager != null) {
                            mSmartLocationManager.requestBoost(10_000L, "edge_fallback");
                            // ✅ 边缘兜底触发时，额外拉取一次强特定 fix，强制 GPS 跳过当前轮询周期立即工作
                            mSmartLocationManager.requestSingleHighAccuracyFix();
                        }
                    } else {
                        edgeOutsideConsecutive = 0;
                    }
                }
            }

            edgeCheckHandler.postDelayed(this, 1000);
        }
    };

    private void publishLockscreenFocus(@Nullable DeliveryInfo target, float distanceMeters) {
        try {
            FocusStateRepository repo = FocusStateRepository.get();
            if (target == null) {
                repo.setLatest(null);
                return;
            }
            String stableId = buildPrimaryKey(target);
            Float dist = Float.isNaN(distanceMeters) ? null : distanceMeters;
            repo.setLatest(new FocusState(stableId, target, dist));
        } catch (Throwable ignore) {
        }
    }

    // ──────────────────────────────────────────────────────
    // Fullscreen Mode Helper Methods
    // ──────────────────────────────────────────────────────

    /**
     * Toggle fullscreen mode - hides/shows top toolbar and bottom navigation
     */
    private void toggleFullscreenMode() {
        isFullscreenMode = !isFullscreenMode;

        // Update button icon
        if (btnFullscreen != null) {
            int iconRes = isFullscreenMode
                    ? R.drawable.ic_fullscreen_exit
                    : R.drawable.ic_fullscreen_enter;
            btnFullscreen.setImageResource(iconRes);
        }

        // Notify parent fragment
        Fragment parent = getParentFragment();
        if (parent instanceof MapHostFragment) {
            ((MapHostFragment) parent).notifyFullscreenToggle(isFullscreenMode);
        }
        // Fallback: if activity implements listener (防止 ViewPager 场景 listener 丢失)
        if (requireActivity() instanceof MapHostFragment.FullscreenModeListener) {
            ((MapHostFragment.FullscreenModeListener) requireActivity()).onFullscreenToggle(isFullscreenMode);
        }

        // Hide/show in-fragment toolbar
        if (mToolbar != null) {
            mToolbar.setVisibility(isFullscreenMode ? View.GONE : View.VISIBLE);
        }

        // Adjust InfoPill bottom margin
        adjustInfoPillMarginForFullscreen();

        logD("Fullscreen mode: " + isFullscreenMode);
    }

    /**
     * Adjust InfoPill bottom margin based on fullscreen state
     */
    private void adjustInfoPillMarginForFullscreen() {
        if (infoPill == null)
            return;

        ViewGroup.LayoutParams params = infoPill.getLayoutParams();
        if (!(params instanceof ViewGroup.MarginLayoutParams))
            return;

        ViewGroup.MarginLayoutParams marginParams = (ViewGroup.MarginLayoutParams) params;

        // Calculate bottom margin
        int bottomMargin;
        if (isFullscreenMode) {
            // Fullscreen: closer to bottom (16dp)
            bottomMargin = (int) (16 * getResources().getDisplayMetrics().density);
        } else {
            // Normal: leave space for bottom nav (72dp = 56dp nav + 16dp margin)
            bottomMargin = (int) (72 * getResources().getDisplayMetrics().density);
        }

        marginParams.bottomMargin = bottomMargin;
        infoPill.setLayoutParams(marginParams);

        // Also adjust collapsed pill
        if (collapsedInfoPill != null) {
            ViewGroup.LayoutParams collapsedParams = collapsedInfoPill.getLayoutParams();
            if (collapsedParams instanceof ViewGroup.MarginLayoutParams) {
                ((ViewGroup.MarginLayoutParams) collapsedParams).bottomMargin = bottomMargin;
                collapsedInfoPill.setLayoutParams(collapsedParams);
            }
        }
    }
}
