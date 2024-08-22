package com.hf.easydelivery.api;

import com.hf.courierservice.Result;
import com.hf.courierservice.bean.DeliveringListData;
import com.hf.easydelivery.event.Event;
import com.hf.easydelivery.event.EventConstant;
import com.hf.easydelivery.ResourceMgr;
import com.hf.easydelivery.common.FileLog;

import java.util.List;
import java.util.ListIterator;

public class GetPackageListRspCb extends ResponseCallBackBase<List<DeliveringListData>> {
    @Override
    public void onComplete(Result<List<DeliveringListData>> result) {
        Result.Success<List<DeliveringListData>> su = (Result.Success<List<DeliveringListData>>)result;
        List<DeliveringListData> lst = su.data;

        ListIterator<DeliveringListData> listIterator = lst.listIterator();
        FileLog.getInstance().writeLog("Get delivering  list:" + lst.size());

        //remove the old data from cache firstly.
        ResourceMgr.getInstance().getDeliveryinfoMgr().clearAll();

        while (listIterator.hasNext()) {
            DeliveringListData d = (DeliveringListData) listIterator.next();
            ResourceMgr.getInstance().getDeliveryinfoMgr().saveDeliveringListData(d);
        }

        //notify the ui delivery data is ready
        ResourceMgr.getInstance().getPublisher().notify(EventConstant.EVENT_DELIVERY_DATA_READY, new Event<Integer>(1));
    }
}
