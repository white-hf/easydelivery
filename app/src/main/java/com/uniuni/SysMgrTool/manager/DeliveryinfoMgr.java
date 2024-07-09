package com.uniuni.SysMgrTool.manager;

import static com.uniuni.SysMgrTool.MySingleton.TAG;
import static com.uniuni.SysMgrTool.ServerInterface.DOMAIN_API;

import android.annotation.SuppressLint;
import android.util.Log;

import com.uniuni.SysMgrTool.Event.Event;
import com.uniuni.SysMgrTool.Event.EventConstant;
import com.uniuni.SysMgrTool.Event.Subscriber;
import com.uniuni.SysMgrTool.MySingleton;
import com.uniuni.SysMgrTool.Request.GetDeliveryTaskApi;
import com.uniuni.SysMgrTool.Response.AppRsp;
import com.uniuni.SysMgrTool.Response.DeliveringListData;
import com.uniuni.SysMgrTool.ServerInterface;
import com.uniuni.SysMgrTool.common.ResponseCallBack;
import com.uniuni.SysMgrTool.common.Result;
import com.uniuni.SysMgrTool.dao.DeliveryInfo;
import com.uniuni.SysMgrTool.dao.DeliveryInfoDao;
import com.uniuni.SysMgrTool.dao.PackageEntity;

import java.util.ArrayList;
import java.util.List;

/**
 * This class manages the delivery info,including getting the delivery info from the server,
 * saving the delivery info to the database, and loading the delivery info from the database.
 */
public class DeliveryinfoMgr implements Subscriber {

    private static final String URL_DELIVERING_LIST = DOMAIN_API + "delivery/parcels/delivering?driver_id=%d";

    String batchId;

    public ArrayList<DeliveryInfo> getListDeliveryInfo() {
        return listDeliveryInfo;
    }

    public int size() {
        return listDeliveryInfo.size();
    }

    private ArrayList<DeliveryInfo> listDeliveryInfo;

    public DeliveryinfoMgr() {
        batchId = MySingleton.getInstance().getProperty(MySingleton.ITEM_CURRENT_BATCH_ID);

        MySingleton.getInstance().getPublisher().subscribe(EventConstant.EVENT_LOGIN , this);
        listDeliveryInfo = new ArrayList<>();
    }

    public final DeliveryInfo get(Long orderId) {
        if (orderId == null)
            return null;

        return listDeliveryInfo.stream().filter(pkg->pkg.getOrderId().equals(orderId)).findFirst().orElse(null);
    }

    public void clearAll() {
        Short driverId = MySingleton.getInstance().getLoginInfo().loginId;
        batchId = MySingleton.getInstance().getProperty(MySingleton.ITEM_CURRENT_BATCH_ID);

        if (batchId == null || batchId.isEmpty() || driverId == null || driverId < 1) {
            return;
        }

        DeliveryInfoDao deliveryInfoDao = MySingleton.getInstance().getmMydb().getDeliveryInfoDao();

        listDeliveryInfo.clear();
        try {
                MySingleton.getInstance().getDbHandler().post(() -> {
                    deliveryInfoDao.delete(batchId, driverId);
            });
        } catch (Exception e) {
            Log.e(TAG, "clear delivery info failed " + e.getMessage());
        }
    }

    /**
     * Get the delivery info from the server, it should be called after user login.
     */
    public void getDeliveryInfo(Short driverId){
        boolean mbAutoSave   =  MySingleton.getInstance().getBooleanProperty(MySingleton.ITEM_AUTO_SAVE_SCAN);
        if (!mbAutoSave)
            getDeliveryTask(driverId);
        else {
            @SuppressLint("DefaultLocale") String realUrl = String.format(URL_DELIVERING_LIST, MySingleton.getInstance().getLoginInfo().loginId);
            MySingleton.getInstance().getServerInterface().getRequestWithRsp(driverId, realUrl, null, AppRsp.class, MySingleton.getInstance().getDbHandler());
        }
    }

    /**
     * Get the delivery packages unscanned from the server, it should be called after user login.
     * @param driverId
     */
    private void getDeliveryTask(Short driverId)
    {
        GetDeliveryTaskApi<String,AppRsp> api = new GetDeliveryTaskApi<>(null,AppRsp.class);
        api.doApi();
    }

    /**
     * Save the delivery info to the database, and app always load the delivery info from the database,
     * therefore, app can use without the network. But we need another interface to sync the delivery info
     * with server.
     * @param d
     */
    public void saveDeliveringListData(DeliveringListData d)
    {
        Short driverId = MySingleton.getInstance().getLoginInfo().loginId;
        if (batchId == null || batchId.isEmpty() || driverId == null || driverId < 1) {
            return;
        }

       DeliveryInfoDao deliveryInfoDao = MySingleton.getInstance().getmMydb().getDeliveryInfoDao();

        MySingleton.getInstance().getDbHandler().post(() -> {
            try {

                DeliveryInfo info = new DeliveryInfo();
                info.setRouteNumber(String.valueOf(d.getRoute_no()));
                info.setLatitude(Double.parseDouble(d.getLat()));
                info.setLongitude(Double.parseDouble(d.getLng()));
                info.setAddress(d.getAddress());
                info.setName(d.getName());
                info.setPhone(d.getMobile());
                info.setUnitNumber(d.getUnit_number());
                info.setBatchNumber(batchId);
                info.setDriverId(driverId);
                info.setOrderSn(d.getTracking_no());
                info.setOrderId(d.getOrder_id());

                deliveryInfoDao.insert(info);

                listDeliveryInfo.add(info);
                } catch (Exception e) {
                    Log.e(TAG, "save delivery info failed " + e.getMessage());
                }
        });
    }

    /**
     * Load the delivery info from the database and save it to the local cache.
     * It should be called every time the app is started.
     */
    public void loadDeliveryInfo(ResponseCallBack<List<DeliveryInfo>> callBack){
        Short driverId = MySingleton.getInstance().getLoginInfo().loginId;

        if (batchId == null || batchId.isEmpty() || driverId == null || driverId < 1) {
            return;
        }

        DeliveryInfoDao deliveryInfoDao = MySingleton.getInstance().getmMydb().getDeliveryInfoDao();

        MySingleton.getInstance().getDbHandler().post(()->{
            try {
                List<DeliveryInfo> records = deliveryInfoDao.findByBatchNumber(batchId , driverId);

                for (DeliveryInfo r : records) {
                    addDeliveryInfo(r);
                }

                if (callBack != null)
                    callBack.onComplete(new Result.Success<List<DeliveryInfo>>(listDeliveryInfo));
            }catch(Exception e)
            {
                if (callBack != null)
                    callBack.onFail(new Result.Error<List<DeliveryInfo>>(e));
            }
        });
    }

    private void addDeliveryInfo(DeliveryInfo p)
    {
        listDeliveryInfo.add(p);
    }

    /**
     * called when user login
     * @param event
     */
    @Override
    public void receive(Event event) {
        getDeliveryInfo((Short)event.getMessage());
    }
}
