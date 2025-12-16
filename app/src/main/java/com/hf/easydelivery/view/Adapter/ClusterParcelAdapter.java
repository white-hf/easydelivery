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
        void onLocate(DeliveryInfo info);
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
        boolean isLarge = com.hf.easydelivery.core.LargeParcelStore.isLarge(h.itemView.getContext(), info);
        h.tvLargeBadge.setVisibility(isLarge ? View.VISIBLE : View.GONE);

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

        h.btnCall.setOnClickListener(v -> {
            if (actionClick != null) actionClick.onCall(info);
        });
        h.btnSms.setOnClickListener(v -> {
            if (actionClick != null) actionClick.onSms(info);
        });
        h.btnShare.setOnClickListener(v -> {
            if (actionClick != null) actionClick.onShare(info);
        });
        h.btnLocate.setOnClickListener(v -> {
            if (actionClick != null) actionClick.onLocate(info);
        });
    }

    @Override
    public void onViewRecycled(@NonNull VH holder) {
        super.onViewRecycled(holder);
    }

    @Override
    public int getItemCount() {
        return data.size();
    }

    static class VH extends RecyclerView.ViewHolder {
        final TextView tvRoute, tvDetail, tvLargeBadge;
        final View btnExpand;
        final View actions;
        final View btnCall, btnSms, btnShare, btnLocate;

        VH(@NonNull View itemView) {
            super(itemView);
            tvRoute = itemView.findViewById(R.id.tv_route);
            tvLargeBadge = itemView.findViewById(R.id.tv_large_badge);
            tvDetail = itemView.findViewById(R.id.tv_detail);
            btnExpand = itemView.findViewById(R.id.btn_expand);
            actions = itemView.findViewById(R.id.actions_container);
            btnCall = itemView.findViewById(R.id.btn_call);
            btnSms = itemView.findViewById(R.id.btn_sms);
            btnShare = itemView.findViewById(R.id.btn_share);
            btnLocate = itemView.findViewById(R.id.btn_locate);
        }
    }
}
