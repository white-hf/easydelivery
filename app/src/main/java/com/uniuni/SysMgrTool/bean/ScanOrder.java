package com.uniuni.SysMgrTool.bean;

import java.io.Serializable;

public class  ScanOrder implements Serializable
{
    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public Short getPackId() {
        return packId;
    }

    public void setPackId(Short packId) {
        this.packId = packId;
    }

    public Boolean getScanned() {
        return isScanned;
    }

    public void setScanned(Boolean scanned) {
        isScanned = scanned;
    }

    public String getZipCode() {
        return zipCode;
    }

    public void setZipCode(String zipCode) {
        this.zipCode = zipCode;
    }

    @Override
    public String toString() {
        return "ScanOrder{" +
                "zipCode='" + zipCode + '\'' +
                ", id='" + id + '\'' +
                ", packId=" + packId +
                ", isScanned=" + isScanned +
                ", driverId=" + driverId +
                ", orderId=" + orderId +
                ", orderSn='" + orderSn + '\'' +
                ", lat='" + lat + '\'' +
                ", lng='" + lng + '\'' +
                '}';
    }

    private String zipCode;
    private String id;
    private Short packId;
    private Boolean isScanned = Boolean.FALSE;
    private Integer driverId;
    private Long    orderId;
    private String orderSn;

    private String lat;
    private String lng;

    private String address;

    private int lastStatus;

    public int getLastStatus() {
        return lastStatus;
    }

    public void setLastStatus(int lastStatus) {
        this.lastStatus = lastStatus;
    }

    public String getLat() {
        return lat;
    }

    public void setLat(String lat) {
        this.lat = lat;
    }

    public String getLng() {
        return lng;
    }

    public void setLng(String lng) {
        this.lng = lng;
    }

    public Long getOrderId() {
        return orderId;
    }

    public void setOrderId(Long orderId) {
        this.orderId = orderId;
    }

    public String getOrderSn() {
        return orderSn;
    }

    public void setOrderSn(String orderSn) {
        this.orderSn = orderSn;
    }

    public Integer getDriverId() {
        return driverId;
    }

    public void setDriverId(Integer driverId) {
        this.driverId = driverId;
    }

    public String getAddress() {
        return address;
    }

    public void setAddress(String address) {
        this.address = address;
    }
}
