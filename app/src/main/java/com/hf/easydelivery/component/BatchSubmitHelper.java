package com.hf.easydelivery.component;

import android.os.Handler;
import android.os.Looper;

import com.hf.courierservice.ICourierService;
import com.hf.courierservice.IResponseCallBack;
import com.hf.courierservice.Result;
import com.hf.courierservice.apihelper.exception.AlreadyScannedException;
import com.hf.courierservice.apihelper.exception.UnAuthorizedException;
import com.hf.courierservice.bean.ParcelScanData;
import com.hf.courierservice.apihelper.FileLog;
import com.hf.easydelivery.dao.ScanRecord;
import com.hf.easydelivery.dao.ScanRecordDao;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

public class BatchSubmitHelper {

    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler uiHandler = new Handler(Looper.getMainLooper());

    private final ScanRecordDao scanDao;
    private final ICourierService courierService;
    private final Handler dbHandler;

    public BatchSubmitHelper(ScanRecordDao scanDao,
                             ICourierService courierService,
                             Handler dbHandler) {
        this.scanDao = scanDao;
        this.courierService = courierService;
        this.dbHandler = dbHandler;
    }

    /**
     * 开始批量提交
     */
    public void submit(List<ScanRecord> list, BatchSubmitCallback callback) {
        executor.execute(() -> doSubmit(list, callback));
    }

    private void doSubmit(List<ScanRecord> list, BatchSubmitCallback cb) {
        int total = list.size();
        AtomicInteger successCount = new AtomicInteger();
        AtomicInteger failCount    = new AtomicInteger();

        // first progress
        postProgress(0, total, 0, 0, cb);
        submitNext(list, 0, total, successCount, failCount, cb);
    }

    private void submitNext(List<ScanRecord> list,
                            int index,
                            int total,
                            AtomicInteger successCount,
                            AtomicInteger failCount,
                            BatchSubmitCallback cb) {
        if (index >= total) {
            uiHandler.post(() -> cb.onComplete(successCount.get(), failCount.get()));
            return;
        }

        ScanRecord rec = list.get(index);
        courierService.scan(
                rec.trackingNo, rec.scanBatchId,
                new IResponseCallBack<ParcelScanData>() {
                    @Override
                    public void onComplete(Result<ParcelScanData> result) {
                        successCount.incrementAndGet();

                        dbHandler.post(() -> scanDao.markUploadedByTrackingNo(rec.trackingNo));
                        FileLog.getInstance().writeLog("Scanned " + rec.trackingNo);
                        uiHandler.post(() -> cb.onSingleComplete(rec.trackingNo));

                        postProgress(successCount.get() + failCount.get(), total,
                                successCount.get(), failCount.get(), cb);

                        executor.execute(() -> submitNext(list, index + 1, total, successCount, failCount, cb));
                    }

                    @Override
                    public void onFail(Exception e) {
                        if (e instanceof AlreadyScannedException) {
                            AlreadyScannedException alreadyScanned = (AlreadyScannedException) e;
                            FileLog.getInstance().warning(
                                    "BatchSubmitHelper",
                                    "scan submit deduped as already scanned: tracking=" + rec.trackingNo
                                            + ", batchId=" + rec.scanBatchId
                                            + ", bizCode=" + alreadyScanned.getBizCode()
                                            + ", url=" + alreadyScanned.getRequestUrl());
                            successCount.incrementAndGet();
                            dbHandler.post(() -> scanDao.markUploadedByTrackingNo(rec.trackingNo));
                            uiHandler.post(() -> cb.onSingleComplete(rec.trackingNo));
                            postProgress(successCount.get() + failCount.get(), total,
                                    successCount.get(), failCount.get(), cb);
                            executor.execute(() -> submitNext(list, index + 1, total, successCount, failCount, cb));
                            return;
                        }
                        if (e instanceof UnAuthorizedException) {
                            UnAuthorizedException unauthorized = (UnAuthorizedException) e;
                            FileLog.getInstance().warning(
                                    "BatchSubmitHelper",
                                    "scan submit unauthorized: tracking=" + rec.trackingNo
                                            + ", batchId=" + rec.scanBatchId
                                            + ", http=" + unauthorized.getHttpStatusCode()
                                            + ", url=" + unauthorized.getRequestUrl());
                            uiHandler.post(() -> cb.onFail(e));
                            return;
                        }
                        FileLog.getInstance().writeLog("Failed to set scanned status  " + rec.trackingNo + e.getMessage());

                        failCount.incrementAndGet();
                        postProgress(successCount.get() + failCount.get(), total,
                                successCount.get(), failCount.get(), cb);

                        executor.execute(() -> submitNext(list, index + 1, total, successCount, failCount, cb));
                    }
                }
        );
    }

    private void postProgress(int done, int total,
                              int success, int fail,
                              BatchSubmitCallback cb) {
        uiHandler.post(() -> cb.onProgress(done, total, success, fail));
    }

    /** 释放资源：在 Activity onDestroy 调用 */
    public void shutdown() {
        executor.shutdownNow();
    }
}
