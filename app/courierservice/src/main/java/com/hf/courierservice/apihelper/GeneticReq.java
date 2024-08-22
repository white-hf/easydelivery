package com.hf.courierservice.apihelper;

import com.android.volley.AuthFailureError;
import com.android.volley.Response;
import com.android.volley.VolleyLog;
import com.android.volley.toolbox.JsonRequest;
import com.google.gson.Gson;

import java.io.UnsupportedEncodingException;
import java.util.HashMap;
import java.util.Map;

public class GeneticReq<T , R> extends GsonRequest<R> {
    private final T mReq;
    private Map<String, String> mHeader;

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

    public void setHeader(Map<String, String> mHeader) {
        this.mHeader = mHeader;
    }

    @Override
    public byte[] getBody() {
        String requestBody = null;
        try {
            if (mReq == null)
                return null;

            Gson mGson = new Gson();
            requestBody = mGson.toJson(mReq);
            return requestBody == null ? null : requestBody.getBytes(JsonRequest.PROTOCOL_CHARSET);
        } catch (UnsupportedEncodingException uee) {
            VolleyLog.wtf(
                    "Unsupported Encoding while trying to get the bytes of %s using %s",
                    requestBody, JsonRequest.PROTOCOL_CHARSET);
            return null;
        }
    }

    @Override
    public Map<String, String> getHeaders() throws AuthFailureError
    {
        if (mHeader != null)
            return mHeader;
        else
            return new HashMap<String, String>();
    }
}
