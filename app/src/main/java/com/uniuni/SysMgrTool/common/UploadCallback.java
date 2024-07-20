package com.uniuni.SysMgrTool.common;

import static java.lang.Thread.sleep;

import androidx.annotation.NonNull;

import com.uniuni.SysMgrTool.Event.Event;
import com.uniuni.SysMgrTool.Event.EventConstant;
import com.uniuni.SysMgrTool.MySingleton;
import com.uniuni.SysMgrTool.dao.PackageEntity;
import com.uniuni.SysMgrTool.manager.PendingPackagesMgr;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.util.concurrent.BlockingQueue;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.Response;

public class UploadCallback implements Callback {
    public static final int TOO_MUCH_REQUEST = 429;
    private final BlockingQueue<PackageEntity> packageQueue;
    private final PendingPackagesMgr mgr;
    private final PackageEntity deliveryInfo;
    public UploadCallback(PendingPackagesMgr mgr, BlockingQueue<PackageEntity> packageQueue, PackageEntity deliveryInfo)
    {
        this.packageQueue = packageQueue;
        this.mgr = mgr;
        this.deliveryInfo = deliveryInfo;
    }

    @Override
    public void onFailure(Call call, @NonNull IOException e) {
        //if the upload failed, we need to requeue the package data to be uploaded again.
        packageQueue.add(deliveryInfo);
        FileLog.getInstance().writeLog("MultipartUploader Upload failed:" + deliveryInfo.trackingId + " " + e.getMessage());
    }

    @Override
    public void onResponse(Call call, @NonNull Response response) throws IOException {
        if (!response.isSuccessful()) {
            //This is a unusual situation or an error that server knows, it should not be checked.
            //We need handle it according to the http code
            if (response.code() == HttpURLConnection.HTTP_UNAUTHORIZED) {
                //need to login again
                MySingleton.getInstance().getMainHandler().post(() -> {
                    MySingleton.getInstance().getPublisher().notify(EventConstant.EVENT_TO_LOGIN, new Event<Integer>(response.code()));
                });

                MySingleton.getInstance().getLoginInfo().userToken = null;
                packageQueue.add(deliveryInfo);
            }
            else if (response.code() == HttpURLConnection.HTTP_FORBIDDEN)
            {
                //already uploaded, because of local cache, there might be a temporary data inconsistency, but it doesn't matter.
                mgr.update(deliveryInfo.trackingId, PendingPackagesMgr.PackageStatus.UPLOADED.getStatus());
                MySingleton.getInstance().getMainHandler().post(() -> {
                    MySingleton.getInstance().getPublisher().notify(EventConstant.EVENT_UPLOAD_SUCCESS, new Event<com.uniuni.SysMgrTool.dao.PackageEntity>(deliveryInfo));
                });
            }
            else if (response.code() == TOO_MUCH_REQUEST)
            {
                //too many requests, we need to wait a while.
                packageQueue.add(deliveryInfo);
                try {
                    sleep(5000);
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }
            }
            else {
                notifyUiUploadResult(false, response.code(), null);
                mgr.update(deliveryInfo.trackingId, PendingPackagesMgr.PackageStatus.FAILED.getStatus());
            }

            FileLog.getInstance().writeLog("Upload response is unsuccessful:" + deliveryInfo.trackingId + " " + response.code());
        } else {
            // Handle successful HTTP response
            if (response.body() != null) {
                String responseBody = response.body().string();
                JSONObject jsonResponse = null;
                try {
                    jsonResponse = new JSONObject(responseBody);
                    String bizCode = jsonResponse.getString("biz_code");
                    if (bizCode.equals("DELIVERY.SUBMIT.SUCCESS")) {
                        FileLog.getInstance().writeLog("Upload successful:" + deliveryInfo.trackingId);

                        mgr.update(deliveryInfo.trackingId, PendingPackagesMgr.PackageStatus.UPLOADED.getStatus());
                        notifyUiUploadResult(true, 0, deliveryInfo);
                    } else {
                        //It depends on the specific business logic.
                        FileLog.getInstance().writeLog("Upload unsuccessfully due to bizcode:" + deliveryInfo.trackingId + " " + responseBody);

                        //this situation is unusual, it should not be checked.
                        mgr.update(deliveryInfo.trackingId, PendingPackagesMgr.PackageStatus.FAILED.getStatus());
                        notifyUiUploadResult(false, 0, null);
                    }
                } catch (JSONException e) {
                    //this situation is unusual, it should not be checked.
                    mgr.update(deliveryInfo.trackingId, PendingPackagesMgr.PackageStatus.FAILED.getStatus());
                    notifyUiUploadResult(false, 0, null);
                    FileLog.getInstance().writeLog("Upload unsuccessfully due to json exception:" + deliveryInfo.trackingId + " " + responseBody);
                }

            } else {
                //this situation is unusual, it should not be checked.
                mgr.update(deliveryInfo.trackingId, PendingPackagesMgr.PackageStatus.FAILED.getStatus());
                notifyUiUploadResult(false, 0, null);
                FileLog.getInstance().writeLog("Upload unsuccessfully due to empty body:" + deliveryInfo.trackingId);
            }
        }
    }

    private void notifyUiUploadResult ( boolean bSuccess, int code, PackageEntity
            deliveryInfo)
    {
        if (!bSuccess) {
            MySingleton.getInstance().getMainHandler().post(() -> {
                MySingleton.getInstance().getPublisher().notify(EventConstant.EVENT_UPLOAD_FAILURE, new Event<Integer>(code));
            });

            if (deliveryInfo != null)
                packageQueue.add(deliveryInfo);
        } else {
            MySingleton.getInstance().getMainHandler().post(() -> {
                MySingleton.getInstance().getPublisher().notify(EventConstant.EVENT_UPLOAD_SUCCESS, new Event<com.uniuni.SysMgrTool.dao.PackageEntity>(deliveryInfo));
            });
        }
    }
}