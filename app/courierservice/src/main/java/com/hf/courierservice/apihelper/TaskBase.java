package com.hf.courierservice.apihelper;

import com.hf.courierservice.IResponseCallBack;

/**
 * This is a interface for tasks handled asynchronously in the handler
 */
public interface TaskBase<T> {
    void doIt(IResponseCallBack<T> cb);
}
