package com.ttsea.jcamera.core;

import android.view.Surface;

import com.ttsea.jcamera.annotation.Facing;
import com.ttsea.jcamera.annotation.Flash;
import com.ttsea.jcamera.callbacks.CameraCallback;

import java.io.File;
import java.util.List;
import java.util.Set;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

interface ICamera {

    /**
     * 设置摄像头回调
     *
     * @param callback
     */
    void setCameraCallback(CameraCallback callback);

    /**
     * SurfaceView创建的时候回调这个方法<br>
     * 不需要主动去调用这个方法
     */
    void onSurfaceCreated();

    /**
     * 打开摄像头，默认打开{@link Constants#FACING_BACK}（后置）摄像头<br>
     * 建议在 Activity.onResume() 中开启摄像头，在 Activity.onPause() 中释放摄像头
     */
    void openCamera();

    /**
     * 打开指定方向摄像头<br>
     *
     * @param facing see {@link Constants#FACING_BACK}、{@link Constants#FACING_FRONT}
     */
    void openCamera(@Facing int facing);

    /**
     * 释放摄像头<br>
     * 建议在 Activity.onResume() 中开启摄像头，在 Activity.onPause() 中释放摄像头
     */
    void releaseCamera();

    /**
     * 开启预览
     */
    void startPreview();

    /**
     * 停止预览
     */
    void stopPreview();

    /**
     * 获取当前开的摄像头
     *
     * @return {@link Constants#FACING_BACK} or {@link Constants#FACING_FRONT}
     */
    @Facing
    int getFacing();

    /**
     * 是否正在预览
     *
     * @return
     */
    boolean isShowingPreview();

    /**
     * 获取相机支持的比例（宽:高）<br>
     * 这里返回的结果是预览比例和图片比例的交集
     * previewSize
     */
    Set<AspectRatio> getSupportedAspectRatios();

    /**
     * 设置比例，如果相机不支持要设置的比例，则会返回false
     *
     * @param ratio 比例
     * @return true:表示设置成功，false:表示设置失败
     */
    boolean setAspectRatio(AspectRatio ratio);

    /**
     * 获取当前比例
     *
     * @return
     */
    AspectRatio getAspectRatio();

    /**
     * 屏幕旋转后，需要调用该方法
     *
     * @param rotation 旋转方向
     *                 {@link Surface#ROTATION_0}
     *                 {@link Surface#ROTATION_90}
     *                 {@link Surface#ROTATION_180}
     *                 {@link Surface#ROTATION_270}
     */
    void onActivityRotation(int rotation);

    /**
     * 设置当前相机所支持的聚焦模式
     *
     * @return list，see {@link #setFlashMode(int)}
     */
    List<Integer> getSupportedFlashModes();

    /**
     * 获取相机当前的聚焦模式
     *
     * @return
     */
    @Flash
    int getFlashMode();

    /**
     * 设置当前相机的聚焦模式<br>
     * 如果当前打开的摄像头不支持要设置闪光模式，则会返回false
     *
     * @param flash see {@link Constants#FLASH_OFF}、{@link Constants#FLASH_ON}、
     *              {@link Constants#FLASH_TORCH}、{@link Constants#FLASH_AUTO}、
     *              {@link Constants#FLASH_RED_EYE}
     * @return true:表示设置成功，false:表示设置失败
     */
    boolean setFlashMode(@Flash int flash);

    /**
     * 获取相机数量
     *
     * @return
     */
    int getNumberOfCameras();

    /**
     * 拍张照
     *
     * @param outputFile 录像输出文件，可以为空<br>
     *                   为空的时候，系统会自动以当前手机时间作为文件名
     */
    void takePhoto(@Nullable File outputFile);

    /**
     * 开始录像
     *
     * @param outputFile 录像输出文件，不可为空
     */
    void startRecord(@NonNull File outputFile);

    /**
     * 停止录像
     */
    void stopRecord();

    /**
     * 用户点击SurfaceView的时候回调这个方法<br>
     * 1.如果摄像头不支持区域聚焦，则直接返回false<br>
     * 2.如果摄像头支持区域聚焦，则需要一步来判断是否有必要触发区域聚焦
     *
     * @param x 用户点击的x坐标
     * @param y 用户点击的y坐标
     * @return 是否触发了区域聚焦
     */
    boolean onSurfaceTapped(float x, float y);

    /**
     * 放大<br>
     * 达到了放大最大值的时候可能不会再执行放大了
     *
     * @param value <0 的时候表示双击放大，自己进行计算放大值
     * @return true:执行了放大动作，false:未执行放大动作
     */
    boolean zoomIn(float value);

    /**
     * 缩小<br>
     * 达到了缩小最小值的时候可能不会再执行缩小了
     *
     * @param value <0 的时候表示双击缩小，自己进行计算放大值
     * @return true:执行了缩小动作，false:未执行缩小动作
     */
    boolean zoomOut(float value);

    void setOneShotPreview();
}
