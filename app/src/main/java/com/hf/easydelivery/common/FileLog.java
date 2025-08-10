package com.hf.easydelivery.common;

import android.content.Context;
import android.util.Log;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.util.Date;

/**
 * 文件日志类，支持 info/debug/warning/error 四级日志，支持格式化字符串和 tag。
 * 日志文件格式为：时间 | LEVEL | TAG | 内容
 * 支持单例、初始化、关闭和 Android Studio 日志同步输出。
 * 仿照 iOS Logger 结构。
 */
public class FileLog {
    public final static String LOG_DIR_NAME = "logs";//\Environment.getExternalStorageDirectory() + "/PhoneData/";
    private final static String LOG_FILE_NAME = "sysmgrtool.log";

    private static final String DEFAULT_TAG = "FileLog";

    File mFile;
    RandomAccessFile mRaf;

    // 是否同步输出到终端（Logcat），默认开启
    private boolean logToConsole = true;

    /**
     * 设置是否同步输出到 Logcat。
     * @param enable 是否输出到 Logcat
     */
    public void setLogToConsole(boolean enable) {
        this.logToConsole = enable;
    }

    private static FileLog instance;

    /**
     * 获取 FileLog 单例对象。
     * @return FileLog 单例
     */
    public static synchronized FileLog getInstance()
    {
        if(instance == null) {
            instance = new FileLog();
        }
        return instance;
    }

    /**
     * 初始化日志文件。必须在使用前调用。
     * @param context Android 上下文
     * @return 是否初始化成功
     */
    public boolean init(Context context) {
        try {
            boolean b = false;
            File logDir = new File(context.getFilesDir(), LOG_DIR_NAME);
            if (!logDir.exists()) {
                b = logDir.mkdirs();
            }

            mFile = new File(logDir, LOG_FILE_NAME);
            try {
                if (!mFile.exists()) {
                    b = mFile.createNewFile();
                }
            } catch (IOException e) {
                Log.e("FileLog", "Failed to create log file", e);
            }


            if (!b)
            {
                File fDir = context.getExternalCacheDir();
                mFile = new File(fDir , LOG_FILE_NAME);
            }

            mRaf  = new RandomAccessFile(mFile, "rw");
            mRaf.seek(mFile.length());

            return true;
        } catch (Exception e) {
            Log.e("FileLog", "Failed to init log file", e);
        }

        return false;
    }

    /**
     * 写入日志（兼容旧接口，等价于 info(DEFAULT_TAG, content)）。
     * @param content 日志内容
     */
    public void writeLog(String content) {
        info(DEFAULT_TAG, content);
    }

    // ======================= 日志级别方法 =========================

    /**
     * 输出 info 级别日志。
     * @param tag 日志标签
     * @param msg 日志内容
     */
    public void info(String tag, String msg) {
        writeLogToFile("INFO", tag, msg);
        if (logToConsole) Log.i(tag, msg);
    }

    /**
     * 输出 info 级别格式化日志。
     * @param tag 日志标签
     * @param format 格式字符串
     * @param args 参数
     */
    public void info(String tag, String format, Object... args) {
        String msg = String.format(format, args);
        info(tag, msg);
    }

    /**
     * 输出 info 级别日志，使用默认 TAG。
     * @param msg 日志内容
     */
    public void info(String msg) {
        info(DEFAULT_TAG, msg);
    }

    /**
     * 输出 info 级别格式化日志，使用默认 TAG。
     * @param format 格式字符串
     * @param args 参数
     */
    public void info(String format, Object... args) {
        info(DEFAULT_TAG, format, args);
    }

    /**
     * 输出 debug 级别日志。
     * @param tag 日志标签
     * @param msg 日志内容
     */
    public void debug(String tag, String msg) {
        writeLogToFile("DEBUG", tag, msg);
        if (logToConsole) Log.d(tag, msg);
    }

    /**
     * 输出 debug 级别格式化日志。
     * @param tag 日志标签
     * @param format 格式字符串
     * @param args 参数
     */
    public void debug(String tag, String format, Object... args) {
        String msg = String.format(format, args);
        debug(tag, msg);
    }

    /**
     * 输出 debug 级别日志，使用默认 TAG。
     * @param msg 日志内容
     */
    public void debug(String msg) {
        debug(DEFAULT_TAG, msg);
    }

    /**
     * 输出 debug 级别格式化日志，使用默认 TAG。
     * @param format 格式字符串
     * @param args 参数
     */
    public void debug(String format, Object... args) {
        debug(DEFAULT_TAG, format, args);
    }

    /**
     * 输出 warning 级别日志。
     * @param tag 日志标签
     * @param msg 日志内容
     */
    public void warning(String tag, String msg) {
        writeLogToFile("WARNING", tag, msg);
        if (logToConsole) Log.w(tag, msg);
    }

    /**
     * 输出 warning 级别格式化日志。
     * @param tag 日志标签
     * @param format 格式字符串
     * @param args 参数
     */
    public void warning(String tag, String format, Object... args) {
        String msg = String.format(format, args);
        warning(tag, msg);
    }

    /**
     * 输出 warning 级别日志，使用默认 TAG。
     * @param msg 日志内容
     */
    public void warning(String msg) {
        warning(DEFAULT_TAG, msg);
    }

    /**
     * 输出 warning 级别格式化日志，使用默认 TAG。
     * @param format 格式字符串
     * @param args 参数
     */
    public void warning(String format, Object... args) {
        warning(DEFAULT_TAG, format, args);
    }

    /**
     * 输出 error 级别日志。
     * @param tag 日志标签
     * @param msg 日志内容
     */
    public void error(String tag, String msg) {
        writeLogToFile("ERROR", tag, msg);
        if (logToConsole) Log.e(tag, msg);
    }

    /**
     * 输出 error 级别格式化日志。
     * @param tag 日志标签
     * @param format 格式字符串
     * @param args 参数
     */
    public void error(String tag, String format, Object... args) {
        String msg = String.format(format, args);
        error(tag, msg);
    }

    /**
     * 输出 error 级别日志，使用默认 TAG。
     * @param msg 日志内容
     */
    public void error(String msg) {
        error(DEFAULT_TAG, msg);
    }

    /**
     * 输出 error 级别格式化日志，使用默认 TAG。
     * @param format 格式字符串
     * @param args 参数
     */
    public void error(String format, Object... args) {
        error(DEFAULT_TAG, format, args);
    }

    // ======================= 写入日志文件 =========================

    /**
     * 写入日志到文件，格式：时间 | LEVEL | TAG | 内容
     * @param level 日志级别
     * @param tag 日志标签
     * @param content 日志内容
     */
    private void writeLogToFile(String level, String tag, String content) {
        if (content == null || content.isEmpty())
            return;
        Date d = new Date();
        String strContent = String.format("%tF %tT | %s | %s | %s\n", d, d, level, tag == null ? DEFAULT_TAG : tag, content);
        try {
            if (mRaf != null) {
                mRaf.write(strContent.getBytes());
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * 删除指定路径的日志文件。
     * @param path 日志文件路径
     */
    public void delLogFile(String path) {
        File file = new File(path);
        if (file.exists()) {
            file.delete();
        }
    }

    /**
     * 关闭日志文件，释放资源。
     */
    public void close()
    {
        try {
            if (mRaf != null)
                mRaf.close();
            mFile = null;
            mRaf = null;
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
