package com.hf.easydelivery;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;

import androidx.annotation.NonNull;
import androidx.room.Room;
import androidx.room.migration.Migration;
import androidx.sqlite.db.SupportSQLiteDatabase;

import com.hf.courierservice.apihelper.TaskBase;
import com.hf.easydelivery.dao.AppDatabase;
import com.hf.easydelivery.dao.DeliveredPackagesDao;
import com.hf.easydelivery.dao.DeliveryInfoDao;
import com.hf.easydelivery.dao.ScanRecordDao;
import com.hf.easydelivery.dao.DailyWorkStatsDao;

import java.util.Objects;

public class MyDb {
    private DeliveryInfoDao deliveryInfoDao;
    private DeliveredPackagesDao deliveredPackagesDao;
    private ScanRecordDao scanRecordDao;
    private DailyWorkStatsDao dailyWorkStatsDao;
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
                AppDatabase.class, "easydelivery")
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
                .build();

        deliveryInfoDao  = db.deliveryInfoDao();
        deliveredPackagesDao = db.deliveredPackagesDao();
        scanRecordDao = db.scanRecordDao();
        dailyWorkStatsDao = db.dailyWorkStatsDao();

        dbLooperThread.start();
    }

    private static final Migration MIGRATION_1_2 = new Migration(1, 2) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase database) {
            database.execSQL("ALTER TABLE delivered_packages ADD COLUMN deliveryResult INTEGER");
            database.execSQL("ALTER TABLE delivered_packages ADD COLUMN failedReason INTEGER");
            database.execSQL("ALTER TABLE delivered_packages ADD COLUMN recipientName TEXT");
        }
    };

    private static final Migration MIGRATION_2_3 = new Migration(2, 3) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase database) {
            database.execSQL("CREATE TABLE IF NOT EXISTS daily_work_stats (driverId INTEGER NOT NULL, statDate INTEGER NOT NULL, totalDistanceMeters REAL NOT NULL, lastUpdated INTEGER NOT NULL, PRIMARY KEY(driverId, statDate))");
        }
    };

    public DeliveryInfoDao getDeliveryInfoDao() {
        return deliveryInfoDao;
    }

    public DeliveredPackagesDao getDeliveredPackagesDao() {
        return deliveredPackagesDao;
    }

    public ScanRecordDao getScanRecordDao() {
        return scanRecordDao;
    }

    public DailyWorkStatsDao getDailyWorkStatsDao() {
        return dailyWorkStatsDao;
    }
}
