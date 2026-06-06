package com.hf.easydelivery.view.Adapter;

import android.content.Context;
import android.text.TextUtils;
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

        Context context = h.itemView.getContext();
        String streetNo = info.getCivilNumber() != null && info.getCivilNumber() > 0
                ? context.getString(R.string.package_list_street_number_format,
                        String.valueOf(info.getCivilNumber()))
                : context.getString(R.string.map_street_number_unknown);
        String unitRaw = info.getUnitNumber();
        String unitNo = TextUtils.isEmpty(unitRaw)
                ? context.getString(R.string.parcel_unit_unknown)
                : context.getString(R.string.package_list_unit_format, unitRaw);
        String name = info.getName() == null ? "" : info.getName().trim();
        String line1 = context.getString(R.string.cluster_line1_format, streetNo, unitNo, "").trim();
        String address = info.getAddress() == null
                ? context.getString(R.string.map_placeholder)
                : info.getAddress();
        String line2 = context.getString(R.string.cluster_line2_format, address);
        h.tvLine1.setText(line1);
        h.tvRecipient.setText(TextUtils.isEmpty(name) ? "" : name);
        h.tvRecipient.setVisibility(TextUtils.isEmpty(name) ? View.GONE : View.VISIBLE);
        h.tvAddress.setText(line2);

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
        final TextView tvRoute, tvLine1, tvRecipient, tvAddress, tvLargeBadge;
        final View btnExpand;
        final View actions;
        final View btnCall, btnSms, btnShare, btnLocate;

        VH(@NonNull View itemView) {
            super(itemView);
            tvRoute = itemView.findViewById(R.id.tv_route);
            tvLargeBadge = itemView.findViewById(R.id.tv_large_badge);
            tvLine1 = itemView.findViewById(R.id.tv_line1);
            tvRecipient = itemView.findViewById(R.id.tv_recipient);
            tvAddress = itemView.findViewById(R.id.tv_address);
            btnExpand = itemView.findViewById(R.id.btn_expand);
            actions = itemView.findViewById(R.id.actions_container);
            btnCall = itemView.findViewById(R.id.btn_call);
            btnSms = itemView.findViewById(R.id.btn_sms);
            btnShare = itemView.findViewById(R.id.btn_share);
            btnLocate = itemView.findViewById(R.id.btn_locate);
        }
    }
}
