package com.uniuni.SysMgrTool;

import android.os.Handler;
import android.os.Message;
import android.util.Log;
import android.widget.Toast;

import androidx.annotation.IntegerRes;
import androidx.annotation.StringRes;

import com.android.volley.AuthFailureError;
import com.android.volley.Request;
import com.android.volley.Response;
import com.android.volley.VolleyError;
import com.android.volley.toolbox.JsonObjectRequest;
import com.android.volley.toolbox.RequestFuture;
import com.uniuni.SysMgrTool.common.ErrResponse;
import com.uniuni.SysMgrTool.common.ErrResponse;
import com.uniuni.SysMgrTool.common.FileLog;
import com.uniuni.SysMgrTool.common.GeneticReq;
import com.uniuni.SysMgrTool.common.GsonRequest;
import com.uniuni.SysMgrTool.Request.InsertOperationLogReq;
import com.uniuni.SysMgrTool.Request.LoginReq;
import com.uniuni.SysMgrTool.Request.TransferPackagesReq;
import com.uniuni.SysMgrTool.Request.UpdateShippingStatusReq;
import com.uniuni.SysMgrTool.Response.Data;
import com.uniuni.SysMgrTool.Response.LoginResponse;
import com.uniuni.SysMgrTool.Response.NewStorageInfoQueryResponse;
import com.uniuni.SysMgrTool.Response.OrderDetailData;
import com.uniuni.SysMgrTool.Response.OrderDetailResponse;
import com.uniuni.SysMgrTool.Response.Orders;
import com.uniuni.SysMgrTool.Response.OrdersOfDetail;
import com.uniuni.SysMgrTool.Response.OrdersResponse;
import com.uniuni.SysMgrTool.Response.ResponseBase;
import com.uniuni.SysMgrTool.Response.StorageData;
import com.uniuni.SysMgrTool.Response.TransferPackagesRsp;
import com.uniuni.SysMgrTool.Response.TransitionData;
import com.uniuni.SysMgrTool.Response.UpdateShippingStatusRsp;
import com.uniuni.SysMgrTool.bean.ScanOrder;
import com.google.gson.Gson;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

public class ServerInterface {

    public static final int RESPONSE_GET_ORDER_DETAIL = 101;
    public static final int RESPONSE_GET_ORDER_LIST   = 102;

    public static final int ERR_ORDER_NOT_EXIST = 20002;

    public static String gToken = "";

    private static final String DOMAIN_STRING = "https://map.cluster.uniexpress.org/";
    private static final String URL_GET_ORDER_BY_DRIVER = DOMAIN_STRING
            + "map/getdriverordersinbatchlist?driver_id=%d&batch_list=%s&hide_associated=1&hide_sub_referrer=0&branch=17";

    private static final String URL_GET_ORDER_DETAIL = DOMAIN_STRING
            + "map/getorderdetail?tno=%s";

    public static final String URL_UPDATE_SHIPPING_STATUS = DOMAIN_STRING + "driver/updateshippingstatus";
    public static final String URL_INSERT_OPERATION_LOG   = DOMAIN_STRING + "driver/insertoperationlog";
    public static final String URL_CHANGE_STATE           = DOMAIN_STRING + "driver/restoretostate?tno=%s&state=%d&warehouse=%d&version=1";
    public static final String URL_QUERY_TRANSITION       = DOMAIN_STRING + "map/getnexttransition?current_state=%d";
    public static final String URL_QUERY_NEW_STORAGE_ID   = DOMAIN_STRING + "business/getnewstorageinfo?warehouse=17";

    public static final String URL_TRANSFER_PACKAGES      = DOMAIN_STRING + "cargo/transferpackagesinrange";

    private static final String URL_LOGIN   = DOMAIN_STRING + "map/login";

    private MyHandler myHandler;

    /**
     * FAILED_DELIVERY_RETRY1(231)-->FAILED_DELIVERY_RETRY2(232)-->RETURN_OFFICE_FROM_TRANSIT(211)-->STORAGE_30_DAYS_FROM_OFFICE(213)
     * Another:WRONG_ADDRESS_FROM_TRANSIT(206)-->STORAGE_30_DAYS_FROM_OFFICE(213)
     * Another:211 - 213
     */

    int STATUS_ARRAY_FAILED_DELIVERY_RETRY[] = {211 , 213};
    int DIRECT_TO_STORAGE_30_DAYS_FROM_OFFICE[] = {213};

    private HashMap<Integer , TransitionData> mHashMapTransition =  new HashMap<>();

    public enum ParcelStatus{
        GATEWAY_TRANSIT_OUT(195),
        GATEWAY_TRANSIT(199),
        IN_TRANSIT(202),
        WRONG_ADDRESS_FROM_TRANSIT(206),
        RETURN_OFFICE_FROM_TRANSIT(211),
        FAILED_DELIVERY_RETRY1(231),
        FAILED_DELIVERY_RETRY2(232),
        STORAGE_30_DAYS_FROM_OFFICE(213);

        private ParcelStatus(int v)
        {
            value = v;
        }
        public int getValue() {
            return value;
        }

        private int value = 0;

    }

    ServerInterface()
    {
        initStatusData();
    }

    public MyHandler getMyHandler()
    {
        return myHandler;
    }

    public ServerInterface(MyHandler h)
    {
        myHandler = h;

        initStatusData();
    }

    private boolean checkString(String s)
    {
        if (s == null || s.isEmpty())
            return false;
        else
            return true;
    }

    private void initStatusData()
    {
        TransitionData d = new TransitionData();
        d.setTransition("SEND_PARCEL_TO_STORAGE");
        d.setTo_code(ParcelStatus.STORAGE_30_DAYS_FROM_OFFICE.getValue());
        d.setTo_state("STORAGE_30_DAYS_FROM_OFFICE");
        d.setNext_shipping_status(2);

        d.setFrom_code(ParcelStatus.RETURN_OFFICE_FROM_TRANSIT.getValue());

        mHashMapTransition.put(ParcelStatus.STORAGE_30_DAYS_FROM_OFFICE.getValue() ,d);

        TransitionData dDELIVER_PARCEL_APT = new TransitionData();
        dDELIVER_PARCEL_APT.setTransition("DELIVER_PARCEL_APT");
        dDELIVER_PARCEL_APT.setTo_code(ParcelStatus.RETURN_OFFICE_FROM_TRANSIT.getValue());
        dDELIVER_PARCEL_APT.setTo_state("RETURN_OFFICE_FROM_TRANSIT");
        dDELIVER_PARCEL_APT.setNext_shipping_status(1);

        dDELIVER_PARCEL_APT.setFrom_code(ParcelStatus.FAILED_DELIVERY_RETRY2.getValue());

        mHashMapTransition.put(ParcelStatus.RETURN_OFFICE_FROM_TRANSIT.getValue(), dDELIVER_PARCEL_APT);

        TransitionData dFAILED_DELIVERY_RETRY2 = new TransitionData();
        dFAILED_DELIVERY_RETRY2.setTransition("FAILED_DELIVERY_RETRY2");
        dFAILED_DELIVERY_RETRY2.setTo_code(ParcelStatus.FAILED_DELIVERY_RETRY2.getValue());
        dFAILED_DELIVERY_RETRY2.setTo_state("FAILED_DELIVERY_RETRY2");
        dFAILED_DELIVERY_RETRY2.setNext_shipping_status(1);

        dFAILED_DELIVERY_RETRY2.setFrom_code(ParcelStatus.FAILED_DELIVERY_RETRY1.getValue());

        mHashMapTransition.put(ParcelStatus.FAILED_DELIVERY_RETRY2.getValue(), dFAILED_DELIVERY_RETRY2);
    }

    private final String getString(@StringRes int resId)
    {
        return MySingleton.getInstance().getCtx().getString(resId);
    }

    private final int getInteger(@IntegerRes int resId)
    {
        return MySingleton.getInstance().getCtx().getResources().getInteger(resId);
    }

    /**
     * 1、查询订单详情，获取latest_status
     * 2、使用latest_status查询https://map.cluster.uniexpress.org/map/getnexttransition?current_state=211
     * 3、从2中结果选择合适的状态修改，一直到入库状态为止。
     * 4、仓库号https://map.cluster.uniexpress.org/business/getnewstorageinfo?warehouse=17返回的。
     */
    public String putinStorage(String tid) {
        /**
         * FAILED_DELIVERY_RETRY1(231)-->FAILED_DELIVERY_RETRY2(232)-->RETURN_OFFICE_FROM_TRANSIT(211)-->STORAGE_30_DAYS_FROM_OFFICE(213)
         * Another:WRONG_ADDRESS_FROM_TRANSIT(206)-->STORAGE_30_DAYS_FROM_OFFICE(213)
         * Another:211 - 213
         */

        String realUrl = String.format(URL_GET_ORDER_DETAIL , tid);
        OrderDetailResponse rsp = synPostRequestWithRsp(Request.Method.GET , realUrl, null , OrderDetailResponse.class);
        if (rsp == null || rsp.getData() == null)
            return null;

        return putinStorage(rsp.getData());
    }

    public void setParcelState(String tid, int state)
    {
        if (!checkString(tid))
            return;

        String realUrl = String.format(URL_CHANGE_STATE , tid , state , MySingleton.getInstance().getLoginInfo().warehouseId);
        postRequest(null , realUrl , true);

        InsertOperationLogReq logReq = new InsertOperationLogReq();
        logReq.setOperator(MySingleton.getInstance().getLoginInfo().loginName);
        logReq.setOperation_code(state);
        logReq.setOperation_type(getInteger(R.integer.default_operation_type));
        logReq.setDescription("");
        logReq.setMemo("");

        insertOperationLog(logReq);
    }

    public String putinStorage(OrderDetailData data)
    {
        //set shipping status to being in storage
        StorageData newStorageInfo = getNewStorageInfo();
        if (newStorageInfo == null)
            return null;

        Log.d("debug", "newStorage Info" + newStorageInfo.getStorage_info());

        int s = data.getOrders().getLatest_status();
        if(s == ParcelStatus.WRONG_ADDRESS_FROM_TRANSIT.getValue() ||
                s == ParcelStatus.RETURN_OFFICE_FROM_TRANSIT.getValue())
        {
            int nextStatus = ParcelStatus.STORAGE_30_DAYS_FROM_OFFICE.getValue();
            TransitionData transitionData = mHashMapTransition.get(nextStatus);
            if (transitionData == null)
                return null;

            if (!updateShippingStatusToX(data.getOrders() , nextStatus , newStorageInfo))
                return null;

        } else if (s == ParcelStatus.FAILED_DELIVERY_RETRY1.getValue() ||
        s == ParcelStatus.FAILED_DELIVERY_RETRY2.getValue()) {
            for(int index : STATUS_ARRAY_FAILED_DELIVERY_RETRY)
            {
                TransitionData transitionData = mHashMapTransition.get(index);
                if (transitionData == null)
                    return null;

                if (!updateShippingStatusToX(data.getOrders() , transitionData.getTo_code()  , newStorageInfo))
                    return null;
            }
        } else
            return null;

        return newStorageInfo.getStorage_info();
    }

    private boolean updateShippingStatusToX(OrdersOfDetail order , int status , StorageData storageData)
    {
        TransitionData d = mHashMapTransition.get(status);
        if (d == null)
            return false;

        UpdateShippingStatusReq req = new UpdateShippingStatusReq();
        req.setOrder_id(order.getOrder_id());
        req.setStaff_id(order.getShipping_staff_id());
        req.setShipping_status(d.getNext_shipping_status());
        req.setScan_location(getString(R.string.default_scanned_location));
        req.setStoraged_warehouse(getInteger(R.integer.default_warehouse_id));
        req.setSend_sms(0);
        req.setException("");

        req.getParcel_info().setOrder_id(req.getOrder_id());
        req.getParcel_info().setExtra_order_sn(order.getOrder_sn());
        req.getParcel_info().setTransition(d.getTransition());
        req.getParcel_info().setStatus(status);

        req.getParcel_info().setStorage_info(storageData.getStorage_info());
        req.getParcel_info().setStorage_code(storageData.getStorage_code());
        req.getParcel_info().setStorage_rotation(storageData.getStorage_rotation());

        req.getParcel_info().setDesc("");
        req.getParcel_info().setWarehouse(getInteger(R.integer.default_warehouse_id));

        UpdateShippingStatusRsp rsp = synPostRequestWithRsp(Request.Method.POST , URL_UPDATE_SHIPPING_STATUS, req , UpdateShippingStatusRsp.class);
        if (rsp == null || rsp.getData() == null || !rsp.isSuccess())
            return false;

        InsertOperationLogReq logReq = new InsertOperationLogReq();
        logReq.setOrder_id(order.getOrder_id());
        logReq.setOperator(MySingleton.getInstance().getLoginInfo().loginName);
        logReq.setOperation_code(status);
        logReq.setOperation_type(getInteger(R.integer.default_operation_type));
        logReq.setDescription("");
        logReq.setMemo("");

        MySingleton.getInstance().getServerInterface().insertOperationLog(logReq);

        return true;
    }

    private StorageData getNewStorageInfo()
    {
        NewStorageInfoQueryResponse rsp = synPostRequestWithRsp(Request.Method.GET , URL_QUERY_NEW_STORAGE_ID , null, NewStorageInfoQueryResponse.class);
        if (rsp == null || rsp.getData() == null)
            return null;

        return rsp.getData();
    }

    public boolean transferPackages(TransferPackagesReq req)
    {
        if (!checkString(req.getSub_batch()) || !checkString(req.getReceiver())
        || !checkString(req.getRoute_no())
        ||!checkString(req.getStart())
        || !checkString(req.getEnd()))
            return false;

        String strLog = String.format("transferPackages from %s to %s" , req.getRoute_no() , req.getReceiver());
        FileLog.getInstance().writeLog(strLog);

        postRequestWithRsp( 0 ,URL_TRANSFER_PACKAGES , req , TransferPackagesRsp.class , null);

        return true;
    }

    public  void handleScanParcel(Long orderId , String orderSn , boolean bToast)
    {
        UpdateShippingStatusReq req = new UpdateShippingStatusReq();
        req.setOrder_id(orderId);
        req.setStaff_id(666);
        req.setShipping_status(0);
        req.setScan_location(MySingleton.getInstance().getLoginInfo().loginLocation);
        req.setStoraged_warehouse(MySingleton.getInstance().getLoginInfo().warehouseId);
        req.setSend_sms(0);
        req.setException("");

        req.getParcel_info().setOrder_id(orderId);
        req.getParcel_info().setExtra_order_sn(orderSn);
        req.getParcel_info().setTransition(getString(R.string.str_parcel_scanned));
        req.getParcel_info().setStatus(getInteger(R.integer.status_parcel_scanned));
        req.getParcel_info().setDesc("");
        req.getParcel_info().setWarehouse(MySingleton.getInstance().getLoginInfo().warehouseId);

        updatesShippingStatus(req , bToast);

        InsertOperationLogReq logReq = new InsertOperationLogReq();
        logReq.setOrder_id(orderId);
        logReq.setOperator(MySingleton.getInstance().getLoginInfo().loginName);
        logReq.setOperation_code(getInteger(R.integer.status_parcel_scanned));
        logReq.setOperation_type(getInteger(R.integer.default_operation_type));
        logReq.setDescription("");
        logReq.setMemo("");

        insertOperationLog(logReq);
    }

    public void login(String name , String password)
    {
        if (name == null || name.isEmpty() ||  password == null || password.isEmpty())
            return;

        String realUrl = String.format("%s?username=%s" , URL_LOGIN, name);

        LoginReq req = new LoginReq();

        FileLog.getInstance().writeLog(realUrl);

        postRequestWithRsp(0 , realUrl , req , LoginResponse.class , myHandler);
    }

    public  void insertOperationLog(InsertOperationLogReq req)
    {
        if (req == null || req.getOrder_id() == null ||
                req.getOperator() == null ||
                req.getOperation_code() == null ||
                req.getOperation_type() == null) {
            return;
        }

        String json  = new Gson().toJson(req);
        JSONObject o ;
        try {
            o = new JSONObject(json);
        } catch (JSONException e) {
            e.printStackTrace();
            return;
        }

        postRequest(o , URL_INSERT_OPERATION_LOG , false);

    }

    public void updatesShippingStatus(UpdateShippingStatusReq req , boolean bToast)
    {
        if (req == null || req.getOrder_id() == null ||
        req.getShipping_status() == null ||
        req.getParcel_info() == null ||
        req.getStaff_id() == null)
            return;

        postRequestWithRsp(req.getOrder_id().intValue(),URL_UPDATE_SHIPPING_STATUS, req , UpdateShippingStatusRsp.class,myHandler);

        /**

        JSONObject o ;
        try {
            o = new JSONObject(json);
        } catch (JSONException e) {
            e.printStackTrace();
            return;
        }

         postRequest(o , URL_UPDATE_SHIPPING_STATUS , bToast);
        **/

        String json  = new Gson().toJson(req);
        FileLog.getInstance().writeLog(String.format("updatesShippingStatus : %s" , json));
    }

    private  <T , R extends ResponseBase> R synPostRequestWithRsp(int m , String url , T req , Class<R> clazz)
    {
        RequestFuture<R> future = RequestFuture.newFuture();

        // Request a string response from the provided URL.
        GeneticReq<T , R> geneticReq = new GeneticReq<T , R>(m , url , req ,  clazz , future , future);
        future.setRequest(MySingleton.getInstance().addToRequestQueue(geneticReq));

        try {
            R result = future.get(60 , TimeUnit.SECONDS);
            //future.get(timeout, unit)
            return result;

        } catch (InterruptedException e) {
            e.printStackTrace();
        } catch (ExecutionException e) {
            e.printStackTrace();
        } catch (TimeoutException e) {
            e.printStackTrace();
            Toast.makeText(MySingleton.getInstance().getCtx(),"请求超时",Toast.LENGTH_SHORT);
        }catch (Exception e)
        {
            Toast.makeText(MySingleton.getInstance().getCtx(),e.getMessage(),Toast.LENGTH_SHORT);
        }

        boolean b = future.isCancelled();

        return null;
    }

    private  <T , R extends ResponseBase> void postRequestWithRsp(int key , String url , T req , Class<R> clazz  , Handler h)
    {
        // Request a string response from the provided URL.
        GeneticReq<T , R> geneticReq = new GeneticReq<T , R>
                (url , req ,  clazz , new Response.Listener<R>() {
                    @Override
                    public void onResponse(R response) {
                        try {
                            if (h != null) {
                                Message m = Message.obtain();
                                m.what = clazz.hashCode();
                                m.arg1 = clazz.hashCode();
                                //Thread t = Thread.currentThread();
                                m.arg2   = key;
                                m.obj = response;

                                h.sendMessage(m);
                            }
                } catch (Exception e) {
                            e.printStackTrace();
                            Toast.makeText(MySingleton.getInstance().getCtx(),  com.uniuni.SysMgrTool.R.string.action_req_failure , Toast.LENGTH_SHORT).show();
                        }
                    };
                }, new Response.ErrorListener() {
                    @Override
                    public void onErrorResponse(VolleyError error) {
                        if (error.networkResponse != null && error.networkResponse.statusCode == 403)
                            Toast.makeText(MySingleton.getInstance().getCtx() , com.uniuni.SysMgrTool.R.string.action_need_login , Toast.LENGTH_SHORT).show();
                        else {
                            Toast.makeText(MySingleton.getInstance().getCtx(), com.uniuni.SysMgrTool.R.string.action_req_failure, Toast.LENGTH_SHORT).show();
                            FileLog.getInstance().writeLog("error:" + url + " " + Log.getStackTraceString(error));
                        }
                    }
                });

        MySingleton.getInstance().addToRequestQueue(geneticReq);
    }

    private void postRequest(JSONObject reqObj, String url , boolean bToast)
    {
        Log.d("debug", "Real url : " + url);

        // Request a string response from the provided URL.
        JsonObjectRequest orderRequest = new JsonObjectRequest
                (Request.Method.POST, url , reqObj, new Response.Listener<JSONObject>() {
                    @Override
                    public void onResponse(JSONObject response) {
                        try {
                            if (response.getString("status").equalsIgnoreCase("SUCCESS")) {
                                if (bToast) Toast.makeText(MySingleton.getInstance().getCtx(), R.string.action_success, Toast.LENGTH_SHORT).show();
                            }else {
                                Toast.makeText(MySingleton.getInstance().getCtx(), R.string.action_failure, Toast.LENGTH_SHORT).show();
                                Toast.makeText(MySingleton.getInstance().getCtx(), response.toString(), Toast.LENGTH_SHORT).show();

                                FileLog.getInstance().writeLog(response.toString());

                                Log.d("debug", response.toString());
                            }
                        } catch (Exception e) {
                            e.printStackTrace();
                            Toast.makeText(MySingleton.getInstance().getCtx(), R.string.action_req_failure, Toast.LENGTH_SHORT).show();
                        }
                    };
                }, new Response.ErrorListener() {
                    @Override
                    public void onErrorResponse(VolleyError error) {
                        // TODO: Handle error
                        if (error.networkResponse != null && error.networkResponse.statusCode == 403)
                            Toast.makeText(MySingleton.getInstance().getCtx() ,R.string.action_need_login , Toast.LENGTH_SHORT).show();
                        else {
                            Toast.makeText(MySingleton.getInstance().getCtx(), R.string.action_req_failure, Toast.LENGTH_SHORT).show();
                            //Toast.makeText(MySingleton.getInstance().getCtx(), error.getMessage() , Toast.LENGTH_SHORT).show();
                            FileLog.getInstance().writeLog("error:" + url + " " + Log.getStackTraceString(error));
                        }
                    }
                })
        {
            @Override
            public Map<String, String> getHeaders() throws AuthFailureError
            {
                Map<String, String> headers = new HashMap<String, String>();
                headers.put("authorization", "Bearer" + " " + MySingleton.getInstance().getServerInterface().gToken);
                return headers;
            }
        };

        MySingleton.getInstance().addToRequestQueue(orderRequest);
    }

    public Boolean getOrderDetail(String tid  , Handler h)
    {
        if (tid == null || tid.isEmpty())
            return Boolean.FALSE;

        String realUrl = String.format(URL_GET_ORDER_DETAIL , tid);

        Log.d("debug", "Real url : " + realUrl);

        // Request a string response from the provided URL.
        GsonRequest<OrderDetailResponse> orderRequest = new GsonRequest<OrderDetailResponse>
                (Request.Method.GET, realUrl , OrderDetailResponse.class, new Response.Listener<OrderDetailResponse>() {
                    @Override
                    public void onResponse(OrderDetailResponse response) {
                        try {
                            if (response.getStatus().equalsIgnoreCase("SUCCESS")) {

                                OrderDetailData d = response.getData();
                                if (d != null)
                                {
                                    Log.d("debug", String.format("Order: %s" , d.getOrders().getOrder_id()));

                                    if (h != null) {
                                        Message m = Message.obtain();
                                        m.what = RESPONSE_GET_ORDER_DETAIL;
                                        m.arg1 = RESPONSE_GET_ORDER_DETAIL;
                                        m.obj = d;

                                        h.sendMessage(m);
                                    }

                                    return;
                                }
                            }else {
                                if (response.getErr_code() == ERR_ORDER_NOT_EXIST) {
                                    Toast.makeText(MySingleton.getInstance().getCtx(), "订单不存在", Toast.LENGTH_SHORT).show();
                                } else {
                                    Toast.makeText(MySingleton.getInstance().getCtx(), "查询订单详情失败", Toast.LENGTH_SHORT).show();
                                    FileLog.getInstance().writeLog(response.getRet_msg());
                                }
                            }
                        } catch (Exception e) {
                            e.printStackTrace();
                            Toast.makeText(MySingleton.getInstance().getCtx(), "请求服务错误", Toast.LENGTH_SHORT).show();
                        }
                    };
                }, new Response.ErrorListener() {
                    @Override
                    public void onErrorResponse(VolleyError error) {
                        // TODO: Handle error
                        if (error.networkResponse != null && error.networkResponse.statusCode == 403)
                            Toast.makeText(MySingleton.getInstance().getCtx() ,R.string.action_need_login , Toast.LENGTH_SHORT).show();
                        else if (error.networkResponse != null && error.networkResponse.data != null) {
                            ErrResponse rs = (ErrResponse) error;
                            if (rs.getErrCode() == ERR_ORDER_NOT_EXIST)
                                Toast.makeText(MySingleton.getInstance().getCtx(), "订单不存在", Toast.LENGTH_SHORT).show();
                            else
                                Toast.makeText(MySingleton.getInstance().getCtx() ,R.string.action_svr_failure , Toast.LENGTH_SHORT).show();
                        }
                        else
                            Toast.makeText(MySingleton.getInstance().getCtx() ,R.string.action_req_failure , Toast.LENGTH_SHORT).show();

                    }
                });

        MySingleton.getInstance().addToRequestQueue(orderRequest);
        return Boolean.TRUE;
    }


    public void loadOrdersByDriver(String batchId, Integer driverId,Handler h)
    {
        String realUrl = String.format(URL_GET_ORDER_BY_DRIVER , driverId , batchId);

        Log.d("debug", "Real url : " + realUrl);

        // Request a string response from the provided URL.
        GsonRequest<OrdersResponse> orderRequest = new GsonRequest<OrdersResponse>
                (Request.Method.GET, realUrl , OrdersResponse.class, new Response.Listener<OrdersResponse>() {
                    @Override
                    public void onResponse(OrdersResponse response) {
                        try {
                            if (response.getStatus().equalsIgnoreCase("SUCCESS")) {
                                Data d = response.getData();
                                if (d != null)
                                {
                                    int c = 0;
                                    for (Orders o: d.getOrders()) {
                                        //保存司机的订单数据
                                        ScanOrder scanOrder = new ScanOrder();
                                        scanOrder.setId(o.getTno());
                                        scanOrder.setPackId(Short.valueOf((short) o.getPack_id()));
                                        scanOrder.setZipCode(o.getZipcode());
                                        scanOrder.setDriverId(driverId);

                                        scanOrder.setOrderId(o.getOrder_id());
                                        scanOrder.setOrderSn(o.getOrder_sn());
                                        scanOrder.setLat(o.getLat());
                                        scanOrder.setLng(o.getLng());
                                        scanOrder.setLastStatus(o.getPath_code());

                                        MySingleton.getInstance().addScanOrder(scanOrder.getId(),scanOrder);
                                        c++;
                                    }

                                    if (h != null) {
                                        Message m = Message.obtain();
                                        m.arg1 = RESPONSE_GET_ORDER_LIST;
                                        m.what = RESPONSE_GET_ORDER_LIST;
                                        m.obj = d;

                                        h.sendMessage(m);
                                    }

                                    Log.d("debug", String.format("Load %d orders" , c));
                                    Toast.makeText(MySingleton.getInstance().getCtx(), String.format("加载数据条数:%d" , c), Toast.LENGTH_LONG).show();
                                }
                            }
                            else
                                Toast.makeText(MySingleton.getInstance().getCtx(), "查询订单详情失败", Toast.LENGTH_SHORT).show();
                        } catch (Exception e) {
                            e.printStackTrace();
                            Toast.makeText(MySingleton.getInstance().getCtx(), R.string.action_req_failure, Toast.LENGTH_SHORT).show();
                        }
                    };
                }, new Response.ErrorListener() {
                    @Override
                    public void onErrorResponse(VolleyError error) {
                        if (error.networkResponse != null && error.networkResponse.statusCode == 403)
                            Toast.makeText(MySingleton.getInstance().getCtx() ,R.string.action_need_login , Toast.LENGTH_SHORT).show();
                        else
                            Toast.makeText(MySingleton.getInstance().getCtx() ,R.string.action_req_failure , Toast.LENGTH_SHORT).show();
                    }
                });

        MySingleton.getInstance().addToRequestQueue(orderRequest);
    }
}
