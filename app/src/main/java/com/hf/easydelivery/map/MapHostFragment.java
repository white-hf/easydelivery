package com.hf.easydelivery.map;

import androidx.lifecycle.ViewModelProvider;
import com.hf.easydelivery.view.model.MapViewModel;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentTransaction;

import com.hf.easydelivery.R;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.hf.easydelivery.view.PackageListFragment;

public class MapHostFragment extends Fragment {

    private MapViewModel mapViewModel;

    /**
     * Listener interface for switching between map and list views.
     */
    public interface MapSwitchListener {
        /**
         * Called when the view is switched between map and list.
         * @param showingMap true if showing map, false if showing list
         */
        void onMapSwitched(boolean showingMap);
    }

    private MapSwitchListener mapSwitchListener;
    private FloatingActionButton fabSwitchView;

    private MapInnerFragment mapFrag;
    private PackageListFragment listFrag;

    /**
     * Sets the listener for map/list switch events.
     * @param listener the listener to set
     */
    public void setMapSwitchListener(MapSwitchListener listener) {
        this.mapSwitchListener = listener;
    }

    public void switchToMap() {
        FragmentManager fm = getChildFragmentManager();
        Fragment mapFrag = fm.findFragmentByTag("map");
        Fragment listFrag = fm.findFragmentByTag("list");

        FragmentTransaction tx = fm.beginTransaction();
        if (mapFrag != null) tx.show(mapFrag);
        if (listFrag != null) tx.hide(listFrag);
        tx.commitAllowingStateLoss();
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View root = inflater.inflate(R.layout.fragment_map_host, container, false);

        mapViewModel = new ViewModelProvider(requireActivity()).get(MapViewModel.class);

        // 默认加载 MapInnerFragment
        if (savedInstanceState == null) {
            getChildFragmentManager()
                    .beginTransaction()
                    .add(R.id.home_container, new MapInnerFragment(), "map")
                    .commitNow();
        }

        fabSwitchView = root.findViewById(R.id.fabSwitchView);

// 初始化两个子 Fragment，但仅显示地图 Fragment
        FragmentManager fm = getChildFragmentManager();
        mapFrag = (MapInnerFragment) fm.findFragmentByTag("map");
        listFrag = (PackageListFragment) fm.findFragmentByTag("list");

        if (mapFrag == null) {
            mapFrag = new MapInnerFragment();
            fm.beginTransaction()
                    .add(R.id.home_container, mapFrag, "map")
                    .commit();
        }

        fabSwitchView.setOnClickListener(v -> {
            // 仅仅负责视图切换，不涉及数据加载
            if (mapFrag.isVisible()) {
                switchToListView();
            } else {
                switchToMapView();
            }
        });


        return root;
    }


    /**
     * 切换到地图视图
     */
    public void switchToMapView() {
        if (mapFrag != null && listFrag != null) {
            getChildFragmentManager().beginTransaction()
                    .hide(listFrag)
                    .show(mapFrag)
                    .commit();
            fabSwitchView.setSelected(false);
        }
    }

    /**
     * 切换到列表视图
     */
    public void switchToListView() {
        // 如果列表 Fragment 尚未创建，则创建它
        if (listFrag == null) {
            listFrag = new PackageListFragment();
            getChildFragmentManager().beginTransaction()
                    .add(R.id.home_container, listFrag, "list")
                    .hide(mapFrag)
                    .commit();
        } else {
            getChildFragmentManager().beginTransaction()
                    .hide(mapFrag)
                    .show(listFrag)
                    .commit();
        }
        fabSwitchView.setSelected(true);
    }

    /**
     * 接收来自 MapInnerFragment 的请求并处理
     */
    public void onRequestInTransitList() {

        if (listFrag != null) {
            listFrag.loadInDeliveryParcels();
        }
    }

    /**
     * 接收来自 MapInnerFragment 的请求并处理
     */
    public void onRequestUnscannedList() {

        if (listFrag != null) {
            listFrag.loadUnscannedParcels();
        }
    }
}