package com.hf.easydelivery.map;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.android.gms.maps.model.LatLng;
import com.google.maps.android.clustering.ClusterItem;
import com.hf.easydelivery.dao.DeliveryInfo;

import java.util.List;

final class StopGroupItem implements ClusterItem {
    private final StopGroup group;

    StopGroupItem(@NonNull StopGroup group) {
        this.group = group;
    }

    @NonNull
    StopGroup getGroup() {
        return group;
    }

    @NonNull
    @Override
    public LatLng getPosition() {
        return group.getCenter();
    }

    @Nullable
    @Override
    public String getTitle() {
        return String.valueOf(group.size());
    }

    @Nullable
    @Override
    public String getSnippet() {
        return null;
    }

    @Nullable
    @Override
    public Float getZIndex() {
        return 2f;
    }

    @NonNull
    List<DeliveryInfo> getDeliveries() {
        return group.getDeliveries();
    }
}
