package com.hf.easydelivery.bean;


public class ScanItem {
    private final String packageNo;
    private final String waybillNo;

    public ScanItem(String packageNo, String waybillNo) {
        this.packageNo = packageNo;
        this.waybillNo = waybillNo;
    }
    public String getPackageNo() { return packageNo; }
    public String getWaybillNo() { return waybillNo; }
}
