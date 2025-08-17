package com.hf.easydelivery.view.Adapter;


import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.hf.easydelivery.R;
import com.hf.easydelivery.bean.ScanItem;

import java.util.List;

public class RecentScansAdapter
        extends RecyclerView.Adapter<RecentScansAdapter.ViewHolder> {

    private final List<ScanItem> items;
    public RecentScansAdapter(List<ScanItem> items) {
        this.items = items;
    }

    @NonNull @Override
    public ViewHolder onCreateViewHolder(
            @NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_recent_scan, parent, false);
        return new ViewHolder(view);
    }

    @Override public void onBindViewHolder(
            @NonNull ViewHolder holder, int pos) {
        ScanItem it = items.get(pos);
        holder.tvPackageNo.setText(
                "包裹：" + (it.getPackageNo() != null ? it.getPackageNo() : "—"));
        holder.tvWaybillNo.setText("运单：" + it.getWaybillNo());
    }

    @Override public int getItemCount() {
        return items.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        TextView tvPackageNo, tvWaybillNo;
        ViewHolder(View v) {
            super(v);
            tvPackageNo  = v.findViewById(R.id.tvItemPackageNo);
            tvWaybillNo  = v.findViewById(R.id.tvItemWaybillNo);
        }
    }
}
