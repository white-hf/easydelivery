package com.uniuni.SysMgrTool;

import android.content.Context;

import com.uniuni.SysMgrTool.common.FileLog;

import java.util.Date;

public class CrashHandler implements Thread.UncaughtExceptionHandler {
        private static CrashHandler instance;
        private Context ctx;
        public static CrashHandler getInstance() {
            if (instance == null) {
                instance = new CrashHandler();
            }
            return instance;
        }

        public void init(Context ctx) {
            this.ctx = ctx;
            Thread.setDefaultUncaughtExceptionHandler(this);
        }

        @Override
        public void uncaughtException(Thread arg0, Throwable arg1) {
            StringBuffer logbuffer = new StringBuffer();

            logbuffer.append(new Date() + "\n");
            StackTraceElement[] stackTrace = arg1.getStackTrace();
            logbuffer.append(arg1.getMessage() + "\n");
            for (int i = 0; i < stackTrace.length; i++) {
                logbuffer.append("file:" + stackTrace[i].getFileName() + " class:" + stackTrace[i].getClassName()
                        + " method:" + stackTrace[i].getMethodName() + " line:" + stackTrace[i].getLineNumber() + "\n");
            }

            logbuffer.append("\n");

            FileLog.getInstance().writeLog(logbuffer.toString());
            System.exit(0);
        }
}
