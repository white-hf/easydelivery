package com.uniuni.SysMgrTool.common;

import com.android.volley.AuthFailureError;
import com.android.volley.Cache;
import com.android.volley.NetworkResponse;
import com.android.volley.ParseError;
import com.android.volley.Response;
import com.android.volley.toolbox.HttpHeaderParser;
import com.android.volley.toolbox.JsonRequest;
import com.uniuni.SysMgrTool.MySingleton;
import com.google.gson.Gson;

import org.json.JSONObject;

import java.io.UnsupportedEncodingException;
import java.util.HashMap;
import java.util.Map;

public class GsonRequest<T> extends JsonRequest<T> {

    private final Response.Listener<T> mListener;

    private final Gson mGson;

    private Class<T> mClass;

    public GsonRequest(int method, String url, Class<T> clazz, Response.Listener<T> listener,
                       Response.ErrorListener errorListener) {
        super(method, url, null , listener , errorListener);
        mGson = new Gson();
        mClass = clazz;
        mListener = listener;
    }

    public GsonRequest(String url, Class<T> clazz, Response.Listener<T> listener,
                       Response.ErrorListener errorListener) {
        this(Method.GET, url, clazz, listener, errorListener);
    }

    @Override
    protected Response<T> parseNetworkResponse(NetworkResponse response) {
        try {
            String jsonString = new String(response.data,
                    HttpHeaderParser.parseCharset(response.headers));

            try {
                JSONObject jsonObj = new JSONObject(jsonString);
                if (jsonObj.has("status") && !jsonObj.getString("status").equalsIgnoreCase("SUCCESS"))
                {
                    Exception e = new Exception(jsonObj.getString("ret_msg"));

                    ErrResponse er = new ErrResponse(e);
                    er.setErrCode(jsonObj.getInt("err_code"));
                    return Response.error(er);
                }

                if (jsonObj.has("biz_code") && !jsonObj.getString("biz_code").equalsIgnoreCase("COMMON.QUERY.SUCCESS"))
                {
                    Exception e = new Exception(jsonObj.getString("biz_message"));

                    ErrResponse er = new ErrResponse(e);
                    return Response.error(er);
                }

            }catch (Exception e)
            {
                return Response.error(new ParseError(e));
            }

            T r = mGson.fromJson(jsonString , mClass);
            Cache.Entry entry = HttpHeaderParser.parseCacheHeaders(response);

            return Response.success(r, entry);
        } catch (UnsupportedEncodingException e) {
            return Response.error(new ParseError(e));
        }
    }

    @Override
    protected void deliverResponse(T response) {
        mListener.onResponse(response);
    }

    @Override
    public Map<String, String> getHeaders() throws AuthFailureError
    {
        Map<String, String> headers = new HashMap<String, String>();
        headers.put("authorization", "Bearer" + " " + MySingleton.getInstance().getServerInterface().gToken);
        return headers;
    }
}