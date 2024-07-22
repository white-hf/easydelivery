package com.uniuni.SysMgrTool.common;

import static com.android.volley.Request.Method;

import android.os.Handler;
import android.os.Message;
import android.util.Log;
import android.widget.Toast;

import com.android.volley.RequestQueue;
import com.android.volley.Response;
import com.android.volley.VolleyError;
import com.android.volley.toolbox.Volley;
import com.uniuni.SysMgrTool.MySingleton;

import org.jetbrains.annotations.NotNull;

import java.net.HttpURLConnection;

/**
 * This is a base class for all api request. To make a api request, you need to extends this class and set url, request and response class.
 * It a template for api request.
 * @param <RE>
 * @param <RS>
 */
public class ServerApiBase<RE , RS> {
    protected String mUrl;
    private final RE mRequest;
    private final Class<RS> mRspClass;

    private Integer mMethod;
    private final Handler mHandler = MySingleton.getInstance().getMainHandler();

    private static final RequestQueue requestQueue = Volley.newRequestQueue(MySingleton.getInstance().getCtx().getApplicationContext());;

    public ServerApiBase(RE req , Class<RS> rspClass)
    {
        mRequest = req;
        mRspClass = rspClass;
    }

    public void setMethod(Integer mMethod) {
        this.mMethod = mMethod;
    }

    public void doApi()
    {
        if (mMethod == null)
            mMethod = Method.GET;

        if (mUrl == null)
            return;

        commRequestWithRsp(mMethod , mUrl , mRequest , mRspClass , mHandler);
    }

    private  <T , RR > void commRequestWithRsp(int m , String url , T req , Class<RR> clazz  , @NotNull Handler h)
    {
        // Request a string response from the provided URL.
        GeneticReq<T , RR> geneticReq = new GeneticReq<T , RR>
                (m , url , req ,  clazz , new Response.Listener<RR>() {
                    @Override
                    public void onResponse(RR response) {
                        try {
                                Message m = Message.obtain();
                                m.what = clazz.hashCode();
                                m.obj = response;
                                h.sendMessage(m);
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                    };
                }, new Response.ErrorListener() {
                    @Override
                    public void onErrorResponse(VolleyError error) {
                        if (error.networkResponse != null && error.networkResponse.statusCode == HttpURLConnection.HTTP_UNAUTHORIZED)
                            Toast.makeText(MySingleton.getInstance().getCtx() , com.uniuni.SysMgrTool.R.string.action_need_login , Toast.LENGTH_SHORT).show();
                        else {
                            Toast.makeText(MySingleton.getInstance().getCtx(), com.uniuni.SysMgrTool.R.string.action_req_failure, Toast.LENGTH_SHORT).show();
                            FileLog.getInstance().writeLog("error:" + url + " " + Log.getStackTraceString(error));
                        }
                    }
                });

        requestQueue.add(geneticReq);
    }
}
