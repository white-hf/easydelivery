package com.uniuni.SysMgrTool.dao;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

import com.google.android.gms.maps.model.LatLng;

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

    @NonNull
    @Override
    public LatLng getPosition() {
        return new LatLng(latitude ,  longitude);
    }

    @Nullable
    @Override
    public String getTitle() {
        return routeNumber;
    }

    @Nullable
    @Override
    public String getSnippet() {
        return null;
    }

    public Short getDriverId() {
        return driverId;
    }

    public void setDriverId(Short driverId) {
        this.driverId = driverId;
    }
}
