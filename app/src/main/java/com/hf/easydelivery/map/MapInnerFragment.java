
package com.hf.easydelivery.map;

/**
 * 地图页 MapFragment
 * 主要逻辑和实现说明：
 * 1. 负责显示包裹派送地图（Google Map），包括标记（Marker）管理、聚合、定位等。
 * 2. 集成 ClusterManager，自动聚合显示大量包裹点，提升渲染效率和交互体验。
 * 3. 负责与智能定位（SmartLocationManager）联动，驾驶模式下超出视野自动居中。
 * 4. 监听并响应包裹数据变更事件（上传成功/失败、保存成功等），及时刷新 Marker 与状态栏。
 * 5. 支持聚合点弹窗（按街道号、单元号排序）、单包裹点点击进入拍照页。
 * 6. 管理顶部状态栏“已送、等送、上传中、失败”数量显示，界面横向排布。
 * 7. 支持定位权限、首次登录检查、集成地图相关异常捕获和调试日志。
 *
 * 目的与核心问题：
 * - 实现地图端所有包裹数据的可视化、交互及导航，提升司机派送效率。
 * - 智能处理高密度点渲染、驾驶时视角自动跟踪、包裹点与业务流程联动。
 * - 保证页面性能和交互友好，兼容定位权限与系统事件，方便问题追踪和维护。
 */

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.pm.PackageManager;
import android.location.Location;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.floatingactionbutton.FloatingActionButton;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;

import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.MapView;
import com.google.android.gms.maps.MapsInitializer;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.Projection;
import com.google.android.gms.maps.model.CameraPosition;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.LatLngBounds;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.VisibleRegion;
import com.google.maps.android.clustering.ClusterItem;
import com.google.maps.android.clustering.ClusterManager;
import com.hf.easydelivery.R;
import com.hf.easydelivery.ResourceMgr;
import com.hf.easydelivery.common.FileLog;
import com.hf.easydelivery.common.Utils;
import com.hf.easydelivery.core.DeliveryinfoMgr;
import com.hf.easydelivery.core.SmartLocationManager;
import com.hf.easydelivery.dao.DeliveryInfo;
import com.hf.easydelivery.dao.PackageEntity;
import com.hf.easydelivery.event.Event;
import com.hf.easydelivery.event.EventConstant;
import com.hf.easydelivery.event.Subscriber;
import com.hf.easydelivery.view.CameraActivity;

import android.content.Intent;
import android.content.Context;

import java.util.ArrayList;

import android.graphics.Point;
import android.os.Handler;
import android.os.Looper;
import android.view.WindowManager;
import android.view.Menu;

import androidx.lifecycle.ViewModelProvider;
import com.hf.easydelivery.view.model.MapViewModel;


public class MapInnerFragment extends Fragment implements Subscriber, OnMapReadyCallback, SmartLocationManager.LocationUpdateListener {
    // Overlay re-entrancy guard
    private boolean overlayBusy = false;

    private static final double MIN_NEXT_PACKAGE_DISTANCE = 50;
    private String STRING_DELIVERY_SUCCESS = null;
    private MapView mapView;
    private GoogleMap googleMap;
    private Projection mProjection;

    private ClusterManager<DeliveryInfo> clusterManager;

    private double mLatitude;
    private double mLongitude;

    // Compact status summary
    private TextView statusSummaryText;  // compact: delivered/pending/uploading/failed
    private FloatingActionButton btnToggle;
    private MaterialToolbar mToolbar;

    private SmartLocationManager mSmartLocationManager;
    private Location mLastLocation = null;

    private MyClusterRenderer<DeliveryInfo> myClusterRenderer;
    private LatLng savedPosition;

    private CameraActivity mCameraFragment;
    private final Bundle args = new Bundle();
    public static final String TAG = "MapFragment";
    private static final String TAG_CAMERA = "camera";
    private static final int MAX_ITEMS_PER_CLUSTER = 20;
    private static final int DEFAULT_ZOOM_LEVEL = 16;

    // === Status counters (align with iOS) ===
    private int deliveredCount = 0;   // 已送达
    private int pendingCount = 0;     // 派送中（地图上的等送）
    private int uploadingCount = 0;   // 上传中（本地待上传队列）
    private int failedCount = 0;      // 无法送达（上传失败）

    // === Driving follow (Google-like) ===
    private static final long CAMERA_UPDATE_MIN_INTERVAL_MS = 1200L;  // 最短更新间隔
    private static final float CAMERA_UPDATE_MIN_DIST_M = 5f;         // 位置变化阈值（米）
    private static final float CAMERA_UPDATE_MIN_BEARING_DELTA = 10f; // 朝向变化阈值（度）
    private static final float CAMERA_TILT_DEG = 45f;                 // 俯仰角

    private boolean isUserInteracting = false;
    private final Handler interactionHandler = new Handler(Looper.getMainLooper());
    private Runnable interactionResetRunnable;

    private LatLng lastCameraUpdateCoord = null;
    private float lastCameraBearing = -1f;
    private long lastCameraUpdateTime = 0L;

    // === Browse lock state ===
    private static final long INTERACTION_RESUME_MS = 30000L; // 手势结束后 30s 内不抢镜
    private boolean browseLock = false; // true=浏览模式（不自动居中），false=跟随模式
    // 自动浏览锁：依据 SmartLocationManager 的运动状态自动控制
    private boolean autoBrowseLock = true; // 默认启用自动逻辑

    private MapViewModel mapViewModel;

    private void updateBrowseLockForState(SmartLocationManager.MovementState state) {
        boolean driving = (state == SmartLocationManager.MovementState.SLOW_DRIVING
                || state == SmartLocationManager.MovementState.NORMAL_DRIVING);
        boolean newLock = !driving; // 非驾驶（静止/步行/未知）一律进入浏览锁
        if (browseLock != newLock) {
            browseLock = newLock;
            FileLog.getInstance().writeLog("[DEBUG] browseLock -> " + browseLock + ", state=" + state);
        }
    }

    // fragment需申请权限
    private ActivityResultLauncher<String> requestLocationPermissionLauncher;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        FileLog.getInstance().writeLog("[TRACE] onCreateView: enter, savedInstanceState=" + savedInstanceState);
        View view = inflater.inflate(R.layout.activity_map, container, false);
        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO);

        STRING_DELIVERY_SUCCESS = getResources().getString(R.string.upload_successfully);

        mapView = view.findViewById(R.id.mapView);
        MaterialToolbar toolbar = view.findViewById(R.id.toolbar);
        mToolbar = toolbar;
        bindToolbarMenu();
        // Compact status bar (30/29/29/33)
        statusSummaryText = view.findViewById(R.id.tv_status_compact);
        btnToggle         = view.findViewById(R.id.btn_toggle_mode);
        ProgressBar progressMap = view.findViewById(R.id.progress_map);

        // Long-press the compact text to show legend; keep UI ultra-clean (no icon)
        if (statusSummaryText != null) {
            statusSummaryText.setOnLongClickListener(v -> { showStatusLegendDialog(); return true; });
        }

        revealStatusThenAutoFade();

        mapView.onCreate(savedInstanceState);
        mapView.getMapAsync(this);
        // Bind right-side mini bar (from XML include)
        View mini = view.findViewById(R.id.include_minibar);
        if (mini != null) {
            android.widget.ImageButton btnMyLoc   = mini.findViewById(R.id.btn_my_loc);
            android.widget.ImageButton btnMapType = mini.findViewById(R.id.btn_map_type);

            if (btnMyLoc != null)   btnMyLoc.setOnClickListener(v -> centerOnMyLocation());
            if (btnMapType != null) btnMapType.setOnClickListener(v -> toggleMapType(btnMapType));
        }
        FileLog.getInstance().writeLog("[TRACE] onCreateView: mapView created and getMapAsync called");

        if (btnToggle != null) btnToggle.setVisibility(View.GONE);

        try {
            MapsInitializer.initialize(requireContext().getApplicationContext());
        } catch (Exception e) {
            FileLog.getInstance().writeLog("[ERROR] Unable to initialize maps: " + e.getMessage());
            Log.e(ResourceMgr.TAG, "Unable to initialize maps", e);
        }
        // Removed duplicate mapView.onResume() call

        requestLocationPermissionLauncher = registerForActivityResult(
                new ActivityResultContracts.RequestPermission(),
                isGranted -> {
                    FileLog.getInstance().writeLog("[DEBUG] Location permission result: " + isGranted);
                    if (isGranted) {
                        getLocation();
                    } else {
                        Toast.makeText(requireContext(), "需要定位权限", Toast.LENGTH_SHORT).show();
                    }
                });

        if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {
            FileLog.getInstance().writeLog("[DEBUG] Location permission not granted, requesting...");
            requestLocationPermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION);
        } else {
            FileLog.getInstance().writeLog("[DEBUG] Location permission already granted, calling getLocation");
            getLocation();
        }

        checkLoginStatus();
        initStatusBar();

        ResourceMgr.getInstance().getPublisher().subscribe(EventConstant.EVENT_UPLOAD_FAILURE, this);
        ResourceMgr.getInstance().getPublisher().subscribe(EventConstant.EVENT_UPLOAD_SUCCESS, this);
        ResourceMgr.getInstance().getPublisher().subscribe(EventConstant.EVENT_SAVE_DELIVERY_SUCCESS, this);
        ResourceMgr.getInstance().getPublisher().subscribe(EventConstant.EVENT_DELIVERY_DATA_READY, this);
        FileLog.getInstance().writeLog("[TRACE] onCreateView: event subscriptions completed");

        mapViewModel = new ViewModelProvider(requireActivity()).get(MapViewModel.class);

        FileLog.getInstance().writeLog("[TRACE] onCreateView: exit");
        return view;
    }

    private void checkLoginStatus() {
        if (!ResourceMgr.getInstance().getLoginInfo().bIsLoggedIn)
            Toast.makeText(requireContext(), R.string.login_tip, Toast.LENGTH_SHORT).show();
    }

    private void initStatusBar()
    {
        // 初始化状态计数：
        try {
            pendingCount = ResourceMgr.getInstance().getDeliveryinfoMgr().getListDeliveryInfo().size();
        } catch (Throwable ignore) { pendingCount = 0; }
        // 尝试从 PendingPackagesMgr 统计“上传中/失败”，若不可用则保持 0
        try {
            int up = 0, fail = 0;
            up = ResourceMgr.getInstance().getPendingPackagesMgr().size();

            uploadingCount = up;
            failedCount = fail;
            // pending = 地图派送中列表 - 正在上传 - 失败（近似 iOS 展示口径）
            if (pendingCount - uploadingCount - failedCount >= 0) {
                pendingCount = pendingCount - uploadingCount - failedCount;
            }
        } catch (Throwable ignore) { /* 某些方法可能不存在，保留默认 0 */ }
        updateStatusBarUI();

    }

    private void performSearch(String routeNumber) {
        DeliveryInfo info = clusterManager.getAlgorithm().getItems().stream().filter(item -> item.getRouteNumber().equals(routeNumber)).findFirst().orElse(null);
        if (info != null) {
            savedPosition = null;
            savedPosition = new LatLng(info.getLatitude(), info.getLongitude());
            CameraPosition cameraPosition = googleMap.getCameraPosition();
            googleMap.moveCamera(CameraUpdateFactory.newLatLngZoom(savedPosition, cameraPosition.zoom));
            // Start CameraActivity directly, passing order_id, latitude, longitude
            Activity activity = getActivity();
            if (activity != null) {
                Intent intent = new Intent(activity, CameraActivity.class);
                long orderId = info.getOrderId() == null ? -1L : info.getOrderId();
                double lat = info.getLatitude();
                double lon = info.getLongitude();
                intent.putExtra("order_id", orderId);
                intent.putExtra("latitude", lat);
                intent.putExtra("longitude", lon);
                activity.startActivity(intent);
            }
        }
    }

    private void getLocation() {
        mSmartLocationManager = SmartLocationManager.getInstance(requireContext());
        mSmartLocationManager.setLocationUpdateListener(this);
    }

    private ArrayList<DeliveryInfo> loadData() {
        return ResourceMgr.getInstance().getDeliveryinfoMgr().getListDeliveryInfo();
    }

    private String extractApartmentNumber(String address) {
        Utils.AddressInfo addressInfo = Utils.extractApartmentAndStreetNumber(address);
        return addressInfo.getApartmentNumber();
    }

    private void showClusterItemListBottomSheet(java.util.List<DeliveryInfo> items) {
        android.app.Activity act = getActivity();
        if (act == null) return;

        com.google.android.material.bottomsheet.BottomSheetDialog dialog =
                new com.google.android.material.bottomsheet.BottomSheetDialog(act);
        android.view.View sheet = act.getLayoutInflater()
                .inflate(R.layout.dialog_cluster_list, null, false);
        dialog.setContentView(sheet);

        androidx.recyclerview.widget.RecyclerView rv = sheet.findViewById(R.id.rv_cluster);
        rv.setLayoutManager(new androidx.recyclerview.widget.LinearLayoutManager(act));
        rv.setAdapter(new com.hf.easydelivery.view.Adapter.ClusterParcelAdapter(items, info -> {
            dialog.dismiss();
            // Start CameraActivity directly, passing orderId, latitude, longitude
            Activity activity = getActivity();
            if (activity != null && info != null) {
                Intent intent = new Intent(activity, CameraActivity.class);
                long orderId = info.getOrderId() == null ? -1L : info.getOrderId();
                double lat = mLatitude;
                double lon = mLongitude;
                intent.putExtra("order_id", orderId);
                intent.putExtra("latitude", lat);
                intent.putExtra("longitude", lon);
                activity.startActivity(intent);
            }
        }));

        dialog.show();
    }




    /**
     * 清除旧 marker，并通过 DeliveryInfoMgr.getDeliveryInfo 异步拉取；
     * bDeliveryTask=true: 派送中；false: 未扫描。
     * 拉取完成后，沿用现有的 “数据 ready” 通知回调里重建 marker（当前逻辑已存在）。
     */
    private void requestAndRefreshMarkers(Boolean bDeliveryTask) {
        // 2) 调用异步接口，数据 ready 会走 EVENT_DELIVERY_DATA_READY → initMarker()
        try {
            ResourceMgr.getInstance()
                    .getDeliveryinfoMgr()
                    .getDeliveryInfo(ResourceMgr.getInstance().getLoginInfo().loginId, bDeliveryTask);
        } catch (Throwable t) {
            Toast.makeText(requireContext(), "请求失败", Toast.LENGTH_SHORT).show();
        }
    }

    private void initMarker() {
        FileLog.getInstance().writeLog("[TRACE] initMarker: enter");
        // Always clear existing markers when rebuilding from data-ready (keeps old markers if network fails)
        try {
            if (clusterManager != null) {
                clusterManager.clearItems();
            }
            if (googleMap != null) {
                googleMap.clear();
            }
        } catch (Throwable ignore) {}

        ArrayList<DeliveryInfo> lst = loadData();
        FileLog.getInstance().writeLog("[DEBUG] initMarker: loaded DeliveryInfo size=" + (lst != null ? lst.size() : 0));

        LatLng firstMarker = null;
        int addCount = 0;
        for (DeliveryInfo info : lst) {
            if (ResourceMgr.getInstance().getPendingPackagesMgr().exit(info.getOrderSn()))
                continue;

            clusterManager.addItem(info);
            addCount++;
            if (firstMarker == null) {
                firstMarker = new LatLng(info.getLatitude(), info.getLongitude());
                mProjection = googleMap.getProjection();
            }
        }
        FileLog.getInstance().writeLog("[DEBUG] initMarker: added to clusterManager, count=" + addCount);

        clusterManager.cluster();

        if (firstMarker != null) {
            if (mLastLocation != null) {
                mLastLocation.setLongitude(firstMarker.longitude);
                mLastLocation.setLatitude(firstMarker.latitude);
            }
            googleMap.moveCamera(CameraUpdateFactory.newLatLngZoom(firstMarker, DEFAULT_ZOOM_LEVEL));
            FileLog.getInstance().writeLog("[TRACE] initMarker: moveCamera to first marker " + firstMarker);
        }
        FileLog.getInstance().writeLog("[TRACE] initMarker: exit");
    }

    private void initClusterManager() {
        FileLog.getInstance().writeLog("[TRACE] initClusterManager: enter");
        clusterManager = null;
        myClusterRenderer = null;

        clusterManager = new ClusterManager<>(requireContext(), googleMap);
        myClusterRenderer = new MyClusterRenderer<>(requireContext(), googleMap, clusterManager);
        clusterManager.setRenderer(myClusterRenderer);
        FileLog.getInstance().writeLog("[TRACE] initClusterManager: clusterManager and renderer initialized");

        googleMap.setOnCameraIdleListener(clusterManager);

        googleMap.setOnCameraMoveStartedListener(reason -> {
            if (reason == GoogleMap.OnCameraMoveStartedListener.REASON_GESTURE) {
                isUserInteracting = true;
                if (interactionResetRunnable != null) {
                    interactionHandler.removeCallbacks(interactionResetRunnable);
                }
                interactionResetRunnable = () -> {
                    isUserInteracting = false;
                    if (!browseLock && mLastLocation != null) {
                        updateCameraForDriving(mLastLocation);
                    }
                };
                // 手势结束后自动恢复跟随，延迟由 INTERACTION_RESUME_MS 控制
                interactionHandler.postDelayed(interactionResetRunnable, INTERACTION_RESUME_MS);
            }
        });

        googleMap.setOnCameraMoveListener(() -> {
            CameraPosition cameraPosition = googleMap.getCameraPosition();
            myClusterRenderer.setZoomLevel(cameraPosition.zoom);
            clusterManager.cluster();
            });

        initMarker();
        FileLog.getInstance().writeLog("[TRACE] initClusterManager: initMarker called");

        clusterManager.setOnClusterClickListener(cluster -> {
            FileLog.getInstance().writeLog("[DEBUG] onClusterClick: size=" + cluster.getSize());
            if (cluster.getSize() < MAX_ITEMS_PER_CLUSTER) {

                java.util.List<DeliveryInfo> list = new java.util.ArrayList<>(cluster.getItems());
                // 与 iOS 一样：按门牌号排序
                list.sort((a, b) -> {
                    Integer n1 = a.getCivilNumber();
                    Integer n2 = b.getCivilNumber();
                    if (n1 == null) n1 = Integer.MAX_VALUE;
                    if (n2 == null) n2 = Integer.MAX_VALUE;
                    return n1.compareTo(n2);
                });
                showClusterItemListBottomSheet(list);

                FileLog.getInstance().writeLog("[TRACE] onClusterClick: showClusterItemListDialog called");
                return true;
            }

            LatLngBounds.Builder builder = LatLngBounds.builder();
            for (ClusterItem item : cluster.getItems()) {
                builder.include(item.getPosition());
            }
            LatLngBounds bounds = builder.build();
            googleMap.animateCamera(CameraUpdateFactory.newLatLngBounds(bounds, 100));
            FileLog.getInstance().writeLog("[TRACE] onClusterClick: animateCamera to bounds");
            return true;
        });

        clusterManager.setOnClusterItemClickListener(item -> {
            FileLog.getInstance().writeLog("[DEBUG] onClusterItemClick: item=" + (item != null ? item.getTitle() : "null"));
            showCamera(item);
            return false;
        });
        FileLog.getInstance().writeLog("[TRACE] initClusterManager: exit");
    }

    private void showCamera(DeliveryInfo info) {
        Activity activity = getActivity();
        if (activity != null) {
            Intent intent = new Intent(activity, CameraActivity.class);
            long orderId = info.getOrderId() == null ? -1L : info.getOrderId();
            double lat = info.getLatitude();
            double lon = info.getLongitude();
            intent.putExtra("order_id", orderId);
            intent.putExtra("latitude", lat);
            intent.putExtra("longitude", lon);
            activity.startActivity(intent);
        }
    }

    @SuppressLint("PotentialBehaviorOverride")
    @Override
    public void onMapReady(GoogleMap map) {
        FileLog.getInstance().writeLog("[TRACE] onMapReady: enter");
        googleMap = map;
        if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED
                && ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            FileLog.getInstance().writeLog("[DEBUG] onMapReady: location permission not granted, requesting...");
            requestLocationPermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION);
            return;
        }
        try {
            googleMap.setMyLocationEnabled(true);
            FileLog.getInstance().writeLog("[TRACE] onMapReady: setMyLocationEnabled(true)");
        } catch (Exception e) {
            FileLog.getInstance().writeLog("[ERROR] onMapReady: setMyLocationEnabled failed: " + e.getMessage());
        }
        initClusterManager();
        FileLog.getInstance().writeLog("[TRACE] onMapReady: initClusterManager called, clusterManager item count=" +
                (clusterManager != null ? clusterManager.getAlgorithm().getItems().size() : "null"));

        if (googleMap != null && savedPosition != null) {
            CameraPosition cameraPosition = googleMap.getCameraPosition();
            googleMap.moveCamera(CameraUpdateFactory.newLatLngZoom(savedPosition, cameraPosition.zoom));
            FileLog.getInstance().writeLog("[TRACE] onMapReady: moved camera to savedPosition=" + savedPosition);
        }
        FileLog.getInstance().writeLog("[TRACE] onMapReady: exit");
    }

    public void removeCustomMarker(DeliveryInfo pkg) {
        if (clusterManager != null && pkg != null) {
            clusterManager.removeItem(pkg);
            clusterManager.cluster();
        }
    }

    @Override
    public void onResume() {
        FileLog.getInstance().writeLog("[TRACE] onResume: enter");
        super.onResume();
        mapView.onResume();

        if (getActivity() != null) {
            getActivity().getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        }

        if (googleMap != null && savedPosition != null) {
            CameraPosition cameraPosition = googleMap.getCameraPosition();
            googleMap.moveCamera(CameraUpdateFactory.newLatLngZoom(savedPosition, cameraPosition.zoom));
            FileLog.getInstance().writeLog("[DEBUG] onResume: moved camera to savedPosition=" + savedPosition);
        }

        if (mSmartLocationManager != null) {
            mSmartLocationManager.startLocationUpdates();
            FileLog.getInstance().writeLog("[TRACE] onResume: startLocationUpdates called");
        }

        if (mapViewModel.shouldDoFirstEnter()) {
            // 这里放原来首次进入 Map 页的弹窗、首查逻辑
            initStatusBar();
        }

        FileLog.getInstance().writeLog("[TRACE] onResume: exit");
    }

    @Override
    public void onPause() {
        FileLog.getInstance().writeLog("[TRACE] onPause: enter");
        super.onPause();
        mapView.onPause();
        if (googleMap != null) {
            savedPosition = googleMap.getCameraPosition().target;
            FileLog.getInstance().writeLog("[DEBUG] onPause: savedPosition=" + savedPosition);
        }
        if (mSmartLocationManager != null) {
            mSmartLocationManager.stopLocationUpdates();
            FileLog.getInstance().writeLog("[TRACE] onPause: stopLocationUpdates called");
        }

        if (getActivity() != null) {
            getActivity().getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        }

        FileLog.getInstance().writeLog("[TRACE] onPause: exit");
    }

    @Override
    public void onDestroyView() {
        FileLog.getInstance().writeLog("[TRACE] onDestroyView: enter");
        super.onDestroyView();
        if (myClusterRenderer != null) {
            myClusterRenderer.onRemove();
            myClusterRenderer = null;
            FileLog.getInstance().writeLog("[DEBUG] onDestroyView: myClusterRenderer removed");
        }
        if (clusterManager != null) {
            ArrayList<DeliveryInfo> deliveryinfoLst = ResourceMgr.getInstance().getDeliveryinfoMgr().getListDeliveryInfo();
            for (DeliveryInfo info : deliveryinfoLst) {
                clusterManager.removeItem(info);
            }
            clusterManager.clearItems();
            clusterManager = null;
            FileLog.getInstance().writeLog("[DEBUG] onDestroyView: clusterManager cleared");
        }
        ResourceMgr.getInstance().getPublisher().unsubscribe(EventConstant.EVENT_UPLOAD_FAILURE, this);
        ResourceMgr.getInstance().getPublisher().unsubscribe(EventConstant.EVENT_UPLOAD_SUCCESS, this);
        ResourceMgr.getInstance().getPublisher().unsubscribe(EventConstant.EVENT_SAVE_DELIVERY_SUCCESS, this);
        ResourceMgr.getInstance().getPublisher().unsubscribe(EventConstant.EVENT_DELIVERY_DATA_READY, this);
        FileLog.getInstance().writeLog("[TRACE] onDestroyView: event subscriptions removed");

        mapView.onDestroy();

        if (mSmartLocationManager != null) {
            mSmartLocationManager.stopLocationUpdates();
            FileLog.getInstance().writeLog("[TRACE] onDestroyView: stopLocationUpdates called");
        }
        FileLog.getInstance().writeLog("[TRACE] onDestroyView: exit");
    }

    @Override
    public void onLowMemory() {
        super.onLowMemory();
        if (mapView != null) mapView.onLowMemory();
    }

    @Override
    public void receive(Event event) {
        FileLog.getInstance().writeLog("[TRACE] receive: eventType=" + event.getEventType());
        DeliveryinfoMgr deliveryinfoMgr = ResourceMgr.getInstance().getDeliveryinfoMgr();
        Activity activity = getActivity();
        switch (event.getEventType()) {
            case EventConstant.EVENT_UPLOAD_FAILURE:
                FileLog.getInstance().writeLog("[ERROR] receive: EVENT_UPLOAD_FAILURE");
                Toast.makeText(requireContext(), "Uploading the data of delivered packages failed", Toast.LENGTH_SHORT).show();
                // 上传失败：上传中-1，无法送达+1
                updateStatusCounts(-1, 0, 0, +1);
                break;
            case EventConstant.EVENT_SAVE_DELIVERY_SUCCESS: {
                FileLog.getInstance().writeLog("[DEBUG] receive: EVENT_SAVE_DELIVERY_SUCCESS");
                PackageEntity packageEntity = (PackageEntity) event.getMessage();
                DeliveryInfo info = deliveryinfoMgr.get(packageEntity.orderId);
                if (info != null) {
                    this.removeCustomMarker(info);
                    deliveryinfoMgr.getListDeliveryInfo().remove(info);
                    String msg = String.format(getResources().getString(R.string.save_successfully), info.getRouteNumber());
                    updateDeliveryInfo(msg);
                    FileLog.getInstance().writeLog("[TRACE] receive: removed marker for orderId=" + packageEntity.orderId);
                } else {
                    FileLog.getInstance().writeLog("[ERROR] receive: failed to get DeliveryInfo for orderId=" + packageEntity.orderId);
                }
                // 入队上传：上传中+1，派送中-1
                updateStatusCounts(+1, -1, 0, 0);
                break;
            }
            case EventConstant.EVENT_UPLOAD_SUCCESS: {
                FileLog.getInstance().writeLog("[DEBUG] receive: EVENT_UPLOAD_SUCCESS");
                PackageEntity packageEntity = (PackageEntity) event.getMessage();
                String msg = String.format(STRING_DELIVERY_SUCCESS, packageEntity.trackingId);
                updateDeliveryInfo(msg);
                Utils.vibrate(requireContext(), 500);

                DeliveryInfo info = deliveryinfoMgr.findNearestPackage(packageEntity.latitude, packageEntity.longitude, MIN_NEXT_PACKAGE_DISTANCE);
                if (info != null && myClusterRenderer != null) {
                    Marker marker = myClusterRenderer.getMarker(info);
                    if (marker != null) {
                        marker.showInfoWindow();
                        FileLog.getInstance().writeLog("[TRACE] receive: showInfoWindow for marker " + info.getOrderId());
                    }
                    LatLng nextPosition = new LatLng(info.getLatitude(), info.getLongitude());
                    CameraPosition cameraPosition = googleMap.getCameraPosition();
                    googleMap.moveCamera(CameraUpdateFactory.newLatLngZoom(nextPosition, cameraPosition.zoom));
                    FileLog.getInstance().writeLog("[TRACE] receive: moveCamera to next package " + nextPosition);
                }
                // 上传成功：上传中-1，已送达+1
                updateStatusCounts(-1, 0, +1, 0);
                break;
            }
            case EventConstant.EVENT_DELIVERY_DATA_READY: {
                FileLog.getInstance().writeLog("[DEBUG] receive: EVENT_DELIVERY_DATA_READY");
                initMarker();
                FileLog.getInstance().writeLog("[TRACE] receive: initMarker called");
                // 交叉校正“派送中”数量（地图数据变化）
                refreshStatus();
                break;
            }
            default:
                FileLog.getInstance().writeLog("[DEBUG] receive: unknown eventType=" + event.getEventType());
        }
    }

    private void updateDeliveryInfo(String msg) {
        Toast.makeText(requireContext(), msg, Toast.LENGTH_SHORT).show();
        refreshStatus();
    }

    private void keepCentered(Location location) {
        // No-op or legacy, replaced by maybeRecenterMap
    }

    // Only recenter when driving and out of padded view bounds
    private void maybeRecenterMap(LatLng coord) {
        if (googleMap == null) return;
        VisibleRegion visible = googleMap.getProjection().getVisibleRegion();
        double latSpan = visible.latLngBounds.northeast.latitude - visible.latLngBounds.southwest.latitude;
        double lngSpan = visible.latLngBounds.northeast.longitude - visible.latLngBounds.southwest.longitude;
        LatLngBounds padded = new LatLngBounds(
            new LatLng(visible.latLngBounds.southwest.latitude - latSpan*0.1,
                       visible.latLngBounds.southwest.longitude - lngSpan*0.1),
            new LatLng(visible.latLngBounds.northeast.latitude + latSpan*0.1,
                       visible.latLngBounds.northeast.longitude + lngSpan*0.1)
        );
        if (!padded.contains(coord)) {
            getActivity().runOnUiThread(() -> {
                googleMap.animateCamera(CameraUpdateFactory.newLatLng(coord));
                // Logger.shared.trace("Auto-center: driving out of view");
            });
        }
    }

    // 防抖：满足时间/距离/方向之一就更新相机，避免过于频繁
    private boolean shouldUpdateCamera(Location loc) {
        long now = System.currentTimeMillis();
        boolean timeOk = (now - lastCameraUpdateTime) > CAMERA_UPDATE_MIN_INTERVAL_MS;

        boolean distOk = true;
        if (lastCameraUpdateCoord != null) {
            float[] results = new float[1];
            Location.distanceBetween(
                    lastCameraUpdateCoord.latitude, lastCameraUpdateCoord.longitude,
                    loc.getLatitude(), loc.getLongitude(),
                    results
            );
            distOk = results[0] > CAMERA_UPDATE_MIN_DIST_M;
        }

        boolean headingOk = false;
        if (loc.hasBearing()) {
            if (lastCameraBearing < 0) headingOk = true;
            else headingOk = Math.abs(loc.getBearing() - lastCameraBearing) > CAMERA_UPDATE_MIN_BEARING_DELTA;
        }
        return timeOk || distOk || headingOk;
    }

    // Estimate how far (in meters) the blue dot is from the desired on-screen target (bottom 68%)
    private float estimateEdgeOffsetMeters(@NonNull Location loc) {
        if (googleMap == null || mapView == null) return 0f;
        Projection proj = googleMap.getProjection();
        if (proj == null) return 0f;
        LatLng user = new LatLng(loc.getLatitude(), loc.getLongitude());
        android.graphics.Point dot = proj.toScreenLocation(user);
        int w = mapView.getWidth();
        int h = mapView.getHeight();
        if (w <= 0 || h <= 0) return 0f;
        android.graphics.Point target = new android.graphics.Point(w / 2, (int) (h * 0.68f));
        int dx = dot.x - target.x;
        int dy = dot.y - target.y;
        double px = Math.hypot(dx, dy);
        // Convert pixels → meters using a small screen delta
        android.graphics.Point p1 = new android.graphics.Point(target.x, target.y);
        android.graphics.Point p2 = new android.graphics.Point(target.x + 100, target.y);
        LatLng ll1 = proj.fromScreenLocation(p1);
        LatLng ll2 = proj.fromScreenLocation(p2);
        float[] results = new float[1];
        android.location.Location.distanceBetween(ll1.latitude, ll1.longitude, ll2.latitude, ll2.longitude, results);
        double metersPer100px = results[0];
        if (metersPer100px <= 0) return 0f;
        return (float) (px * (metersPer100px / 100.0));
    }

    // 导航式相机：蓝点位于屏幕下方 ~68% 处，按行驶方向旋转，保持当前缩放
    private void updateCameraForDriving(Location loc) {
        if (googleMap == null || mapView == null) return;
        if (isUserInteracting) return; // 正在手势交互时不抢镜

        Projection proj = googleMap.getProjection();
        if (proj == null) return;

        LatLng user = new LatLng(loc.getLatitude(), loc.getLongitude());
        Point dot = proj.toScreenLocation(user);

        int w = mapView.getWidth();
        int h = mapView.getHeight();
        if (w == 0 || h == 0) {
            // 视图还没布局好，回退到直接置中
            googleMap.animateCamera(CameraUpdateFactory.newLatLng(user));
            return;
        }

        // 目标让蓝点落在屏幕下方 68% 位置
        Point target = new Point(w / 2, (int) (h * 0.68f));
        int dx = dot.x - target.x;
        int dy = dot.y - target.y;

        Point newCenterPt = new Point(w / 2 + dx, h / 2 + dy);
        LatLng newCenter = proj.fromScreenLocation(newCenterPt);

        CameraPosition current = googleMap.getCameraPosition();
        float bearing = current.bearing;
        if (loc.hasBearing()) {
            bearing = loc.getBearing();
        } else {
            float head = Float.NaN;
            if (mSmartLocationManager != null && mSmartLocationManager.hasReliableHeading()) {
                head = mSmartLocationManager.getCurrentHeading();
            }
            if (!Float.isNaN(head)) {
                bearing = head;
            } else if (lastCameraBearing >= 0) {
                bearing = lastCameraBearing;
            }
        }

        CameraPosition cam = new CameraPosition.Builder(current)
                .target(newCenter)
                .zoom(current.zoom)       // 保持用户当前缩放，不强行改变
                .tilt(CAMERA_TILT_DEG)    // 俯视角（可按需求调）
                .bearing(bearing)         // 车头方向
                .build();

        googleMap.animateCamera(CameraUpdateFactory.newCameraPosition(cam));

        // 记录状态供下一次阈值判断
        lastCameraUpdateCoord = user;
        lastCameraUpdateTime = System.currentTimeMillis();
        lastCameraBearing = bearing;
    }



    public void removeThumbnail(int index) {
        if (mCameraFragment != null)
            mCameraFragment.removeThumbnail(index);
    }

    @Override
    public void onLocationUpdate(Location location, SmartLocationManager.MovementState state) {
        Activity act = getActivity();
        if (act == null) return;

        act.runOnUiThread(() -> {
            updateBrowseLockForState(state);
            // Prefer smoothed location from SmartLocationManager if available
            Location camLoc = location;
            if (mSmartLocationManager != null) {
                Location sm = mSmartLocationManager.getLastSmoothedLocation();
                if (sm != null) {
                    camLoc = new Location(sm); // copy to avoid mutating manager's instance
                    if (location != null && location.hasBearing()) {
                        camLoc.setBearing(location.getBearing());
                    }
                }
            }

            LatLng coord = new LatLng(camLoc.getLatitude(), camLoc.getLongitude());
            // 首次进入：把相机移动到当前位置
            if (mLastLocation == null && googleMap != null) {
                googleMap.moveCamera(CameraUpdateFactory.newLatLngZoom(coord, DEFAULT_ZOOM_LEVEL));
            }
            mLastLocation = camLoc;

            // Edge risk boost: if dot drifts far from desired target and moving fast, request temporary high-frequency updates
            if (mSmartLocationManager != null) {
                float offsetMeters = estimateEdgeOffsetMeters(camLoc);
                float speed = camLoc.hasSpeed() ? camLoc.getSpeed() : 0f; // m/s
                mSmartLocationManager.requestBoostIfEdgeRisk(offsetMeters, speed);
            }

            // 驾驶中采用“谷歌导航式”跟随（带前视偏移与倾斜）
            if (state == SmartLocationManager.MovementState.SLOW_DRIVING ||
                    state == SmartLocationManager.MovementState.NORMAL_DRIVING) {
                if (shouldUpdateCamera(camLoc)) {
                    updateCameraForDriving(camLoc);
                }
            } else {
                // 非驾驶状态保留当前相机，不强制跟随
            }
        });
    }

    private void refreshStatus() {
        // 重新统计 pending（基于最新地图派送中列表），其余延续当前累计
        try {
            int total = ResourceMgr.getInstance().getDeliveryinfoMgr().getListDeliveryInfo().size();
            int computedPending = total - uploadingCount - failedCount;
            pendingCount = Math.max(0, computedPending);
        } catch (Throwable ignore) { /* 保持现状 */ }
        updateStatusBarUI();
    }

    // 统一的状态计数更新（对齐 iOS MapViewController）
    private void updateStatusCounts(int uploadingDelta, int pendingDelta, int deliveredDelta, int failedDelta) {
        uploadingCount += uploadingDelta;
        pendingCount += pendingDelta;
        deliveredCount += deliveredDelta;
        failedCount += failedDelta;
        if (uploadingCount < 0) uploadingCount = 0;
        if (pendingCount   < 0) pendingCount   = 0;
        if (deliveredCount < 0) deliveredCount = 0;
        if (failedCount    < 0) failedCount    = 0;
        updateStatusBarUI();
    }

    // 刷新状态栏 UI 文案（compact format: delivered/pending/uploading/failed）
    private void updateStatusBarUI() {
        // Compact format: delivered/pending/uploading/failed
        String compact = deliveredCount + "/" + pendingCount + "/" + uploadingCount + "/" + failedCount;
        if (statusSummaryText != null) {
            statusSummaryText.setText(compact);
            revealStatusThenAutoFade();
        }
    }

    // Subtle auto-fade for compact status bar after reveal
    private void revealStatusThenAutoFade() {
        if (statusSummaryText == null) return;
        statusSummaryText.animate().cancel();
        statusSummaryText.setAlpha(1f);
        statusSummaryText.animate()
                .alpha(0.35f)      // keep it subtle after a short time
                .setStartDelay(2500)
                .setDuration(500)
                .start();
    }

    // 2c: Show dialog explaining the compact status bar numbers
    private void showStatusLegendDialog() {
        Activity act = getActivity();
        if (act == null) return;
        new AlertDialog.Builder(act)
                .setTitle("状态说明")
                .setMessage("顺序为：\n已送达 / 派送中 / 上传中 / 无法送达\n\n例如：30/29/29/33")
                .setPositiveButton("知道了", null)
                .show();
    }


    // Removed: addMiniToolbarToMap, makeTinyButton, addDivider (now using XML include)

    public void centerOnMyLocation() {
        if (googleMap == null) return;
        Location loc = (mLastLocation != null) ? mLastLocation : (mSmartLocationManager != null ? mSmartLocationManager.getLastSmoothedLocation() : null);
        if (loc == null) {
            Toast.makeText(requireContext(), "暂无定位", Toast.LENGTH_SHORT).show();
            return;
        }
        LatLng me = new LatLng(loc.getLatitude(), loc.getLongitude());
        CameraPosition current = googleMap.getCameraPosition();
        googleMap.animateCamera(CameraUpdateFactory.newLatLngZoom(me, current.zoom <= 0 ? DEFAULT_ZOOM_LEVEL : current.zoom));
        // 清除浏览锁，恢复跟随
        browseLock = false;
        lastCameraUpdateCoord = me;
        lastCameraUpdateTime = System.currentTimeMillis();
        if (loc.hasBearing()) lastCameraBearing = loc.getBearing();
    }

    public void toggleMapType(android.widget.ImageButton btn) {
        if (googleMap == null) return;
        int type = googleMap.getMapType();
        if (type == GoogleMap.MAP_TYPE_NORMAL) {
            googleMap.setMapType(GoogleMap.MAP_TYPE_SATELLITE);
            // 可选：切换图标以示区别
            // btn.setImageResource(android.R.drawable.ic_menu_mapmode);
        } else {
            googleMap.setMapType(GoogleMap.MAP_TYPE_NORMAL);
            // 可选：恢复默认图标
            // btn.setImageResource(android.R.drawable.ic_menu_mapmode);
        }
    }
    private void bindToolbarMenu() {
        if (mToolbar == null) return;
        Menu menu = mToolbar.getMenu();
        if (menu != null) menu.clear();
        mToolbar.inflateMenu(R.menu.map_menu);

        mToolbar.setOnMenuItemClickListener(item -> {
            int id = item.getItemId();
            if (id == R.id.menu_refresh) {
                // 刷新派送中（bDeliveryTask = true）
                requestAndRefreshMarkers(true);
                return true;
            } else if (id == R.id.menu_unscanned) {
                // 未扫描（bDeliveryTask = false）
                requestAndRefreshMarkers(false);
                return true;
            }
            return false;
        });
    }
}
