package com.hf.easydelivery.adapter;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.hf.easydelivery.R;
import com.hf.easydelivery.dao.PackageEntity;

import java.util.ArrayList;
import java.util.List;

public class DeliveredPackagesAdapter extends RecyclerView.Adapter<DeliveredPackagesAdapter.ViewHolder> {

    public interface OnItemDoubleClickListener {
        void onItemDoubleClick(PackageEntity item);
    }

    private List<PackageEntity> packages = new ArrayList<>();
    private final OnItemDoubleClickListener doubleClickListener;

    public DeliveredPackagesAdapter(OnItemDoubleClickListener doubleClickListener) {
        this.doubleClickListener = doubleClickListener;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_delivered_package, parent, false);
        return new ViewHolder(view, doubleClickListener);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        PackageEntity packageEntity = packages.get(position);
        holder.bind(packageEntity);
    }

    @Override
    public int getItemCount() {
        return packages.size();
    }

    public void setPackages(List<PackageEntity> packages) {
        this.packages = packages;
        notifyDataSetChanged();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {

        TextView trackingId;
        TextView status;
        TextView saveTime;
        private long lastClickTime = 0;
        private static final long DOUBLE_CLICK_TIME_DELTA = 300; // milliseconds
        private final OnItemDoubleClickListener doubleClickListener;

        public ViewHolder(@NonNull View itemView, OnItemDoubleClickListener listener) {
            super(itemView);
            this.doubleClickListener = listener;
            trackingId = itemView.findViewById(R.id.tracking_id);
            status = itemView.findViewById(R.id.status);
            saveTime = itemView.findViewById(R.id.save_time);
        }

        public void bind(final PackageEntity packageEntity) {
            trackingId.setText(packageEntity.trackingId);
            status.setText(packageEntity.status);
            if (saveTime != null) {
                java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("HH:mm:ss",
                        java.util.Locale.getDefault());
                saveTime.setText(sdf.format(new java.util.Date(packageEntity.createTime)));
            }
            itemView.setOnClickListener(v -> {
                long clickTime = System.currentTimeMillis();
                if (clickTime - lastClickTime < DOUBLE_CLICK_TIME_DELTA) {
                    if (doubleClickListener != null) {
                        doubleClickListener.onItemDoubleClick(packageEntity);
                    }
                }
                lastClickTime = clickTime;
            });
        }
    }
}
