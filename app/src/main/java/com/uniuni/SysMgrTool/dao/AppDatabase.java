package com.uniuni.SysMgrTool.dao;

import androidx.room.Database;
import androidx.room.RoomDatabase;
import androidx.room.TypeConverters;

@Database(entities = {ScannedRecord.class,OrderIdRecord.class,DeliveryInfo.class, PackageEntity.class},version = 1)
@TypeConverters(DateConverter.class)
public abstract class AppDatabase extends RoomDatabase{
    public abstract ScannedRecordDao getScannedRecordDao();
    public abstract OrderIdRecordDao getOrderIdRecordDao();
    public abstract DeliveryInfoDao deliveryInfoDao();
    public abstract DeliveredPackagesDao deliveredPackagesDao();
}
