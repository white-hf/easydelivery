package com.hf.easydelivery.api;

import static java.lang.Thread.sleep;

import com.hf.courierservice.Result;
import com.hf.courierservice.apihelper.exception.ForbiddenException;
import com.hf.courierservice.apihelper.exception.TooMuchRequestException;
import com.hf.courierservice.apihelper.exception.UnAuthorizedException;
import com.hf.easydelivery.event.Event;
import com.hf.easydelivery.event.EventConstant;
import com.hf.easydelivery.ResourceMgr;
import com.hf.easydelivery.common.FileLog;
import com.hf.easydelivery.core.PendingPackagesMgr;
import com.hf.easydelivery.dao.PackageEntity;

import java.io.IOException;

public class UploadedDeliveryDataRspCb extends ResponseCallBackBase<Void>{

    private final PendingPackagesMgr mgr;
    private final PackageEntity deliveryInfo;

    public UploadedDeliveryDataRspCb(PendingPackagesMgr mgr, PackageEntity deliveryInfo)
    {
        this.mgr = mgr;
        this.deliveryInfo = deliveryInfo;
    }

    @Override
    public void onComplete(Result<Void> result) {
        FileLog.getInstance().writeLog("Upload successful:" + deliveryInfo.trackingId);

        mgr.update(deliveryInfo.trackingId, PendingPackagesMgr.PackageStatus.UPLOADED.getStatus());
        notifyUiUploadResult(true,  deliveryInfo);
    }

    @Override
    public void onFail(Exception result) {
        if (result instanceof UnAuthorizedException)
        {
            //need to login again
            ResourceMgr.getInstance().getMainHandler().post(() -> {
                ResourceMgr.getInstance().getPublisher().notify(EventConstant.EVENT_TO_LOGIN, new Event<Void>(null));
            });

            ResourceMgr.getInstance().getLoginInfo().bIsLoggedIn = false;
            mgr.addQueue(deliveryInfo , false);
        }else if (result instanceof ForbiddenException)
        {
            //already uploaded, because of local cache, there might be a temporary data inconsistency, but it doesn't matter.
            mgr.update(deliveryInfo.trackingId, PendingPackagesMgr.PackageStatus.UPLOADED.getStatus());
            ResourceMgr.getInstance().getMainHandler().post(() -> {
                ResourceMgr.getInstance().getPublisher().notify(EventConstant.EVENT_UPLOAD_SUCCESS, new Event<com.hf.easydelivery.dao.PackageEntity>(deliveryInfo));
            });
        }else if (result instanceof TooMuchRequestException)
        {
            //too many requests, we need to wait a while.
            mgr.addQueue(deliveryInfo , false);
            try {
                sleep(5000);
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        }else if (result instanceof IOException)
        {
            //This is network error, we need to retry.
            mgr.addQueue(deliveryInfo , false);
            FileLog.getInstance().writeLog("MultipartUploader Upload failed for network error:" + deliveryInfo.trackingId + " " + result.getMessage());
        }
        else
        {
            notifyUiUploadResult(false, deliveryInfo);
            mgr.update(deliveryInfo.trackingId, PendingPackagesMgr.PackageStatus.FAILED.getStatus());
        }

        FileLog.getInstance().writeLog("MultipartUploader Upload failed:" + deliveryInfo.trackingId + " " + result.getMessage());
    }

    private void notifyUiUploadResult (boolean bSuccess, PackageEntity  deliveryInfo)
    {
        if (!bSuccess) {
            ResourceMgr.getInstance().getMainHandler().post(() -> {
                ResourceMgr.getInstance().getPublisher().notify(EventConstant.EVENT_UPLOAD_FAILURE, new Event<com.hf.easydelivery.dao.PackageEntity>(deliveryInfo));
            });
        } else {
            ResourceMgr.getInstance().getMainHandler().post(() -> {
                ResourceMgr.getInstance().getPublisher().notify(EventConstant.EVENT_UPLOAD_SUCCESS, new Event<com.hf.easydelivery.dao.PackageEntity>(deliveryInfo));
            });
        }
    }
}
