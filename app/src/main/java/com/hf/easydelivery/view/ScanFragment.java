package com.hf.easydelivery.view;

import static com.hf.easydelivery.R.*;

import android.Manifest;
import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.content.Context;
import android.content.res.ColorStateList;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.util.Size;
import android.util.Pair;
import android.graphics.Typeface;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.style.AbsoluteSizeSpan;
import android.text.style.ForegroundColorSpan;
import android.text.style.StyleSpan;
import android.view.Gravity;
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
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ScanFragment extends Fragment implements Subscriber {

    private static final String TAG = "ScanFragment";

    // --- 视图和适配器 ---
    private PreviewView previewView;
    private TextView tvPackageNumber, tvSubmitting, tvAutoSubmitState, tvHint;
    private RecyclerView rvRecentScans;
    private RecentScansAdapter adapter;
    private final List<ScanItem> recentScans = new ArrayList<>(); // 始终作为适配器的数据源
    private MaterialButtonToggleGroup segmented;
    private MaterialButton btnUnscanned, btnScanned, btnScanMode;
    private FrameLayout flProgressOverlay;
    private ProgressBar pbSubmitting;
    private View viewFinderOverlay;
    private View scanLine;

    // --- ViewModel: 业务逻辑和状态管理的核心 ---
    private ScanViewModel scanViewModel;

    // --- 相机和扫描器 ---
    private BarcodeScanner barcodeScanner;
    private ExecutorService cameraExecutor;
    private boolean cameraWasBound = false;
    private long lastDetectTime = 0;
    private final long DEBOUNCE_MS = 1000;
    private boolean testMode = false;
    private static final Pattern WAYBILL_TOKEN_PATTERN = Pattern.compile("[A-Za-z0-9]{8,}");
    private static final float TEST_MODE_MIN_SCORE = 0.04f;
    private static final float NORMAL_MODE_MIN_SCORE = -0.10f;

    // --- 其他 ---
    private Vibrator vibrator;
    private TokenRefresher tokenRefresher;
    private TokenRefresher autoSubmitRefresher;
    private static final long REFRESH_INTERVAL = 5 * 60 * 1000L;
    private static final long AUTO_SUBMIT_INTERVAL = 30 * 1000L;
    // 仅在第一次进入页面拉取数据 / 仅在首次且有未扫时提示是否开启相机
    private boolean hasLoadedOnce = false;
    private boolean firstPrompt = true;
    private boolean manualSubmitInProgress = false;
    private final Handler uiHandler = new Handler(Looper.getMainLooper());
    private final Runnable hideAutoSubmitHintRunnable = () -> {
        if (tvAutoSubmitState != null) tvAutoSubmitState.setVisibility(View.GONE);
    };
    private final Runnable resetScanFeedbackRunnable = () ->
            renderScanFeedbackState(ScanFeedbackState.IDLE, R.string.scan_status_ready, false);
    private final Runnable resetResultCardRunnable = this::resetResultCardVisualState;

    private enum ScanFeedbackState {
        IDLE,
        CANDIDATE,
        SUCCESS,
        ERROR
    }

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
        tokenRefresher = new TokenRefresher(REFRESH_INTERVAL);
        autoSubmitRefresher = new TokenRefresher(AUTO_SUBMIT_INTERVAL);

        return view;
    }

    private void setupViews(View view) {
        previewView = view.findViewById(id.previewView);
        tvPackageNumber = view.findViewById(id.tvPackageNumber);
        rvRecentScans = view.findViewById(id.rvRecentScans);
        segmented = view.findViewById(R.id.segmented);
        btnUnscanned = view.findViewById(R.id.btnUnscanned);
        btnScanned = view.findViewById(R.id.btnScanned);
        flProgressOverlay = view.findViewById(R.id.flProgressOverlay);
        pbSubmitting = view.findViewById(R.id.pbSubmitting);
        tvSubmitting = view.findViewById(R.id.tvSubmitting);
        tvAutoSubmitState = view.findViewById(R.id.tvAutoSubmitState);
        tvHint = view.findViewById(R.id.tvHint);
        btnScanMode = view.findViewById(R.id.btnScanMode);
        viewFinderOverlay = view.findViewById(id.viewFinderOverlay);
        scanLine = view.findViewById(id.scanLine);

        renderScanResult(null);
        updateScanModeUi();
        renderScanFeedbackState(ScanFeedbackState.IDLE, R.string.scan_status_ready, false);

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

        if (btnScanMode != null) {
            btnScanMode.setOnClickListener(v -> setTestModeEnabled(!testMode, true));
        }
        if (tvHint != null) {
            tvHint.setOnLongClickListener(v -> {
                setTestModeEnabled(!testMode, true);
                return true;
            });
        }

        // 扫描线动画 (无改动)
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
                        submitOfflineWithPrecheck(true);
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
                    submitOfflineWithPrecheck(true);
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
                String message = (String) payload;
                Toast.makeText(getContext(), message, Toast.LENGTH_SHORT).show();
                if (message.equals(getString(R.string.scan_not_your_parcel))) {
                    renderScanFeedbackState(ScanFeedbackState.ERROR, R.string.scan_status_not_your, true);
                } else if (message.equals(getString(R.string.scan_data_loading))) {
                    renderScanFeedbackState(ScanFeedbackState.ERROR, R.string.scan_status_loading, true);
                } else if (message.equals(getString(R.string.scan_report_closed_or_invalid))) {
                    renderScanFeedbackState(ScanFeedbackState.ERROR, R.string.scan_status_batch_invalid, true);
                }
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
                renderScanFeedbackState(ScanFeedbackState.ERROR, R.string.scan_status_duplicate, true);
            }
        });

        scanViewModel.getScanSuccessEvent().observe(getViewLifecycleOwner(), event -> {
            if (event == null) return;
            if (Boolean.TRUE.equals(event.getMessage())) {
                playScanHaptic();
                animateResultCardSuccess();
                renderScanFeedbackState(ScanFeedbackState.SUCCESS, R.string.scan_status_success, true);
            }
        });

        // 观察【提交状态】的变化，来控制进度条浮层的显隐
        scanViewModel.getSubmissionState().observe(getViewLifecycleOwner(), state -> {
            switch (state) {
                case SUBMITTING:
                    if (manualSubmitInProgress) {
                        flProgressOverlay.bringToFront();
                        ViewCompat.setElevation(flProgressOverlay, 32f);
                        flProgressOverlay.setClickable(true);
                        flProgressOverlay.setFocusable(true);
                        flProgressOverlay.setVisibility(View.VISIBLE);
                    } else {
                        flProgressOverlay.setVisibility(View.GONE);
                    }
                    break;
                case IDLE:
                case COMPLETE:
                case FAILED:
                    flProgressOverlay.setVisibility(View.GONE);
                    manualSubmitInProgress = false;
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

        scanViewModel.getAutoSubmitUiState().observe(getViewLifecycleOwner(), this::renderAutoSubmitHint);
    }

    private void renderAutoSubmitHint(ScanViewModel.AutoSubmitUiState state) {
        if (tvAutoSubmitState == null || state == null) return;
        uiHandler.removeCallbacks(hideAutoSubmitHintRunnable);
        switch (state) {
            case IDLE:
                tvAutoSubmitState.setVisibility(View.GONE);
                break;
            case SYNCING:
                tvAutoSubmitState.setText(R.string.scan_auto_submit_syncing);
                tvAutoSubmitState.setBackgroundColor(0xCC2B2B2B);
                tvAutoSubmitState.setTextColor(0xFFDDDDDD);
                tvAutoSubmitState.setVisibility(View.VISIBLE);
                break;
            case OK:
                tvAutoSubmitState.setText(R.string.scan_auto_submit_ok);
                tvAutoSubmitState.setBackgroundColor(0xCC1B5E20);
                tvAutoSubmitState.setTextColor(0xFFE8F5E9);
                tvAutoSubmitState.setVisibility(View.VISIBLE);
                uiHandler.postDelayed(hideAutoSubmitHintRunnable, 1500L);
                break;
            case FAILED:
                tvAutoSubmitState.setText(R.string.scan_auto_submit_failed);
                tvAutoSubmitState.setBackgroundColor(0xCCB71C1C);
                tvAutoSubmitState.setTextColor(0xFFFFEBEE);
                tvAutoSubmitState.setVisibility(View.VISIBLE);
                break;
        }
    }

    /**
     * 更新进度文本，由 LiveData 观察者调用
     */
    private void updateProgressText(Integer count) {
        // Top-right progress badge was removed to keep the scan view visually simpler.
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

    private void animateResultCardSuccess() {
        if (tvPackageNumber == null) {
            return;
        }
        uiHandler.removeCallbacks(resetResultCardRunnable);
        ViewCompat.setBackgroundTintList(tvPackageNumber, ColorStateList.valueOf(0xFF1F5D3A));
        tvPackageNumber.animate().cancel();
        tvPackageNumber.setScaleX(1f);
        tvPackageNumber.setScaleY(1f);
        tvPackageNumber.animate()
                .scaleX(1.035f)
                .scaleY(1.035f)
                .setDuration(110L)
                .withEndAction(() -> tvPackageNumber.animate()
                        .scaleX(1f)
                        .scaleY(1f)
                        .setDuration(180L)
                        .start())
                .start();
        uiHandler.postDelayed(resetResultCardRunnable, 420L);
    }

    private void resetResultCardVisualState() {
        if (tvPackageNumber == null) {
            return;
        }
        ViewCompat.setBackgroundTintList(tvPackageNumber, null);
        tvPackageNumber.setScaleX(1f);
        tvPackageNumber.setScaleY(1f);
    }

    private void setTestModeEnabled(boolean enabled, boolean showToast) {
        testMode = enabled;
        updateScanModeUi();
        if (showToast) {
            int msgRes = testMode ? R.string.scan_test_mode_on : R.string.scan_test_mode_off;
            Toast.makeText(requireContext(), msgRes, Toast.LENGTH_SHORT).show();
        }
        if (cameraWasBound) {
            bindCameraNow();
        }
    }

    private void updateScanModeUi() {
        if (btnScanMode != null) {
            btnScanMode.setText(testMode ? R.string.scan_screen_mode_on : R.string.scan_screen_mode_off);
        }
        if (tvHint != null) {
            tvHint.setText(testMode ? R.string.scan_mode_hint_on : R.string.scan_mode_hint_off);
            tvHint.setGravity(Gravity.CENTER_VERTICAL);
        }
    }

    private void renderScanFeedbackState(ScanFeedbackState state, int messageRes, boolean autoReset) {
        if (scanLine == null || viewFinderOverlay == null) {
            return;
        }
        uiHandler.removeCallbacks(resetScanFeedbackRunnable);
        switch (state) {
            case IDLE:
                scanLine.setBackgroundColor(0xFFE53935);
                viewFinderOverlay.setBackgroundResource(R.drawable.overlay_barcode_finder);
                break;
            case CANDIDATE:
                scanLine.setBackgroundColor(0xFFFFB300);
                viewFinderOverlay.setBackgroundResource(R.drawable.overlay_barcode_finder_align);
                break;
            case SUCCESS:
                scanLine.setBackgroundColor(0xFF43A047);
                viewFinderOverlay.setBackgroundResource(R.drawable.overlay_barcode_finder_success);
                break;
            case ERROR:
                scanLine.setBackgroundColor(0xFFEF5350);
                viewFinderOverlay.setBackgroundResource(R.drawable.overlay_barcode_finder_error);
                break;
        }
        if (autoReset) {
            uiHandler.postDelayed(resetScanFeedbackRunnable, 1400L);
        }
    }

    private void appendResultSegment(SpannableStringBuilder builder, Context context,
                                     String label, String value, boolean accent) {
        int labelStart = builder.length();
        builder.append(label).append(getString(R.string.scan_label_separator));
        builder.setSpan(new ForegroundColorSpan(
                        ContextCompat.getColor(context, R.color.scan_result_label)),
                labelStart, builder.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        builder.setSpan(new AbsoluteSizeSpan(15, true),
                labelStart, builder.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);

        int valueStart = builder.length();
        builder.append(value != null ? value : getString(R.string.scan_placeholder));
        int colorRes = accent ? R.color.scan_result_accent : R.color.scan_result_value;
        builder.setSpan(new ForegroundColorSpan(ContextCompat.getColor(context, colorRes)),
                valueStart, builder.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        builder.setSpan(new StyleSpan(Typeface.BOLD), valueStart, builder.length(),
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        int valueSizeSp = accent ? 34 : 21;
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
                vibrator.vibrate(VibrationEffect.createWaveform(
                        new long[]{0, 35, 45, 55}, -1));
            } else {
                vibrator.vibrate(120);
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
        return raw != null && WAYBILL_TOKEN_PATTERN.matcher(raw).matches();
    }

    @Nullable
    private String extractWaybillCandidate(@Nullable String raw) {
        if (raw == null) {
            return null;
        }
        String trimmed = raw.trim();
        if (trimmed.isEmpty()) {
            return null;
        }
        Matcher matcher = WAYBILL_TOKEN_PATTERN.matcher(trimmed);
        String best = null;
        while (matcher.find()) {
            String candidate = matcher.group();
            if (candidate == null || candidate.isEmpty()) {
                continue;
            }
            if (best == null || candidate.length() > best.length()) {
                best = candidate;
            }
        }
        return best == null ? null : best.toUpperCase(Locale.US);
    }

    @Nullable
    private String selectBestBarcodeValue(@NonNull List<Barcode> barcodes) {
        Barcode bestBarcode = null;
        float bestScore = Float.NEGATIVE_INFINITY;
        for (Barcode barcode : barcodes) {
            String candidate = extractWaybillCandidate(barcode.getRawValue());
            if (candidate == null) {
                continue;
            }
            android.graphics.Rect box = barcode.getBoundingBox();
            float score = 1f;
            if (box != null && previewView != null && previewView.getWidth() > 0 && previewView.getHeight() > 0) {
                float left = viewFinderOverlay != null ? viewFinderOverlay.getLeft() : 0f;
                float top = viewFinderOverlay != null ? viewFinderOverlay.getTop() : 0f;
                float right = viewFinderOverlay != null ? viewFinderOverlay.getRight() : previewView.getWidth();
                float bottom = viewFinderOverlay != null ? viewFinderOverlay.getBottom() : previewView.getHeight();
                float expandX = testMode ? previewView.getWidth() * 0.10f : previewView.getWidth() * 0.06f;
                float expandY = testMode ? previewView.getHeight() * 0.10f : previewView.getHeight() * 0.06f;
                left -= expandX;
                right += expandX;
                top -= expandY;
                bottom += expandY;
                float width = Math.max(1f, box.width());
                float height = Math.max(1f, box.height());
                float frameArea = Math.max(1f, (float) previewView.getWidth() * previewView.getHeight());
                float areaRatio = (width * height) / frameArea;
                float centerX = box.exactCenterX();
                float centerY = box.exactCenterY();
                if (centerX < left || centerX > right || centerY < top || centerY > bottom) {
                    continue;
                }
                float dx = Math.abs(centerX - ((left + right) / 2f)) / Math.max(1f, (right - left) / 2f);
                float dy = Math.abs(centerY - ((top + bottom) / 2f)) / Math.max(1f, (bottom - top) / 2f);
                float centerPenalty = (dx * 0.20f) + (dy * 0.16f);
                score = areaRatio * 7.5f - centerPenalty;
            }
            if ((testMode && score < TEST_MODE_MIN_SCORE) || (!testMode && score < NORMAL_MODE_MIN_SCORE)) {
                continue;
            }
            if (bestBarcode == null || score > bestScore) {
                bestBarcode = barcode;
                bestScore = score;
            }
        }
        return bestBarcode == null ? null : extractWaybillCandidate(bestBarcode.getRawValue());
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
        scanViewModel.fetchScanBatchStatus(new ScanViewModel.BatchStatusCallback() {
            @Override
            public void onResult(@NonNull ScanViewModel.BatchStatus status) {
                if (status == ScanViewModel.BatchStatus.OPEN) {
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
                    return;
                }
                if (status == ScanViewModel.BatchStatus.CLOSED) {
                    new AlertDialog.Builder(requireContext())
                            .setTitle(R.string.scan_report_closed_title)
                            .setMessage(R.string.scan_batch_reopen_required_message)
                            .setPositiveButton(R.string.action_yes, (dialog, which) ->
                                    scanViewModel.reopenScanBatch(new ScanViewModel.OpenBatchCallback() {
                                        @Override
                                        public void onResult(boolean reopened) {
                                            if (reopened) {
                                                showGenerateReportConfirmDialog();
                                            }
                                        }

                                        @Override
                                        public void onError(Exception e) {
                                            Toast.makeText(getContext(), R.string.scan_reopen_batch_failed, Toast.LENGTH_SHORT).show();
                                        }
                                    })
                            )
                            .setNegativeButton(R.string.action_cancel, null)
                            .show();
                    return;
                }
                new AlertDialog.Builder(requireContext())
                        .setTitle(R.string.scan_report_closed_title)
                        .setMessage(R.string.scan_batch_create_required_message)
                        .setPositiveButton(R.string.action_yes, (dialog, which) ->
                                scanViewModel.createScanBatchForSubmit(new ScanViewModel.OpenBatchCallback() {
                                    @Override
                                    public void onResult(boolean created) {
                                        if (created) {
                                            showGenerateReportConfirmDialog();
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

    private void submitOfflineWithPrecheck(boolean manualSubmit) {
        scanViewModel.fetchScanBatchStatus(new ScanViewModel.BatchStatusCallback() {
            @Override
            public void onResult(@NonNull ScanViewModel.BatchStatus status) {
                if (status == ScanViewModel.BatchStatus.OPEN) {
                    manualSubmitInProgress = manualSubmit;
                    scanViewModel.submitOfflineScans();
                    return;
                }
                if (status == ScanViewModel.BatchStatus.CLOSED) {
                    new AlertDialog.Builder(requireContext())
                            .setTitle(R.string.scan_report_closed_title)
                            .setMessage(R.string.scan_batch_reopen_required_message)
                            .setPositiveButton(R.string.action_yes, (dialog, which) ->
                                    scanViewModel.reopenScanBatch(new ScanViewModel.OpenBatchCallback() {
                                        @Override
                                        public void onResult(boolean reopened) {
                                            if (reopened) {
                                                manualSubmitInProgress = manualSubmit;
                                                scanViewModel.submitOfflineScans();
                                            }
                                        }

                                        @Override
                                        public void onError(Exception e) {
                                            Toast.makeText(getContext(), R.string.scan_reopen_batch_failed, Toast.LENGTH_SHORT).show();
                                        }
                                })
                    )
                    .setNegativeButton(R.string.action_cancel, null)
                    .show();
                    return;
                }
                new AlertDialog.Builder(requireContext())
                        .setTitle(R.string.scan_report_closed_title)
                        .setMessage(R.string.scan_batch_create_required_message)
                        .setPositiveButton(R.string.action_yes, (dialog, which) ->
                                scanViewModel.createScanBatchForSubmit(new ScanViewModel.OpenBatchCallback() {
                                    @Override
                                    public void onResult(boolean created) {
                                        if (created) {
                                            manualSubmitInProgress = manualSubmit;
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
                        .setTargetResolution(testMode ? new Size(1920, 1080) : new Size(1280, 720))
                        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                        .build();

                imageAnalysis.setAnalyzer(cameraExecutor, image -> {
                    try {
                        if (image.getImage() == null) { image.close(); return; }
                        InputImage inputImage = InputImage.fromMediaImage(image.getImage(), image.getImageInfo().getRotationDegrees());
                         barcodeScanner.process(inputImage)
                                .addOnSuccessListener(barcodes -> {
                                    if (barcodes != null && !barcodes.isEmpty()) {
                                        String bestValue = selectBestBarcodeValue(barcodes);
                                        if (bestValue != null) {
                                            renderScanFeedbackState(ScanFeedbackState.CANDIDATE, R.string.scan_status_candidate, false);
                                            onBarcodeDetectedFromService(bestValue);
                                        } else {
                                            renderScanFeedbackState(ScanFeedbackState.CANDIDATE, R.string.scan_status_candidate, true);
                                        }
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
        startScanForegroundRefreshers();
        scanViewModel.tryAutoSubmitOfflineScans();
    }

    @Override
    public void onPause() {
        super.onPause();
        stopScanForegroundRefreshers();
        try {
            ProcessCameraProvider.getInstance(requireContext()).get().unbindAll();
        } catch (Exception e) {
            FileLog.e(TAG, "Failed to unbind camera onPause", e);
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        uiHandler.removeCallbacks(hideAutoSubmitHintRunnable);
        uiHandler.removeCallbacks(resetScanFeedbackRunnable);
        uiHandler.removeCallbacks(resetResultCardRunnable);
        stopScanForegroundRefreshers();
        if (cameraExecutor != null && !cameraExecutor.isShutdown()) cameraExecutor.shutdown();
        if (barcodeScanner != null) barcodeScanner.close();

        ResourceMgr.getInstance().getPublisher().unsubscribe(EventConstant.EVENT_DELIVERY_DATA_READY, this);
    }

    private void startScanForegroundRefreshers() {
        if (tokenRefresher != null) {
            tokenRefresher.start(() -> scanViewModel.refreshBatchId());
        }
        if (autoSubmitRefresher != null) {
            autoSubmitRefresher.start(() -> scanViewModel.tryAutoSubmitOfflineScans());
        }
    }

    private void stopScanForegroundRefreshers() {
        if (tokenRefresher != null) tokenRefresher.stop();
        if (autoSubmitRefresher != null) autoSubmitRefresher.stop();
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
