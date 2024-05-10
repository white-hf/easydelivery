package com.uniuni.SysMgrTool.bean;

import java.util.Date;

public class BizData {
    private long scan_batch_id;
    private String name;
    private String dispatch_nos;
    private int driver_id;
    private int unscanned_parcels;
    private int scanned_parcels;
    private int returned_parcels;
    private Date scan_time;
    private int scan_batch_status;
    public void setScan_batch_id(long scan_batch_id) {
        this.scan_batch_id = scan_batch_id;
    }
    public long getScan_batch_id() {
        return scan_batch_id;
    }

    public void setName(String name) {
        this.name = name;
    }
    public String getName() {
        return name;
    }

    public void setDispatch_nos(String dispatch_nos) {
        this.dispatch_nos = dispatch_nos;
    }
    public String getDispatch_nos() {
        return dispatch_nos;
    }

    public void setDriver_id(int driver_id) {
        this.driver_id = driver_id;
    }
    public int getDriver_id() {
        return driver_id;
    }

    public void setUnscanned_parcels(int unscanned_parcels) {
        this.unscanned_parcels = unscanned_parcels;
    }
    public int getUnscanned_parcels() {
        return unscanned_parcels;
    }

    public void setScanned_parcels(int scanned_parcels) {
        this.scanned_parcels = scanned_parcels;
    }
    public int getScanned_parcels() {
        return scanned_parcels;
    }

    public void setReturned_parcels(int returned_parcels) {
        this.returned_parcels = returned_parcels;
    }
    public int getReturned_parcels() {
        return returned_parcels;
    }

    public void setScan_time(Date scan_time) {
        this.scan_time = scan_time;
    }
    public Date getScan_time() {
        return scan_time;
    }

    public void setScan_batch_status(int scan_batch_status) {
        this.scan_batch_status = scan_batch_status;
    }
    public int getScan_batch_status() {
        return scan_batch_status;
    }
}
