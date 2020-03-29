package com.ttsea.jcamera.core;

interface IMaskView {

    /**
     * 设置{@link ICamera}
     *
     * @param iCamera
     */
    void setICamera(ICamera iCamera);

    /**
     * 停止并清除View的动画效果
     */
    void clearAnimation();

    /**
     * 获取区域聚焦半径，可以在xml中进行如下配置<br>
     * 配置属性：app:focusAreaRadius="68dp"
     *
     * @return
     */
    float getFocusAreaRadius();

    /**
     * 获取view的宽度
     *
     * @return
     */
    int getViewWidth();

    /**
     * 获取view的高度
     *
     * @return
     */
    int getViewHeight();
}
