package com.hf.easydelivery;

import static com.hf.easydelivery.Constants.ITEM_CURRENT_BATCH_ID;

import android.content.Context;
import android.content.SharedPreferences;

import android.os.Handler;
import android.os.Looper;
import android.util.Log;


import androidx.preference.PreferenceManager;
import com.android.volley.RequestQueue;

import com.android.volley.toolbox.Volley;
import com.hf.courierservice.ICourierService;
import com.hf.easydelivery.event.Publisher;
import com.hf.easydelivery.api.CourierServiceFactory;
import com.hf.easydelivery.common.ConfigurationManager;
import com.hf.easydelivery.common.FileLog;
import com.hf.easydelivery.common.Utils;

import com.hf.easydelivery.core.PendingPackagesMgr;
import com.hf.easydelivery.core.DeliveryinfoMgr;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;

public class ResourceMgr {

    private static ResourceMgr instance;

    private com.hf.easydelivery.event.Publisher publisher;
    private PendingPackagesMgr mPendingPackagesMgr;

    private RequestQueue requestQueue;

    private Context ctx;

    public static final String TAG = "easydelivery";

    private MyDb mMydb;
    private LoginInfo mLoginInfo = new LoginInfo();

    private DeliveryinfoMgr mDeliveryinfoMgr;
    private ConfigurationManager mConfigurationManager;
    private ICourierService mCourierService;
    private Handler mMainHandler;

    static public class LoginInfo {
        public String loginName = "0";
        public Short loginId = 0;
        public String userToken = null;
        public String loginLocation = "Halifax Warehouse";
        public Integer warehouseId = 17;
        public boolean bIsLoggedIn = false;
    }

    public Handler getMainHandler() {
        return mMainHandler;
    }

    public DeliveryinfoMgr getDeliveryinfoMgr() {
        return mDeliveryinfoMgr;
    }

    public PendingPackagesMgr getPendingPackagesMgr() {
        return mPendingPackagesMgr;
    }
    public ICourierService getCourierService() {
        return mCourierService;
    }

    public ResourceMgr() {
    }

    public Context getCtx() {
        return ctx;
    }
    public RequestQueue getRequestQueue() {
        return requestQueue;
    }
    public ConfigurationManager getConfigurationManager() {
        return mConfigurationManager;
    }

    public void init(Context ctx) {
        this.ctx = ctx;

        FileLog.getInstance().init(ctx);
        requestQueue = Volley.newRequestQueue(ctx);;

        publisher = new Publisher();
        mMydb = new MyDb();
        mMydb.initDb(ctx);

        mDeliveryinfoMgr = new DeliveryinfoMgr();
        mPendingPackagesMgr = new PendingPackagesMgr();

        mConfigurationManager = new ConfigurationManager(ctx , "config.json");
        mCourierService = CourierServiceFactory.createCourierService();
        mCourierService.init(ctx);

        mMainHandler = new Handler(Looper.getMainLooper());
        setBatchId();
    }

    public Handler getDbHandler() {
        return mMydb.getHandler();
    }

    public MyDb getmMydb() {
        return mMydb;
    }

    public void shutdownExecutorService() {
        final ExecutorService executorService = mPendingPackagesMgr.getExecutorService();
        if (executorService != null) {
            executorService.shutdown();
            try {
                if (!executorService.awaitTermination(60, TimeUnit.SECONDS)) {
                    executorService.shutdownNow();
                    if (!executorService.awaitTermination(60, TimeUnit.SECONDS)) {
                        System.err.println("ExecutorService did not terminate");
                    }
                }
            } catch (InterruptedException ie) {
                executorService.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }

        FileLog.getInstance().close();
    }

    public Publisher getPublisher() {
        return publisher;
    }

    public LoginInfo getLoginInfo() {
        return mLoginInfo;
    }

    public String getProperty(String item) {
        SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(ctx);
        return settings.getString(item, "");
    }

    private void setBatchId() {
        SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(ctx);
        String batchId = String.format("HASUB-%s", Utils.getCurrentDate());
        settings.edit().putString(ITEM_CURRENT_BATCH_ID, batchId).apply();
    }

    public boolean getBooleanProperty(String item) {
        SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(ctx);
        return settings.getBoolean(item, false);
    }

    public Integer getIntProperty(String item) {
        Integer de = 0;
        if (item == null || item.isEmpty())
            return de;

        SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(ctx);
        try {
            String s = settings.getString(item, "");
            return Integer.valueOf(s);
        } catch (Exception e) {
            Log.e(TAG, e.getMessage());
            return de;
        }
    }


    public static synchronized ResourceMgr getInstance() {
        if (instance == null) {
            instance = new ResourceMgr();
        }

        return instance;
    }
}

