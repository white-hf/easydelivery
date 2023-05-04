package com.uniuni.SysMgrTool.common;

import com.android.volley.Response;
import com.android.volley.VolleyLog;
import com.google.gson.Gson;

import java.io.UnsupportedEncodingException;

public class GeneticReq<T , R> extends GsonRequest<R> {
    private final T mReq;
    public GeneticReq(int method, String url, T req,  Class<R> clazz, Response.Listener<R> listener,
                       Response.ErrorListener errorListener) {
        super(method, url, clazz , listener , errorListener);
        mReq = req;
    }

    public GeneticReq(String url, T req,  Class<R> clazz, Response.Listener<R> listener,
                      Response.ErrorListener errorListener) {
        super(Method.POST, url, clazz , listener , errorListener);
        mReq = req;
    }


    @Override
    public byte[] getBody() {
        String requestBody = null;
        try {
            if (mReq == null)
                return null;

            Gson mGson = new Gson();
            requestBody = mGson.toJson(mReq);
            return requestBody == null ? null : requestBody.getBytes(PROTOCOL_CHARSET);
        } catch (UnsupportedEncodingException uee) {
            VolleyLog.wtf(
                    "Unsupported Encoding while trying to get the bytes of %s using %s",
                    requestBody, PROTOCOL_CHARSET);
            return null;
        }
    }
}
