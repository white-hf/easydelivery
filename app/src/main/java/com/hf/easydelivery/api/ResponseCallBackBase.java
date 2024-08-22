package com.hf.easydelivery.api;

import android.widget.Toast;

import com.hf.courierservice.ResponseCallBack;
import com.hf.courierservice.Result;
import com.hf.courierservice.apihelper.exception.UnAuthorizedException;
import com.hf.easydelivery.ResourceMgr;
import com.hf.easydelivery.common.FileLog;

public class ResponseCallBackBase<T> implements ResponseCallBack<T> {

    @Override
    public void onComplete(Result<T> result) {
    }

    @Override
    public void onFail(Exception result) {
        handleUnAuthorized(result);
    }

    protected void handleUnAuthorized(Exception result) {
        if(result instanceof UnAuthorizedException)
        {
            Toast.makeText(ResourceMgr.getInstance().getCtx() , com.hf.easydelivery.R.string.action_need_login , Toast.LENGTH_SHORT).show();
        }
        else
        {
            Toast.makeText(ResourceMgr.getInstance().getCtx(), com.hf.easydelivery.R.string.action_req_failure, Toast.LENGTH_SHORT).show();
            FileLog.getInstance().writeLog("error:" + result.getMessage());
        }
    }
}

