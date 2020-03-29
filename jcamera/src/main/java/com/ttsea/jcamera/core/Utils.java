package com.ttsea.jcamera.core;

import android.app.Activity;
import android.content.Context;
import android.content.pm.PackageManager;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraManager;
import android.os.Build;
import android.util.DisplayMetrics;
import android.view.Display;
import android.view.View;
import android.view.WindowManager;

import androidx.annotation.NonNull;

final class Utils {

    /** Check if this device has a camera */
    public static boolean checkCameraHardware(Context context) {
        if (context.getPackageManager().hasSystemFeature(PackageManager.FEATURE_CAMERA)) {
            // this device has a camera
            return true;
        } else {
            // no camera on this device
            return false;
        }
    }

    /**
     * 将px值转换为dip或dp值，保证尺寸大小不变
     *
     * @param context 上下文
     * @param pxValue px
     * @return int dip
     */
    public static int px2dip(Context context, float pxValue) {
        final float scale = context.getResources().getDisplayMetrics().density;
        return (int) (pxValue / scale + 0.5f);
    }

    /**
     * 将dip或dp值转换为px值，保证尺寸大小不变
     *
     * @param context  上下文
     * @param dipValue dip
     * @return int px
     */
    public static int dip2px(Context context, float dipValue) {
        final float scale = context.getResources().getDisplayMetrics().density;
        return (int) (dipValue * scale + 0.5f);
    }


    /**
     * 返回屏幕宽度(px)
     *
     * @param context 上下文
     * @return 返回屏幕宽度(px)
     */
    public static int getWindowWidth(Context context) {
        DisplayMetrics dm = new DisplayMetrics();
        ((Activity) context).getWindowManager().getDefaultDisplay()
                .getMetrics(dm);
        return dm.widthPixels;
    }

    /**
     * 返回屏幕高度(px)
     *
     * @param context 上下文
     * @return 返回屏幕高度(px)
     */
    public static int getWindowHeight(Context context) {
        DisplayMetrics dm = new DisplayMetrics();
        ((Activity) context).getWindowManager().getDefaultDisplay()
                .getMetrics(dm);
        return dm.heightPixels;
    }

    /** 判断str是否为空 */
    public static boolean isEmpty(String str) {
        if (str == null || str.length() < 1) {
            return true;
        }
        return false;
    }

    /**
     * 获取Display
     *
     * @param view 当前view
     * @return
     */
    public static Display getDisplay(@NonNull View view) {
        if (Build.VERSION.SDK_INT >= 17) {
            return view.getDisplay();
        }

        final WindowManager wm = (WindowManager) view.getContext().getSystemService(
                Context.WINDOW_SERVICE);
        return wm.getDefaultDisplay();
    }

    /**
     * 判断设备是否支持Camera2 api并且Camera支持高版本特性<br>
     * 比如：5.0，Camera2在一些低端机上会出现预览界面拉伸的问题<br>
     * 所以硬件兼容级别为legacy时，暂时先放弃使用camera2 api
     *
     * @return 是否支持高版本api
     */
    public static boolean cameraSupportHighLevel(Context context) {
        if (Build.VERSION.SDK_INT < 21) {
            return false;
        }

        try {
            CameraManager manager = (CameraManager) context.getSystemService(Context.CAMERA_SERVICE);
            String[] ids = manager.getCameraIdList();

            if (ids == null || ids.length <= 0) {
                return false;
            }

            for (String id : ids) {
                CameraCharacteristics characteristics = manager.getCameraCharacteristics(id);
                Integer level = characteristics.get(CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL);

                if (level == null) {
                    continue;
                }

                if (level != CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_LEGACY) {
                    return true;
                }
            }

        } catch (Exception e) {
            String errorMsg = "Exception e:" + e.getMessage();
            CameraxLog.w(errorMsg);
        }

        return false;
    }
}
