package com.uniuni.SysMgrTool.dao;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

import com.uniuni.SysMgrTool.MySingleton;
import com.uniuni.SysMgrTool.bean.ScanOrder;

import java.util.Date;


//order id , tid , pick id, staff id,staff name, scanned time,
//scanned location,warehouse id,batch id, block id , iscommitted, committed date
@Entity(tableName = "scanned_record")
public class ScannedRecord {
    @PrimaryKey
    @NonNull
    public String tid;

    public Long orderId;
    public String orderSn;
    public Short pickId;
    public Short staffId;
    public Integer driverId;
    public String staffName;
    public Date scannedTime;
    public String scannedLocation;
    public Short  wareHouseId;
    public String batchId;
    public Integer blockId;
    public Short isCommitted = 0;
    public Date  committedDate;

    public void copyFrom(ScanOrder order)
    {
        orderId = order.getOrderId();
        tid     = order.getId();
        pickId  = order.getPackId();
        orderSn = order.getOrderSn();
        staffId = MySingleton.getInstance().getLoginInfo().loginId;
        driverId = order.getDriverId();
        staffName = MySingleton.getInstance().getLoginInfo().loginName;
        scannedTime = new Date();
        scannedLocation = MySingleton.getInstance().getLoginInfo().loginLocation;
        wareHouseId     = MySingleton.getInstance().getLoginInfo().warehouseId.shortValue();
        batchId         = MySingleton.getInstance().getProperty(MySingleton.ITEM_CURRENT_BATCH_ID);
        blockId         = 0;
    }
}