package com.hf.easydelivery.dao;

import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "scan_records")
public class ScanRecord {
    @PrimaryKey(autoGenerate = true)
    public long id;

    public long timestamp;      // 扫描时间，毫秒
    public Integer driverId;    // 司机 ID
    public String trackingNo;   // 运单号
    public Short packageNo;     // 包裹号
    public Long scanBatchId;    // 扫描批次号
    public Boolean bUploaded;   // 是否已经上传

    public ScanRecord(long timestamp,
                      Integer driverId,
                      String trackingNo,
                      Short packageNo,
                      Long scanBatchId,
                      Boolean bUploaded) {
        this.timestamp   = timestamp;
        this.driverId    = driverId;
        this.trackingNo  = trackingNo;
        this.packageNo   = packageNo;
        this.scanBatchId = scanBatchId;
        this.bUploaded   = bUploaded;
    }
}

