package com.hf.easydelivery.view.model;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;
import java.util.ArrayList;
import java.util.List;

import com.hf.easydelivery.ResourceMgr;
import com.hf.easydelivery.dao.DeliveryInfo;
import com.hf.easydelivery.event.Event;
import com.hf.easydelivery.event.EventConstant;
import com.hf.easydelivery.event.Subscriber;

/**
 * 管理 Map 页的业务状态
 */
public class MapViewModel extends ViewModel implements Subscriber {

    private boolean firstShown = true;

    private final MutableLiveData<List<DeliveryInfo>> packages = new MutableLiveData<>(new ArrayList<>());

    public MapViewModel() {
        ResourceMgr.getInstance().getPublisher().subscribe(EventConstant.EVENT_UPLOAD_SUCCESS, this);
        ResourceMgr.getInstance().getPublisher().subscribe(EventConstant.EVENT_DELIVERY_DATA_READY, this);
    }

    /**
     * 是否首次进入 Map 页
     */
    public boolean shouldDoFirstEnter() {
        if (firstShown) {
            firstShown = false;
            return true;
        }
        return false;
    }

    public LiveData<List<DeliveryInfo>> getPackages() {
        return packages;
    }

    public void setPackages(List<DeliveryInfo> newList) {
        packages.setValue(newList);
    }

    public void removePackageById(String id) {
        List<DeliveryInfo> list = packages.getValue();
        if (list != null) {
            List<DeliveryInfo> updated = new ArrayList<>(list);
            updated.removeIf(p -> p.getOrderSn().equals(id));
            packages.postValue(updated);
        }
    }

    /**
     * Refresh the package list directly from ResourceMgr and update LiveData.
     */
    public void refreshPackagesFromResourceMgr() {
        List<DeliveryInfo> deliveryInfos = ResourceMgr.getInstance().getDeliveryinfoMgr().getListDeliveryInfo();

        packages.postValue(deliveryInfos);
    }

    @Override
    public void receive(Event event) {
        if (EventConstant.EVENT_UPLOAD_SUCCESS.equals(event.getEventType())) {
            String trackingNo = (String) event.getMessage();
            removePackageById(trackingNo);
        } else if (EventConstant.EVENT_DELIVERY_DATA_READY.equals(event.getEventType())) {
            refreshPackagesFromResourceMgr();
        }
    }
}