package com.hf.easydelivery.view.Adapter;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.hf.easydelivery.R;
import com.hf.easydelivery.dao.DeliveryInfo;

import java.util.ArrayList;
import java.util.List;

public class ClusterParcelAdapter extends RecyclerView.Adapter<ClusterParcelAdapter.VH> {
    public interface OnItemClick {
        void onClick(DeliveryInfo info);
    }

    private final List<DeliveryInfo> data = new ArrayList<>();
    private final OnItemClick click;

    public ClusterParcelAdapter(List<DeliveryInfo> items, OnItemClick click) {
        if (items != null) data.addAll(items);
        this.click = click;
    }

    @NonNull @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_cluster_parcel, parent, false);
        return new VH(v);
    }

    @Override
    public void onBindViewHolder(@NonNull VH h, int pos) {
        DeliveryInfo info = data.get(pos);
        h.tvRoute.setText(info.getRouteNumber()); // 粗体 17sp

        // iOS 同款文案："{streetNo}号 {unitNo}单元    {name}\n地址: {address}"
        String streetNo = String.valueOf(info.getCivilNumber()); // 需要你的 DeliveryInfo 提供
        String unitNo   = info.getUnitNumber() != null ? info.getUnitNumber() : "无";               // 需要你的 DeliveryInfo 提供
        String line1 = streetNo + "号 " + unitNo + "单元    " + info.getName();
        String line2 = "地址: " + info.getAddress();
        h.tvDetail.setText(line1 + "\n" + line2);

        h.itemView.setOnClickListener(v -> {
            if (click != null) click.onClick(info);
        });
    }

    @Override
    public int getItemCount() { return data.size(); }

    static class VH extends RecyclerView.ViewHolder {
        final TextView tvRoute, tvDetail;
        VH(@NonNull View itemView) {
            super(itemView);
            tvRoute  = itemView.findViewById(R.id.tv_route);
            tvDetail = itemView.findViewById(R.id.tv_detail);
        }
    }
}