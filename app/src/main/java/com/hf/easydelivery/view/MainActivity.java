package com.hf.easydelivery.view;


import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.hf.easydelivery.R;
import com.hf.easydelivery.ResourceMgr;
import com.hf.easydelivery.map.MapFragment;

public class MainActivity extends AppCompatActivity {
    private BottomNavigationView bottomNav;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // 1. 检查登录态
        if (!isLoggedIn()) {
            // 跳转登录页
            Intent intent = new Intent(this, LoginActivity.class);
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK); // 禁止回退到主页
            startActivity(intent);
            // 可选：return;（避免主页面初始化被误执行）
        }

        bottomNav = findViewById(R.id.bottom_nav);

        // 默认显示送件
        if (savedInstanceState == null) {
            getSupportFragmentManager().beginTransaction()
                    .replace(R.id.container, new MapFragment())
                    .commit();
        }

        bottomNav.setOnItemSelectedListener(item -> {
            Fragment selected = null; // Initialize selected to null
            int itemId = item.getItemId();

            if (itemId == R.id.nav_deliver) {
                selected = new MapFragment();
            } else if (itemId == R.id.nav_scan) {
                selected = new ScanFragment();
            } else if (itemId == R.id.nav_me) {
                selected = new MeFragment();
            }

            if (selected != null) {
                getSupportFragmentManager().beginTransaction()
                        .replace(R.id.container, selected)
                        .commit();
                return true;
            }
            return false;
        });
    }
    // 判断是否已登录（可自定义token规则）
    private boolean isLoggedIn() {
        return  ResourceMgr.getInstance().getLoginInfo().bIsLoggedIn;

    }
}
