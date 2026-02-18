package com.hf.courierservice.apihelper;

import android.content.Context;
import android.util.Log;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.charset.StandardCharsets;
import java.util.Date;

import android.content.ContentResolver;
import android.content.ContentValues;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.os.ParcelFileDescriptor;
import android.provider.MediaStore;
import java.io.OutputStream;
import java.io.BufferedOutputStream;
import java.io.OutputStreamWriter;
import java.io.BufferedWriter;
import java.io.Writer;
import android.content.SharedPreferences;
import androidx.core.content.FileProvider;

/**
 * 文件日志类，支持 info/debug/warning/error 四级日志，支持格式化字符串和 tag。
 * 日志文件格式为：时间 | LEVEL | TAG | 内容
 * 支持单例、初始化、关闭和 Android Studio 日志同步输出。
 * 仿照 iOS Logger 结构。
 */
public class FileLog {
    // We now write logs to the public Downloads collection via MediaStore (Android
    // 10+),
    // falling back to app-private files for older OS or failure cases.
    public final static String LOG_DISPLAY_NAME = "easydelivery.log"; // filename shown in Downloads
    private static final String PREFS_NAME = "filelog_prefs";
    private static final String PREF_KEY_URI = "downloads_log_uri";
    private static final String PREF_KEY_MIN_LEVEL = "min_log_level";
    private static final long MAX_LOG_BYTES = 10 * 1024 * 1024L; // 10 MB

    private static final String DEFAULT_TAG = "FileLog";

    // App context
    private Context appCtx;

    // MediaStore path (preferred on Android 10+)
    private Uri mLogUri;
    private OutputStream mOs; // append output stream
    private Writer mWriter;
    private long currentSizeBytes = 0L;

    // Legacy fallback
    File mFile;
    RandomAccessFile mRaf;

    // 是否同步输出到终端（Logcat），默认开启
    private boolean logToConsole = true;
    private volatile int minLogLevel = LEVEL_DEBUG;

    public static final int LEVEL_DEBUG = 10;
    public static final int LEVEL_INFO = 20;
    public static final int LEVEL_WARNING = 30;
    public static final int LEVEL_ERROR = 40;
    public static final int LEVEL_NONE = 100;

    /**
     * 设置是否同步输出到 Logcat。
     * 
     * @param enable 是否输出到 Logcat
     */
    public void setLogToConsole(boolean enable) {
        this.logToConsole = enable;
    }

    private static FileLog instance;

    /**
     * 获取 FileLog 单例对象。
     * 
     * @return FileLog 单例
     */
    public static synchronized FileLog getInstance() {
        if (instance == null) {
            instance = new FileLog();
        }
        return instance;
    }

    /**
     * 初始化日志文件。必须在使用前调用。
     * 
     * @param context Android 上下文
     * @return 是否初始化成功
     */
    public boolean init(Context context) {
        appCtx = context.getApplicationContext();
        restoreMinLogLevel();
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                // Try MediaStore Downloads first
                mLogUri = restoreOrCreateDownloadsUri(appCtx);
                if (mLogUri != null) {
                    try {
                        // "wa" = write-append
                        mOs = appCtx.getContentResolver().openOutputStream(mLogUri, "wa");
                        if (mOs != null) {
                            mWriter = new BufferedWriter(new OutputStreamWriter(new BufferedOutputStream(mOs)));
                            currentSizeBytes = queryMediaStoreSize();
                            return true;
                        }
                    } catch (Exception e) {
                        Log.e("FileLog", "openOutputStream failed, fallback to legacy", e);
                    }
                }
            }
            // Fallback: app-private file
            File logDir = new File(appCtx.getFilesDir(), "logs");
            if (!logDir.exists())
                logDir.mkdirs();
            mFile = new File(logDir, LOG_DISPLAY_NAME);
            if (!mFile.exists())
                mFile.createNewFile();
            mRaf = new RandomAccessFile(mFile, "rw");
            mRaf.seek(mFile.length());
            currentSizeBytes = mFile.length();
            return true;
        } catch (Exception e) {
            Log.e("FileLog", "Failed to init log file", e);
            return false;
        }
    }

    private void restoreMinLogLevel() {
        if (appCtx == null) {
            return;
        }
        SharedPreferences sp = appCtx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        String configured = sp.getString(PREF_KEY_MIN_LEVEL, null);
        if (configured == null || configured.trim().isEmpty()) {
            minLogLevel = LEVEL_INFO;
            return;
        }
        minLogLevel = parseLevel(configured, LEVEL_INFO);
    }

    public void setMinLogLevel(int level) {
        minLogLevel = normalizeLevel(level);
        persistMinLogLevel();
    }

    public void setMinLogLevel(String levelName) {
        minLogLevel = parseLevel(levelName, minLogLevel);
        persistMinLogLevel();
    }

    public int getMinLogLevel() {
        return minLogLevel;
    }

    private int normalizeLevel(int level) {
        if (level <= LEVEL_DEBUG) return LEVEL_DEBUG;
        if (level <= LEVEL_INFO) return LEVEL_INFO;
        if (level <= LEVEL_WARNING) return LEVEL_WARNING;
        if (level <= LEVEL_ERROR) return LEVEL_ERROR;
        return LEVEL_NONE;
    }

    private int parseLevel(String levelName, int fallback) {
        if (levelName == null) {
            return fallback;
        }
        String normalized = levelName.trim().toUpperCase();
        switch (normalized) {
            case "DEBUG":
                return LEVEL_DEBUG;
            case "INFO":
                return LEVEL_INFO;
            case "WARN":
            case "WARNING":
                return LEVEL_WARNING;
            case "ERROR":
                return LEVEL_ERROR;
            case "NONE":
            case "OFF":
                return LEVEL_NONE;
            default:
                return fallback;
        }
    }

    private void persistMinLogLevel() {
        if (appCtx == null) {
            return;
        }
        try {
            SharedPreferences sp = appCtx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
            sp.edit().putString(PREF_KEY_MIN_LEVEL, levelToName(minLogLevel)).apply();
        } catch (Exception ignore) {
        }
    }

    private String levelToName(int level) {
        if (level <= LEVEL_DEBUG) return "DEBUG";
        if (level <= LEVEL_INFO) return "INFO";
        if (level <= LEVEL_WARNING) return "WARNING";
        if (level <= LEVEL_ERROR) return "ERROR";
        return "NONE";
    }

    private boolean shouldLog(int level) {
        return level >= minLogLevel;
    }

    private Uri restoreOrCreateDownloadsUri(Context ctx) {
        try {
            SharedPreferences sp = ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
            String saved = sp.getString(PREF_KEY_URI, null);
            ContentResolver cr = ctx.getContentResolver();
            if (saved != null) {
                Uri u = Uri.parse(saved);
                // sanity check: can we open it?
                try (OutputStream test = cr.openOutputStream(u, "wa")) {
                    if (test != null)
                        return u;
                } catch (Exception ignore) {
                }
            }
            // query existing by DISPLAY_NAME in Downloads
            Uri collection = MediaStore.Downloads.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY);
            String sel = MediaStore.MediaColumns.DISPLAY_NAME + "=?";
            String[] selArgs = new String[] { LOG_DISPLAY_NAME };
            try (android.database.Cursor c = cr.query(collection, new String[] { MediaStore.MediaColumns._ID }, sel,
                    selArgs, null)) {
                if (c != null && c.moveToFirst()) {
                    long id = c.getLong(0);
                    Uri existing = Uri.withAppendedPath(collection, String.valueOf(id));
                    sp.edit().putString(PREF_KEY_URI, existing.toString()).apply();
                    return existing;
                }
            }
            // not found -> create
            ContentValues values = new ContentValues();
            values.put(MediaStore.MediaColumns.DISPLAY_NAME, LOG_DISPLAY_NAME);
            values.put(MediaStore.MediaColumns.MIME_TYPE, "text/plain");
            values.put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS);
            Uri created = cr.insert(collection, values);
            if (created != null) {
                sp.edit().putString(PREF_KEY_URI, created.toString()).apply();
            }
            return created;
        } catch (Exception e) {
            Log.e("FileLog", "restoreOrCreateDownloadsUri failed", e);
            return null;
        }
    }

    private void writeLine(String line) throws IOException {
        if (line == null)
            return;
        byte[] data = line.getBytes(StandardCharsets.UTF_8);
        rotateIfNeeded(data.length);
        if (mWriter != null) {
            mWriter.write(line);
            mWriter.flush();
        } else if (mRaf != null) {
            mRaf.write(data);
        }
        currentSizeBytes += data.length;
    }

    private long queryMediaStoreSize() {
        if (mLogUri == null || appCtx == null)
            return 0L;
        try (android.database.Cursor c = appCtx.getContentResolver().query(mLogUri,
                new String[] { MediaStore.MediaColumns.SIZE }, null, null, null)) {
            if (c != null && c.moveToFirst()) {
                return c.getLong(0);
            }
        } catch (Exception ignore) {
        }
        return 0L;
    }

    private void rotateIfNeeded(int nextBytes) throws IOException {
        if (MAX_LOG_BYTES <= 0)
            return;
        if (currentSizeBytes + nextBytes <= MAX_LOG_BYTES)
            return;
        if (mWriter != null && mLogUri != null) {
            resetMediaStoreStream();
        } else if (mRaf != null) {
            mRaf.setLength(0);
            mRaf.seek(0);
            currentSizeBytes = 0L;
        }
    }

    private void resetMediaStoreStream() throws IOException {
        closeCurrentWriter();
        if (appCtx == null || mLogUri == null)
            return;
        ParcelFileDescriptor pfd = appCtx.getContentResolver().openFileDescriptor(mLogUri, "rw");
        if (pfd == null)
            return;
        FileOutputStream fos = new ParcelFileDescriptor.AutoCloseOutputStream(pfd);
        fos.getChannel().truncate(0);
        fos.getChannel().position(0);
        mOs = fos;
        mWriter = new BufferedWriter(new OutputStreamWriter(new BufferedOutputStream(mOs)));
        currentSizeBytes = 0L;
    }

    private void closeCurrentWriter() {
        try {
            if (mWriter != null) {
                mWriter.close();
            }
        } catch (IOException ignore) {
        }
        try {
            if (mOs != null) {
                mOs.close();
            }
        } catch (IOException ignore) {
        }
        mWriter = null;
        mOs = null;
    }

    /**
     * 写入日志（兼容旧接口，等价于 info(DEFAULT_TAG, content)）。
     * 
     * @param content 日志内容
     */
    public void writeLog(String content) {
        info(DEFAULT_TAG, content);
    }

    // ======================= 日志级别方法 =========================

    /**
     * 输出 info 级别日志。
     * 
     * @param tag 日志标签
     * @param msg 日志内容
     */
    public void info(String tag, String msg) {
        if (!shouldLog(LEVEL_INFO)) return;
        writeLogToFile("INFO", tag, msg);
        if (logToConsole)
            Log.i(tag, msg);
    }

    /**
     * 输出 info 级别格式化日志。
     * 
     * @param tag    日志标签
     * @param format 格式字符串
     * @param args   参数
     */
    public void info(String tag, String format, Object... args) {
        String msg = String.format(format, args);
        info(tag, msg);
    }

    /**
     * 输出 info 级别日志，使用默认 TAG。
     * 
     * @param msg 日志内容
     */
    public void info(String msg) {
        info(DEFAULT_TAG, msg);
    }

    /**
     * 输出 info 级别格式化日志，使用默认 TAG。
     * 
     * @param format 格式字符串
     * @param args   参数
     */
    public void info(String format, Object... args) {
        info(DEFAULT_TAG, format, args);
    }

    /**
     * 输出 debug 级别日志。
     * 
     * @param tag 日志标签
     * @param msg 日志内容
     */
    public void debug(String tag, String msg) {
        if (!shouldLog(LEVEL_DEBUG)) return;
        writeLogToFile("DEBUG", tag, msg);
        if (logToConsole)
            Log.d(tag, msg);
    }

    /**
     * 输出 debug 级别格式化日志。
     * 
     * @param tag    日志标签
     * @param format 格式字符串
     * @param args   参数
     */
    public void debug(String tag, String format, Object... args) {
        String msg = String.format(format, args);
        debug(tag, msg);
    }

    /**
     * 输出 debug 级别日志，使用默认 TAG。
     * 
     * @param msg 日志内容
     */
    public void debug(String msg) {
        debug(DEFAULT_TAG, msg);
    }

    /**
     * 输出 debug 级别格式化日志，使用默认 TAG。
     * 
     * @param format 格式字符串
     * @param args   参数
     */
    public void debug(String format, Object... args) {
        debug(DEFAULT_TAG, format, args);
    }

    /**
     * 输出 warning 级别日志。
     * 
     * @param tag 日志标签
     * @param msg 日志内容
     */
    public void warning(String tag, String msg) {
        if (!shouldLog(LEVEL_WARNING)) return;
        writeLogToFile("WARNING", tag, msg);
        if (logToConsole)
            Log.w(tag, msg);
    }

    /**
     * 输出 warning 级别格式化日志。
     * 
     * @param tag    日志标签
     * @param format 格式字符串
     * @param args   参数
     */
    public void warning(String tag, String format, Object... args) {
        String msg = String.format(format, args);
        warning(tag, msg);
    }

    /**
     * 输出 warning 级别日志，使用默认 TAG。
     * 
     * @param msg 日志内容
     */
    public void warning(String msg) {
        warning(DEFAULT_TAG, msg);
    }

    /**
     * 输出 warning 级别格式化日志，使用默认 TAG。
     * 
     * @param format 格式字符串
     * @param args   参数
     */
    public void warning(String format, Object... args) {
        warning(DEFAULT_TAG, format, args);
    }

    /**
     * 输出 error 级别日志。
     * 
     * @param tag 日志标签
     * @param msg 日志内容
     */
    public void error(String tag, String msg) {
        if (!shouldLog(LEVEL_ERROR)) return;
        writeLogToFile("ERROR", tag, msg);
        if (logToConsole)
            Log.e(tag, msg);
    }

    /**
     * 输出 error 级别格式化日志。
     * 
     * @param tag    日志标签
     * @param format 格式字符串
     * @param args   参数
     */
    public void error(String tag, String format, Object... args) {
        String msg = String.format(format, args);
        error(tag, msg);
    }

    /**
     * 输出 error 级别日志，使用默认 TAG。
     * 
     * @param msg 日志内容
     */
    public void error(String msg) {
        error(DEFAULT_TAG, msg);
    }

    /**
     * 输出 error 级别格式化日志，使用默认 TAG。
     * 
     * @param format 格式字符串
     * @param args   参数
     */
    public void error(String format, Object... args) {
        error(DEFAULT_TAG, format, args);
    }

    // ======================= 静态便捷方法 =========================

    public static void d(String tag, String msg) {
        getInstance().debug(tag, msg);
    }

    public static void i(String tag, String msg) {
        getInstance().info(tag, msg);
    }

    public static void w(String tag, String msg) {
        getInstance().warning(tag, msg);
    }

    public static void e(String tag, String msg) {
        getInstance().error(tag, msg);
    }

    public static void e(String tag, String msg, Throwable tr) {
        getInstance().error(tag, msg + "\n" + Log.getStackTraceString(tr));
    }

    // ======================= 写入日志文件 =========================

    /**
     * 写入日志到文件，格式：时间 | LEVEL | TAG | 内容
     * 
     * @param level   日志级别
     * @param tag     日志标签
     * @param content 日志内容
     */
    private void writeLogToFile(String level, String tag, String content) {
        if (content == null || content.isEmpty())
            return;
        Date d = new Date();
        String strContent = String.format("%tF %tT | %s | %s | %s\n", d, d, level, tag == null ? DEFAULT_TAG : tag,
                content);
        try {
            writeLine(strContent);
        } catch (Exception e) {
            Log.e("FileLog", "writeLogToFile failed", e);
        }
    }

    /**
     * 删除指定路径的日志文件。
     * 
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
    public void close() {
        try {
            if (mWriter != null) {
                mWriter.flush();
                // Do not close mOs twice; closing writer will close underlying stream
                mWriter.close();
            } else if (mOs != null) {
                mOs.flush();
                mOs.close();
            }
        } catch (IOException e) {
            Log.e("FileLog", "close writer/stream failed", e);
        }
        try {
            if (mRaf != null)
                mRaf.close();
        } catch (IOException e) {
            Log.e("FileLog", "close mRaf failed", e);
        }
        mWriter = null;
        mOs = null;
        mRaf = null;
        mFile = null;
    }

    /**
     * 获取日志分享所需的 Uri。
     */
    public Uri getShareUri() {
        if (appCtx == null)
            return null;
        if (mLogUri == null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            mLogUri = restoreOrCreateDownloadsUri(appCtx);
        }
        if (mLogUri != null) {
            return mLogUri;
        }
        File legacy = ensureLegacyFile();
        if (legacy != null && legacy.exists()) {
            try {
                return FileProvider.getUriForFile(appCtx,
                        appCtx.getPackageName() + ".fileprovider",
                        legacy);
            } catch (IllegalArgumentException e) {
                Log.e("FileLog", "getShareUri: fail to build FileProvider uri", e);
            }
        }
        return null;
    }

    private File ensureLegacyFile() {
        if (appCtx == null)
            return null;
        if (mFile != null)
            return mFile;
        File logDir = new File(appCtx.getFilesDir(), "logs");
        if (!logDir.exists()) {
            logDir.mkdirs();
        }
        mFile = new File(logDir, LOG_DISPLAY_NAME);
        return mFile;
    }

    public String getLogLocationHint() {
        if (mLogUri != null)
            return mLogUri.toString();
        if (mFile != null)
            return mFile.getAbsolutePath();
        return "";
    }
}
