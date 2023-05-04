package com.uniuni.SysMgrTool.dao;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import java.util.Date;
@Dao
public interface OrderIdRecordDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    public void addRecord(OrderIdRecord ... record);

    @Query("SELECT * FROM orderid_record WHERE createDate >= :startDate and tid like '%' ||:orderIdKey")
    public OrderIdRecord [] findOrderId(String orderIdKey, Date startDate);

    @Query("SELECT * FROM orderid_record WHERE createDate >= :startDate")
    public OrderIdRecord [] findOrderIdByDate(Date startDate);
}
