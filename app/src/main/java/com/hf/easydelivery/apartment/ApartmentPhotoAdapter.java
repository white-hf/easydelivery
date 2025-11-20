package com.hf.easydelivery.apartment;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.text.TextUtils;
import android.text.format.DateFormat;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.hf.easydelivery.R;
import com.hf.easydelivery.dao.ApartmentPhotoEntity;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

class ApartmentPhotoAdapter extends RecyclerView.Adapter<ApartmentPhotoAdapter.PhotoViewHolder> {

    interface Listener {
        void onDelete(@NonNull ApartmentPhotoEntity entity);
        void onEdit(@NonNull ApartmentPhotoEntity entity);
    }

    private final Listener listener;
    private final List<ApartmentPhotoEntity> items = new ArrayList<>();

    ApartmentPhotoAdapter(Listener listener) {
        this.listener = listener;
    }

    void submitList(List<ApartmentPhotoEntity> data) {
        items.clear();
        if (data != null) {
            items.addAll(data);
        }
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public PhotoViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_apartment_photo, parent, false);
        return new PhotoViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull PhotoViewHolder holder, int position) {
        ApartmentPhotoEntity entity = items.get(position);
        holder.bind(entity, listener);
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    static class PhotoViewHolder extends RecyclerView.ViewHolder {
        private final ImageView preview;
        private final TextView address;
        private final TextView meta;
        private final TextView sourceTag;
        private final ImageButton deleteBtn;
        private final ImageButton editBtn;

        PhotoViewHolder(@NonNull View itemView) {
            super(itemView);
            preview = itemView.findViewById(R.id.apartment_thumb);
            address = itemView.findViewById(R.id.apartment_address);
            meta = itemView.findViewById(R.id.apartment_meta);
            sourceTag = itemView.findViewById(R.id.apartment_source);
            deleteBtn = itemView.findViewById(R.id.apartment_delete);
            editBtn = itemView.findViewById(R.id.apartment_edit);
        }

        void bind(ApartmentPhotoEntity entity, Listener listener) {
            String display = !TextUtils.isEmpty(entity.displayAddress) ? entity.displayAddress : entity.addressKey;
            address.setText(display);
            CharSequence time = DateFormat.format("yyyy-MM-dd HH:mm", entity.savedAt);
            meta.setText(time);
            sourceTag.setText(ApartmentPhotoEntity.SOURCE_MANUAL.equals(entity.source)
                    ? R.string.my_apartment_source_manual
                    : R.string.my_apartment_source_auto);
            if (!ApartmentPhotoEntity.SOURCE_MANUAL.equals(entity.source)) {
                editBtn.setVisibility(View.GONE);
            } else {
                editBtn.setVisibility(View.VISIBLE);
            }

            if (!TextUtils.isEmpty(entity.filePath)) {
                File file = new File(entity.filePath);
                if (file.exists()) {
                    Bitmap bitmap = BitmapFactory.decodeFile(file.getAbsolutePath());
                    preview.setImageBitmap(bitmap);
                } else {
                    preview.setImageResource(R.drawable.ic_gallery);
                }
            } else {
                preview.setImageResource(R.drawable.ic_gallery);
            }

            deleteBtn.setOnClickListener(v -> listener.onDelete(entity));
            editBtn.setOnClickListener(v -> listener.onEdit(entity));
        }
    }
}
