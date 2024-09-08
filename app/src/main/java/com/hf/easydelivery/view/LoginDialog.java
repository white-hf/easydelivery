package com.hf.easydelivery.view;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.EditText;
import android.widget.Toast;

import com.hf.courierservice.ICourierService;
import com.hf.easydelivery.R;
import com.hf.easydelivery.ResourceMgr;
import com.hf.easydelivery.api.LoginResponseCb;

public class LoginDialog extends AlertDialog {
    protected LoginDialog(Context context) {
        super(context);
    }

    private static boolean isNumeric(String str) {
        if (str == null || str.isEmpty()) {
            return false;
        }
        return str.matches("-?\\d+(\\.\\d+)?");
    }

    public static AlertDialog init(Context mContext) {
        AlertDialog.Builder  dialog = new AlertDialog.Builder(mContext);

        LayoutInflater layoutInflater = LayoutInflater.from(mContext);
        final View myLoginView = layoutInflater.inflate(
                R.layout.my_login_layout, null);
        dialog.setView(myLoginView);

        EditText loginAccountEt = (EditText) myLoginView
                .findViewById(R.id.my_login_account_et);
        EditText loginPasswordEt = (EditText) myLoginView
                .findViewById(R.id.my_login_password_et);

        loginAccountEt.setText(ResourceMgr.getInstance().getLoginInfo().loginName);

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
                            ResourceMgr.getInstance().getLoginInfo().loginName = name;

                            if (isNumeric(name)) {
                                ICourierService courierService = ResourceMgr.getInstance().getCourierService();
                                assert courierService != null;

                                courierService.login(name, pwd , new LoginResponseCb());
                            }
                            else
                                Toast.makeText(mContext , R.string.str_login_tips,Toast.LENGTH_SHORT).show();
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
