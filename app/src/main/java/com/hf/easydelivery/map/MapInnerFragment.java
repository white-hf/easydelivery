package com.hf.easydelivery.map;

import android.Manifest;
import android.annotation.SuppressLint;
import android.animation.ValueAnimator;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.location.Location;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.util.Log;
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
import com.google.maps.android.clustering.ClusterItem;

import com.hf.courierservice.apihelper.FileLog;
import com.hf.easydelivery.R;
import com.hf.easydelivery.ResourceMgr;
import com.hf.easydelivery.common.Utils;
import com.hf.easydelivery.core.SmartLocationManager;
import com.hf.easydelivery.dao.DeliveryInfo;
import com.hf.easydelivery.view.CameraActivity;
import com.hf.easydelivery.view.Adapter.ClusterParcelAdapter;
import com.hf.easydelivery.view.model.MapViewModel;
import com.hf.easydelivery.view.model.ScanViewModel;

import android.graphics.Point;
import android.view.animation.DecelerateInterpolator;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

public class MapInnerFragment extends Fragment implements OnMapReadyCallback, SmartLocationManager.LocationUpdateListener {

    private static final String TAG = "MapInnerFragment";

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
    private TextView pillRouteText;
    private TextView pillAddressText;
    private TextView pillRecipientText;
    private MaterialButton btnPillShowList;
    private MaterialButton btnResumeFollow;
    private DeliveryInfo currentFocusedDelivery = null;
    private Long dismissedOrderId = null;
    private boolean autoFollowEnabled = true;
    private boolean approachingTarget = false;
    private float preferredFollowZoom = DEFAULT_FOLLOW_ZOOM;
    private final List<Long> currentFocusGroupIds = new ArrayList<>();

    private SmartLocationManager mSmartLocationManager;
    private Location mLastLocation = null;
    private final Handler interactionHandler = new Handler(Looper.getMainLooper());
    private Runnable interactionResetRunnable;

    private MapViewModel mapViewModel;
    // 支持两种数据源：派送中(地图) / 未扫描(扫码)
    private ScanViewModel scanViewModel;
    private enum DataMode { DELIVERY, UNSCANNED }
    private DataMode currentMode = DataMode.DELIVERY; // 默认派送中

    private ActivityResultLauncher<String> requestLocationPermissionLauncher;
    private SmartLocationManager.MovementState lastMovementState = SmartLocationManager.MovementState.STATIONARY;

    // === 行驶跟踪相关 ===
    private static final long AUTO_RESUME_DELAY_MS = 2_000L;
    private static final long CAMERA_MIN_INTERVAL_MS = 800L;
    private static final float CAMERA_MIN_DISTANCE_METERS = 5f;
    private static final float CAMERA_MIN_HEADING_DELTA = 10f;
    private static final float DRIVING_MIN_ZOOM = 16f;
    private static final float DRIVING_TARGET_SCREEN_FRACTION_Y = 0.68f;
    private static final float APPROACH_DISTANCE_METERS = 130f;
    private static final float LEAVE_DISTANCE_METERS = 200f;
    private static final float CLOSE_DISTANCE_METERS = 60f;
    private static final float DEFAULT_FOLLOW_ZOOM = 15f;
    private static final float APPROACH_ZOOM_LEVEL = 17f;
    private static final float CLOSE_ZOOM_LEVEL = 18f;

    private boolean isUserInteracting = false;
    private boolean hasCenteredOnUser = false;
    private long lastCameraUpdateUptime = 0L;
    private LatLng lastCameraTargetLatLng = null;
    private float lastCameraBearing = Float.NaN;
    private ValueAnimator cameraAnimator;

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
        pillRouteText = view.findViewById(R.id.pill_route);
        pillAddressText = view.findViewById(R.id.pill_address);
        pillRecipientText = view.findViewById(R.id.pill_recipient);
        btnPillShowList = view.findViewById(R.id.btn_pill_show_list);
        btnResumeFollow = view.findViewById(R.id.btn_resume_follow);
        ImageButton pillCloseButton = view.findViewById(R.id.btn_pill_close);

        if (statusSummaryText != null) {
            statusSummaryText.setOnLongClickListener(v -> {
                showStatusLegendDialog();
                return true;
            });
        }
        if (btnToggle != null) btnToggle.setVisibility(View.GONE);

        if (pillCloseButton != null) {
            pillCloseButton.setOnClickListener(v -> hideInfoPill(true));
        }
        if (btnPillShowList != null) {
            btnPillShowList.setOnClickListener(v -> {
                if (currentFocusedDelivery != null) {
                    ArrayList<DeliveryInfo> single = new ArrayList<>();
                    single.add(currentFocusedDelivery);
                    showClusterItemListBottomSheet(single);
                }
            });
        }
        if (btnResumeFollow != null) {
            btnResumeFollow.setOnClickListener(v -> {
                autoFollowEnabled = true;
                isUserInteracting = false;
                btnResumeFollow.setVisibility(View.GONE);
                if (mLastLocation != null) {
                    followLocation(mLastLocation, lastMovementState, true);
                }
            });
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
            if (msg != null) Toast.makeText(getContext(), msg, Toast.LENGTH_SHORT).show();
        });
    }

    private void updateMapItems(List<DeliveryInfo> items) {
        if (googleMap == null || clusterManager == null) return;
        clusterManager.clearItems();
        DeliveryInfo firstItem = null;
        if (items != null) {
            for (DeliveryInfo info : items) {
                if (info == null) continue;
                // 过滤掉正在上传队列中的包裹，保持原有口径
                try {
                    if (ResourceMgr.getInstance().getPendingPackagesMgr().exit(info.getOrderSn())) continue;
                } catch (Throwable ignore) {}
                clusterManager.addItem(info);
                if (firstItem == null) firstItem = info;
            }
        }
        clusterManager.cluster();

        if (items != null && !items.isEmpty()) {
            if (savedPosition == null) {
                // 如果列表不为空，构建边界以包含所有包裹
                LatLngBounds.Builder builder = new LatLngBounds.Builder();

                for (DeliveryInfo info : items) {
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
        }else if ((items ==null || items.isEmpty()) && savedPosition == null) {
            // 如果包裹列表为空，则移动到当前位置
            centerOnMyLocation();
        }
    }

    private void updateStatusBarUI(MapViewModel.MapStatus status) {
        // Compact format: delivered/pending/uploading/failed
        String compact = String.format("%d/%d/%d/%d", status.deliveredCount, status.pendingCount, status.uploadingCount, status.failedCount);
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
                .setMessage("顺序为：\n已送达 / 派送中 / 上传中 / 无法送达\n\n例如：30/29/29/33")
                .setPositiveButton("知道了", null)
                .show();
    }

    @SuppressLint("MissingPermission")
    @Override
    public void onMapReady(GoogleMap map) {
        FileLog.i(TAG, "onMapReady: enter");
        googleMap = map;
        googleMap.setMyLocationEnabled(true);
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

        googleMap.setOnCameraIdleListener(clusterManager);
        googleMap.setOnCameraMoveStartedListener(reason -> {
            if (reason == GoogleMap.OnCameraMoveStartedListener.REASON_GESTURE) {
                isUserInteracting = true;
                autoFollowEnabled = false;
                if (btnResumeFollow != null) {
                    btnResumeFollow.setVisibility(View.VISIBLE);
                }
                cancelCameraAnimator();
                if (interactionResetRunnable != null) {
                    interactionHandler.removeCallbacks(interactionResetRunnable);
                }
                interactionResetRunnable = () -> {
                    isUserInteracting = false;
                    if (mLastLocation != null) {
                        followLocation(mLastLocation, lastMovementState, true);
                    }
                };
                interactionHandler.postDelayed(interactionResetRunnable, AUTO_RESUME_DELAY_MS);
            }
        });

        googleMap.setOnCameraMoveListener(() -> {
            CameraPosition cameraPosition = googleMap.getCameraPosition();
            myClusterRenderer.setZoomLevel(cameraPosition.zoom);
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
        sorted.sort((a, b) -> {
            int streetCompare = compareNumbersSafe(a.getCivilNumber(), b.getCivilNumber());
            if (streetCompare != 0) return streetCompare;
            String unitA = a.getUnitNumber() == null ? "" : a.getUnitNumber();
            String unitB = b.getUnitNumber() == null ? "" : b.getUnitNumber();
            if (unitA.isEmpty() && unitB.isEmpty()) return 0;
            if (unitA.isEmpty()) return 1;
            if (unitB.isEmpty()) return -1;
            int unitNumCompare = compareNumericStrings(unitA, unitB);
            if (unitNumCompare != 0) return unitNumCompare;
            return unitA.compareToIgnoreCase(unitB);
        });

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

    private void showInfoPill(DeliveryInfo info, boolean autoTriggered) {
        if (infoPill == null) return;
        if (autoTriggered && dismissedOrderId != null && info.getOrderId() != null && dismissedOrderId.equals(info.getOrderId())) {
            return;
        }
        dismissedOrderId = null;
        String streetLabel = info.getCivilNumber() > 0 ? info.getCivilNumber() + "号" : "街号未知";
        String unitLabel = (info.getUnitNumber() == null || info.getUnitNumber().isEmpty()) ? "" : info.getUnitNumber() + "单元";
        String parcelLabel = info.getRouteNumber() == null ? "包裹号未知" : "包裹号 " + info.getRouteNumber();
        pillRouteText.setText(String.format(Locale.getDefault(), "%s  %s  %s", streetLabel, unitLabel, parcelLabel).trim());
        String baseAddress = info.getAddress() == null ? "" : info.getAddress();
        pillAddressText.setText(baseAddress);
        pillRecipientText.setText("收件人: " + (info.getName() == null ? "—" : info.getName()));
        infoPill.setVisibility(View.VISIBLE);
    }

    private void hideInfoPill(boolean rememberDismiss) {
        if (infoPill == null || infoPill.getVisibility() != View.VISIBLE) return;
        infoPill.setVisibility(View.GONE);
        if (rememberDismiss && currentFocusedDelivery != null && currentFocusedDelivery.getOrderId() != null) {
            dismissedOrderId = currentFocusedDelivery.getOrderId();
        }
        if (rememberDismiss) {
            currentFocusedDelivery = null;
        }
    }

    private boolean updateNearbyFocus(Location location) {
        boolean wasApproaching = approachingTarget;
        float previousZoom = preferredFollowZoom;
        DeliveryInfo nearest = findNearestDelivery(location, false);
        if (nearest == null) {
            approachingTarget = false;
            currentFocusedDelivery = null;
            preferredFollowZoom = DEFAULT_FOLLOW_ZOOM;
            hideInfoPill(false);
            return wasApproaching || Math.abs(previousZoom - preferredFollowZoom) > 0.15f;
        }
        float distance = distanceTo(nearest, location);
        boolean changed = false;
        if (distance <= APPROACH_DISTANCE_METERS) {
            approachingTarget = true;
            preferredFollowZoom = computeZoomForDistance(distance);
            boolean isDifferent = currentFocusedDelivery == null
                    || currentFocusedDelivery.getOrderId() == null
                    || nearest.getOrderId() == null
                    || !currentFocusedDelivery.getOrderId().equals(nearest.getOrderId());
            if (isDifferent) {
                currentFocusedDelivery = nearest;
                rebuildFocusGroup(location);
                showInfoPill(nearest, true);
                changed = true;
            }
            if (!wasApproaching) {
                changed = true;
            }
        } else if (currentFocusedDelivery != null && distance > LEAVE_DISTANCE_METERS) {
            approachingTarget = false;
            preferredFollowZoom = DEFAULT_FOLLOW_ZOOM;
            if (dismissedOrderId == null || currentFocusedDelivery.getOrderId() == null
                    || !currentFocusedDelivery.getOrderId().equals(dismissedOrderId)) {
                hideInfoPill(false);
            }
            currentFocusedDelivery = null;
            currentFocusGroupIds.clear();
            changed = wasApproaching;
        } else {
            approachingTarget = false;
            preferredFollowZoom = DEFAULT_FOLLOW_ZOOM;
        }
        if (!changed) {
            changed = Math.abs(previousZoom - preferredFollowZoom) > 0.15f;
        }
        return changed;
    }

    /** Distance between two delivery points in meters. */
    private float distanceMeters(@NonNull DeliveryInfo a, @NonNull DeliveryInfo b) {
        float[] results = new float[1];
        Location.distanceBetween(a.getLatitude(), a.getLongitude(),
                b.getLatitude(), b.getLongitude(),
                results);
        return results[0];
    }
    private void rebuildFocusGroup(@NonNull Location location) {
        // === Focus group (neighbors / oscillation suppression) helpers ===
        final float FOCUS_GROUP_NEARBY_METERS = 50f; // neighbor cluster radius

        currentFocusGroupIds.clear();
        if (currentFocusedDelivery == null || currentFocusedDelivery.getOrderId() == null) return;

        List<DeliveryInfo> list;
        try {
            list = ResourceMgr.getInstance().getDeliveryinfoMgr().getListDeliveryInfo();
        } catch (Throwable t) {
            return;
        }
        if (list == null || list.isEmpty()) return;

        final long anchorId = currentFocusedDelivery.getOrderId();
        currentFocusGroupIds.add(anchorId); // include anchor itself

        for (DeliveryInfo info : list) {
            if (info == null || info.getOrderId() == null) continue;
            if (info.getOrderId().equals(anchorId)) continue;

            // 仅按锚点距离筛选邻居
            float dAnchor = distanceMeters(currentFocusedDelivery, info);
            if (dAnchor <= FOCUS_GROUP_NEARBY_METERS) {
                currentFocusGroupIds.add(info.getOrderId());
            }
        }
    }

    private float computeZoomForDistance(float distance) {
        if (distance <= CLOSE_DISTANCE_METERS) return CLOSE_ZOOM_LEVEL;
        if (distance <= APPROACH_DISTANCE_METERS) {
            float ratio = (distance - CLOSE_DISTANCE_METERS) / (APPROACH_DISTANCE_METERS - CLOSE_DISTANCE_METERS);
            ratio = Math.max(0f, Math.min(1f, ratio));
            return CLOSE_ZOOM_LEVEL + ratio * (APPROACH_ZOOM_LEVEL - CLOSE_ZOOM_LEVEL);
        }
        if (distance <= LEAVE_DISTANCE_METERS) {
            float ratio = (distance - APPROACH_DISTANCE_METERS) / (LEAVE_DISTANCE_METERS - APPROACH_DISTANCE_METERS);
            ratio = Math.max(0f, Math.min(1f, ratio));
            return APPROACH_ZOOM_LEVEL + ratio * (DEFAULT_FOLLOW_ZOOM - APPROACH_ZOOM_LEVEL);
        }
        return DEFAULT_FOLLOW_ZOOM;
    }

    private DeliveryInfo findNearestDelivery(Location location) {
        return findNearestDelivery(location, false);
    }

    private DeliveryInfo findNearestDelivery(Location location, boolean ignoreCurrent) {
        if (location == null) return null;
        List<DeliveryInfo> list;
        try {
            list = ResourceMgr.getInstance().getDeliveryinfoMgr().getListDeliveryInfo();
        } catch (Throwable t) {
            return null;
        }
        if (list == null || list.isEmpty()) return null;
        float best = Float.MAX_VALUE;
        DeliveryInfo bestInfo = null;
        for (DeliveryInfo info : list) {
            if (info == null) continue;
            if (ignoreCurrent && currentFocusedDelivery != null
                    && currentFocusedDelivery.getOrderId() != null
                    && info.getOrderId() != null
                    && currentFocusedDelivery.getOrderId().equals(info.getOrderId())) {
                continue;
            }
            float dist = distanceMeters(location.getLatitude(), location.getLongitude(), info.getLatitude(), info.getLongitude());
            if (dist < best) {
                best = dist;
                bestInfo = info;
            }
        }
        return bestInfo;
    }

    private float distanceTo(DeliveryInfo info, Location location) {
        if (info == null || location == null) return Float.MAX_VALUE;
        return distanceMeters(location.getLatitude(), location.getLongitude(), info.getLatitude(), info.getLongitude());
    }

    private float distanceMeters(double lat1, double lon1, double lat2, double lon2) {
        float[] results = new float[1];
        Location.distanceBetween(lat1, lon1, lat2, lon2, results);
        return results[0];
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
        mSmartLocationManager.setLocationUpdateListener(this);
        mSmartLocationManager.startLocationUpdates();
    }

    private void centerOnMyLocation() {
        if (googleMap == null) return;
        Location loc = (mLastLocation != null) ? mLastLocation : (mSmartLocationManager != null ? mSmartLocationManager.getLastSmoothedLocation() : null);
        if (loc == null) {
            Toast.makeText(requireContext(), "暂无定位", Toast.LENGTH_SHORT).show();
            return;
        }
        isUserInteracting = false;
        autoFollowEnabled = true;
        if (btnResumeFollow != null) {
            btnResumeFollow.setVisibility(View.GONE);
        }
        if (interactionResetRunnable != null) {
            interactionHandler.removeCallbacks(interactionResetRunnable);
        }
        followLocation(loc, lastMovementState, true);
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

    private void followLocation(@NonNull Location location, SmartLocationManager.MovementState state, boolean force) {
        if (googleMap == null) return;

        boolean driving = isDrivingState(state);
        boolean allowCameraMove = force || (autoFollowEnabled && !isUserInteracting);
        boolean canUpdateCamera = force || shouldUpdateCamera(location, state);

        if (!allowCameraMove && driving) {
            return;
        }
        if (!canUpdateCamera && !force) {
            return;
        }

        CameraPosition targetCamera;
        if (driving) {
            targetCamera = buildDrivingCamera(location);
        } else if (!hasCenteredOnUser || force) {
            targetCamera = buildCenteredCamera(location);
        } else if (!isUserInteracting && shouldUpdateCamera(location, state)) {
            targetCamera = buildCenteredCamera(location);
        } else {
            return;
        }

        if (targetCamera == null) return;

        if (cameraAnimator != null && cameraAnimator.isRunning()) {
            CameraPosition interim = googleMap.getCameraPosition();
            CameraPosition mid = new CameraPosition.Builder(interim)
                    .target(new LatLng((interim.target.latitude + targetCamera.target.latitude) / 2,
                            (interim.target.longitude + targetCamera.target.longitude) / 2))
                    .zoom(interim.zoom + (targetCamera.zoom - interim.zoom) * 0.5f)
                    .bearing(interim.bearing + (targetCamera.bearing - interim.bearing) * 0.5f)
                    .tilt(interim.tilt + (targetCamera.tilt - interim.tilt) * 0.5f)
                    .build();
            googleMap.moveCamera(CameraUpdateFactory.newCameraPosition(mid));
        }
        animateCameraTo(targetCamera);
        lastCameraUpdateUptime = SystemClock.uptimeMillis();
        lastCameraTargetLatLng = targetCamera.target;
        lastCameraBearing = targetCamera.bearing;
        hasCenteredOnUser = true;

        if (mSmartLocationManager != null && driving) {
            float offset = estimateEdgeOffsetMeters(location);
            mSmartLocationManager.requestBoostIfEdgeRisk(offset, location.getSpeed());
        }
    }

    private boolean isDrivingState(SmartLocationManager.MovementState state) {
        return state == SmartLocationManager.MovementState.SLOW_DRIVING
                || state == SmartLocationManager.MovementState.NORMAL_DRIVING;
    }

    private boolean shouldUpdateCamera(Location location, SmartLocationManager.MovementState state) {
        if (googleMap == null) return false;

        long now = SystemClock.uptimeMillis();
        boolean timeOk = (now - lastCameraUpdateUptime) > CAMERA_MIN_INTERVAL_MS;

        float distance = 0f;
        if (lastCameraTargetLatLng != null) {
            float[] results = new float[1];
            Location.distanceBetween(
                    lastCameraTargetLatLng.latitude, lastCameraTargetLatLng.longitude,
                    location.getLatitude(), location.getLongitude(),
                    results);
            distance = results[0];
        }
        boolean distanceOk = distance > CAMERA_MIN_DISTANCE_METERS;

        boolean headingOk = false;
        if (location.hasBearing() && !Float.isNaN(lastCameraBearing)) {
            float delta = Math.abs(location.getBearing() - lastCameraBearing);
            if (delta > 180f) delta = 360f - delta;
            headingOk = delta > CAMERA_MIN_HEADING_DELTA;
        }

        if (!timeOk && !distanceOk && !headingOk) {
            return false;
        }

        if (isDrivingState(state)) {
            return timeOk && (distanceOk || headingOk);
        }
        return distanceOk || headingOk || !hasCenteredOnUser;
    }

    private CameraPosition buildCenteredCamera(Location location) {
        LatLng target = new LatLng(location.getLatitude(), location.getLongitude());
        CameraPosition current = googleMap.getCameraPosition();
        float zoom = current.zoom < 15f ? 15f : current.zoom;
        return new CameraPosition.Builder(current)
                .target(target)
                .zoom(zoom)
                .bearing(current.bearing)
                .build();
    }

    private CameraPosition buildDrivingCamera(Location location) {
        if (googleMap == null) return null;
        LatLng driverLatLng = new LatLng(location.getLatitude(), location.getLongitude());
        CameraPosition current = googleMap.getCameraPosition();
        LatLng targetLatLng = driverLatLng;

        if (mapView != null && mapView.getWidth() > 0 && mapView.getHeight() > 0) {
            try {
                Point point = googleMap.getProjection().toScreenLocation(driverLatLng);
                int width = mapView.getWidth();
                int height = mapView.getHeight();
                int targetX = width / 2;
                int targetY = (int) (height * DRIVING_TARGET_SCREEN_FRACTION_Y);
                int dx = point.x - targetX;
                int dy = point.y - targetY;
                Point newCenter = new Point(width / 2 + dx, height / 2 + dy);
                newCenter.x = Math.max(0, Math.min(width, newCenter.x));
                newCenter.y = Math.max(0, Math.min(height, newCenter.y));
                targetLatLng = googleMap.getProjection().fromScreenLocation(newCenter);
            } catch (Exception ignore) {
                targetLatLng = driverLatLng;
            }
        }

        float zoom = Math.max(preferredFollowZoom, DRIVING_MIN_ZOOM);
        float tilt = current.tilt < 45f ? 45f : current.tilt;
        float bearing = current.bearing;

        if (location.hasBearing() && location.getSpeed() > 0.5f) {
            bearing = location.getBearing();
        } else if (mSmartLocationManager != null) {
            float heading = mSmartLocationManager.getCurrentHeading();
            if (!Float.isNaN(heading)) {
                bearing = heading;
            }
        }

        if (bearing < 0f) bearing = 0f;
        if (bearing > 360f) bearing = bearing % 360f;

        return new CameraPosition.Builder(current)
                .target(targetLatLng)
                .zoom(zoom)
                .tilt(tilt)
                .bearing(bearing)
                .build();
    }

    private float estimateEdgeOffsetMeters(Location location) {
        if (googleMap == null || mapView == null || mapView.getWidth() == 0 || mapView.getHeight() == 0) {
            return 0f;
        }
        try {
            LatLng latLng = new LatLng(location.getLatitude(), location.getLongitude());
            Point point = googleMap.getProjection().toScreenLocation(latLng);
            int width = mapView.getWidth();
            int height = mapView.getHeight();
            Point targetPoint = new Point(width / 2, (int) (height * DRIVING_TARGET_SCREEN_FRACTION_Y));
            float dx = point.x - targetPoint.x;
            float dy = point.y - targetPoint.y;
            float pixelDistance = (float) Math.hypot(dx, dy);
            if (pixelDistance <= 0f) return 0f;

            Point refA = new Point(targetPoint.x, targetPoint.y);
            Point refB = new Point(targetPoint.x + 100, targetPoint.y);
            LatLng llA = googleMap.getProjection().fromScreenLocation(refA);
            LatLng llB = googleMap.getProjection().fromScreenLocation(refB);
            float[] results = new float[1];
            Location.distanceBetween(llA.latitude, llA.longitude, llB.latitude, llB.longitude, results);
            float metersPerPx = (results[0] <= 0f) ? 0f : results[0] / 100f;
            return metersPerPx * pixelDistance;
        } catch (Exception ignore) {
            return 0f;
        }
    }

    private void animateCameraTo(CameraPosition targetCamera) {
        if (googleMap == null || targetCamera == null) return;

        CameraPosition start = googleMap.getCameraPosition();
        if (cameraAnimator != null) {
            cameraAnimator.cancel();
        }
        cameraAnimator = ValueAnimator.ofFloat(0f, 1f);
        cameraAnimator.setDuration(350);
        cameraAnimator.setInterpolator(new DecelerateInterpolator());
        cameraAnimator.addUpdateListener(anim -> {
            float t = (float) anim.getAnimatedValue();
            CameraPosition interpolated = interpolateCamera(start, targetCamera, t);
            googleMap.moveCamera(CameraUpdateFactory.newCameraPosition(interpolated));
        });
        cameraAnimator.start();
    }

    private CameraPosition interpolateCamera(CameraPosition start, CameraPosition end, float t) {
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
        if (bearing < 0f) bearing += 360f;

        return new CameraPosition(new LatLng(lat, lng), zoom, tilt, bearing);
    }

    private void cancelCameraAnimator() {
        if (cameraAnimator != null) {
            cameraAnimator.cancel();
            cameraAnimator = null;
        }
    }

    @Override
    public void onLocationUpdate(Location location, SmartLocationManager.MovementState state) {
        // 通知 ViewModel 更新定位
        mapViewModel.updateMyLocation(location);
        mLastLocation = location;
        lastMovementState = state;

        if (googleMap == null) return;
        boolean focusChanged = updateNearbyFocus(location);
        Location effective = location;
        if (mSmartLocationManager != null) {
            Location predicted = mSmartLocationManager.getPredictedLocation();
            if (predicted != null) {
                effective = predicted;
            }
        }
        followLocation(effective, state, focusChanged && autoFollowEnabled);
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
        if (interactionResetRunnable != null) {
            interactionHandler.removeCallbacks(interactionResetRunnable);
            interactionResetRunnable = null;
        }
        cancelCameraAnimator();
        if (mSmartLocationManager != null) mSmartLocationManager.stopLocationUpdates();
        requireActivity().getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        if (myClusterRenderer != null) {
            myClusterRenderer.onRemove();
            myClusterRenderer = null;
        }
        if (clusterManager != null) {
            clusterManager.clearItems();
            clusterManager = null;
        }
        cancelCameraAnimator();
        if (mapView != null) mapView.onDestroy();
    }

    @Override
    public void onLowMemory() {
        super.onLowMemory();
        if (mapView != null) mapView.onLowMemory();
    }
}
