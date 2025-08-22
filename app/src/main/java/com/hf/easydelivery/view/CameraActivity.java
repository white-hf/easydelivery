package com.hf.easydelivery.view;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;

import androidx.camera.core.CameraSelector;
import androidx.camera.core.ImageCapture;
import androidx.camera.core.ImageCaptureException;
import androidx.camera.core.ImageProxy;
import androidx.camera.core.Preview;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.view.PreviewView;
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
import androidx.appcompat.app.AppCompatActivity;
import androidx.cardview.widget.CardView;
import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.google.android.material.button.MaterialButton;
import com.hf.easydelivery.R;
import com.hf.easydelivery.ResourceMgr;
import com.hf.easydelivery.common.BitmapUtils;
import com.hf.courierservice.apihelper.FileLog;
import com.hf.easydelivery.core.PendingPackagesMgr;
import com.hf.easydelivery.dao.DeliveryInfo;
import com.hf.easydelivery.dao.PackageEntity;


import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;

import android.widget.TextView;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.text.SimpleDateFormat;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import com.hf.easydelivery.common.PermissionUtils;

/**
 * CameraActivity（从 Fragment 完整改造为 Activity）
 * - 相机统一改用 CameraServie（共享、可避免与扫码页竞争）
 * - 修复所有 Fragment API 遗留：requireActivity()/getArguments()/view.findViewById 等
 * - UI/业务逻辑保持不变（缩略图/短信/拨号/完成校验等）
 */
public class CameraActivity extends AppCompatActivity implements SensorEventListener {

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
    private Sensor accelerometer, magnetometer;
    private final float[] accelerometerReading = new float[3];
    private final float[] magnetometerReading = new float[3];
    private boolean isPortrait = false;

    // 业务参数
    private Long mOrderId;
    private double mLatitude;
    private double mLongitude;

    private SmsBottomSheetFragment mSmsBottomSheetFragment;

    // 宿主 chrome
    private View hostToolbar, hostBottomBar;

    // 相机服务
    // private final CameraService cameraServie = CameraService.getInstance();
    // private boolean cameraBound = false;
    private boolean cameraStarted = false;

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
            mLatitude = args.getDouble("latitude", -1);
            mLongitude = args.getDouble("longitude", -1);
        }
        mSmsBottomSheetFragment = new SmsBottomSheetFragment(mOrderId);

        // 2) 顶部信息栏
        infoBar = findViewById(R.id.info_bar);
        tvRouteNumber = findViewById(R.id.tv_route_number);
        tvOrderSn = findViewById(R.id.tv_tracking_number);
        tvCustomerName = findViewById(R.id.tv_customer_name);
        tvUnitNumber = findViewById(R.id.tv_unit_number);
        tvAddress = findViewById(R.id.tv_address);


        final DeliveryInfo deliveryInfo = ResourceMgr.getInstance().getDeliveryinfoMgr().get(mOrderId);
        if (deliveryInfo != null) {
            tvRouteNumber.setText(String.valueOf(deliveryInfo.getRouteNumber()));
            tvOrderSn.setText(deliveryInfo.getOrderSn());
            tvCustomerName.setText(deliveryInfo.getName());
            tvUnitNumber.setText(deliveryInfo.getUnitNumber());
            tvAddress.setText(deliveryInfo.getAddress());
        }

        // 3) 缩略图栏
        thumbnailContainer = findViewById(R.id.thumbnail_container);
        mImageFiles.clear(); mImageViews.clear(); mCardViews.clear();
        for (int i = 0; i < MAX_PHOTOS; i++) {
            CardView cardView = createThumbnailCardView(i);
            thumbnailContainer.addView(cardView);
            mImageFiles.add(null);
        }

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
        failButton.setOnClickListener(v -> showFailReasonDialog());
        okButton.setOnClickListener(v -> {
            int count = (int) mImageFiles.stream().filter(Objects::nonNull).count();
            if (count < IMAGE_COUNT) {
                okButton.setEnabled(false);
                Toast.makeText(this, getString(R.string.take_picture), Toast.LENGTH_SHORT).show();
                return;
            }
            okButton.setEnabled(true);
            if (deliveryInfo != null) {
                PackageEntity packageEntity = deliveryInfo.transferToPackageEntity();
                packageEntity.createTime = System.currentTimeMillis();
                packageEntity.imagePath = Arrays.toString(mImageFiles.stream().filter(Objects::nonNull).map(File::getAbsolutePath).toArray(String[]::new));
                packageEntity.latitude = mLatitude;
                packageEntity.longitude = mLongitude;
                packageEntity.status = PendingPackagesMgr.PackageStatus.Pending.getStatus();
                ResourceMgr.getInstance().getPendingPackagesMgr().save(packageEntity);
            }
            clearThumbnails();
            if (!switchToNextPackage()) {
                Toast.makeText(this, "已完成", Toast.LENGTH_SHORT).show();
                finish();
            }
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
                ConstraintLayout root = (possibleRoot instanceof ConstraintLayout) ? (ConstraintLayout) possibleRoot : null;
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
                if (cb != null) cb.setVisibility(View.GONE);
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

    @Override
    protected void onStart() {
        super.onStart();
        FileLog.getInstance().debug(TAG, "onStart: no-op");
    }

    @Override
    protected void onResume() {
        super.onResume();
        startCameraIfNeeded();

        if (accelerometer != null && magnetometer != null) {
            sensorManager.registerListener(this, accelerometer, SensorManager.SENSOR_DELAY_NORMAL);
            sensorManager.registerListener(this, magnetometer, SensorManager.SENSOR_DELAY_NORMAL);
        }
    }

    @Override
    protected void onPause() {
        exitImmersiveFullscreen();
        showHostChrome();

        super.onPause();
        if (sensorManager != null) sensorManager.unregisterListener(this);
        // Unbind camera on pause
        try {
            ProcessCameraProvider provider = ProcessCameraProvider.getInstance(this).get();
            provider.unbindAll();
            cameraStarted = false;
        } catch (Exception e) {
            FileLog.getInstance().error(TAG, "unbind onPause failed", e);
        }
    }

    @Override
    protected void onDestroy() {
        FileLog.getInstance().debug(TAG, "onDestroy: clean up.");
        showHostChrome();
        cameraExecutor.shutdown();
        super.onDestroy();
    }

    // ---------- UI chrome ----------
    private void prepareHostChromeRefs() {
        try {
            String pkg = getPackageName();
            String[] toolbarNames = new String[]{"toolbar", "appbar", "top_bar"};
            for (String name : toolbarNames) {
                int resId = getResources().getIdentifier(name, "id", pkg);
                if (resId != 0) {
                    View v = findViewById(resId);
                    if (v != null) { hostToolbar = v; break; }
                }
            }
            String[] bottomNames = new String[]{"bottom_nav", "nav_view", "tab_layout", "bottom_bar"};
            for (String name : bottomNames) {
                int resId = getResources().getIdentifier(name, "id", pkg);
                if (resId != 0) {
                    View v = findViewById(resId);
                    if (v != null) { hostBottomBar = v; break; }
                }
            }
        } catch (Exception e) {
            FileLog.getInstance().debug("CameraActivity", "prepareHostChromeRefs error: " + e.getMessage());
        }
    }

    private void hideHostChrome() {
        try {
            if (hostToolbar != null) hostToolbar.setVisibility(View.GONE);
            if (hostBottomBar != null) hostBottomBar.setVisibility(View.GONE);
        } catch (Exception e) {
            FileLog.getInstance().debug("CameraActivity", "hideHostChrome: " + e.getMessage());
        }
    }

    private void showHostChrome() {
        try {
            if (hostToolbar != null) hostToolbar.setVisibility(View.VISIBLE);
            if (hostBottomBar != null) hostBottomBar.setVisibility(View.VISIBLE);
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
        }
        float[] rotationMatrix = new float[9];
        if (SensorManager.getRotationMatrix(rotationMatrix, null, accelerometerReading, magnetometerReading)) {
            float[] orientation = new float[3];
            SensorManager.getOrientation(rotationMatrix, orientation);
            float pitch = orientation[1];
            isPortrait = Math.abs(pitch) > Math.PI / 4;
        }
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {}

    // ---------- 权限/相机绑定（CameraServie） ----------
    private void initCamera() {
        if (PermissionUtils.hasCameraPermission(this)) {
            startCamera();
        } else {
            PermissionUtils.requestCameraPermission(this, CAMERA_PERMISSION_REQUEST_CODE);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == CAMERA_PERMISSION_REQUEST_CODE) {
            if (PermissionUtils.isPermissionGranted(grantResults)) {
                startCamera();
            } else {
                Toast.makeText(this, "Camera permission is required to use this feature", Toast.LENGTH_SHORT).show();
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
        if (str == null) return "";
        if (str.length() <= maxLen) return str;
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
        for (int i = 0; i < MAX_PHOTOS; i++) removeThumbnail(i);
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
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CALL_PHONE) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.CALL_PHONE}, CALL_PERMISSION_REQUEST_CODE);
        } else {
            final DeliveryInfo deliveryInfo = ResourceMgr.getInstance().getDeliveryinfoMgr().get(mOrderId);
            if (deliveryInfo != null) {
                Intent callIntent = new Intent(Intent.ACTION_CALL);
                callIntent.setData(Uri.parse("tel:" + deliveryInfo.getPhone()));
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

    private void saveImage(byte[] bytes, File file, boolean portrait) throws IOException {
        Bitmap bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.length);
        if (portrait) {
            Matrix matrix = new Matrix();
            matrix.postRotate(90);
            bitmap = Bitmap.createBitmap(bitmap, 0, 0, bitmap.getWidth(), bitmap.getHeight(), matrix, true);
        }
        bitmap = BitmapUtils.compressBitmapToTarget(bitmap, 120 * 1024);
        try (FileOutputStream output = new FileOutputStream(file)) {
            bitmap.compress(Bitmap.CompressFormat.JPEG, 80, output);
        }
    }

    private void addThumbnail(final File imageFile) { addThumbnail(imageFile, false); }

    private void addThumbnail(final File imageFile, boolean withAnim) {
        for (int i = 0; i < MAX_PHOTOS; i++) {
            if (mImageFiles.get(i) == null) {
                mImageFiles.set(i, imageFile);
                int tw = getResources().getDimensionPixelSize(R.dimen.thumbnail_width);
                int th = getResources().getDimensionPixelSize(R.dimen.thumbnail_height);
                if (tw <= 0) tw = (int) (64 * getResources().getDisplayMetrics().density);
                if (th <= 0) th = (int) (64 * getResources().getDisplayMetrics().density);
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
        if (imageFile == null) return;
        FullImageFragment dialog = FullImageFragment.newInstance(imageFile.getAbsolutePath(), index);
        dialog.show(getSupportFragmentManager(), "full_image");
    }

    public void removeThumbnail(int index) {
        if (index < 0 || index >= mImageFiles.size()) return;
        File f = mImageFiles.get(index);
        mImageFiles.set(index, null);
        if (f != null && f.exists()) f.delete();

        ImageView iv = (index < mImageViews.size()) ? mImageViews.get(index) : null;
        if (iv == null) return;
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

    private void showFailReasonDialog() {
        Toast.makeText(this, "失败原因弹窗", Toast.LENGTH_SHORT).show();
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
                    FrameLayout.LayoutParams.MATCH_PARENT
            ));

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
                            .start()
                    ).start();
        }
    }

    // ---------- 拍照（走 CameraServie） ----------
    private void takePicture() {
        FileLog.getInstance().debug(TAG, "takePicture via CameraX");
        // --- 播放拍照反馈 ---
        playShutterFeedback();
        boolean full = true;
        for (File imageFile : mImageFiles) {
            if (imageFile == null) { full = false; break; }
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
            if (tw <= 0) tw = (int) (64 * getResources().getDisplayMetrics().density);
            if (th <= 0) th = (int) (64 * getResources().getDisplayMetrics().density);
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
                        saveImage(bytes, imageFile, isPortrait);
                        // On UI thread, replace the temp thumbnail with the real one
                        runOnUiThread(() -> {
                            if (placeholderIndex >= 0) {
                                // Remove temp file
                                File old = mImageFiles.get(placeholderIndex);
                                if (old != null && old.exists() && tempFileForReplace != null && old.equals(tempFileForReplace)) {
                                    old.delete();
                                }
                                mImageFiles.set(placeholderIndex, imageFile);
                                int tw = getResources().getDimensionPixelSize(R.dimen.thumbnail_width);
                                int th = getResources().getDimensionPixelSize(R.dimen.thumbnail_height);
                                if (tw <= 0) tw = (int) (64 * getResources().getDisplayMetrics().density);
                                if (th <= 0) th = (int) (64 * getResources().getDisplayMetrics().density);
                                Bitmap thumb = BitmapUtils.decodeSampledBitmapFromFile(imageFile.getAbsolutePath(), tw, th);
                                ImageView iv = mImageViews.get(placeholderIndex);
                                iv.setImageBitmap(thumb);
                                updateOkButtonState();
                            } else {
                                // fallback: insert real thumbnail into first available slot
                                addThumbnail(imageFile, true);
                                updateOkButtonState();
                            }
                        });
                    } catch (Exception e) {
                        FileLog.getInstance().error(TAG, "post-save compress failed", e);
                        runOnUiThread(() -> Toast.makeText(CameraActivity.this, R.string.picture_save_failed, Toast.LENGTH_SHORT).show());
                    } finally {
                        image.close();
                    }
                });
            }
            @Override
            public void onError(@NonNull ImageCaptureException exception) {
                runOnUiThread(() -> Toast.makeText(CameraActivity.this, "Capture failed: " + exception.getMessage(), Toast.LENGTH_SHORT).show());
            }
        });
    }

    private void startCamera() {
        FileLog.getInstance().debug(TAG, "startCamera: begin");
        if (cameraStarted) {
            FileLog.getInstance().debug(TAG, "startCamera: already started, skipping");
            return;
        }
        cameraStarted = true;
        ListenableFuture<ProcessCameraProvider> cameraProviderFuture = ProcessCameraProvider.getInstance(this);
        cameraProviderFuture.addListener(() -> {
            try {
                ProcessCameraProvider cameraProvider = cameraProviderFuture.get();
                FileLog.getInstance().debug(TAG, "startCamera: provider.get() success");
                Preview preview = new Preview.Builder().build();
                preview.setSurfaceProvider(previewView.getSurfaceProvider());
                imageCapture = new ImageCapture.Builder().build();
                FileLog.getInstance().debug(TAG, "startCamera: prepared preview and imageCapture, binding now...");
                CameraSelector cameraSelector = new CameraSelector.Builder()
                        .requireLensFacing(CameraSelector.LENS_FACING_BACK)
                        .build();
                cameraProvider.unbindAll();
                cameraProvider.bindToLifecycle(this, cameraSelector, preview, imageCapture);
                FileLog.getInstance().debug(TAG, "startCamera: bindToLifecycle completed successfully");
            } catch (Exception e) {
                FileLog.getInstance().error(TAG, "startCamera failed: "
                        + e.getClass().getSimpleName() + " - " + e.getMessage(), e);
                // Retry binding later if failed
                previewView.postDelayed(this::startCameraIfNeeded, 300);
            }
        }, ContextCompat.getMainExecutor(this));
    }
}