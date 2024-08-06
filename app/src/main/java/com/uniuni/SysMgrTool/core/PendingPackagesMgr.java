package com.uniuni.SysMgrTool.core;

import android.os.Handler;
import android.util.Log;

import com.uniuni.SysMgrTool.Event.Event;
import com.uniuni.SysMgrTool.Event.EventConstant;
import com.uniuni.SysMgrTool.Event.Subscriber;
import com.uniuni.SysMgrTool.MySingleton;
import com.uniuni.SysMgrTool.Request.DeliveredUploadParams;
import com.uniuni.SysMgrTool.common.FileLog;
import com.uniuni.SysMgrTool.common.MultipartUploader;
import com.uniuni.SysMgrTool.common.UploadCallback;
import com.uniuni.SysMgrTool.dao.DeliveredPackagesDao;
import com.uniuni.SysMgrTool.dao.DeliveryInfo;
import com.uniuni.SysMgrTool.dao.PackageEntity;

import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;

import okhttp3.Callback;

/**
 * This class manages the delivered packages, including saving the delivered packages to the database,uploading the delivered packages to the server, and loading the delivered packages from the database.
 * It use the queue and thread pool for uploading the delivered packages to the server.
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

    private static final String DELIVERED_API = "https://delivery-service-api.uniuni.ca/delivery";

    //private static final String DELIVERED_API = "http://192.168.2.23:8964/delivery";

    private LinkedList<PackageEntity> waitingUploadPackageList;
    private final BlockingQueue<PackageEntity> packageQueue = new LinkedBlockingQueue<>();
    private final ExecutorService executorService = Executors.newFixedThreadPool(1); // 1 producer, 1 consumer

    private final DeliveredPackagesDao deliveredPackagesDao = MySingleton.getInstance().getmMydb().getDeliveredPackagesDao();

    public PendingPackagesMgr() {
        MySingleton.getInstance().getPublisher().subscribe(EventConstant.EVENT_LOGIN , this);
        waitingUploadPackageList = new LinkedList<>();

        // Start the consumer thread
        executorService.execute(this::consumePackages);
    }

    public void addQueue(PackageEntity packageEntity) {
        packageQueue.add(packageEntity);
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
        final Handler dbHandler = MySingleton.getInstance().getDbHandler();

        dbHandler.post(()->{
            try {
                deliveredPackagesDao.insert(packageEntity);
                waitingUploadPackageList.add(packageEntity);
                packageQueue.add(packageEntity); //upload to server
            }catch (Exception e) {
                Log.e(MySingleton.TAG , "save delivery data failed" , e);
                FileLog.getInstance().writeLog("save delivery data failed" + e.getMessage());
            }

            MySingleton.getInstance().getMainHandler().post(()->{
                MySingleton.getInstance().getPublisher().notify(EventConstant.EVENT_SAVE_DELIVERY_SUCCESS , new Event<PackageEntity>(packageEntity));
            });
        });
    }

    /**
     * it should be called when user login.
     * @param driverId
     * @param status
     */
    public void load(Short driverId, String status) {
        final Handler dbHandler = MySingleton.getInstance().getDbHandler();
        dbHandler.post(()->{
            List<PackageEntity> packages = deliveredPackagesDao.loadByDriverAndStatus(driverId, status);
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
        final Handler dbHandler = MySingleton.getInstance().getDbHandler();
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
                Log.e(MySingleton.TAG, "update delivery data failed", e);
                FileLog.getInstance().writeLog("update delivery data failed" + e.getMessage());
            }
        });
    }

    public void fixtool() {

        final Handler dbHandler = MySingleton.getInstance().getDbHandler();
        PackageEntity packageEntity = null;
        dbHandler.post(() -> {
            String[] routeIds = {
                    "3"
            };

            for (String routeId : routeIds) {
                final DeliveryInfo info = MySingleton.getInstance().getDeliveryinfoMgr().getByRouteId(routeId);
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
        DeliveredUploadParams params = new DeliveredUploadParams();
        params.setUrl(DELIVERED_API);
        params.setAuthorization("Bearer" + " " + MySingleton.getInstance().getLoginInfo().userToken);

        Map<String, String> formFields = new HashMap<>();
        formFields.put("delivered_location", "1");
        formFields.put("order_id", String.valueOf(deliveryInfo.orderId));
        formFields.put("longitude", String.valueOf(deliveryInfo.longitude));
        formFields.put("latitude",String.valueOf(deliveryInfo.latitude));
        formFields.put("delivery_result", "0");
        params.setFormFields(formFields);

        params.setImageFiles(deliveryInfo.imagePath);
        Callback cb = new UploadCallback(this , packageQueue , deliveryInfo);
        boolean bSuccess  = MultipartUploader.upload(params,cb);

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

    private void consumePackages() {
        try {
            while (true) {
                if (packageQueue.size() > 5){
                    //to avoid too many requests, like when there is a network issue, accumulating too many requests.
                    Thread.sleep(1000);
                }

                if ( MySingleton.getInstance().getLoginInfo().userToken == null)
                {
                    Thread.sleep(1000);
                    continue;
                }

                PackageEntity deliveryInfo = packageQueue.take();

                //check if the package is delivered
//                if (!MySingleton.getInstance().getdDeliveryinfoMgr().exit(deliveryInfo.orderId)) {
//                    update(deliveryInfo.trackingId, PackageStatus.UPLOADED.getStatus());
//                    MySingleton.getInstance().getMainHandler().post(() -> {
//                        MySingleton.getInstance().getPublisher().notify(EventConstant.EVENT_UPLOAD_SUCCESS, new Event<com.uniuni.SysMgrTool.dao.PackageEntity>(deliveryInfo));
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
        load((Short)event.getMessage() , PackageStatus.Pending.getStatus());
    }
}

