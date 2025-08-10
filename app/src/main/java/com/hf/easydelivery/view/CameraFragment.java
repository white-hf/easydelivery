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
    private SmsBottomSheetFragment mSmsBottomSheetFragment = new SmsBottomSheetFragment(mOrderId);

    @Override
    public void onStart() {
        super.onStart();
        mImageFiles.forEach(img->{img = null;});
        // 不在 onStart 启动预览，交由 onResume 控制
    }

    @Override
    public void onResume() {
        FileLog.getInstance().debug(TAG, "onResume: init camera if needed, register sensors.");
        super.onResume();

        if (mCameraDevice == null) {
            initCamera();
        } else if (!isPreviewStarted) {
            startPreview();
        }

        if (accelerometer != null && magnetometer != null) {
            sensorManager.registerListener(this, accelerometer, SensorManager.SENSOR_DELAY_NORMAL);
            sensorManager.registerListener(this, magnetometer, SensorManager.SENSOR_DELAY_NORMAL);
        }
    }

    @Override
    public void onPause() {
        FileLog.getInstance().debug(TAG, "onPause: releasing camera and unregistering sensors.");
        closeCamera();
        super.onPause();
        sensorManager.unregisterListener(this);
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

        ImageButton closeButton = view.findViewById(R.id.btn_close);
        if (closeButton != null) {
            closeButton.setOnClickListener(
                    v -> requireActivity().getSupportFragmentManager().popBackStack());
        }

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

        // 8. 初始化相机
        initCamera();

        // 9. 其它功能按钮
        ImageButton cancelButton = view.findViewById(R.id.cancel_button);
        cancelButton.setOnClickListener(v -> requireActivity().getSupportFragmentManager().popBackStack());
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
        imageView.setImageResource(R.drawable.ic_marker_background);

        cardView.addView(imageView);
        mImageViews.add(imageView);
        mCardViews.add(cardView);
        return cardView;
    }

    private boolean checkCameraPermission() {
        return getActivity().checkSelfPermission(android.Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED;
    }

    private void closeCamera()
    {
        FileLog.getInstance().debug(TAG, "closeCamera: invoked.");
        if (mCameraDevice != null) {
            mCameraDevice.close();
            mCameraDevice = null;
            FileLog.getInstance().debug(TAG, "closeCamera: mCameraDevice closed and set to null.");
        }
        isPreviewStarted = false;
    }

    private final CameraDevice.StateCallback mCameraStateCallback =
            new CameraDevice.StateCallback() {
                @Override
                public void onOpened(@NonNull CameraDevice cameraDevice) {
                    mCameraDevice = cameraDevice;
                    startPreview();
                }

                @Override
                public void onDisconnected(@NonNull CameraDevice cameraDevice) {
                    mCameraDevice = cameraDevice;
                    closeCamera();
                }

                @Override
                public void onError(@NonNull CameraDevice cameraDevice, int error) {
                    mCameraDevice = cameraDevice;
                    closeCamera();
                }
            };

    public void startPreview() {
        if (mCameraDevice == null || isPreviewStarted)
            return;

        mCameraPreview.setCameraManager(mCameraManager);

        Surface previewSurface = mCameraPreview.getSurface();
        try {
            final CaptureRequest.Builder previewRequestBuilder = mCameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
            previewRequestBuilder.addTarget(previewSurface);

            if (imageReader == null)
                imageReader = ImageReader.newInstance(
                    mCameraPreview.getWidth(), mCameraPreview.getHeight(),
                    android.graphics.ImageFormat.JPEG, 1);

            List<Surface> outputSurfaces = new ArrayList<>(2);
            outputSurfaces.add(imageReader.getSurface());
            outputSurfaces.add(mCameraPreview.getSurface());

            mCameraDevice.createCaptureSession(outputSurfaces,
                    new CameraCaptureSession.StateCallback() {
                        @Override
                        public void onConfigured(@NonNull CameraCaptureSession session) {
                            mCaptureSession = session;
                            if (null == mCameraDevice) return;
                            try {
                                previewRequestBuilder.set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE);
                                previewRequestBuilder.set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON_AUTO_FLASH);
                                if (mPreviewRequest == null)
                                    mPreviewRequest = previewRequestBuilder.build();

                                mCaptureSession.setRepeatingRequest(mPreviewRequest, null, null);
                            } catch (CameraAccessException e) {
                                e.printStackTrace();
                            }
                            isPreviewStarted = true; // 只在预览成功后标记
                        }

                        @Override
                        public void onConfigureFailed(@NonNull CameraCaptureSession session) {
                            FileLog.getInstance().error(TAG, "Failed to configure camera capture session");
                        }
                    }, null);

        } catch (CameraAccessException e) {
            FileLog.getInstance().error(TAG, "Error starting camera preview", e);
        }
    }

    // 拍照
    private void takePicture() {
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
                Bitmap thumb = BitmapUtils.decodeSampledBitmapFromFile(
                        imageFile.getAbsolutePath(),
                        getResources().getDimensionPixelSize(R.dimen.thumbnail_width),
                        getResources().getDimensionPixelSize(R.dimen.thumbnail_height)
                );
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
     */
    public void removeThumbnail(int index) {
        mImageFiles.set(index, null);
        mImageViews.get(index).setImageResource(R.drawable.ic_marker_background);
        // 动画已在调用方处理
    }

    /**
     * 失败原因弹窗
     */
    private void showFailReasonDialog() {
        // TODO: 实现失败原因弹窗
        Toast.makeText(getContext(), "失败原因弹窗", Toast.LENGTH_SHORT).show();
    }

    @Override
    public void onDestroyView() {
        FileLog.getInstance().debug(TAG, "onDestroyView: force close camera and clean up resources.");
        closeCamera(); // 确保所有相机资源释放
        super.onDestroyView();
    }
}

