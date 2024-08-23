package com.hf.courierservice.apihelper;

import static com.android.volley.Request.Method;

import android.content.Context;
import android.util.Log;

import com.android.volley.RequestQueue;
import com.android.volley.Response;
import com.android.volley.VolleyError;
import com.android.volley.toolbox.Volley;
import com.hf.courierservice.ResponseCallBack;
import com.hf.courierservice.apihelper.exception.UnAuthorizedException;

import java.net.HttpURLConnection;
import java.util.Map;

/**
 * This is a base class for all api request. To make a api request, you need to extends this class and set url, request and response class.
 * It a template for api request.
 * @param <RE>
 * @param <RS>
 */
public class ApiRequestBase<RE , RS> {
    private static final String TAG = "ApiRequestBase";

    protected String mUrl;
    private final RE mRequest;
    private final Class<RS> mRspClass;

    private Integer mMethod;
    private Map<String, String> mHeader;

    private static RequestQueue requestQueue;

    public ApiRequestBase(RE req , Class<RS> rspClass)
    {
        mRequest = req;
        mRspClass = rspClass;
    }

    public static void initRequestQueue(Context ctx)
    {
        if (requestQueue == null)
            requestQueue = Volley.newRequestQueue(ctx);
    }

    protected void setMethod(Integer mMethod) {
        this.mMethod = mMethod;
    }

    protected void setHeader(Map<String, String> header) {
        mHeader = header;
    }

    public void doApi(ResponseCallBack cb)
    {
        if (mMethod == null)
            mMethod = Method.GET;

        if (mUrl == null)
            return;

        commRequestWithRsp(mMethod , mUrl , mRequest , mRspClass , cb);
    }

    private  <T , RR > void commRequestWithRsp(int m , String url , T req , Class<RR> clazz ,ResponseCallBack cb)
    {
        // Request a string response from the provided URL.
        GeneticReq<T , RR> geneticReq = new GeneticReq<T , RR>
                (m , url , req ,  clazz , new Response.Listener<RR>() {
                    @Override
                    public void onResponse(RR response) {
                        try {
                           TaskBase taskBase = (TaskBase)response;
                           taskBase.doIt(cb);
                        } catch (Exception e) {
                            Log.e(TAG , e.getMessage());
                        }
                    };
                }, new Response.ErrorListener() {
                    @Override
                    public void onErrorResponse(VolleyError error) {
                        if (error.networkResponse != null && error.networkResponse.statusCode == HttpURLConnection.HTTP_UNAUTHORIZED) {
                            cb.onFail(new UnAuthorizedException());
                        }
                        else {
                            cb.onFail(error);
                        }
                    }
                });

        geneticReq.setHeader(mHeader);
        requestQueue.add(geneticReq);
    }
}
