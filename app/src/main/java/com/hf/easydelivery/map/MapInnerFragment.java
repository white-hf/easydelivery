package com.hf.easydelivery.map;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.location.Location;
import android.os.Build;
import android.os.Bundle;
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
import android.widget.ImageButton;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import android.os.SystemClock;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatDelegate;
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
import com.google.android.gms.maps.model.CameraPosition;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.LatLngBounds;
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
import com.hf.easydelivery.view.DeliveredPackagesFragment;
import com.hf.easydelivery.view.model.MapViewModel;
import com.hf.easydelivery.view.model.ScanViewModel;
import com.hf.easydelivery.map.config.ProfileManager;
import com.hf.easydelivery.view.DeveloperPanelBottomSheet;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * 地图派送界面（内层 Fragment）
 * <p>职责摘要:</p>
 * <ul>
 *   <li>协调 {@link DeliveryFocusManager}、{@link CameraFollowController} 完成定位→焦点→UI 展示的流程。</li>
 *   <li>负责 Info Pill UI 的渲染/交互（关闭、跳转拍照、显示群组列表）。</li>
 *   <li>维护地图控件与 ViewModel 的绑定：包裹数据刷新、定位权限、相机与手势事件。</li>
 * </ul>
 * <p>核心流程:</p>
 * <ol>
 *   <li>ViewModel 推送包裹数据 → Fragment 缓存当前“派送中”快照，用于后续距离排序。</li>
 *   <li>SmartLocationManager 推送定位 → `DeliveryFocusManager.sortByDistance(...)` 计算最近 20 单、自动构建 50m 内聚合列表，用于 Info Pill & zoom。</li>
 *   <li>UI 回调（折叠、展开、恢复跟随）仅影响 Fragment 与 {@link CameraFollowController}，逻辑趋于无状态。</li>
 * </ol>
 */
public class MapInnerFragment extends Fragment implements OnMapReadyCallback, SmartLocationManager.LocationUpdateListener {
    private static final String TAG = "MapInnerFragment";
    private static final float DEFAULT_DISTANCE_METERS = 1000f;
    private static final float INFO_PILL_PROXIMITY_THRESHOLD_METERS = 500f;
    private static final long MAX_LOCATION_AGE_MS = 15_000L;
    private static final long MANUAL_CENTER_HOLD_MS = 3_000L;
    private static final long INSIDE_MANUAL_CENTER_HOLD_MS = 500L;
    private static final long INSIDE_BOOST_DURATION_MS = 5_000L;
    private static final long INSIDE_BOOST_COOLDOWN_MS = 25_000L;
    private void logD(String msg){
        try { FileLog.getInstance().debug(TAG, msg); } catch (Throwable ignore) {}
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
            } catch (Throwable ignore) {}
            try {
                if (cameraController != null) {
                    cameraController.applyAppProfile(newProfile);
                    logD("profile changed -> camera=" + newProfile);
                }
            } catch (Throwable ignore) {}
            try {
                if (focusManager != null) {
                    focusManager.applyAppProfile(newProfile);
                    logD("profile changed -> focus=" + newProfile);
                }
            } catch (Throwable ignore) {}
        }
    };
    private List<DeliveryInfo> currentMapDeliveries = Collections.emptyList();
    private List<DeliveryInfo> nearestDeliveries = Collections.emptyList();
    private List<DeliveryInfo> currentCloseDeliveries = Collections.emptyList();
    private DeliveryInfo currentPrimaryDelivery = null;
    private String currentPrimaryKey = null;
    private float lastNearestDistanceMeters = Float.NaN;
    private View btnResumeFollow;
    private boolean autoFollowPausedByGesture = false;
    private long manualCenterHoldUntilMs = 0L;
    private InfoPillProximityController.RegionState currentRegionState = InfoPillProximityController.RegionState.IN_TRANSIT;
    private boolean insideZoneBoostEnabled = false;
    private long lastInsideBoostMs = 0L;
    private boolean forceProximityEvaluation = false;
    private long autoFollowPausedAtMs = 0L;

    private SmartLocationManager mSmartLocationManager;
    private Location mLastLocation = null;
    private MapViewModel mapViewModel;
    // 支持两种数据源：派送中(地图) / 未扫描(扫码)
    private ScanViewModel scanViewModel;
    private enum DataMode { DELIVERY, UNSCANNED }
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
    private long lastToolbarTapMs = 0L;

    // ===== Top-3 主案：Fragment 侧轻量采样/抑制配置 =====
    // 远距降采样：当最近目标很远时，降低 Proximity 评估频率
    private static final float FAR_DISTANCE_SAMPLE_THRESHOLD_M = 1500f;   // >1.5km 认为“远距”
    private static final long FAR_SAMPLE_MIN_INTERVAL_MS = 2500L;         // 远距评估最少间隔
    private static final long NEAR_SAMPLE_MIN_INTERVAL_MS = 800L;         // 近距评估最少间隔

    // 区域通勤极简：高速穿越空区时，短时间抑制 Proximity 评估
    private static final float COMMUTE_SWITCH_M = 600f;                   // 未越过 600m 仍认为同一区域
    private static final long COMMUTE_SUPPRESS_MS = 120_000L;             // 抑制 2 分钟

    // UI 兜底的回差：策略层已有 lock/unlock，这里只做折叠后的最小回差
    private static final float LOCK_HYSTERESIS_EXTRA_M = 80f;

    // 运行时状态
    private long lastProximityEvalMs = 0L;                                // 上次执行 proximity 的时间
    @Nullable private LatLng commuteAnchorLatLng = null;                  // 区域通勤锚点
    private long commuteSuppressUntilMs = 0L;                             // 通勤抑制到期时间
    private long lastCommuteSuppressedKey = 0L;

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
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
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
        } catch (Throwable ignore) {}
        try {
            if (cameraController != null) {
                cameraController.applyAppProfile(appProfile);
            }
        } catch (Throwable ignore) {}
        try {
            if (focusManager != null) {
                focusManager.applyAppProfile(appProfile);
            }
        } catch (Throwable ignore) {}
        try {
            profileManager.addListener(profileListener);
        } catch (Throwable ignore) {}

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
            try { btnResumeFollow.bringToFront(); } catch (Throwable ignore) {}
            try { btnResumeFollow.setElevation(10f); } catch (Throwable ignore) {}
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
            if (btnMyLoc != null) btnMyLoc.setOnClickListener(v -> centerOnMyLocation(false));
            if (btnMapType != null) btnMapType.setOnClickListener(v -> toggleMapType(btnMapType));
        }

        if (mToolbar != null) {
            mToolbar.setOnClickListener(v -> onToolbarTapped());
        }
    }

    private void setupToolbar() {
        if (mToolbar == null) return;
        Menu menu = mToolbar.getMenu();
        if (menu != null) menu.clear();
        mToolbar.inflateMenu(R.menu.map_menu);
        updateNavigationMenuItem();
        mToolbar.setNavigationIcon(null);
            mToolbar.setOnMenuItemClickListener(item -> {
                int id = item.getItemId();
                if (id == R.id.menu_refresh) {
                    currentMode = DataMode.DELIVERY;
                    try { mapViewModel.requestAndRefreshMarkers(true); } catch (Throwable ignore) {}
                    if (getParentFragment() instanceof MapHostFragment)
                        ((MapHostFragment) getParentFragment()).onRequestInTransitList();
                    Toast.makeText(requireContext(), "刷新派送中...", Toast.LENGTH_SHORT).show();
                    return true;
                } else if (id == R.id.menu_unscanned) {
                    currentMode = DataMode.UNSCANNED;
                    if (scanViewModel != null) scanViewModel.queryUnscanned();
                    if (getParentFragment() instanceof MapHostFragment) {
                        ((MapHostFragment) getParentFragment()).onRequestUnscannedList();
                    }
                    Toast.makeText(requireContext(), "查询未扫描...", Toast.LENGTH_SHORT).show();
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
                        Toast.makeText(requireContext(), "需要定位权限", Toast.LENGTH_SHORT).show();
                    }
                });

        if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {
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
                    try { mSmartLocationManager.requestBoost(ms); } catch (Throwable ignore) {}
                }
            });
            proximityCoordinator.setListener(new ProximityCoordinator.Listener() {
                @Override public void onShow(@NonNull DeliveryInfo target, float distanceMeters, @NonNull List<DeliveryInfo> nearby) {
                    currentPrimaryDelivery = target;
                    currentCloseDeliveries = nearby;
                    currentPrimaryKey = buildPrimaryKey(target);
                    lastNearestDistanceMeters = distanceMeters;
                    // 进入时退出通勤抑制
                    commuteSuppressUntilMs = 0L;
                    showInfoPill(target, nearby);
                }
                @Override public void onUpdate(@NonNull DeliveryInfo target, float distanceMeters, @NonNull List<DeliveryInfo> nearby) {
                    currentPrimaryDelivery = target;
                    currentCloseDeliveries = nearby;
                    currentPrimaryKey = buildPrimaryKey(target);
                    lastNearestDistanceMeters = distanceMeters;
                    // 更新时退出通勤抑制
                    commuteSuppressUntilMs = 0L;
                    showInfoPill(target, nearby);
                }
                @Override public void onHide() {
                    // 隐藏时清当前主键（允许下次策略自由选择）
                    currentPrimaryKey = null;
                    hideInfoPillCompletely();
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
            if (!isAdded()) return;
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
                        .setTitle("上传失败")
                        .setMessage(msg)
                        .setPositiveButton("确定", null)
                        .show();
            }
        });
        mapViewModel.getDeliveryCompletedLive().observe(getViewLifecycleOwner(), event -> {
            DeliveryInfo info = event.getMessage();
            if (info != null && proximityCoordinator != null) {
                logD("deliveryCompletedLive -> onDeliveryCompleted for key=" + buildPrimaryKey(info));
                clearCommuteSuppression();
                proximityCoordinator.onDeliveryCompleted(info);
                requestImmediateProximityRefresh();
            }
        });
    }

    private void updateMapItems(List<DeliveryInfo> items) {
        if (googleMap == null || clusterManager == null) return;
        clusterManager.clearItems();
        DeliveryInfo firstItem = null;
        List<DeliveryInfo> sanitized = new ArrayList<>();
        if (items != null) {
            for (DeliveryInfo info : items) {
                if (info == null) continue;
                // 过滤掉正在上传队列中的包裹，保持原有口径
                try {
                    if (ResourceMgr.getInstance().getPendingPackagesMgr().exit(info.getOrderSn())) continue;
                } catch (Throwable ignore) {}
                clusterManager.addItem(info);
                if (firstItem == null) firstItem = info;
                sanitized.add(info);
            }
        }
        currentMapDeliveries = sanitized;
        logD("updateMapItems sanitized=" + sanitized.size());
        clusterManager.cluster();

        if (!isCurrentPrimaryStillPending() && currentPrimaryDelivery != null) {
            logD("current primary removed -> hiding pill and forcing refresh");
            hideInfoPillCompletely();
            forceProximityEvaluation = true;
            requestImmediateProximityRefresh();
        }

        if (!sanitized.isEmpty()) {
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
        if (firstItem != null && savedPosition == null) {
            LatLng firstPosition = new LatLng(firstItem.getLatitude(), firstItem.getLongitude());
            googleMap.moveCamera(CameraUpdateFactory.newLatLngZoom(firstPosition, 14));
        } else if (sanitized.isEmpty() && savedPosition == null) {
            centerOnMyLocation(true);
        }
        if (sanitized.isEmpty()) {
            hideInfoPillCompletely();
        }
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
        if (statusSummaryText == null) return;
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
                .setTitle("状态说明")
                .setMessage("顺序为：\n已送达 / 派送中\n\n例如：30/29")
                .setPositiveButton("知道了", null)
                .show();
    }

    @SuppressLint("MissingPermission")
    @Override
    public void onMapReady(GoogleMap map) {
        FileLog.i(TAG, "onMapReady: enter");
        googleMap = map;
        googleMap.setMyLocationEnabled(true);
        cameraController = new CameraFollowController(googleMap, mapView);
        cameraController.setSmartLocationManager(mSmartLocationManager);
        cameraController.setNavigationModeEnabled(navigationModeEnabled);
        try {
            if (profileManager != null) {
                cameraController.applyAppProfile(profileManager.getCurrent());
                logD("onMapReady -> apply camera profile " + profileManager.getCurrent());
            }
        } catch (Throwable ignore) {}
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
        } catch (Throwable ignore) {}
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
            dialog.dismiss();
            showCamera(info);
        }));
        dialog.show();
    }

    private void showInfoPill(DeliveryInfo info, List<DeliveryInfo> focusGroup) {
        if (infoPill == null || info == null) return;
        expandInfoPill();
        // 同步主/群组，保证折叠提示一致
        currentPrimaryDelivery = info;
        DeliveryFocusManager.InfoGroup infoGroup = focusManager.buildInfoGroup(info, focusGroup);
        List<DeliveryInfo> sameAddressGroup = infoGroup.sameAddress;
        currentCloseDeliveries = sameAddressGroup;
        currentPrimaryKey = buildPrimaryKey(info);

        int nearbyCount = infoGroup.nearbyCount;
        int sameAddressCount = sameAddressGroup.size();

        String streetLabel = info.getCivilNumber() > 0 ? info.getCivilNumber() + "号" : "街号未知";
        String unitLabel = (info.getUnitNumber() == null || info.getUnitNumber().isEmpty()) ? "" : info.getUnitNumber() + "单元";
        String parcelLabel = info.getRouteNumber() == null ? "包裹号未知" : info.getRouteNumber() + "包裹";

        List<String> primaryParts = new ArrayList<>();
        if (!TextUtils.isEmpty(streetLabel)) primaryParts.add(streetLabel);
        if (!TextUtils.isEmpty(unitLabel)) primaryParts.add(unitLabel);
        if (!TextUtils.isEmpty(parcelLabel)) primaryParts.add(parcelLabel);
        if (sameAddressCount > 1) {
            primaryParts.add(String.format(Locale.getDefault(), "同址共%d票", sameAddressCount));
        }
        if (nearbyCount > 1) {
            primaryParts.add(String.format(Locale.getDefault(), "附近共%d票", nearbyCount));
        }
        pillRouteText.setText(TextUtils.join("  ", primaryParts).trim());

        String baseAddress = info.getAddress() == null ? "" : info.getAddress();
        pillAddressText.setText(baseAddress);

        String recipient = info.getName() == null ? "—" : info.getName();
        String parcelSummary = buildParcelSummary(sameAddressGroup, info);
        String recipientLine = String.format(Locale.getDefault(), "收件人: %s", recipient);
        if (!parcelSummary.isEmpty()) {
            recipientLine = recipientLine + "\n包裹: " + parcelSummary;
        }
        pillRecipientText.setText(recipientLine);
        infoPill.setVisibility(View.VISIBLE);
        updateCollapsedHint();
    }

    private String buildParcelSummary(List<DeliveryInfo> focusGroup, DeliveryInfo fallback) {
        List<DeliveryInfo> source = (focusGroup == null || focusGroup.isEmpty())
                ? (fallback == null ? Collections.emptyList() : Collections.singletonList(fallback))
                : focusGroup;
        if (source.isEmpty()) return "";

        List<String> labels = new ArrayList<>();
        for (DeliveryInfo item : source) {
            if (item == null) continue;
            String label = item.getRouteNumber();
            if (TextUtils.isEmpty(label)) {
                Long oid = item.getOrderId();
                label = oid == null ? "" : String.valueOf(oid);
            }
            if (TextUtils.isEmpty(label)) continue;
            labels.add(label);
            if (labels.size() >= 5) break;
        }

        if (labels.isEmpty()) return "";

        String summary = TextUtils.join("、", labels);
        if (focusGroup != null && focusGroup.size() > labels.size()) {
            summary = summary + "…";
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
        if (infoPill == null) return;
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
        if (infoPill != null) infoPill.setVisibility(View.GONE);
        if (collapsedInfoPill != null) collapsedInfoPill.setVisibility(View.GONE);
        if (collapsedInfoText != null) collapsedInfoText.setText("暂无派送");
    }

    private void updateCollapsedHint() {
        if (collapsedInfoText == null) return;
        if (currentPrimaryDelivery == null) {
            collapsedInfoText.setText("暂无派送");
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
            label = "当前包裹";
        }
        String address = currentPrimaryDelivery.getAddress();
        if (TextUtils.isEmpty(address)) {
            address = "位置未知";
        }
        collapsedInfoText.setText(String.format(Locale.getDefault(), "%s · %s", label, address));
    }

    private String buildPrimaryKey(@Nullable DeliveryInfo info) {
        if (info == null) return "";
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
        if (currentPrimaryKey == null || currentMapDeliveries == null) return false;
        for (DeliveryInfo info : currentMapDeliveries) {
            if (info == null) continue;
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
        if (target == null) return;
        ViewGroup.LayoutParams params = target.getLayoutParams();
        if (!(params instanceof ViewGroup.MarginLayoutParams)) return;
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

    private int controlInsetFor(@Nullable View control) {
        if (control == null || control.getVisibility() != View.VISIBLE) return 0;
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

    private void showCamera(DeliveryInfo info) {
        Intent intent = new Intent(requireActivity(), CameraActivity.class);
        intent.putExtra("order_id", info.getOrderId() == null ? -1L : info.getOrderId());
        intent.putExtra("latitude", info.getLatitude());
        intent.putExtra("longitude", info.getLongitude());
        startActivity(intent);
    }

    private void getLocation() {
        mSmartLocationManager = SmartLocationManager.getInstance(requireContext());
        if (mSmartLocationManager != null) {
            mSmartLocationManager.setLocationUpdateListener(this);
            mSmartLocationManager.startLocationUpdates();
        }
        if (cameraController != null) {
            cameraController.setSmartLocationManager(mSmartLocationManager);
        }
    }

    private void centerOnMyLocation(boolean resumeAutoFollow) {
        if (googleMap == null) return;
        Location loc = getBestAvailableLocation();
        if (loc == null) {
            Toast.makeText(requireContext(), "暂无定位", Toast.LENGTH_SHORT).show();
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
        if (cameraController != null) {
            cameraController.resetHasCenteredOnUser();
            float distanceMeters = Float.isNaN(lastNearestDistanceMeters)
                    ? DEFAULT_DISTANCE_METERS
                    : lastNearestDistanceMeters;
            logD("centerOnMyLocation zoomFromDist=" + distanceMeters);
            float zoom = focusManager.computeZoomForDistance(distanceMeters);
            boolean alignToCenter = !resumeAutoFollow || currentRegionState == InfoPillProximityController.RegionState.INSIDE;
            boolean insideZone = currentRegionState == InfoPillProximityController.RegionState.INSIDE;
            cameraController.resumeFollowNow(loc, lastMovementState, zoom, alignToCenter, insideZone);
        }
    }

    // import: CameraUpdate, CameraUpdateFactory, LatLng, SystemClock, @NonNull

    private void toggleMapType(ImageButton btn) {
        if (googleMap == null) return;
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
        String msg = navigationModeEnabled ? "导航模式已开启" : "导航模式已关闭";
        Toast.makeText(requireContext(), msg, Toast.LENGTH_SHORT).show();
    }

    private void updateNavigationMenuItem() {
        if (mToolbar == null) return;
        android.view.Menu menu = mToolbar.getMenu();
        if (menu == null) return;
        MenuItem navItem = menu.findItem(R.id.menu_nav_mode);
        if (navItem == null) return;
        navItem.setIcon(navigationModeEnabled ? R.drawable.ic_nav_mode_on : R.drawable.ic_nav_mode_off);
        navItem.setTitle(navigationModeEnabled ? "退出导航视图" : "导航视图");
    }

    /** Hidden developer shortcut: double-tap the toolbar to open the panel. */
    private void onToolbarTapped() {
        final long now = SystemClock.elapsedRealtime();
        if (now - lastToolbarTapMs <= DEV_DOUBLE_TAP_WINDOW_MS) {
            lastToolbarTapMs = 0L;
            try {
                showDeveloperPanel();
            } catch (Throwable t) {
                try { FileLog.getInstance().error(TAG, "Open developer panel failed", t); } catch (Throwable ignore) {}
                Toast.makeText(requireContext(), "Developer panel unavailable", Toast.LENGTH_SHORT).show();
            }
        } else {
            lastToolbarTapMs = now;
        }
    }

    /** Show DeveloperPanelBottomSheet; requires the Map fragment as host for runtime config apply. */
    private void showDeveloperPanel() {
        DeveloperPanelBottomSheet sheet = DeveloperPanelBottomSheet.newInstance();
        sheet.show(getChildFragmentManager(), "DeveloperPanelBottomSheet");
    }

    @Override
    public void onLocationUpdate(Location location, SmartLocationManager.MovementState state) {
        logD("onLocationUpdate loc=" + location.getLatitude() + "," + location.getLongitude()
                + ", mv=" + state
                + ", items=" + (currentMapDeliveries == null ? 0 : currentMapDeliveries.size())
                + ", pausedByGesture=" + autoFollowPausedByGesture);
        // 通知 ViewModel 更新定位
        mapViewModel.updateMyLocation(location);
        mLastLocation = location;
        lastMovementState = state;
        if (googleMap == null || cameraController == null) return;

        Location effective = location;
        if (mSmartLocationManager != null) {
            Location predicted = mSmartLocationManager.getPredictedLocation();
            if (predicted != null) {
                effective = predicted;
            }
        }

        // 1) 交给 Proximity 决策 InfoPill 的显隐/更新（Top-3：远距降采样 + 区域通勤极简）
        try {
            if (proximityCoordinator != null) {
                if (shouldEvaluateProximity(effective, state)) {
                    proximityCoordinator.onLocation(effective, state, currentMapDeliveries);
                    // 进入/更新后：若上次手动折叠且仍锁定同一目标，UI 侧增加一点回差以防抖（兜底）
                    if (infoPillCollapsed && currentPrimaryDelivery != null && !Float.isNaN(lastNearestDistanceMeters)) {
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
        } catch (Throwable ignore) {}

        if (proximityController != null) {
            currentRegionState = proximityController.getRegionState();
        } else {
            currentRegionState = InfoPillProximityController.RegionState.IN_TRANSIT;
        }
        maybeRequestInsideBoost(state);
        maybeRecoverAutoFollow(state);

        // 2) Phase 1/2 回退路径：无需 FocusDecision（Phase 3 未接入时生效）
        float distanceMeters = Float.isNaN(lastNearestDistanceMeters)
                ? DEFAULT_DISTANCE_METERS
                : lastNearestDistanceMeters;
        float preferredZoom = focusManager.computeZoomForDistance(distanceMeters);
        boolean insideZone = currentRegionState == InfoPillProximityController.RegionState.INSIDE;
        boolean manualHold = isManualCenterHoldActive();
        boolean allowAutoFollow = navigationModeEnabled || (!autoFollowPausedByGesture && !manualHold);
        boolean shouldForce = navigationModeEnabled || (!cameraController.hasCenteredOnUser() && allowAutoFollow);
        boolean interacting = navigationModeEnabled ? false : (isUserInteracting || autoFollowPausedByGesture || manualHold);
        logD("camera follow: force=" + shouldForce
                + ", allow=" + allowAutoFollow
                + ", interacting=" + interacting
                + ", insideZone=" + insideZone
                + ", prefZoom=" + preferredZoom);
        cameraController.follow(
                effective,
                state,
                shouldForce,
                allowAutoFollow,
                interacting,
                insideZone,
                preferredZoom
        );
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
        if (autoFollowPausedByGesture) return;
        autoFollowPausedByGesture = true;
        autoFollowPausedAtMs = SystemClock.uptimeMillis();
        showResumeFollowButton(); // 像 Google Maps 一样在用户干预时显示
        logD("auto-follow paused by user gesture");
    }

    private void clearAutoFollowPause() {
        autoFollowPausedByGesture = false;
        isUserInteracting = false;
        manualCenterHoldUntilMs = 0L;
        autoFollowPausedAtMs = 0L;
        hideResumeFollowButton(); // 点击“重新跟随”后隐藏
        logD("auto-follow pause cleared");
    }
    
    private void clearCommuteSuppression() {
        commuteSuppressUntilMs = 0L;
        commuteAnchorLatLng = null;
    }


    private void showResumeFollowButton() {
        if (btnResumeFollow == null || navigationModeEnabled) return;
        // Ensure the button is above MapView/InfoPill and can receive clicks
        try { btnResumeFollow.bringToFront(); } catch (Throwable ignore) {}
        try { btnResumeFollow.setClickable(true); } catch (Throwable ignore) {}
        try { btnResumeFollow.setElevation(10f); } catch (Throwable ignore) {}
        try { btnResumeFollow.setTranslationZ(24f); } catch (Throwable ignore) {}
        // Only animate when transitioning from GONE/INVISIBLE to VISIBLE
        if (btnResumeFollow.getVisibility() != View.VISIBLE) {
            btnResumeFollow.setAlpha(0f);
            btnResumeFollow.setVisibility(View.VISIBLE);
            btnResumeFollow.animate().alpha(1f).setDuration(180).start();
        }
    }

    private void hideResumeFollowButton() {
        if (btnResumeFollow == null || btnResumeFollow.getVisibility() != View.VISIBLE) return;
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
        if (manualCenterHoldUntilMs == 0L) return false;
        boolean active = SystemClock.uptimeMillis() < manualCenterHoldUntilMs;
        if (!active) {
            manualCenterHoldUntilMs = 0L;
        }
        return active;
    }

    private void requestFreshLocation() {
        if (mSmartLocationManager == null) return;
        try { mSmartLocationManager.requestBoost(8_000L); } catch (Throwable ignore) {}
    }

    private void maybeRecoverAutoFollow(@NonNull SmartLocationManager.MovementState state) {
        if (!autoFollowPausedByGesture) return;
        if (navigationModeEnabled) {
            logD("navigation mode active -> auto-resume follow");
            clearAutoFollowPause();
            if (cameraController != null) {
                cameraController.resetHasCenteredOnUser();
            }
            return;
        }
        if (autoFollowPausedAtMs == 0L) return;
        boolean moving = state == SmartLocationManager.MovementState.SLOW_DRIVING
                || state == SmartLocationManager.MovementState.NORMAL_DRIVING
                || state == SmartLocationManager.MovementState.WALKING;
        if (!moving) return;
        long now = SystemClock.uptimeMillis();
        if (now - autoFollowPausedAtMs < 5_000L) {
            return;
        }
        logD("auto-follow paused >5s during drive/walk -> auto-resume");
        clearAutoFollowPause();
        if (cameraController != null) {
            cameraController.resetHasCenteredOnUser();
        }
    }

    private void maybeRequestInsideBoost(@NonNull SmartLocationManager.MovementState state) {
        if (!insideZoneBoostEnabled) return;
        if (currentRegionState != InfoPillProximityController.RegionState.INSIDE) return;
        if (state == SmartLocationManager.MovementState.NORMAL_DRIVING) return;
        if (mSmartLocationManager == null) return;
        long now = SystemClock.uptimeMillis();
        if (now - lastInsideBoostMs < INSIDE_BOOST_COOLDOWN_MS) return;
        try {
            mSmartLocationManager.requestBoost(INSIDE_BOOST_DURATION_MS);
            lastInsideBoostMs = now;
            logD("inside boost requested for " + INSIDE_BOOST_DURATION_MS + " ms");
        } catch (Throwable ignore) {}
    }

    private void requestImmediateProximityRefresh() {
        if (proximityCoordinator == null || mLastLocation == null) return;
        forceProximityEvaluation = true;
        logD("requestImmediateProximityRefresh() -> forcing evaluate at loc=" + mLastLocation.getLatitude() + "," + mLastLocation.getLongitude());
        proximityCoordinator.onLocation(mLastLocation, lastMovementState, currentMapDeliveries);
    }

    @Nullable
    private Location getFreshLocationCandidate(@Nullable Location candidate) {
        if (candidate == null) return null;
        if (!isLocationFresh(candidate)) return null;
        return new Location(candidate);
    }

    private boolean isLocationFresh(@Nullable Location loc) {
        if (loc == null) return false;
        long ageMs = Math.abs(System.currentTimeMillis() - loc.getTime());
        return ageMs <= MAX_LOCATION_AGE_MS;
    }

    /** 计算两点之间的直线距离（米）。 */
    private static float distanceBetweenMeters(@NonNull LatLng a, @NonNull LatLng b) {
        float[] out = new float[1];
        android.location.Location.distanceBetween(a.latitude, a.longitude, b.latitude, b.longitude, out);
        return out[0];
    }

    /**
     * Top-3：决定本次是否需要触发 Proximity 评估（远距降采样 + 区域通勤极简）。
     * 仅影响 ProximityCoordinator 的 onLocation 调用频率，不改变相机跟随。
     */
    private boolean shouldEvaluateProximity(@NonNull Location loc, @NonNull SmartLocationManager.MovementState state) {
        final long now = System.currentTimeMillis();
        if (forceProximityEvaluation) {
            forceProximityEvaluation = false;
            lastProximityEvalMs = now;
            return true;
        }

        // 1) 若处于通勤抑制窗口，直接跳过
        if (now < commuteSuppressUntilMs) {
            logD("proximity skip: commute-suppressed until=" + commuteSuppressUntilMs);
            return false;
        }

        // 2) 基于最近距离选择采样间隔（lastNearestDistanceMeters 由上一次策略回调更新）
        final long minInterval = (Float.isNaN(lastNearestDistanceMeters) || lastNearestDistanceMeters > FAR_DISTANCE_SAMPLE_THRESHOLD_M)
                ? FAR_SAMPLE_MIN_INTERVAL_MS
                : NEAR_SAMPLE_MIN_INTERVAL_MS;
        if (now - lastProximityEvalMs < minInterval) {
            return false; // 采样间隔未到
        }

        // 3) 区域通勤极简：当驾驶且仍未越过切换距离，则进入短时抑制窗口
        if (state == SmartLocationManager.MovementState.SLOW_DRIVING || state == SmartLocationManager.MovementState.NORMAL_DRIVING) {
            LatLng here = new LatLng(loc.getLatitude(), loc.getLongitude());
            if (commuteAnchorLatLng == null) {
                commuteAnchorLatLng = here; // 第一次进入驾驶，设锚点
            } else {
                float moved = distanceBetweenMeters(here, commuteAnchorLatLng);
                if (moved < COMMUTE_SWITCH_M && (Float.isNaN(lastNearestDistanceMeters) || lastNearestDistanceMeters > FAR_DISTANCE_SAMPLE_THRESHOLD_M)) {
                    if (now >= commuteSuppressUntilMs) {
                        commuteSuppressUntilMs = now + COMMUTE_SUPPRESS_MS;
                        logD("proximity commute-suppress start for " + COMMUTE_SUPPRESS_MS + " ms (moved=" + moved + ")");
                    } else {
                        logD("proximity commute-suppress already active until=" + commuteSuppressUntilMs);
                    }
                    return false;
                }
                // 越过切换距离，更新锚点，允许评估
                if (moved >= COMMUTE_SWITCH_M) {
                    commuteAnchorLatLng = here;
                    commuteSuppressUntilMs = 0L;
                }
            }
        } else {
            // 非驾驶则清空通勤锚点
            commuteAnchorLatLng = null;
        }

        lastProximityEvalMs = now;
        return true;
    }

    @Override
    public void onResume() {
        super.onResume();
        if (mapView != null) mapView.onResume();
        requireActivity().getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        if (mSmartLocationManager != null) mSmartLocationManager.startLocationUpdates();
    }

    @Override
    public void onPause() {
        super.onPause();
        if (mapView != null) mapView.onPause();
        if (googleMap != null) savedPosition = googleMap.getCameraPosition().target;
        if (cameraController != null) {
            cameraController.cancelAnimations();
        }
        if (mSmartLocationManager != null) mSmartLocationManager.stopLocationUpdates();
        requireActivity().getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        if (profileManager != null) {
            try { profileManager.removeListener(profileListener); } catch (Throwable ignore) {}
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
        if (cameraController != null) {
            cameraController.cancelAnimations();
            cameraController.resetRuntimeState();
            cameraController = null;
        }
        if (proximityCoordinator != null) {
            proximityCoordinator.setListener(null);
            try { proximityCoordinator.setEtaProvider(null); } catch (Throwable ignore) {}
            try { proximityCoordinator.setBoostable(null); } catch (Throwable ignore) {}
        }
        if (mSmartLocationManager != null) {
            try { mSmartLocationManager.setLocationUpdateListener(null); } catch (Throwable ignore) {}
        }
        hideInfoPillCompletely();
        currentMapDeliveries = Collections.emptyList();
        nearestDeliveries = Collections.emptyList();
        currentCloseDeliveries = Collections.emptyList();
        currentPrimaryDelivery = null;
        currentPrimaryKey = null;
        lastNearestDistanceMeters = Float.NaN;

        if (mapView != null) mapView.onDestroy();
    }

    @Override
    public void onLowMemory() {
        super.onLowMemory();
        if (mapView != null) mapView.onLowMemory();
    }

}
