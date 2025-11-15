package com.hf.easydelivery.component;

import android.os.Handler;
import android.os.Looper;

import com.hf.courierservice.ICourierService;
import com.hf.courierservice.IResponseCallBack;
import com.hf.courierservice.Result;
import com.hf.courierservice.apihelper.exception.UnAuthorizedException;
import com.hf.courierservice.bean.ParcelScanData;
import com.hf.courierservice.apihelper.FileLog;
import com.hf.easydelivery.dao.ScanRecord;
import com.hf.easydelivery.dao.ScanRecordDao;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
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

        // 首次进度
        postProgress(0, total, 0, 0, cb);

        for (int i = 0; i < total; i++) {
            ScanRecord rec = list.get(i);

            CountDownLatch latch = new CountDownLatch(1);
            courierService.scan(
                    rec.trackingNo, rec.scanBatchId,
                    new IResponseCallBack<ParcelScanData>() {
                        @Override
                        public void onComplete(Result<ParcelScanData> result) {
                            successCount.incrementAndGet();

                            // 标记已上传（在当前线程快速执行）
                            dbHandler.post(() -> {
                                        scanDao.markUploadedByTrackingNo(rec.trackingNo);
                                    });

                            FileLog.getInstance().writeLog("Scanned " + rec.trackingNo);
                            latch.countDown();

                            uiHandler.post(() -> cb.onSingleComplete(rec.trackingNo));
                        }
                        @Override
                        public void onFail(Exception e) {
                            FileLog.getInstance().writeLog("Failed to set scanned status  " + rec.trackingNo + e.getMessage());

                            if (e instanceof UnAuthorizedException)
                            {
                                cb.onFail(e);
                                return;
                            }

                            failCount.incrementAndGet();
                            latch.countDown();
                        }
                    }
            );

            // 等待网络或超时
            try {
                latch.await(50, TimeUnit.MILLISECONDS);
            } catch (InterruptedException ignored) {}

            // 轻微休息
            //try { TimeUnit.MILLISECONDS.sleep(10); } catch (InterruptedException ignored){}

            int done = successCount.get() + failCount.get();
            // 每隔 2 条 或 最后一条 更新一次 UI
            if (done % 2 == 0 || done == total) {
                postProgress(done, total,
                        successCount.get(),
                        failCount.get(),
                        cb);
            }
        }

        // 完成回调
        uiHandler.post(() -> cb.onComplete(
                successCount.get(), failCount.get()
        ));
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
