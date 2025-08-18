package com.hf.easydelivery.core;

import android.os.Handler;

import com.hf.courierservice.ICourierService;
import com.hf.easydelivery.event.Event;
import com.hf.easydelivery.event.EventConstant;
import com.hf.easydelivery.event.Subscriber;
import com.hf.courierservice.bean.DeliveredUploadParams;
import com.hf.easydelivery.ResourceMgr;
import com.hf.easydelivery.api.UploadedDeliveryDataRspCb;
import com.hf.courierservice.apihelper.FileLog;
import com.hf.easydelivery.dao.DeliveredPackagesDao;
import com.hf.easydelivery.dao.DeliveryInfo;
import com.hf.easydelivery.dao.PackageEntity;

import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.LinkedList;
import java.util.ListIterator;
import java.util.Set;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

/**
 * This class manages delivered packages, including saving the delivered packages to the database,uploading the delivered packages to the server, and loading the delivered packages from the database.
 * It use the queue and thread pool for uploading the delivered packages to the server.
 * It is a key class for running without network.
 */
public class PendingPackagesMgr implements Subscriber {

    public ExecutorService getExecutorService() {
        return executorService;
    }

    public int size() {
        return waitingUploadPackageList.size();
    }

    static public enum PackageStatus {
        Pending("waiting_upload"),
        UPLOADED("uploaded"),
        FAILED("failed");
        private final String status;

        PackageStatus(String status) {
            this.status = status;
        }

        public String getStatus() {
            return status;
        }
    }


    private long backoffTime = 1000; // Initial backoff time in milliseconds
    private final long maxBackoffTime = 32000; // Maximum backoff time in milliseconds

    private List<PackageEntity> waitingUploadPackageList;
    // 去重集合：记录已入队的 trackingId，避免重复入队
    private final Set<String> enqueued = Collections.synchronizedSet(new HashSet<>());
    // Consumer 运行标志，用于优雅退出
    private volatile boolean running = true;

    private final BlockingQueue<PackageEntity> packageQueue = new LinkedBlockingQueue<>();
    private final ExecutorService executorService = Executors.newFixedThreadPool(1); // 1 producer, 1 consumer

    private final DeliveredPackagesDao deliveredPackagesDao = ResourceMgr.getInstance().getmMydb().getDeliveredPackagesDao();

    public PendingPackagesMgr() {
        ResourceMgr.getInstance().getPublisher().subscribe(EventConstant.EVENT_LOGIN , this);
        waitingUploadPackageList = Collections.synchronizedList(new LinkedList<>());
        FileLog.getInstance().debug("[PendingMgr] init: subscribe login & start consumer");
        // Start the consumer thread
        executorService.execute(this::consumePackages);
    }

    public void addQueue(PackageEntity packageEntity, boolean bAddToWaitList) {
        final String tracking = packageEntity.trackingId;
        if (tracking == null) {
            FileLog.getInstance().error("[PendingMgr] addQueue: trackingId is null, skip");
            return;
        }
        synchronized (enqueued) {
            if (enqueued.contains(tracking)) {
                FileLog.getInstance().debug("[PendingMgr] addQueue: skip duplicate tracking=" + tracking);
            } else {
                packageQueue.add(packageEntity);
                enqueued.add(tracking);
                FileLog.getInstance().debug("[PendingMgr] addQueue: enqueued tracking=" + tracking + ", qSize=" + packageQueue.size());
            }
        }
        if (bAddToWaitList) {
            waitingUploadPackageList.add(packageEntity);
        }
    }

    public Boolean exit(String trackingId) {
        if (trackingId == null || trackingId.isEmpty()) return Boolean.FALSE;
        PackageEntity packageEntity = waitingUploadPackageList.stream()
                .filter(pkg -> trackingId.equals(pkg.trackingId))
                .findFirst()
                .orElse(null);
        return packageEntity != null ? Boolean.TRUE : Boolean.FALSE;
    }

    //save the deliverd package data to the local database and the queue for uploading to the server
    //first save, then upload. The strategy can ensure data will be uploaded with bad internet service.
    //there might be a risk losing data when the storage is damaged or the device is reset.
    //So we must assess the importance of the data before uploading.
    public void save(PackageEntity packageEntity) {
        final Handler dbHandler = ResourceMgr.getInstance().getDbHandler();
        final String tracking = packageEntity.trackingId;
        FileLog.getInstance().debug("[PendingMgr] save: begin tracking=" + tracking + ", orderId=" + packageEntity.orderId);

        dbHandler.post(() -> {
            try {
                deliveredPackagesDao.insert(packageEntity);
                // 使用统一入口避免重复入队
                addQueue(packageEntity, true);
                FileLog.getInstance().debug("[PendingMgr] save: inserted & enqueued tracking=" + tracking);
                ResourceMgr.getInstance().getMainHandler().post(() ->
                        ResourceMgr.getInstance().getPublisher().notify(
                                EventConstant.EVENT_SAVE_DELIVERY_SUCCESS,
                                new Event<PackageEntity>(packageEntity))
                );
            } catch (Exception e) {
                FileLog.getInstance().error("[PendingMgr] save failed tracking=" + tracking + ", err=" + e.getMessage());
            }
        });
    }

    /**
     * it should be called when user login.
     * @param driverId
     * @param status
     */
    public void load(String driverId, String status) {
        final Handler dbHandler = ResourceMgr.getInstance().getDbHandler();
        FileLog.getInstance().debug("[PendingMgr] load: driver=" + driverId + ", status=" + status);

        dbHandler.post(() -> {
            List<PackageEntity> packages = deliveredPackagesDao.loadByDriverAndStatus(Short.parseShort(driverId), status);
            waitingUploadPackageList.clear();
            waitingUploadPackageList.addAll(packages);
            int added = 0;
            for (PackageEntity p : packages) {
                final String t = p.trackingId;
                synchronized (enqueued) {
                    if (!enqueued.contains(t)) {
                        packageQueue.add(p);
                        enqueued.add(t);
                        added++;
                    }
                }
            }
            FileLog.getInstance().debug("[PendingMgr] load: loaded=" + packages.size() + ", reEnqueued=" + added + ", qSize=" + packageQueue.size());
        });
    }


    /**
     * update the status of the package, it should be called when the package is delivered and the picture is uploaded successfully.
     * @param trackingId
     * @param newStatus
     */
    public void update(String trackingId, String newStatus) {
        final Handler dbHandler = ResourceMgr.getInstance().getDbHandler();
        FileLog.getInstance().debug("[PendingMgr] update: tracking=" + trackingId + ", status=" + newStatus);

        dbHandler.post(() -> {
            try {
                ListIterator<PackageEntity> iterator = waitingUploadPackageList.listIterator();
                while (iterator.hasNext()) {
                    PackageEntity packageEntity = iterator.next();
                    if (trackingId.equals(packageEntity.trackingId)) {
                        packageEntity.status = newStatus;
                        packageEntity.saveTime = System.currentTimeMillis();
                        int rows = deliveredPackagesDao.update(packageEntity);
                        if (rows == 0) throw new Exception("update failed");
                        iterator.remove();
                        synchronized (enqueued) { enqueued.remove(trackingId); }
                        FileLog.getInstance().debug("[PendingMgr] update ok: tracking=" + trackingId + ", status=" + newStatus);
                        break;
                    }
                }
            } catch (Exception e) {
                FileLog.getInstance().error("[PendingMgr] update failed tracking=" + trackingId + ", err=" + e.getMessage());
            }
        });
    }

    public void fixtool() {

        final Handler dbHandler = ResourceMgr.getInstance().getDbHandler();
        PackageEntity packageEntity = null;
        dbHandler.post(() -> {
            String[] routeIds = {
                    "3"
            };

            for (String routeId : routeIds) {
                final DeliveryInfo info = ResourceMgr.getInstance().getDeliveryinfoMgr().getByRouteId(routeId);
                if (info != null) {
                    PackageEntity byOrderId = deliveredPackagesDao.getByOrderId(info.getOrderId());
                    if (byOrderId != null) {
                        byOrderId.status = PackageStatus.Pending.getStatus();
                        deliveredPackagesDao.update(byOrderId);
                    }
                }
            }
        });
    }

    /**
     * upload the delivered packages data to the server, the data includes multiples images, gps, and tracking id.
     * It loops through the package list and uploads the data to the server.
     * It will be triggered when the user clicks the deliver button to save the data, and it
     * will be triggered after user logins and there is data that
     * @param deliveryInfo
     */
    public void upload(PackageEntity deliveryInfo) {
        boolean bSuccess = isUploadSuccess(deliveryInfo);

        if (bSuccess) {
            // Reset backoff time on successful upload
            backoffTime = 1000;
        }
        else {
            // Exponential backoff with a maximum wait time
            backoffTime = Math.min(backoffTime * 2, maxBackoffTime);
            try {
                Thread.sleep(backoffTime);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }

    private boolean isUploadSuccess(PackageEntity deliveryInfo) {
        DeliveredUploadParams params = new DeliveredUploadParams();

        params.setOrderId(deliveryInfo.orderId);
        params.setLatitude(deliveryInfo.latitude);
        params.setLongitude(deliveryInfo.longitude);
        params.setImageFiles(deliveryInfo.imagePath);

        ICourierService courierService = ResourceMgr.getInstance().getCourierService();
        assert courierService != null;

        UploadedDeliveryDataRspCb uploadedDeliveryDataRspCb = new UploadedDeliveryDataRspCb(this, deliveryInfo);
        FileLog.getInstance().debug("[PendingMgr] request: tracking=" + deliveryInfo.trackingId + ", orderId=" + deliveryInfo.orderId);
        FileLog.getInstance().debug("[PendingMgr] upload try tracking=" + deliveryInfo.trackingId + ", lat=" + deliveryInfo.latitude + ", lng=" + deliveryInfo.longitude);
        boolean bSuccess  = courierService.uploadDeliveredPackages(params , uploadedDeliveryDataRspCb);
        FileLog.getInstance().debug("[PendingMgr] request done: tracking=" + deliveryInfo.trackingId + ", success=" + bSuccess);
        return bSuccess;
    }

    private void consumePackages() {
        FileLog.getInstance().debug("[PendingMgr] consumer: start");
        try {
            while (running) {
                if (!ResourceMgr.getInstance().getLoginInfo().bIsLoggedIn) {
                    // 未登录：稍作等待，避免空转
                    TimeUnit.MILLISECONDS.sleep(500);
                    continue;
                }

                // 控制队列洪峰，避免过多请求
                if (packageQueue.size() > 100) {
                    TimeUnit.MILLISECONDS.sleep(500);
                }

                PackageEntity deliveryInfo = packageQueue.poll(1, TimeUnit.SECONDS);
                if (deliveryInfo == null) {
                    continue; // 空轮询
                }

                final String tracking = deliveryInfo.trackingId;
                FileLog.getInstance().debug("[PendingMgr] consumer: dequeued tracking=" + tracking);

                boolean ok = isUploadSuccess(deliveryInfo);
                if (ok) {
                    // 成功：指数退避复位。具体 DB 状态更新请在回调里触发 onUploadSuccess。
                    backoffTime = 1000;
                } else {
                    // 失败：指数退避，不阻塞 consumer 线程，延迟重新入队
                    backoffTime = Math.min(backoffTime * 2, maxBackoffTime);
                    long delayMs = backoffTime;
                    FileLog.getInstance().debug("[PendingMgr] consumer: upload failed, schedule retry in " + delayMs + "ms, tracking=" + tracking);
                    ResourceMgr.getInstance().getMainHandler().postDelayed(() -> {
                        // 允许重试重新入队
                        synchronized (enqueued) {
                            if (!enqueued.contains(tracking)) enqueued.add(tracking);
                        }
                        packageQueue.add(deliveryInfo);
                        FileLog.getInstance().debug("[PendingMgr] re-enqueued tracking=" + tracking);
                    }, delayMs);
                }
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            FileLog.getInstance().error("[PendingMgr] consumer interrupted: " + e.getMessage());
        } finally {
            FileLog.getInstance().debug("[PendingMgr] consumer: exit");
        }
    }

    public void shutdown() {
        running = false;
        executorService.shutdownNow();
        FileLog.getInstance().debug("[PendingMgr] shutdown requested");
    }

    // === Callbacks for upload results (to be called by UploadedDeliveryDataRspCb) ===
    public void onUploadSuccess(PackageEntity pkg) {
        final String tracking = pkg.trackingId;
        FileLog.getInstance().debug("[PendingMgr] onUploadSuccess tracking=" + tracking);
        // 从去重集合移除，允许后续同 tracking 再次入队（通常不需要，但保持一致性）
        synchronized (enqueued) { enqueued.remove(tracking); }
        update(tracking, PackageStatus.UPLOADED.getStatus());
        ResourceMgr.getInstance().getMainHandler().post(() ->
                ResourceMgr.getInstance().getPublisher().notify(
                        EventConstant.EVENT_UPLOAD_SUCCESS,
                        new Event<PackageEntity>(pkg))
        );
    }

    public void onUploadRetriableFailure(PackageEntity pkg, int httpCode, String reason) {
        final String tracking = pkg.trackingId;
        backoffTime = Math.min(backoffTime * 2, maxBackoffTime);
        final long delayMs = backoffTime;
        FileLog.getInstance().error("[PendingMgr] onUploadRetriableFailure tracking=" + tracking + ", http=" + httpCode + ", reason=" + reason + ", retryInMs=" + delayMs);
        ResourceMgr.getInstance().getMainHandler().postDelayed(() -> {
            synchronized (enqueued) {
                if (!enqueued.contains(tracking)) enqueued.add(tracking);
            }
            packageQueue.add(pkg);
            FileLog.getInstance().debug("[PendingMgr] re-enqueued(after fail) tracking=" + tracking);
        }, delayMs);
    }

    public void onUploadUnrecoverableFailure(PackageEntity pkg, int httpCode, String reason) {
        final String tracking = pkg.trackingId;
        FileLog.getInstance().error("[PendingMgr] onUploadUnrecoverableFailure tracking=" + tracking + ", http=" + httpCode + ", reason=" + reason);
        synchronized (enqueued) { enqueued.remove(tracking); }
        // 标记失败
        update(tracking, PackageStatus.FAILED.getStatus());
        // 通知 UI（失败）
        ResourceMgr.getInstance().getMainHandler().post(() ->
                ResourceMgr.getInstance().getPublisher().notify(
                        EventConstant.EVENT_UPLOAD_FAILURE,
                        new Event<PackageEntity>(pkg))
        );
    }

    public void onUnauthorized(PackageEntity pkg) {
        final String tracking = pkg.trackingId;
        FileLog.getInstance().error("[PendingMgr] onUnauthorized tracking=" + tracking + ", notify UI to re-login");
        // 由界面模块处理重新登录：发出未授权事件
        ResourceMgr.getInstance().getMainHandler().post(() ->
                ResourceMgr.getInstance().getPublisher().notify(
                        "EVENT_UNAUTHORIZED",
                        new Event<PackageEntity>(pkg))
        );
        // 同时保留队列中的任务，通过登录成功后 load() 再次入队；如需更激进，也可直接延迟重入队
    }

    @Override
    public void receive(Event event) {
        load((String)event.getMessage() , PackageStatus.Pending.getStatus());
    }
}
