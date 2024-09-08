package com.hf.easydelivery;

import android.app.Application;
import android.content.Context;
import android.content.res.Configuration;
import android.os.LocaleList;

import java.util.Locale;

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

        setLocale(ctx);
    }

    @Override
    public void onTerminate() {
        super.onTerminate();
        ResourceMgr.getInstance().shutdownExecutorService();
    }

    private void setLocale(Context c) {
        boolean bCn = ResourceMgr.getInstance().getBooleanProperty("switch_cn");
        LocaleList locales;
        if (!bCn)
        {
            locales = new LocaleList(Locale.ENGLISH);
        }
        else
            locales = new LocaleList(Locale.CHINESE);

        Configuration configuration = getResources().getConfiguration();

        configuration.setLocales(locales);
        Context newContext = createConfigurationContext(configuration);
        getResources().updateConfiguration(configuration, newContext.getResources().getDisplayMetrics());
    }
}
