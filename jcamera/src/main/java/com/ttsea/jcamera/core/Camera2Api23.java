package com.ttsea.jcamera.core;

import android.annotation.TargetApi;
import android.content.Context;
import android.graphics.ImageFormat;
import android.hardware.camera2.params.StreamConfigurationMap;

@TargetApi(23)
class Camera2Api23 extends Camera2 {

    public Camera2Api23(Context context, ISurface iSurface, IMaskView iMaskView) {
        super(context, iSurface, iMaskView);
    }

    protected void collectPictureSizes(SizeMap sizes, StreamConfigurationMap map) {
        // Try to get hi-res output sizes
        android.util.Size[] outputSizes = map.getHighResolutionOutputSizes(ImageFormat.JPEG);
        if (outputSizes != null) {
            for (android.util.Size size : map.getHighResolutionOutputSizes(ImageFormat.JPEG)) {
                sizes.addSize(new Size(size.getWidth(), size.getHeight()));
            }
        }
        if (sizes.isEmpty()) {
            // super.collectPictureSizes(sizes, map);
        }
    }
}