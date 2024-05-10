package com.uniuni.SysMgrTool.Response;

import android.os.Message;
import android.util.Log;
import android.widget.Toast;

import com.uniuni.SysMgrTool.MySingleton;
import com.uniuni.SysMgrTool.R;
import com.uniuni.SysMgrTool.Task.TaskBase;

public class LoginResponse extends ResponseBase implements TaskBase {
    public LoginResponseData getData() {
        return data;
    }

    public void setData(LoginResponseData data) {
        this.data = data;
    }

    private LoginResponseData data;

    @Override
    public void doIt(Message msg) {
        if (this.getStatus().equalsIgnoreCase("success")) {
            MySingleton.getInstance().getServerInterface().gToken = this.getData().getToken();
            Log.d("debug", "token:" + MySingleton.getInstance().getServerInterface().gToken);

            Toast.makeText(MySingleton.getInstance().getCtx(), R.string.str_login_success, Toast.LENGTH_SHORT).show();
        } else
            Toast.makeText(MySingleton.getInstance().getCtx(), R.string.str_login_failure, Toast.LENGTH_SHORT).show();
    }
}
