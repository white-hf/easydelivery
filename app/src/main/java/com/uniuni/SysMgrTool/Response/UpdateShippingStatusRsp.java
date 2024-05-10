package com.uniuni.SysMgrTool.Response;

import android.os.Message;

import com.google.gson.Gson;
import com.uniuni.SysMgrTool.MyDb;
import com.uniuni.SysMgrTool.MySingleton;
import com.uniuni.SysMgrTool.Task.MyHandler;
import com.uniuni.SysMgrTool.Task.TaskBase;
import com.uniuni.SysMgrTool.common.FileLog;
import com.uniuni.SysMgrTool.dao.ScannedRecord;

public class UpdateShippingStatusRsp extends ResponseBase implements TaskBase {
    public UpdateShippingStatusRspData getData() {
        return data;
    }

    public void setData(UpdateShippingStatusRspData data) {
        this.data = data;
    }

    private UpdateShippingStatusRspData data;

    @Override
    public void doIt(Message msg) {
        if (this.isSuccess())
        {
            MyHandler h = MySingleton.getInstance().getServerInterface().getMyHandler();
            if (msg.arg2 < 1)
                return;

            Long key = Long.valueOf(msg.arg2);
            ScannedRecord r = (ScannedRecord)h.getReqContext(key);
            if (null == r)
                return;

            MyDb db = MySingleton.getInstance().getmMydb();

            db.updateScannedData(r);
            h.removeReqContext(key);

            String strLog = String.format("update db by key:%d succeed" , key.intValue());
            FileLog.getInstance().writeLog(strLog);
        }
        else
        {
            Gson gson = new Gson();
            FileLog.getInstance().writeLog(gson.toJson(this));
        }
    }
}
