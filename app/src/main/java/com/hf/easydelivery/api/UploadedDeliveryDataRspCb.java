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

import androidx.annotation.NonNull;

public class UploadedDeliveryDataRspCb extends ResponseCallBackBase<Void>{

    private final PendingPackagesMgr mgr;
    private final PackageEntity deliveryInfo;

    public UploadedDeliveryDataRspCb(@NonNull PendingPackagesMgr mgr, @NonNull PackageEntity deliveryInfo)
    {
        this.mgr = mgr;
        this.deliveryInfo = deliveryInfo;
    }

    @Override
    public void onComplete(Result<Void> result) {
        FileLog.getInstance().writeLog("[Upload] success trackingId=" + deliveryInfo.trackingId +
                ", orderId=" + deliveryInfo.orderId +
                ", lat=" + deliveryInfo.latitude + ", lng=" + deliveryInfo.longitude);
        // 统一交给 PendingPackagesMgr 做状态更新、事件分发、去重管理
        mgr.onUploadSuccess(deliveryInfo);
    }

    @Override
    public void onFail(Exception result) {
        final String base = "[Upload] fail trackingId=" + deliveryInfo.trackingId +
                ", orderId=" + deliveryInfo.orderId +
                ", lat=" + deliveryInfo.latitude + ", lng=" + deliveryInfo.longitude +
                ", err=" + (result == null ? "<null>" : result.getClass().getSimpleName()) +
                ": " + (result == null ? "" : result.getMessage());
        FileLog.getInstance().writeLog(base);

        if (result instanceof UnAuthorizedException) {
            // 401：不在此处直接重入队，统一交给管理类处理（发布登录事件 + 延后重试）
            mgr.onUnauthorized(deliveryInfo);
            return;
        }

        if (result instanceof ForbiddenException) {
            // 403（已上传或被业务拒绝但视为完成）：直接视为成功，防止重复
            FileLog.getInstance().writeLog("[Upload] forbidden -> treat as success (dedupe) trackingId=" + deliveryInfo.trackingId);
            mgr.onUploadSuccess(deliveryInfo);
            return;
        }

        if (result instanceof TooMuchRequestException) {
            // 429：可重试错误，交由管理类做指数回退与延时重入队
            mgr.onUploadRetriableFailure(deliveryInfo, 429, result.getMessage());
            return;
        }

        if (result instanceof IOException) {
            // 网络类异常：可重试
            mgr.onUploadRetriableFailure(deliveryInfo, -1, result.getMessage());
            return;
        }

        // 其它 4xx 等不可重试错误：标记失败，由管理类发失败通知（不立即重试）
        mgr.onUploadUnrecoverableFailure(deliveryInfo, 400, result == null ? null : result.getMessage());
    }
}
