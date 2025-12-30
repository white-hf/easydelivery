package com.hf.courierservice.bean;

import java.util.List;

/**
 * 生成扫描报告返回数据
 */
public class ScanBatchGenerateReportData {
    private String scan_time;
    private int assigned_parcels_count;
    private int scanned_parcels_count;
    private int unscanned_parcels_count;
    private List<String> unscanned_parcels;
    private int returned_parcels_count;
    private List<String> returned_parcels;

    public String getScan_time() {
        return scan_time;
    }
    public void setScan_time(String scan_time) {
        this.scan_time = scan_time;
    }

    public int getAssigned_parcels_count() {
        return assigned_parcels_count;
    }
    public void setAssigned_parcels_count(int assigned_parcels_count) {
        this.assigned_parcels_count = assigned_parcels_count;
    }

    public int getScanned_parcels_count() {
        return scanned_parcels_count;
    }
    public void setScanned_parcels_count(int scanned_parcels_count) {
        this.scanned_parcels_count = scanned_parcels_count;
    }

    public int getUnscanned_parcels_count() {
        return unscanned_parcels_count;
    }
    public void setUnscanned_parcels_count(int unscanned_parcels_count) {
        this.unscanned_parcels_count = unscanned_parcels_count;
    }

    public List<String> getUnscanned_parcels() {
        return unscanned_parcels;
    }
    public void setUnscanned_parcels(List<String> unscanned_parcels) {
        this.unscanned_parcels = unscanned_parcels;
    }

    public int getReturned_parcels_count() {
        return returned_parcels_count;
    }
    public void setReturned_parcels_count(int returned_parcels_count) {
        this.returned_parcels_count = returned_parcels_count;
    }

    public List<String> getReturned_parcels() {
        return returned_parcels;
    }
    public void setReturned_parcels(List<String> returned_parcels) {
        this.returned_parcels = returned_parcels;
    }
}
