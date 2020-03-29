package com.ttsea.jcamera.callbacks;

import android.hardware.Camera;

import androidx.annotation.Nullable;

/**
 * 摄像头回到<br>
 * 摄像头被打开/关闭/出错/拍照/拍视频都会有回调
 */
public interface CameraCallback {
    /** 该设备没有摄像头 */
    int CODE_NO_CAMERA = 1;
    /** 打开摄像头失败 */
    int CODE_OPEN_FAILED = 2;
    /** 开启预览失败 */
    int CODE_START_PREVIEW_FAILED = 3;
    /** 适配尺寸的时候出错 */
    int CODE_CONFIG_SIZE_FAILED = 4;

    /**
     * 摄像头被打开，
     * 这个时候可以给摄像头设置一些参数
     */
    void onCameraOpened();

    /**
     * 摄像头被关闭
     */
    void onCameraClosed();

    /**
     * 当摄像头出错的时候会回调这个方法
     *
     * @param errorCode 错误码
     * @param msg       错误信息
     */
    void onCameraError(int errorCode, String msg);

    /**
     * 拍照回调
     *
     * @param data 返回的数据是{@link Camera.PictureCallback}中对应的数据
     */
    void onPictureTaken(@Nullable byte[] data);
}
