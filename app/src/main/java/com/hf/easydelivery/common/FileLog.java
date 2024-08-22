package com.hf.easydelivery.common;

import android.content.Context;
import android.util.Log;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.util.Date;

public class FileLog {
    public final static String LOG_DIR_NAME = "logs";//\Environment.getExternalStorageDirectory() + "/PhoneData/";
    private final static String LOG_FILE_NAME = "sysmgrtool.log";

    File mFile;
    RandomAccessFile mRaf;

    private static FileLog instance;

    public static FileLog getInstance()
    {
        if(instance == null) {
            instance = new FileLog();
            return instance;
        }
        else
            return instance;
    }

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

    public void writeLog(String content) {
        if (content == null || content.isEmpty())
            return;

        Date d = new Date();
        String strContent = d + ":" + content + "\n";
        try {
            mRaf.write(strContent.getBytes());
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void delLogFile(String path) {
        File file = new File(path);
        if (file.exists()) {
            file.delete();
        }
    }

    public void close()
    {
        try {
            mRaf.close();
            mFile = null;
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
