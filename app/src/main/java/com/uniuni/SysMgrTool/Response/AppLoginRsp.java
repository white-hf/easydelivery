package com.uniuni.SysMgrTool.Response;

import android.os.Message;
import android.util.Log;
import android.widget.Toast;
import com.uniuni.SysMgrTool.Event.Event;
import com.uniuni.SysMgrTool.Event.EventConstant;
import com.uniuni.SysMgrTool.MySingleton;
import com.uniuni.SysMgrTool.R;
import com.uniuni.SysMgrTool.ServerInterface;
import com.uniuni.SysMgrTool.Task.TaskBase;

public class AppLoginRsp implements TaskBase {
    private String biz_code;
    private String biz_message;
    private Biz_data biz_data;
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

    public void setBiz_data(Biz_data biz_data) {
        this.biz_data = biz_data;
    }
    public Biz_data getBiz_data() {
        return biz_data;
    }

    @Override
    public void doIt(Message msg) {
        if (this.getBiz_code().equalsIgnoreCase("COMMON.QUERY.SUCCESS")) {
            ServerInterface.gToken = this.getBiz_data().getAccess_token();
            Log.d("debug", "token:" + ServerInterface.gToken);

            //notify other modules
            MySingleton.getInstance().getPublisher().notify(EventConstant.EVENT_LOGIN , new Event<String>(ServerInterface.gToken));

            Toast.makeText(MySingleton.getInstance().getCtx(), R.string.str_login_success, Toast.LENGTH_SHORT).show();
        } else
            Toast.makeText(MySingleton.getInstance().getCtx(), R.string.str_login_failure, Toast.LENGTH_SHORT).show();
    }
}
