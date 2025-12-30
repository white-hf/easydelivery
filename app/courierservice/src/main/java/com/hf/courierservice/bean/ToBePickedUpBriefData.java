package com.hf.courierservice.bean;

/**
 * 待分拣扫描包裹概要数据
 */
public class ToBePickedUpBriefData {
    private int total_number;
    private String address;
    private long scan_batch_id;
    private int scan_batch_status;
    private int scanned_item_quantity;

    public int getTotal_number() {
        return total_number;
    }
    public void setTotal_number(int total_number) {
        this.total_number = total_number;
    }

    public String getAddress() {
        return address;
    }
    public void setAddress(String address) {
        this.address = address;
    }

    public long getScan_batch_id() {
        return scan_batch_id;
    }
    public void setScan_batch_id(long scan_batch_id) {
        this.scan_batch_id = scan_batch_id;
    }

    public int getScan_batch_status() {
        return scan_batch_status;
    }
    public void setScan_batch_status(int scan_batch_status) {
        this.scan_batch_status = scan_batch_status;
    }

    public int getScanned_item_quantity() {
        return scanned_item_quantity;
    }
    public void setScanned_item_quantity(int scanned_item_quantity) {
        this.scanned_item_quantity = scanned_item_quantity;
    }
}
