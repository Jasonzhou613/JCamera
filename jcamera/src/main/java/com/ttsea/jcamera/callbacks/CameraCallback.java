package com.ttsea.jcamera.callbacks;

import java.io.File;

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
     * 这个时候可以给摄像头设置一些参数<br>
     * 在主线程中回调
     */
    void onCameraOpened();

    /**
     * 摄像头被关闭<br>
     * 在主线程中回调
     */
    void onCameraClosed();

    /**
     * 当摄像头出错的时候会回调这个方法<br>
     * 在主线程中回调
     *
     * @param errorCode 错误码
     * @param msg       错误信息
     */
    void onCameraError(int errorCode, String msg);

    /**
     * 启动预览的时候会回调这个方法<br>
     * 在主线程中回调
     */
    void onStartPreview();

    /**
     * 停止预览的时候会回调这个方法<br>
     * 在主线程中回调
     */
    void onStopPreview();

    /**
     * 拍照回调<br>
     * 在主线程中回调
     *
     * @param picFile  返回照片的路径，为空的时候表示出错了
     * @param errorMsg 出错的时候所带的错误信息
     */
    void onPictureTaken(@Nullable File picFile, String errorMsg);

    /**
     * 录像异常<br>
     * 在主线程中回调
     *
     * @param errorMsg 错误信息
     */
    void onRecordError(String errorMsg);

    /**
     * 捕捉一帧数据，这里返回的数据是已经处理好旋转角度的byte[]数据<br>
     * 将会在子程中回调
     *
     * @param data   返回的byte数据
     * @param format data数据类型
     * @param width  data宽度
     * @param height data高度
     */
    void oneShotFrameData(@Nullable byte[] data, int format, int width, int height);

    /**
     * 捕捉每一帧数据，这里返回的数据是已经处理好旋转角度的byte[]数据<br>
     * 将会在子程中回调
     *
     * @param data   返回的byte数据
     * @param format data数据类型
     * @param width  data宽度
     * @param height data高度
     */
    void everyFrameData(@Nullable byte[] data, int format, int width, int height);
}
