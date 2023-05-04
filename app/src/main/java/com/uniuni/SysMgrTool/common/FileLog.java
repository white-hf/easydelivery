package com.uniuni.SysMgrTool.common;

import android.util.Log;

import com.uniuni.SysMgrTool.MySingleton;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.util.Date;

public class FileLog {
    public final static String FILE_PATH = "/data/data/com.example.user.SysMgrTool";//\Environment.getExternalStorageDirectory() + "/PhoneData/";
    private final static String FILE_NAME = "scantool.log";

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

    public boolean init()
    {
        try {
            boolean b = false;
            File fileDir = new File(FILE_PATH);
            if (!fileDir.exists()) {
                b = fileDir.mkdirs();
                if (!b)
                    Log.d("debug","init log failed");

                if (!fileDir.exists()) {
                    //return false;
                }
            }

            String p = null;
            if (b)
                mFile = new File(FILE_PATH, FILE_NAME);
            else {
                File fDir = MySingleton.getInstance().getCtx().getExternalCacheDir();
                mFile = new File(fDir , FILE_NAME);
            }

            mRaf  = new RandomAccessFile(mFile, "rw");
            mRaf.seek(mFile.length());

            return true;
        } catch (Exception e) {e.printStackTrace();
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
