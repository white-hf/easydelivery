package com.hf.easydelivery.dao;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.Ignore;
import androidx.room.PrimaryKey;

import com.google.android.gms.maps.model.LatLng;
import com.hf.easydelivery.common.Utils;
import com.hf.courierservice.bean.Dispatch_type;

import java.util.Locale;
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

    @Ignore
    private Integer civilNumber = Integer.valueOf(0);

    @Ignore
    private String streetName = "";

    @Ignore
    private Integer state = 0;

    @Ignore
    private Dispatch_type dispatchType;

    public Dispatch_type getDispatchType() {
        return dispatchType;
    }

    public void setDispatchType(Dispatch_type dispatchType) {
        this.dispatchType = dispatchType;
    }

    public String getOrderSn() {
        return orderSn;
    }

    public Integer getCivilNumber() {
        return civilNumber;
    }

    public Integer getState() {
        return state;
    }

    public void setState(Integer s) {
        state = s;
    }

    public PackageEntity transferToPackageEntity() {
        PackageEntity packageEntity = new PackageEntity();
        packageEntity.driverId = driverId;
        packageEntity.batchNumber = batchNumber;
        packageEntity.trackingId = orderSn;
        packageEntity.orderId = orderId;
        packageEntity.longitude = longitude;
        packageEntity.latitude = latitude;
        packageEntity.recipientName = name;
        packageEntity.deliveryResult = 0;
        packageEntity.failedReason = null;
        // packageEntity.status = String.valueOf(state);

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

        Utils.AddressInfo addressInfo = Utils.extractApartmentAndStreetNumber(address);

        String streetNumber = addressInfo.getStreetNumber();
        if (streetNumber != null && !streetNumber.isEmpty()) {
            try {
                civilNumber = Integer.parseInt(streetNumber.replaceAll("[^0-9]", ""));
            } catch (Exception ignored) {
            }
        }

        String extractedUnit = addressInfo.getApartmentNumber();
        if (extractedUnit != null && !extractedUnit.trim().isEmpty()) {
            unitNumber = extractedUnit;
        }

        streetName = Utils.extractFirstWord(address);
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
        return new LatLng(latitude, longitude);
    }

    @Nullable
    @Override
    public String getTitle() {
        return routeNumber;
    }

    @Nullable
    @Override
    public String getSnippet() {
        return name;
    }

    /**
     * The z-index of this marker.
     */
    @Nullable
    @Override
    public Float getZIndex() {
        return 0f;
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

    public String getStreetName() {
        return streetName;
    }

    public String getStableKey() {
        if (orderSn != null && !orderSn.isEmpty()) {
            return "sn:" + orderSn;
        }
        if (orderId != null) {
            return "id:" + orderId;
        }
        return String.format(Locale.US, "pos:%.6f,%.6f:%s", latitude, longitude,
                routeNumber == null ? "" : routeNumber);
    }
}
