package com.ttsea.jcamera.core;

import android.content.Context;
import android.content.pm.PackageManager;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraManager;
import android.os.Build;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

import androidx.annotation.NonNull;

final class Utils {

    /** 判断str是否为空 */
    public static boolean isEmpty(String str) {
        if (str == null || str.length() < 1) {
            return true;
        }
        return false;
    }

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
            JCameraLog.w(errorMsg);
        }

        return false;
    }

    /**
     * 得到当前时间
     *
     * @param pattern 时间格式，形如：yyyy-MM-dd 或者yyyy-MM-dd HH:mm:ss
     * @return String
     */
    public static String getCurrentTime(String pattern) {
        SimpleDateFormat formatter = new SimpleDateFormat(pattern, Locale.getDefault());
        Date curDate = new Date(System.currentTimeMillis());// 获取当前时间
        return formatter.format(curDate);
    }

    /**
     * 获取手机屏幕比例
     *
     * @param context 上下文
     * @return 长宽或者宽长比例，值大的在前面
     */
    public static AspectRatio getScreenRatio(@NonNull Context context) {
        int w = DisplayUtils.getWindowWidth(context);
        int h = DisplayUtils.getWindowHeight(context);

        if (w == 0 || h == 0) {
            w = 3;
            h = 4;

        } else {
            if (DisplayUtils.isLandscape(context)) {
                w = w + DisplayUtils.getNavigationBarHeight(context);
            } else {
                h = h + DisplayUtils.getNavigationBarHeight(context);
            }
        }

        if (w > h) {
            return AspectRatio.of(w, h);
        }

        return AspectRatio.of(h, w);
    }
}
