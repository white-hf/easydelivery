package com.hf.easydelivery.apartment;

import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.widget.EditText;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.hf.easydelivery.R;
import com.hf.easydelivery.dao.ApartmentPhotoEntity;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class ApartmentGalleryActivity extends AppCompatActivity implements ApartmentPhotoAdapter.Listener {

    private RecyclerView recyclerView;
    private View emptyView;
    private ApartmentPhotoAdapter adapter;
    private ApartmentPhotoService service;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_apartment_gallery);
        setTitle(R.string.my_apartment_title);
        service = ApartmentPhotoService.getInstance(this);
        recyclerView = findViewById(R.id.recycler_apartment);
        emptyView = findViewById(R.id.apartment_empty_view);
        adapter = new ApartmentPhotoAdapter(this);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        recyclerView.setAdapter(adapter);
        loadData();
    }

    private void loadData() {
        executor.execute(() -> {
            List<ApartmentPhotoEntity> list = service.listAll();
            // 自动清理脏数据（无文件路径或文件不存在）
            List<ApartmentPhotoEntity> cleaned = new ArrayList<>();
            if (list != null) {
                for (ApartmentPhotoEntity e : list) {
                    if (e == null || TextUtils.isEmpty(e.filePath)) {
                        if (e != null) service.delete(e);
                        continue;
                    }
                    java.io.File f = new java.io.File(e.filePath);
                    if (!f.exists()) {
                        service.delete(e);
                        continue;
                    }
                    cleaned.add(e);
                }
            }
            runOnUiThread(() -> {
                List<ApartmentPhotoEntity> safeList = cleaned;
                adapter.submitList(safeList);
                emptyView.setVisibility(safeList.isEmpty() ? View.VISIBLE : View.GONE);
            });
        });
    }

    @Override
    public void onDelete(@NonNull ApartmentPhotoEntity entity) {
        new AlertDialog.Builder(this)
                .setMessage(R.string.my_apartment_delete_confirm)
                .setPositiveButton(android.R.string.ok, (dialog, which) -> executor.execute(() -> {
                    service.delete(entity);
                    loadData();
                }))
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    @Override
    public void onEdit(@NonNull ApartmentPhotoEntity entity) {
        final EditText input = new EditText(this);
        input.setHint(R.string.my_apartment_edit_hint);
        input.setText(TextUtils.isEmpty(entity.displayAddress) ? entity.addressKey : entity.displayAddress);
        new AlertDialog.Builder(this)
                .setTitle(R.string.my_apartment_edit_title)
                .setView(input)
                .setPositiveButton(android.R.string.ok, (dialog, which) -> {
                    String raw = input.getText().toString();
                    String normalized = ApartmentAddressKeyBuilder.manualKeyFromInput(raw);
                    if (TextUtils.isEmpty(normalized)) {
                        showError();
                        return;
                    }
                    executor.execute(() -> {
                        service.updateAddressKey(entity.id, normalized, raw);
                        loadData();
                    });
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    private void showError() {
        runOnUiThread(() -> android.widget.Toast.makeText(this, R.string.my_apartment_edit_error, android.widget.Toast.LENGTH_SHORT).show());
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        executor.shutdown();
    }
}
