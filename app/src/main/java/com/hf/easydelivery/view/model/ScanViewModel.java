package com.hf.easydelivery.view.model;

import android.content.Context;
import android.os.Handler;
import android.util.Pair;

import androidx.annotation.MainThread;
import androidx.annotation.NonNull;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MediatorLiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;
import com.hf.courierservice.IResponseCallBack;
import com.hf.courierservice.Result;
import com.hf.courierservice.apihelper.FileLog;
import com.hf.courierservice.bean.ScanBatchCreateData;
import com.hf.courierservice.bean.ScanBatchGenerateReportData;
import com.hf.courierservice.bean.ScanBatchReportData;
import com.hf.courierservice.bean.ScanBatchReviewData;
import com.hf.courierservice.bean.ToBePickedUpBriefData;
import com.hf.easydelivery.ResourceMgr;
import com.hf.easydelivery.R;
import com.hf.easydelivery.bean.ScanItem;
import com.hf.easydelivery.core.DeliveryinfoMgr;
import com.hf.easydelivery.event.Event;
import com.hf.easydelivery.component.BatchSubmitCallback;
import com.hf.easydelivery.component.BatchSubmitHelper;
import com.hf.easydelivery.dao.DeliveryInfo;
import com.hf.easydelivery.dao.ScanRecord;
import com.hf.easydelivery.dao.ScanRecordDao;
import com.hf.easydelivery.common.Utils;
import com.hf.courierservice.apihelper.exception.UnAuthorizedException;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import com.hf.easydelivery.event.Subscriber;
import com.hf.easydelivery.event.EventConstant;

/**
 * 管理 Scan 页的业务状态（B 档：Fragment 仅负责渲染）
 * - ViewModel 负责：
 * 1) 通过 ScanPackagesMgr 拉取【未扫描】列表（仅内存，不落库）
 * 2) 维护【今日已扫】waybill 集合
 * 3) 根据“未扫描原始列表 - 已扫集合”计算出【未扫描可派发列表】（过滤去重）
 * 4) 暴露 batch 信息（scanBatchId / scanBatchStatus）
 * 5) (新) 管理【已扫描】列表 (ScanItem)
 * 6) (新) 处理条码扫描、数据提交等所有业务逻辑
 *
 * 说明：
 * - 为降低耦合，事件订阅交由外层视图决定；当网络回包（或收到 EVENT_DELIVERY_DATA_READY）后，调用
 * {@link #refreshFromRepo()} 即可推动 LiveData 更新。
 */
public class ScanViewModel extends ViewModel implements Subscriber {

    private static final String TAG = "ScanViewModel";
    private static final String REVIEW_STATUS = "REVIEW";
    private static final String REOPEN_STATUS = "REOPEN";

    public interface PendingCountCallback {
        void onResult(int count);
        void onError(Exception e);
    }

    public interface OpenBatchCallback {
        void onResult(boolean hasOpen);
        void onError(Exception e);
    }

    public enum BatchStatus { OPEN, CLOSED, NONE }

    public interface BatchStatusCallback {
        void onResult(@NonNull BatchStatus status);
        void onError(Exception e);
    }

    // --- 新增：用于提交状态的枚举 ---
    public enum SubmissionState { IDLE, SUBMITTING, COMPLETE, FAILED }
    public enum AutoSubmitUiState { IDLE, SYNCING, OK, FAILED }

    // --- 数据仓库与助手类 ---
    private final ResourceMgr resourceMgr = ResourceMgr.getInstance();
    private final DeliveryinfoMgr.ScanPackagesMgr scanPackagesMgr = new DeliveryinfoMgr.ScanPackagesMgr();
    private final BatchSubmitHelper submitHelper;

    // --- LiveData：原始未扫描列表（来自仓库） ---
    private final MutableLiveData<List<DeliveryInfo>> unscannedRawLive = new MutableLiveData<>(Collections.emptyList());
    // --- LiveData：今日已扫的 waybill 集合 ---
    private final MutableLiveData<Set<String>> scannedWaybillsLive = new MutableLiveData<>(new HashSet<>());
    // --- LiveData：过滤后的未扫描列表（= 原始未扫描 - 今日已扫） ---
    private final MediatorLiveData<List<DeliveryInfo>> unscannedFilteredLive = new MediatorLiveData<>();

    // --- 新增：管理已扫描列表和计数的 LiveData ---
    private final MutableLiveData<List<ScanItem>> scannedListLive = new MutableLiveData<>(new ArrayList<>());
    private final MutableLiveData<Integer> scannedCountLive = new MutableLiveData<>(0);

    // --- LiveData：计数与批次信息 ---
    private final MutableLiveData<Integer> totalCountLive = new MutableLiveData<>(0);
    private final MutableLiveData<Long> scanBatchIdLive = new MutableLiveData<>(0L);
    private final MutableLiveData<Integer> scanBatchStatusLive = new MutableLiveData<>(0);

    // --- 新增：用于驱动 UI 一次性事件的 LiveData (例如 Toast) ---
    private final MutableLiveData<Event<String>> toastMessage = new MutableLiveData<>();
    private final MutableLiveData<Event<Pair<String, String>>> duplicateScanEvent = new MutableLiveData<>(); // <运单号, 包裹号>
    private final MutableLiveData<Event<Boolean>> scanSuccessEvent = new MutableLiveData<>();

    // --- 新增：用于驱动提交流程 UI 的 LiveData ---
    private final MutableLiveData<SubmissionState> submissionState = new MutableLiveData<>(SubmissionState.IDLE);
    private final MutableLiveData<Pair<Integer, Integer>> submissionProgress = new MutableLiveData<>(); // <已完成, 总数>
    private final MutableLiveData<AutoSubmitUiState> autoSubmitUiState = new MutableLiveData<>(AutoSubmitUiState.IDLE);

    private final MutableLiveData<Event<Boolean>> showCameraPromptEvent = new MutableLiveData<>();
    private long lastAutoSubmitAttemptMs = 0L;
    private volatile boolean submittingInternal = false;

    // --- 首次进入标记（供 Fragment 控制首次 UI 行为，如：开相机提示） ---
    private boolean firstEnter = true;

    public LiveData<Event<Boolean>> getShowCameraPromptEvent() { return showCameraPromptEvent; }
    public LiveData<Event<Boolean>> getScanSuccessEvent() { return scanSuccessEvent; }
    public ScanViewModel() {
        submitHelper = new BatchSubmitHelper(
                resourceMgr.getmMydb().getScanRecordDao(),
                resourceMgr.getCourierService(),
                resourceMgr.getDbHandler()
        );

        // 组合源：当原始未扫或已扫集合变化时，重算过滤列表
        unscannedFilteredLive.addSource(unscannedRawLive, rawList -> recomputeFiltered());
        unscannedFilteredLive.addSource(scannedWaybillsLive, scannedSet -> recomputeFiltered());

        // 订阅未扫描数据变化事件（EVENT_DELIVERY_DATA_READY）
        try {
            ResourceMgr.getInstance().getPublisher().subscribe(EventConstant.EVENT_DELIVERY_DATA_READY, this);
        } catch (Throwable ignore) {}
    }

    // region ★ 对外暴露 LiveData ★
    public LiveData<List<DeliveryInfo>> getUnscannedFilteredLive() { return unscannedFilteredLive; }
    public LiveData<List<ScanItem>> getScannedListLive() { return scannedListLive; }
    public LiveData<Integer> getTotalCountLive() { return totalCountLive; }
    public LiveData<Integer> getScannedCountLive() { return scannedCountLive; }
    public LiveData<Event<String>> getToastMessage() { return toastMessage; }
    public LiveData<Event<Pair<String, String>>> getDuplicateScanEvent() { return duplicateScanEvent; }
    public LiveData<SubmissionState> getSubmissionState() { return submissionState; }
    public LiveData<Pair<Integer, Integer>> getSubmissionProgress() { return submissionProgress; }
    public LiveData<AutoSubmitUiState> getAutoSubmitUiState() { return autoSubmitUiState; }
    // endregion

    // region ★ 业务逻辑处理 ★

    /**
     * 新增：从 Fragment 接收从数据库加载的今日扫描记录，并初始化相关 LiveData
     * @param todayRecords 从数据库查出的今日所有扫描记录 (包括已上传和未上传)
     */
    @MainThread
    public void loadInitialData(List<ScanItem> todayRecords) {
        final ArrayList<ScanItem> items = new ArrayList<>(todayRecords.size());
        final HashSet<String> waybills = new HashSet<>();
        for (ScanItem r : todayRecords) {
            String w = r.getWaybillNo();
            String p = r.getPackageNo() == null ? "" : String.valueOf(r.getPackageNo());
            items.add(new ScanItem(p, w, r.isUploaded(), true)); // isScanned = true
            waybills.add(w);
        }

        // 在主线程更新 LiveData
        scannedListLive.postValue(items);
        scannedWaybillsLive.postValue(waybills);
        scannedCountLive.postValue(items.size());

        FileLog.i(TAG, "loadInitialData: DB load complete, scanned=" + items.size());
        refreshFromRepo(); // 确保总数和未扫描列表也随之刷新
    }

    /**
     * 加载今日已扫描数据
     *  - 如果提供 TodayScannedLoader，则使用 Loader
     *  - 否则走内置 DB 查询
     */
    public void loadTodayScannedFromDb() {

        // 内置 DB 查询
        try {
            final String dateStr = Utils.getCurrentDate();
            final Integer driverId = ResourceMgr.getInstance().getLoginInfo() == null
                    ? null
                    : ResourceMgr.getInstance().getLoginInfo().loginId;
            if (driverId == null) {
                FileLog.getInstance().warning(TAG, "loadTodayScannedFromDb: driverId null, skip");
                return;
            }
            final ScanRecordDao scanRecordDao = ResourceMgr.getInstance().getmMydb().getScanRecordDao();

            ResourceMgr.getInstance().getDbHandler().post(() -> {
                List<ScanRecord> pending;
                List<ScanRecord> uploaded;
                try { pending = scanRecordDao.loadByDate(dateStr, false, driverId); }
                catch (Exception e) { pending = new ArrayList<>(); }

                try { uploaded = scanRecordDao.loadByDate(dateStr, true, driverId); }
                catch (Exception e) { uploaded = new ArrayList<>(); }

                final ArrayList<ScanItem> items = new ArrayList<>();
                final HashSet<String> waybills = new HashSet<>();

                for (ScanRecord r : pending) {
                    String w = r.trackingNo;
                    String p = r.packageNo == null ? "" : String.valueOf(r.packageNo);
                    items.add(new ScanItem(p, w, false, true));
                    waybills.add(w);
                }
                for (ScanRecord r : uploaded) {
                    String w = r.trackingNo;
                    String p = r.packageNo == null ? "" : String.valueOf(r.packageNo);
                    items.add(new ScanItem(p, w, true, true));
                    waybills.add(w);
                }

                // 更新 LiveData
                loadInitialData(items);

                // 检查是否有未扫描数据并触发弹窗事件
                boolean hasUnscanned = !scanPackagesMgr.getListDeliveryInfo().isEmpty() &&
                        scanPackagesMgr.getListDeliveryInfo().size() > items.size();

                if (shouldDoFirstEnter() && hasUnscanned) {
                    // 使用 postValue，因为这个代码块在 DbHandler 线程上
                    showCameraPromptEvent.postValue(new Event<>(true));
                }
                FileLog.i(TAG, "loadTodayScannedFromDb: hasUnscanned=" + hasUnscanned);

                FileLog.getInstance().debug(TAG,
                        "loadTodayScannedFromDb(internal): items=%d, waybills=%d",
                        items.size(), waybills.size());
            });
        } catch (Throwable t) {
            FileLog.getInstance().error(TAG,
                    "loadTodayScannedFromDb(internal) failed: %s", t.getMessage());
        }
    }


    /**
     * 新增：处理从相机扫到的条码的核心逻辑
     * @param waybillNo 运单号
     */
    public void processBarcode(String waybillNo) {
        // 检查是否重复扫描
        if (scannedWaybillsLive.getValue() != null && scannedWaybillsLive.getValue().contains(waybillNo)) {
            String pkgNo = findPackageNoInScannedList(waybillNo);
            duplicateScanEvent.postValue(new Event<>(new Pair<>(waybillNo, pkgNo)));
            return;
        }

        // 检查扫描批次是否开启 (status == 0)
        if (scanBatchIdLive.getValue() == null || scanBatchIdLive.getValue() < 1 || scanBatchStatusLive.getValue() != 0) {
            toastMessage.postValue(new Event<>(getString(R.string.scan_report_closed_or_invalid)));
            return;
        }

        final DeliveryInfo deliveryInfo = getByTrackingNo(waybillNo);
        if (deliveryInfo != null) {
            // 扫描成功，是自己的包裹
            handleSuccessfulScan(waybillNo, deliveryInfo.getRouteNumber());
        } else {
            // 扫描失败
            if (scanPackagesMgr.size() == 0) {
                toastMessage.postValue(new Event<>(getString(R.string.scan_data_loading)));
            } else {
                toastMessage.postValue(new Event<>(getString(R.string.scan_not_your_parcel)));
            }
        }
    }

    /**
     * 进入扫描页时：查询待分拣概要并创建扫描批次
     */
    public void prepareScanBatchOnEnter() {
        Long currentBatchId = scanBatchIdLive.getValue();
        Integer currentStatus = scanBatchStatusLive.getValue();
        if (currentBatchId != null && currentBatchId > 0 && currentStatus != null && currentStatus == 0) {
            return;
        }

        Integer driverId = resourceMgr.getLoginInfo() != null ? resourceMgr.getLoginInfo().loginId : null;
        if (driverId == null || driverId <= 0) return;

        resourceMgr.getCourierService().fetchToBePickedUpBrief(driverId,
                new IResponseCallBack<ToBePickedUpBriefData>() {
                    @Override
                    public void onComplete(Result<ToBePickedUpBriefData> result) {
                        if (result instanceof Result.Success) {
                            ToBePickedUpBriefData data = ((Result.Success<ToBePickedUpBriefData>) result).data;
                            int total = data == null ? 0 : data.getTotal_number();
                            if (total > 0) {
                                createScanBatch(driverId);
                            } else {
                                toastMessage.postValue(new Event<>(getString(R.string.scan_no_parcels_to_scan)));
                            }
                        } else if (result instanceof Result.Error) {
                            toastMessage.postValue(new Event<>(getString(R.string.scan_query_unscanned_failed)));
                        }
                    }

                    @Override
                    public void onFail(Exception e) {
                        toastMessage.postValue(new Event<>(getString(R.string.scan_query_unscanned_failed)));
                    }
                });
    }

    private void createScanBatch(int driverId) {
        resourceMgr.getCourierService().createScanBatch(driverId, 0, 0,
                new IResponseCallBack<ScanBatchCreateData>() {
                    @Override
                    public void onComplete(Result<ScanBatchCreateData> result) {
                        if (result instanceof Result.Success) {
                            ScanBatchCreateData data = ((Result.Success<ScanBatchCreateData>) result).data;
                            long batchId = data == null ? 0 : data.getScan_batch_id();
                            if (batchId > 0) {
                                resourceMgr.getDeliveryinfoMgr().updateScanBatchInfo(batchId, 0);
                                pushBatchFields();
                            } else {
                                toastMessage.postValue(new Event<>(getString(R.string.scan_create_batch_failed)));
                            }
                        } else if (result instanceof Result.Error) {
                            toastMessage.postValue(new Event<>(getString(R.string.scan_create_batch_failed)));
                        }
                    }

                    @Override
                    public void onFail(Exception e) {
                        toastMessage.postValue(new Event<>(getString(R.string.scan_create_batch_failed)));
                    }
                });
    }

    /**
     * 新增：处理一次成功扫描的内部方法
     */
    private void handleSuccessfulScan(String waybillNo, String packageNo) {
        FileLog.i(TAG, "handleSuccessfulScan: waybillNo=" + waybillNo + ", packageNo=" + packageNo);

        // 创建新的 ScanItem 并更新 LiveData
        ScanItem newItem = new ScanItem(packageNo, waybillNo, false, true); // uploaded=false, isScanned=true

        List<ScanItem> currentScanned = new ArrayList<>(scannedListLive.getValue());
        currentScanned.add(0, newItem);
        scannedListLive.postValue(currentScanned);

        Set<String> currentWaybills = new HashSet<>(scannedWaybillsLive.getValue());
        currentWaybills.add(waybillNo);
        scannedWaybillsLive.postValue(currentWaybills);

        scannedCountLive.postValue(currentScanned.size());

        // 存入数据库
        saveScanRecord(waybillNo, packageNo, scanBatchIdLive.getValue());
        scanSuccessEvent.postValue(new Event<>(Boolean.TRUE));
        tryAutoSubmitOfflineScans();
    }

    /**
     * 新增：提交所有未上传的扫描记录
     */
    public void submitOfflineScans() {
        submitOfflineScansInternal(true, true, true, true);
    }

    private void submitOfflineScansInternal(boolean showClosedToast,
                                            boolean showNoDataToast,
                                            boolean notifyResultToast,
                                            boolean publishUiState) {
        if (submittingInternal) {
            return;
        }
        if (!hasOpenBatch()) {
            if (showClosedToast) {
                toastMessage.postValue(new Event<>(getString(R.string.scan_report_closed_cannot_submit)));
            }
            return;
        }

        // 异步从数据库查询
        resourceMgr.getDbHandler().post(() -> {
            String strToday = Utils.getCurrentDate();
            Integer driverId = resourceMgr.getLoginInfo().loginId;
            List<ScanRecord> list = resourceMgr.getmMydb().getScanRecordDao().loadByDate(strToday, false, driverId);

            if (list.isEmpty()) {
                if (showNoDataToast) {
                    toastMessage.postValue(new Event<>(getString(R.string.scan_no_scanned_to_submit)));
                }
                if (!publishUiState) {
                    autoSubmitUiState.postValue(AutoSubmitUiState.IDLE);
                }
                return;
            }
            // 回到主线程（或任何有 Looper 的线程）启动提交
            new Handler(resourceMgr.getDbHandler().getLooper())
                    .post(() -> batchSubmit(list, notifyResultToast, publishUiState));
        });
    }

    public void tryAutoSubmitOfflineScans() {
        if (submittingInternal) {
            return;
        }
        long now = System.currentTimeMillis();
        if (now - lastAutoSubmitAttemptMs < 3_000L) {
            return;
        }
        lastAutoSubmitAttemptMs = now;
        autoSubmitUiState.postValue(AutoSubmitUiState.SYNCING);

        fetchScanBatchStatus(new BatchStatusCallback() {
            @Override
            public void onResult(@NonNull BatchStatus status) {
                if (status == BatchStatus.OPEN) {
                    submitOfflineScansInternal(false, false, false, false);
                    return;
                }
                if (status == BatchStatus.CLOSED) {
                    reopenScanBatch(new OpenBatchCallback() {
                        @Override
                        public void onResult(boolean reopened) {
                            if (reopened) {
                                submitOfflineScansInternal(false, false, false, false);
                            }
                        }

                        @Override
                        public void onError(Exception e) {
                            FileLog.getInstance().warning(TAG, "auto submit reopen failed: " + e.getMessage());
                            autoSubmitUiState.postValue(AutoSubmitUiState.FAILED);
                        }
                    });
                    return;
                }
                createScanBatchForSubmit(new OpenBatchCallback() {
                    @Override
                    public void onResult(boolean created) {
                        if (created) {
                            submitOfflineScansInternal(false, false, false, false);
                        }
                    }

                    @Override
                    public void onError(Exception e) {
                        FileLog.getInstance().warning(TAG, "auto submit create batch failed: " + e.getMessage());
                        autoSubmitUiState.postValue(AutoSubmitUiState.FAILED);
                    }
                });
            }

            @Override
            public void onError(Exception e) {
                FileLog.getInstance().warning(TAG, "auto submit fetch status failed: " + e.getMessage());
                autoSubmitUiState.postValue(AutoSubmitUiState.FAILED);
            }
        });
    }

    /**
     * 生成扫描报告
     */
    public void generateScanBatchReport() {
        Long batchId = scanBatchIdLive.getValue();
        if (batchId == null || batchId < 1) {
            toastMessage.postValue(new Event<>(getString(R.string.scan_batch_invalid)));
            return;
        }

        resourceMgr.getCourierService().generateScanBatchReport(batchId,
                new IResponseCallBack<ScanBatchGenerateReportData>() {
                    @Override
                    public void onComplete(Result<ScanBatchGenerateReportData> result) {
                        if (result instanceof Result.Success) {
                            resourceMgr.getDeliveryinfoMgr().updateScanBatchInfo(batchId, 1);
                            pushBatchFields();
                            toastMessage.postValue(new Event<>(getString(R.string.scan_report_generated)));
                            submitScanBatchReview(batchId);
                            clearLocalScanRecords(batchId);
                        } else if (result instanceof Result.Error) {
                            toastMessage.postValue(new Event<>(getString(R.string.scan_report_generate_failed)));
                        }
                    }

                    @Override
                    public void onFail(Exception e) {
                        toastMessage.postValue(new Event<>(getString(R.string.scan_report_generate_failed)));
                    }
                });
    }

    private void submitScanBatchReview(long batchId) {
        resourceMgr.getCourierService().submitScanBatchReview(batchId, REVIEW_STATUS,
                new IResponseCallBack<ScanBatchReviewData>() {
                    @Override
                    public void onComplete(Result<ScanBatchReviewData> result) {
                        if (result instanceof Result.Success) {
                            FileLog.i(TAG, "submitScanBatchReview success, batchId=" + batchId);
                        } else if (result instanceof Result.Error) {
                            FileLog.e(TAG, "submitScanBatchReview error, batchId=" + batchId,
                                    ((Result.Error<ScanBatchReviewData>) result).exception);
                        }
                    }

                    @Override
                    public void onFail(Exception e) {
                        FileLog.e(TAG, "submitScanBatchReview failed, batchId=" + batchId, e);
                    }
                });
    }

    public void reopenScanBatch(@NonNull OpenBatchCallback callback) {
        Long batchId = scanBatchIdLive.getValue();
        if (batchId == null || batchId < 1) {
            callback.onResult(false);
            return;
        }
        resourceMgr.getCourierService().submitScanBatchReview(batchId, REOPEN_STATUS,
                new IResponseCallBack<ScanBatchReviewData>() {
                    @Override
                    public void onComplete(Result<ScanBatchReviewData> result) {
                        if (result instanceof Result.Success) {
                            resourceMgr.getDeliveryinfoMgr().updateScanBatchInfo(batchId, 0);
                            pushBatchFields();
                            resourceMgr.getMainHandler().post(() -> callback.onResult(true));
                        } else {
                            resourceMgr.getMainHandler().post(() -> callback.onResult(false));
                        }
                    }

                    @Override
                    public void onFail(Exception e) {
                        resourceMgr.getMainHandler().post(() -> callback.onError(e));
                    }
                });
    }

    /**
     * 新增：内部方法，执行批量提交
     */
    private void batchSubmit(List<ScanRecord> list, boolean notifyResultToast, boolean publishUiState) {
        submittingInternal = true;
        if (publishUiState) {
            submissionState.postValue(SubmissionState.SUBMITTING);
            submissionProgress.postValue(new Pair<>(0, list.size()));
        }

        submitHelper.submit(list, new BatchSubmitCallback() {
            @Override
            public void onProgress(int done, int total, int success, int fail) {
                if (publishUiState) {
                    submissionProgress.postValue(new Pair<>(done, total));
                }
            }

            @Override
            public void onSingleComplete(String trackingNo) {
                // 在已扫描列表中，将被成功上传的条目标记为 uploaded
                List<ScanItem> currentScanned = new ArrayList<>(scannedListLive.getValue());
                for (ScanItem item : currentScanned) {
                    if (item.getWaybillNo().equals(trackingNo)) {
                        item.setUploaded(true);
                    }
                }
                scannedListLive.postValue(currentScanned);
            }

            @Override
            public void onComplete(int successCount, int failCount) {
                submittingInternal = false;
                if (!publishUiState) {
                    autoSubmitUiState.postValue(failCount > 0 ? AutoSubmitUiState.FAILED : AutoSubmitUiState.OK);
                }
                if (notifyResultToast) {
                    toastMessage.postValue(new Event<>(
                            getString(R.string.scan_submit_complete, successCount, failCount)));
                }
                if (publishUiState) {
                    submissionState.postValue(SubmissionState.COMPLETE);
                }
            }

            @Override
            public void onFail(Exception e) {
                submittingInternal = false;
                if (!publishUiState) {
                    FileLog.getInstance().warning(TAG, "auto submit failed: " + e.getMessage());
                    autoSubmitUiState.postValue(AutoSubmitUiState.FAILED);
                }
                if (notifyResultToast) {
                    toastMessage.postValue(new Event<>(getString(R.string.scan_login_expired)));
                }
                if (e instanceof UnAuthorizedException) {
                    resourceMgr.requestLoginRedirect();
                }
                if (publishUiState) {
                    submissionState.postValue(SubmissionState.FAILED);
                }
            }
        });
    }

    // endregion

    // region ★ 数据加载/刷新 ★
    /**
     * 网络/事件回包后，调用此方法把仓库内存刷新到 LiveData。
     */
    @MainThread
    public void refreshFromRepo() {
        List<DeliveryInfo> snapshot = scanPackagesMgr.getListDeliveryInfo();
        if (snapshot == null) snapshot = Collections.emptyList();

        if (!snapshot.isEmpty())
            showCameraPromptEvent.postValue(new Event<>(true));

        unscannedRawLive.postValue(new ArrayList<>(snapshot));
        totalCountLive.postValue(snapshot.size());
        pushBatchFields();
        FileLog.i(TAG, "refreshFromRepo: got=" + snapshot.size() + " items");
    }

    /** “查询”按钮/重复刷新 */
    public void queryUnscanned() {
        FileLog.i(TAG, "queryUnscanned: start");
        Integer driverId = resourceMgr.getLoginInfo() != null ? resourceMgr.getLoginInfo().loginId : null;
        if (driverId == null || driverId <= 0) return;

        scanPackagesMgr.fetch(driverId);
    }

    /** 扫描批次变更时，调用以便 UI 获取最新批次状态 */
    public void refreshBatchId() {
        resourceMgr.getDeliveryinfoMgr().fechScanBatchId();
        pushBatchFields();
    }

    // endregion

    // region ★ 内部工具方法 ★

    private String findPackageNoInScannedList(String waybillNo) {
        List<ScanItem> scanned = scannedListLive.getValue();
        if (scanned == null) return null;
        for (ScanItem si : scanned) {
            if (waybillNo.equals(si.getWaybillNo())) {
                return si.getPackageNo();
            }
        }
        return null;
    }

    /** 按运单号在“原始未扫列表”中查询（用于扫码命中校验） */
    private DeliveryInfo getByTrackingNo(@NonNull String waybill) {
        return scanPackagesMgr.getByTrackingNo(waybill);
    }

    /**
     * 新增：将扫描记录存入数据库
     */
    private void saveScanRecord(String waybillNo, String packageNo, Long scanBatchId) {
        Short packageNoShort = null;
        try {
            if (packageNo != null) packageNoShort = Short.parseShort(packageNo);
        } catch (NumberFormatException e) { /* ignore */ }

        ScanRecord rec = new ScanRecord(
                System.currentTimeMillis(),
                resourceMgr.getLoginInfo().loginId,
                waybillNo,
                packageNoShort,
                scanBatchId,
                false // uploaded = false
        );

        resourceMgr.getDbHandler().post(() -> {
            resourceMgr.getmMydb().getScanRecordDao().insert(rec);
            FileLog.d(TAG, "saveScanRecord: inserted waybillNo=" + waybillNo);
        });
    }

    private void recomputeFiltered() {
        List<DeliveryInfo> raw = unscannedRawLive.getValue();
        Set<String> scanned = scannedWaybillsLive.getValue();
        if (raw == null) raw = Collections.emptyList();
        if (scanned == null) scanned = Collections.emptySet();

        List<DeliveryInfo> filtered = new ArrayList<>();
        if (!raw.isEmpty()) {
            for (DeliveryInfo d : raw) {
                if (d != null && d.getOrderSn() != null && !scanned.contains(d.getOrderSn())) {
                    filtered.add(d);
                }
            }
        }
        unscannedFilteredLive.postValue(filtered);
    }

    private void pushBatchFields() {
        scanBatchIdLive.postValue(resourceMgr.getDeliveryinfoMgr().getScanBatchId());
        scanBatchStatusLive.postValue(resourceMgr.getDeliveryinfoMgr().getScanBatchStatus());
    }

    public void loadPendingOfflineCount(@NonNull PendingCountCallback callback) {
        resourceMgr.getDbHandler().post(() -> {
            try {
                String strToday = Utils.getCurrentDate();
                Integer driverId = resourceMgr.getLoginInfo().loginId;
                List<ScanRecord> list = resourceMgr.getmMydb().getScanRecordDao()
                        .loadByDate(strToday, false, driverId);
                resourceMgr.getMainHandler().post(() -> callback.onResult(list.size()));
            } catch (Exception e) {
                resourceMgr.getMainHandler().post(() -> callback.onError(e));
            }
        });
    }

    public void fetchOpenScanBatch(@NonNull OpenBatchCallback callback) {
        fetchScanBatchStatus(new BatchStatusCallback() {
            @Override
            public void onResult(@NonNull BatchStatus status) {
                callback.onResult(status == BatchStatus.OPEN);
            }

            @Override
            public void onError(Exception e) {
                callback.onError(e);
            }
        });
    }

    public void fetchScanBatchStatus(@NonNull BatchStatusCallback callback) {
        ResourceMgr.LoginInfo loginInfo = resourceMgr.getLoginInfo();
        resourceMgr.getCourierService().fetchDriverReport(loginInfo.warehouseId, loginInfo.loginId, Utils.getCurrentDate(),
                new IResponseCallBack<List<ScanBatchReportData>>() {
                    @Override
                    public void onComplete(Result<List<ScanBatchReportData>> result) {
                        if (!(result instanceof Result.Success)) {
                            resourceMgr.getMainHandler().post(() -> callback.onResult(BatchStatus.NONE));
                            return;
                        }
                        List<ScanBatchReportData> lst = ((Result.Success<List<ScanBatchReportData>>) result).data;
                        ScanBatchReportData open = null;
                        ScanBatchReportData closed = null;
                        if (lst != null) {
                            for (ScanBatchReportData item : lst) {
                                if (item != null && item.getScan_batch_status() == 0) {
                                    open = item;
                                    break;
                                }
                                if (item != null && closed == null) {
                                    closed = item;
                                }
                            }
                        }
                        if (open != null) {
                            resourceMgr.getDeliveryinfoMgr().updateScanBatchInfo(open.getScan_batch_id(), open.getScan_batch_status());
                            pushBatchFields();
                            resourceMgr.getMainHandler().post(() -> callback.onResult(BatchStatus.OPEN));
                        } else if (closed != null) {
                            resourceMgr.getDeliveryinfoMgr().updateScanBatchInfo(closed.getScan_batch_id(), closed.getScan_batch_status());
                            pushBatchFields();
                            resourceMgr.getMainHandler().post(() -> callback.onResult(BatchStatus.CLOSED));
                        } else {
                            // Fallback: backend list may briefly lag. Keep local OPEN to avoid false "closed/none" prompt.
                            if (hasOpenBatch()) {
                                resourceMgr.getMainHandler().post(() -> callback.onResult(BatchStatus.OPEN));
                            } else {
                                resourceMgr.getMainHandler().post(() -> callback.onResult(BatchStatus.NONE));
                            }
                        }
                    }

                    @Override
                    public void onFail(Exception e) {
                        resourceMgr.getMainHandler().post(() -> callback.onError(e));
                    }
                });
    }

    public void createScanBatchForSubmit(@NonNull OpenBatchCallback callback) {
        Integer driverId = resourceMgr.getLoginInfo() != null ? resourceMgr.getLoginInfo().loginId : null;
        if (driverId == null || driverId <= 0) {
            callback.onResult(false);
            return;
        }
        resourceMgr.getCourierService().createScanBatch(driverId, 0, 0,
                new IResponseCallBack<ScanBatchCreateData>() {
                    @Override
                    public void onComplete(Result<ScanBatchCreateData> result) {
                        if (result instanceof Result.Success) {
                            ScanBatchCreateData data = ((Result.Success<ScanBatchCreateData>) result).data;
                            long batchId = data == null ? 0 : data.getScan_batch_id();
                            if (batchId > 0) {
                                resourceMgr.getDeliveryinfoMgr().updateScanBatchInfo(batchId, 0);
                                pushBatchFields();
                                resourceMgr.getMainHandler().post(() -> callback.onResult(true));
                                return;
                            }
                        }
                        resourceMgr.getMainHandler().post(() -> callback.onResult(false));
                    }

                    @Override
                    public void onFail(Exception e) {
                        resourceMgr.getMainHandler().post(() -> callback.onError(e));
                    }
                });
    }

    private String getString(int resId, Object... args) {
        Context ctx = resourceMgr.getCtx();
        if (ctx == null) {
            return "";
        }
        return args.length == 0 ? ctx.getString(resId) : ctx.getString(resId, args);
    }

    /** 是否首次进入 Scan 页（供 Fragment 做一次性 UI 行为） */
    public boolean shouldDoFirstEnter() {
        if (firstEnter) {
            firstEnter = false;
            return true;
        }
        return false;
    }

    public boolean hasValidBatchId() {
        Long batchId = scanBatchIdLive.getValue();
        return batchId != null && batchId > 0;
    }

    public boolean hasOpenBatch() {
        Integer status = scanBatchStatusLive.getValue();
        return hasValidBatchId() && status != null && status == 0;
    }

    private void clearLocalScanRecords(long batchId) {
        Integer driverId = resourceMgr.getLoginInfo() != null ? resourceMgr.getLoginInfo().loginId : null;
        if (driverId == null || driverId <= 0) {
            return;
        }
        resourceMgr.getDbHandler().post(() -> {
            resourceMgr.getmMydb().getScanRecordDao().deleteByBatchId(batchId, driverId);
            FileLog.i(TAG, "clearLocalScanRecords: batchId=" + batchId);
        });

        scannedListLive.postValue(new ArrayList<>());
        scannedWaybillsLive.postValue(new HashSet<>());
        scannedCountLive.postValue(0);
        recomputeFiltered();
    }

    @Override
    protected void onCleared() {
        super.onCleared();
        submitHelper.shutdown();
        try {
            ResourceMgr.getInstance().getPublisher().unsubscribe(EventConstant.EVENT_DELIVERY_DATA_READY, this);
        } catch (Throwable ignore) {}
    }
    /**
     * 订阅回调：收到 EVENT_DELIVERY_DATA_READY 时刷新未扫描数据
     */
    @Override
    public void receive(Event event) {
        if (event == null || event.getEventType() == null) return;
        if (EventConstant.EVENT_DELIVERY_DATA_READY.equals(event.getEventType())) {
            // 未扫描数据加载完成（来自 ScanPackagesMgr/DeliveryinfoMgr）→ 刷新 LiveData
            refreshFromRepo();
        }
    }
    // endregion
}
