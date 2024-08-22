package com.hf.easydelivery;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;

import androidx.annotation.NonNull;
import androidx.room.Room;

import com.hf.courierservice.apihelper.TaskBase;
import com.hf.easydelivery.dao.AppDatabase;
import com.hf.easydelivery.dao.DeliveredPackagesDao;
import com.hf.easydelivery.dao.DeliveryInfoDao;

import java.util.Objects;

public class MyDb {
    private DeliveryInfoDao deliveryInfoDao;
    private DeliveredPackagesDao deliveredPackagesDao;
    private Handler mHandler;

    public Handler getHandler() {
        return mHandler;
    }

    private final Thread dbLooperThread = new Thread() {
        public void run() {
            Looper.prepare();
            final Looper mLooper = Looper.myLooper();

            mHandler = new Handler(Objects.requireNonNull(mLooper)) {
                public void handleMessage(@NonNull Message msg) {
                    super.handleMessage(msg);

                    TaskBase task = (TaskBase)msg.obj;
                    task.doIt(null);
                }
            };
            Looper.loop();
        }
    };

    public void initDb(Context cxt)
    {
        AppDatabase db = Room.databaseBuilder(cxt,
                AppDatabase.class, "easydelivery").build();

        deliveryInfoDao  = db.deliveryInfoDao();
        deliveredPackagesDao = db.deliveredPackagesDao();

        dbLooperThread.start();
    }

    public DeliveryInfoDao getDeliveryInfoDao() {
        return deliveryInfoDao;
    }

    public DeliveredPackagesDao getDeliveredPackagesDao() {
        return deliveredPackagesDao;
    }
}
