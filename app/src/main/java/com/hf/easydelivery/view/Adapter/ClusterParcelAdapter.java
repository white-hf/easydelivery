package com.hf.easydelivery.view.Adapter;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.MapView;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.MarkerOptions;
import com.hf.easydelivery.R;
import com.hf.easydelivery.dao.DeliveryInfo;

import java.util.ArrayList;
import java.util.List;

/**
 * 聚合列表适配器：行内可展开操作与小地图。小地图懒加载，收起/回收时销毁以节省资源。
 */
public class ClusterParcelAdapter extends RecyclerView.Adapter<ClusterParcelAdapter.VH> {
    public interface OnItemClick {
        void onClick(DeliveryInfo info);
    }

    public interface OnActionClick {
        void onCall(DeliveryInfo info);
        void onSms(DeliveryInfo info);
        void onShare(DeliveryInfo info);
    }

    private final List<DeliveryInfo> data = new ArrayList<>();
    private final OnItemClick click;
    private final OnActionClick actionClick;
    private int expandedPosition = -1;

    public ClusterParcelAdapter(List<DeliveryInfo> items, OnItemClick click, OnActionClick actionClick) {
        if (items != null) data.addAll(items);
        this.click = click;
        this.actionClick = actionClick;
    }

    @NonNull
    @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_cluster_parcel, parent, false);
        return new VH(v);
    }

    @Override
    public void onBindViewHolder(@NonNull VH h, int pos) {
        DeliveryInfo info = data.get(pos);
        h.tvRoute.setText(info.getRouteNumber());

        String streetNo = String.valueOf(info.getCivilNumber());
        String unitNo = info.getUnitNumber() != null ? info.getUnitNumber() : "无";
        String line1 = streetNo + "号 " + unitNo + "单元    " + info.getName();
        String line2 = "地址: " + info.getAddress();
        h.tvDetail.setText(line1 + "\n" + line2);

        h.itemView.setOnClickListener(v -> {
            expandedPosition = -1;
            notifyDataSetChanged();
            if (click != null) click.onClick(info);
        });

        h.btnExpand.setOnClickListener(v -> {
            expandedPosition = (expandedPosition == pos) ? -1 : pos;
            notifyDataSetChanged();
        });

        boolean expanded = expandedPosition == pos;
        h.actions.setVisibility(expanded ? View.VISIBLE : View.GONE);
        h.mapContainer.setVisibility(expanded ? View.VISIBLE : View.GONE);
        if (expanded) {
            h.bindMap(info);
        } else {
            h.destroyMap();
        }

        h.btnCall.setOnClickListener(v -> {
            if (actionClick != null) actionClick.onCall(info);
        });
        h.btnSms.setOnClickListener(v -> {
            if (actionClick != null) actionClick.onSms(info);
        });
        h.btnShare.setOnClickListener(v -> {
            if (actionClick != null) actionClick.onShare(info);
        });
    }

    @Override
    public void onViewRecycled(@NonNull VH holder) {
        super.onViewRecycled(holder);
        holder.destroyMap();
    }

    @Override
    public int getItemCount() {
        return data.size();
    }

    static class VH extends RecyclerView.ViewHolder {
        final TextView tvRoute, tvDetail;
        final View btnExpand;
        final View actions;
        final View btnCall, btnSms, btnShare;
        final ViewGroup mapContainer;
        MapView mapView;
        GoogleMap miniMap;

        VH(@NonNull View itemView) {
            super(itemView);
            tvRoute = itemView.findViewById(R.id.tv_route);
            tvDetail = itemView.findViewById(R.id.tv_detail);
            btnExpand = itemView.findViewById(R.id.btn_expand);
            actions = itemView.findViewById(R.id.actions_container);
            btnCall = itemView.findViewById(R.id.btn_call);
            btnSms = itemView.findViewById(R.id.btn_sms);
            btnShare = itemView.findViewById(R.id.btn_share);
            mapContainer = itemView.findViewById(R.id.map_container);
        }

        void bindMap(DeliveryInfo info) {
            if (mapContainer == null) return;
            if (mapView == null) {
                mapView = new MapView(itemView.getContext());
                mapContainer.setVisibility(View.VISIBLE);
                mapContainer.post(() -> {
                    if (mapView.getParent() == null) {
                        mapContainer.setTag(mapView);
                        mapContainer.setClickable(false);
                        mapContainer.setFocusable(false);
                        mapContainer.setEnabled(false);
                        mapContainer.addView(mapView,
                                new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                                        ViewGroup.LayoutParams.MATCH_PARENT));
                        mapView.onCreate(null);
                        mapView.onResume();
                        mapView.getMapAsync(new OnMapReadyCallback() {
                            @Override
                            public void onMapReady(@NonNull GoogleMap googleMap) {
                                miniMap = googleMap;
                                miniMap.getUiSettings().setAllGesturesEnabled(false);
                                miniMap.getUiSettings().setMapToolbarEnabled(false);
                                miniMap.clear();
                                LatLng target = new LatLng(info.getLatitude(), info.getLongitude());
                                miniMap.addMarker(new MarkerOptions().position(target).title(info.getRouteNumber()));
                                miniMap.moveCamera(CameraUpdateFactory.newLatLngZoom(target, 16f));
                            }
                        });
                    }
                });
            } else {
                mapView.onResume();
                if (miniMap != null) {
                    miniMap.clear();
                    LatLng target = new LatLng(info.getLatitude(), info.getLongitude());
                    miniMap.addMarker(new MarkerOptions().position(target).title(info.getRouteNumber()));
                    miniMap.moveCamera(CameraUpdateFactory.newLatLngZoom(target, 16f));
                }
            }
        }

        void destroyMap() {
            if (mapView != null) {
                try {
                    mapView.onPause();
                    mapView.onDestroy();
                    if (mapContainer instanceof ViewGroup) {
                        ((ViewGroup) mapContainer).removeAllViews();
                    }
                } catch (Exception ignored) {
                }
                mapView = null;
                miniMap = null;
            }
        }
    }
}
