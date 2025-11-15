package com.hf.easydelivery.view.Adapter;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.hf.easydelivery.R;
import com.hf.easydelivery.domain.WorkStatsRepository;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class WorkStatsAdapter extends RecyclerView.Adapter<WorkStatsAdapter.StatViewHolder> {

    public interface OnItemClickListener {
        void onItemClick(WorkStatsRepository.WorkDayStat item);
    }

    private final List<WorkStatsRepository.WorkDayStat> items = new ArrayList<>();
    private final SimpleDateFormat dayFormatter = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
    private final OnItemClickListener listener;

    public WorkStatsAdapter(OnItemClickListener listener) {
        this.listener = listener;
    }

    public void submitList(List<WorkStatsRepository.WorkDayStat> data) {
        items.clear();
        if (data != null) {
            items.addAll(data);
        }
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public StatViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_work_day_stat, parent, false);
        return new StatViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull StatViewHolder holder, int position) {
        WorkStatsRepository.WorkDayStat stat = items.get(position);
        holder.dateText.setText(dayFormatter.format(dateFromKey(stat.dateKey)));
        holder.parcelText.setText(String.valueOf(stat.deliveredPackages));
        holder.distanceText.setText(String.format(Locale.getDefault(), "%.1f", stat.distanceKilometers()));
        holder.itemView.setOnClickListener(v -> {
            if (listener != null) {
                listener.onItemClick(stat);
            }
        });
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    private Date dateFromKey(long dateKey) {
        int year = (int) (dateKey / 10000);
        int month = (int) ((dateKey % 10000) / 100) - 1;
        int day = (int) (dateKey % 100);
        Calendar calendar = Calendar.getInstance();
        calendar.clear();
        calendar.set(year, month, day, 0, 0, 0);
        return calendar.getTime();
    }

    static class StatViewHolder extends RecyclerView.ViewHolder {
        final TextView dateText;
        final TextView parcelText;
        final TextView distanceText;

        StatViewHolder(@NonNull View itemView) {
            super(itemView);
            dateText = itemView.findViewById(R.id.tv_day_date);
            parcelText = itemView.findViewById(R.id.tv_day_parcels);
            distanceText = itemView.findViewById(R.id.tv_day_distance);
        }
    }
}
