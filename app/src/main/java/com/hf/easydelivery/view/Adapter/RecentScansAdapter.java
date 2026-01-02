package com.hf.easydelivery.view.Adapter;


import android.content.Context;
import android.graphics.Typeface;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.style.AbsoluteSizeSpan;
import android.text.style.ForegroundColorSpan;
import android.text.style.StyleSpan;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
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

    @Override
    public void onBindViewHolder(
            @NonNull ViewHolder holder, int pos) {
        ScanItem it = items.get(pos);
        Context context = holder.itemView.getContext();
        String placeholder = context.getString(R.string.scan_placeholder);
        String packageValue = it.getPackageNo() != null ? it.getPackageNo() : placeholder;
        holder.tvPackageNo.setText(buildLine(context,
                context.getString(R.string.scan_label_package), packageValue, true));
        holder.tvWaybillNo.setText(buildLine(context,
                context.getString(R.string.scan_label_waybill), it.getWaybillNo(), false));

        if (it.isScanned() && !it.isUploaded()) {
            holder.cardContainer.setBackgroundResource(R.drawable.bg_scan_item_warning);
            holder.ivUnsynced.setImageResource(android.R.drawable.stat_sys_warning);
            holder.ivUnsynced.setVisibility(View.VISIBLE);
        } else {
            holder.cardContainer.setBackgroundResource(R.drawable.bg_scan_item_normal);
            holder.ivUnsynced.setVisibility(View.GONE);
        }
    }

    private CharSequence buildLine(Context context, String label, String value, boolean emphasize) {
        SpannableStringBuilder builder = new SpannableStringBuilder();
        int labelColor = ContextCompat.getColor(context, R.color.scan_list_label);
        int valueColor = emphasize
                ? ContextCompat.getColor(context, R.color.scan_result_accent)
                : ContextCompat.getColor(context, R.color.scan_list_value);

        int labelStart = builder.length();
        builder.append(label).append(context.getString(R.string.scan_label_separator));
        builder.setSpan(new ForegroundColorSpan(labelColor), labelStart, builder.length(),
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        builder.setSpan(new AbsoluteSizeSpan(13, true), labelStart, builder.length(),
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);

        int valueStart = builder.length();
        builder.append(value != null ? value : context.getString(R.string.scan_placeholder));
        builder.setSpan(new ForegroundColorSpan(valueColor), valueStart, builder.length(),
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        builder.setSpan(new StyleSpan(Typeface.BOLD), valueStart, builder.length(),
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        int valueSizeSp = emphasize ? 20 : 15;
        builder.setSpan(new AbsoluteSizeSpan(valueSizeSp, true), valueStart, builder.length(),
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        return builder;
    }

    @Override public int getItemCount() {
        return items.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        TextView tvPackageNo, tvWaybillNo;
        ImageView ivUnsynced;
        View cardContainer;
        ViewHolder(View v) {
            super(v);
            tvPackageNo  = v.findViewById(R.id.tvItemPackageNo);
            tvWaybillNo  = v.findViewById(R.id.tvItemWaybillNo);
            ivUnsynced = v.findViewById(R.id.ivUnsynced);
            cardContainer = v.findViewById(R.id.text_container);
        }
    }
}
