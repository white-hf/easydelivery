package com.uniuni.SysMgrTool.Response;

import android.os.Message;

import com.uniuni.SysMgrTool.MySingleton;
import com.uniuni.SysMgrTool.Task.TaskBase;

import java.util.List;
import java.util.ListIterator;

public class AppRsp implements TaskBase {
    private String biz_code;
    private String biz_message;
    private List<DeliveringListData> biz_data;
    public void setBiz_code(String biz_code) {
        this.biz_code = biz_code;
    }
    public String getBiz_code() {
        return biz_code;
    }

    public void setBiz_message(String biz_message) {
        this.biz_message = biz_message;
    }
    public String getBiz_message() {
        return biz_message;
    }

    public void setBiz_data(List<DeliveringListData> biz_data) {
        this.biz_data = biz_data;
    }
    public List<DeliveringListData> getBiz_data() {
        return biz_data;
    }


    @Override
    public void doIt(Message msg) {
        List<DeliveringListData> lst = this.getBiz_data();
        ListIterator<DeliveringListData> listIterator = lst.listIterator();
        System.out.println("Get delivering  list:" + this.getBiz_data().size());

        //remove the old data from cache firstly.
        MySingleton.getInstance().getdDeliveryinfoMgr().clearAll();

        while (listIterator.hasNext()) {
            DeliveringListData d = (DeliveringListData) listIterator.next();
            MySingleton.getInstance().getdDeliveryinfoMgr().saveDeliveringListData(d);
        }
    }
}