package com.hf.easydelivery.view;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.graphics.Rect;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.location.Location;
import android.os.SystemClock;

import androidx.camera.core.Camera;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ImageAnalysis;
import androidx.camera.core.ImageCapture;
import androidx.camera.core.ImageCaptureException;
import androidx.camera.core.ImageProxy;
import androidx.camera.core.Preview;
import androidx.camera.core.ZoomState;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.view.PreviewView;

import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.common.util.concurrent.ListenableFuture;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import android.app.ProgressDialog;

import com.hf.courierservice.bean.DeliveredUploadParams;
import com.hf.uniuni.CourierService;
import com.hf.easydelivery.api.RetryDeliveryRspCb;
import com.hf.easydelivery.ResourceMgr;
import com.hf.easydelivery.core.PendingPackagesMgr;
import androidx.appcompat.app.AppCompatActivity;
import androidx.cardview.widget.CardView;
import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.button.MaterialButton;
import com.hf.easydelivery.R;
import com.hf.easydelivery.ResourceMgr;
import com.hf.easydelivery.common.BitmapUtils;
import com.hf.courierservice.apihelper.FileLog;
import com.hf.easydelivery.core.DeliveryinfoMgr;
import com.hf.easydelivery.core.PendingPackagesMgr;
import com.hf.easydelivery.core.PowerSaverSelector;
import com.hf.easydelivery.core.SmartLocationManager;
import com.hf.easydelivery.dao.DeliveryInfo;
import com.hf.easydelivery.dao.PackageEntity;

import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;

import android.widget.EditText;
import android.widget.TextView;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.text.SimpleDateFormat;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;
import java.util.HashSet;
import java.util.Set;

import com.hf.easydelivery.common.PermissionUtils;

import android.text.TextUtils;
import android.util.Size;

import com.google.mlkit.vision.barcode.common.Barcode;
import com.google.mlkit.vision.barcode.BarcodeScanner;
import com.google.mlkit.vision.barcode.BarcodeScannerOptions;
import com.google.mlkit.vision.barcode.BarcodeScanning;
import com.google.mlkit.vision.common.InputImage;
import com.hf.easydelivery.view.Adapter.ClusterParcelAdapter;
import com.hf.easydelivery.apartment.ApartmentAddressKeyBuilder;
import com.hf.easydelivery.apartment.ApartmentPhotoService;
import com.hf.easydelivery.apartment.ApartmentPhotoService.MatchResult;

/**
 * CameraActivity（从 Fragment 完整改造为 Activity）
 * - 相机统一改用 CameraServie（共享、可避免与扫码页竞争）
 * - 修复所有 Fragment API 遗留：requireActivity()/getArguments()/view.findViewById 等
 * - UI/业务逻辑保持不变（缩略图/短信/拨号/完成校验等）
 */
public class CameraActivity extends AppCompatActivity
        implements SensorEventListener, SmartLocationManager.LocationUpdateListener {

    private static final String TAG = "CameraActivity";
    private static final int CAMERA_PERMISSION_REQUEST_CODE = 1001;
    private static final int SMS_PERMISSION_REQUEST_CODE = 1;
    private static final int CALL_PERMISSION_REQUEST_CODE = 2;
    public static final int IMAGE_COUNT = 2;
    private static final int MAX_PHOTOS = 3;
    private static final int REQUEST_CODE_PICK_IMAGE = 2001;

    // ----------- UI -----------
    private CardView infoBar;
    private TextView tvRouteNumber, tvOrderSn, tvCustomerName, tvUnitNumber, tvAddress;

    private LinearLayout thumbnailContainer;
    private final List<File> mImageFiles = new ArrayList<>();
    private final List<ImageView> mImageViews = new ArrayList<>();
    private final List<CardView> mCardViews = new ArrayList<>();

    private ImageButton captureButton, galleryButton, retakeButton;

    private MaterialButton smsButton, phoneButton, failButton, okButton;

    private FrameLayout cameraPreviewLayout;
    private PreviewView previewView;

    private ImageCapture imageCapture;

    // 传感器
    private SensorManager sensorManager;
    private Sensor accelerometer, magnetometer, lightSensor;
    private final float[] accelerometerReading = new float[3];
    private final float[] magnetometerReading = new float[3];
    private float ambientLux = Float.NaN;
    private float lastPitchDegrees = Float.NaN;

    // 业务参数
    private Long mOrderId;
    private double targetLatitude = Double.NaN;
    private double targetLongitude = Double.NaN;
    private double currentLatitude = Double.NaN;
    private double currentLongitude = Double.NaN;

    private SmsBottomSheetFragment mSmsBottomSheetFragment;
    private ApartmentPhotoService apartmentPhotoService;
    private ApartmentAddressKeyBuilder.KeyData apartmentKeyData;
    private MatchResult activeAutoApartmentMatch;
    private final Set<String> apartmentAutoFilePaths = new HashSet<>();
    private SmartLocationManager smartLocationManager;
    private Location lastKnownLocation;
    private DeliveryInfo deliveryInfo;

    // 宿主 chrome
    private View hostToolbar, hostBottomBar;

    // 相机服务
    // private final CameraService cameraServie = CameraService.getInstance();
    // private boolean cameraBound = false;
    private boolean cameraStarted = false;
    private boolean flashSupported = false;
    private boolean lastFlashOn = false;
    private static final float EXTREME_LOW_LIGHT_LUX_THRESHOLD = 5f;
    private static final float DEFAULT_ZOOM_RATIO = 1.0f;
    private static final float ZOOM_RATIO_TOLERANCE = 0.05f;
    private Camera boundCamera;
    private ImageAnalysis barcodeAnalysis;
    private BarcodeScanner labelScanner;
    private final ExecutorService analysisExecutor = Executors.newSingleThreadExecutor();

    private enum CaptureIntent {
        WAYBILL, DROP_OFF, BUILDING
    }

    private CaptureIntent captureStage = CaptureIntent.WAYBILL;
    private CaptureIntent lastResolvedIntent = CaptureIntent.WAYBILL;
    private long captureOrderId = -1L;
    private int captureSequenceIndex = 0;
    private float lastAppliedZoomRatio = DEFAULT_ZOOM_RATIO;
    private long lastZoomAdjustMillis = 0L;
    private static final long ZOOM_COMMAND_INTERVAL_MS = 120L;
    private long lastBarcodeHitMillis = 0L;
    private static final long BARCODE_HINT_TTL_MS = 2000L;
    private boolean mismatchDialogShowing = false;
    private String lastMismatchCode = null;
    private long lastBarcodeAnalysisMillis = 0L;

    // Executor for background image processing
    private final ExecutorService cameraExecutor = Executors.newSingleThreadExecutor();

    // ---------- Activity 生命周期 ----------
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_camera);

        // 1) 读取参数（从 Intent）
        Bundle args = getIntent() != null ? getIntent().getExtras() : null;
        if (args != null) {
            mOrderId = args.getLong("order_id", -1);
            targetLatitude = args.getDouble("latitude", Double.NaN);
            targetLongitude = args.getDouble("longitude", Double.NaN);
        }
        ensureCaptureSequenceSynced();
        mSmsBottomSheetFragment = new SmsBottomSheetFragment(mOrderId);
        try {
            BarcodeScannerOptions options = new BarcodeScannerOptions.Builder()
                    .setBarcodeFormats(Barcode.FORMAT_ALL_FORMATS)
                    .build();
            labelScanner = BarcodeScanning.getClient(options);
        } catch (Exception e) {
            FileLog.getInstance().error(TAG, "Failed to init barcode scanner: " + e.getMessage(), e);
        }

        // 2) 顶部信息栏
        infoBar = findViewById(R.id.info_bar);
        tvRouteNumber = findViewById(R.id.tv_route_number);
        tvOrderSn = findViewById(R.id.tv_tracking_number);
        tvCustomerName = findViewById(R.id.tv_customer_name);
        tvUnitNumber = findViewById(R.id.tv_unit_number);
        tvAddress = findViewById(R.id.tv_address);

        deliveryInfo = ResourceMgr.getInstance().getDeliveryinfoMgr().get(mOrderId);
        if (deliveryInfo != null) {
            tvRouteNumber.setText(String.valueOf(deliveryInfo.getRouteNumber()));
            tvOrderSn.setText(deliveryInfo.getOrderSn());
            tvCustomerName.setText(deliveryInfo.getName());
            tvUnitNumber.setText(deliveryInfo.getUnitNumber());
            tvAddress.setText(deliveryInfo.getAddress());

            // --- 让地址可点击进入导航 ---
            tvAddress.setClickable(true);
            tvAddress.setFocusable(true);
            tvAddress.setContentDescription(getString(R.string.tap_to_navigate));

            // 下划线效果，像可点击的链接
            tvAddress.setPaintFlags(tvAddress.getPaintFlags() | android.graphics.Paint.UNDERLINE_TEXT_FLAG);

            // 触摸水波纹反馈（有则用）
            try {
                android.util.TypedValue out = new android.util.TypedValue();
                if (getTheme().resolveAttribute(android.R.attr.selectableItemBackgroundBorderless, out, true)) {
                    tvAddress.setBackgroundResource(out.resourceId);
                }
            } catch (Exception ignore) {
            }

            // 略微增大可点区域
            int padH = (int) (8 * getResources().getDisplayMetrics().density);
            int padV = (int) (4 * getResources().getDisplayMetrics().density);
            tvAddress.setPadding(
                    tvAddress.getPaddingLeft() + padH,
                    tvAddress.getPaddingTop() + padV,
                    tvAddress.getPaddingRight() + padH,
                    tvAddress.getPaddingBottom() + padV);

            // 右侧加一个导航小图标（系统自带）
            try {
                tvAddress.setCompoundDrawablesWithIntrinsicBounds(0, 0, android.R.drawable.ic_menu_directions, 0);
                tvAddress.setCompoundDrawablePadding((int) (6 * getResources().getDisplayMetrics().density));
            } catch (Exception ignore) {
            }

            tvAddress.setOnClickListener(v -> openNavigationToPackage());
        }
        initLocationManager();

        // 3) 缩略图栏
        thumbnailContainer = findViewById(R.id.thumbnail_container);
        mImageFiles.clear();
        mImageViews.clear();
        mCardViews.clear();
        for (int i = 0; i < MAX_PHOTOS; i++) {
            CardView cardView = createThumbnailCardView(i);
            thumbnailContainer.addView(cardView);
            mImageFiles.add(null);
        }

        apartmentPhotoService = ApartmentPhotoService.getInstance(this);
        initApartmentAssist();

        // 4) 拍照/相册/重拍栏
        captureButton = findViewById(R.id.shutter_button);
        galleryButton = findViewById(R.id.gallery_button);
        retakeButton = findViewById(R.id.retake_button);

        captureButton.setOnClickListener(v -> takePicture());
        galleryButton.setOnClickListener(v -> openGallery());
        retakeButton.setOnClickListener(v -> removeLastThumbnail());

        // 5) 底部操作栏
        smsButton = findViewById(R.id.sms_button);
        phoneButton = findViewById(R.id.phone_button);
        failButton = findViewById(R.id.fail_button);
        okButton = findViewById(R.id.ok_button);

        smsButton.setOnClickListener(v -> showSmsBottomSheet());
        phoneButton.setOnClickListener(v -> makeCall());
        failButton.setOnClickListener(v -> showFailOptionsDialog());
        okButton.setOnClickListener(v -> {
            if (!hasEnoughPhotos(null)) {
                return;
            }
            warnIfFarFromTarget();
            submitPackage(0, null);
        });
        updateOkButtonState();

        // 6) 预览区
        previewView = findViewById(R.id.previewView);
        // Removed direct call to startCamera()

        // 7) 传感器
        sensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
        if (sensorManager != null) {
            accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
            magnetometer = sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD);
            lightSensor = sensorManager.getDefaultSensor(Sensor.TYPE_LIGHT);
            if (lightSensor == null) {
                FileLog.getInstance().debug(TAG, "light sensor unavailable; fallback to CameraX auto flash");
            }
        } else {
            Toast.makeText(this, "Sensor not available", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        // 8) 浮动关闭键（可选）
        ImageButton closeButton = findViewById(R.id.btn_close);
        if (closeButton == null) {
            try {
                View possibleRoot = findViewById(android.R.id.content);
                ConstraintLayout root = (possibleRoot instanceof ConstraintLayout) ? (ConstraintLayout) possibleRoot
                        : null;
                if (root != null) {
                    closeButton = new ImageButton(this);
                    int closeId = getResources().getIdentifier("btn_close", "id", getPackageName());
                    closeButton.setId(closeId != 0 ? closeId : View.generateViewId());
                    int size = (int) (40 * getResources().getDisplayMetrics().density);
                    ConstraintLayout.LayoutParams lp = new ConstraintLayout.LayoutParams(size, size);
                    lp.topToTop = ConstraintLayout.LayoutParams.PARENT_ID;
                    lp.startToStart = ConstraintLayout.LayoutParams.PARENT_ID;
                    int margin = (int) (12 * getResources().getDisplayMetrics().density);
                    lp.setMargins(margin, margin, margin, margin);
                    closeButton.setLayoutParams(lp);
                    closeButton.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
                    closeButton.setBackgroundResource(android.R.drawable.btn_default_small);
                    closeButton.setImageResource(android.R.drawable.ic_menu_close_clear_cancel);
                    closeButton.setColorFilter(android.graphics.Color.WHITE);
                    closeButton.setContentDescription(getString(android.R.string.cancel));
                    closeButton.setElevation(24f);
                    root.addView(closeButton);
                }
            } catch (Exception e) {
                FileLog.getInstance().error(TAG, "create floating close button failed", e);
            }
        }
        if (closeButton != null) {
            closeButton.setOnClickListener(v -> finish());
        } else {
            FileLog.getInstance().debug(TAG, "closeButton not found in layout, skipping listener setup");
        }

        // 9) 可选隐藏 cancel 按钮
        try {
            int cancelId = getResources().getIdentifier("cancel_button", "id", getPackageName());
            if (cancelId != 0) {
                View cb = findViewById(cancelId);
                if (cb != null)
                    cb.setVisibility(View.GONE);
            }
        } catch (Exception e) {
            FileLog.getInstance().debug(TAG, "optional cancel_button not found: " + e.getMessage());
        }

        // 10) 权限/相机准备
        initCamera();

        // 全屏沉浸
        prepareHostChromeRefs();
        enterImmersiveFullscreen();
        hideHostChrome();
    }

    private void initLocationManager() {
        if (smartLocationManager != null) {
            return;
        }
        try {
            smartLocationManager = SmartLocationManager.getInstance(getApplicationContext());
            if (smartLocationManager == null) {
                FileLog.getInstance().error(TAG,
                        "initLocationManager: SmartLocationManager unavailable (context null?)");
                return;
            }
        } catch (Exception e) {
            FileLog.getInstance().error(TAG, "initLocationManager failed: " + e.getMessage(), e);
            smartLocationManager = null;
        }
        refreshCurrentLocationSnapshot();
    }

    private void startLocationTracking() {
        initLocationManager();
        if (smartLocationManager == null) {
            return;
        }
        smartLocationManager.setLocationUpdateListener(this);
        smartLocationManager.startLocationUpdates();
        refreshCurrentLocationSnapshot();
    }

    private void stopLocationTracking() {
        if (smartLocationManager == null) {
            return;
        }
        try {
            smartLocationManager.setLocationUpdateListener(null);
            smartLocationManager.stopLocationUpdates();
        } catch (Exception e) {
            FileLog.getInstance().error(TAG, "stopLocationTracking error: " + e.getMessage(), e);
        }
    }

    private void refreshCurrentLocationSnapshot() {
        if (smartLocationManager == null) {
            return;
        }
        try {
            Location snapshot = smartLocationManager.getLastSmoothedLocation();
            if (snapshot == null) {
                snapshot = smartLocationManager.getPredictedLocation();
            }
            if (snapshot != null) {
                updateCurrentLocation(snapshot);
            }
        } catch (Exception e) {
            FileLog.getInstance().error(TAG, "refreshCurrentLocationSnapshot failed: " + e.getMessage(), e);
        }
    }

    private void updateCurrentLocation(@NonNull Location location) {
        lastKnownLocation = new Location(location);
        currentLatitude = location.getLatitude();
        currentLongitude = location.getLongitude();
    }

    private void ensureCaptureSequenceSynced() {
        long currentOrder = mOrderId != null ? mOrderId : -1L;
        if (captureOrderId != currentOrder) {
            captureOrderId = currentOrder;
            captureStage = CaptureIntent.WAYBILL;
            captureSequenceIndex = 0;
            lastResolvedIntent = CaptureIntent.WAYBILL;
            lastAppliedZoomRatio = DEFAULT_ZOOM_RATIO;
            lastBarcodeHitMillis = 0L;
            if (boundCamera != null) {
                applyProximityZoom(true);
            }
        }
    }

    private double resolveTargetLatitude() {
        if (!Double.isNaN(targetLatitude))
            return targetLatitude;
        if (deliveryInfo != null)
            return deliveryInfo.getLatitude();
        return Double.NaN;
    }

    private double resolveTargetLongitude() {
        if (!Double.isNaN(targetLongitude))
            return targetLongitude;
        if (deliveryInfo != null)
            return deliveryInfo.getLongitude();
        return Double.NaN;
    }

    private float estimateDistanceToTargetMeters() {
        double targetLat = resolveTargetLatitude();
        double targetLng = resolveTargetLongitude();
        if (Double.isNaN(targetLat) || Double.isNaN(targetLng)) {
            return Float.NaN;
        }
        double currentLat = currentLatitude;
        double currentLng = currentLongitude;
        if (Double.isNaN(currentLat) || Double.isNaN(currentLng)) {
            if (lastKnownLocation != null) {
                currentLat = lastKnownLocation.getLatitude();
                currentLng = lastKnownLocation.getLongitude();
            } else {
                return Float.NaN;
            }
        }
        float[] results = new float[1];
        Location.distanceBetween(currentLat, currentLng, targetLat, targetLng, results);
        return results[0];
    }

    @Override
    public void onLocationUpdate(Location location, SmartLocationManager.MovementState state) {
        if (location == null)
            return;
        updateCurrentLocation(location);
    }

    @Override
    protected void onStart() {
        super.onStart();
        FileLog.getInstance().debug(TAG, "onStart: no-op");
    }

    @Override
    protected void onResume() {
        super.onResume();
        startCameraIfNeeded();
        startLocationTracking();

        if (accelerometer != null) {
            sensorManager.registerListener(this, accelerometer, SensorManager.SENSOR_DELAY_NORMAL);
        }
        if (magnetometer != null) {
            sensorManager.registerListener(this, magnetometer, SensorManager.SENSOR_DELAY_NORMAL);
        }
        if (lightSensor != null) {
            sensorManager.registerListener(this, lightSensor, SensorManager.SENSOR_DELAY_NORMAL);
        }
    }

    @Override
    protected void onPause() {
        exitImmersiveFullscreen();
        showHostChrome();

        super.onPause();
        if (sensorManager != null)
            sensorManager.unregisterListener(this);
        stopLocationTracking();
        // Unbind camera on pause
        try {
            ProcessCameraProvider provider = ProcessCameraProvider.getInstance(this).get();
            provider.unbindAll();
            cameraStarted = false;
            boundCamera = null;
            flashSupported = false;
            lastFlashOn = false;
            if (barcodeAnalysis != null) {
                barcodeAnalysis.clearAnalyzer();
                barcodeAnalysis = null;
            }
            lastBarcodeAnalysisMillis = 0L;
            lastBarcodeHitMillis = 0L;
        } catch (Exception e) {
            FileLog.getInstance().error(TAG, "unbind onPause failed", e);
        }
    }

    @Override
    protected void onDestroy() {
        FileLog.getInstance().debug(TAG, "onDestroy: clean up.");
        showHostChrome();
        stopLocationTracking();
        if (!cameraExecutor.isShutdown()) {
            cameraExecutor.shutdown();
        }
        if (!analysisExecutor.isShutdown()) {
            analysisExecutor.shutdown();
        }
        if (labelScanner != null) {
            labelScanner.close();
            labelScanner = null;
        }
        lastBarcodeHitMillis = 0L;
        super.onDestroy();
    }

    // ---------- UI chrome ----------
    private void prepareHostChromeRefs() {
        try {
            String pkg = getPackageName();
            String[] toolbarNames = new String[] { "toolbar", "appbar", "top_bar" };
            for (String name : toolbarNames) {
                int resId = getResources().getIdentifier(name, "id", pkg);
                if (resId != 0) {
                    View v = findViewById(resId);
                    if (v != null) {
                        hostToolbar = v;
                        break;
                    }
                }
            }
            String[] bottomNames = new String[] { "bottom_nav", "nav_view", "tab_layout", "bottom_bar" };
            for (String name : bottomNames) {
                int resId = getResources().getIdentifier(name, "id", pkg);
                if (resId != 0) {
                    View v = findViewById(resId);
                    if (v != null) {
                        hostBottomBar = v;
                        break;
                    }
                }
            }
        } catch (Exception e) {
            FileLog.getInstance().debug("CameraActivity", "prepareHostChromeRefs error: " + e.getMessage());
        }
    }

    private void hideHostChrome() {
        try {
            if (hostToolbar != null)
                hostToolbar.setVisibility(View.GONE);
            if (hostBottomBar != null)
                hostBottomBar.setVisibility(View.GONE);
        } catch (Exception e) {
            FileLog.getInstance().debug("CameraActivity", "hideHostChrome: " + e.getMessage());
        }
    }

    private void showHostChrome() {
        try {
            if (hostToolbar != null)
                hostToolbar.setVisibility(View.VISIBLE);
            if (hostBottomBar != null)
                hostBottomBar.setVisibility(View.VISIBLE);
        } catch (Exception e) {
            FileLog.getInstance().debug("CameraActivity", "showHostChrome: " + e.getMessage());
        }
    }

    private void enterImmersiveFullscreen() {
        try {
            final Window window = getWindow();
            View decor = window.getDecorView();
            int flags = View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                    | View.SYSTEM_UI_FLAG_FULLSCREEN
                    | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                    | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                    | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                    | View.SYSTEM_UI_FLAG_LAYOUT_STABLE;
            decor.setSystemUiVisibility(flags);
            window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        } catch (Exception e) {
            FileLog.getInstance().debug("CameraActivity", "enterImmersiveFullscreen: " + e.getMessage());
        }
    }

    private void exitImmersiveFullscreen() {
        try {
            final Window window = getWindow();
            View decor = window.getDecorView();
            decor.setSystemUiVisibility(View.SYSTEM_UI_FLAG_LAYOUT_STABLE);
            window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        } catch (Exception e) {
            FileLog.getInstance().debug("CameraActivity", "exitImmersiveFullscreen: " + e.getMessage());
        }
    }

    // ---------- 传感器 ----------
    @Override
    public void onSensorChanged(SensorEvent event) {
        if (event.sensor == accelerometer) {
            System.arraycopy(event.values, 0, accelerometerReading, 0, accelerometerReading.length);
        } else if (event.sensor == magnetometer) {
            System.arraycopy(event.values, 0, magnetometerReading, 0, magnetometerReading.length);
        } else if (event.sensor == lightSensor) {
            ambientLux = event.values[0];
            applyDynamicFlashMode();
            return;
        }
        float[] rotationMatrix = new float[9];
        if (SensorManager.getRotationMatrix(rotationMatrix, null, accelerometerReading, magnetometerReading)) {
            float[] orientation = new float[3];
            SensorManager.getOrientation(rotationMatrix, orientation);
            float pitchRad = orientation[1];
            float newPitch = (float) Math.toDegrees(pitchRad);
            boolean changed = Float.isNaN(lastPitchDegrees) || Math.abs(newPitch - lastPitchDegrees) > 2f;
            lastPitchDegrees = newPitch;
            if (changed) {
                applyProximityZoom(false);
            }
        }
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {
    }

    // ---------- 权限/相机绑定（CameraServie） ----------
    private void initCamera() {
        if (PermissionUtils.hasCameraPermission(this)) {
            startCamera();
        } else {
            PermissionUtils.requestCameraPermission(this, CAMERA_PERMISSION_REQUEST_CODE);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
            @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == CAMERA_PERMISSION_REQUEST_CODE) {
            if (PermissionUtils.isPermissionGranted(grantResults)) {
                startCamera();
            } else {
                Toast.makeText(this, "Camera permission is required to use this feature", Toast.LENGTH_SHORT).show();
            }
        } else if (requestCode == CALL_PERMISSION_REQUEST_CODE) {
            if (PermissionUtils.isPermissionGranted(grantResults)) {
                makeCall();
            } else {
                Toast.makeText(this, "拨打电话需要电话权限", Toast.LENGTH_SHORT).show();
            }
        }
    }

    private void startCameraIfNeeded() {
        // CameraX handles binding automatically, call startCamera if needed
        if (!cameraStarted) {
            startCamera();
        }
    }

    // ---------- 业务/UI 工具 ----------
    private String ellipsis(String str, int maxLen) {
        if (str == null)
            return "";
        if (str.length() <= maxLen)
            return str;
        return str.substring(0, maxLen) + "...";
    }

    private void updateOkButtonState() {
        int count = (int) mImageFiles.stream().filter(Objects::nonNull).count();
        if (okButton != null) {
            boolean enabled = count >= IMAGE_COUNT;
            okButton.setEnabled(enabled);
            okButton.setAlpha(enabled ? 1f : 0.4f);
        }
    }

    private void openGallery() {
        if (mImageFiles.stream().filter(Objects::nonNull).count() >= MAX_PHOTOS) {
            Toast.makeText(this, getString(R.string.take_picture_full), Toast.LENGTH_SHORT).show();
            return;
        }
        Intent intent = new Intent(Intent.ACTION_PICK);
        intent.setType("image/*");
        startActivityForResult(intent, REQUEST_CODE_PICK_IMAGE);
    }

    private void applyProximityZoom() {
        applyProximityZoom(false);
    }

    private void applyProximityZoom(boolean force) {
        if (boundCamera == null) {
            return;
        }
        long now = SystemClock.elapsedRealtime();
        if (!force && now - lastZoomAdjustMillis < ZOOM_COMMAND_INTERVAL_MS) {
            return;
        }
        lastZoomAdjustMillis = now;
        ensureCaptureSequenceSynced();
        ZoomState zoomState = boundCamera.getCameraInfo().getZoomState().getValue();
        if (zoomState == null) {
            return;
        }
        CaptureIntent intent = resolveCaptureIntent();
        float targetZoom = computeZoomRatio(zoomState, intent);
        float currentZoom = zoomState.getZoomRatio();
        if (Math.abs(currentZoom - targetZoom) < ZOOM_RATIO_TOLERANCE) {
            lastResolvedIntent = intent;
            lastAppliedZoomRatio = currentZoom;
            return;
        }
        boundCamera.getCameraControl().setZoomRatio(targetZoom);
        lastResolvedIntent = intent;
        lastAppliedZoomRatio = targetZoom;

    }

    private CaptureIntent resolveCaptureIntent() {
        if (captureStage == CaptureIntent.WAYBILL && isBarcodeHintActive()) {
            return CaptureIntent.WAYBILL;
        }
        switch (captureStage) {
            case WAYBILL:
                return CaptureIntent.WAYBILL;
            case DROP_OFF:
                // Removed sensor logic to prevent premature switch to BUILDING
                // if (!Float.isNaN(lastPitchDegrees) && Math.abs(lastPitchDegrees) > 35f) {
                // return CaptureIntent.BUILDING;
                // }
                return CaptureIntent.DROP_OFF;
            case BUILDING:
                // if (!Float.isNaN(lastPitchDegrees) && Math.abs(lastPitchDegrees) < 15f) {
                // return CaptureIntent.WAYBILL;
                // }
                return CaptureIntent.BUILDING;
            default:
                return captureStage;
        }
    }

    private float computeZoomRatio(@NonNull ZoomState zoomState, CaptureIntent intent) {
        float baseZoom = DEFAULT_ZOOM_RATIO;
        float multiplier = intentMultiplier(intent);
        float desired = baseZoom * multiplier;
        float minZoom = zoomState.getMinZoomRatio();
        float maxZoom = zoomState.getMaxZoomRatio();
        return Math.max(minZoom, Math.min(maxZoom, desired));
    }

    private float intentMultiplier(CaptureIntent intent) {
        switch (intent) {
            case WAYBILL:
                return 1.68f;
            case BUILDING:
                return 0.65f;
            case DROP_OFF:
            default:
                return 0.65f;
        }
    }

    private boolean isBarcodeHintActive() {
        return captureStage == CaptureIntent.WAYBILL &&
                SystemClock.elapsedRealtime() - lastBarcodeHitMillis < BARCODE_HINT_TTL_MS;
    }

    private CaptureIntent nextStageAfter(CaptureIntent intent) {
        switch (intent) {
            case WAYBILL:
                return CaptureIntent.DROP_OFF;
            case DROP_OFF:
                return CaptureIntent.BUILDING;
            case BUILDING:
            default:
                return CaptureIntent.DROP_OFF;
        }
    }

    private void advanceStageForPreview(CaptureIntent completedIntent) {
        captureStage = nextStageAfter(completedIntent);
        if (captureStage != CaptureIntent.WAYBILL) {
            clearBarcodeHint();
        }
        applyProximityZoom(true);
    }

    private void clearBarcodeHint() {
        lastBarcodeHitMillis = 0L;
        lastMismatchCode = null;
    }

    private void promptMismatch(@NonNull String detected, @NonNull String expected) {
        if (mismatchDialogShowing && detected.equals(lastMismatchCode)) {
            return;
        }
        mismatchDialogShowing = true;
        lastMismatchCode = detected;
        runOnUiThread(() -> {
            if (captureButton != null)
                captureButton.setEnabled(false);
            new AlertDialog.Builder(this)
                    .setTitle(R.string.camera_mismatch_title)
                    .setMessage(getString(R.string.camera_mismatch_message, detected, expected))
                    .setCancelable(false)
                    .setPositiveButton(R.string.camera_mismatch_continue, (dialog, which) -> {
                        mismatchDialogShowing = false;
                        if (captureButton != null)
                            captureButton.setEnabled(true);
                    })
                    .setNegativeButton(R.string.camera_mismatch_cancel, (dialog, which) -> {
                        mismatchDialogShowing = false;
                        if (captureButton != null)
                            captureButton.setEnabled(true);
                        Toast.makeText(this, R.string.camera_mismatch_toast, Toast.LENGTH_LONG).show();
                    })
                    .show();
        });
    }

    private void markCaptureCommitted(@Nullable CaptureIntent intent) {
        ensureCaptureSequenceSynced();
        captureSequenceIndex += 1;
        CaptureIntent appliedIntent = intent != null ? intent : lastResolvedIntent;

    }

    private String intentLabel(@Nullable CaptureIntent intent) {
        if (intent == null)
            return "unknown";
        switch (intent) {
            case WAYBILL:
                return "waybill";
            case DROP_OFF:
                return "dropOff";
            case BUILDING:
                return "building";
            default:
                return intent.toString();
        }
    }

    private void applyDynamicFlashMode() {
        if (imageCapture == null) {
            return;
        }
        if (!flashSupported) {
            return;
        }
        if (lightSensor == null) {
            imageCapture.setFlashMode(ImageCapture.FLASH_MODE_AUTO);
            return;
        }
        boolean useFlash = shouldUseFlash();
        int desired = useFlash ? ImageCapture.FLASH_MODE_ON : ImageCapture.FLASH_MODE_OFF;
        if (imageCapture.getFlashMode() != desired) {
            imageCapture.setFlashMode(desired);
        }
        if (lastFlashOn != useFlash) {
            lastFlashOn = useFlash;
            FileLog.getInstance().debug(TAG,
                    "applyDynamicFlashMode: mode=" + (useFlash ? "ON" : "OFF") + " lux=" + ambientLux);
        }
    }

    private boolean shouldUseFlash() {
        return flashSupported && !Float.isNaN(ambientLux) && ambientLux < EXTREME_LOW_LIGHT_LUX_THRESHOLD;
    }

    private ImageAnalysis buildBarcodeAnalyzer() {
        if (labelScanner == null) {
            return null;
        }
        if (barcodeAnalysis != null) {
            return barcodeAnalysis;
        }
        barcodeAnalysis = new ImageAnalysis.Builder()
                .setTargetResolution(new Size(640, 480))
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build();
        barcodeAnalysis.setAnalyzer(analysisExecutor, image -> {
            if (labelScanner == null) {
                image.close();
                return;
            }
            long now = SystemClock.elapsedRealtime();
            if (now - lastBarcodeAnalysisMillis < 200) {
                image.close();
                return;
            }
            lastBarcodeAnalysisMillis = now;
            if (image.getImage() == null) {
                image.close();
                return;
            }
            final int frameWidth = image.getWidth();
            final int frameHeight = image.getHeight();
            InputImage inputImage = InputImage.fromMediaImage(image.getImage(),
                    image.getImageInfo().getRotationDegrees());
            labelScanner.process(inputImage)
                    .addOnSuccessListener(barcodes -> {
                        if (barcodes == null || barcodes.isEmpty())
                            return;
                        for (Barcode barcode : barcodes) {
                            Rect box = barcode.getBoundingBox();
                            if (isCentralLabel(box, frameWidth, frameHeight)) {
                                String raw = barcode.getRawValue();
                                if (raw != null && handleDetectedBarcode(raw)) {
                                    runOnUiThread(() -> applyProximityZoom(false));
                                }
                                break;
                            }
                        }
                    })
                    .addOnFailureListener(
                            e -> FileLog.getInstance().debug(TAG, "barcode scan failed: " + e.getMessage()))
                    .addOnCompleteListener(task -> image.close());
        });
        return barcodeAnalysis;
    }

    private boolean isCentralLabel(Rect rect, int width, int height) {
        if (rect == null || width <= 0 || height <= 0)
            return false;
        float frameArea = width * height;
        float area = rect.width() * rect.height();
        if (area < frameArea * 0.02f || area > frameArea * 0.5f) {
            return false;
        }
        float centerX = rect.exactCenterX();
        float centerY = rect.exactCenterY();
        float normX = Math.abs(centerX - width / 2f) / (width / 2f);
        float normY = Math.abs(centerY - height / 2f) / (height / 2f);
        return normX < 0.35f && normY < 0.35f;
    }

    private boolean handleDetectedBarcode(@NonNull String rawValue) {
        String normalized = normalizeTracking(rawValue);
        if (normalized == null || normalized.isEmpty())
            return false;
        String expected = getCurrentOrderTracking();
        if (expected == null) {
            lastBarcodeHitMillis = SystemClock.elapsedRealtime();
            return true;
        }
        if (!normalized.equalsIgnoreCase(expected)) {
            runOnUiThread(() -> promptMismatch(normalized, expected));
            return false;
        }
        lastBarcodeHitMillis = SystemClock.elapsedRealtime();
        lastMismatchCode = null;
        return true;
    }

    private String getCurrentOrderTracking() {
        if (deliveryInfo == null) {
            return null;
        }
        return normalizeTracking(deliveryInfo.getOrderSn());
    }

    private String normalizeTracking(String raw) {
        if (raw == null)
            return null;
        String trimmed = raw.replaceAll("[^A-Za-z0-9]", "").toUpperCase(Locale.getDefault());
        return trimmed.isEmpty() ? null : trimmed;
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_CODE_PICK_IMAGE && resultCode == RESULT_OK && data != null) {
            Uri uri = data.getData();
            if (uri != null) {
                try {
                    File file = createImageFile();
                    Bitmap bitmap = BitmapFactory.decodeStream(getContentResolver().openInputStream(uri));
                    bitmap = BitmapUtils.compressBitmapToTarget(bitmap, 120 * 1024);
                    FileOutputStream fos = new FileOutputStream(file);
                    bitmap.compress(Bitmap.CompressFormat.JPEG, 80, fos);
                    fos.close();
                    addThumbnail(file, true);
                    updateOkButtonState();
                } catch (Exception e) {
                    Toast.makeText(this, "图片选取失败", Toast.LENGTH_SHORT).show();
                }
            }
        }
    }

    private void removeLastThumbnail() {
        for (int i = MAX_PHOTOS - 1; i >= 0; i--) {
            if (mImageFiles.get(i) != null) {
                final int index = i;
                ImageView iv = mImageViews.get(i);
                iv.animate().scaleX(0.7f).scaleY(0.7f).alpha(0f).setDuration(200).withEndAction(() -> {
                    removeThumbnail(index);
                    iv.setScaleX(1f);
                    iv.setScaleY(1f);
                    iv.setAlpha(1f);
                    updateOkButtonState();
                }).start();
                break;
            }
        }
    }

    private void clearThumbnails() {
        for (int i = 0; i < MAX_PHOTOS; i++)
            removeThumbnail(i, false);
        apartmentAutoFilePaths.clear();
        activeAutoApartmentMatch = null;
        updateOkButtonState();
    }

    private boolean switchToNextPackage() {
        // TODO: 批量包裹切换
        return false;
    }

    private void showSmsBottomSheet() {
        mSmsBottomSheetFragment.setOrderId(mOrderId);
        mSmsBottomSheetFragment.show(getSupportFragmentManager(), "SmsBottomSheetFragment");
    }

    private void makeCall() {
        if (ContextCompat.checkSelfPermission(this,
                Manifest.permission.CALL_PHONE) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[] { Manifest.permission.CALL_PHONE },
                    CALL_PERMISSION_REQUEST_CODE);
        } else {
            DeliveryInfo info = deliveryInfo;
            if (info == null && mOrderId != null) {
                info = ResourceMgr.getInstance().getDeliveryinfoMgr().get(mOrderId);
                deliveryInfo = info;
            }
            if (info != null) {
                Intent callIntent = new Intent(Intent.ACTION_CALL);
                callIntent.setData(Uri.parse("tel:" + info.getPhone()));
                startActivity(callIntent);
            }
        }
    }

    private CardView createThumbnailCardView(int index) {
        Context ctx = this;
        CardView cardView = new CardView(ctx);
        int sizePx = (int) (64 * ctx.getResources().getDisplayMetrics().density);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(sizePx, sizePx);
        params.setMargins(8, 0, 8, 0);
        cardView.setLayoutParams(params);
        cardView.setRadius(10 * ctx.getResources().getDisplayMetrics().density);
        cardView.setCardElevation(2 * ctx.getResources().getDisplayMetrics().density);
        cardView.setUseCompatPadding(true);

        ImageView imageView = new ImageView(ctx);
        imageView.setLayoutParams(new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));
        imageView.setScaleType(ImageView.ScaleType.CENTER_CROP);
        imageView.setBackgroundResource(R.drawable.bg_thumb_image_rounded);
        imageView.setClipToOutline(true);
        imageView.setTag(index);
        imageView.setOnClickListener(v -> showFullImage((int) v.getTag()));

        cardView.addView(imageView);
        mImageViews.add(imageView);
        mCardViews.add(cardView);
        return cardView;
    }

    private File createImageFile() throws IOException {
        String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(new Date());
        String imageFileName = "JPEG_" + timeStamp + "_";
        File storageDir = getExternalFilesDir(Environment.DIRECTORY_PICTURES);
        return File.createTempFile(imageFileName, ".jpg", storageDir);
    }

    private void saveImage(byte[] bytes, File file, int rotationDegrees) throws IOException {
        Bitmap bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.length);
        if (bitmap == null) {
            throw new IOException("Unable to decode captured frame");
        }
        if (rotationDegrees % 360 != 0) {
            Matrix matrix = new Matrix();
            matrix.postRotate(rotationDegrees);
            Bitmap rotated = Bitmap.createBitmap(bitmap, 0, 0, bitmap.getWidth(), bitmap.getHeight(), matrix, true);
            if (rotated != bitmap) {
                bitmap.recycle();
                bitmap = rotated;
            }
        }
        Bitmap compressed = BitmapUtils.compressBitmapToTarget(bitmap, 400 * 1024);
        if (compressed != null && compressed != bitmap) {
            bitmap.recycle();
            bitmap = compressed;
        }
        try (FileOutputStream output = new FileOutputStream(file)) {
            bitmap.compress(Bitmap.CompressFormat.JPEG, 90, output);
        } finally {
            if (!bitmap.isRecycled()) {
                bitmap.recycle();
            }
        }
    }

    private void addThumbnail(final File imageFile) {
        addThumbnail(imageFile, false);
    }

    private void addThumbnail(final File imageFile, boolean withAnim) {
        for (int i = 0; i < MAX_PHOTOS; i++) {
            if (mImageFiles.get(i) == null) {
                mImageFiles.set(i, imageFile);
                int tw = getResources().getDimensionPixelSize(R.dimen.thumbnail_width);
                int th = getResources().getDimensionPixelSize(R.dimen.thumbnail_height);
                if (tw <= 0)
                    tw = (int) (64 * getResources().getDisplayMetrics().density);
                if (th <= 0)
                    th = (int) (64 * getResources().getDisplayMetrics().density);
                Bitmap thumb = BitmapUtils.decodeSampledBitmapFromFile(imageFile.getAbsolutePath(), tw, th);
                ImageView iv = mImageViews.get(i);
                iv.setImageBitmap(thumb);
                if (withAnim) {
                    iv.setScaleX(0.7f);
                    iv.setScaleY(0.7f);
                    iv.setAlpha(0f);
                    iv.animate().scaleX(1f).scaleY(1f).alpha(1f).setDuration(200).start();
                }
                updateOkButtonState();
                break;
            }
        }
    }

    private void showFullImage(int index) {
        File imageFile = mImageFiles.get(index);
        if (imageFile == null)
            return;
        FullImageFragment dialog = FullImageFragment.newInstance(imageFile.getAbsolutePath(), index);
        dialog.show(getSupportFragmentManager(), "full_image");
    }

    public void removeThumbnail(int index) {
        removeThumbnail(index, true);
    }

    private void removeThumbnail(int index, boolean deleteFile) {
        if (index < 0 || index >= mImageFiles.size())
            return;
        File f = mImageFiles.get(index);
        boolean autoFile = f != null && apartmentAutoFilePaths.contains(f.getAbsolutePath());
        if (autoFile) {
            deleteFile = false;
            apartmentAutoFilePaths.remove(f.getAbsolutePath());
            if (activeAutoApartmentMatch != null
                    && activeAutoApartmentMatch.file != null
                    && f.getAbsolutePath().equals(activeAutoApartmentMatch.file.getAbsolutePath())) {
                activeAutoApartmentMatch = null;
            }
        }
        mImageFiles.set(index, null);
        if (deleteFile && f != null && f.exists())
            f.delete();

        ImageView iv = (index < mImageViews.size()) ? mImageViews.get(index) : null;
        if (iv == null)
            return;
        iv.animate().cancel();
        iv.setImageDrawable(null);
        iv.setImageBitmap(null);
        iv.setAlpha(1f);
        iv.setScaleX(1f);
        iv.setScaleY(1f);
        iv.setBackgroundResource(R.drawable.bg_thumb_image_rounded);
        iv.invalidate();
        if (thumbnailContainer != null) {
            thumbnailContainer.invalidate();
            thumbnailContainer.requestLayout();
        }
        updateOkButtonState();
    }

    private void showFailOptionsDialog() {
        new AlertDialog.Builder(this)
                .setTitle("Delivery Failed")
                .setItems(new String[] { "Retry Delivery", "Delivery Failed" }, (dialog, which) -> {
                    if (which == 0) {
                        handleRetryDelivery();
                    } else {
                        showFailReasonSelectionDialog();
                    }
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private void handleRetryDelivery() {
        if (!hasEnoughPhotos(null))
            return;
        warnIfFarFromTarget();

        DeliveryInfo infoSnapshot = deliveryInfo;
        if (infoSnapshot == null && mOrderId != null) {
            infoSnapshot = ResourceMgr.getInstance().getDeliveryinfoMgr().get(mOrderId);
            deliveryInfo = infoSnapshot;
        }
        if (infoSnapshot == null) {
            Toast.makeText(this, "Package info missing", Toast.LENGTH_SHORT).show();
            return;
        }

        final DeliveryInfo infoSnapshotTrue = infoSnapshot;

        ProgressDialog pd = new ProgressDialog(this);
        pd.setMessage("Retrying delivery...");
        pd.setCancelable(false);
        pd.show();

        DeliveredUploadParams params = new DeliveredUploadParams();
        params.setOrderId(infoSnapshot.getOrderId());
        params.setLongitude(currentLongitude);
        params.setLatitude(currentLatitude);
        params.setRecipientName("");
        params.setImageFiles(serializeImagePaths());

        ResourceMgr.LoginInfo loginInfo = ResourceMgr.getInstance().getLoginInfo();
        if (loginInfo != null) {
            params.setDriverId(String.valueOf(loginInfo.loginId));
        }

        CourierService service = new CourierService();
        new Thread(() -> service.retryDelivery(params,
                new RetryDeliveryRspCb(infoSnapshotTrue.getOrderSn(), new RetryDeliveryRspCb.Callback() {
                    @Override
                    public void onSuccess() {
                        FileLog.getInstance().debug(TAG,
                                "Retry delivery API success for order: " + infoSnapshotTrue.getOrderSn());
                        runOnUiThread(() -> {
                            pd.dismiss();
                            Toast.makeText(CameraActivity.this, "Retry success!", Toast.LENGTH_SHORT).show();
                            // Retry is a direct API call, no need to submitPackage (which queues for async
                            // upload)
                            finish();
                        });
                    }

                    @Override
                    public void onFail(String error) {
                        FileLog.getInstance().error(TAG, "Retry delivery API failed: " + error);
                        runOnUiThread(() -> {
                            pd.dismiss();
                            Toast.makeText(CameraActivity.this, "Retry failed: " + error, Toast.LENGTH_SHORT).show();
                        });
                    }
                }))).start();
    }

    private void showFailReasonSelectionDialog() {
        String[] labels = getResources().getStringArray(R.array.delivery_fail_reason_labels);
        String[] codes = getResources().getStringArray(R.array.delivery_fail_reason_codes);
        if (labels == null || labels.length == 0) {
            Toast.makeText(this, R.string.fail_reason_title, Toast.LENGTH_SHORT).show();
            return;
        }
        new AlertDialog.Builder(this)
                .setTitle(R.string.fail_reason_title)
                .setItems(labels, (dialog, which) -> {
                    if (which >= 0 && which < (codes == null ? 0 : codes.length)) {
                        handleFailReasonSelection(codes[which]);
                    }
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    private void handleFailReasonSelection(@Nullable String codeStr) {
        if (!hasEnoughPhotos(R.string.fail_reason_require_photo)) {
            return;
        }
        warnIfFarFromTarget();
        Integer reason = null;
        if (codeStr != null) {
            try {
                reason = Integer.parseInt(codeStr);
            } catch (NumberFormatException ignored) {
            }
        }
        if (reason == null || reason <= 0) {
            reason = 8; // default to "other"
        }
        submitPackage(1, reason);
    }

    private boolean hasEnoughPhotos(@Nullable Integer overrideMessageRes) {
        int count = (int) mImageFiles.stream().filter(Objects::nonNull).count();
        if (count < IMAGE_COUNT) {
            int messageRes = overrideMessageRes != null ? overrideMessageRes : R.string.take_picture;
            Toast.makeText(this, getString(messageRes), Toast.LENGTH_SHORT).show();
            return false;
        }
        return true;
    }

    private void submitPackage(int deliveryResult, @Nullable Integer failReasonCode) {
        submitPackage(deliveryResult, failReasonCode, PendingPackagesMgr.PackageStatus.Pending.getStatus());
    }

    private void submitPackage(int deliveryResult, @Nullable Integer failReasonCode, String status) {
        DeliveryInfo infoSnapshot = deliveryInfo;
        if (infoSnapshot == null && mOrderId != null) {
            infoSnapshot = ResourceMgr.getInstance().getDeliveryinfoMgr().get(mOrderId);
            deliveryInfo = infoSnapshot;
        }
        if (infoSnapshot == null) {
            Toast.makeText(this, "包裹信息缺失，无法保存", Toast.LENGTH_SHORT).show();
            FileLog.getInstance().error(TAG, "submitPackage: deliveryInfo missing for orderId=" + mOrderId);
            return;
        }

        PackageEntity packageEntity = infoSnapshot.transferToPackageEntity();
        packageEntity.createTime = System.currentTimeMillis();
        packageEntity.imagePath = serializeImagePaths();
        Double latToSave = !Double.isNaN(currentLatitude) ? currentLatitude
                : (!Double.isNaN(targetLatitude) ? targetLatitude : null);
        Double lngToSave = !Double.isNaN(currentLongitude) ? currentLongitude
                : (!Double.isNaN(targetLongitude) ? targetLongitude : null);
        packageEntity.latitude = latToSave;
        packageEntity.longitude = lngToSave;
        packageEntity.status = status;
        packageEntity.deliveryResult = deliveryResult;
        packageEntity.failedReason = failReasonCode;
        packageEntity.recipientName = infoSnapshot.getName();
        ResourceMgr.getInstance().getPendingPackagesMgr().save(packageEntity);

        clearThumbnails();
        if (deliveryResult == 1) {
            Toast.makeText(this, getString(R.string.fail_submit_success), Toast.LENGTH_SHORT).show();
        } else {
            Toast.makeText(this, "已完成", Toast.LENGTH_SHORT).show();
        }

        findAndShowNextPackages(infoSnapshot);
    }

    private String serializeImagePaths() {
        return Arrays.toString(mImageFiles.stream()
                .filter(Objects::nonNull)
                .map(File::getAbsolutePath)
                .toArray(String[]::new));
    }

    // ---------- 拍照反馈 ----------
    private void playShutterFeedback() {
        // 1. 播放系统相机快门声
        try {
            android.media.MediaActionSound sound = new android.media.MediaActionSound();
            sound.play(android.media.MediaActionSound.SHUTTER_CLICK);
        } catch (Exception e) {
            FileLog.getInstance().debug(TAG, "Shutter sound failed: " + e.getMessage());
        }

        // 2. 屏幕闪烁效果
        if (previewView != null) {
            View flashView = new View(this);
            flashView.setBackgroundColor(android.graphics.Color.WHITE);
            flashView.setAlpha(0f);

            addContentView(flashView, new FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.MATCH_PARENT));

            flashView.animate()
                    .alpha(0.6f)
                    .setDuration(80)
                    .withEndAction(() -> flashView.animate()
                            .alpha(0f)
                            .setDuration(80)
                            .withEndAction(() -> {
                                if (flashView.getParent() instanceof ViewGroup) {
                                    ((ViewGroup) flashView.getParent()).removeView(flashView);
                                }
                            })
                            .start())
                    .start();
        }
    }

    // ---------- 拍照（走 CameraServie） ----------
    private void takePicture() {
        FileLog.getInstance().debug(TAG, "takePicture via CameraX");
        applyProximityZoom(true);
        applyDynamicFlashMode();
        final CaptureIntent intentForShot = lastResolvedIntent;

        // --- 播放拍照反馈 ---
        playShutterFeedback();
        boolean full = true;
        for (File imageFile : mImageFiles) {
            if (imageFile == null) {
                full = false;
                break;
            }
        }
        if (full) {
            Toast.makeText(this, getString(R.string.take_picture_full), Toast.LENGTH_SHORT).show();
            return;
        }
        if (imageCapture == null) {
            Toast.makeText(this, "Camera not ready", Toast.LENGTH_SHORT).show();
            return;
        }

        // 1. Show instant thumbnail using previewView.getBitmap()
        Bitmap previewBitmap = previewView.getBitmap();
        File tempFile = null;
        int tempIndex = -1;
        if (previewBitmap != null) {
            // Scale down preview bitmap to avoid memory issues
            int tw = getResources().getDimensionPixelSize(R.dimen.thumbnail_width);
            int th = getResources().getDimensionPixelSize(R.dimen.thumbnail_height);
            if (tw <= 0)
                tw = (int) (64 * getResources().getDisplayMetrics().density);
            if (th <= 0)
                th = (int) (64 * getResources().getDisplayMetrics().density);
            Bitmap scaledPreview = Bitmap.createScaledBitmap(previewBitmap, tw, th, true);
            try {
                tempFile = createImageFile();
                FileOutputStream fos = new FileOutputStream(tempFile);
                scaledPreview.compress(Bitmap.CompressFormat.JPEG, 60, fos);
                fos.close();
                // Find first available index and insert temp file
                for (int i = 0; i < MAX_PHOTOS; i++) {
                    if (mImageFiles.get(i) == null) {
                        tempIndex = i;
                        break;
                    }
                }
                final File tempFileFinal = tempFile;
                final int tempIndexFinal = tempIndex;
                runOnUiThread(() -> {
                    if (tempIndexFinal >= 0) {
                        mImageFiles.set(tempIndexFinal, tempFileFinal);
                        ImageView iv = mImageViews.get(tempIndexFinal);
                        iv.setImageBitmap(scaledPreview);
                        iv.setScaleX(0.7f);
                        iv.setScaleY(0.7f);
                        iv.setAlpha(0f);
                        iv.animate().scaleX(1f).scaleY(1f).alpha(1f).setDuration(200).start();
                        updateOkButtonState();
                    }
                });
            } catch (IOException e) {
                FileLog.getInstance().error(TAG, "Temp thumbnail failed", e);
            }
        }

        // 2. Offload actual image saving to background thread
        File imageFile;
        try {
            imageFile = createImageFile();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        final int placeholderIndex = tempIndex;
        final File tempFileForReplace = tempFile;
        imageCapture.takePicture(ContextCompat.getMainExecutor(this), new ImageCapture.OnImageCapturedCallback() {
            @Override
            public void onCaptureSuccess(@NonNull ImageProxy image) {
                cameraExecutor.execute(() -> {
                    try {
                        // Convert ImageProxy to byte[]
                        ByteBuffer buffer = image.getPlanes()[0].getBuffer();
                        byte[] bytes = new byte[buffer.remaining()];
                        buffer.get(bytes);
                        int rotationDegrees = image.getImageInfo().getRotationDegrees();
                        saveImage(bytes, imageFile, rotationDegrees);
                        // On UI thread, replace the temp thumbnail with the real one
                        runOnUiThread(() -> {
                            if (placeholderIndex >= 0) {
                                // Remove temp file
                                File old = mImageFiles.get(placeholderIndex);
                                if (old != null && old.exists() && tempFileForReplace != null
                                        && old.equals(tempFileForReplace)) {
                                    old.delete();
                                }
                                mImageFiles.set(placeholderIndex, imageFile);
                                int tw = getResources().getDimensionPixelSize(R.dimen.thumbnail_width);
                                int th = getResources().getDimensionPixelSize(R.dimen.thumbnail_height);
                                if (tw <= 0)
                                    tw = (int) (64 * getResources().getDisplayMetrics().density);
                                if (th <= 0)
                                    th = (int) (64 * getResources().getDisplayMetrics().density);
                                Bitmap thumb = BitmapUtils.decodeSampledBitmapFromFile(imageFile.getAbsolutePath(), tw,
                                        th);
                                ImageView iv = mImageViews.get(placeholderIndex);
                                iv.setImageBitmap(thumb);
                                updateOkButtonState();
                                markCaptureCommitted(intentForShot);
                                advanceStageForPreview(intentForShot);
                                if (intentForShot == CaptureIntent.BUILDING) {
                                    handleBuildingPhotoCaptured(imageFile);
                                }
                            } else {
                                // fallback: insert real thumbnail into first available slot
                                addThumbnail(imageFile, true);
                                updateOkButtonState();
                                markCaptureCommitted(intentForShot);
                                advanceStageForPreview(intentForShot);
                                if (intentForShot == CaptureIntent.BUILDING) {
                                    handleBuildingPhotoCaptured(imageFile);
                                }
                            }
                        });
                    } catch (Exception e) {
                        FileLog.getInstance().error(TAG, "post-save compress failed", e);
                        runOnUiThread(() -> Toast
                                .makeText(CameraActivity.this, R.string.picture_save_failed, Toast.LENGTH_SHORT)
                                .show());
                    } finally {
                        image.close();
                    }
                });
            }

            @Override
            public void onError(@NonNull ImageCaptureException exception) {
                runOnUiThread(() -> Toast
                        .makeText(CameraActivity.this, "Capture failed: " + exception.getMessage(), Toast.LENGTH_SHORT)
                        .show());
            }
        });
    }

    private void startCamera() {

        if (cameraStarted) {
            FileLog.getInstance().debug(TAG, "startCamera: already started, skipping");
            return;
        }
        cameraStarted = true;
        ListenableFuture<ProcessCameraProvider> cameraProviderFuture = ProcessCameraProvider.getInstance(this);
        cameraProviderFuture.addListener(() -> {
            try {
                ProcessCameraProvider cameraProvider = cameraProviderFuture.get();

                Preview preview = new Preview.Builder().build();
                preview.setSurfaceProvider(previewView.getSurfaceProvider());
                ImageCapture.Builder builder = new ImageCapture.Builder();
                if (lightSensor == null) {
                    builder.setFlashMode(ImageCapture.FLASH_MODE_AUTO);
                }
                imageCapture = builder.build();

                CameraSelector cameraSelector = new CameraSelector.Builder()
                        .requireLensFacing(CameraSelector.LENS_FACING_BACK)
                        .build();
                cameraProvider.unbindAll();
                ImageAnalysis analysis = buildBarcodeAnalyzer();
                if (analysis != null) {
                    boundCamera = cameraProvider.bindToLifecycle(this, cameraSelector, preview, imageCapture, analysis);
                } else {
                    boundCamera = cameraProvider.bindToLifecycle(this, cameraSelector, preview, imageCapture);
                }
                flashSupported = boundCamera != null && boundCamera.getCameraInfo().hasFlashUnit();
                lastFlashOn = false;
                applyDynamicFlashMode();
                applyProximityZoom(true);

            } catch (Exception e) {
                FileLog.getInstance().error(TAG, "startCamera failed: "
                        + e.getClass().getSimpleName() + " - " + e.getMessage(), e);
                // Retry binding later if failed
                previewView.postDelayed(this::startCameraIfNeeded, 300);
            }
        }, ContextCompat.getMainExecutor(this));
    }

    /**
     * 从当前定位到包裹目的地（targetLatitude, targetLongitude）发起导航。
     * 优先使用 Google Maps turn-by-turn；不可用时回退到通用 VIEW。
     */
    private void openNavigationToPackage() {
        double lat = resolveTargetLatitude();
        double lng = resolveTargetLongitude();
        if (Double.isNaN(lat) || Double.isNaN(lng)) {
            Toast.makeText(this, getString(R.string.nav_location_invalid), Toast.LENGTH_SHORT).show();
            return;
        }
        // 1) 优先：Google Maps 导航
        try {
            android.net.Uri gmmIntentUri = android.net.Uri.parse("google.navigation:q=" + lat + "," + lng + "&mode=d");
            Intent mapIntent = new Intent(Intent.ACTION_VIEW, gmmIntentUri);
            mapIntent.setPackage("com.google.android.apps.maps");
            if (mapIntent.resolveActivity(getPackageManager()) != null) {
                startActivity(mapIntent);
                return;
            }
        } catch (Exception ignored) {
        }

        // 2) 回退：任意地图应用 / 浏览器
        try {
            String url = "https://www.google.com/maps/dir/?api=1&destination=" + lat + "," + lng
                    + "&travelmode=driving";
            Intent webMap = new Intent(Intent.ACTION_VIEW, android.net.Uri.parse(url));
            startActivity(webMap);
        } catch (Exception e) {
            Toast.makeText(this, getString(R.string.no_map_app_found), Toast.LENGTH_SHORT).show();
        }
    }

    private boolean warnIfFarFromTarget() {
        DeliveryInfo infoSnapshot = deliveryInfo;
        if (infoSnapshot == null) {
            return false;
        }
        refreshCurrentLocationSnapshot();
        double currentLat = currentLatitude;
        double currentLng = currentLongitude;
        if (Double.isNaN(currentLat) || Double.isNaN(currentLng)) {
            if (lastKnownLocation != null) {
                currentLat = lastKnownLocation.getLatitude();
                currentLng = lastKnownLocation.getLongitude();
            }
        }
        double targetLat = resolveTargetLatitude();
        double targetLng = resolveTargetLongitude();
        if (Double.isNaN(targetLat) || Double.isNaN(targetLng))
            return false;
        if (Math.abs(targetLat) < 0.000001 && Math.abs(targetLng) < 0.000001)
            return false;
        if (Double.isNaN(currentLat) || Double.isNaN(currentLng))
            return false;
        float[] results = new float[1];
        Location.distanceBetween(currentLat, currentLng, targetLat, targetLng, results);
        float distance = results[0];
        if (distance > 150f) {
            Toast.makeText(this, String.format(java.util.Locale.getDefault(), "当前位置与包裹相差约%.0f米，请确认后再派送", distance),
                    Toast.LENGTH_SHORT).show();
            FileLog.getInstance().debug(TAG, "warnIfFarFromTarget: distance=" + distance + " target=(" + targetLat + ","
                    + targetLng + ") current=(" + currentLat + "," + currentLng + ")");
            return true;
        }
        return false;
    }

    private void findAndShowNextPackages(DeliveryInfo currentInfo) {
        // 使用 PowerSaverSelector：同址优先 + 距离补足（+3），一次性在送达后触发
        if (currentInfo == null) {
            finish();
            return;
        }
        DeliveryinfoMgr mgr = ResourceMgr.getInstance().getDeliveryinfoMgr();
        if (mgr == null) {
            finish();
            return;
        }
        PowerSaverSelector.Params params = new PowerSaverSelector.Params();
        params.extraNearCount = 3; // “同址数量 + 3”
        params.nearRadiusMeters = 150f; // 对非同址的软半径；≤0 则不限制

        Location ref = buildLocationFromPackage(currentInfo);
        if (ref == null) {
            ref = lastKnownLocation;
        }

        List<DeliveryInfo> next = new PowerSaverSelector().selectNext(currentInfo, ref, mgr, params);
        int count = next == null ? 0 : next.size();
        if (count == 0) {
            try {
                FileLog.getInstance().debug(TAG, "findAndShowNextPackages: no candidates, finishing camera flow");
            } catch (Throwable ignore) {
            }
            finish();
            return;
        }
        if (count == 1) {
            try {
                FileLog.getInstance().debug(TAG, "findAndShowNextPackages: single candidate -> auto switch");
            } catch (Throwable ignore) {
            }
            // 仅一条：直接切换
            resetForNewPackage(next.get(0));
            return;
        }
        try {
            FileLog.getInstance().debug(TAG,
                    "findAndShowNextPackages: multiple candidates=" + count + " -> show chooser");
        } catch (Throwable ignore) {
        }
        // 多条：弹出选择
        showNextPackageChooser(next);
    }

    private List<DeliveryInfo> findNextPackages(DeliveryInfo currentInfo) {
        if (currentInfo == null)
            return Collections.emptyList();
        DeliveryinfoMgr mgr = ResourceMgr.getInstance().getDeliveryinfoMgr();
        if (mgr == null)
            return Collections.emptyList();
        PowerSaverSelector.Params params = new PowerSaverSelector.Params();
        params.extraNearCount = 3;
        params.nearRadiusMeters = 150f;
        Location ref = buildLocationFromPackage(currentInfo);
        if (ref == null) {
            ref = lastKnownLocation;
        }
        return new PowerSaverSelector().selectNext(currentInfo, ref, mgr, params);
    }

    @Nullable
    private Location buildLocationFromPackage(@Nullable DeliveryInfo info) {
        if (info == null)
            return null;
        try {
            double lat = info.getLatitude();
            double lng = info.getLongitude();
            if (Math.abs(lat) < 1e-6 && Math.abs(lng) < 1e-6) {
                return null;
            }
            Location tmp = new Location("pkg");
            tmp.setLatitude(lat);
            tmp.setLongitude(lng);
            return tmp;
        } catch (Throwable ignore) {
            return null;
        }
    }

    private void showNextPackageChooser(List<DeliveryInfo> items) {
        BottomSheetDialog dialog = new BottomSheetDialog(this);
        View sheet = getLayoutInflater().inflate(R.layout.dialog_cluster_list, null, false);
        TextView title = sheet.findViewById(R.id.tv_cluster_title);
        if (title != null) {
            title.setText("同一地址的下一个包裹");
        }
        dialog.setContentView(sheet);

        java.util.concurrent.atomic.AtomicBoolean isPackageSelected = new java.util.concurrent.atomic.AtomicBoolean(
                false);

        RecyclerView rv = sheet.findViewById(R.id.rv_cluster);
        rv.setLayoutManager(new LinearLayoutManager(this));
        ClusterParcelAdapter adapter = new ClusterParcelAdapter(items, info -> {
            isPackageSelected.set(true);
            dialog.dismiss();
            resetForNewPackage(info);
        });
        rv.setAdapter(adapter);

        dialog.setOnDismissListener(d -> {
            if (!isPackageSelected.get()) {
                finish();
            }
        });

        dialog.show();
    }

    private void resetForNewPackage(DeliveryInfo newInfo) {
        clearThumbnails();
        updateInfoBar(newInfo);
        ensureCaptureSequenceSynced();
        Toast.makeText(this, "已切换到下一个包裹: " + newInfo.getRouteNumber(), Toast.LENGTH_SHORT).show();
        initApartmentAssist();
    }

    private void updateInfoBar(DeliveryInfo newInfo) {
        if (newInfo == null)
            return;
        this.deliveryInfo = newInfo;
        this.mOrderId = newInfo.getOrderId();
        if (tvRouteNumber != null)
            tvRouteNumber.setText(String.valueOf(newInfo.getRouteNumber()));
        if (tvOrderSn != null)
            tvOrderSn.setText(newInfo.getOrderSn());
        if (tvCustomerName != null)
            tvCustomerName.setText(newInfo.getName());
        if (tvUnitNumber != null)
            tvUnitNumber.setText(newInfo.getUnitNumber());
        if (tvAddress != null) {
            tvAddress.setText(newInfo.getAddress());
            tvAddress.setOnClickListener(v -> openNavigationToPackage());
        }
        initApartmentAssist();
    }

    private void initApartmentAssist() {
        if (apartmentPhotoService == null || deliveryInfo == null) {
            apartmentKeyData = null;
            return;
        }
        apartmentKeyData = apartmentPhotoService.buildKeyData(deliveryInfo);
        maybeAutoFillApartmentPhoto();
    }

    private void maybeAutoFillApartmentPhoto() {
        if (apartmentPhotoService == null || deliveryInfo == null) {
            return;
        }
        MatchResult match = apartmentPhotoService.findMatch(deliveryInfo);
        if (match == null) {
            return;
        }
        applyAutoFilledPhoto(match);
    }

    private void applyAutoFilledPhoto(@NonNull MatchResult match) {
        File file = match.file;
        if (file == null || !file.exists())
            return;
        activeAutoApartmentMatch = match;
        apartmentAutoFilePaths.add(file.getAbsolutePath());

        // Fix: Place in the last slot (index 2) for building photo
        int targetIndex = MAX_PHOTOS - 1;
        if (mImageFiles.get(targetIndex) == null) {
            mImageFiles.set(targetIndex, file);
            updateThumbnailView(targetIndex, file);
            updateOkButtonState();
        }
    }

    private int findImageIndexByPath(String path) {
        if (TextUtils.isEmpty(path))
            return -1;
        for (int i = 0; i < mImageFiles.size(); i++) {
            File file = mImageFiles.get(i);
            if (file != null && path.equals(file.getAbsolutePath())) {
                return i;
            }
        }
        return -1;
    }

    private void handleBuildingPhotoCaptured(File imageFile) {
        if (apartmentPhotoService == null || deliveryInfo == null) {
            return;
        }
        ApartmentAddressKeyBuilder.KeyData keyData = apartmentPhotoService.buildKeyData(deliveryInfo);
        if (keyData == null || !keyData.isApartment) {
            return;
        }
        if (keyData.hasStructuredKey && !TextUtils.isEmpty(keyData.key)) {
            if (apartmentPhotoService.hasPhotoForKey(keyData.key)) {
                return;
            }
            promptStructuredApartmentKey(imageFile, keyData);
        } else {
            promptManualAddressKey(imageFile);
        }
    }

    private void promptStructuredApartmentKey(File imageFile,
            ApartmentAddressKeyBuilder.KeyData keyData) {
        String preset = TextUtils.isEmpty(keyData.displayAddress)
                ? keyData.key
                : keyData.displayAddress;
        // Pass the structured key as hiddenKey
        showApartmentConfirmDialog(imageFile, preset, keyData.key, false);
    }

    private void promptManualAddressKey(File imageFile) {
        String suggested = apartmentPhotoService != null && deliveryInfo != null
                ? apartmentPhotoService.suggestManualBase(deliveryInfo.getAddress())
                : "";
        showApartmentConfirmDialog(imageFile, suggested, null, true);
    }

    private void showApartmentConfirmDialog(File imageFile,
            @Nullable String initialText,
            @Nullable String hiddenKey,
            boolean manualSource) {
        runOnUiThread(() -> {
            final EditText input = new EditText(this);
            input.setHint(R.string.camera_apartment_manual_hint);
            if (!TextUtils.isEmpty(initialText)) {
                input.setText(initialText);
                input.setSelection(initialText.length());
            }
            AlertDialog dialog = new AlertDialog.Builder(this)
                    .setTitle(R.string.camera_apartment_manual_title)
                    .setMessage(manualSource ? null : getString(R.string.camera_apartment_auto_confirm_msg))
                    .setView(input)
                    .setPositiveButton(android.R.string.ok, null)
                    .setNegativeButton(R.string.cancel, null)
                    .create();
            dialog.setOnShowListener(d -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
                String raw = input.getText().toString().trim();
                if (TextUtils.isEmpty(raw)) {
                    Toast.makeText(this, R.string.camera_apartment_manual_error, Toast.LENGTH_SHORT).show();
                    return;
                }

                String normalized;
                // If hiddenKey is provided and user didn't change the text, use hiddenKey
                if (hiddenKey != null && initialText != null && raw.equals(initialText.trim())) {
                    normalized = hiddenKey;
                } else {
                    normalized = ApartmentAddressKeyBuilder.manualKeyFromInput(raw);
                }

                if (TextUtils.isEmpty(normalized)) {
                    Toast.makeText(this, R.string.camera_apartment_manual_error, Toast.LENGTH_SHORT).show();
                    return;
                }
                dialog.dismiss();
                saveApartmentPhotoAsync(imageFile, normalized, raw, manualSource);
            }));
            dialog.show();
        });
    }

    private void addSignatureButton() {
        try {
            // Find the info bar container (CardView) or its child layout
            ViewGroup infoLayout = (ViewGroup) infoBar.getChildAt(0); // Assuming CardView has one child

            // Create signature button
            android.widget.ImageView signatureBtn = new android.widget.ImageView(this);
            signatureBtn.setImageResource(android.R.drawable.ic_menu_edit); // Use a pencil/edit icon
            signatureBtn.setColorFilter(ContextCompat.getColor(this, R.color.colorPrimary)); // Use app primary color

            // Layout params
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                    (int) (32 * getResources().getDisplayMetrics().density),
                    (int) (32 * getResources().getDisplayMetrics().density));
            params.gravity = android.view.Gravity.CENTER_VERTICAL;
            params.setMarginStart((int) (8 * getResources().getDisplayMetrics().density));

            signatureBtn.setLayoutParams(params);
            signatureBtn.setOnClickListener(v -> showSignatureDialog());

            // Add to the layout (assuming horizontal or relative layout in info bar)
            // Ideally we should add it to a specific container, but for now appending to
            // the main info layout
            // If the layout is vertical, this might look bad. Let's try to find a better
            // spot or add it dynamically.
            // A safer bet is to add it to the 'tvAddress' or create a new container.
            // Given the constraints, let's try adding it to the end of the infoLayout if
            // it's horizontal,
            // or just rely on a floating action button or similar if layout is complex.
            // Let's try adding it to the infoLayout.
            if (infoLayout instanceof LinearLayout) {
                infoLayout.addView(signatureBtn);
            } else if (infoLayout instanceof androidx.constraintlayout.widget.ConstraintLayout) {
                // If constraint layout, we might need to clone constraints.
                // Simplification: Add a floating button on top of the info bar
                FrameLayout root = findViewById(android.R.id.content);
                FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(params.width, params.height);
                lp.gravity = android.view.Gravity.TOP | android.view.Gravity.END;
                lp.topMargin = (int) (16 * getResources().getDisplayMetrics().density); // Adjust based on info bar
                                                                                        // height
                lp.rightMargin = (int) (16 * getResources().getDisplayMetrics().density);
                addContentView(signatureBtn, lp);
            } else {
                infoLayout.addView(signatureBtn);
            }

        } catch (Exception e) {
            FileLog.getInstance().error(TAG, "Failed to add signature button", e);
        }
    }

    private void showSignatureDialog() {
        if (deliveryInfo == null)
            return;
        SignatureDialogFragment dialog = SignatureDialogFragment.newInstance(deliveryInfo.getName());
        dialog.setOnSignatureCompletedListener((path, recipientName) -> {
            lastSignaturePath = path;
            lastRecipientName = recipientName;

            // Auto-fill signature to the 2nd slot (index 1)
            if (path != null) {
                File file = new File(path);
                if (file.exists()) {
                    // Ensure list has enough capacity or set specifically
                    if (mImageFiles.size() <= 1) {
                        // If less than 2 items, add nulls until index 1 is reachable
                        while (mImageFiles.size() < 2) {
                            mImageFiles.add(null);
                        }
                    }
                    // Set at index 1
                    mImageFiles.set(1, file);
                    updateThumbnailView(1, file);
                    updateOkButtonState();
                }
            }
        });
        dialog.show(getSupportFragmentManager(), "signature_dialog");
    }

    private void updateThumbnailView(int index, File file) {
        if (index < 0 || index >= mImageViews.size())
            return;

        int tw = getResources().getDimensionPixelSize(R.dimen.thumbnail_width);
        int th = getResources().getDimensionPixelSize(R.dimen.thumbnail_height);
        if (tw <= 0)
            tw = (int) (64 * getResources().getDisplayMetrics().density);
        if (th <= 0)
            th = (int) (64 * getResources().getDisplayMetrics().density);

        Bitmap thumb = BitmapUtils.decodeSampledBitmapFromFile(file.getAbsolutePath(), tw, th);
        ImageView iv = mImageViews.get(index);
        iv.setImageBitmap(thumb);
        iv.setScaleX(0.7f);
        iv.setScaleY(0.7f);
        iv.setAlpha(0f);
        iv.animate().scaleX(1f).scaleY(1f).alpha(1f).setDuration(200).start();
    }

    private String lastSignaturePath;
    private String lastRecipientName;

    private void saveApartmentPhotoAsync(File imageFile, String key, String displayAddress, boolean manual) {
        cameraExecutor.execute(() -> {
            boolean saved = apartmentPhotoService.savePhoto(imageFile, key, displayAddress, manual);
            runOnUiThread(() -> {
                if (saved) {
                    String label = TextUtils.isEmpty(displayAddress) ? key : displayAddress;
                    Toast.makeText(this, getString(R.string.camera_apartment_saved_toast, label), Toast.LENGTH_SHORT)
                            .show();
                } else {
                    Toast.makeText(this, R.string.picture_save_failed, Toast.LENGTH_SHORT).show();
                }
            });
        });
    }
}
