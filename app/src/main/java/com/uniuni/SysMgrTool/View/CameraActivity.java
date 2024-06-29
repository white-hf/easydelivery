package com.uniuni.SysMgrTool.View;

import android.Manifest;
import android.content.pm.PackageManager;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import com.uniuni.SysMgrTool.R;
import com.uniuni.SysMgrTool.common.BitmapUtils;

import android.content.Context;
import android.content.pm.PackageManager;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.os.Bundle;
import android.util.Log;
import android.view.Surface;
import android.view.View;
import android.widget.Button;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;

import java.util.Arrays;

import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CaptureRequest;
import android.media.Image;
import android.media.ImageReader;
import android.os.Bundle;
import android.os.Environment;
import android.util.Log;
import android.view.Surface;
import android.view.View;
import android.widget.Button;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.Toast;


import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class CameraActivity extends AppCompatActivity implements SensorEventListener {

    private static final String TAG = "CameraActivity";
    private static final int CAMERA_PERMISSION_REQUEST_CODE = 1001;

    private CameraManager mCameraManager;
    private CameraDevice mCameraDevice = null;
    private CameraCaptureSession mCaptureSession;
    private CameraPreview mCameraPreview;

    private ImageReader imageReader;

    private LinearLayout mThumbnailContainer;
    private List<File> mImageFiles;

    private CaptureRequest mPreviewRequest;

    private SensorManager sensorManager;
    private Sensor accelerometer;
    private Sensor magnetometer;

    private float[] accelerometerReading = new float[3];
    private float[] magnetometerReading = new float[3];

    private boolean isPortrait = false; // 默认是横屏

    private Long mOrderId;

    @Override
    protected void onStart() {

        super.onStart();

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
    protected void onPause() {
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
        // 初始化相机
        mCameraManager = (CameraManager) getSystemService(Context.CAMERA_SERVICE);
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
        ActivityCompat.requestPermissions(this,
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
                Toast.makeText(this, "Camera permission is required to use this feature", Toast.LENGTH_SHORT).show();
            }
        }
    }


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_camera);

        mOrderId = getIntent().getLongExtra("order_id", -1);

        initCamera();

        // initialize the thumbnail container
        mThumbnailContainer = findViewById(R.id.thumbnail_container);
        mImageFiles = new ArrayList<>();

        //
        Button cancelButton = findViewById(R.id.cancel_button);
        cancelButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                finish();
            }
        });

        Button captureButton = findViewById(R.id.capture_button);
        captureButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                takePicture();
            }
        });


        FrameLayout previewLayout = findViewById(R.id.camera_preview);
        mCameraPreview = new CameraPreview(this);
        previewLayout.addView(mCameraPreview);

        sensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
        if (sensorManager != null) {
            accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
            magnetometer = sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD);
        } else {
            Toast.makeText(this, "Sensor not available", Toast.LENGTH_SHORT).show();
            finish();
        }

    }

    private boolean checkCameraPermission() {
        return checkSelfPermission(android.Manifest.permission.CAMERA) ==
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
            // 创建预览需要的CaptureRequest.Builder
            final CaptureRequest.Builder previewRequestBuilder = mCameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
            // 将SurfaceView的surface作为CaptureRequest.Builder的目标
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
                    saveImage(bytes, imageFile);

                    // 将缩略图显示在列表中
                    addThumbnail(imageFile);

                    mCaptureSession.setRepeatingRequest(mPreviewRequest, null, null);

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
        File storageDir = getExternalFilesDir(Environment.DIRECTORY_PICTURES);
        File imageFile = File.createTempFile(
                imageFileName,
                ".jpg",
                storageDir
        );
        mImageFiles.add(imageFile);
        return imageFile;
    }

    // 保存图片到文件
    private void saveImage(byte[] bytes, File file) throws IOException {
        // 创建用于压缩图片的 Options 对象
        BitmapFactory.Options options = new BitmapFactory.Options();
        // 设置为 true 表示只解码图片的尺寸信息，而不加载图片内容到内存中
        options.inJustDecodeBounds = true;
        // 计算采样率，这里简单设置为原图尺寸的 1/4
        options.inSampleSize = 4;
        // 解码图片尺寸信息
        BitmapFactory.decodeFile(file.getAbsolutePath(), options);

        // 关闭只解码尺寸信息的选项，以便加载完整图片到内存中
        options.inJustDecodeBounds = false;
        // 设置压缩质量为 80%，可以根据需求进行调整

        // 使用压缩后的 Options 对象解码原图
        Bitmap bitmap;
        bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.length , options);

        if (isPortrait)
        {
            Bitmap bp;
            Matrix matrix = new Matrix();
            matrix.postRotate(90);
            bp = Bitmap.createBitmap(bitmap, 0, 0, bitmap.getWidth(), bitmap.getHeight(), matrix, true);
            bitmap = bp;
        }

        if (bitmap != null) {
            // 将压缩后的图片写入文件
            try (FileOutputStream output = new FileOutputStream(file)) {
                bitmap.compress(Bitmap.CompressFormat.JPEG, 80, output);
            }
        }
    }

    // 添加缩略图到列表
    private void addThumbnail(final File imageFile) {
        ImageView imageView = new ImageView(this);
        imageView.setLayoutParams(new LinearLayout.LayoutParams(
                getResources().getDimensionPixelSize(R.dimen.thumbnail_width),
                getResources().getDimensionPixelSize(R.dimen.thumbnail_height)
        ));
        imageView.setImageBitmap(BitmapUtils.decodeSampledBitmapFromFile(
                imageFile.getAbsolutePath(),
                getResources().getDimensionPixelSize(R.dimen.thumbnail_width),
                getResources().getDimensionPixelSize(R.dimen.thumbnail_height)
        ));
        imageView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                // 点击缩略图查看大图
                showFullImage(imageFile);
            }
        });
        mThumbnailContainer.addView(imageView);
    }

    // 显示大图
    private void showFullImage(File imageFile) {
        // 创建 FullImageFragment 实例并传递图片文件路径
        FullImageFragment fullImageFragment = new FullImageFragment();
        Bundle bundle = new Bundle();
        bundle.putString("imageFile", imageFile.getAbsolutePath());
        fullImageFragment.setArguments(bundle);

        // 使用 FragmentTransaction 启动 FullImageFragment
        getSupportFragmentManager().beginTransaction()
                .add(android.R.id.content, fullImageFragment)
                .addToBackStack(null)
                .commit();

    }
}
