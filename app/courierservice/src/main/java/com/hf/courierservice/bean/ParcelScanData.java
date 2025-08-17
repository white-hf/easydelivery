package com.hf.courierservice.bean;


/**
 * 扫描包裹后的返回数据，用于跨模块复用
 */
public class ParcelScanData {
    private long orderId;
    private String trackingNo;
    private int routeNo;

    public ParcelScanData() {}

    public long getOrderId() {
        return orderId;
    }
    public void setOrderId(long orderId) {
        this.orderId = orderId;
    }

    public String getTrackingNo() {
        return trackingNo;
    }
    public void setTrackingNo(String trackingNo) {
        this.trackingNo = trackingNo;
    }

    public int getRouteNo() {
        return routeNo;
    }
    public void setRouteNo(int routeNo) {
        this.routeNo = routeNo;
    }
}
