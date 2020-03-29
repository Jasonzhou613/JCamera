package com.ttsea.jcamera.core;

import android.content.Context;
import android.view.Display;
import android.view.OrientationEventListener;
import android.view.Surface;

abstract class OrientationDetector extends OrientationEventListener {
    private Display mDisplay;
    private int lastRotation;

    public OrientationDetector(Context context) {
        super(context);
    }

    @Override
    public void onOrientationChanged(int orientation) {
        if (orientation == OrientationEventListener.ORIENTATION_UNKNOWN || mDisplay == null) {
            return;
        }

        int rotation = mDisplay.getRotation();
        if (lastRotation == rotation) {
            return;
        }

        lastRotation = rotation;
        dispatchOrientationChanged(rotation);
    }

    /**
     * see {@link OrientationEventListener#enable()}
     *
     * @param display
     */
    public void enable(Display display) {
        mDisplay = display;
        super.enable();
    }

    @Override
    public void disable() {
        super.disable();
        mDisplay = null;
    }

    /**
     * 屏幕旋转后，会回调这个方法
     *
     * @param rotation 旋转方向
     *                 {@link Surface#ROTATION_0}
     *                 {@link Surface#ROTATION_90}
     *                 {@link Surface#ROTATION_180}
     *                 {@link Surface#ROTATION_270}
     */
    abstract void dispatchOrientationChanged(int rotation);
}
