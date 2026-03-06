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
         * 
         * @param showingMap true if showing map, false if showing list
         */
        void onMapSwitched(boolean showingMap);
    }

    /**
     * Listener interface for fullscreen mode toggle.
     */
    public interface FullscreenModeListener {
        /**
         * Called when fullscreen mode is toggled.
         * 
         * @param isFullscreen true if entering fullscreen, false if exiting
         */
        void onFullscreenToggle(boolean isFullscreen);
    }

    private MapSwitchListener mapSwitchListener;
    private FullscreenModeListener fullscreenListener;
    private FloatingActionButton fabSwitchView;

    private MapInnerFragment mapFrag;
    private PackageListFragment listFrag;
    private PackageListFragment.ListMode currentListMode = PackageListFragment.ListMode.IN_TRANSIT_FROM_MAP;

    /**
     * Sets the listener for map/list switch events.
     * 
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
        if (mapFrag != null)
            tx.show(mapFrag);
        if (listFrag != null)
            tx.hide(listFrag);
        tx.commitAllowingStateLoss();

        if (fabSwitchView != null) {
            fabSwitchView.setSelected(false);
        }
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
            @Nullable Bundle savedInstanceState) {
        View root = inflater.inflate(R.layout.fragment_map_host, container, false);

        mapViewModel = new ViewModelProvider(requireActivity()).get(MapViewModel.class);

        fabSwitchView = root.findViewById(R.id.fabSwitchView);

        // 初始化两个子 Fragment，但仅显示地图 Fragment
        FragmentManager fm = getChildFragmentManager();
        mapFrag = (MapInnerFragment) fm.findFragmentByTag("map");
        listFrag = (PackageListFragment) fm.findFragmentByTag("list");

        boolean removedExtraMap = false;
        FragmentTransaction cleanupTx = fm.beginTransaction();
        for (Fragment child : fm.getFragments()) {
            if (child instanceof MapInnerFragment && child != mapFrag) {
                cleanupTx.remove(child);
                removedExtraMap = true;
            }
        }
        if (removedExtraMap) {
            cleanupTx.commitNowAllowingStateLoss();
        }

        if (mapFrag == null) {
            mapFrag = new MapInnerFragment();
            fm.beginTransaction()
                    .add(R.id.home_container, mapFrag, "map")
                    .commitNow();
        }

        if (fabSwitchView != null) {
            fabSwitchView.setOnClickListener(v -> {
                // 旧入口保留（当前已在地图右侧工具栏提供主入口）
                if (mapFrag != null && mapFrag.isVisible()) {
                    switchToListView();
                } else {
                    switchToMapView();
                }
            });
        }

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
            if (fabSwitchView != null) {
                fabSwitchView.setSelected(false);
            }
        }
    }

    /**
     * 切换到列表视图
     */
    public void switchToListView() {
        // 如果列表 Fragment 尚未创建，则创建它
        if (listFrag == null) {
            listFrag = new PackageListFragment();
            Bundle args = new Bundle();
            args.putString("arg_list_mode", currentListMode.name());
            listFrag.setArguments(args);
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
        if (fabSwitchView != null) {
            fabSwitchView.setSelected(true);
        }

        applyCurrentListMode();
    }

    /**
     * 接收来自 MapInnerFragment 的请求并处理
     */
    public void onRequestInTransitList() {

        currentListMode = PackageListFragment.ListMode.IN_TRANSIT_FROM_MAP;
        if (listFrag != null) {
            listFrag.loadInDeliveryParcels();
        }
    }

    /**
     * 接收来自 MapInnerFragment 的请求并处理
     */
    public void onRequestUnscannedList() {

        currentListMode = PackageListFragment.ListMode.UNSCANNED_FROM_SCAN;
        if (listFrag != null) {
            listFrag.loadUnscannedParcels();
        }
    }

    private void applyCurrentListMode() {
        if (listFrag == null || !listFrag.isAdded() || listFrag.getView() == null) {
            return;
        }
        if (currentListMode == PackageListFragment.ListMode.UNSCANNED_FROM_SCAN) {
            listFrag.loadUnscannedParcels();
        } else {
            listFrag.loadInDeliveryParcels();
        }
    }

    /**
     * Sets the listener for fullscreen mode events.
     * 
     * @param listener the listener to set
     */
    public void setFullscreenModeListener(FullscreenModeListener listener) {
        this.fullscreenListener = listener;
    }

    /**
     * Notifies the listener that fullscreen mode has been toggled.
     * 
     * @param isFullscreen true if entering fullscreen, false if exiting
     */
    public void notifyFullscreenToggle(boolean isFullscreen) {
        if (fullscreenListener != null) {
            fullscreenListener.onFullscreenToggle(isFullscreen);
        }
    }
}
