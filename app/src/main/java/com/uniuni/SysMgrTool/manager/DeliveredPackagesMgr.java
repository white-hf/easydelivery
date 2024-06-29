package com.uniuni.SysMgrTool.manager;

import android.content.Context;
import android.os.Handler;

import androidx.annotation.NonNull;
import androidx.room.Room;

import com.uniuni.SysMgrTool.Event.Event;
import com.uniuni.SysMgrTool.Event.EventConstant;
import com.uniuni.SysMgrTool.Event.Subscriber;
import com.uniuni.SysMgrTool.MySingleton;
import com.uniuni.SysMgrTool.Request.DeliveredUploadParams;
import com.uniuni.SysMgrTool.common.MultipartUploader;
import com.uniuni.SysMgrTool.dao.DeliveredPackagesDao;
import com.uniuni.SysMgrTool.dao.PackageEntity;

import java.io.File;
import java.io.IOException;
import java.net.SocketTimeoutException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;

import javax.net.ssl.HttpsURLConnection;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.Response;

public class DeliveredPackagesMgr implements Subscriber {

    public enum PackageStatus {
        NOT_UPLOADED("unloaded"),
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

    private static final String DELIVERED_API = "https://delivery-service-api.uniuni.ca/delivery";

    private LinkedList<PackageEntity> packageList;
    private final BlockingQueue<PackageEntity> packageQueue = new LinkedBlockingQueue<>();
    private final ExecutorService executorService = Executors.newFixedThreadPool(2); // 1 producer, 1 consumer

    private final DeliveredPackagesDao deliveredPackagesDao = MySingleton.getInstance().getmMydb().getDeliveredPackagesDao();

    public DeliveredPackagesMgr() {
        MySingleton.getInstance().getPublisher().subscribe(EventConstant.EVENT_LOGIN , this);
        packageList = new LinkedList<>();

        // Start the consumer thread
        executorService.execute(this::consumePackages);
    }

    public final ExecutorService getExecutorService() {
        return executorService;
    }

    public Boolean exit(String trackingId) {
        if (trackingId == null || trackingId.isEmpty()) {
            return Boolean.FALSE;
        }

        PackageEntity packageEntity = packageList.stream().filter(pkg->pkg.trackingId.equals(trackingId)).findFirst().orElse(null);
        if (packageEntity != null)
            return Boolean.TRUE;

        return Boolean.FALSE;
    }

    //save the deliverd package data to the local database and the queue for uploading to the server
    //first save, then upload. The strategy can ensure data will be uploaded with bad internet service.
    //there might be a risk losing data when the storage is damaged or the device is reset.
    //So we must assess the importance of the data before uploading.
    public void save(PackageEntity packageEntity) {
        final Handler dbHandler = MySingleton.getInstance().getmDbHandler();

        dbHandler.post(()->{
            deliveredPackagesDao.insert(packageEntity);
            packageList.add(packageEntity);
            packageQueue.add(packageEntity); //upload to server
        });
    }

    /**
     * it should be called when user login.
     * @param driverId
     * @param status
     */
    public void load(Short driverId, String status) {
        final Handler dbHandler = MySingleton.getInstance().getmDbHandler();
        dbHandler.post(()->{
            List<PackageEntity> packages = deliveredPackagesDao.loadByDriverAndStatus(driverId, status);
            packageList.clear();
            packageList.addAll(packages);

            packageQueue.addAll(packages);//the leftover data from last login needs to be uploaded
        });
    }

    /**
     * update the status of the package, it should be called when the package is delivered and the picture is uploaded successfully.
     * @param trackingId
     * @param newStatus
     */
    public void update(String trackingId, String newStatus) {
        final Handler dbHandler = MySingleton.getInstance().getmDbHandler();
        dbHandler.post(()->{
            ListIterator<PackageEntity> iterator = packageList.listIterator();
            while (iterator.hasNext()) {
                PackageEntity packageEntity = iterator.next();
                if (packageEntity.trackingId.equals(trackingId)) {
                    packageEntity.status = newStatus;
                    deliveredPackagesDao.update(packageEntity);
                    iterator.remove();
                    break;
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
        params.setAuthorization(MySingleton.getInstance().getLoginInfo().userToken);

        Map<String, String> formFields = new HashMap<>();
        formFields.put("delivered_location", "1");
        formFields.put("order_id", String.valueOf(deliveryInfo.orderId));
        formFields.put("longitude", String.valueOf(deliveryInfo.longitude));
        formFields.put("latitude",String.valueOf(deliveryInfo.latitude));
        formFields.put("delivery_result", "0");
        params.setFormFields(formFields);

        // List of image files
        List<File> imageFiles = Arrays.asList(
                new File(getFilesDir(), "image0.jpeg"),
                new File(getFilesDir(), "image1.jpeg")
        );

        params.setImageFiles(imageFiles);

        MultipartUploader.upload(params, new Callback() {
            @Override
            public void onFailure(@NonNull Call call, @NonNull IOException e) {
                //if the upload failed, we need to requeue the package data to be uploaded again.
                packageQueue.add(deliveryInfo);

                if (e instanceof SocketTimeoutException) {
                    // Handle timeout
                    System.out.println("Upload failed: Timeout");
                } else {
                    // Other I/O error
                    System.out.println("Upload failed: " + e.getMessage());
                }
            }

            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                if (!response.isSuccessful()) {
                    // Handle unsuccessful HTTP response
                    System.out.println("Upload failed: HTTP status code " + response.code());
                    if (response.code() == HttpsURLConnection.HTTP_UNAUTHORIZED) {
                        //it is possible that the token has expired, so we need to login again.
                        MySingleton.getInstance().getPublisher().notify(EventConstant.EVENT_LOGIN_REQUEST,null);
                    }else
                    {
                        // Other HTTP error
                        System.out.println("Upload failed: " + response.message());
                        //notify ui to show error message
                        MySingleton.getInstance().getPublisher().notify(EventConstant.EVENT_UPLOAD_FAILURE,new Event<Integer>(response.code()));
                    }

                    packageQueue.add(deliveryInfo);
                }
                else {
                    // Handle successful HTTP response
                    System.out.println("Upload successful:" + deliveryInfo.trackingId);
                    update(deliveryInfo.trackingId, PackageStatus.UPLOADED.getStatus());
                }
            }
        });
    }

    private String getFilesDir() {
        return null;
    }

    private void consumePackages() {
        try {
            while (true) {
                PackageEntity deliveryInfo = packageQueue.take();
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
        load((Short)event.getMessage() , PackageStatus.NOT_UPLOADED.getStatus());
    }
}

