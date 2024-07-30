package com.uniuni.SysMgrTool.manager;

import static com.uniuni.SysMgrTool.MySingleton.TAG;

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

    static public class DistanceCalculator {
        private static final double EARTH_RADIUS = 6371e3; // in meters

        public static double haversine(double lat1, double lon1, double lat2, double lon2) {
            double dLat = Math.toRadians(lat2 - lat1);
            double dLon = Math.toRadians(lon2 - lon1);

            double a = Math.sin(dLat / 2) * Math.sin(dLat / 2) +
                    Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) *
                            Math.sin(dLon / 2) * Math.sin(dLon / 2);
            double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));

            return EARTH_RADIUS * c;
        }
    }

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

    public final DeliveryInfo getByRouteId(String routeId) {
        if (routeId == null)
            return null;

        return listDeliveryInfo.stream().filter(pkg->pkg.getRouteNumber().equals(routeId)).findFirst().orElse(null);
    }

    public Boolean exit(Long orderId) {
        if (orderId == null) {
            return Boolean.FALSE;
        }

        DeliveryInfo deliveryInfo = listDeliveryInfo.stream().filter(pkg -> pkg.getOrderId().equals(orderId)).findFirst().orElse(null);
        if (deliveryInfo != null)
            return Boolean.TRUE;

        return Boolean.FALSE;
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
     * @param driverId
     */
    public void getDeliveryInfo(Short driverId , Boolean bDeliveryTask)
    {
        GetDeliveryTaskApi<String,AppRsp> api = new GetDeliveryTaskApi<>(null,AppRsp.class , bDeliveryTask);
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

        listDeliveryInfo.add(info);
        MySingleton.getInstance().getDbHandler().post(() -> {
            try {
                    deliveryInfoDao.insert(info);
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
     * Find the nearest package to the current location. It should be called after one package is delivered, then show the next package.
     * @param currentLat
     * @param currentLon
     * @param minDistance
     * @return
     */
    public DeliveryInfo findNearestPackage(double currentLat, double currentLon, double minDistance) {
        DeliveryInfo nearestPackage = null;
        double minDistanceFound = Double.MAX_VALUE;

        for (DeliveryInfo deliveryInfo : listDeliveryInfo) {
            double distance = DistanceCalculator.haversine(currentLat, currentLon, deliveryInfo.getLatitude(), deliveryInfo.getLongitude());
            if (distance > minDistance && distance < minDistanceFound) {
                nearestPackage = deliveryInfo;
                break;
            }
        }

        return nearestPackage;
    }
    /**
     * called when user login
     * @param event
     */
    @Override
    public void receive(Event event) {
        getDeliveryInfo((Short)event.getMessage() , true);
    }
}
