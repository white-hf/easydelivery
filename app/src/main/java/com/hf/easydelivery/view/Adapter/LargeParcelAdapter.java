package com.hf.easydelivery.view.Adapter;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.hf.easydelivery.R;
import com.hf.easydelivery.core.LargeParcelStore;

import java.util.ArrayList;
import java.util.List;

public class LargeParcelAdapter extends RecyclerView.Adapter<LargeParcelAdapter.VH> {
    private final List<LargeParcelStore.Entry> data = new ArrayList<>();

    public void submit(List<LargeParcelStore.Entry> entries) {
        data.clear();
        if (entries != null) {
            data.addAll(entries);
        }
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_large_parcel, parent, false);
        return new VH(v);
    }

    @Override
    public void onBindViewHolder(@NonNull VH holder, int position) {
        LargeParcelStore.Entry e = data.get(position);
        holder.tvTracking.setText(e.tracking);
        holder.tvRoute.setText(e.route);
    }

    @Override
    public int getItemCount() {
        return data.size();
    }

    static class VH extends RecyclerView.ViewHolder {
        TextView tvTracking, tvRoute;
        VH(@NonNull View itemView) {
            super(itemView);
            tvTracking = itemView.findViewById(R.id.tv_tracking);
            tvRoute = itemView.findViewById(R.id.tv_route);
        }
    }
}
