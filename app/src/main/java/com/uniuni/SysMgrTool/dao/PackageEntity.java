package com.uniuni.SysMgrTool.dao;

import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "delivered_packages")
public class PackageEntity {
    @PrimaryKey
    public Long orderId;
    public String trackingId;
    public String imagePath;// if there are multiple images, separate them by comma
    public long saveTime;
    public long createTime;
    public String status;
    public Double longitude;
    public Double latitude;
    public Short driverId;
    public String batchNumber;
}
