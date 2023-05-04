package com.uniuni.SysMgrTool.Request;

import com.uniuni.SysMgrTool.bean.ParcelInfo;

public class UpdateShippingStatusReq extends RequestBase {
    private Integer staff_id;
    private Integer shipping_status;
    private String scan_location;

    public Integer getStoraged_warehouse() {
        return storaged_warehouse;
    }

    public void setStoraged_warehouse(Integer storaged_warehouse) {
        this.storaged_warehouse = storaged_warehouse;
    }

    private Integer storaged_warehouse;
    private Integer send_sms;
    private String exception;
    ParcelInfo parcel_info = new ParcelInfo();

    public Integer getShipping_status() {
        return shipping_status;
    }

    public void setShipping_status(Integer shipping_status) {
        this.shipping_status = shipping_status;
    }

    public String getScan_location() {
        return scan_location;
    }

    public void setScan_location(String scan_location) {
        this.scan_location = scan_location;
    }

    public Integer getSend_sms() {
        return send_sms;
    }

    public void setSend_sms(Integer send_sms) {
        this.send_sms = send_sms;
    }

    public String getException() {
        return exception;
    }

    public void setException(String exception) {
        this.exception = exception;
    }

    public ParcelInfo getParcel_info() {
        return parcel_info;
    }

    public void setParcel_info(ParcelInfo parcel_info) {
        this.parcel_info = parcel_info;
    }

    public Integer getStaff_id() {
        return staff_id;
    }

    public void setStaff_id(Integer staff_id) {
        this.staff_id = staff_id;
    }

}
