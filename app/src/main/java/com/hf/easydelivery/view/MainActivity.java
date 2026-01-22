package com.hf.easydelivery.view;

import android.content.Intent;
import android.os.Bundle;
import androidx.annotation.NonNull;
import androidx.activity.OnBackPressedCallback;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.viewpager2.adapter.FragmentStateAdapter;
import androidx.viewpager2.widget.ViewPager2;

import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.hf.easydelivery.R;
import com.hf.easydelivery.ResourceMgr;
import com.hf.easydelivery.core.SmartLocationManager;
import com.hf.easydelivery.map.MapHostFragment;

public class MainActivity extends AppCompatActivity
        implements MapHostFragment.FullscreenModeListener {
    private static final String TAG_DELIVER = "tab_deliver";
    private static final String TAG_SCAN = "tab_scan";
    private static final String TAG_ME = "tab_me";
    private BottomNavigationView bottomNav;
    private ViewPager2 viewPager;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Use OnBackPressedDispatcher to handle back presses normally without overlay
        // logic
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                new androidx.appcompat.app.AlertDialog.Builder(MainActivity.this)
                        .setTitle(R.string.exit_confirm_title)
                        .setMessage(R.string.exit_confirm_message)
                        .setPositiveButton(R.string.action_exit, (dialog, which) -> {
                            setEnabled(false);
                            stopLocationTrackingForExit(); // 避免重复触发
                            MainActivity.super.onBackPressed();
                        })
                        .setNegativeButton(R.string.action_cancel, (dialog, which) -> {
                            dialog.dismiss();
                        })
                        .show();
            }
        });

        // 1. 检查登录态
        if (!isLoggedIn()) {
            Intent intent = new Intent(this, LoginActivity.class);
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            startActivity(intent);
            // 注意：此处不 return，让系统继续创建 UI（登录返回后也能复用现有实例）
        }

        viewPager = findViewById(R.id.viewPager);
        bottomNav = findViewById(R.id.bottom_nav);

        // Set up ViewPager2 with FragmentStateAdapter
        viewPager.setAdapter(new MainPagerAdapter(this));
        // Disable swipe if desired
        viewPager.setUserInputEnabled(false);

        // 绑定底部切换，切换页面
        bottomNav.setOnItemSelectedListener(item -> {
            int itemId = item.getItemId();
            if (itemId == R.id.nav_deliver) {
                viewPager.setCurrentItem(0, false);
                return true;
            } else if (itemId == R.id.nav_scan) {
                viewPager.setCurrentItem(1, false);
                return true;
            } else if (itemId == R.id.nav_me) {
                viewPager.setCurrentItem(2, false);
                return true;
            }
            return false;
        });

        // 页面切换时同步底部导航栏选中项
        viewPager.registerOnPageChangeCallback(new ViewPager2.OnPageChangeCallback() {
            @Override
            public void onPageSelected(int position) {
                super.onPageSelected(position);
                switch (position) {
                    case 0:
                        bottomNav.setSelectedItemId(R.id.nav_deliver);
                        break;
                    case 1:
                        bottomNav.setSelectedItemId(R.id.nav_scan);
                        break;
                    case 2:
                        bottomNav.setSelectedItemId(R.id.nav_me);
                        break;
                }
            }
        });

        // 设置默认选中项
        int defaultIndex = 0;
        if (savedInstanceState != null) {
            String savedTag = savedInstanceState.getString("currentTag", TAG_DELIVER);
            if (TAG_SCAN.equals(savedTag))
                defaultIndex = 1;
            else if (TAG_ME.equals(savedTag))
                defaultIndex = 2;
        }
        viewPager.setCurrentItem(defaultIndex, false);
        // 同步底部导航栏
        switch (defaultIndex) {
            case 0:
                bottomNav.setSelectedItemId(R.id.nav_deliver);
                break;
            case 1:
                bottomNav.setSelectedItemId(R.id.nav_scan);
                break;
            case 2:
                bottomNav.setSelectedItemId(R.id.nav_me);
                break;
        }
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
    }

    @Override
    protected void onResume() {
        super.onResume();

        // Set fullscreen listener on MapHostFragment
        FragmentStateAdapter adapter = (FragmentStateAdapter) viewPager.getAdapter();
        if (adapter != null && viewPager.getCurrentItem() == 0) {
            Fragment mapHostFragment = getSupportFragmentManager()
                    .findFragmentByTag("f0"); // ViewPager2 uses "f{position}" as tag
            if (mapHostFragment instanceof MapHostFragment) {
                ((MapHostFragment) mapHostFragment).setFullscreenModeListener(this);
            }
        }
    }

    @Override
    public void onFullscreenToggle(boolean isFullscreen) {
        // Hide/show bottom navigation
        if (bottomNav != null) {
            bottomNav.setVisibility(isFullscreen ? android.view.View.GONE : android.view.View.VISIBLE);
        }

        // Hide/show ActionBar (if any)
        if (getSupportActionBar() != null) {
            if (isFullscreen) {
                getSupportActionBar().hide();
            } else {
                getSupportActionBar().show();
            }
        }
    }

    @Override
    protected void onDestroy() {
        if (isFinishing()) {
            stopLocationTrackingForExit();
        }
        super.onDestroy();
    }

    // ViewPager2 adapter
    private class MainPagerAdapter extends FragmentStateAdapter {
        public MainPagerAdapter(@NonNull FragmentActivity fa) {
            super(fa);
        }

        @NonNull
        @Override
        public Fragment createFragment(int position) {
            if (position == 0) {
                return new MapHostFragment();
            } else if (position == 1) {
                return new ScanFragment();
            } else {
                return new MeFragment();
            }
        }

        @Override
        public int getItemCount() {
            return 3;
        }

        @Override
        public long getItemId(int position) {
            return position;
        }

        @Override
        public boolean containsItem(long itemId) {
            return itemId >= 0 && itemId < 3;
        }

    }

    // 判断是否已登录（可自定义token规则）
    private void stopLocationTrackingForExit() {
        SmartLocationManager mgr = SmartLocationManager.getInstance(this);
        if (mgr != null) {
            mgr.stopForegroundTracking(false);
            mgr.stopLocationUpdates();
        }
    }

    private boolean isLoggedIn() {
        return ResourceMgr.getInstance().getLoginInfo().bIsLoggedIn;
    }

    @Override
    protected void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        // 保存当前页面
        int currentItem = viewPager != null ? viewPager.getCurrentItem() : 0;
        String tag = TAG_DELIVER;
        if (currentItem == 1)
            tag = TAG_SCAN;
        else if (currentItem == 2)
            tag = TAG_ME;
        outState.putString("currentTag", tag);
    }
}
