package com.hf.easydelivery.api;

import android.widget.Toast;

import com.hf.courierservice.ResponseCallBack;
import com.hf.courierservice.Result;
import com.hf.easydelivery.event.Event;
import com.hf.easydelivery.event.EventConstant;
import com.hf.easydelivery.R;
import com.hf.easydelivery.ResourceMgr;
import com.hf.easydelivery.common.FileLog;

public class LoginResponseCb implements ResponseCallBack<String> {
    @Override
    public void onComplete(Result<String> result) {
        Result.Success<String> success = (Result.Success<String>) result;
        ResourceMgr.getInstance().getLoginInfo().bIsLoggedIn = true;

        //notify other modules
        ResourceMgr.getInstance().getPublisher().notify(EventConstant.EVENT_LOGIN , new Event<String>(ResourceMgr.getInstance().getLoginInfo().loginName));

        Toast.makeText(ResourceMgr.getInstance().getCtx(), R.string.str_login_success, Toast.LENGTH_SHORT).show();
    }

    @Override
    public void onFail(Exception result) {
        Toast.makeText(ResourceMgr.getInstance().getCtx(), R.string.str_login_failure, Toast.LENGTH_SHORT).show();
        FileLog.getInstance().writeLog(result.getMessage());
    }
}
