package com.uniuni.SysMgrTool.dao;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.Query;
import androidx.room.Update;

import java.util.List;

@Dao
public interface DeliveredPackagesDao {

    @Insert
    void insert(PackageEntity packageEntity);

    @Query("SELECT * FROM delivered_packages WHERE driverId = :driverId AND status = :status ORDER BY saveTime")
    List<PackageEntity> loadByDriverAndStatus(Short driverId, String status);

    @Query("SELECT * FROM delivered_packages WHERE orderId = :orderId")
    PackageEntity getByOrderId(Long orderId);

    @Update
    void update(PackageEntity packageEntity);
}
