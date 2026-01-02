package com.hf.easydelivery.view.model;

import android.location.Location;
import android.util.Log;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.hf.courierservice.apihelper.FileLog;
import com.hf.easydelivery.ResourceMgr;
import com.hf.easydelivery.R;
import com.hf.easydelivery.core.DeliveryinfoMgr;
import com.hf.easydelivery.dao.DeliveryInfo;
import com.hf.easydelivery.dao.PackageEntity;
import com.hf.easydelivery.event.Event;
import com.hf.easydelivery.event.EventConstant;
import com.hf.easydelivery.event.Publisher;
import com.hf.easydelivery.event.Subscriber;

import java.util.ArrayList;
import java.util.List;

public class MapViewModel extends ViewModel implements Subscriber {

    private static final String TAG = "MapViewModel";

    // LiveData 用于向 Fragment 暴露数据和状态
    private final MutableLiveData<List<DeliveryInfo>> mapItemsLive = new MutableLiveData<>();
    private final MutableLiveData<MapStatus> statusLive = new MutableLiveData<>();
    private final MutableLiveData<Location> myLocationLive = new MutableLiveData<>();
    private final MutableLiveData<Event<String>> toastMessageLive = new MutableLiveData<>();
    private final MutableLiveData<Event<String>> dialogMessageLive = new MutableLiveData<>();
    private final MutableLiveData<Event<Boolean>> uploadSuccessHapticLive = new MutableLiveData<>();
    private final MutableLiveData<Event<DeliveryInfo>> deliveryCompletedLive = new MutableLiveData<>();

    // 内部状态计数
    private int deliveredCount = 0;
    private int pendingCount = 0;

    // 标志：是否首次进入
    private boolean firstEnter = true;

    // 内部类：聚合所有状态数据，方便一次性更新
    public static class MapStatus {
        public int deliveredCount;
        public int pendingCount;
        public boolean isLoading = true;
    }

    public MapViewModel() {
        // 订阅所有相关的事件
        Publisher publisher = ResourceMgr.getInstance().getPublisher();
        publisher.subscribe(EventConstant.EVENT_UPLOAD_FAILURE, this);
        publisher.subscribe(EventConstant.EVENT_UPLOAD_SUCCESS, this);
        publisher.subscribe(EventConstant.EVENT_SAVE_DELIVERY_SUCCESS, this);
        publisher.subscribe(EventConstant.EVENT_DELIVERY_DATA_READY, this);

        // 初始化状态
        MapStatus status = new MapStatus();
        status.isLoading = true;
        statusLive.setValue(status);
        FileLog.i(TAG, "MapViewModel created, subscribing to events.");
    }

    // region ★ 向 Fragment 暴露的 LiveData ★
    public LiveData<List<DeliveryInfo>> getMapItemsLive() {
        return mapItemsLive;
    }

    public LiveData<MapStatus> getStatusLive() {
        return statusLive;
    }

    public LiveData<Location> getMyLocationLive() {
        return myLocationLive;
    }

    public LiveData<Event<String>> getToastMessageLive() {
        return toastMessageLive;
    }

    public LiveData<Event<String>> getDialogMessageLive() {
        return dialogMessageLive;
    }

    public LiveData<Event<Boolean>> getUploadSuccessHapticLive() {
        return uploadSuccessHapticLive;
    }

    public LiveData<Event<DeliveryInfo>> getDeliveryCompletedLive() {
        return deliveryCompletedLive;
    }
    // endregion

    // region ★ 公共方法：供 Fragment 调用 ★

    /**
     * 触发数据加载和状态初始化。
     * 仅在 Fragment 首次进入时调用一次。
     */
    public void init() {
        if (firstEnter) {
            firstEnter = false;
            initStatusBarCounts();
            requestAndRefreshMarkers(true); // 默认加载派送中包裹
        }
    }

    /**
     * 更新当前定位，供 Fragment 调用。
     */
    public void updateMyLocation(Location location) {
        myLocationLive.setValue(location);
    }

    /**
     * 刷新派送中或未扫描包裹数据
     *
     * @param bDeliveryTask true = 派送中, false = 未扫描
     */
    public void requestAndRefreshMarkers(Boolean bDeliveryTask) {
        FileLog.i(TAG, "requestAndRefreshMarkers: loading data, bDeliveryTask=" + bDeliveryTask);
        publishStatus(true);
        try {
            ResourceMgr.getInstance().getDeliveryinfoMgr().getDeliveryInfo(ResourceMgr.getInstance().getLoginInfo().loginId, bDeliveryTask);
        } catch (Throwable t) {
            toastMessageLive.setValue(new Event<>(getString(R.string.map_request_failed)));
            publishStatus(false);
        }
    }

    // endregion

    // region ★ 事件处理：响应 Event Bus ★

    /**
     * 事件回调，所有业务逻辑都在这里处理，然后通过 LiveData 通知 UI
     */
    @Override
    public void receive(Event event) {
        DeliveryinfoMgr deliveryinfoMgr = ResourceMgr.getInstance().getDeliveryinfoMgr();
        switch (event.getEventType()) {
            case EventConstant.EVENT_UPLOAD_FAILURE:
                FileLog.e(TAG, "receive: EVENT_UPLOAD_FAILURE");
                toastMessageLive.setValue(new Event<>(getString(R.string.map_upload_failed_toast)));
                dialogMessageLive.setValue(new Event<>(getString(R.string.map_upload_failed_dialog)));
                updateStatusCounts(0);
                break;

            case EventConstant.EVENT_SAVE_DELIVERY_SUCCESS:
                FileLog.i(TAG, "receive: EVENT_SAVE_DELIVERY_SUCCESS");
                PackageEntity packageEntity = (PackageEntity) event.getMessage();
                if (packageEntity == null) return;
                toastMessageLive.setValue(new Event<>(getString(R.string.map_save_success)));
                // 从内存列表移除包裹，并更新 UI
                DeliveryInfo info = deliveryinfoMgr.get(packageEntity.orderId);
                if (info != null) {
                    deliveryinfoMgr.getListDeliveryInfo().remove(info);
                    deliveryCompletedLive.setValue(new Event<>(info));
                }

                refreshMapItemsFromRepo();
                updateStatusCounts(0);
                break;

            case EventConstant.EVENT_UPLOAD_SUCCESS:
                FileLog.i(TAG, "receive: EVENT_UPLOAD_SUCCESS");
                PackageEntity pkg = (PackageEntity) event.getMessage();
                if (pkg == null) return;
                toastMessageLive.setValue(new Event<>(getString(R.string.map_upload_success)));
                uploadSuccessHapticLive.setValue(new Event<>(Boolean.TRUE));
                updateStatusCounts(1);
                break;

            case EventConstant.EVENT_DELIVERY_DATA_READY:
                FileLog.i(TAG, "receive: EVENT_DELIVERY_DATA_READY");
                refreshMapItemsFromRepo();
                updateStatusCounts(0);
                break;
        }
    }

    // endregion

    // region ★ 内部工具方法 ★

    /**
     * 从 ResourceMgr 仓库加载数据到 LiveData
     */
    private void refreshMapItemsFromRepo() {
        List<DeliveryInfo> lst = ResourceMgr.getInstance().getDeliveryinfoMgr().getListDeliveryInfo();
        mapItemsLive.postValue(lst); // postValue 确保在主线程更新
        FileLog.i(TAG, "refreshMapItemsFromRepo: updated list size=" + (lst != null ? lst.size() : 0));
    }

    /**
     * 初始化状态计数，仅在第一次加载时调用
     */
    private void initStatusBarCounts() {
        refreshPendingFromRepo();
        publishStatus(false);
        FileLog.i(TAG, "initStatusBarCounts: initial counts - pending=" + pendingCount);
    }

    /**
     * 统一的状态计数更新
     */
    private void updateStatusCounts(int deliveredDelta) {
        deliveredCount += deliveredDelta;
        if (deliveredCount < 0) deliveredCount = 0;
        refreshPendingFromRepo();
        publishStatus(false);
        FileLog.i(TAG, String.format("updateStatusCounts: delivered=%d, pending=%d", deliveredCount, pendingCount));
    }

    private void refreshPendingFromRepo() {
        try {
            List<DeliveryInfo> list = ResourceMgr.getInstance().getDeliveryinfoMgr().getListDeliveryInfo();
            pendingCount = list == null ? 0 : list.size();
        } catch (Throwable t) {
            pendingCount = 0;
        }
    }

    private void publishStatus(boolean isLoading) {
        MapStatus status = new MapStatus();
        status.deliveredCount = deliveredCount;
        status.pendingCount = pendingCount;
        status.isLoading = isLoading;
        statusLive.setValue(status);
    }
    // endregion

    private String getString(int resId, Object... args) {
        if (ResourceMgr.getInstance().getCtx() == null) {
            return "";
        }
        return args.length == 0
                ? ResourceMgr.getInstance().getCtx().getString(resId)
                : ResourceMgr.getInstance().getCtx().getString(resId, args);
    }

    @Override
    protected void onCleared() {
        super.onCleared();
        // 取消事件订阅，避免内存泄漏
        Publisher publisher = ResourceMgr.getInstance().getPublisher();
        publisher.unsubscribe(EventConstant.EVENT_UPLOAD_FAILURE, this);
        publisher.unsubscribe(EventConstant.EVENT_UPLOAD_SUCCESS, this);
        publisher.unsubscribe(EventConstant.EVENT_SAVE_DELIVERY_SUCCESS, this);
        publisher.unsubscribe(EventConstant.EVENT_DELIVERY_DATA_READY, this);
        FileLog.i(TAG, "MapViewModel cleared.");
    }
}
