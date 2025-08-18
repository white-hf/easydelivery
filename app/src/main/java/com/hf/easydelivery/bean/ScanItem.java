package com.hf.easydelivery.bean;


public class ScanItem {
    private final String packageNo;
    private final String waybillNo;

    private final boolean uploaded;
    private final boolean isScanned;

    public boolean isScanned() {
        return isScanned;
    }

    public ScanItem(String packageNo, String waybillNo , boolean bUpload , boolean bScanned) {
        this.packageNo = packageNo;
        this.waybillNo = waybillNo;
        this.uploaded = bUpload;
        this.isScanned = bScanned;
    }
    public String getPackageNo() { return packageNo; }
    public String getWaybillNo() { return waybillNo; }

    public boolean isUploaded() {
        return uploaded;
    }
}
