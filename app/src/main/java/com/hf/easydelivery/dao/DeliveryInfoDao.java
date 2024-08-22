package com.hf.easydelivery.dao;

import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.Query;

import java.util.List;

@Dao
public interface DeliveryInfoDao {
    @Insert
    void insert(DeliveryInfo deliveryInfo);

    @Query("SELECT * FROM delivery_info WHERE batch_number = :batchNumber and driver_id = :driverId")
    List<DeliveryInfo> findByBatchNumber(String batchNumber , Short driverId);

    @Query("DELETE FROM delivery_info where batch_number = :batchNumber and driver_id = :driverId")
    void delete(String batchNumber , Short driverId);
}
