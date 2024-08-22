package com.hf.easydelivery.view;

import android.content.Context;
import android.hardware.camera2.CameraManager;
import android.util.AttributeSet;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.SurfaceView;

public class CameraPreview extends SurfaceView implements SurfaceHolder.Callback {

    private CameraManager mCameraManager;

    public void setCameraActivity(CameraFragment mCameraFragment) {
        this.mCameraFragment = mCameraFragment;
    }

    private CameraFragment mCameraFragment;

    public CameraPreview(Context context ) {
        this(context, null);
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

        System.out.println("ok");

        if (mCameraManager != null)
            mCameraFragment.startPreview();
    }

    @Override
    public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {

    }

    @Override
    public void surfaceDestroyed(SurfaceHolder holder) {

        if (mCameraManager != null) {
            mCameraManager = null;
        }
    }

    public void setCameraManager(CameraManager cameraManager) {
        mCameraManager = cameraManager;
    }

    public Surface getSurface() {
        return getHolder().getSurface();
    }
}
