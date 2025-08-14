package com.hf.easydelivery.view;


import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.View;
import androidx.annotation.NonNull;
import androidx.activity.OnBackPressedCallback;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.hf.easydelivery.R;
import com.hf.easydelivery.ResourceMgr;
import com.hf.easydelivery.map.MapFragment;
import com.hf.easydelivery.common.FileLog;
import java.util.concurrent.atomic.AtomicBoolean;

public class MainActivity extends AppCompatActivity {
    private static final String TAG_DELIVER = "tab_deliver";
    private static final String TAG_SCAN    = "tab_scan";
    private static final String TAG_ME      = "tab_me";
    private String currentTag = TAG_DELIVER;
    private BottomNavigationView bottomNav;
    private View overlayContainer;
    private final java.util.concurrent.atomic.AtomicBoolean overlayBusy = new java.util.concurrent.atomic.AtomicBoolean(false);

    // 在 MainActivity 里加：
    public void showOverlay(@NonNull Fragment f, @NonNull String tag) {
        if (!overlayBusy.compareAndSet(false, true)) {
            FileLog.getInstance().warning("MainActivity", "showOverlay: reentry blocked, tag=" + tag);
            return;
        }
        final var fm = getSupportFragmentManager();
        try {
            fm.executePendingTransactions();

            if (fm.isStateSaved()) {
                // Defer the overlay until after state is restored to avoid IllegalStateException
                FileLog.getInstance().warning("MainActivity", "showOverlay: state is saved, deferring. tag=" + tag);
                overlayContainer.postDelayed(() -> {
                    try {
                        // Re-check after delay
                        if (!fm.isStateSaved()) {
                            showOverlay(f, tag);
                        } else {
                            FileLog.getInstance().warning("MainActivity", "showOverlay: still stateSaved, using commitAllowingStateLoss. tag=" + tag);
                            var tx2 = fm.beginTransaction().setReorderingAllowed(true);
                            tx2.add(R.id.overlay_container, f, tag);
                            tx2.addToBackStack(tag);
                            if (overlayContainer != null) {
                                overlayContainer.setVisibility(View.VISIBLE);
                                overlayContainer.bringToFront();
                            }
                            tx2.commitAllowingStateLoss();
                        }
                    } catch (Exception ex) {
                        FileLog.getInstance().error("MainActivity", "showOverlay deferred exception: " + ex);
                    }
                }, 16);
                return;
            }

            final var existing = fm.findFragmentByTag(tag);
            FileLog.getInstance().debug("MainActivity", "showOverlay tag=" + tag + ", existing=" + (existing != null));

            // If the same overlay is already visible, just ensure the container is visible and return.
            if (existing != null && existing.isAdded() && existing.isVisible()) {
                if (overlayContainer != null) {
                    overlayContainer.setVisibility(View.VISIBLE);
                    overlayContainer.bringToFront();
                }
                FileLog.getInstance().debug("MainActivity", "showOverlay: already visible, skip transaction");
                return;
            }

            var tx = fm.beginTransaction().setReorderingAllowed(true);

            if (existing == null) {
                tx.add(R.id.overlay_container, f, tag);
            } else {
                tx.show(existing);
            }

            // Avoid stacking multiple identical back stack entries.
            boolean needAddToBackStack = true;
            int count = fm.getBackStackEntryCount();
            if (count > 0) {
                var last = fm.getBackStackEntryAt(count - 1);
                if (last != null && tag.equals(last.getName())) {
                    needAddToBackStack = false;
                }
            }
            if (needAddToBackStack) {
                tx.addToBackStack(tag);
            }

            if (overlayContainer != null) {
                overlayContainer.setVisibility(View.VISIBLE);
                overlayContainer.bringToFront();
            }

            tx.commit();
            FileLog.getInstance().debug("MainActivity", "showOverlay committed, tag=" + tag);
        } finally {
            overlayBusy.set(false);
        }
    }

    public void hideOverlay() {
        FileLog.getInstance().debug("MainActivity", "hideOverlay called");
        getSupportFragmentManager().executePendingTransactions();
        if (getSupportFragmentManager().getBackStackEntryCount() > 0) {
            getSupportFragmentManager().popBackStack();
            if (overlayContainer != null) {
                overlayContainer.post(() -> overlayContainer.setVisibility(View.GONE));
            }
            FileLog.getInstance().debug("MainActivity", "hideOverlay: container set to GONE");
        }
        // Visibility will be handled by onBackStackChanged listener.
    }


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        overlayContainer = findViewById(R.id.overlay_container);
        if (overlayContainer != null) overlayContainer.setVisibility(View.GONE);
        if (overlayContainer != null) {
            overlayContainer.bringToFront();
            overlayContainer.setClickable(true);
            overlayContainer.setFocusable(true);
        }

        getSupportFragmentManager().addOnBackStackChangedListener(() -> {
            int count = getSupportFragmentManager().getBackStackEntryCount();
            if (overlayContainer != null) overlayContainer.setVisibility(count > 0 ? View.VISIBLE : View.GONE);
            FileLog.getInstance().debug("MainActivity", "onBackStackChanged count=" + count);
        });

        // Use OnBackPressedDispatcher to handle back presses instead of deprecated onBackPressed()
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                var overlay = overlayContainer != null ? overlayContainer : findViewById(R.id.overlay_container);
                if (overlay != null && overlay.getVisibility() == View.VISIBLE
                        && getSupportFragmentManager().getBackStackEntryCount() > 0) {
                    FileLog.getInstance().debug("MainActivity", "back: pop overlay");
                    getSupportFragmentManager().popBackStack();
                    overlay.post(() -> {
                        overlay.setVisibility(View.GONE);
                        overlay.bringToFront(); // keep z-order consistent for next show
                    });
                } else {
                    FileLog.getInstance().debug("MainActivity", "back: default");
                    setEnabled(false);
                    MainActivity.super.onBackPressed();
                }
            }
        });

        // 1. 检查登录态
        if (!isLoggedIn()) {
            Intent intent = new Intent(this, LoginActivity.class);
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            startActivity(intent);
            // 注意：此处不 return，让系统继续创建 UI（登录返回后也能复用现有实例）
        }

        bottomNav = findViewById(R.id.bottom_nav);

        // 2. 恢复或初始化三个 Fragment（常驻，后续只 show/hide）
        if (savedInstanceState == null) {
            // 首次：创建并 add，默认显示送件
            Fragment deliver = new MapFragment();
            getSupportFragmentManager().beginTransaction()
                    .add(R.id.container, deliver, TAG_DELIVER)
                    .commit();
            // 其余先不 add，等首次切到再懒加载；也可以提前 add 并 hide
            currentTag = TAG_DELIVER;
        } else {
            // 进程重建：从 FragmentManager 恢复当前 tag（如果需要可以从 savedInstanceState 取回）
            Fragment visible = getSupportFragmentManager().findFragmentByTag(TAG_DELIVER);
            if (visible != null && visible.isVisible()) currentTag = TAG_DELIVER;
            Fragment scan = getSupportFragmentManager().findFragmentByTag(TAG_SCAN);
            if (scan != null && scan.isVisible()) currentTag = TAG_SCAN;
            Fragment me = getSupportFragmentManager().findFragmentByTag(TAG_ME);
            if (me != null && me.isVisible()) currentTag = TAG_ME;

            if (savedInstanceState.containsKey("currentTag")) {
                currentTag = savedInstanceState.getString("currentTag", currentTag);
            }
        }

        // 3. 绑定底部切换，使用 show/hide，避免重复 new/replace
        bottomNav.setOnItemSelectedListener(item -> {
            int itemId = item.getItemId();
            if (itemId == R.id.nav_deliver) {
                showFragment(TAG_DELIVER);
                return true;
            } else if (itemId == R.id.nav_scan) {
                showFragment(TAG_SCAN);
                return true;
            } else if (itemId == R.id.nav_me) {
                showFragment(TAG_ME);
                return true;
            }
            return false;
        });

        // 4. 设置默认选中项（与 currentTag 对齐）
        if (TAG_DELIVER.equals(currentTag)) {
            bottomNav.setSelectedItemId(R.id.nav_deliver);
        } else if (TAG_SCAN.equals(currentTag)) {
            bottomNav.setSelectedItemId(R.id.nav_scan);
        } else {
            bottomNav.setSelectedItemId(R.id.nav_me);
        }
    }

    private void showFragment(@NonNull String targetTag) {
        FileLog.getInstance().debug("MainActivity", "showFragment target=" + targetTag + ", current=" + currentTag);
        if (targetTag.equals(currentTag)) return;
        final var fm = getSupportFragmentManager();
        final var tx = fm.beginTransaction();

        // 先隐藏当前三个中所有已存在的实例
        Fragment fDeliver = fm.findFragmentByTag(TAG_DELIVER);
        Fragment fScan    = fm.findFragmentByTag(TAG_SCAN);
        Fragment fMe      = fm.findFragmentByTag(TAG_ME);
        if (fDeliver != null) tx.hide(fDeliver);
        if (fScan != null)    tx.hide(fScan);
        if (fMe != null)      tx.hide(fMe);

        // 目标：若不存在则创建并 add
        Fragment target = fm.findFragmentByTag(targetTag);
        if (target == null) {
            if (TAG_DELIVER.equals(targetTag)) {
                target = new MapFragment();
            } else if (TAG_SCAN.equals(targetTag)) {
                target = new ScanFragment();
            } else if (TAG_ME.equals(targetTag)) {
                target = new MeFragment();
            }
            if (target != null) {
                tx.add(R.id.container, target, targetTag);
            }
        } else {
            tx.show(target);
        }

        if (overlayContainer != null) overlayContainer.bringToFront();

        tx.commit();
        currentTag = targetTag;
    }
    // 判断是否已登录（可自定义token规则）
    private boolean isLoggedIn() {
        return  ResourceMgr.getInstance().getLoginInfo().bIsLoggedIn;
    }

    @Override
    protected void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putString("currentTag", currentTag);
    }
}
