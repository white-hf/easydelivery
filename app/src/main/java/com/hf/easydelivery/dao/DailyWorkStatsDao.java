package com.hf.easydelivery.dao;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Update;

@Dao
public interface DailyWorkStatsDao {

    @Query("SELECT * FROM daily_work_stats WHERE driverId = :driverId AND statDate = :date LIMIT 1")
    DailyWorkStatsEntity getForDate(int driverId, long date);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void upsert(DailyWorkStatsEntity entity);

    @Query("UPDATE daily_work_stats SET totalDistanceMeters = :distance, lastUpdated = :lastUpdated WHERE driverId = :driverId AND statDate = :date")
    void updateDistance(int driverId, long date, double distance, long lastUpdated);

    @Query("SELECT totalDistanceMeters FROM daily_work_stats WHERE driverId = :driverId AND statDate = :date")
    Double getDistanceMeters(int driverId, long date);

    @Query("SELECT * FROM daily_work_stats WHERE driverId = :driverId AND statDate BETWEEN :startDate AND :endDate")
    java.util.List<DailyWorkStatsEntity> getForRange(int driverId, long startDate, long endDate);
}
