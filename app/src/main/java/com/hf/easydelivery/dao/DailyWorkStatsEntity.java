package com.hf.easydelivery.dao;

import androidx.annotation.NonNull;
import androidx.room.Entity;

@Entity(tableName = "daily_work_stats", primaryKeys = {"driverId", "statDate"})
public class DailyWorkStatsEntity {
    @NonNull
    public Integer driverId;
    @NonNull
    public Long statDate; // yyyymmdd (local date)
    public double totalDistanceMeters;
    public long lastUpdated;

    public DailyWorkStatsEntity(@NonNull Integer driverId,
                                @NonNull Long statDate,
                                double totalDistanceMeters,
                                long lastUpdated) {
        this.driverId = driverId;
        this.statDate = statDate;
        this.totalDistanceMeters = totalDistanceMeters;
        this.lastUpdated = lastUpdated;
    }
}
