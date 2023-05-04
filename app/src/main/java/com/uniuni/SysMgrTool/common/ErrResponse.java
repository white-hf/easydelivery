package com.uniuni.SysMgrTool.common;

import com.android.volley.NetworkResponse;
import com.android.volley.ParseError;


public class ErrResponse extends ParseError {
    public int getErrCode() {
        return errCode;
    }

    public void setErrCode(int errCode) {
        this.errCode = errCode;
    }

    private int errCode;

    public ErrResponse() {}

    public ErrResponse(NetworkResponse networkResponse) {
        super(networkResponse);
    }

    public ErrResponse(Throwable cause) {
        super(cause);
    }
}