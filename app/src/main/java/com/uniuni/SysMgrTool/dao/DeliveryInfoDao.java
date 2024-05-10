package com.uniuni.SysMgrTool.dao;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.Query;

import java.util.List;

@Dao
public interface DeliveryInfoDao {
    @Insert
    void insert(DeliveryInfo deliveryInfo);

    @Query("SELECT * FROM delivery_info WHERE batch_number = :batchNumber")
    List<DeliveryInfo> findByBatchNumber(String batchNumber);
}
