package com.hf.easydelivery.view;

import static com.hf.easydelivery.R.*;

import android.Manifest;
import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.content.Context;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.util.Pair;
import android.graphics.Typeface;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.style.AbsoluteSizeSpan;
import android.text.style.ForegroundColorSpan;
import android.text.style.StyleSpan;
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
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ImageAnalysis;
import androidx.camera.core.Preview;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.view.PreviewView;
import androidx.core.content.ContextCompat;
import androidx.core.view.MenuHost;
import androidx.core.view.MenuProvider;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.Lifecycle;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.button.MaterialButtonToggleGroup;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.mlkit.vision.barcode.BarcodeScanner;
import com.google.mlkit.vision.barcode.BarcodeScannerOptions;
import com.google.mlkit.vision.barcode.BarcodeScanning;
import com.google.mlkit.vision.barcode.common.Barcode;
import com.google.mlkit.vision.common.InputImage;
import com.hf.courierservice.apihelper.FileLog;
import com.hf.easydelivery.R;
import com.hf.easydelivery.ResourceMgr;
import com.hf.easydelivery.bean.ScanItem;
import com.hf.easydelivery.common.PermissionUtils;
import com.hf.easydelivery.common.TokenRefresher;
import com.hf.easydelivery.common.Utils;
import com.hf.easydelivery.dao.DeliveryInfo;
import com.hf.easydelivery.event.Event;
import com.hf.easydelivery.event.EventConstant;
import com.hf.easydelivery.event.Subscriber;
import com.hf.easydelivery.view.Adapter.RecentScansAdapter;
import com.hf.easydelivery.view.model.ScanViewModel;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class ScanFragment extends Fragment implements Subscriber {

    private static final String TAG = "ScanFragment";

    // --- 视图和适配器 ---
    private PreviewView previewView;
    private TextView tvProgress, tvPackageNumber, tvSubmitting;
    private RecyclerView rvRecentScans;
    private RecentScansAdapter adapter;
    private final List<ScanItem> recentScans = new ArrayList<>(); // 始终作为适配器的数据源
    private MaterialButtonToggleGroup segmented;
    private MaterialButton btnUnscanned, btnScanned;
    private FrameLayout flProgressOverlay;
    private ProgressBar pbSubmitting;

    // --- ViewModel: 业务逻辑和状态管理的核心 ---
    private ScanViewModel scanViewModel;

    // --- 相机和扫描器 ---
    private BarcodeScanner barcodeScanner;
    private ExecutorService cameraExecutor;
    private boolean cameraWasBound = false;
    private long lastDetectTime = 0;
    private final long DEBOUNCE_MS = 1000;
    private boolean testMode = false;

    // --- 其他 ---
    private Vibrator vibrator;
    private TokenRefresher tokenRefresher;
private static final long REFRESH_INTERVAL = 5 * 60 * 1000L;
    // 仅在第一次进入页面拉取数据 / 仅在首次且有未扫时提示是否开启相机
    private boolean hasLoadedOnce = false;
    private boolean firstPrompt = true;

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.activity_scan, container, false);

        // --- 核心改动：初始化 ViewModel ---
        scanViewModel = new ViewModelProvider(requireActivity()).get(ScanViewModel.class);

        // 初始化视图控件
        setupViews(view);

        // 初始化相机和扫描器相关
        setupCameraAndScanner();

        // --- 核心改动：设置所有 LiveData 的观察者 ---
        observeViewModel();

        // 设置菜单
        setupMenu(view);

        // --- 首次进入时只加载一次数据（避免每次进入都耗时拉取） ---
        if (!hasLoadedOnce) {
            hasLoadedOnce = true;
            scanViewModel.loadTodayScannedFromDb();    // 从 DB 读取今日已扫（含已提交）
            scanViewModel.prepareScanBatchOnEnter();   // 进入页面时创建扫描批次
            scanViewModel.queryUnscanned();            // 拉取未扫描数据（网络）
        }

        // 订阅数据就绪事件，通知 ViewModel 刷新
        ResourceMgr.getInstance().getPublisher().subscribe(EventConstant.EVENT_DELIVERY_DATA_READY, this);
        // Token 刷新器，现在调用 ViewModel 的方法
        tokenRefresher = new TokenRefresher(REFRESH_INTERVAL);
        tokenRefresher.start(() -> scanViewModel.refreshBatchId());

        return view;
    }

    private void setupViews(View view) {
        previewView = view.findViewById(id.previewView);
        tvProgress = view.findViewById(id.tvProgress);
        tvPackageNumber = view.findViewById(id.tvPackageNumber);
        rvRecentScans = view.findViewById(id.rvRecentScans);
        segmented = view.findViewById(R.id.segmented);
        btnUnscanned = view.findViewById(R.id.btnUnscanned);
        btnScanned = view.findViewById(R.id.btnScanned);
        flProgressOverlay = view.findViewById(R.id.flProgressOverlay);
        pbSubmitting = view.findViewById(R.id.pbSubmitting);
        tvSubmitting = view.findViewById(R.id.tvSubmitting);

        renderScanResult(null);

        adapter = new RecentScansAdapter(recentScans);
        rvRecentScans.setLayoutManager(new LinearLayoutManager(getContext()));
        rvRecentScans.setAdapter(adapter);

// 让列表底部留出“可滚动”的安全区域，避免被底部导航/Tab遮挡
        rvRecentScans.setClipToPadding(false);

        ViewCompat.setOnApplyWindowInsetsListener(rvRecentScans, (v, insets) -> {
            int sysBottom = insets.getInsets(WindowInsetsCompat.Type.systemBars()).bottom;

            // 尝试获取底部导航/Tab的高度，如果有的话
            int navExtra = 0;
            View bottomNav = requireActivity().findViewById(R.id.bottom_nav); // 确保 ID 正确
            if (bottomNav != null) {
                navExtra = bottomNav.getHeight();
            }

            // 计算所需的底部内边距，取系统底部和导航栏高度中的最大值
            int desiredBottom = Math.max(sysBottom, navExtra);

            // 兜底：如果无法获取到任何有效高度，给一个默认的 72dp 间距
            if (desiredBottom < dp2px(72)) {
                desiredBottom = dp2px(72);
            }

            // 设置所有方向的内边距
            v.setPadding(dp2px(8), dp2px(8), dp2px(8), desiredBottom);

            return insets;
        });

        segmented.check(R.id.btnScanned);
        segmented.addOnButtonCheckedListener((group, checkedId, isChecked) -> {
            if (isChecked) applySegment(); // 切换时刷新列表
        });

        View tvHint = view.findViewById(R.id.tvHint);
        if (tvHint != null) {
            tvHint.setOnLongClickListener(v -> {
                testMode = !testMode;
                int msgRes = testMode ? R.string.scan_test_mode_on : R.string.scan_test_mode_off;
                Toast.makeText(requireContext(), msgRes, Toast.LENGTH_SHORT).show();
                return true;
            });
        }

        // 扫描线动画 (无改动)
        View scanLine = view.findViewById(id.scanLine);
        scanLine.post(() -> {
            float height = view.findViewById(id.viewFinderOverlay).getHeight();
            ObjectAnimator animator = ObjectAnimator.ofFloat(scanLine, "translationY", 0f, height - scanLine.getHeight());
            animator.setDuration(1500);
            animator.setRepeatMode(ValueAnimator.REVERSE);
            animator.setRepeatCount(ValueAnimator.INFINITE);
            animator.start();
        });
    }



    private int dp2px(int dp) {
        float density = getResources().getDisplayMetrics().density;
        return (int) (dp * density + 0.5f);
    }

    private void setupCameraAndScanner() {
        vibrator = (Vibrator) requireContext().getSystemService(Context.VIBRATOR_SERVICE);
        barcodeScanner = BarcodeScanning.getClient(new BarcodeScannerOptions.Builder().setBarcodeFormats(Barcode.FORMAT_ALL_FORMATS).build());
        cameraExecutor = Executors.newSingleThreadExecutor();
    }

    private void setupMenu(View root) {
        // 优先使用 Fragment 布局中的 Toolbar（如果存在）
        if (root != null) {
            androidx.appcompat.widget.Toolbar toolbar = root.findViewById(R.id.scantoolbar);
            if (toolbar != null) {
                // 如果布局里已经通过 app:menu 指定了菜单，这里只需要挂点击监听
                toolbar.setOnMenuItemClickListener(item -> {
                    final int id = item.getItemId();
                    FileLog.i(TAG, "toolbar menu clicked: " + getResources().getResourceEntryName(id));
                    if (id == R.id.action_query_unscanned) {
                        scanViewModel.queryUnscanned();
                        Toast.makeText(getContext(), R.string.scan_query_unscanned_toast, Toast.LENGTH_SHORT).show();
                        return true;
                    } else if (id == R.id.action_submit_offline) {
                        submitOfflineWithPrecheck();
                        return true;
                    } else if (id == R.id.action_generate_report) {
                        confirmGenerateReport();
                        return true;
                    } else if (id == R.id.action_begin_scan) {
                        startCameraIfNeeded();
                        return true;
                    }


                    return false;
                });
                return; // 使用 Fragment 自己的 Toolbar 时，无需再向 Activity 注册菜单
            }
        }

        // 否则回退到 Activity 的 MenuHost 方案
        MenuHost menuHost = requireActivity();
        menuHost.addMenuProvider(new MenuProvider() {
            @Override
            public void onCreateMenu(@NonNull Menu menu, @NonNull MenuInflater menuInflater) {
                menuInflater.inflate(R.menu.scan_actions, menu);
            }

            @Override
            public boolean onMenuItemSelected(@NonNull MenuItem item) {
                int id = item.getItemId();
                FileLog.i(TAG, "activity menu clicked: " + getResources().getResourceEntryName(id));
                if (id == R.id.action_query_unscanned) {
                    scanViewModel.queryUnscanned();
                    Toast.makeText(getContext(), R.string.scan_query_unscanned_toast, Toast.LENGTH_SHORT).show();
                    return true;
                } else if (id == R.id.action_submit_offline) {
                    submitOfflineWithPrecheck();
                    return true;
                } else if (id == R.id.action_generate_report) {
                    confirmGenerateReport();
                    return true;
                }
                return false;
            }
        }, getViewLifecycleOwner(), Lifecycle.State.RESUMED);
    }

    /**
     * 新增：集中设置所有 LiveData 的观察者，来响应 ViewModel 的数据变化并更新 UI
     */
    private void observeViewModel() {
        // 观察【已扫描列表】的变化
        scanViewModel.getScannedListLive().observe(getViewLifecycleOwner(), scannedList -> {
            btnScanned.setText(getString(R.string.scan_scanned_count, scannedList.size()));
            applySegment(); // 刷新列表显示
            // 更新顶部扫描结果
            if (scannedList != null && !scannedList.isEmpty()) {
                ScanItem lastScanned = scannedList.get(0);
                renderScanResult(lastScanned);
            } else {
                renderScanResult(null);
            }
        });

        // 观察【未扫描列表】的变化
        scanViewModel.getUnscannedFilteredLive().observe(getViewLifecycleOwner(), unscannedList -> {
            btnUnscanned.setText(getString(R.string.scan_unscanned_count, unscannedList.size()));
            applySegment(); // 刷新列表显示
            // 仅在“第一次且确有未扫包裹”时提示是否开启相机（避免资源竞争）
            if (firstPrompt && !unscannedList.isEmpty()) {
                firstPrompt = false;
                Toast.makeText(getContext(),
                        getString(R.string.scan_unscanned_toast, unscannedList.size()),
                        Toast.LENGTH_LONG).show();
                startCameraIfNeeded();
            }
        });

        // 观察【扫描计数】和【总数】的变化，来更新进度文本
        scanViewModel.getScannedCountLive().observe(getViewLifecycleOwner(), this::updateProgressText);
        scanViewModel.getTotalCountLive().observe(getViewLifecycleOwner(), this::updateProgressText);

        // 注：ViewModel 暴露的是 com.hf.easydelivery.event.Event<T>，该类不包含 getContentIfNotHandled()
        // 因此这里使用 getMessage() 读取实际负载。
        // 观察【Toast 消息】事件（使用项目自带 Event#getMessage()，避免依赖 getContentIfNotHandled）
        scanViewModel.getToastMessage().observe(getViewLifecycleOwner(), event -> {
            if (event == null) return;
            Object payload = event.getMessage();
            if (payload instanceof String) {
                Toast.makeText(getContext(), (String) payload, Toast.LENGTH_SHORT).show();
            }
        });

        // 观察【重复扫描】事件（同样通过 Event#getMessage() 读取负载）
        scanViewModel.getDuplicateScanEvent().observe(getViewLifecycleOwner(), event -> {
            if (event == null) return;
            Object payload = event.getMessage();
            if (payload instanceof Pair) {
                @SuppressWarnings("unchecked")
                Pair<String, String> data = (Pair<String, String>) payload;
                String pkgValue = data.second == null ? "" : data.second;
                String msg = getString(R.string.scan_duplicate_toast, pkgValue);
                Toast.makeText(getContext(), msg, Toast.LENGTH_SHORT).show();
                renderScanResult(new ScanItem(pkgValue, data.first, false, true));
            }
        });

        scanViewModel.getScanSuccessEvent().observe(getViewLifecycleOwner(), event -> {
            if (event == null) return;
            if (Boolean.TRUE.equals(event.getMessage())) {
                playScanHaptic();
            }
        });

        // 观察【提交状态】的变化，来控制进度条浮层的显隐
        scanViewModel.getSubmissionState().observe(getViewLifecycleOwner(), state -> {
            switch (state) {
                case SUBMITTING:
                    flProgressOverlay.setVisibility(View.VISIBLE);
                    break;
                case IDLE:
                case COMPLETE:
                case FAILED:
                    flProgressOverlay.setVisibility(View.GONE);
                    break;
            }
        });

        // 观察【提交进度】的变化，来更新进度条
        scanViewModel.getSubmissionProgress().observe(getViewLifecycleOwner(), progress -> {
            int done = progress.first;
            int total = progress.second;
            pbSubmitting.setMax(total);
            pbSubmitting.setProgress(done);
            tvSubmitting.setText(getString(R.string.scan_submitting_format, done, total));
        });
    }

    /**
     * 更新进度文本，由 LiveData 观察者调用
     */
    private void updateProgressText(Integer count) {
        Integer scanned = scanViewModel.getScannedCountLive().getValue();
        Integer total = scanViewModel.getTotalCountLive().getValue();
        if (scanned != null && total != null) {
            tvProgress.setText(getString(R.string.scan_progress_format, scanned, total));
        }
    }

    /**
     * 根据当前选择的分段（已扫/未扫），刷新 RecyclerView 的数据
     */
    private void applySegment() {
        recentScans.clear();
        boolean isUnscannedSelected = segmented.getCheckedButtonId() == R.id.btnUnscanned;

        if (isUnscannedSelected) {
            // 从 ViewModel 获取未扫描数据
            List<DeliveryInfo> unscanned = scanViewModel.getUnscannedFilteredLive().getValue();
            if (unscanned != null) {
                for (DeliveryInfo d : unscanned) {
                    recentScans.add(new ScanItem(d.getRouteNumber(), d.getOrderSn(), false, false));
                }
            }
        } else {
            // 从 ViewModel 获取已扫描数据
            List<ScanItem> scanned = scanViewModel.getScannedListLive().getValue();
            if (scanned != null) {
                recentScans.addAll(scanned);
            }
        }
        adapter.notifyDataSetChanged();
        if (!recentScans.isEmpty()) {
            rvRecentScans.scrollToPosition(0);
        }
    }

    private void renderScanResult(@Nullable ScanItem item) {
        if (tvPackageNumber == null) return;
        Context context = getContext();
        if (context == null) return;
        String placeholder = getString(R.string.scan_placeholder);
        String packageValue = item != null && item.getPackageNo() != null ? item.getPackageNo() : placeholder;
        String waybillValue = item != null && item.getWaybillNo() != null ? item.getWaybillNo() : placeholder;
        SpannableStringBuilder builder = new SpannableStringBuilder();
        appendResultSegment(builder, context, getString(R.string.scan_label_package), packageValue, true);
        builder.append("\n");
        appendResultSegment(builder, context, getString(R.string.scan_label_waybill), waybillValue, false);
        tvPackageNumber.setText(builder);
    }

    private void appendResultSegment(SpannableStringBuilder builder, Context context,
                                     String label, String value, boolean accent) {
        int labelStart = builder.length();
        builder.append(label).append(getString(R.string.scan_label_separator));
        builder.setSpan(new ForegroundColorSpan(
                        ContextCompat.getColor(context, R.color.scan_result_label)),
                labelStart, builder.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        builder.setSpan(new AbsoluteSizeSpan(14, true),
                labelStart, builder.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);

        int valueStart = builder.length();
        builder.append(value != null ? value : getString(R.string.scan_placeholder));
        int colorRes = accent ? R.color.scan_result_accent : R.color.scan_result_value;
        builder.setSpan(new ForegroundColorSpan(ContextCompat.getColor(context, colorRes)),
                valueStart, builder.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        builder.setSpan(new StyleSpan(Typeface.BOLD), valueStart, builder.length(),
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        int valueSizeSp = accent ? 28 : 17;
        builder.setSpan(new AbsoluteSizeSpan(valueSizeSp, true),
                valueStart, builder.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
    }

    /**
     * 相机服务检测到条码后的回调
     */
    private void onBarcodeDetectedFromService(String waybillNo) {
        if (isValidWaybill(waybillNo) && isNewBarcode(waybillNo)) {
            // 仅负责将条码传递给 ViewModel
            scanViewModel.processBarcode(waybillNo);
        }
    }

    private void playScanHaptic() {
        if (vibrator != null) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator.vibrate(VibrationEffect.createOneShot(80, VibrationEffect.DEFAULT_AMPLITUDE));
            } else {
                vibrator.vibrate(80);
            }
        }
    }

    // 防抖和格式校验逻辑 (无改动)
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


    // --- 相机和权限管理 (无改动) ---

    private void startCameraIfNeeded() {
        if (!PermissionUtils.hasCameraPermission(requireContext())) {
            requestCameraPermission();
            return;
        }

        new AlertDialog.Builder(requireContext())
                .setTitle(R.string.scan_prompt_title)
                .setMessage(R.string.scan_prompt_message)
                .setPositiveButton(R.string.action_yes, (dialog, which) -> bindCameraNow())
                .setNegativeButton(R.string.action_no, null)
                .show();
    }

    private void confirmGenerateReport() {
        scanViewModel.loadPendingOfflineCount(new ScanViewModel.PendingCountCallback() {
            @Override
            public void onResult(int count) {
                if (count > 0) {
                    new AlertDialog.Builder(requireContext())
                            .setTitle(R.string.scan_confirm_submit_title)
                            .setMessage(getString(R.string.scan_confirm_submit_message_format, count))
                            .setPositiveButton(R.string.action_yes, (dialog, which) -> showGenerateReportConfirmDialog())
                            .setNegativeButton(R.string.action_cancel, null)
                            .show();
                } else {
                    showGenerateReportConfirmDialog();
                }
            }

            @Override
            public void onError(Exception e) {
                Toast.makeText(getContext(), R.string.scan_query_offline_failed, Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void showGenerateReportConfirmDialog() {
        int scannedCount = 0;
        int unscannedCount = 0;
        List<ScanItem> scanned = scanViewModel.getScannedListLive().getValue();
        List<DeliveryInfo> unscanned = scanViewModel.getUnscannedFilteredLive().getValue();
        if (scanned != null) scannedCount = scanned.size();
        if (unscanned != null) unscannedCount = unscanned.size();

        String message = getString(R.string.scan_report_confirm_message, scannedCount, unscannedCount);

        new AlertDialog.Builder(requireContext())
                .setTitle(R.string.scan_report_confirm_title)
                .setMessage(message)
                .setPositiveButton(R.string.scan_report_confirm_positive,
                        (dialog, which) -> scanViewModel.generateScanBatchReport())
                .setNegativeButton(R.string.action_cancel, null)
                .show();
    }

    private void submitOfflineWithPrecheck() {
        scanViewModel.fetchOpenScanBatch(new ScanViewModel.OpenBatchCallback() {
            @Override
            public void onResult(boolean hasOpen) {
                if (hasOpen) {
                    scanViewModel.submitOfflineScans();
                    return;
                }
                new AlertDialog.Builder(requireContext())
                        .setTitle(R.string.scan_report_closed_title)
                        .setMessage(R.string.scan_report_closed_or_invalid)
                        .setPositiveButton(R.string.action_yes, (dialog, which) ->
                                scanViewModel.createScanBatchForSubmit(new ScanViewModel.OpenBatchCallback() {
                                    @Override
                                    public void onResult(boolean created) {
                                        if (created) {
                                            scanViewModel.submitOfflineScans();
                                        }
                                    }

                                    @Override
                                    public void onError(Exception e) {
                                        Toast.makeText(getContext(), R.string.scan_create_batch_failed, Toast.LENGTH_SHORT).show();
                                    }
                                })
                        )
                        .setNegativeButton(R.string.action_cancel, null)
                        .show();
            }

            @Override
            public void onError(Exception e) {
                Toast.makeText(getContext(), R.string.scan_query_offline_failed, Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void bindCameraNow() {
        if (!PermissionUtils.hasCameraPermission(requireContext())) {
            requestCameraPermission();
            return;
        }
        FileLog.i(TAG, "bindCameraNow: attempting to bind camera");
        if (previewView == null) return;
        cameraWasBound = true;
        ListenableFuture<ProcessCameraProvider> cameraProviderFuture = ProcessCameraProvider.getInstance(requireContext());
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
                        if (image.getImage() == null) { image.close(); return; }
                        InputImage inputImage = InputImage.fromMediaImage(image.getImage(), image.getImageInfo().getRotationDegrees());
                         barcodeScanner.process(inputImage)
                                .addOnSuccessListener(barcodes -> {
                                    if (barcodes != null && !barcodes.isEmpty()) {
                                        onBarcodeDetectedFromService(barcodes.get(0).getRawValue());
                                    }
                                })
                                .addOnFailureListener(e -> FileLog.e(TAG, "Barcode analysis failed", e))
                                .addOnCompleteListener(task -> image.close());
                    } catch (Exception e) {
                        image.close();
                    }
                });

                CameraSelector cameraSelector = new CameraSelector.Builder().requireLensFacing(CameraSelector.LENS_FACING_BACK).build();
                cameraProvider.unbindAll();
                cameraProvider.bindToLifecycle(this, cameraSelector, preview, imageAnalysis);
            } catch (Exception e) {
                FileLog.e(TAG, "CameraX binding failed", e);
            }
        }, ContextCompat.getMainExecutor(requireContext()));
    }

    private final ActivityResultLauncher<String> requestPermissionLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(), isGranted -> {
                if (isGranted) {
                    startCameraIfNeeded();
                } else {
                    Toast.makeText(getContext(), R.string.scan_camera_permission_required, Toast.LENGTH_SHORT).show();
                }
            });

    private void requestCameraPermission() {
        requestPermissionLauncher.launch(Manifest.permission.CAMERA);
    }

    // --- Fragment 生命周期 (无改动) ---

    @Override
    public void onResume() {
        super.onResume();
        if (cameraWasBound) {
            bindCameraNow();
        }
    }

    @Override
    public void onPause() {
        super.onPause();
        try {
            ProcessCameraProvider.getInstance(requireContext()).get().unbindAll();
        } catch (Exception e) {
            FileLog.e(TAG, "Failed to unbind camera onPause", e);
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        if (tokenRefresher != null) tokenRefresher.stop();
        if (cameraExecutor != null && !cameraExecutor.isShutdown()) cameraExecutor.shutdown();
        if (barcodeScanner != null) barcodeScanner.close();

        ResourceMgr.getInstance().getPublisher().unsubscribe(EventConstant.EVENT_DELIVERY_DATA_READY, this);
    }

    /**
     * 订阅的事件回调
     */
    @Override
    public void receive(Event event) {
        if (event.getEventType().equals(EventConstant.EVENT_DELIVERY_DATA_READY)) {
            // 收到数据就绪事件后，通知 ViewModel 从仓库刷新其数据
            scanViewModel.refreshFromRepo();
        }
    }
}
