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

        FloatingActionButton fabSwitchView = root.findViewById(R.id.fabSwitchView);
        FloatingActionButton fabMyLocation = root.findViewById(R.id.fabMyLocation);
        FloatingActionButton fabToggleMapType = root.findViewById(R.id.fabToggleMapType);

        fabSwitchView.setOnClickListener(v -> {
            FragmentManager fm = getChildFragmentManager();
            Fragment mapFrag = fm.findFragmentByTag("map");
            Fragment listFrag = fm.findFragmentByTag("list");
            FragmentTransaction tx = fm.beginTransaction();

            boolean mapVisible = mapFrag != null && mapFrag.isVisible();
            boolean listVisible = listFrag != null && listFrag.isVisible();

            if (mapVisible) {
                if (listFrag == null) {
                    listFrag = new PackageListFragment();
                    tx.add(R.id.home_container, listFrag, "list");
                }
                if (mapFrag != null) tx.hide(mapFrag);
                if (listFrag != null) tx.show(listFrag);
                if (mapSwitchListener != null) mapSwitchListener.onMapSwitched(false);
            } else if (listVisible) {
                if (listFrag != null) tx.hide(listFrag);
                if (mapFrag != null) tx.show(mapFrag);
                if (mapSwitchListener != null) mapSwitchListener.onMapSwitched(true);
            }
            tx.commitAllowingStateLoss();
        });

        fabMyLocation.setOnClickListener(v -> {
            Fragment currentFragment = getChildFragmentManager().findFragmentById(R.id.home_container);
            if (currentFragment instanceof MapInnerFragment) {
                ((MapInnerFragment) currentFragment).centerOnMyLocation();
            }
        });

        fabToggleMapType.setOnClickListener(v -> {
            Fragment currentFragment = getChildFragmentManager().findFragmentById(R.id.home_container);
            if (currentFragment instanceof MapInnerFragment) {
                ((MapInnerFragment) currentFragment).toggleMapType(fabToggleMapType);
            }
        });

        return root;
    }
}