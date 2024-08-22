package com.hf.courierservice;


/**
 * This is an interface for courier service response callback.
 * @author jvtang
 * @since 2024-08-21
 */
public interface ResponseCallBack<T> {
        void onComplete(Result<T> result);
        void onFail(Exception result);
}
