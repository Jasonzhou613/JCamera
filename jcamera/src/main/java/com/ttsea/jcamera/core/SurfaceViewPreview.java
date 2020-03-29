package com.ttsea.jcamera.core;

import android.content.Context;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.SurfaceView;

class SurfaceViewPreview extends SurfaceView implements SurfaceHolder.Callback, ISurface {
    private ICamera iCamera;
    private SurfaceHolder mHolder;

    public SurfaceViewPreview(Context context) {
        super(context);

        init();
    }

    private void init() {
        getHolder().addCallback(this);
        // deprecated setting, but required on Android versions prior to 3.0
        getHolder().setType(SurfaceHolder.SURFACE_TYPE_PUSH_BUFFERS);
    }

    @Override
    public void surfaceCreated(SurfaceHolder holder) {
        mHolder = holder;
        if (iCamera != null) {
            iCamera.onSurfaceCreated();
        }
    }

    @Override
    public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
        mHolder = holder;
        CameraxLog.d("surfaceChanged, format:" + format + ", width:" + width + ", height:" + height);
    }

    @Override
    public void surfaceDestroyed(SurfaceHolder holder) {
        mHolder = null;
    }

    @Override
    public void setICamera(ICamera iCamera) {
        this.iCamera = iCamera;
    }

    @Override
    public boolean isReady() {
        return mHolder != null;
    }

    @Override
    public Surface getSurface() {
        if (mHolder == null) {
            return null;
        }
        return mHolder.getSurface();
    }
}
