package com.ttsea.jcamera.core;

import android.view.Surface;

interface ISurface {

    /**
     * 设置{@link ICamera}
     *
     * @param iCamera
     */
    void setICamera(ICamera iCamera);

    /**
     * SurfaceView是否已经准备好
     *
     * @return true:表示准备好了，false:表示未准备好
     */
    boolean isReady();

    /**
     * 获取对应的Surface
     *
     * @return
     */
    Surface getSurface();
}
