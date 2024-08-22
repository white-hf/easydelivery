package com.hf.uniuni.Request;

import com.hf.uniuni.Response.AppLoginRsp;
import com.hf.courierservice.apihelper.ApiRequestBase;
import com.hf.uniuni.CourierService;

import com.android.volley.Request;

public class UserLoginTaskApiRequest extends ApiRequestBase<AppLoginReq, AppLoginRsp> {
    private static final String URL_APP_LOGIN = CourierService.DOMAIN_API + "auth/login";

    public UserLoginTaskApiRequest(AppLoginReq req) {
        super(req, AppLoginRsp.class);
        mUrl = URL_APP_LOGIN;

        this.setMethod(Request.Method.POST);
    }
}
