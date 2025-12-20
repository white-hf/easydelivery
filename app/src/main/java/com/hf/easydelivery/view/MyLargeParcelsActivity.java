package com.hf.easydelivery.view;

import android.os.Bundle;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.os.VibratorManager;
import android.text.TextUtils;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.view.PreviewView;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ImageAnalysis;
import androidx.camera.core.Preview;
import androidx.core.content.ContextCompat;

import com.hf.easydelivery.R;
import com.hf.easydelivery.core.LargeParcelStore;
import com.hf.easydelivery.view.Adapter.LargeParcelAdapter;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.mlkit.vision.barcode.common.Barcode;
import com.google.mlkit.vision.barcode.BarcodeScanner;
import com.google.mlkit.vision.barcode.BarcodeScannerOptions;
import com.google.mlkit.vision.barcode.BarcodeScanning;
import com.google.mlkit.vision.common.InputImage;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import java.util.List;

/**
 * Secondary flow: My Large Parcels
 * - Simple “scan or enter tracking” input + list.
 * - Single active dataset; user can clear all.
 * - No extra metadata.
 */
public class MyLargeParcelsActivity extends AppCompatActivity {

    private EditText etTracking;
    private EditText etRoute;
    private LargeParcelAdapter adapter;
    private PreviewView previewView;
    private ExecutorService cameraExecutor;
    private BarcodeScanner barcodeScanner;
    private long lastDetectMs = 0L;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_my_large_parcels);

        ImageButton btnBack = findViewById(R.id.btn_back);
        btnBack.setOnClickListener(v -> finish());

        previewView = findViewById(R.id.preview_view);
        etTracking = findViewById(R.id.et_tracking);
        etRoute = findViewById(R.id.et_route);
        Button btnAdd = findViewById(R.id.btn_add);
        Button btnClear = findViewById(R.id.btn_clear);
        RecyclerView rv = findViewById(R.id.rv_large_parcels);
        rv.setLayoutManager(new LinearLayoutManager(this));
        adapter = new LargeParcelAdapter();
        rv.setAdapter(adapter);

        btnAdd.setOnClickListener(v -> {
            String tracking = etTracking.getText().toString().trim();
            String route = etRoute.getText().toString().trim();
            if (TextUtils.isEmpty(tracking)) {
                etTracking.setError("请输入或扫描包裹号");
                return;
            }
            LargeParcelStore.add(this, tracking, route);
            etTracking.setText("");
            etRoute.setText("");
            refresh();
        });

        btnClear.setOnClickListener(v -> {
            LargeParcelStore.clear(this);
            refresh();
        });

        // 初始化扫码
        cameraExecutor = Executors.newSingleThreadExecutor();
        barcodeScanner = BarcodeScanning.getClient(new BarcodeScannerOptions.Builder()
                .setBarcodeFormats(Barcode.FORMAT_ALL_FORMATS)
                .build());
        startCamera();

        refresh();
    }

    private void refresh() {
        List<LargeParcelStore.Entry> list = LargeParcelStore.list(this);
        adapter.submit(list);
        View empty = findViewById(R.id.empty_view);
        empty.setVisibility(list.isEmpty() ? View.VISIBLE : View.GONE);
    }

    private void startCamera() {
        ListenableFuture<ProcessCameraProvider> cameraProviderFuture = ProcessCameraProvider.getInstance(this);
        cameraProviderFuture.addListener(() -> {
            try {
                ProcessCameraProvider cameraProvider = cameraProviderFuture.get();
                cameraProvider.unbindAll();

                Preview preview = new Preview.Builder().build();
                preview.setSurfaceProvider(previewView.getSurfaceProvider());

                ImageAnalysis analysis = new ImageAnalysis.Builder()
                        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                        .build();
                analysis.setAnalyzer(cameraExecutor, imageProxy -> {
                    try {
                        if (System.currentTimeMillis() - lastDetectMs < 800) {
                            imageProxy.close();
                            return;
                        }
                        @androidx.camera.core.ExperimentalGetImage
                        android.media.Image mediaImage = imageProxy.getImage();
                        if (mediaImage == null) {
                            imageProxy.close();
                            return;
                        }
                        InputImage img = InputImage.fromMediaImage(mediaImage, imageProxy.getImageInfo().getRotationDegrees());
                        final MyLargeParcelsActivity activity = MyLargeParcelsActivity.this;
                        final long captureTimeMs = System.currentTimeMillis();
                        barcodeScanner.process(img)
                                .addOnSuccessListener(barcodes -> {
                                    if (barcodes != null && !barcodes.isEmpty()) {
                                        String raw = null;
                                        for (Barcode b : barcodes) {
                                            if (!TextUtils.isEmpty(b.getRawValue())) {
                                                raw = b.getRawValue();
                                                break;
                                            }
                                        }
                                            final String finalRaw = raw;
                                            if (!TextUtils.isEmpty(finalRaw)) {
                                                lastDetectMs = captureTimeMs;
                                                runOnUiThread(() -> {
                                                    LargeParcelStore.add(activity, finalRaw, "");
                                                    vibrateShort();
                                                    refresh();
                                                });
                                            }
                                        }
                                    })
                                .addOnCompleteListener(t -> imageProxy.close());
                    } catch (Exception e) {
                        imageProxy.close();
                    }
                });

                CameraSelector selector = CameraSelector.DEFAULT_BACK_CAMERA;
                cameraProvider.bindToLifecycle(this, selector, preview, analysis);
            } catch (Exception e) {
                // ignore
            }
        }, ContextCompat.getMainExecutor(this));
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (barcodeScanner != null) {
            barcodeScanner.close();
        }
        if (cameraExecutor != null) {
            cameraExecutor.shutdown();
        }
    }

    private void vibrateShort() {
        try {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
                VibratorManager vm = (VibratorManager) getSystemService(VIBRATOR_MANAGER_SERVICE);
                if (vm != null) {
                    vm.getDefaultVibrator().vibrate(VibrationEffect.createOneShot(60, VibrationEffect.DEFAULT_AMPLITUDE));
                }
            } else {
                Vibrator v = (Vibrator) getSystemService(VIBRATOR_SERVICE);
                if (v != null) {
                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                        v.vibrate(VibrationEffect.createOneShot(60, VibrationEffect.DEFAULT_AMPLITUDE));
                    } else {
                        v.vibrate(60);
                    }
                }
            }
        } catch (Exception ignore) { }
    }
}
