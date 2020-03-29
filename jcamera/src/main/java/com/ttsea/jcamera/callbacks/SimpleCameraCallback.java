package com.ttsea.jcamera.callbacks;

import androidx.annotation.Nullable;

public abstract class SimpleCameraCallback implements CameraCallback {

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
    public void onPictureTaken(@Nullable byte[] data) {

    }
}
