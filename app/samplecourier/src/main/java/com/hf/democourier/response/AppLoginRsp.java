package com.hf.democourier.response;

import com.hf.courierservice.ResponseCallBack;
import com.hf.courierservice.Result;
import com.hf.courierservice.apihelper.TaskBase;

/**
 * This class is to showcase to handle the login response.
 * @author jvtang
 * @since 2024-08-23
 */
public class AppLoginRsp implements TaskBase<String> {
    private String rspCode;
    private String rspMessage;
    private String strToken;

    public String getRspCode() {
        return rspCode;
    }

    public void setRspCode(String rspCode) {
        this.rspCode = rspCode;
    }

    public String getRspMessage() {
        return rspMessage;
    }

    public void setRspMessage(String rspMessage) {
        this.rspMessage = rspMessage;
    }

    public String getStrToken() {
        return strToken;
    }

    public void setStrToken(String strToken) {
        this.strToken = strToken;
    }

    @Override
    public void doIt(ResponseCallBack<String> cb) {
        if (this.getRspCode().equalsIgnoreCase("SUCCESS")) {

            //Get token from response,....
            String userToken = this.getStrToken();

            //App does not need the token.
            cb.onComplete(new Result.Success<String>(""));
        } else {
            Exception ex = new Exception(getRspMessage());
            cb.onFail(ex);
        }
    }
}
