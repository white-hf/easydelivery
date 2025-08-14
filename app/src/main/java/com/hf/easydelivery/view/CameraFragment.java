package com.hf.easydelivery.view;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;

import com.hf.easydelivery.R;
import com.hf.easydelivery.ResourceMgr;
import com.hf.easydelivery.common.BitmapUtils;
import com.hf.easydelivery.dao.DeliveryInfo;
import com.hf.easydelivery.dao.PackageEntity;
import com.hf.easydelivery.core.PendingPackagesMgr;

import android.content.Context;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
// import android.util.Log;
import android.view.Surface;

import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;

import java.util.Arrays;

import android.graphics.Bitmap;
import android.hardware.camera2.CaptureRequest;
import android.media.Image;
import android.media.ImageReader;
import android.os.Environment;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;


import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Objects;


import com.google.android.material.button.MaterialButton;

import androidx.cardview.widget.CardView;
import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.constraintlayout.widget.ConstraintLayout.LayoutParams;

import com.hf.easydelivery.common.FileLog;

/**
 * CameraFragment.java
 *
 * “拍照上传”主流程，主要功能对齐 iOS PhotoViewController，包括：
 * - 顶部信息栏（infoBar）：展示路由号、订单号、客户名、单元号、地址（字段超长省略）
 * - 缩略图栏（thumbBar）：最多3张图片，支持添加、删除、点击放大，全动画（scale/alpha）
 * - 拍照/相册/重拍按钮：可拍照、选相册、重拍最后一张
 * - 下方四大操作（actionBar2）：短信、拨号、失败原因弹窗、完成
 * - 校验图片数量，最少2张，少于2张时完成按钮置灰且toast提示
 * - 全屏预览与删除（DialogFragment/Activity）
 * - 批量包裹切换，完成后自动切换下一个包裹，无则返回主页
 * - 所有用户提示、逻辑、UI动画严格对齐 iOS，所有行为有注释
 */

public class CameraFragment extends Fragment implements SensorEventListener {
    // 标记预览是否已启动，避免重复
    private boolean isPreviewStarted = false;

    // 防重绑：相机是否已绑定（打开并完成预览配置）
    private boolean cameraBound = false;

    // 复用实例时新任务参数（可选）
    private Long pendingOrderId = null;
    private Double pendingLatitude = null;
    private Double pendingLongitude = null;

    private static final int SMS_PERMISSION_REQUEST_CODE = 1;
    private static final int CALL_PERMISSION_REQUEST_CODE = 2;
    private static final String TAG = "CameraActivity";
    private static final int CAMERA_PERMISSION_REQUEST_CODE = 1001;
    public static final int IMAGE_COUNT = 2;
    private static final int MAX_PHOTOS = 3;

    // ----------- UI控件成员变量 -----------
    // infoBar 相关
    private CardView infoBar; // 顶部信息栏整体（由 LinearLayout 改为 CardView）
    private TextView tvRouteNumber;
    private TextView tvOrderSn;
    private TextView tvCustomerName;
    private TextView tvUnitNumber;
    private TextView tvAddress;

    // 缩略图栏
    private LinearLayout thumbnailContainer; // 缩略图栏（保持 LinearLayout 不变）
    private List<File> mImageFiles = new ArrayList<>();
    private List<ImageView> mImageViews = new ArrayList<>();
    private List<CardView> mCardViews = new ArrayList<>();

    // 拍照/相册/重拍栏
    private ImageButton captureButton, galleryButton, retakeButton;

    // 下方 actionBar2 操作栏

    private MaterialButton smsButton;
    private MaterialButton phoneButton;
    private MaterialButton failButton;
    private MaterialButton okButton;

    // 预览
    private FrameLayout cameraPreviewLayout;
    private CameraPreview mCameraPreview;

    // 相机相关
    private CameraManager mCameraManager;
    private CameraDevice mCameraDevice = null;
    private CameraCaptureSession mCaptureSession;
    private ImageReader imageReader;
    private CaptureRequest mPreviewRequest;

    // 传感器
    private SensorManager sensorManager;
    private Sensor accelerometer;
    private Sensor magnetometer;
    private float[] accelerometerReading = new float[3];
    private float[] magnetometerReading = new float[3];
    private boolean isPortrait = false; // 默认是横屏

    private Long mOrderId;
    private double mLatitude;
    private double mLongitude;

    // 短信弹窗
    private SmsBottomSheetFragment mSmsBottomSheetFragment;

    private View hostToolbar;
    private View hostBottomBar;

    private void prepareHostChromeRefs() {
        // 使用资源名称动态查找，避免编译期直接引用不存在的 R.id.*
        try {
            String pkg = requireActivity().getPackageName();
            String[] toolbarNames = new String[]{"toolbar", "appbar", "top_bar"};
            for (String name : toolbarNames) {
                int resId = getResources().getIdentifier(name, "id", pkg);
                if (resId != 0) {
                    View v = requireActivity().findViewById(resId);
                    if (v != null) { hostToolbar = v; break; }
                }
            }

            String[] bottomNames = new String[]{"bottom_nav", "nav_view", "tab_layout", "bottom_bar"};
            for (String name : bottomNames) {
                int resId = getResources().getIdentifier(name, "id", pkg);
                if (resId != 0) {
                    View v = requireActivity().findViewById(resId);
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
            final Window window = requireActivity().getWindow();
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
            final Window window = requireActivity().getWindow();
            View decor = window.getDecorView();
            decor.setSystemUiVisibility(View.SYSTEM_UI_FLAG_LAYOUT_STABLE);
            window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        } catch (Exception e) {
            FileLog.getInstance().debug("CameraActivity", "exitImmersiveFullscreen: " + e.getMessage());
        }
    }

    @Override
    public void onStart() {
        super.onStart();
        // 不在 onStart 启动预览，交由 onResume 控制
        FileLog.getInstance().debug(TAG, "onStart: no-op for thumbnails (kept as-is)");
    }

    @Override
    public void onResume() {
        FileLog.getInstance().debug(TAG, "onResume: init camera if needed, register sensors.");
        super.onResume();
        prepareHostChromeRefs();
        enterImmersiveFullscreen();
        hideHostChrome();

        startCameraIfNeeded();

        if (accelerometer != null && magnetometer != null) {
            sensorManager.registerListener(this, accelerometer, SensorManager.SENSOR_DELAY_NORMAL);
            sensorManager.registerListener(this, magnetometer, SensorManager.SENSOR_DELAY_NORMAL);
        }
    }

    @Override
    public void onPause() {
        FileLog.getInstance().debug(TAG, "onPause: releasing camera and unregistering sensors.");
        exitImmersiveFullscreen();
        showHostChrome();
        stopCameraIfBound();
        super.onPause();
        if (sensorManager != null) sensorManager.unregisterListener(this);
    }


    @Override
    public void onDestroyView() {
        FileLog.getInstance().debug(TAG, "onDestroyView: force close camera and clean up resources.");
        showHostChrome();   // 再保险
        stopCameraIfBound();
        super.onDestroyView();
    }

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
            float roll = orientation[2];

            // 根据 pitch 和 roll 判断是横屏还是竖屏
            if (Math.abs(pitch) > Math.PI / 4) {
                isPortrait = true;
            } else {
                isPortrait = false;
            }

            if (isPortrait) {
                FileLog.getInstance().debug("Orientation", "Portrait");
                // 竖屏
            } else {
                FileLog.getInstance().debug("Orientation", "Landscape");
                // 横屏
            }
        }
    }

    /**
     * Called when the accuracy of the registered sensor has changed.  Unlike
     * onSensorChanged(), this is only called when this accuracy value changes.
     *
     * <p>See the SENSOR_STATUS_* constants in
     * {@link SensorManager SensorManager} for details.
     *
     * @param sensor
     * @param accuracy The new accuracy of this sensor, one of
     *                 {@code SensorManager.SENSOR_STATUS_*}
     */
    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {

    }


    private void initCamera()
    {
        mCameraManager = null;
        mCameraManager = (CameraManager) getActivity().getSystemService(Context.CAMERA_SERVICE);
        if (checkCameraPermission() && mCameraManager != null) {
            try {
                String cameraId = mCameraManager.getCameraIdList()[0];
                mCameraManager.openCamera(cameraId, mCameraStateCallback, null);
            } catch (CameraAccessException e) {
                FileLog.getInstance().error(TAG, "Error accessing camera", e);
            }
        }
        else
            requestCameraPermission();
    }

    private void requestCameraPermission() {
        ActivityCompat.requestPermissions(getActivity(),
                new String[]{Manifest.permission.CAMERA},
                CAMERA_PERMISSION_REQUEST_CODE);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == CAMERA_PERMISSION_REQUEST_CODE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                // Permission granted, initialize the camera
                initCamera();
            } else {
                // Permission denied, show a message to the user
                Toast.makeText(getContext(), "Camera permission is required to use this feature", Toast.LENGTH_SHORT).show();
            }
        }
    }


    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_camera, container, false);

        // 1. 获取参数
        Bundle args = getArguments();
        if (args != null) {
            mOrderId = args.getLong("order_id", -1);
            mLatitude = args.getDouble("latitude", -1);
            mLongitude = args.getDouble("longitude", -1);
        }
        // -- initialize SmsBottomSheetFragment here with valid mOrderId
        mSmsBottomSheetFragment = new SmsBottomSheetFragment(mOrderId);

        // 2. infoBar 顶部信息栏
        infoBar = (CardView) view.findViewById(R.id.info_bar);
        tvRouteNumber = view.findViewById(R.id.tv_route_number);
        tvOrderSn = view.findViewById(R.id.tv_tracking_number); // 订单号id规范为tv_tracking_number
        tvCustomerName = view.findViewById(R.id.tv_customer_name);
        tvUnitNumber = view.findViewById(R.id.tv_unit_number);
        tvAddress = view.findViewById(R.id.tv_address);
        // 填充 infoBar 字段，超长省略
        final DeliveryInfo deliveryInfo = ResourceMgr.getInstance().getDeliveryinfoMgr().get(mOrderId);
        if (deliveryInfo != null) {
            tvRouteNumber.setText(String.valueOf(deliveryInfo.getRouteNumber()));
            tvOrderSn.setText(deliveryInfo.getOrderSn());
            tvCustomerName.setText(ellipsis(deliveryInfo.getName(), 8));
            tvUnitNumber.setText(deliveryInfo.getUnitNumber());
            tvAddress.setText(ellipsis(deliveryInfo.getAddress(), 15));
        }

        // 3. 缩略图栏 thumbnailContainer
        thumbnailContainer = (LinearLayout) view.findViewById(R.id.thumbnail_container);
        mImageFiles.clear();
        mImageViews.clear();
        mCardViews.clear();
        for (int i = 0; i < MAX_PHOTOS; i++) {
            CardView cardView = createThumbnailCardView(i);
            thumbnailContainer.addView(cardView);
            mImageFiles.add(null);
        }

        // 4. 拍照/相册/重拍栏
        captureButton = view.findViewById(R.id.shutter_button);
        galleryButton = view.findViewById(R.id.gallery_button);
        retakeButton = view.findViewById(R.id.retake_button);
        // 拍照按钮
        captureButton.setOnClickListener(v -> takePicture());
        // 相册按钮
        galleryButton.setOnClickListener(v -> openGallery());
        // 重拍按钮，删除最后一张
        retakeButton.setOnClickListener(v -> {
            removeLastThumbnail();
        });

        // 5. 下方操作栏
        smsButton  = (MaterialButton) view.findViewById(R.id.sms_button);
        phoneButton = (MaterialButton) view.findViewById(R.id.phone_button);
        failButton  = (MaterialButton) view.findViewById(R.id.fail_button);
        okButton    = (MaterialButton) view.findViewById(R.id.ok_button);
        // 短信
        smsButton.setOnClickListener(v -> showSmsBottomSheet());
        // 拨号
        phoneButton.setOnClickListener(v -> makeCall());
        // 失败原因弹窗
        failButton.setOnClickListener(v -> showFailReasonDialog());
        // 完成
        okButton.setOnClickListener(v -> {
            int count = (int) mImageFiles.stream().filter(Objects::nonNull).count();
            if (count < IMAGE_COUNT) {
                okButton.setEnabled(false);
                Toast.makeText(getContext(), getString(R.string.take_picture), Toast.LENGTH_SHORT).show();
                return;
            }
            okButton.setEnabled(true);
            PackageEntity packageEntity = deliveryInfo.transferToPackageEntity();
            packageEntity.createTime = System.currentTimeMillis();
            packageEntity.imagePath = Arrays.toString(mImageFiles.stream().filter(Objects::nonNull).map(File::getAbsolutePath).toArray(String[]::new));
            packageEntity.latitude = mLatitude;
            packageEntity.longitude = mLongitude;
            packageEntity.status = PendingPackagesMgr.PackageStatus.Pending.getStatus();
            ResourceMgr.getInstance().getPendingPackagesMgr().save(packageEntity);
            // 清空缩略图，自动切换下一个包裹，如无则返回主页
            clearThumbnails();
            if (!switchToNextPackage()) {
                requireActivity().getSupportFragmentManager().popBackStack();
            }
        });


        // 完成按钮初始校验
        updateOkButtonState();

        // 6. 预览区
        cameraPreviewLayout = view.findViewById(R.id.camera_preview);
        mCameraPreview = new CameraPreview(getContext());
        mCameraPreview.setCameraActivity(this);
        cameraPreviewLayout.addView(mCameraPreview);
        // 7. 传感器
        sensorManager = (SensorManager) requireActivity().getSystemService(Context.SENSOR_SERVICE);
        if (sensorManager != null) {
            accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
            magnetometer = sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD);
        } else {
            Toast.makeText(getContext(), "Sensor not available", Toast.LENGTH_SHORT).show();
            requireActivity().getSupportFragmentManager().popBackStack();
        }

        // 1) 悬浮关闭键 —— 若布局中无 @id/btn_close，则动态创建并添加到根 ConstraintLayout
        ImageButton closeButton = view.findViewById(R.id.btn_close);
        if (closeButton == null) {
            try {
                // 根容器必须是 ConstraintLayout（fragment_camera.xml 的根就是）
                ConstraintLayout root = (ConstraintLayout) view;
                closeButton = new ImageButton(requireContext());
                // 若 R.id.btn_close 不存在则动态生成一个 id
                int closeId = getResources().getIdentifier("btn_close", "id", requireContext().getPackageName());
                if (closeId != 0) {
                    closeButton.setId(closeId);
                } else {
                    closeButton.setId(View.generateViewId());
                }
                // 样式与尺寸：40dp，圆形无边框点击效果，白色图标
                int size = (int) (40 * getResources().getDisplayMetrics().density);
                LayoutParams lp = new LayoutParams(size, size);
                lp.topToTop = LayoutParams.PARENT_ID;
                lp.startToStart = LayoutParams.PARENT_ID;
                int margin = (int) (12 * getResources().getDisplayMetrics().density);
                lp.setMargins(margin, margin, margin, margin);
                closeButton.setLayoutParams(lp);
                closeButton.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
                closeButton.setBackgroundResource(android.R.drawable.btn_default_small);
                closeButton.setImageResource(android.R.drawable.ic_menu_close_clear_cancel);
                closeButton.setColorFilter(android.graphics.Color.WHITE);
                closeButton.setContentDescription(getString(android.R.string.cancel));
                closeButton.setElevation(24f);
                // 添加到根布局
                root.addView(closeButton);
                FileLog.getInstance().debug(TAG, "Floating close button created programmatically.");
            } catch (Exception e) {
                FileLog.getInstance().error(TAG, "create floating close button failed", e);
            }
        } else {
            // 如果布局里已有，则确保可见并放到最上层
            closeButton.setVisibility(View.VISIBLE);
            closeButton.bringToFront();
        }
        // 点击关闭：退出拍照界面
        if (closeButton != null) {
            closeButton.setOnClickListener(v -> {
                try {
                    requireActivity().getSupportFragmentManager().popBackStack();
                } catch (Exception e) {
                    FileLog.getInstance().error(TAG, "closeButton popBackStack error", e);
                }
            });
        }


// 2) 隐藏 infoBar 里的 cancel_button，避免占位（若布局无此ID，安全跳过）
        try {
            int cancelId = getResources().getIdentifier("cancel_button", "id", requireContext().getPackageName());
            if (cancelId != 0) {
                View cb = view.findViewById(cancelId);
                if (cb != null) cb.setVisibility(View.GONE);
            }
        } catch (Exception e) {
            FileLog.getInstance().debug(TAG, "optional cancel_button not found: " + e.getMessage());
        }

        // 8. 初始化相机
        initCamera();

        cameraBound = false; // 新视图创建后，等待 onResume 按需绑定

        // prepareHostChromeRefs() is now called in onResume before hideHostChrome
        return view;
    }

    /**
     * 字符串超长省略
     */
    private String ellipsis(String str, int maxLen) {
        if (str == null) return "";
        if (str.length() <= maxLen) return str;
        return str.substring(0, maxLen) + "...";
    }

    /**
     * 校验图片数量，动态置灰完成按钮
     */
    private void updateOkButtonState() {
        int count = (int) mImageFiles.stream().filter(Objects::nonNull).count();
        if (okButton != null) {
            boolean enabled = count >= IMAGE_COUNT;
            okButton.setEnabled(enabled);
            okButton.setAlpha(enabled ? 1f : 0.4f);
        }
    }

    /**
     * 打开相册选取图片
     */
    private static final int REQUEST_CODE_PICK_IMAGE = 2001;
    private void openGallery() {
        if (mImageFiles.stream().filter(Objects::nonNull).count() >= MAX_PHOTOS) {
            Toast.makeText(getContext(), getString(R.string.take_picture_full), Toast.LENGTH_SHORT).show();
            return;
        }
        Intent intent = new Intent(Intent.ACTION_PICK);
        intent.setType("image/*");
        startActivityForResult(intent, REQUEST_CODE_PICK_IMAGE);
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_CODE_PICK_IMAGE && resultCode == getActivity().RESULT_OK && data != null) {
            Uri uri = data.getData();
            if (uri != null) {
                try {
                    File file = createImageFile();
                    Bitmap bitmap = BitmapFactory.decodeStream(getActivity().getContentResolver().openInputStream(uri));
                    // 压缩到120KB并等比缩放
                    bitmap = BitmapUtils.compressBitmapToTarget(bitmap, 120 * 1024);
                    FileOutputStream fos = new FileOutputStream(file);
                    bitmap.compress(Bitmap.CompressFormat.JPEG, 80, fos);
                    fos.close();
                    addThumbnail(file, true);
                    updateOkButtonState();
                } catch (Exception e) {
                    Toast.makeText(getContext(), "图片选取失败", Toast.LENGTH_SHORT).show();
                }
            }
        }
    }

    /**
     * 删除最后一张图片
     */
    private void removeLastThumbnail() {
        for (int i = MAX_PHOTOS - 1; i >= 0; i--) {
            if (mImageFiles.get(i) != null) {
                final int index = i; // 关键：定义一个final变量
                ImageView iv = mImageViews.get(i);
                iv.animate().scaleX(0.7f).scaleY(0.7f).alpha(0f).setDuration(200).withEndAction(() -> {
                    removeThumbnail(index); // 用final变量
                    iv.setScaleX(1f);
                    iv.setScaleY(1f);
                    iv.setAlpha(1f);
                    updateOkButtonState();
                }).start();
                break;
            }
        }
    }

    /**
     * 清空缩略图
     */
    private void clearThumbnails() {
        for (int i = 0; i < MAX_PHOTOS; i++) {
            removeThumbnail(i);

        }
        updateOkButtonState();
    }

    /**
     * 切换下一个包裹（伪实现：返回false表示无下一个包裹）
     */
    private boolean switchToNextPackage() {
        // TODO: 实现批量包裹切换，返回true表示已切换，false表示无下一个包裹
        return false;
    }

    private void showSmsBottomSheet() {
        mSmsBottomSheetFragment.setOrderId(mOrderId);
        mSmsBottomSheetFragment.show(requireActivity().getSupportFragmentManager(), "SmsBottomSheetFragment");
    }

    private void makeCall() {
        if (ContextCompat.checkSelfPermission(getContext(), Manifest.permission.CALL_PHONE) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(getActivity(), new String[]{Manifest.permission.CALL_PHONE}, CALL_PERMISSION_REQUEST_CODE);
        } else {
            final DeliveryInfo deliveryInfo = ResourceMgr.getInstance().getDeliveryinfoMgr().get(mOrderId);
            if (deliveryInfo != null) {
                Intent callIntent = new Intent(Intent.ACTION_CALL);
                callIntent.setData(Uri.parse("tel:" + deliveryInfo.getPhone()));
                startActivity(callIntent);
            }
        }
    }

    // private ImageView createEmptyImageView(int index) {
    //     ImageView imageView = new ImageView(requireContext());
    //     // 使用80x80dp，若有R.dimen.thumbnail_width/height则用资源，否则直接写死80
    //     int sizePx = (int) (80 * getResources().getDisplayMetrics().density); // 80dp->px
    //     LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(sizePx, sizePx);
    //     params.setMargins(8, 0, 8, 0);
    //     imageView.setLayoutParams(params);
    //     imageView.setTag(index);
    //     imageView.setOnClickListener(v -> showFullImage((int) v.getTag()));
    //     imageView.setImageResource(R.drawable.ic_marker_background); // Set a placeholder image
    //     return imageView;
    // }

    private CardView createThumbnailCardView(int index) {
        Context ctx = requireContext();

        CardView cardView = new CardView(ctx);
        int sizePx = (int) (64 * ctx.getResources().getDisplayMetrics().density); // 64dp
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(sizePx, sizePx);
        params.setMargins(8, 0, 8, 0);
        cardView.setLayoutParams(params);
        cardView.setRadius(10 * ctx.getResources().getDisplayMetrics().density); // 10dp
        cardView.setCardElevation(2 * ctx.getResources().getDisplayMetrics().density);
        cardView.setUseCompatPadding(true);

        ImageView imageView = new ImageView(ctx);
        imageView.setLayoutParams(new FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
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

    private boolean checkCameraPermission() {
        return getActivity().checkSelfPermission(android.Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED;
    }

    private void closeCamera() {
        FileLog.getInstance().debug(TAG, "closeCamera: invoked.");
        try {
            if (mCaptureSession != null) {
                mCaptureSession.stopRepeating();
                mCaptureSession.close();
            }
        } catch (Exception ignore) {}
        mCaptureSession = null;

        if (imageReader != null) {
            try { imageReader.close(); } catch (Exception ignore) {}
            imageReader = null;
        }

        if (mCameraDevice != null) {
            try { mCameraDevice.close(); } catch (Exception ignore) {}
            mCameraDevice = null;
            FileLog.getInstance().debug(TAG, "closeCamera: mCameraDevice closed and set to null.");
        }
        isPreviewStarted = false;
        cameraBound = false;
    }

    private final CameraDevice.StateCallback mCameraStateCallback =
            new CameraDevice.StateCallback() {
                @Override
                public void onOpened(@NonNull CameraDevice cameraDevice) {
                    mCameraDevice = cameraDevice;
                    cameraBound = true;
                    FileLog.getInstance().debug(TAG, "mCameraStateCallback.onOpened: cameraBound=true");
                    startPreview();
                }

                @Override
                public void onDisconnected(@NonNull CameraDevice cameraDevice) {
                    mCameraDevice = cameraDevice;
                    closeCamera();
                    cameraBound = false;
                    FileLog.getInstance().debug(TAG, "mCameraStateCallback.onDisconnected/onError: cameraBound=false");
                }

                @Override
                public void onError(@NonNull CameraDevice cameraDevice, int error) {
                    mCameraDevice = cameraDevice;
                    closeCamera();
                    cameraBound = false;
                    FileLog.getInstance().debug(TAG, "mCameraStateCallback.onDisconnected/onError: cameraBound=false");
                }
            };

    public void startPreview() {
        if (mCameraDevice == null || isPreviewStarted) return;

        FileLog.getInstance().debug(TAG, "startPreview: enter (bound=" + cameraBound + ", previewStarted=" + isPreviewStarted + ")");

        // Ensure preview surface has valid size; if not, retry after layout.
        final int w = mCameraPreview != null ? mCameraPreview.getWidth() : 0;
        final int h = mCameraPreview != null ? mCameraPreview.getHeight() : 0;
        if (w <= 0 || h <= 0) {
            FileLog.getInstance().debug(TAG, "startPreview: preview size not ready (w=" + w + ", h=" + h + "), retry in 16ms");
            if (cameraPreviewLayout != null) {
                cameraPreviewLayout.postDelayed(this::startPreview, 16);
            }
            return;
        }

        mCameraPreview.setCameraManager(mCameraManager);
        final Surface previewSurface = mCameraPreview.getSurface();
        try {
            final CaptureRequest.Builder previewRequestBuilder = mCameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
            previewRequestBuilder.addTarget(previewSurface);

            if (imageReader == null) {
                imageReader = ImageReader.newInstance(w, h, android.graphics.ImageFormat.JPEG, 1);
            }

            List<Surface> outputSurfaces = new ArrayList<>(2);
            outputSurfaces.add(imageReader.getSurface());
            outputSurfaces.add(previewSurface);

            mCameraDevice.createCaptureSession(outputSurfaces, new CameraCaptureSession.StateCallback() {
                @Override
                public void onConfigured(@NonNull CameraCaptureSession session) {
                    mCaptureSession = session;
                    if (mCameraDevice == null) return;
                    try {
                        previewRequestBuilder.set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE);
                        previewRequestBuilder.set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON_AUTO_FLASH);
                        if (mPreviewRequest == null) mPreviewRequest = previewRequestBuilder.build();
                        mCaptureSession.setRepeatingRequest(mPreviewRequest, null, null);
                        isPreviewStarted = true;
                        cameraBound = true;
                        FileLog.getInstance().debug(TAG, "startPreview: configured successfully (" + w + "x" + h + ")");
                    } catch (CameraAccessException e) {
                        FileLog.getInstance().error(TAG, "startPreview: CameraAccessException in onConfigured", e);
                    }
                }

                @Override
                public void onConfigureFailed(@NonNull CameraCaptureSession session) {
                    FileLog.getInstance().error(TAG, "startPreview: onConfigureFailed");
                }
            }, null);

        } catch (CameraAccessException e) {
            FileLog.getInstance().error(TAG, "startPreview: CameraAccessException", e);
        }
    }

    // 拍照
    private void takePicture() {
        FileLog.getInstance().debug(TAG, "takePicture: cameraBound=" + cameraBound + ", device=" + (mCameraDevice != null));
        if (mCameraDevice == null) {
            FileLog.getInstance().error(TAG, "CameraDevice is null. Cannot take picture.");
            return;
        }

        boolean bFull = true;
        for (File imageFile : mImageFiles)
            if (imageFile == null) {
                bFull = false;
                break;
            }

        if (bFull)
        {
            Toast.makeText(ResourceMgr.getInstance().getCtx(), getString(R.string.take_picture_full), Toast.LENGTH_SHORT).show();
            return;
        }

        try {
            // 获取图片文件路径
            File imageFile = createImageFile();

            // 创建图片保存的回调
            ImageReader.OnImageAvailableListener readerListener = reader->{
                Image image = null;
                try {
                    image = reader.acquireLatestImage();
                    ByteBuffer buffer = image.getPlanes()[0].getBuffer();
                    byte[] bytes = new byte[buffer.capacity()];
                    buffer.get(bytes);

                    // 保存图片
                    saveImage(bytes, imageFile,true);

                    // 将缩略图显示在列表中
                    addThumbnail(imageFile);

                    mCaptureSession.setRepeatingRequest(mPreviewRequest, null, null);
                    Toast.makeText(getContext(), getActivity().getString(R.string.picture_saved), Toast.LENGTH_SHORT).show();

                } catch (Exception e) {
                    FileLog.getInstance().error(TAG, "Error saving image", e);
                } finally {
                    if (image != null) {
                        image.close();
                    }
                }
            };


            // 设置图片保存的监听器
            imageReader.setOnImageAvailableListener(readerListener, null);

            final CaptureRequest.Builder captureBuilder =
                    mCameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE);
            captureBuilder.addTarget(imageReader.getSurface());
            captureBuilder.set(CaptureRequest.CONTROL_MODE,
                    CaptureRequest.CONTROL_MODE_AUTO);

            // 开始拍照
            mCaptureSession.stopRepeating();
            mCaptureSession.capture(captureBuilder.build(), null, null);
        } catch (CameraAccessException e) {
            FileLog.getInstance().error(TAG, "Error taking picture", e);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    /** 按需启动相机：已绑定则不重复；未绑定则初始化/启动 */
    private void startCameraIfNeeded() {
        if (cameraBound) {
            FileLog.getInstance().debug(TAG, "startCameraIfNeeded: already bound, skip.");
            if (!isPreviewStarted) {
                startPreview();
            }
            return;
        }
        if (mCameraDevice == null) {
            initCamera();
        } else {
            startPreview();
        }
    }

    /** 停止相机预览并释放资源（若已绑定） */
    private void stopCameraIfBound() {
        if (!cameraBound && mCameraDevice == null && !isPreviewStarted) {
            FileLog.getInstance().debug(TAG, "stopCameraIfBound: nothing to stop.");
            return;
        }
        try {
            if (mCaptureSession != null) {
                mCaptureSession.stopRepeating();
                mCaptureSession.close();
            }
        } catch (Exception ignore) {}
        mCaptureSession = null;

        if (imageReader != null) {
            try { imageReader.close(); } catch (Exception ignore) {}
            imageReader = null;
        }

        closeCamera();      // 将 cameraDevice 关闭并复位标志位
        cameraBound = false;
        FileLog.getInstance().debug(TAG, "stopCameraIfBound: camera released.");
    }

    /**
     * 复用 CameraFragment 实例时，应用一个新的任务（包裹）：
     * - 更新 orderId/坐标
     * - 刷新 infoBar 文本
     * - 清空旧的缩略图
     */
    public void applyNewTask(@NonNull Long orderId, double latitude, double longitude) {
        this.mOrderId = orderId;
        this.mLatitude = latitude;
        this.mLongitude = longitude;

        try {
            final DeliveryInfo deliveryInfo = ResourceMgr.getInstance().getDeliveryinfoMgr().get(mOrderId);
            if (deliveryInfo != null) {
                if (tvRouteNumber != null) tvRouteNumber.setText(String.valueOf(deliveryInfo.getRouteNumber()));
                if (tvOrderSn != null) tvOrderSn.setText(deliveryInfo.getOrderSn());
                if (tvCustomerName != null) tvCustomerName.setText(ellipsis(deliveryInfo.getName(), 8));
                if (tvUnitNumber != null) tvUnitNumber.setText(deliveryInfo.getUnitNumber());
                if (tvAddress != null) tvAddress.setText(ellipsis(deliveryInfo.getAddress(), 15));
            }
            clearThumbnails();
            updateOkButtonState();
            if (mSmsBottomSheetFragment != null) {
                mSmsBottomSheetFragment.setOrderId(mOrderId);
            }
            FileLog.getInstance().debug(TAG, "applyNewTask: updated UI for orderId=" + orderId);
        } catch (Exception e) {
            FileLog.getInstance().error(TAG, "applyNewTask error", e);
        }
    }

    // 创建图片文件
    private File createImageFile() throws IOException {
        String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(new Date());
        String imageFileName = "JPEG_" + timeStamp + "_";
        File storageDir = getActivity().getExternalFilesDir(Environment.DIRECTORY_PICTURES);
        File imageFile = File.createTempFile(
                imageFileName,
                ".jpg",
                storageDir
        );
        return imageFile;
    }

    /**
     * 保存图片，压缩到120KB并等比缩放
     */
    private void saveImage(byte[] bytes, File file, boolean isPortrait) throws IOException {
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inJustDecodeBounds = false;
        Bitmap bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.length);
        // 旋转
        if (isPortrait) {
            Matrix matrix = new Matrix();
            matrix.postRotate(90);
            bitmap = Bitmap.createBitmap(bitmap, 0, 0, bitmap.getWidth(), bitmap.getHeight(), matrix, true);
        }
        // 压缩到120KB
        bitmap = BitmapUtils.compressBitmapToTarget(bitmap, 120 * 1024);
        FileOutputStream output = new FileOutputStream(file);
        bitmap.compress(Bitmap.CompressFormat.JPEG, 80, output);
        output.close();
    }

    private int calculateInSampleSize(BitmapFactory.Options options, int reqWidth, int reqHeight) {
        final int height = options.outHeight;
        final int width = options.outWidth;
        int inSampleSize = 1;

        if (height > reqHeight || width > reqWidth) {
            final int halfHeight = height / 2;
            final int halfWidth = width / 2;

            while ((halfHeight / inSampleSize) >= reqHeight && (halfWidth / inSampleSize) >= reqWidth) {
                inSampleSize *= 2;
            }
        }

        return inSampleSize;
    }

    // 添加缩略图到列表，带动画
    private void addThumbnail(final File imageFile) {
        addThumbnail(imageFile, false);
    }

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

    /**
     * 全屏预览图片，支持删除
     */
    private void showFullImage(int index) {
        File imageFile = mImageFiles.get(index);
        if (imageFile == null) return;
        // 全屏DialogFragment，可删除图片
        FullImageFragment dialog = FullImageFragment.newInstance(imageFile.getAbsolutePath(), index);

        dialog.show(requireActivity().getSupportFragmentManager(), "full_image");
    }

    /**
     * 删除指定缩略图，重布局
     * 真正移除图像内容，取消动画，强制重绘，并更新完成按钮状态
     */
    public void removeThumbnail(int index) {
        if (index < 0 || index >= mImageFiles.size()) return;

        // 1) 数据层清空并尝试删除文件（可选）
        File f = mImageFiles.get(index);
        mImageFiles.set(index, null);
        if (f != null && f.exists()) {
            // noinspection ResultOfMethodCallIgnored
            f.delete();
        }

        // 2) 视图层复位：一定要把 image 本身清掉，而不是只换 background
        ImageView iv = (index < mImageViews.size()) ? mImageViews.get(index) : null;
        if (iv == null) return;

        // 取消可能的动画，避免动画完成后把旧位图又“带回来”
        iv.animate().cancel();

        // 真正移除图像内容
        iv.setImageDrawable(null);
        iv.setImageBitmap(null);

        // 复位属性，防止残留的缩放/透明度
        iv.setAlpha(1f);
        iv.setScaleX(1f);
        iv.setScaleY(1f);

        // 保留圆角底或占位底
        iv.setBackgroundResource(R.drawable.bg_thumb_image_rounded);

        // 强制重绘
        iv.invalidate();
        if (thumbnailContainer != null) {
            thumbnailContainer.invalidate();
            thumbnailContainer.requestLayout();
        }

        // 3) 更新“完成”按钮可用态
        updateOkButtonState();
    }

    /**
     * 失败原因弹窗
     */
    private void showFailReasonDialog() {
        // TODO: 实现失败原因弹窗
        Toast.makeText(getContext(), "失败原因弹窗", Toast.LENGTH_SHORT).show();
    }

}

