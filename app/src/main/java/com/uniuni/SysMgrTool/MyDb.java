package com.uniuni.SysMgrTool;

import static com.uniuni.SysMgrTool.Task.MyHandler.MSG_LOADED_SCANNED_DATA;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;

import androidx.room.Room;

import com.uniuni.SysMgrTool.Response.DeliveringListData;
import com.uniuni.SysMgrTool.bean.ScanOrder;
import com.uniuni.SysMgrTool.common.FileLog;
import com.uniuni.SysMgrTool.dao.AppDatabase;
import com.uniuni.SysMgrTool.dao.DeliveryInfo;
import com.uniuni.SysMgrTool.dao.DeliveryInfoDao;
import com.uniuni.SysMgrTool.dao.OrderIdRecord;
import com.uniuni.SysMgrTool.dao.OrderIdRecordDao;
import com.uniuni.SysMgrTool.dao.ScannedRecord;
import com.uniuni.SysMgrTool.dao.ScannedRecordDao;

import java.util.Calendar;
import java.util.Date;
import java.util.List;

public class MyDb {

    public static final int MSG_SAVE_SCAN = 0;
    public static final int MSG_UPDATE_SCAN = 1;
    public static final int MSG_LOAD_DATA   = 2;
    public static final int MSG_SAVE_IDS = 3;
    public static final int MSG_SAVE_DELIVERY_INFO = 4;

    private ScannedRecordDao scannedRecordDao;

    private OrderIdRecordDao orderIdRecordDao;
    private DeliveryInfoDao deliveryInfoDao;

    public OrderIdRecordDao getOrderIdRecordDao() {
        return orderIdRecordDao;
    }

    public Handler getHandler() {
        return mHandler;
    }

    private Handler mHandler;

    private Thread LooperThread = new Thread ()
    {
        private Looper mLooper = Looper.myLooper();
        public void run() {
            Looper.prepare();

            mHandler = new Handler(Looper.myLooper()) {
                private String batchId;
                private int driverId;
                public void handleMessage(Message msg) {
                    try {
                        driverId   = MySingleton.getInstance().getIntProperty(MySingleton.ITEM_DRIVER_ID);
                        batchId = MySingleton.getInstance().getProperty(MySingleton.ITEM_CURRENT_BATCH_ID);
                        // process incoming messages here
                        if (msg.arg1 == MSG_SAVE_SCAN) {
                            ScanOrder order = (ScanOrder) msg.obj;
                            ScannedRecord r = new ScannedRecord();
                            r.copyFrom(order);
                            scannedRecordDao.addRecord(r);
                        } else if (msg.arg1 == MSG_UPDATE_SCAN) {
                            ScannedRecord r = (ScannedRecord) msg.obj;
                            scannedRecordDao.updateRecord(r);
                        } else if (MSG_LOAD_DATA == msg.arg1) {
                            FileLog.getInstance().writeLog(String.format("Before loadUnCommittedRecords,batchId:%s,driverId:%d",batchId,driverId));
                            List<ScannedRecord> lst = (List<ScannedRecord>) msg.obj;

                            ScannedRecord[] records;
                            if (driverId == 9999)
                                records = scannedRecordDao.loadAll();
                            else
                                records = scannedRecordDao.loadUnCommittedRecords(batchId, driverId);

                            if (records == null || records.length == 0)
                                return;

                            for (ScannedRecord r : records) {
                                lst.add(r);
                            }

                            notifyScannedDataLoaded(lst.size());

                        } else if (MSG_SAVE_IDS == msg.arg1) {
                            ScanOrder order = (ScanOrder) msg.obj;
                            OrderIdRecord r = new OrderIdRecord();
                            r.tid = order.getId();
                            r.createDate = new Date();
                            if (r.tid.equals("JY2332100002008212"))
                                System.out.println("ok");

                            orderIdRecordDao.addRecord(r);
                        }else if (MSG_SAVE_DELIVERY_INFO == msg.arg1) {
                            DeliveringListData d = (DeliveringListData) msg.obj;
                            saveDeliveringListData(d);
                        }
                        else if (ServerInterface.RESPONSE_GET_ORDER_LIST == msg.arg1) {
                            MySingleton.getInstance().saveOrderIds();
                        }
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
            };

            Looper.loop();
        }
    };

    public void saveScannedData(ScanOrder order)
    {
        Message m = Message.obtain();
        m.arg1    = MSG_SAVE_SCAN;
        m.arg2    = 0;
        m.obj = order;

        mHandler.sendMessage(m);
    }

    private void notifyScannedDataLoaded(int arg2)
    {
        Message m = Message.obtain();
        m.arg1    = MSG_LOADED_SCANNED_DATA;
        m.arg2    = arg2;

        MySingleton.getInstance().sendMessage(m);
    }

    public void saveOrderIds(ScanOrder order)
    {
        Message m = Message.obtain();
        m.arg1   = MSG_SAVE_IDS;
        m.obj = order;

        mHandler.sendMessage(m);
    }

    public void saveDeliveringListData(DeliveringListData d)
    {
        String batchId = MySingleton.getInstance().getProperty(MySingleton.ITEM_CURRENT_BATCH_ID);

        DeliveryInfo info = new DeliveryInfo();
        info.setRouteNumber(String.valueOf(d.getRoute_no()));
        info.setLatitude(Double.valueOf(d.getLat()));
        info.setLongitude(Double.valueOf(d.getLng()));
        info.setAddress(d.getAddress());
        info.setName(d.getName());
        info.setPhone(d.getMobile());
        info.setUnitNumber(d.getUnit_number());
        info.setBatchNumber(batchId);

        deliveryInfoDao.insert(info);
    }

    public void sendSaveDeliveryInfoMsg(DeliveringListData info)
    {
        Message m = Message.obtain();
        m.arg1    = MSG_SAVE_DELIVERY_INFO;
        m.obj     = info;

        mHandler.sendMessage(m);
    }

    public void updateScannedData(ScannedRecord order)
    {
        Message m = Message.obtain();
        m.arg1    = MSG_UPDATE_SCAN;
        m.obj     = order;

        mHandler.sendMessage(m);
    }

    public void loadScannedData(Object o)
    {
        Message m = Message.obtain();
        m.arg1    = MSG_LOAD_DATA;
        m.obj = o;

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
        LooperThread.start();
    }

    public boolean loadDeliveryInfoFromDb()
    {
        if (!MySingleton.getInstance().isLoadDeliveryInfo())
        {
            return true;
        }

        String batchId = MySingleton.getInstance().getProperty(MySingleton.ITEM_CURRENT_BATCH_ID);
        Integer driverId = MySingleton.getInstance().getIntProperty(MySingleton.ITEM_DRIVER_ID);

        if (batchId == null || batchId.isEmpty() || driverId == null || driverId < 1) {
            return false;
        }

        new Thread(new Runnable() {
            @Override
            public void run() {
                List<DeliveryInfo>  records = deliveryInfoDao.findByBatchNumber(batchId);

                if (records == null || records.isEmpty())
                    return;

                for (DeliveryInfo r : records) {
                    MySingleton.getInstance().addDeliveryInfo(r);
                }

            }
        }).start();

        return true;
    }
}
