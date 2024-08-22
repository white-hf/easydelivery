package com.hf.easydelivery.core;

import android.os.Handler;
import android.util.Log;

import com.hf.courierservice.ICourierService;
import com.hf.easydelivery.event.Event;
import com.hf.easydelivery.event.EventConstant;
import com.hf.easydelivery.event.Subscriber;
import com.hf.courierservice.bean.DeliveredUploadParams;
import com.hf.easydelivery.ResourceMgr;
import com.hf.easydelivery.api.UploadedDeliveryDataRspCb;
import com.hf.easydelivery.common.FileLog;
import com.hf.easydelivery.dao.DeliveredPackagesDao;
import com.hf.easydelivery.dao.DeliveryInfo;
import com.hf.easydelivery.dao.PackageEntity;

import java.util.LinkedList;
import java.util.List;
import java.util.ListIterator;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;

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

    private LinkedList<PackageEntity> waitingUploadPackageList;
    private final BlockingQueue<PackageEntity> packageQueue = new LinkedBlockingQueue<>();
    private final ExecutorService executorService = Executors.newFixedThreadPool(1); // 1 producer, 1 consumer

    private final DeliveredPackagesDao deliveredPackagesDao = ResourceMgr.getInstance().getmMydb().getDeliveredPackagesDao();

    public PendingPackagesMgr() {
        ResourceMgr.getInstance().getPublisher().subscribe(EventConstant.EVENT_LOGIN , this);
        waitingUploadPackageList = new LinkedList<>();

        // Start the consumer thread
        executorService.execute(this::consumePackages);
    }

    public void addQueue(PackageEntity packageEntity , boolean bAddToWaitList) {
        packageQueue.add(packageEntity);

        if (bAddToWaitList)
            waitingUploadPackageList.add(packageEntity);
    }

    public Boolean exit(String trackingId) {
        if (trackingId == null || trackingId.isEmpty()) {
            return Boolean.FALSE;
        }

        PackageEntity packageEntity = waitingUploadPackageList.stream().filter(pkg->pkg.trackingId.equals(trackingId)).findFirst().orElse(null);
        if (packageEntity != null)
            return Boolean.TRUE;

        return Boolean.FALSE;
    }

    //save the deliverd package data to the local database and the queue for uploading to the server
    //first save, then upload. The strategy can ensure data will be uploaded with bad internet service.
    //there might be a risk losing data when the storage is damaged or the device is reset.
    //So we must assess the importance of the data before uploading.
    public void save(PackageEntity packageEntity) {
        final Handler dbHandler = ResourceMgr.getInstance().getDbHandler();

        dbHandler.post(()->{
            try {
                deliveredPackagesDao.insert(packageEntity);
                waitingUploadPackageList.add(packageEntity);
                packageQueue.add(packageEntity); //upload to server

                ResourceMgr.getInstance().getMainHandler().post(()->{
                    ResourceMgr.getInstance().getPublisher().notify(EventConstant.EVENT_SAVE_DELIVERY_SUCCESS , new Event<PackageEntity>(packageEntity));
                });
            }catch (Exception e) {
                Log.e(ResourceMgr.TAG , "save delivery data failed" , e);
                FileLog.getInstance().writeLog("save delivery data failed" + e.getMessage());
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
        dbHandler.post(()->{
            List<PackageEntity> packages = deliveredPackagesDao.loadByDriverAndStatus(Short.parseShort(driverId), status);
            waitingUploadPackageList.clear();
            waitingUploadPackageList.addAll(packages);

            packageQueue.addAll(packages);//the leftover data from last login needs to be uploaded
        });
    }


    /**
     * update the status of the package, it should be called when the package is delivered and the picture is uploaded successfully.
     * @param trackingId
     * @param newStatus
     */
    public void update(String trackingId, String newStatus) {
        final Handler dbHandler = ResourceMgr.getInstance().getDbHandler();
        dbHandler.post(() -> {
            try {
                ListIterator<PackageEntity> iterator = waitingUploadPackageList.listIterator();
                while (iterator.hasNext()) {
                    PackageEntity packageEntity = iterator.next();
                    if (packageEntity.trackingId.equals(trackingId)) {
                        packageEntity.status = newStatus;
                        packageEntity.saveTime = System.currentTimeMillis();
                        int rows = deliveredPackagesDao.update(packageEntity);

                        if (rows == 0)
                            throw new Exception("update failed");

                        iterator.remove();
                        break;
                    }
                }
            } catch (Exception e) {
                Log.e(ResourceMgr.TAG, "update delivery data failed", e);
                FileLog.getInstance().writeLog("update delivery data failed" + e.getMessage());
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
        params.setLatitude(deliveryInfo.longitude);
        params.setLongitude(deliveryInfo.latitude);
        params.setImageFiles(deliveryInfo.imagePath);

        ICourierService courierService = ResourceMgr.getInstance().getCourierService();
        assert courierService != null;

        UploadedDeliveryDataRspCb uploadedDeliveryDataRspCb = new UploadedDeliveryDataRspCb(this, deliveryInfo);
        boolean bSuccess  = courierService.uploadDeliveredPackages(params , uploadedDeliveryDataRspCb);
        return bSuccess;
    }

    private void consumePackages() {
        try {
            while (true) {
                if (packageQueue.size() > 5){
                    //to avoid too many requests, like when there is a network issue, accumulating too many requests.
                    Thread.sleep(1000);
                }

                if (!ResourceMgr.getInstance().getLoginInfo().bIsLoggedIn)
                {
                    Thread.sleep(1000);
                    continue;
                }

                PackageEntity deliveryInfo = packageQueue.take();

                //check if the package is delivered
//                if (!MySingleton.getInstance().getdDeliveryinfoMgr().exit(deliveryInfo.orderId)) {
//                    update(deliveryInfo.trackingId, PackageStatus.UPLOADED.getStatus());
//                    MySingleton.getInstance().getMainHandler().post(() -> {
//                        MySingleton.getInstance().getPublisher().notify(EventConstant.EVENT_UPLOAD_SUCCESS, new Event<com.hf.easydelivery.dao.PackageEntity>(deliveryInfo));
//                    });
//                }else
                    upload(deliveryInfo);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    public void shutdown() {
        executorService.shutdown();
    }

    @Override
    public void receive(Event event) {
        load((String)event.getMessage() , PackageStatus.Pending.getStatus());
    }
}

