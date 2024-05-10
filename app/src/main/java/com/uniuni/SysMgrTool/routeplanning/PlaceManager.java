package com.uniuni.SysMgrTool.routeplanning;

import static java.lang.Thread.sleep;

import com.uniuni.SysMgrTool.MySingleton;
import com.uniuni.SysMgrTool.bean.ScanOrder;
import com.uniuni.SysMgrTool.thirdpart.ThirdApi;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

public class PlaceManager {
    private ArrayList<Place> mLstPlace = new ArrayList<>();

    public void initPlace() {
        int    driverId        = MySingleton.getInstance().getIntProperty(MySingleton.ITEM_DRIVER_ID);
        String batchId         = MySingleton.getInstance().getProperty(MySingleton.ITEM_CURRENT_BATCH_ID);

        if (driverId < 1 || batchId == null || batchId.isEmpty())
            return;

        MySingleton.getInstance().getServerInterface().loadOrdersByDriver(batchId , driverId , null);
        try {
            sleep(3000);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        final HashMap<String, ScanOrder> orderHashMap = MySingleton.getInstance().getmHashOrders();
        for (Map.Entry<String, ScanOrder> entry : orderHashMap.entrySet()) {
            ScanOrder o = entry.getValue();

            Place p = new Place();
            p.setPickId(o.getPackId());
            p.setTid(o.getId());
            p.setLon((Double.valueOf(o.getLng())));
            p.setAlon(Double.valueOf(o.getLat()));
            p.setAddress(o.getAddress());

            mLstPlace.add(p);
        }

        for(Place p : mLstPlace)
        {
            p.initOtherPlaces(mLstPlace);
            p.judgeTop3NearstPaces();
        }
    }

    public String getJudgeResult()
    {
        StringBuffer sb = new StringBuffer();
        for(Place p : mLstPlace)
        {
            if (p.isbBigGap())
            {
                sb.append(p.getTid())
                        .append("-")
                        .append(p.getPickId())
                        .append(":");
            }
        }

        return sb.toString();
    }

    public String checkPlaceFromThirdPart() throws IOException, InterruptedException {
        ThirdApi api = new ThirdApi();
        StringBuffer sb = new StringBuffer();

        Double[] coordinate = new Double[2];
        for(Place p : mLstPlace)
        {
            Double lan = null;
            Double lon = null;
            boolean r = api.parseAddress(p.getAddress() , coordinate);
            if (r)
            {
                if (p.checkDistance(coordinate[0],coordinate[1]))
                {
                    sb.append(p.getTid())
                            .append("-")
                            .append(p.getTid())
                            .append(":");
                }
            }

            sleep(5000);
        }

        return sb.toString();
    }
}
