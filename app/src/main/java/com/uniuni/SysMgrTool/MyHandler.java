package com.uniuni.SysMgrTool;

import static com.uniuni.SysMgrTool.ServerInterface.RESPONSE_GET_ORDER_DETAIL;

import android.os.Looper;
import android.os.Message;

import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;

import com.uniuni.SysMgrTool.Response.LoginResponse;
import com.uniuni.SysMgrTool.Response.OrderDetailData;
import com.uniuni.SysMgrTool.Response.UpdateShippingStatusRsp;
import com.uniuni.SysMgrTool.View.OderDetailView;
import com.google.gson.Gson;
import com.uniuni.SysMgrTool.common.FileLog;
import com.uniuni.SysMgrTool.dao.ScannedRecord;

import android.os.Handler;
import android.util.Log;
import android.widget.Toast;

import java.util.concurrent.ConcurrentHashMap;

public class MyHandler extends Handler {
    public final static int MSG_LOADED_SCANNED_DATA = 201;
    public final static String ORDER_DIALOG = "orderdetail";

    private FragmentManager mFm;
    private ScannerActivity mScannerActivity;
    private OderDetailView mOderDetailView = new OderDetailView(null);

    private ConcurrentHashMap<Long , Object> mReqContext = new ConcurrentHashMap<Long , Object>();

    public MyHandler(Looper looper , FragmentManager fm)
    {
        super(looper);
        mFm = fm;
    }

    public void addReqContext(Long key , Object o)
    {
        mReqContext.put(key , o);
    }

    public Object getReqContext(Long k)
    {
        return mReqContext.get(k);
    }

    public void removeReqContext(Long k)
    {
        mReqContext.remove(k);
    }

    void setScannerActivity(ScannerActivity v)
    {
        mScannerActivity = v;
    }

    public void handleMessage(Message msg) {
        super.handleMessage(msg);

        if (msg.arg1 == LoginResponse.class.hashCode()) {
            LoginResponse r = (LoginResponse) msg.obj;
            if (r.getStatus().equalsIgnoreCase("success")) {
                MySingleton.getInstance().getServerInterface().gToken = r.getData().getToken();
                Log.d("debug", "token:" + MySingleton.getInstance().getServerInterface().gToken);

                Toast.makeText(MySingleton.getInstance().getCtx(), R.string.str_login_success, Toast.LENGTH_SHORT).show();
            } else
                Toast.makeText(MySingleton.getInstance().getCtx(), R.string.str_login_failure, Toast.LENGTH_SHORT).show();
        } else if (msg.arg1 == RESPONSE_GET_ORDER_DETAIL) {
            OrderDetailData d = (OrderDetailData) msg.obj;

            String orderText = MySingleton.getInstance().formatOrderDetailInfo(d);
            if (mScannerActivity != null && !mScannerActivity.isOperateModel())
            {
                mScannerActivity.setScanSummary(orderText);
                mScannerActivity.setScannedStatus(d.getOrders().getOrder_id() , d.getOrders().getOrder_sn());

            }else {
                if (mFm.isDestroyed())
                    return;

                Fragment f = mFm.findFragmentByTag(ORDER_DIALOG);
                if (f != null)
                {

                }
                else {
                    mOderDetailView.setmOrderDetailData(d);
                    mOderDetailView.setmMsg(orderText);
                    mOderDetailView.show(mFm, ORDER_DIALOG);
                }
            }
        }else if (msg.arg1 == UpdateShippingStatusRsp.class.hashCode())
        {
                UpdateShippingStatusRsp rsp = (UpdateShippingStatusRsp)msg.obj;
                if (rsp.isSuccess())
                {
                    MyHandler h = MySingleton.getInstance().getServerInterface().getMyHandler();
                    if (msg.arg2 < 1)
                        return;

                    Long key = Long.valueOf(msg.arg2);
                    ScannedRecord r = (ScannedRecord)h.getReqContext(key);
                    if (null == r)
                        return;

                    MyDb db = MySingleton.getInstance().getmMydb();

                    db.updateScannedData(r);
                    h.removeReqContext(key);

                    String strLog = String.format("update db by key:%d succeed" , key.intValue());
                    FileLog.getInstance().writeLog(strLog);
                }
                else
                {
                    Gson gson = new Gson();
                    FileLog.getInstance().writeLog(gson.toJson(rsp));
                }
        }
        else if (msg.arg1 == MSG_LOADED_SCANNED_DATA) {

        }
    }
}
