package com.uniuni.SysMgrTool.dao;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.Query;
import androidx.room.Update;

@Dao
public interface ScannedRecordDao {
    @Insert
    public void addRecord(ScannedRecord ... record);

    @Update
    public void updateRecord(ScannedRecord ... record);

    @Query("SELECT * FROM scanned_record WHERE isCommitted = 0 and batchId = :currentBatchId and driverId=:driverId")
    public ScannedRecord [] loadUnCommittedRecords(String currentBatchId , Integer driverId);

    @Query("SELECT * FROM scanned_record")
    public ScannedRecord [] loadAll();
}