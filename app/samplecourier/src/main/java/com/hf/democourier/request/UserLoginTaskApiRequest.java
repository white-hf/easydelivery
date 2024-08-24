package com.hf.democourier.request;

import com.android.volley.Request;
import com.hf.courierservice.apihelper.ApiRequestBase;
import com.hf.democourier.response.AppLoginRsp;

public class UserLoginTaskApiRequest extends ApiRequestBase<AppLoginReq, AppLoginRsp> {
    private static final String DOMAIN_API = "courier-api.hf.com"; //Replace with your domain
    private static final String URL_APP_LOGIN = DOMAIN_API + "/login";

    public UserLoginTaskApiRequest(AppLoginReq req) {
        super(req, AppLoginRsp.class);
        mUrl = URL_APP_LOGIN;

        this.setMethod(Request.Method.POST);
    }
}
