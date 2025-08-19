package com.hf.easydelivery.view;

import static com.hf.easydelivery.R.*;

import android.Manifest;
import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.util.Log;
import com.hf.courierservice.apihelper.FileLog;
import android.util.Size;
import android.graphics.Rect;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.widget.Toolbar;
import androidx.camera.view.PreviewView;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.core.view.MenuHost;
import androidx.core.view.MenuProvider;
import androidx.lifecycle.Lifecycle;

import com.google.mlkit.vision.barcode.BarcodeScanner;
import com.google.mlkit.vision.barcode.BarcodeScannerOptions;
import com.google.mlkit.vision.barcode.BarcodeScanning;
import com.google.mlkit.vision.barcode.common.Barcode;
import com.google.mlkit.vision.common.InputImage;
import com.hf.courierservice.bean.ParcelScanData;
import com.hf.easydelivery.MyDb;
import com.hf.easydelivery.R;
import com.hf.easydelivery.ResourceMgr;
import com.hf.easydelivery.bean.ScanItem;
import com.hf.easydelivery.common.PermissionUtils;
import com.hf.easydelivery.common.TokenRefresher;
import com.hf.easydelivery.common.Utils;
import com.hf.easydelivery.component.BatchSubmitCallback;
import com.hf.easydelivery.component.BatchSubmitHelper;
import com.hf.easydelivery.core.PendingPackagesMgr;
import com.hf.easydelivery.dao.DeliveryInfo;
import com.hf.easydelivery.dao.PackageEntity;
import com.hf.easydelivery.dao.ScanRecord;
import com.hf.easydelivery.dao.ScanRecordDao;
import com.hf.easydelivery.event.Event;
import com.hf.easydelivery.event.EventConstant;
import com.hf.easydelivery.event.Subscriber;
import com.hf.easydelivery.view.Adapter.RecentScansAdapter;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.button.MaterialButtonToggleGroup;
import java.text.SimpleDateFormat;
import java.util.Locale;
import java.util.HashSet;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import androidx.lifecycle.ViewModelProvider;
import com.hf.easydelivery.view.model.*;

import androidx.camera.core.Camera;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ImageAnalysis;
import androidx.camera.core.ImageProxy;
import androidx.camera.core.Preview;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.core.content.ContextCompat;
import com.google.common.util.concurrent.ListenableFuture;

public class ScanFragment extends Fragment implements Subscriber {

    private static final String TAG = "ScanFragment";

    //
    // First‑time display & camera binding guards
    private boolean firstShown = false;

    // Track if camera was ever bound
    private boolean cameraWasBound = false;


    private PreviewView previewView;
    private TextView tvProgress, tvPackageNumber;
    private RecyclerView rvRecentScans;
    private RecentScansAdapter adapter;
    private final List<ScanItem> recentScans = new ArrayList<>();

    // Segmented toggle + data sources (Unscanned / Scanned)
    private MaterialButtonToggleGroup segmented;
    private MaterialButton btnUnscanned, btnScanned;
    private final List<DeliveryInfo> unscannedList = new ArrayList<>();
    private final List<ScanItem> scannedList = new ArrayList<>();
    private final HashSet<String> scannedWaybills = new HashSet<>();

    private int totalCount = 0, scannedCount = 0;
    private long lastDetectTime = 0;
    private final long DEBOUNCE_MS = 1000;
    // Gating config: central viewfinder area (70% x 50%) and min on-screen width ratio (≈ distance proxy)
    private static final float SCAN_BOX_WIDTH_RATIO = 0.7f;
    private static final float SCAN_BOX_HEIGHT_RATIO = 0.5f;
    private static final float MIN_BARCODE_WIDTH_RATIO = 0.2f; // ~20% of image width
    // Test mode: allow scanning smaller on-screen barcodes (e.g., computer monitor)
    private boolean testMode = false; // long-press tvHint to toggle
    private float currentMinBarcodeWidthRatio() { return testMode ? 0.05f : MIN_BARCODE_WIDTH_RATIO; }

    private BarcodeScanner barcodeScanner;
    private ExecutorService cameraExecutor;

    private boolean offlineMode = true;
    private final Map<String, String> waybillToPackageMap = new HashMap<>();
    private Vibrator vibrator;

    private BatchSubmitHelper submitHelper;

    private FrameLayout flProgressOverlay;
    private ProgressBar pbSubmitting;
    private TextView tvSubmitting;

    private ScanViewModel scanViewModel;


    private TokenRefresher tokenRefresher;
    private static final long REFRESH_INTERVAL = 5 * 60 * 1000L; // 5分钟

    // Fragment菜单支持
    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        FileLog.i(TAG, "onCreate");
        super.onCreate(savedInstanceState);
        // setHasOptionsMenu(true); // 允许Fragment使用菜单
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        FileLog.i(TAG, "onCreateView");
        View view = inflater.inflate(R.layout.activity_scan, container, false);

        // 由于Fragment无setSupportActionBar, 如需Toolbar请放在MainActivity控制
        previewView      = view.findViewById(id.previewView);
        tvProgress       = view.findViewById(id.tvProgress);
        tvPackageNumber  = view.findViewById(id.tvPackageNumber);
        rvRecentScans    = view.findViewById(id.rvRecentScans);

        adapter = new RecentScansAdapter(recentScans);
        rvRecentScans.setLayoutManager(new LinearLayoutManager(getContext()));
        rvRecentScans.setAdapter(adapter);

        segmented     = view.findViewById(R.id.segmented);
        btnUnscanned  = view.findViewById(R.id.btnUnscanned);
        btnScanned    = view.findViewById(R.id.btnScanned);

        if (segmented != null) {
            // 默认展示“已扫描”
            segmented.check(R.id.btnScanned);
            segmented.addOnButtonCheckedListener((group, checkedId, isChecked) -> {
                if (!isChecked) return;
                applySegment(checkedId == R.id.btnUnscanned ? 0 : 1);
            });
        }

        // Fallback: if Activity uses a standalone Toolbar menu, hook clicks here
        bindToolbarMenu();


        flProgressOverlay = view.findViewById(R.id.flProgressOverlay);
        pbSubmitting      = view.findViewById(R.id.pbSubmitting);
        tvSubmitting      = view.findViewById(R.id.tvSubmitting);

        // Ensure the overlay is above preview and is touch-blocking when visible
        if (flProgressOverlay != null) {
            flProgressOverlay.bringToFront();
            flProgressOverlay.setClickable(true);
            flProgressOverlay.setFocusable(true);
        }

        View tvHint = view.findViewById(R.id.tvHint);
        if (tvHint != null) {
            tvHint.setOnLongClickListener(v -> {
                testMode = !testMode;
                Utils.showOnUi(requireContext(), testMode ? "测试模式：允许扫描屏幕上较小条码" : "测试模式关闭");
                return true;
            });
        }

        vibrator = (Vibrator) requireContext().getSystemService(Context.VIBRATOR_SERVICE);

        BarcodeScannerOptions options = new BarcodeScannerOptions.Builder()
                .setBarcodeFormats(Barcode.FORMAT_ALL_FORMATS)
                .build();
        barcodeScanner = BarcodeScanning.getClient(options);

        cameraExecutor = Executors.newSingleThreadExecutor();


        View scanLine = view.findViewById(id.scanLine);
        scanLine.post(() -> {
            float height = view.findViewById(id.viewFinderOverlay).getHeight();
            ObjectAnimator animator = ObjectAnimator.ofFloat(
                    scanLine, "translationY", 0f, height - scanLine.getHeight()
            );
            animator.setDuration(1500);
            animator.setRepeatMode(ValueAnimator.REVERSE);
            animator.setRepeatCount(ValueAnimator.INFINITE);
            animator.start();
        });

        scanViewModel = new ViewModelProvider(requireActivity()).get(ScanViewModel.class);

        submitHelper = new BatchSubmitHelper(
                ResourceMgr.getInstance().getmMydb().getScanRecordDao(),
                ResourceMgr.getInstance().getCourierService(),
                ResourceMgr.getInstance().getDbHandler()
        );

        tokenRefresher = new TokenRefresher(REFRESH_INTERVAL);
        tokenRefresher.start(() -> {
            ResourceMgr.getInstance().getDeliveryinfoMgr().fechScanBatchId();
        });


        // Use MenuHost API (recommended) instead of deprecated Fragment options menu
        MenuHost menuHost = requireActivity();
        menuHost.addMenuProvider(new MenuProvider() {
            @Override
            public void onCreateMenu(@NonNull Menu menu, @NonNull MenuInflater menuInflater) {
                FileLog.d(TAG, "MenuProvider.onCreateMenu invoked");
                menuInflater.inflate(R.menu.scan_actions, menu);
            }

            @Override
            public boolean onMenuItemSelected(@NonNull MenuItem item) {
                int id = item.getItemId();
                if (id == R.id.action_query_unscanned) {
                    queryUnscanned();
                    return true;

                } else if (id == R.id.action_submit_offline) {
                    submitScansOffline();
                    return true;
                }
                return false;
            }
        }, getViewLifecycleOwner(), Lifecycle.State.RESUMED);

        ResourceMgr.getInstance().getPublisher().subscribe(EventConstant.EVENT_DELIVERY_DATA_READY, this);

        return view;
    }


    // Start camera only when needed, and only after user confirmation if unscannedList has items
    private void startCameraIfNeeded() {
        if (unscannedList == null || unscannedList.isEmpty()) {

            return;
        }
        // 检查相机权限
        if (!PermissionUtils.hasCameraPermission(requireContext())) {
            requestCameraPermission();
            return;
        }

        // 有未扫描包裹 → 询问是否开启相机
        new AlertDialog.Builder(requireContext())
                .setTitle("提示")
                .setMessage("您有未扫描包裹，是否开始扫描？")
                .setPositiveButton("是", (dialog, which) -> bindCameraNow())
                .setNegativeButton("否", (dialog, which) -> {
                    Log.d("ScanFragment", "用户选择暂不启动相机扫描");
                })
                .setCancelable(false)
                .show();
    }

    /**
     * Actually bind the camera if needed, used after user confirms.
     * Checks CAMERA permission before proceeding.
     */
    private void bindCameraNow() {
        // 检查相机权限
        if (!PermissionUtils.hasCameraPermission(requireContext())) {
            requestCameraPermission();
            return;
        }

        FileLog.i(TAG, "bindCameraNow: CAMERA permission granted, attempting to bind camera");
        if (previewView == null) return;
        cameraWasBound = true;
        ListenableFuture<ProcessCameraProvider> cameraProviderFuture =
                ProcessCameraProvider.getInstance(requireContext());
        cameraProviderFuture.addListener(() -> {
            try {
                ProcessCameraProvider cameraProvider = cameraProviderFuture.get();

                Preview preview = new Preview.Builder().build();
                preview.setSurfaceProvider(previewView.getSurfaceProvider());

                ImageAnalysis imageAnalysis = new ImageAnalysis.Builder()
                        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                        .build();

                imageAnalysis.setAnalyzer(cameraExecutor, image -> {
                    try {
                        InputImage inputImage =
                                InputImage.fromMediaImage(image.getImage(), image.getImageInfo().getRotationDegrees());
                        barcodeScanner.process(inputImage)
                                .addOnSuccessListener(barcodes -> {
                                    if (barcodes != null && !barcodes.isEmpty()) {
                                        for (Barcode barcode : barcodes) {
                                            String rawValue = barcode.getRawValue();
                                            if (rawValue != null) {
                                                onBarcodeDetectedFromService(rawValue);
                                            }
                                        }
                                    }
                                })
                                .addOnFailureListener(e -> FileLog.e(TAG, "Barcode analysis failed", e))
                                .addOnCompleteListener(task -> image.close());
                    } catch (Exception e) {
                        image.close();
                    }
                });

                CameraSelector cameraSelector = new CameraSelector.Builder()
                        .requireLensFacing(CameraSelector.LENS_FACING_BACK)
                        .build();

                cameraProvider.unbindAll();
                cameraProvider.bindToLifecycle(this, cameraSelector, preview, imageAnalysis);
                FileLog.i(TAG, "bindCameraNow: CameraX bound successfully");
            } catch (Exception e) {
                FileLog.e(TAG, "CameraX binding failed", e);
            }
        }, ContextCompat.getMainExecutor(requireContext()));
    }

    /**
     * (Re)binds the Toolbar menu click listener and inflates menu if needed.
     */
    private void bindToolbarMenu() {
        Toolbar toolbar = requireActivity().findViewById(R.id.scantoolbar);
        if (toolbar != null) {
            if (toolbar.getMenu().findItem(R.id.action_query_unscanned) == null) {
                toolbar.getMenu().clear();
                toolbar.inflateMenu(R.menu.scan_actions);
            }
            toolbar.setOnMenuItemClickListener(item -> {
                int id = item.getItemId();
                FileLog.d(TAG, "Toolbar menu click: " + item.getTitle());
                if (id == R.id.action_query_unscanned) {
                    queryUnscanned();
                    return true;
                } else if (id == R.id.action_submit_offline) {
                    submitScansOffline();
                    return true;
                }
                return false;
            });
        } else {
            FileLog.w(TAG, "Toolbar not found; menu clicks won't be handled here.");
        }
    }

    // Fragment推荐的权限申请新方式
    private final ActivityResultLauncher<String> requestPermissionLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(), isGranted -> {
                if (isGranted) {
                    FileLog.i(TAG, "requestPermissionLauncher: CAMERA permission granted by user");
                    // 用户同意权限，根据调用来源，通常是 startCameraIfNeeded 或 bindCameraNow
                    // 这里直接调用 startCameraIfNeeded，用户流程会继续
                    startCameraIfNeeded();
                } else {
                    FileLog.w(TAG, "requestPermissionLauncher: CAMERA permission denied by user");
                    Toast.makeText(getContext(), "需要相机权限", Toast.LENGTH_SHORT).show();
                }
            });

    private void requestCameraPermission() {
        if (PermissionUtils.hasCameraPermission(requireContext())) {
            FileLog.i(TAG, "Already has CAMERA permission");
            startCameraIfNeeded();
        } else {
            requestPermissionLauncher.launch(Manifest.permission.CAMERA);
        }
    }

    private void preloadOfflineData() {
        ResourceMgr.getInstance().getDeliveryinfoMgr().getDeliveryInfo(ResourceMgr.getInstance().getLoginInfo().loginId, false);
        ResourceMgr.getInstance().getDeliveryinfoMgr().fechScanBatchId();
    }



    private void queryUnscanned() {
        FileLog.i(TAG, "queryUnscanned: start");
        preloadOfflineData();

        flProgressOverlay.setVisibility(View.VISIBLE);
        pbSubmitting.setProgress(0);
        tvSubmitting.setText("查询中");

        new Handler().postDelayed(() -> {
            flProgressOverlay.setVisibility(View.GONE);
            Utils.showOnUi(requireContext(), "未扫描包裹：" + unscannedList.size() + " 件");
            FileLog.i(TAG, "queryUnscanned: end, unscannedList.size()=" + unscannedList.size());
        }, 1500);
    }

    // Callback for CameraService analyzer
    private void onBarcodeDetectedFromService(String waybillNo) {
        if (isValidWaybill(waybillNo) && isNewBarcode(waybillNo)) {
            onBarcodeDetected(waybillNo);
        }
    }

    private boolean isNewBarcode(String raw) {
        long now = System.currentTimeMillis();
        if (now - lastDetectTime < DEBOUNCE_MS) return false;
        String last = recentScans.isEmpty() ? "" : recentScans.get(0).getWaybillNo();
        if (raw.equals(last)) return false;
        lastDetectTime = now;
        return true;
    }

    private boolean isValidWaybill(String raw) {
        return raw != null && raw.matches("[A-Za-z0-9]{8,}");
    }

    private void onBarcodeDetected(String waybillNo) {
        // If already scanned, toast + show in the package label and return
        if (scannedWaybills.contains(waybillNo)) {
            final String pkg = waybillToPackageMap.containsKey(waybillNo)
                    ? waybillToPackageMap.get(waybillNo)
                    : findPackageNoInScannedList(waybillNo);
            requireActivity().runOnUiThread(() -> {
                String msg = "该包裹已扫\n包裹号：" + (pkg == null ? "" : pkg);
                Toast.makeText(requireContext(), msg, Toast.LENGTH_SHORT).show();
                tvPackageNumber.setText("包裹号：" + (pkg == null ? "" : pkg) + "\n" + "运单号：" + waybillNo);
            });
            return;
        }

        if (vibrator != null) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator.vibrate(
                        VibrationEffect.createOneShot(200, VibrationEffect.DEFAULT_AMPLITUDE)
                );
            } else {
                vibrator.vibrate(200);
            }
        }

        if (offlineMode)
        {
            long scanBatchId = ResourceMgr.getInstance().getDeliveryinfoMgr().getScanBatchId();
            int  status      = ResourceMgr.getInstance().getDeliveryinfoMgr().getScanBatchStatus(); // 0=open
            if (scanBatchId < 1 || status != 0)
            {
                Utils.showOnUi(requireContext(), "扫描报告已关闭或批次无效，请使用总部App扫描任意包裹打开报告，然后点击右上角“查询”。");
                return;
            }

            final DeliveryInfo byTrackingNo = ResourceMgr.getInstance().getDeliveryinfoMgr().getByTrackingNo(waybillNo);
            if (byTrackingNo  != null)
                requireActivity().runOnUiThread(() ->handleResult(waybillNo, byTrackingNo.getRouteNumber()));
            else {
                if (ResourceMgr.getInstance().getDeliveryinfoMgr().size() == 0) {
                    ResourceMgr.getInstance().getDeliveryinfoMgr().getDeliveryInfo(ResourceMgr.getInstance().getLoginInfo().loginId,
                            false);
                    Utils.showOnUi(requireContext(), "数据加载中，请稍后重扫");
                } else
                    Utils.showOnUi(requireContext(), "不是您的包裹");
            }
        }
        else
            requireActivity().runOnUiThread(() -> handleResult(waybillNo, "111"));
    }

    private String findPackageNoInScannedList(String waybillNo) {
        for (ScanItem si : scannedList) {
            if (waybillNo.equals(si.getWaybillNo())) {
                return si.getPackageNo();
            }
        }
        return null;
    }

    private void handleResult(String waybillNo, String packageNo) {
        FileLog.i(TAG, "handleResult: scanned successfully, waybillNo=" + waybillNo + ", packageNo=" + packageNo);
        // 顶部展示（大号包裹号）
        tvPackageNumber.setText("包裹号：" + (packageNo != null ? packageNo : "—") + "\n" + "运单号：" + (waybillNo != null ? waybillNo : ""));

        // 已扫描：加入头部
        scannedWaybills.add(waybillNo);
        ScanItem item = new ScanItem(packageNo, waybillNo, false , true); // Not uploaded yet
        // Persist mapping for future duplicate detection UI
        waybillToPackageMap.put(waybillNo, packageNo);
        scannedList.add(0, item);

        // 未扫描：移除同运单
        for (int i = 0; i < unscannedList.size(); i++) {
            DeliveryInfo d = unscannedList.get(i);
            if (waybillNo.equals(d.getOrderSn())) {
                unscannedList.remove(i);
                break;
            }
        }

        // 进度与分段计数
        scannedCount = scannedList.size();
        totalCount   = ResourceMgr.getInstance().getDeliveryinfoMgr().size();
        tvProgress.setText("已扫描 " + scannedCount + " / " + totalCount);
        refreshSegmentCounts();

        // 刷新当前显示数据（recentScans 作为适配器数据源）
        applySegment(segmented != null && segmented.getCheckedButtonId() == R.id.btnUnscanned ? 0 : 1);

        // 入库
        Short packageNoShort = null;
        try {
            if (packageNo != null) packageNoShort = Short.parseShort(packageNo);
        } catch (NumberFormatException e) { /* ignore */ }
        Long scanBatchId = ResourceMgr.getInstance().getDeliveryinfoMgr().getScanBatchId();
        saveScanRecord(waybillNo , packageNoShort , scanBatchId);
    }

    //
    // Only bind camera if needed, and run "first shown" logic once
    @Override
    public void onResume() {
        FileLog.i(TAG, "onResume");
        super.onResume();
        bindToolbarMenu();
        if (scanViewModel.shouldDoFirstEnter()) {
            if (offlineMode) {
                preloadOfflineData();
            }
            loadOfflineRecordsForToday();
            showUnscannedHintOnce();
        }
        if (cameraWasBound) {
            bindCameraNow();
        }
    }

    @Override
    public void onPause() {
        FileLog.i(TAG, "onPause");
        super.onPause();
        // Unbind CameraX to release the camera
        try {
            ProcessCameraProvider cameraProvider = ProcessCameraProvider.getInstance(requireContext()).get();
            cameraProvider.unbindAll();
        } catch (Exception e) {
            FileLog.e(TAG, "Failed to unbind camera onPause", e);
        }
    }

    @Override
    public void onDestroyView() {
        FileLog.i(TAG, "onDestroyView");
        if (tokenRefresher != null) {
            tokenRefresher.stop();
        }
        cameraExecutor.shutdown();
        barcodeScanner.close();
        submitHelper.shutdown();
        ResourceMgr.getInstance().getPublisher().unsubscribe(EventConstant.EVENT_DELIVERY_DATA_READY, this);
        // Unbind CameraX to release the camera
        try {
            ProcessCameraProvider cameraProvider = ProcessCameraProvider.getInstance(requireContext()).get();
            cameraProvider.unbindAll();
        } catch (Exception e) {
            FileLog.e(TAG, "Failed to unbind camera onDestroyView", e);
        }
        super.onDestroyView();
    }

    private void saveScanRecord(String waybillNo, Short packageNo , Long scanBatchId)
    {
        FileLog.d(TAG, "saveScanRecord: inserting waybillNo=" + waybillNo + ", packageNo=" + packageNo + ", scanBatchId=" + scanBatchId);
        long now = System.currentTimeMillis();
        ScanRecord rec = new ScanRecord(
                now,
                ResourceMgr.getInstance().getLoginInfo().loginId,
                waybillNo,
                packageNo,
                scanBatchId,
                false
        );

        final Handler dbHandler = ResourceMgr.getInstance().getDbHandler();

        dbHandler.post(() -> {
            final ScanRecordDao scanRecordDao = ResourceMgr.getInstance().getmMydb().getScanRecordDao();
            scanRecordDao.insert(rec);
        });
    }

    private void submitScansOffline() {
        FileLog.i(TAG, "submitScansOffline: start");
        // 扫描报告状态校验：未打开则阻止提交（与 iOS 一致）
        Long scanBatchIdCheck = ResourceMgr.getInstance().getDeliveryinfoMgr().getScanBatchId();
        if (scanBatchIdCheck == null || scanBatchIdCheck < 1) {
            FileLog.w(TAG, "submitScansOffline: scanBatchIdCheck invalid: " + scanBatchIdCheck);
            Utils.showOnUi(requireContext(), "扫描报告已关闭，请使用总部App扫描任意包裹打开报告，然后点击右上角“查询”。");
            return;
        }

        if (ResourceMgr.getInstance().getDeliveryinfoMgr().getScanBatchStatus() != 0 ) {
            FileLog.w(TAG, "submitScansOffline: scanBatchStatus invalid");
            Utils.showOnUi(requireContext(), "扫描报告已关闭或批次无效，请使用总部App扫描任意包裹打开报告，然后点击右上角“查询”。");
            return;
        }

        final Handler dbHandler = ResourceMgr.getInstance().getDbHandler();

        dbHandler.post(() -> {
            final ScanRecordDao scanRecordDao = ResourceMgr.getInstance().getmMydb().getScanRecordDao();

            String strToday = Utils.getCurrentDate();

            Integer driverId = ResourceMgr.getInstance().getLoginInfo().loginId;
            List<ScanRecord> list = scanRecordDao.loadByDate(strToday, false , driverId);
            int total = list.size();
            requireActivity().runOnUiThread(() -> {
                if (total == 0)
                    Utils.showOnUi(requireContext() , "您没需要提交的已扫包裹数据");
                else
                    new AlertDialog.Builder(requireContext())
                            .setTitle("扫描提交")
                            .setMessage(total + " 条未上传记录，是否提交？")
                            .setPositiveButton("提交", (dialog, which) -> batchSubmit(list))
                            .setNegativeButton("取消", null)
                            .show();
            });
        });
    }

    private void batchSubmit(List<ScanRecord> list) {
        FileLog.i(TAG, "batchSubmit: start, list.size()=" + (list == null ? 0 : list.size()));
        requireActivity().runOnUiThread(() -> {
            pbSubmitting.setMax(list.size());
            pbSubmitting.setProgress(0);
            tvSubmitting.setText("提交中 0 / " + list.size());
            flProgressOverlay.setVisibility(View.VISIBLE);
        });

        submitHelper.submit(list, new BatchSubmitCallback() {
            @Override
            public void onProgress(int done, int total, int success, int fail) {
                FileLog.d(TAG, "batchSubmit: progress " + done + "/" + total + ", success=" + success + ", fail=" + fail);
                requireActivity().runOnUiThread(() -> {
                    pbSubmitting.setProgress(done);
                    tvSubmitting.setText(
                            String.format("提交中 %d / %d", done, total)
                    );
                });
            }

            @Override
            public void onSingleComplete(String trackingNo) {
                requireActivity().runOnUiThread(() -> {

                        for (ScanItem item : scannedList) {
                            if (item.getWaybillNo().equals(trackingNo)) {
                                item.setUploaded(true);
                            }
                        }
                    adapter.notifyDataSetChanged();
                });
            }

            @Override
            public void onComplete(int successCount, int failCount) {
                FileLog.i(TAG, "batchSubmit: complete, success=" + successCount + ", fail=" + failCount);
                requireActivity().runOnUiThread(() -> {
                    flProgressOverlay.setVisibility(View.GONE);
                    Utils.showOnUi(requireContext() , "提交完成，成功：" + successCount
                            + "，失败：" + failCount);
                    tvProgress.setText("已扫描 " + scannedCount + " / " + totalCount);
                });
            }

            @Override
            public void onFail(Exception e) {
                FileLog.e(TAG, "batchSubmit: failed", e);
                // showLoginDialog();
                Toast.makeText(requireContext(), "登录失效，请重新登录", Toast.LENGTH_SHORT).show();
            }
        });
    }

    @Override
    public void receive(Event event) {
        FileLog.i(TAG, "receive: event=" + (event == null ? "null" : event.getEventType()));
        totalCount = ResourceMgr.getInstance().getDeliveryinfoMgr().size();
        tvProgress.setText("已扫描 " + scannedCount + " / " + totalCount);
        // 用已扫描集合过滤未扫描列表，并刷新分段计数
        List<DeliveryInfo> src = ResourceMgr.getInstance().getDeliveryinfoMgr().getListDeliveryInfo();
        unscannedList.clear();
        for (DeliveryInfo d : src) {
            if (!scannedWaybills.contains(d.getOrderSn())) unscannedList.add(d);
        }
        refreshSegmentCounts();
        applySegment(segmented != null && segmented.getCheckedButtonId() == R.id.btnUnscanned ? 0 : 1);
        // First-time-show logic: start camera if needed
        if (!firstShown) {
            firstShown = true;
            startCameraIfNeeded();

            showUnscannedHintOnce();
        }
    }


    private void loadOfflineRecordsForToday() {
        FileLog.i(TAG, "loadOfflineRecordsForToday: begin DB load");
        final String dateStr = new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(new java.util.Date());
        final ScanRecordDao scanRecordDao = ResourceMgr.getInstance().getmMydb().getScanRecordDao();
        final Integer driverId = ResourceMgr.getInstance().getLoginInfo().loginId;

        ResourceMgr.getInstance().getDbHandler().post(() -> {
            List<ScanRecord> pending;
            List<ScanRecord> uploaded;
            try {
                pending  = scanRecordDao.loadByDate(dateStr, false, driverId);
            } catch (Exception e) {
                pending = new ArrayList<>();
            }
            try {
                uploaded = scanRecordDao.loadByDate(dateStr, true, driverId);
            } catch (Exception e) {
                uploaded = new ArrayList<>();
            }
            final ArrayList<ScanRecord> all = new ArrayList<>(pending.size() + uploaded.size());
            all.addAll(pending);
            all.addAll(uploaded);

            // 构建“已扫描”数据与集合
            final ArrayList<ScanItem> items = new ArrayList<>(all.size());
            final HashSet<String> waybills = new HashSet<>();
            for (ScanRecord r : pending) {
                String w = r.trackingNo;
                String p = r.packageNo == null ? "" : String.valueOf(r.packageNo);
                items.add(new ScanItem(p, w, false , true));
                waybills.add(w);
                waybillToPackageMap.put(w, p);
            }
            for (ScanRecord r : uploaded) {
                String w = r.trackingNo;
                String p = r.packageNo == null ? "" : String.valueOf(r.packageNo);
                items.add(new ScanItem(p, w, true , true));
                waybills.add(w);
                waybillToPackageMap.put(w, p);
            }

            // 用“已扫描”集合过滤“未扫描”
            final List<DeliveryInfo> src = ResourceMgr.getInstance().getDeliveryinfoMgr().getListDeliveryInfo();
            final ArrayList<DeliveryInfo> filtered = new ArrayList<>();
            for (DeliveryInfo d : src) {
                if (!waybills.contains(d.getOrderSn())) filtered.add(d);
            }

            requireActivity().runOnUiThread(() -> {
                scannedWaybills.clear();
                scannedWaybills.addAll(waybills);

                scannedList.clear();
                scannedList.addAll(items);

                unscannedList.clear();
                unscannedList.addAll(filtered);

                totalCount = ResourceMgr.getInstance().getDeliveryinfoMgr().size();
                scannedCount = scannedList.size();
                tvProgress.setText("已扫描 " + scannedCount + " / " + totalCount);
                refreshSegmentCounts();
                applySegment(segmented != null && segmented.getCheckedButtonId() == R.id.btnUnscanned ? 0 : 1);
                FileLog.i(TAG, "loadOfflineRecordsForToday: DB load complete, scanned=" + scannedList.size() + ", unscanned=" + unscannedList.size());
            });
        });
    }
    private void refreshSegmentCounts() {
        if (btnUnscanned != null) btnUnscanned.setText("未扫描(" + unscannedList.size() + ")");
        if (btnScanned   != null) btnScanned.setText("已扫描(" + scannedList.size() + ")");
    }

    /** 切换 RecyclerView 的数据源（复用 recentScans / adapter） */
    private void applySegment(int segmentIndex) {
        recentScans.clear();
        if (segmentIndex == 0) {
            // 未扫描：DeliveryInfo -> ScanItem, not uploaded
            for (DeliveryInfo d : unscannedList) {
                recentScans.add(new ScanItem(d.getRouteNumber(), d.getOrderSn(), false,false));
            }
        } else {
            // 已扫描
            recentScans.addAll(scannedList);
        }
        adapter.notifyDataSetChanged();
        if (!recentScans.isEmpty()) {
            rvRecentScans.scrollToPosition(0);
        }
    }

    //
    // 一次性提示当前未扫数量（使用本地过滤后的列表，避免司机困惑）
    private void showUnscannedHintOnce() {
        int unscanned = unscannedList.size();
        // 若列表还未就绪，可用 DeliveryInfoMgr 的总量减去已扫估算，但不强求
        if (unscanned > 0) {
            Utils.showOnUi(requireContext(), "您有 " + unscanned + " 个包裹需要扫描");
        } else {
            Utils.showOnUi(requireContext(), "您当前没有需要扫描的包裹");
        }
    }
}

