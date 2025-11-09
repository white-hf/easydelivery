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
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.ImageButton;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
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
import com.google.android.material.floatingactionbutton.FloatingActionButton;
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

    private MapView mapView;
    private GoogleMap googleMap;
    private ClusterManager<DeliveryInfo> clusterManager;
    private MyClusterRenderer<DeliveryInfo> myClusterRenderer;
    private LatLng savedPosition;
    private MaterialToolbar mToolbar;
    private TextView statusSummaryText;
    private FloatingActionButton btnToggle;
    private ProgressBar progressMap;
    private View infoPill;
    private View collapsedInfoPill;
    private TextView collapsedInfoText;
    private TextView pillRouteText;
    private TextView pillAddressText;
    private TextView pillRecipientText;
    private MaterialButton btnPillShowList;
    private final DeliveryFocusManager focusManager = new DeliveryFocusManager();
    private CameraFollowController cameraController;
    private List<DeliveryInfo> currentMapDeliveries = Collections.emptyList();
    private List<DeliveryInfo> nearestDeliveries = Collections.emptyList();
    private List<DeliveryInfo> currentCloseDeliveries = Collections.emptyList();
    private DeliveryInfo currentPrimaryDelivery = null;
    private String currentPrimaryKey = null;
    private float lastNearestDistanceMeters = Float.NaN;
    private View btnResumeFollow;
    private boolean autoFollowPausedByGesture = false;

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

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        FileLog.i(TAG, "onCreateView: enter");
        View view = inflater.inflate(R.layout.activity_map, container, false);
        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO);

        // 初始化 ViewModel
        mapViewModel = new ViewModelProvider(this).get(MapViewModel.class);
        // 未扫描数据由 ScanViewModel 提供（跨页面共享，用 Activity 作用域）
        scanViewModel = new ViewModelProvider(requireActivity()).get(ScanViewModel.class);

        setupViews(view);
        setupToolbar();
        setupMap(savedInstanceState);
        setupPermissions();

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
        btnToggle = view.findViewById(R.id.btn_toggle_mode);
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
        }

        if (statusSummaryText != null) {
            statusSummaryText.setOnLongClickListener(v -> {
                showStatusLegendDialog();
                return true;
            });
        }
        if (btnToggle != null) btnToggle.setVisibility(View.GONE);

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
            if (btnMyLoc != null) btnMyLoc.setOnClickListener(v -> centerOnMyLocation());
            if (btnMapType != null) btnMapType.setOnClickListener(v -> toggleMapType(btnMapType));
        }
    }

    private void setupToolbar() {
        if (mToolbar == null) return;
        Menu menu = mToolbar.getMenu();
        if (menu != null) menu.clear();
        mToolbar.inflateMenu(R.menu.map_menu);
        mToolbar.setNavigationIcon(null);
        mToolbar.setOnMenuItemClickListener(item -> {
            int id = item.getItemId();
            if (id == R.id.menu_refresh) {
                // 派送中
                currentMode = DataMode.DELIVERY;

                    // 兼容旧实现：如未提供 loadDeliveryTasks，则使用原方法
                    try { mapViewModel.requestAndRefreshMarkers(true); } catch (Throwable ignore) {}
                // 调用父 Fragment 的方法来切换和加载派送中列表
                if (getParentFragment() instanceof MapHostFragment)
                    ((MapHostFragment) getParentFragment()).onRequestInTransitList();

                Toast.makeText(requireContext(), "刷新派送中...", Toast.LENGTH_SHORT).show();
                return true;
            } else if (id == R.id.menu_unscanned) {
                // 未扫描
                currentMode = DataMode.UNSCANNED;
                if (scanViewModel != null) scanViewModel.queryUnscanned();

                if (getParentFragment() instanceof MapHostFragment) {
                    ((MapHostFragment) getParentFragment()).onRequestUnscannedList();
                }

                Toast.makeText(requireContext(), "查询未扫描...", Toast.LENGTH_SHORT).show();
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
            if (Boolean.TRUE.equals(shouldVibrate)) {
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
        clusterManager.cluster();

        if (!sanitized.isEmpty()) {
            if (savedPosition == null) {
                // 如果列表不为空，构建边界以包含所有包裹
                LatLngBounds.Builder builder = new LatLngBounds.Builder();

                for (DeliveryInfo info : sanitized) {
                    if (info != null) {
                        builder.include(new LatLng(info.getLatitude(), info.getLongitude()));
                    }
                }

                // 移动相机以适应所有包裹，并设置 150px 的内边距
                LatLngBounds bounds = builder.build();
                // 计算边界的跨度（经度和纬度之差）
                double latSpan = bounds.northeast.latitude - bounds.southwest.latitude;
                double lngSpan = bounds.northeast.longitude - bounds.southwest.longitude;

                // 如果所有包裹都在一个非常小的范围内（例如，跨度小于 0.005 度，约 500 米）
                if (latSpan < 0.005 && lngSpan < 0.005) {
                    // 回退到固定缩放级别，并聚焦到第一个有效包裹的位置
                    googleMap.animateCamera(CameraUpdateFactory.newLatLngZoom(
                            new LatLng(firstItem.getLatitude(), firstItem.getLongitude()), 15));
                } else {
                    // 如果包裹分布广泛，使用fitBounds来显示所有包裹
                    int padding = 150;
                    googleMap.animateCamera(CameraUpdateFactory.newLatLngBounds(bounds, padding));
                }
            }
        }

        // 首次加载后定位到第一个包裹
        if (firstItem != null && savedPosition == null) {
            LatLng firstPosition = new LatLng(firstItem.getLatitude(), firstItem.getLongitude());
            googleMap.moveCamera(CameraUpdateFactory.newLatLngZoom(firstPosition, 14));
        } else if (sanitized.isEmpty() && savedPosition == null) {
            // 如果包裹列表为空，则移动到当前位置
            centerOnMyLocation();
        }
        if (sanitized.isEmpty()) {
            hideInfoPillCompletely();
        }
    }

    private void updateStatusBarUI(MapViewModel.MapStatus status) {
        // Compact format: delivered/pending/uploading/failed
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
            isUserInteracting = gesture;
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
        sorted.sort(this::compareDeliveriesForAddress);

        rv.setAdapter(new ClusterParcelAdapter(sorted, info -> {
            dialog.dismiss();
            showCamera(info);
        }));
        dialog.show();
    }

    private int compareNumbersSafe(Integer a, Integer b) {
        int valA = a == null ? Integer.MAX_VALUE : a;
        int valB = b == null ? Integer.MAX_VALUE : b;
        return Integer.compare(valA, valB);
    }

    private int compareNumericStrings(String a, String b) {
        try {
            int ai = Integer.parseInt(a.replaceAll("[^0-9]", ""));
            int bi = Integer.parseInt(b.replaceAll("[^0-9]", ""));
            return Integer.compare(ai, bi);
        } catch (NumberFormatException ignored) {
            return a.compareToIgnoreCase(b);
        }
    }

    private int compareDeliveriesForAddress(DeliveryInfo a, DeliveryInfo b) {
        String streetA = streetNameKey(a);
        String streetB = streetNameKey(b);
        int cmp = streetA.compareTo(streetB);
        if (cmp != 0) return cmp;

        int civilA = safeCivilNumber(a);
        int civilB = safeCivilNumber(b);
        cmp = Integer.compare(civilA, civilB);
        if (cmp != 0) return cmp;

        UnitKey unitA = buildUnitKey(a);
        UnitKey unitB = buildUnitKey(b);
        cmp = Integer.compare(unitA.emptyFlag, unitB.emptyFlag);
        if (cmp != 0) return cmp;
        cmp = Integer.compare(unitA.numeric, unitB.numeric);
        if (cmp != 0) return cmp;
        cmp = unitA.raw.compareTo(unitB.raw);
        if (cmp != 0) return cmp;

        String routeA = safeString(a.getRouteNumber());
        String routeB = safeString(b.getRouteNumber());
        return routeA.compareTo(routeB);
    }

    private String streetNameKey(DeliveryInfo info) {
        String street = info.getStreetName();
        if (TextUtils.isEmpty(street) && info.getAddress() != null) {
            street = info.getAddress();
        }
        if (street == null) street = "";
        String normalized = street.toLowerCase(Locale.US)
                .replaceAll("[^a-z0-9]", " ")
                .replaceAll("\\s+", " ")
                .trim();
        return normalized;
    }

    private int safeCivilNumber(DeliveryInfo info) {
        Integer civil = info.getCivilNumber();
        if (civil == null || civil <= 0) {
            return Integer.MAX_VALUE;
        }
        return civil;
    }

    private UnitKey buildUnitKey(DeliveryInfo info) {
        String raw = info.getUnitNumber();
        if (TextUtils.isEmpty(raw)) {
            return UnitKey.EMPTY;
        }
        String trimmed = raw.trim();
        if (trimmed.isEmpty()) {
            return UnitKey.EMPTY;
        }
        String lower = trimmed.toLowerCase(Locale.US);
        int numeric = parseFirstNumber(lower);
        return new UnitKey(0, numeric, lower);
    }

    private int parseFirstNumber(String text) {
        if (TextUtils.isEmpty(text)) return Integer.MAX_VALUE;
        String digits = text.replaceAll("[^0-9]", "");
        if (digits.isEmpty()) return Integer.MAX_VALUE;
        try {
            return Integer.parseInt(digits);
        } catch (NumberFormatException ex) {
            return Integer.MAX_VALUE;
        }
    }

    private String safeString(String value) {
        return value == null ? "" : value.toLowerCase(Locale.US);
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

    private void showInfoPill(DeliveryInfo info, List<DeliveryInfo> focusGroup) {
        if (infoPill == null || info == null) return;
        expandInfoPill();
        List<DeliveryInfo> group = (focusGroup == null || focusGroup.isEmpty())
                ? Collections.singletonList(info)
                : focusGroup;
        int groupSize = group.size();

        String streetLabel = info.getCivilNumber() > 0 ? info.getCivilNumber() + "号" : "街号未知";
        String unitLabel = (info.getUnitNumber() == null || info.getUnitNumber().isEmpty()) ? "" : info.getUnitNumber() + "单元";
        String parcelLabel = info.getRouteNumber() == null ? "包裹号未知" : info.getRouteNumber() + "包裹";

        List<String> primaryParts = new ArrayList<>();
        if (!TextUtils.isEmpty(streetLabel)) primaryParts.add(streetLabel);
        if (!TextUtils.isEmpty(unitLabel)) primaryParts.add(unitLabel);
        if (!TextUtils.isEmpty(parcelLabel)) primaryParts.add(parcelLabel);
        if (groupSize > 1) {
            primaryParts.add(String.format(Locale.getDefault(), "共%d票", groupSize));
        }
        pillRouteText.setText(TextUtils.join("  ", primaryParts).trim());

        String baseAddress = info.getAddress() == null ? "" : info.getAddress();
        pillAddressText.setText(baseAddress);

        String recipient = info.getName() == null ? "—" : info.getName();
        String parcelSummary = buildParcelSummary(group, info);
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
        extra = Math.max(extra, controlInsetFor(btnToggle));
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

    private void centerOnMyLocation() {
        if (googleMap == null) return;
        Location loc = getBestAvailableLocation();
        clearAutoFollowPause();
        if (loc == null) {
            Toast.makeText(requireContext(), "暂无定位", Toast.LENGTH_SHORT).show();
            return;
        }
        if (cameraController != null) {
            cameraController.resetHasCenteredOnUser();
            float distanceMeters = Float.isNaN(lastNearestDistanceMeters)
                    ? DEFAULT_DISTANCE_METERS
                    : lastNearestDistanceMeters;
            float zoom = focusManager.computeZoomForDistance(distanceMeters);
            cameraController.centerOn(loc, lastMovementState, zoom);
        }
    }

    private void toggleMapType(ImageButton btn) {
        if (googleMap == null) return;
        int type = googleMap.getMapType();
        if (type == GoogleMap.MAP_TYPE_NORMAL) {
            googleMap.setMapType(GoogleMap.MAP_TYPE_SATELLITE);
        } else {
            googleMap.setMapType(GoogleMap.MAP_TYPE_NORMAL);
        }
    }

    @Override
    public void onLocationUpdate(Location location, SmartLocationManager.MovementState state) {
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

        List<DeliveryInfo> snapshot = currentMapDeliveries;
        nearestDeliveries = focusManager.sortByDistance(effective, snapshot, 20);
        currentPrimaryDelivery = nearestDeliveries.isEmpty() ? null : nearestDeliveries.get(0);
        currentCloseDeliveries = focusManager.collectWithinRadius(nearestDeliveries, 50f);

        if (currentPrimaryDelivery == null) {
            hideInfoPillCompletely();
            lastNearestDistanceMeters = Float.NaN;
        } else {
            float distance = focusManager.distanceTo(currentPrimaryDelivery, effective);
            lastNearestDistanceMeters = distance;
            if (distance <= INFO_PILL_PROXIMITY_THRESHOLD_METERS) {
                String newKey = buildPrimaryKey(currentPrimaryDelivery);
                boolean changed = !TextUtils.equals(currentPrimaryKey, newKey);
                currentPrimaryKey = newKey;
                if (infoPillCollapsed && changed) {
                    expandInfoPill();
                }
                showInfoPill(currentPrimaryDelivery, currentCloseDeliveries);
            } else {
                hideInfoPillCompletely();
            }
        }

        float distanceMeters = Float.isNaN(lastNearestDistanceMeters)
                ? DEFAULT_DISTANCE_METERS
                : lastNearestDistanceMeters;
        float preferredZoom = focusManager.computeZoomForDistance(distanceMeters);
        boolean allowAutoFollow = !autoFollowPausedByGesture;
        boolean shouldForce = !cameraController.hasCenteredOnUser() && allowAutoFollow;
        boolean interacting = isUserInteracting || autoFollowPausedByGesture;
        cameraController.follow(effective, state, shouldForce, allowAutoFollow, interacting,
                preferredZoom);
    }

    private void onResumeFollowClicked() {
        centerOnMyLocation();
    }

    private void pauseAutoFollowByGesture() {
        if (autoFollowPausedByGesture) return;
        autoFollowPausedByGesture = true;
        showResumeFollowButton();
    }

    private void clearAutoFollowPause() {
        autoFollowPausedByGesture = false;
        isUserInteracting = false;
        hideResumeFollowButton();
    }

    private void showResumeFollowButton() {
        if (btnResumeFollow == null || btnResumeFollow.getVisibility() == View.VISIBLE) return;
        btnResumeFollow.setAlpha(0f);
        btnResumeFollow.setVisibility(View.VISIBLE);
        btnResumeFollow.animate().alpha(1f).setDuration(180).start();
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
            loc = mSmartLocationManager.getPredictedLocation();
            if (loc == null) {
                loc = mSmartLocationManager.getLastSmoothedLocation();
            }
        }
        if (loc == null) {
            loc = mLastLocation;
        }
        return loc;
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
        if (mapView != null) mapView.onDestroy();
    }

    @Override
    public void onLowMemory() {
        super.onLowMemory();
        if (mapView != null) mapView.onLowMemory();
    }

}
