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

    @Update
    void update(PackageEntity packageEntity);
}
