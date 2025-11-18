package com.hf.easydelivery;

import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.Build;

import androidx.annotation.NonNull;

import com.hf.courierservice.apihelper.FileLog;

import java.io.File;
import java.io.FileWriter;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.io.Writer;
import java.lang.reflect.Field;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

public class CrashHandler implements Thread.UncaughtExceptionHandler {

    private static final String TAG = "CrashHandler";
    private static volatile CrashHandler instance;
    private Context mContext;
    private Thread.UncaughtExceptionHandler mDefaultHandler;
    private final Map<String, String> mDeviceInfo = new HashMap<>();

    private CrashHandler() {}

    public static CrashHandler getInstance() {
        if (instance == null) {
            synchronized (CrashHandler.class) {
                if (instance == null) {
                    instance = new CrashHandler();
                }
            }
        }
        return instance;
    }

    public void init(Context ctx) {
        mContext = ctx.getApplicationContext();
        // 获取系统默认的UncaughtException处理器
        mDefaultHandler = Thread.getDefaultUncaughtExceptionHandler();
        // 设置该CrashHandler为程序的默认处理器
        Thread.setDefaultUncaughtExceptionHandler(this);
    }

    @Override
    public void uncaughtException(@NonNull Thread thread, @NonNull Throwable ex) {
        boolean handled = handleException(ex);
        if (!handled && mDefaultHandler != null) {
            // 如果我们没有处理则让系统默认的异常处理器来处理
            mDefaultHandler.uncaughtException(thread, ex);
        } else {
            // 稍作延迟，确保日志写入完成
            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                FileLog.getInstance().error(TAG, "Error during sleep", e);
            }
            // 退出程序
            android.os.Process.killProcess(android.os.Process.myPid());
            System.exit(1);
        }
    }

    private boolean handleException(Throwable ex) {
        if (ex == null) {
            return false;
        }
        // 收集设备参数信息
        collectDeviceInfo(mContext);
        // 保存日志文件
        saveCrashInfoToFile(ex);
        return true;
    }

    public void collectDeviceInfo(Context ctx) {
        try {
            PackageManager pm = ctx.getPackageManager();
            PackageInfo pi = pm.getPackageInfo(ctx.getPackageName(), PackageManager.GET_ACTIVITIES);
            if (pi != null) {
                String versionName = pi.versionName == null ? "null" : pi.versionName;
                String versionCode = String.valueOf(pi.versionCode);
                mDeviceInfo.put("versionName", versionName);
                mDeviceInfo.put("versionCode", versionCode);
            }
        } catch (PackageManager.NameNotFoundException e) {
            FileLog.getInstance().error(TAG, "An error occurred while collecting package info", e);
        }

        Field[] fields = Build.class.getDeclaredFields();
        for (Field field : fields) {
            try {
                field.setAccessible(true);
                mDeviceInfo.put(field.getName(), String.valueOf(field.get(null)));
            } catch (Exception e) {
                FileLog.getInstance().error(TAG, "An error occurred while collecting crash info", e);
            }
        }
    }

    private void saveCrashInfoToFile(Throwable ex) {
        StringBuilder sb = new StringBuilder();
        
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault());
        sb.append("Crash Time: ").append(sdf.format(new Date())).append("\n");

        for (Map.Entry<String, String> entry : mDeviceInfo.entrySet()) {
            String key = entry.getKey();
            String value = entry.getValue();
            sb.append(key).append(" = ").append(value).append("\n");
        }
        sb.append("\n-------------------- Stack Trace --------------------\n\n");

        Writer writer = new StringWriter();
        PrintWriter printWriter = new PrintWriter(writer);
        ex.printStackTrace(printWriter);
        Throwable cause = ex.getCause();
        while (cause != null) {
            cause.printStackTrace(printWriter);
            cause = cause.getCause();
        }
        printWriter.close();
        String result = writer.toString();
        sb.append(result);

        FileLog.getInstance().writeLog(sb.toString());
        writeCrashToExternal(sb.toString());
    }

    private void writeCrashToExternal(String logContent) {
        if (mContext == null) return;
        try {
            File dir = mContext.getExternalFilesDir("crash_logs");
            if (dir == null) {
                dir = new File(mContext.getExternalFilesDir(null), "crash_logs");
            }
            if (dir != null && (dir.exists() || dir.mkdirs())) {
                String filename = "crash_" + System.currentTimeMillis() + ".log";
                File file = new File(dir, filename);
                FileWriter writer = new FileWriter(file, false);
                writer.write(logContent);
                writer.flush();
                writer.close();
            }
        } catch (Exception e) {
            FileLog.getInstance().error(TAG, "Failed to write crash log to external storage", e);
        }
    }
}
