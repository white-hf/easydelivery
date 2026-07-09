package com.hf.easydelivery.view;


import static com.hf.easydelivery.common.Utils.isNumeric;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.View;
import android.widget.*;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.hf.courierservice.ICourierService;
import com.hf.courierservice.IResponseCallBack;
import com.hf.courierservice.Result;
import com.hf.courierservice.apihelper.exception.UnAuthorizedException;
import com.hf.easydelivery.R;
import androidx.lifecycle.ViewModelProvider;
import com.hf.easydelivery.view.model.MapViewModel;
import com.hf.easydelivery.ResourceMgr;
import com.hf.easydelivery.api.LoginResponseCb;
import com.hf.easydelivery.common.Utils;
import com.hf.easydelivery.event.Event;
import com.hf.easydelivery.event.EventConstant;

public class LoginActivity extends AppCompatActivity {
    private EditText etDriverId, etPassword;
    private Button btnLogin;
    private ProgressBar loading;
    private SharedPreferences sp;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);

        etDriverId = findViewById(R.id.etDriverId);
        etPassword = findViewById(R.id.etPassword);
        btnLogin = findViewById(R.id.loginButton);
        loading = findViewById(R.id.activityIndicator);

        // Ensure MapViewModel is initialized
        MapViewModel mapViewModel = new ViewModelProvider(this).get(MapViewModel.class);

        sp = getSharedPreferences("login", MODE_PRIVATE);
        String lastId = sp.getString("lastDriverId", "");
        etDriverId.setText(lastId);

        TextView versionLabel = findViewById(R.id.versionLabel);
        if (versionLabel != null) {
            try {
                String versionName = getPackageManager().getPackageInfo(getPackageName(), 0).versionName;
                versionLabel.setText("v" + versionName);
            } catch (Exception e) {
                versionLabel.setText("v2.0");
            }
        }

        btnLogin.setOnClickListener(v -> {
            String driverId = etDriverId.getText().toString().trim();
            String password = etPassword.getText().toString().trim();

            if (driverId.isEmpty() || password.isEmpty()) {
                showAlert(getString(R.string.login_input_error_title),
                        getString(R.string.login_input_error_message));
                return;
            }

            loading.setVisibility(View.VISIBLE);
            btnLogin.setEnabled(false);

            ICourierService courierService = ResourceMgr.getInstance().getCourierService();
            assert courierService != null;

            courierService.login(driverId, password, new IResponseCallBack<String>() {
                       @Override
                       public void onComplete(Result<String> result) {
                           Result.Success<String> success = (Result.Success<String>) result;

                           ResourceMgr.getInstance().getLoginInfo().loginName = driverId;
                           ResourceMgr.getInstance().getLoginInfo().loginId = Integer.valueOf(driverId);

                           ResourceMgr.getInstance().getLoginInfo().bIsLoggedIn = true;

                           //notify other modules
                           ResourceMgr.getInstance().getPublisher().notify(EventConstant.EVENT_LOGIN , new Event<String>(ResourceMgr.getInstance().getLoginInfo().loginName));

                           // TODO: 替换成实际的登录API请求（这里只模拟登录延迟）
                           etDriverId.postDelayed(() -> {
                               Utils.showOnUi(LoginActivity.this, getString(R.string.str_login_success));
                               loading.setVisibility(View.GONE);
                               btnLogin.setEnabled(true);

                               // 假设登录永远成功
                               sp.edit().putString("lastDriverId", driverId).apply();

                               sp.edit().putString("token", "rspToken").apply(); // 记录token（或userId等）

                               // 跳转主页
                               startActivity(new Intent(LoginActivity.this, MainActivity.class));
                               finish();

                           }, 1000);
                       }


                       @Override
                       public void onFail(Exception result) {
                           loading.setVisibility(View.GONE);
                           btnLogin.setEnabled(true);

                           if (result instanceof UnAuthorizedException) {
                               Utils.showOnUi(LoginActivity.this, getString(R.string.str_login_user_failure));
                           } else {
                               Utils.showOnUi(LoginActivity.this, getString(R.string.str_login_failure));
                           }
                       }
                   });

        });
    }

    private void showAlert(String title, String message) {
        new AlertDialog.Builder(this)
                .setTitle(title)
                .setMessage(message)
                .setPositiveButton(R.string.action_ok, null)
                .show();
    }
}
