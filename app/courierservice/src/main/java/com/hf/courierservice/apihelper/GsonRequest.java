package com.hf.courierservice.apihelper;

import com.android.volley.Cache;
import com.android.volley.NetworkResponse;
import com.android.volley.ParseError;
import com.android.volley.Response;
import com.android.volley.toolbox.HttpHeaderParser;
import com.android.volley.toolbox.JsonRequest;
import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;
import com.hf.courierservice.apihelper.exception.ErrResponse;

import org.json.JSONObject;

import java.io.UnsupportedEncodingException;


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

    /**
     * Subclass has to override this method to parse the response to confirm if the request is successful and the
     * result has a valid json object, otherwise it will throw an exception.
     * @param jsonObj
     */
    protected void parseRequestResult(JSONObject jsonObj)
    {

    }

    @Override
    protected Response<T> parseNetworkResponse(NetworkResponse response) {
        try {
            String jsonString = new String(response.data,
                    HttpHeaderParser.parseCharset(response.headers));

            Cache.Entry entry = HttpHeaderParser.parseCacheHeaders(response);

            try {
                T r = mGson.fromJson(jsonString , mClass);
                return Response.success(r, entry);
            }catch (JsonSyntaxException e)
            {
                Exception je = new Exception(jsonString);
                ErrResponse er = new ErrResponse(je);
                return Response.error(er);
            }

        } catch (UnsupportedEncodingException e) {
            return Response.error(new ParseError(e));
        }
    }

    @Override
    protected void deliverResponse(T response) {
        mListener.onResponse(response);
    }
}