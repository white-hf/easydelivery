package com.uniuni.SysMgrTool.View;

import android.content.Context;
import android.hardware.camera2.CameraManager;
import android.util.AttributeSet;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.SurfaceView;

import java.io.IOException;

public class CameraPreview extends SurfaceView implements SurfaceHolder.Callback {

    private CameraManager mCameraManager;
    private CameraActivity mCameraActivity;

    public CameraPreview(Context context) {
        this(context, null);
        mCameraActivity = (CameraActivity)context;

    }

    public CameraPreview(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public CameraPreview(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        getHolder().addCallback(this);
    }

    @Override
    public void surfaceCreated(SurfaceHolder holder) {
        // Surface 已创建，通知 CameraManager
        System.out.println("ok");

        if (mCameraManager != null)
            mCameraActivity.startPreview();
    }

    @Override
    public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
        // Surface 尺寸或格式发生变化，需要重新设置预览参数
    }

    @Override
    public void surfaceDestroyed(SurfaceHolder holder) {
        // Surface 将要被销毁，释放相关资源
        if (mCameraManager != null) {
            mCameraManager = null;
        }
    }

    // 设置相机管理器
    public void setCameraManager(CameraManager cameraManager) {
        mCameraManager = cameraManager;
    }

    // 获取 Surface 对象
    public Surface getSurface() {
        return getHolder().getSurface();
    }
}
