package com.hf.easydelivery.dao;

import androidx.room.Database;
import androidx.room.RoomDatabase;
import androidx.room.TypeConverters;

@Database(entities = {DeliveryInfo.class, PackageEntity.class, ScanRecord.class, DailyWorkStatsEntity.class},version = 3, exportSchema = false)
@TypeConverters(DateConverter.class)
public abstract class AppDatabase extends RoomDatabase{
    public abstract DeliveryInfoDao deliveryInfoDao();
    public abstract DeliveredPackagesDao deliveredPackagesDao();
    public abstract ScanRecordDao scanRecordDao();
    public abstract DailyWorkStatsDao dailyWorkStatsDao();
}
