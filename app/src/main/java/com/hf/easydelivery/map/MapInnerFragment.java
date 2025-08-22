package com.hf.easydelivery.map;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.location.Location;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
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
import com.google.android.gms.maps.model.Marker;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
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

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

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

        if (statusSummaryText != null) {
            statusSummaryText.setOnLongClickListener(v -> {
                showStatusLegendDialog();
                return true;
            });
        }
        if (btnToggle != null) btnToggle.setVisibility(View.GONE);

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
                // ... (手势交互逻辑保持不变)
            }
        });

        googleMap.setOnCameraMoveListener(() -> {
            CameraPosition cameraPosition = googleMap.getCameraPosition();
            myClusterRenderer.setZoomLevel(cameraPosition.zoom);
            // 聚合刷新由 OnCameraIdleListener 触发，这里不重复调用以降低卡顿
        });

        clusterManager.setOnClusterClickListener(cluster -> {
            showClusterItemListBottomSheet(new ArrayList<>(cluster.getItems()));
            return true;
        });

        clusterManager.setOnClusterItemClickListener(item -> {
            showCamera(item);
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
        rv.setAdapter(new ClusterParcelAdapter(items, info -> {
            dialog.dismiss();
            showCamera(info);
        }));
        dialog.show();
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
        LatLng me = new LatLng(loc.getLatitude(), loc.getLongitude());
        CameraPosition current = googleMap.getCameraPosition();
        googleMap.animateCamera(CameraUpdateFactory.newLatLngZoom(me, current.zoom <= 6 ? 15 : current.zoom));
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
        // ... (其他与定位相关的相机逻辑保留)
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
        if (mapView != null) mapView.onDestroy();
    }

    @Override
    public void onLowMemory() {
        super.onLowMemory();
        if (mapView != null) mapView.onLowMemory();
    }
}