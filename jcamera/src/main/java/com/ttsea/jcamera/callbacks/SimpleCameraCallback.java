package com.ttsea.jcamera.callbacks;

import java.io.File;

import androidx.annotation.Nullable;

public class SimpleCameraCallback implements CameraCallback {

    @Override
    public void onCameraOpened() {

    }

    @Override
    public void onCameraClosed() {

    }

    @Override
    public void onCameraError(int errorCode, String msg) {

    }

    @Override
    public void onStartPreview() {

    }

    @Override
    public void onStopPreview() {

    }

    @Override
    public void onPictureTaken(@Nullable File file, String errorMsg) {

    }

    @Override
    public void oneShotFrameData(@Nullable byte[] data, int format, int width, int height) {

    }

    @Override
    public void everyFrameData(@Nullable byte[] data, int format, int width, int height) {

    }
}
