package com.hf.easydelivery.view.Adapter;
import android.view.GestureDetector;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.hf.easydelivery.R;
import com.hf.easydelivery.ResourceMgr;
import com.hf.easydelivery.dao.PackageEntity;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class PackageAdapter extends RecyclerView.Adapter<PackageAdapter.PackageViewHolder> {

    private List<PackageEntity> packages;

    @NonNull
    @Override
    public PackageViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View itemView = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.package_item, parent, false);
        return new PackageViewHolder(itemView);
    }

    @Override
    public void onBindViewHolder(@NonNull PackageViewHolder holder, int position) {
        PackageEntity currentPackage = packages.get(position);
        holder.trackingId.setText(currentPackage.trackingId);

        SimpleDateFormat dateFormat = new SimpleDateFormat("yy-MM-dd HH:mm", Locale.getDefault());
        holder.saveTime.setText(dateFormat.format(new Date(currentPackage.saveTime)));
        holder.driverId.setText(String.valueOf(currentPackage.driverId));
    }

    @Override
    public int getItemCount() {
        return packages != null ? packages.size() : 0;
    }

    public void setPackages(List<PackageEntity> packages) {
        this.packages = packages;
        notifyDataSetChanged();
    }

    static class PackageViewHolder extends RecyclerView.ViewHolder {
        private TextView trackingId;
        private TextView saveTime;
        private TextView driverId;

        public PackageViewHolder(@NonNull View itemView) {
            super(itemView);
            trackingId = itemView.findViewById(R.id.tracking_id);
            saveTime = itemView.findViewById(R.id.save_time);
            driverId = itemView.findViewById(R.id.driver_id);

            GestureDetector gestureDetector = new GestureDetector(itemView.getContext(), new GestureDetector.SimpleOnGestureListener() {
                @Override
                public boolean onDoubleTap(MotionEvent e) {
                    // 双击事件
                    int position = getBindingAdapterPosition();
                    if (position != RecyclerView.NO_POSITION) {
                        // 执行双击处理逻辑
                        onItemDoubleClick(position);
                    }
                    return true;
                }


            });

            itemView.setOnTouchListener((v, event) -> gestureDetector.onTouchEvent(event));

        }

        private void onItemDoubleClick(int position) {

            if (position >= 0 && position < ((PackageAdapter) ((RecyclerView) itemView.getParent()).getAdapter()).packages.size()) {
                PackageEntity packageEntity = ((PackageAdapter) ((RecyclerView) itemView.getParent()).getAdapter()).packages.get(position);

                //put the failed data to uploading queue again
                ResourceMgr.getInstance().getPendingPackagesMgr().addQueue(packageEntity , true);
                Toast.makeText(itemView.getContext(), "Uploading again:" + packageEntity.trackingId, Toast.LENGTH_SHORT).show();
            }
        }
    }
}
