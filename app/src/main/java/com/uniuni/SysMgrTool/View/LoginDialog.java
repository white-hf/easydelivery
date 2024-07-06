package com.uniuni.SysMgrTool.View;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.Toast;

import com.uniuni.SysMgrTool.MySingleton;
import com.uniuni.SysMgrTool.R;

public class LoginDialog extends AlertDialog {
    protected LoginDialog(Context context) {
        super(context);
    }

    public static AlertDialog init(Context mContext) {
        AlertDialog.Builder  dialog = new AlertDialog.Builder(mContext);
        dialog.setTitle(R.string.str_login);

        // 取得自定义View
        LayoutInflater layoutInflater = LayoutInflater.from(mContext);
        final View myLoginView = layoutInflater.inflate(
                R.layout.my_login_layout, null);
        dialog.setView(myLoginView);

        EditText loginAccountEt = (EditText) myLoginView
                .findViewById(R.id.my_login_account_et);
        EditText loginPasswordEt = (EditText) myLoginView
                .findViewById(R.id.my_login_password_et);

        loginAccountEt.setText(MySingleton.getInstance().getLoginInfo().loginName);

        dialog.setPositiveButton(R.string.str_confirm,
                new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {

                        String name = loginAccountEt.getText().toString();
                        String pwd  = loginPasswordEt.getText().toString();

                        if (name.isEmpty() || pwd.isEmpty())
                        {
                            Toast.makeText(mContext , R.string.str_login_tips,Toast.LENGTH_SHORT).show();
                        }
                        else
                        {
                            MySingleton.getInstance().getLoginInfo().loginName = name;
                           MySingleton.getInstance().getServerInterface().appLogin(name,pwd);
                        }
                    }
                });

        dialog.setNegativeButton(R.string.str_cancel,
                new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        dialog.dismiss();
                    }
                });

        return dialog.create();
    }
}
