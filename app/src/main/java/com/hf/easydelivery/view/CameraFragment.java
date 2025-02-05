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
import android.util.Log;
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

public class CameraFragment extends Fragment implements SensorEventListener {

    private static final int SMS_PERMISSION_REQUEST_CODE = 1;
    private static final int CALL_PERMISSION_REQUEST_CODE = 2;

    private static final String TAG = "CameraActivity";
    private static final int CAMERA_PERMISSION_REQUEST_CODE = 1001;
    public static final int IMAGE_COUNT = 2;

    private CameraManager mCameraManager;
    private CameraDevice mCameraDevice = null;
    private CameraCaptureSession mCaptureSession;
    private CameraPreview mCameraPreview;

    private ImageReader imageReader;

    private static final int MAX_PHOTOS = 3;
    private LinearLayout mThumbnailContainer;
    private List<File> mImageFiles = new ArrayList<>();
    private List<ImageView> mImageViews = new ArrayList<>();

    private CaptureRequest mPreviewRequest;

    private SensorManager sensorManager;
    private Sensor accelerometer;
    private Sensor magnetometer;

    private float[] accelerometerReading = new float[3];
    private float[] magnetometerReading = new float[3];

    private boolean isPortrait = false; // 默认是横屏

    private Long mOrderId;

    private double mLatitude;
    private double mLongitude;

    private SmsBottomSheetFragment mSmsBottomSheetFragment = new SmsBottomSheetFragment(mOrderId);

    @Override
    public void onStart() {
        super.onStart();

        mImageFiles.forEach(img->{img = null;});
        startPreview();
    }

    @Override
    public void onResume() {
        super.onResume();

        if (mCameraDevice == null)
            initCamera();

        startPreview();

        if (accelerometer != null && magnetometer != null) {
            sensorManager.registerListener(this, accelerometer, SensorManager.SENSOR_DELAY_NORMAL);
            sensorManager.registerListener(this, magnetometer, SensorManager.SENSOR_DELAY_NORMAL);
        }
    }

    @Override
    public void onPause() {
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
                Log.d("Orientation", "Portrait");
                // 竖屏
            } else {
                Log.d("Orientation", "Landscape");
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
                Log.e(TAG, "Error accessing camera", e);
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

        Bundle args = getArguments();
        if (args != null) {
            mOrderId = args.getLong("order_id", -1);
            mLatitude = args.getDouble("latitude", -1);
            mLongitude = args.getDouble("longitude", -1);
        }

        TextView orderInfoTextView = view.findViewById(R.id.orderInfo_textview);
        final DeliveryInfo deliveryInfo = ResourceMgr.getInstance().getDeliveryinfoMgr().get(mOrderId);
        if (deliveryInfo != null) {
            orderInfoTextView.setText(String.valueOf(deliveryInfo.getRouteNumber() + "|" + deliveryInfo.getAddress()));
        }

        initCamera();

        // initialize the thumbnail container
        mThumbnailContainer = view.findViewById(R.id.thumbnail_container);
        // Initialize photoPaths with empty strings and create empty ImageViews
        for (int i = 0; i < MAX_PHOTOS; i++) {
            ImageView imageView = createEmptyImageView(i);
            mImageViews.add(imageView);
            mThumbnailContainer.addView(imageView);
            mImageFiles.add(null);
        }

        //
        Button cancelButton = view.findViewById(R.id.cancel_button);
        cancelButton.setOnClickListener(v->{
            requireActivity().getSupportFragmentManager().popBackStack();
        });

        Button okButton = view.findViewById(R.id.ok_button);
        okButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                //save the data of the package delivered to local db and the queue for uploading to the server
                int i = 0;
                for (File imageFile : mImageFiles)
                    if (imageFile != null)
                        i++;

                if (i < IMAGE_COUNT) {
                    Toast.makeText(ResourceMgr.getInstance().getCtx(), getString(R.string.take_picture), Toast.LENGTH_SHORT).show();
                    return;
                }

                PackageEntity packageEntity = deliveryInfo.transferToPackageEntity();
                packageEntity.createTime = System.currentTimeMillis();
                packageEntity.imagePath = Arrays.toString(mImageFiles.stream().filter(Objects::nonNull).map(File::getAbsolutePath).toArray(String[]::new));
                packageEntity.latitude = mLatitude;
                packageEntity.longitude = mLongitude;
                packageEntity.status = PendingPackagesMgr.PackageStatus.Pending.getStatus();

                ResourceMgr.getInstance().getPendingPackagesMgr().save(packageEntity);
                requireActivity().getSupportFragmentManager().popBackStack();
            }
        });


        Button captureButton = view.findViewById(R.id.capture_button);
        captureButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                takePicture();
            }
        });


        FrameLayout previewLayout = view.findViewById(R.id.camera_preview);
        mCameraPreview = null;
        mCameraPreview = new CameraPreview(getContext());
        mCameraPreview.setCameraActivity(this);
        previewLayout.addView(mCameraPreview);

        sensorManager = (SensorManager) requireActivity().getSystemService(Context.SENSOR_SERVICE);
        if (sensorManager != null) {
            accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
            magnetometer = sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD);
        } else {
            Toast.makeText(getContext(), "Sensor not available", Toast.LENGTH_SHORT).show();
            requireActivity().getSupportFragmentManager().popBackStack();
        }

        Button smsButton = view.findViewById(R.id.sms_button);
        Button phoneButton = view.findViewById(R.id.phone_button);

        smsButton.setOnClickListener(v -> showSmsBottomSheet());
        phoneButton.setOnClickListener(v -> makeCall());

        return view;
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

    private ImageView createEmptyImageView(int index) {
        ImageView imageView = new ImageView(requireContext());
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(400, 400);
        params.setMargins(8, 0, 8, 0);
        imageView.setLayoutParams(params);
        imageView.setTag(index);
        imageView.setOnClickListener(v -> showFullImage((int) v.getTag()));
        imageView.setImageResource(R.drawable.ic_marker_background); // Set a placeholder image
        return imageView;
    }

    private boolean checkCameraPermission() {
        return getActivity().checkSelfPermission(android.Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED;
    }

    private void closeCamera()
    {
        if (mCameraDevice != null) {
            mCameraDevice.close();
            mCameraDevice = null;
        }
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
        if (mCameraDevice == null)
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
                            // 当摄像头已经准备好时，开始显示预览

                            try {
                                // 自动对焦
                                previewRequestBuilder.set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE);
                                // 打开闪光灯
                                previewRequestBuilder.set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON_AUTO_FLASH);
                                // 显示预览
                                if (mPreviewRequest == null)
                                    mPreviewRequest = previewRequestBuilder.build();

                                mCaptureSession.setRepeatingRequest(mPreviewRequest, null, null);
                            } catch (CameraAccessException e) {
                                e.printStackTrace();
                            }
                            // TODO: 设置预览参数
                        }

                        @Override
                        public void onConfigureFailed(@NonNull CameraCaptureSession session) {
                            Log.e(TAG, "Failed to configure camera capture session");
                        }
                    }, null);

        } catch (CameraAccessException e) {
            Log.e(TAG, "Error starting camera preview", e);
        }
    }

    // 拍照
    private void takePicture() {
        if (mCameraDevice == null) {
            Log.e(TAG, "CameraDevice is null. Cannot take picture.");
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
                    Log.e(TAG, "Error saving image", e);
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
            Log.e(TAG, "Error taking picture", e);
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

    private void saveImage(byte[] bytes, File file, boolean isPortrait) throws IOException {
        // 创建用于解码图片的 Options 对象
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inJustDecodeBounds = true; // 只解码尺寸信息
        BitmapFactory.decodeByteArray(bytes, 0, bytes.length, options);

        // 计算采样率
        int reqWidth = 1080;  // 目标宽度，可以根据需求调整
        int reqHeight = 1920; // 目标高度，可以根据需求调整
        options.inSampleSize = calculateInSampleSize(options, reqWidth, reqHeight);

        // 关闭只解码尺寸信息的选项，以便加载完整图片到内存中
        options.inJustDecodeBounds = false;

        // 使用压缩后的 Options 对象解码原图
        Bitmap bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.length, options);

        // 如果需要旋转图片
        if (isPortrait) {
            Bitmap rotatedBitmap;
            Matrix matrix = new Matrix();
            matrix.postRotate(90);
            rotatedBitmap = Bitmap.createBitmap(bitmap, 0, 0, bitmap.getWidth(), bitmap.getHeight(), matrix, true);
            bitmap = rotatedBitmap;
        }

        if (bitmap != null) {
            // 将压缩后的图片写入文件
            try (FileOutputStream output = new FileOutputStream(file)) {
                bitmap.compress(Bitmap.CompressFormat.JPEG, 80, output); // 设置压缩质量为 90%
            }
        }
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

    // 添加缩略图到列表
    private void addThumbnail(final File imageFile) {
        // Find first empty spot
        for (int i = 0; i < MAX_PHOTOS; i++) {
            if (mImageFiles.get(i) == null) {
                mImageFiles.set(i, imageFile);
                mImageViews.get(i).setImageBitmap(BitmapUtils.decodeSampledBitmapFromFile(
                        imageFile.getAbsolutePath(),
                        getResources().getDimensionPixelSize(R.dimen.thumbnail_width),
                        getResources().getDimensionPixelSize(R.dimen.thumbnail_height)
                ));

                break;
            }
        }
    }

    // Display the full image
    private void showFullImage(int index) {

        File imageFile = mImageFiles.get(index);
        if (imageFile == null) {
            Log.e(TAG, "Image file is null");
            return;
        }

        FullImageFragment fullImageFragment = new FullImageFragment();
        Bundle bundle = new Bundle();
        bundle.putString("imageFile", imageFile.getAbsolutePath());
        bundle.putInt("imageIndex", index);
        fullImageFragment.setArguments(bundle);


        requireActivity().getSupportFragmentManager().beginTransaction()
                .add(android.R.id.content, fullImageFragment)
                .addToBackStack(null)
                .commit();

    }

    public void removeThumbnail(int index) {
        mImageFiles.set(index, null);
        mImageViews.get(index).setImageResource(R.drawable.ic_marker_background); // Set a placeholder image
    }
}
