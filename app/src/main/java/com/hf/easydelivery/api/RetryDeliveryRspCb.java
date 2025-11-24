package com.hf.easydelivery.api;

import com.hf.courierservice.Result;
import com.hf.courierservice.IResponseCallBack;
import com.hf.courierservice.apihelper.FileLog;
import androidx.annotation.NonNull;

public class RetryDeliveryRspCb implements IResponseCallBack<Void> {
    private final String trackingId;
    private final Callback callback;

    public interface Callback {
        void onSuccess();

        void onFail(String error);
    }

    public RetryDeliveryRspCb(String trackingId, Callback callback) {
        this.trackingId = trackingId;
        this.callback = callback;
    }

    @Override
    public void onComplete(Result<Void> result) {
        FileLog.getInstance().writeLog("[Retry] success trackingId=" + trackingId);
        if (callback != null) {
            callback.onSuccess();
        }
    }

    @Override
    public void onFail(Exception result) {
        String msg = result != null ? result.getMessage() : "Unknown error";
        FileLog.getInstance().writeLog("[Retry] fail trackingId=" + trackingId + ", err=" + msg);
        if (callback != null) {
            callback.onFail(msg);
        }
    }
}
