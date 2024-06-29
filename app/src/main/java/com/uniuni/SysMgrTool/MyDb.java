package com.uniuni.SysMgrTool;

import static com.uniuni.SysMgrTool.Task.MyHandler.MSG_LOADED_SCANNED_DATA;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;

import androidx.annotation.NonNull;
import androidx.room.Room;

import com.uniuni.SysMgrTool.Response.DeliveringListData;
import com.uniuni.SysMgrTool.bean.ScanOrder;
import com.uniuni.SysMgrTool.common.FileLog;
import com.uniuni.SysMgrTool.dao.AppDatabase;
import com.uniuni.SysMgrTool.dao.DeliveredPackagesDao;
import com.uniuni.SysMgrTool.dao.DeliveryInfo;
import com.uniuni.SysMgrTool.dao.DeliveryInfoDao;
import com.uniuni.SysMgrTool.dao.OrderIdRecord;
import com.uniuni.SysMgrTool.dao.OrderIdRecordDao;
import com.uniuni.SysMgrTool.dao.ScannedRecord;
import com.uniuni.SysMgrTool.dao.ScannedRecordDao;

import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Objects;

public class MyDb {

    public static final int MSG_SAVE_SCAN = 0;
    public static final int MSG_UPDATE_SCAN = 1;
    public static final int MSG_LOAD_DATA   = 2;
    public static final int MSG_SAVE_IDS = 3;
    public static final int MSG_SAVE_DELIVERY_INFO = 4;

    private ScannedRecordDao scannedRecordDao;

    private OrderIdRecordDao orderIdRecordDao;
    private DeliveryInfoDao deliveryInfoDao;
    private DeliveredPackagesDao deliveredPackagesDao;

    private Handler mHandler;

    public void saveScannedData(ScanOrder order)
    {
        Message m = Message.obtain();
        m.arg1    = MSG_SAVE_SCAN;
        m.arg2    = 0;
        m.obj = order;

        mHandler.sendMessage(m);
    }

    public void saveOrderIds(ScanOrder order)
    {
        Message m = Message.obtain();
        m.arg1   = MSG_SAVE_IDS;
        m.obj = order;

        mHandler.sendMessage(m);
    }

    public void updateScannedData(ScannedRecord order)
    {
        Message m = Message.obtain();
        m.arg1    = MSG_UPDATE_SCAN;
        m.obj     = order;

        mHandler.sendMessage(m);
    }


    public ScannedRecordDao getScannedRecordDao() {
        return scannedRecordDao;
    }

    //search by the last four characters
    public OrderIdRecord[] searchOrderIds(String key)
    {
        //
        Calendar   cal   =   Calendar.getInstance();
        cal.add(Calendar.DATE,   -21);
        Date d = cal.getTime();

        if (key == null || key.isEmpty())
            return  null;
        
        try {
            return orderIdRecordDao.findOrderId(key , d);
        }catch (Exception e)
        {
            e.printStackTrace();
        }
        return null;
    }

    public void initDb(Context cxt)
    {
        AppDatabase db = Room.databaseBuilder(cxt,
                AppDatabase.class, "uniuni").build();
        scannedRecordDao = db.getScannedRecordDao();
        orderIdRecordDao = db.getOrderIdRecordDao();
        deliveryInfoDao  = db.deliveryInfoDao();
        deliveredPackagesDao = db.deliveredPackagesDao();
    }

    public DeliveryInfoDao getDeliveryInfoDao() {
        return deliveryInfoDao;
    }

    public DeliveredPackagesDao getDeliveredPackagesDao() {
        return deliveredPackagesDao;
    }
}
