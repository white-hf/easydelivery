package com.hf.easydelivery.dao;


import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.Query;

import java.util.List;

@Dao
public interface ScanRecordDao {
    /** 插入一条扫描记录 */
    @Insert
    void insert(ScanRecord record);

    /**
     * 按日期查询：只返回表中 date(timestamp/1000,'unixepoch') = :date 的记录
     * @param date “YYYY-MM-DD” 格式的日期字符串
     */
    @Query("SELECT *FROM scan_records WHERE date(timestamp / 1000, 'unixepoch') = :date and bUploaded = :bUploaded and driverId = :driverId ORDER BY timestamp DESC")
    List<ScanRecord> loadByDate(String date , Boolean bUploaded , Integer driverId);

    /**
     * 根据 trackingNo 将 bUploaded 标记为 true
     */
    @Query("UPDATE scan_records SET bUploaded = 1 WHERE trackingNo = :trackingNo")
    void markUploadedByTrackingNo(String trackingNo);

    /**
     * 删除指定批次和司机的扫描记录
     */
    @Query("DELETE FROM scan_records WHERE scanBatchId = :batchId AND driverId = :driverId")
    void deleteByBatchId(long batchId, int driverId);
}
