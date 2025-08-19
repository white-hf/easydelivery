package com.hf.easydelivery.view.model;

import androidx.annotation.MainThread;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MediatorLiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.hf.easydelivery.core.DeliveryinfoMgr;
import com.hf.easydelivery.dao.DeliveryInfo;
import com.hf.easydelivery.ResourceMgr;
import com.hf.courierservice.apihelper.FileLog;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;

/**
 * 管理 Scan 页的业务状态（B 档：Fragment 仅负责渲染）
 * - ViewModel 负责：
 *   1) 通过 ScanPackagesMgr 拉取【未扫描】列表（仅内存，不落库）
 *   2) 维护【今日已扫】waybill 集合
 *   3) 根据“未扫描原始列表 - 已扫集合”计算出【未扫描可派发列表】（过滤去重）
 *   4) 暴露 batch 信息（scanBatchId / scanBatchStatus）
 *
 * 说明：
 * - 为降低耦合，事件订阅交由外层视图决定；当网络回包（或收到 EVENT_DELIVERY_DATA_READY）后，调用
 *   {@link #refreshFromRepo()} 即可推动 LiveData 更新。
 */
public class ScanViewModel extends ViewModel {

    private static final String TAG = "ScanViewModel";

    // --- 数据仓库：未扫描（仅内存） ---
    private final DeliveryinfoMgr.ScanPackagesMgr scanMgr = new DeliveryinfoMgr.ScanPackagesMgr();

    // --- LiveData：原始未扫描列表（来自仓库） ---
    private final MutableLiveData<List<DeliveryInfo>> unscannedRawLive = new MutableLiveData<>(Collections.emptyList());

    // --- LiveData：今日已扫的 waybill 集合（来自本地 DB 加载后 setScannedWaybills 传入） ---
    private final MutableLiveData<Set<String>> scannedWaybillsLive = new MutableLiveData<>(new HashSet<>());

    // --- LiveData：过滤后的未扫描列表（= 原始未扫描 - 今日已扫） ---
    private final MediatorLiveData<List<DeliveryInfo>> unscannedFilteredLive = new MediatorLiveData<>();

    // --- LiveData：计数与批次信息 ---
    private final MutableLiveData<Integer> totalCountLive = new MutableLiveData<>(0);
    private final MutableLiveData<Integer> filteredCountLive = new MutableLiveData<>(0);
    private final MutableLiveData<Long> scanBatchIdLive = new MutableLiveData<>(0L);
    private final MutableLiveData<Integer> scanBatchStatusLive = new MutableLiveData<>(0);

    // --- 首次进入标记（供 Fragment 控制首次 UI 行为，如：开相机提示） ---
    private boolean firstShown = true;

    /**
     * 可选：由外部注入“今日已扫运单号”加载器，避免在 VM 里直接依赖具体 Dao。
     */
    public interface TodayScannedLoader {
        @NonNull Collection<String> loadTodayWaybills();
    }

    @Nullable private TodayScannedLoader todayScannedLoader;

    public void setTodayScannedLoader(@NonNull TodayScannedLoader loader) {
        this.todayScannedLoader = loader;
        FileLog.getInstance().debug(TAG, "TodayScannedLoader injected: %s", loader.getClass().getSimpleName());
    }

    public ScanViewModel() {
        // 组合源：当原始未扫或已扫集合变化时，重算过滤列表
        unscannedFilteredLive.addSource(unscannedRawLive, ignored -> recomputeFiltered());
        unscannedFilteredLive.addSource(scannedWaybillsLive, ignored -> recomputeFiltered());
    }

    // region ★ 对外暴露 LiveData ★
    public LiveData<List<DeliveryInfo>> getUnscannedRawLive() { return unscannedRawLive; }
    public LiveData<List<DeliveryInfo>> getUnscannedFilteredLive() { return unscannedFilteredLive; }
    public LiveData<Integer> getTotalCountLive() { return totalCountLive; }
    public LiveData<Integer> getFilteredCountLive() { return filteredCountLive; }
    public LiveData<Long> getScanBatchIdLive() { return scanBatchIdLive; }
    public LiveData<Integer> getScanBatchStatusLive() { return scanBatchStatusLive; }
    public LiveData<Set<String>> getScannedWaybillsLive() { return scannedWaybillsLive; }
    // endregion

    // region ★ 外部触发：加载/刷新 ★
    /**
     * 预加载未扫描列表 & 扫描批次信息（通常在页面创建 / 下拉刷新时调用）。
     * 注意：仓库为异步拉取；当网络回包（或事件）到达时，请调用 {@link #refreshFromRepo()}。
     */
    @MainThread
    public void preloadUnscanned() {
        Integer driverId = ResourceMgr.getInstance().getLoginInfo() != null
                ? ResourceMgr.getInstance().getLoginInfo().loginId
                : null;
        if (driverId == null || driverId <= 0) {
            FileLog.getInstance().warning(TAG, "preloadUnscanned ignored: invalid driverId=%s", String.valueOf(driverId));
            return;
        }
        FileLog.getInstance().debug(TAG, "preloadUnscanned: driverId=%d", driverId);
        // 触发网络拉取（未扫描）
        try {
            scanMgr.getDeliveryInfo(driverId, /*bDeliveryTask=*/false);
        } catch (Throwable t) {
            FileLog.getInstance().error(TAG, "scanMgr.getDeliveryInfo failed: %s", t.getMessage());
        }
        // 同步批次信息（如果仓库内部已维护）
        try {
            scanMgr.fechScanBatchId();
        } catch (Throwable t) {
            FileLog.getInstance().warning(TAG, "fechScanBatchId failed: %s", t.getMessage());
        }
        pushBatchFields();
    }

    /** 同上，但用于“查询”按钮/重复刷新 */
    @MainThread
    public void queryUnscanned() {
        FileLog.getInstance().debug(TAG, "queryUnscanned triggered");
        preloadUnscanned();
    }

    /**
     * 网络/事件回包后，调用此方法把仓库内存刷新到 LiveData。
     *（将原来 Fragment 中的 EVENT_DELIVERY_DATA_READY 响应改为调用本方法）
     */
    @MainThread
    public void refreshFromRepo() {
        List<DeliveryInfo> snapshot = null;
        try {
            snapshot = scanMgr.getListDeliveryInfo();
        } catch (Throwable t) {
            FileLog.getInstance().error(TAG, "getListDeliveryInfo failed: %s", t.getMessage());
        }
        if (snapshot == null) snapshot = Collections.emptyList();
        FileLog.getInstance().debug(TAG, "refreshFromRepo: got=%d items", snapshot.size());
        unscannedRawLive.setValue(new ArrayList<>(snapshot));
        totalCountLive.setValue(snapshot.size());
        pushBatchFields();
    }

    /** 从 DB 加载今日已扫（通过注入的 TodayScannedLoader），并更新过滤 */
    @MainThread
    public void loadTodayScannedFromDb() {
        if (todayScannedLoader == null) {
            FileLog.getInstance().warning(TAG, "loadTodayScannedFromDb skipped: TodayScannedLoader not set");
            return;
        }
        try {
            Collection<String> wb = todayScannedLoader.loadTodayWaybills();
            FileLog.getInstance().debug(TAG, "loadTodayScannedFromDb: loaded %d waybills", wb == null ? 0 : wb.size());
            setScannedWaybills(wb);
        } catch (Throwable t) {
            FileLog.getInstance().error(TAG, "loadTodayScannedFromDb failed: %s", t.getMessage());
        }
    }

    /**
     * （进阶）由 ViewModel 负责加载“今日已扫记录”，则在本方法里自行访问 DB 并 setScannedWaybills。
     * 如果你当前已在 Fragment 里完成 DB 加载，也可以直接调用 {@link #setScannedWaybills(Collection)}。
     */
    @MainThread
    public void loadTodayScannedFromDb(Collection<String> waybillsFromDb) {
        if (waybillsFromDb == null) {
            FileLog.getInstance().warning(TAG, "loadTodayScannedFromDb(Collection) with null -> treat as empty");
            setScannedWaybills(Collections.emptySet());
            return;
        }
        FileLog.getInstance().debug(TAG, "loadTodayScannedFromDb(Collection): size=%d", waybillsFromDb.size());
        setScannedWaybills(waybillsFromDb);
    }

    /** 供外部把“今日已扫的运单号集合”喂给 VM（然后触发未扫过滤重算） */
    @MainThread
    public void setScannedWaybills(Collection<String> waybills) {
        if (waybills == null) {
            FileLog.getInstance().warning(TAG, "setScannedWaybills(null) -> empty");
            scannedWaybillsLive.setValue(new HashSet<>());
        } else {
            HashSet<String> set = new HashSet<>(waybills);
            scannedWaybillsLive.setValue(set);
            FileLog.getInstance().debug(TAG, "setScannedWaybills: size=%d", set.size());
        }
    }

    /** 扫描批次变更时（如 TokenRefresher），调用以便 UI 获取最新批次状态 */
    @MainThread
    public void refreshBatchId() {
        scanMgr.fechScanBatchId();
        pushBatchFields();
    }

    // endregion

    // region ★ 查询/判断 ★
    /** 当前扫描批次是否开放（具体规则可按你项目中 scanBatchStatus 定义调整） */
    public boolean isScanBatchOpen() {
        Integer st = scanBatchStatusLive.getValue();
        boolean open = st != null && st == 1; // 1 表示开放（按现有约定）
        FileLog.getInstance().debug(TAG, "isScanBatchOpen=%s (status=%s)", String.valueOf(open), String.valueOf(st));
        return open;
    }

    /** 按运单号在“原始未扫列表”中查询（用于扫码命中校验） */
    public DeliveryInfo getByTrackingNo(@NonNull String waybill) {
        if (waybill == null || waybill.isEmpty()) {
            FileLog.getInstance().warning(TAG, "getByTrackingNo: empty input");
            return null;
        }
        List<DeliveryInfo> src = unscannedRawLive.getValue();
        if (src == null || src.isEmpty()) return null;
        for (DeliveryInfo d : src) {
            if (d == null) continue;
            if (waybill.equalsIgnoreCase(String.valueOf(d.getOrderSn()))) {
                return d;
            }
        }
        return null;
    }

    /** 是否首次进入 Scan 页（供 Fragment 做一次性 UI 行为） */
    public boolean shouldDoFirstEnter() {
        if (firstShown) { firstShown = false; return true; }
        return false;
    }
    // endregion

    // region ★ 内部工具 ★
    private void recomputeFiltered() {
        List<DeliveryInfo> raw = unscannedRawLive.getValue();
        Set<String> scanned = scannedWaybillsLive.getValue();
        if (raw == null) raw = Collections.emptyList();
        if (scanned == null) scanned = Collections.emptySet();

        if (raw.isEmpty()) {
            unscannedFilteredLive.setValue(Collections.emptyList());
            filteredCountLive.setValue(0);
            return;
        }

        List<DeliveryInfo> filtered = new ArrayList<>(raw.size());
        for (DeliveryInfo d : raw) {
            if (d == null) continue;
            String waybill = String.valueOf(d.getOrderSn());
            if (!scanned.contains(waybill)) {
                filtered.add(d);
            }
        }
        FileLog.getInstance().debug(TAG, "recomputeFiltered: raw=%d, scanned=%d, filtered=%d", raw.size(), scanned.size(), filtered.size());
        unscannedFilteredLive.setValue(filtered);
        filteredCountLive.setValue(filtered.size());
    }

    private void pushBatchFields() {
        try {
            long id = scanMgr.getScanBatchId();
            int st = scanMgr.getScanBatchStatus();
            scanBatchIdLive.setValue(id);
            scanBatchStatusLive.setValue(st);
            FileLog.getInstance().debug(TAG, "pushBatchFields: id=%d, status=%d", id, st);
        } catch (Throwable t) {
            FileLog.getInstance().warning(TAG, "pushBatchFields failed: %s", t.getMessage());
        }
    }
    // endregion
}