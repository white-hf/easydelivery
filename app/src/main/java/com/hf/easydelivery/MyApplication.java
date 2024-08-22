package com.hf.easydelivery;

import android.app.Application;
import android.content.Context;

public class MyApplication extends Application {

    public MyApplication() {

    }

    @Override
    public void onCreate() {
        super.onCreate();
        Context ctx = this.getApplicationContext();

        CrashHandler handler = CrashHandler.getInstance();
        handler.init(this);
        ResourceMgr.getInstance().init(ctx);
    }

    @Override
    public void onTerminate() {
        super.onTerminate();
        ResourceMgr.getInstance().shutdownExecutorService();
    }
}
