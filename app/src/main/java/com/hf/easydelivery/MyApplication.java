package com.hf.easydelivery;

import android.app.Application;
import android.content.Context;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.os.Build;
import android.os.LocaleList;
import androidx.preference.PreferenceManager;
import com.hf.courierservice.apihelper.FileLog;

import java.util.Locale;

import com.hf.easydelivery.service.FocusStateRepository;
import com.hf.easydelivery.service.LockScreenFocusController;
import com.hf.easydelivery.telemetry.Telemetry;
import com.hf.easydelivery.telemetry.TelemetryConfig;
import android.content.pm.ApplicationInfo;

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

        boolean debuggable = (getApplicationInfo().flags & ApplicationInfo.FLAG_DEBUGGABLE) != 0;
        String defaultLogLevel = "INFO";
        String configuredLogLevel = PreferenceManager.getDefaultSharedPreferences(ctx)
                .getString("log_level", defaultLogLevel);
        FileLog.getInstance().setMinLogLevel(configuredLogLevel);

        TelemetryConfig telemetryConfig = debuggable
                ? TelemetryConfig.debugDefaults()
                : TelemetryConfig.disabled();
        Telemetry.init(telemetryConfig);

        // Lockscreen focus pipeline init (lightweight; no location subscriptions)
        FocusStateRepository.init(ctx);
        LockScreenFocusController.init(ctx);
    }

    @Override
    public void onTerminate() {
        super.onTerminate();
        ResourceMgr.getInstance().shutdownExecutorService();
    }

    private void setLocale(Context c) {
        boolean bCn = ResourceMgr.getInstance().getBooleanProperty("switch_cn");
        Locale locale;
        if (!bCn)
        {
            locale = new Locale("en");
        }
        else
            locale = new Locale("zh", "CN");

        Locale.setDefault(locale);
        Resources res = c.getResources();
        Configuration config = new Configuration(res.getConfiguration());

        if (Build.VERSION.SDK_INT >= 17) {
            config.setLocale(locale);
            c = c.createConfigurationContext(config);
        } else {
            config.locale = locale;
            res.updateConfiguration(config, res.getDisplayMetrics());
        }
    }
}
