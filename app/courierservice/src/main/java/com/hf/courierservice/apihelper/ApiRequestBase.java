package com.hf.courierservice.apihelper;

import static com.android.volley.Request.Method;

import android.content.Context;

import com.hf.courierservice.Result;
import com.hf.courierservice.apihelper.FileLog;

import com.android.volley.RequestQueue;
import com.android.volley.Response;
import com.android.volley.VolleyError;
import com.android.volley.toolbox.Volley;
import com.hf.courierservice.IResponseCallBack;
import com.hf.courierservice.apihelper.exception.RequestParamException;
import com.hf.courierservice.apihelper.exception.AlreadyScannedException;
import com.hf.courierservice.apihelper.exception.UnAuthorizedException;

import org.json.JSONObject;

import java.net.HttpURLConnection;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.function.Consumer;

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

    public void doApi(IResponseCallBack cb)
    {
        if (mMethod == null)
            mMethod = Method.GET;

        if (mUrl == null)
            return;

        commRequestWithRsp(mMethod , mUrl , mRequest , mRspClass , cb);
    }

    private  <T , RR > void commRequestWithRsp(int m , String url , T req , Class<RR> clazz , IResponseCallBack cb)
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
                            FileLog.e(TAG, "Exception in response handling", e);
                        }
                    };
                }, new Response.ErrorListener() {
                    @Override
                    public void onErrorResponse(VolleyError error) {
                        if (error.networkResponse != null && (error.networkResponse.statusCode == HttpURLConnection.HTTP_UNAUTHORIZED
                                || error.networkResponse.statusCode == HttpURLConnection.HTTP_FORBIDDEN
                                || 449 == error.networkResponse.statusCode)) {
                            int statusCode = error.networkResponse.statusCode;
                            String responseBody = extractResponseBody(error);
                            String bizCode = extractBizField(responseBody, "biz_code");
                            String bizMessage = extractBizField(responseBody, "biz_message");
                            FileLog.w(TAG, "Unauthorized-style response, http=" + statusCode + ", url=" + url
                                    + ", bizCode=" + bizCode + ", bizMessage=" + bizMessage);
                            if (statusCode == HttpURLConnection.HTTP_FORBIDDEN
                                    && "SCAN.ALREADY.SCANNED".equals(bizCode)) {
                                cb.onFail(new AlreadyScannedException(url, bizCode, bizMessage));
                                return;
                            }
                            cb.onFail(new UnAuthorizedException(statusCode, url));
                        }else if (error.networkResponse != null && error.networkResponse.statusCode == HttpURLConnection.HTTP_BAD_REQUEST)
                        {
                            cb.onFail(new RequestParamException());
                        }
                        else {
                            String httpCode = "";
                            if (error.networkResponse != null)
                                httpCode = String.valueOf(error.networkResponse.statusCode);
                            FileLog.e(TAG, "Exception in onErrorResponse, http status code is " + httpCode, error);
                            cb.onFail(error);
                        }
                    }
                });

        geneticReq.setHeader(mHeader);
        requestQueue.add(geneticReq);
    }

    private static String extractResponseBody(VolleyError error) {
        if (error == null || error.networkResponse == null || error.networkResponse.data == null) {
            return null;
        }
        try {
            return new String(error.networkResponse.data, StandardCharsets.UTF_8);
        } catch (Exception ignored) {
            return null;
        }
    }

    private static String extractBizField(String responseBody, String fieldName) {
        if (responseBody == null || responseBody.isEmpty()) {
            return null;
        }
        try {
            JSONObject object = new JSONObject(responseBody);
            if (object.has(fieldName) && !object.isNull(fieldName)) {
                return object.optString(fieldName, null);
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    /**
     * Static helper to call API and handle success/error with lambdas.
     */
    public static <REQ, RESP> void callApi(
            ApiRequestBase<REQ, RESP> api,
            Consumer<RESP> onSuccess,
            Consumer<Exception> onError
    ) {
        FileLog.d(TAG, "callApi: invoking " + api.mUrl);
        api.doApi(new IResponseCallBack<RESP>() {
            public void onComplete(Result<RESP> result) {
                if (result instanceof Result.Success) {
                    RESP data = ((Result.Success<RESP>) result).data;  // ✅ 拿到真正的 data
                    try {
                        onSuccess.accept(data);
                    } catch (Exception e) {
                        FileLog.e(TAG, "callApi: exception in success consumer for " + api.mUrl, e);
                        onError.accept(e);
                    }
                } else if (result instanceof Result.Error) {
                    Exception e = ((Result.Error<RESP>) result).exception;
                    FileLog.e(TAG, "callApi: Result.Error for " + api.mUrl, e);
                    onError.accept(e);
                }
            }

            @Override
            public void onFail(Exception e) {
                FileLog.e(TAG, "callApi: error for " + api.mUrl, e);
                onError.accept(e);
            }
        });
    }
}
