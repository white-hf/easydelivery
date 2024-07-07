package com.uniuni.SysMgrTool.dao;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

import com.google.android.gms.maps.model.LatLng;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Entity(tableName = "delivery_info")
public class DeliveryInfo implements com.google.maps.android.clustering.ClusterItem {
    @PrimaryKey(autoGenerate = true)
    private int id;

    @ColumnInfo(name = "batch_number")
    private String batchNumber;

    @ColumnInfo(name = "route_number")
    private String routeNumber;

    @ColumnInfo(name = "longitude")
    private double longitude;

    @ColumnInfo(name = "latitude")
    private double latitude;

    @ColumnInfo(name = "address")
    private String address;

    @ColumnInfo(name = "unit_number")
    private String unitNumber;

    @ColumnInfo(name = "name")
    private String name;

    @ColumnInfo(name = "phone")
    private String phone;

    @ColumnInfo(name = "driver_id")
    private Short driverId;

    @ColumnInfo(name = "order_sn")
    private String orderSn;

    @ColumnInfo(name = "order_id")
    private Long orderId;

    public String getOrderSn() {
        return orderSn;
    }

    public PackageEntity transferToPackageEntity() {
        PackageEntity packageEntity = new PackageEntity();
        packageEntity.driverId = driverId;
        packageEntity.batchNumber = batchNumber;
        packageEntity.trackingId = orderSn;
        packageEntity.orderId = orderId;
        packageEntity.longitude = longitude;
        packageEntity.latitude = latitude;

        return packageEntity;
    }

    public void setOrderSn(String orderSn) {
        this.orderSn = orderSn;
    }

    // Getters and setters for each field
    // ...

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public String getBatchNumber() {
        return batchNumber;
    }

    public void setBatchNumber(String batchNumber) {
        this.batchNumber = batchNumber;
    }

    public String getRouteNumber() {
        return routeNumber;
    }

    public void setRouteNumber(String routeNumber) {
        this.routeNumber = routeNumber;
    }

    public double getLongitude() {
        return longitude;
    }

    public void setLongitude(double longitude) {
        this.longitude = longitude;
    }

    public double getLatitude() {
        return latitude;
    }

    public void setLatitude(double latitude) {
        this.latitude = latitude;
    }

    public String getAddress() {
        return address;
    }

    public void setAddress(String address) {
        this.address = address;
    }

    public String getUnitNumber() {
        return unitNumber;
    }

    public void setUnitNumber(String unitNumber) {
        this.unitNumber = unitNumber;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getPhone() {
        return phone;
    }

    public void setPhone(String phone) {
        this.phone = phone;
    }

    private static final Pattern NUMBER_PATTERN = Pattern.compile("(\\d+)");
    private String extractFirstNumber(String address) {
        if (address == null || address.isEmpty()) {
            return "";
        }

        Matcher matcher = NUMBER_PATTERN.matcher(address);

        if (matcher.find()) {
            return matcher.group(1).trim();
        }

        return "";
    }



    @NonNull
    @Override
    public LatLng getPosition() {
        return new LatLng(latitude ,  longitude);
    }

    @Nullable
    @Override
    public String getTitle() {
        return routeNumber + " (" + extractFirstNumber(address) + ")";
    }

    @Nullable
    @Override
    public String getSnippet() {
        return name;
    }

    public Short getDriverId() {
        return driverId;
    }

    public void setDriverId(Short driverId) {
        this.driverId = driverId;
    }

    public Long getOrderId() {
        return orderId;
    }

    public void setOrderId(Long orderId) {
        this.orderId = orderId;
    }
}
