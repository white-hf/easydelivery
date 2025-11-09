package com.hf.easydelivery.dao;

import androidx.lifecycle.LiveData;
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

    @Query("SELECT * FROM delivered_packages WHERE trackingId = :trackingId LIMIT 1")
    PackageEntity getByTrackingId(String trackingId);

    @Query("SELECT status FROM delivered_packages WHERE trackingId = :trackingId LIMIT 1")
    String getStatusByTrackingId(String trackingId);

    @Query("UPDATE delivered_packages SET status = :status WHERE trackingId = :trackingId")
    int updateStatusByTrackingId(String trackingId, String status);

    @Update
    int update(PackageEntity packageEntity);

    @Query("SELECT * FROM delivered_packages WHERE status = :status ORDER BY createTime DESC LIMIT 30")
    LiveData<List<PackageEntity>> getPackagesByStatus(String status);

    @Query("SELECT * FROM delivered_packages WHERE status IN (:statuses) AND createTime >= :startTime AND createTime < :endTime ORDER BY createTime DESC")
    LiveData<List<PackageEntity>> getPackagesByStatusAndDate(List<String> statuses, long startTime, long endTime);

    @Query("SELECT COUNT(*) FROM delivered_packages WHERE driverId = :driverId AND saveTime BETWEEN :startTime AND :endTime")
    int countDeliveredByDriverAndTime(Short driverId, long startTime, long endTime);
}
