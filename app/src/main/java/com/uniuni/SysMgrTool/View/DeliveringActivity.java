
package com.uniuni.SysMgrTool.View;

import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.FrameLayout;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentTransaction;

import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.MarkerOptions;
import com.uniuni.SysMgrTool.R;

public class DeliveringActivity extends AppCompatActivity {

    private boolean isMapMode = true;
    private Button viewToggleButton;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_delivering);

        // 初始化地图界面
        initMapFragment();

        // 切换地图/列表模式按钮
        viewToggleButton = findViewById(R.id.viewToggleButton);
        viewToggleButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                toggleViewMode();
            }
        });


    }

    private void initMapFragment() {
        MapFragment mapFragment = new MapFragment();
        FragmentManager fragmentManager = getSupportFragmentManager();
        FragmentTransaction fragmentTransaction = fragmentManager.beginTransaction();
        fragmentTransaction.replace(R.id.fragmentContainer, mapFragment);
        fragmentTransaction.commit();
    }

    private void toggleViewMode() {
        FragmentManager fragmentManager = getSupportFragmentManager();
        FragmentTransaction fragmentTransaction = fragmentManager.beginTransaction();

        if (isMapMode) {
            // 切换到列表模式
            ListViewFragment listFragment = new ListViewFragment();
            fragmentTransaction.replace(R.id.fragmentContainer, listFragment);
            viewToggleButton.setText(R.string.view_map);
        } else {
            // 切换到地图模式
            MapFragment mapFragment = new MapFragment();
            fragmentTransaction.replace(R.id.fragmentContainer, mapFragment);
            viewToggleButton.setText(R.string.view_list);
        }

        isMapMode = !isMapMode;
        fragmentTransaction.commit();
    }
}
