package com.uniuni.SysMgrTool.Task;

import android.os.Looper;
import android.os.Message;


import android.os.Handler;
import java.util.concurrent.ConcurrentHashMap;

public class MyHandler extends Handler {
    public final static int MSG_LOADED_SCANNED_DATA = 201;

    private ConcurrentHashMap<Long , Object> mReqContext = new ConcurrentHashMap<Long , Object>();

    public MyHandler(Looper looper)
    {
        super(looper);
    }

    public void addReqContext(Long key , Object o)
    {
        mReqContext.put(key , o);
    }

    public Object getReqContext(Long k)
    {
        return mReqContext.get(k);
    }

    public void removeReqContext(Long k)
    {
        mReqContext.remove(k);
    }

    public void handleMessage(Message msg) {
        super.handleMessage(msg);

        TaskBase task = (TaskBase)msg.obj;
        task.doIt(msg);

    }
}
