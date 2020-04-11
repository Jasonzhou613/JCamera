package com.ttsea.jcamera.core;

import android.content.Context;
import android.content.res.Resources;
import android.graphics.Point;
import android.os.Build;
import android.provider.Settings;
import android.text.TextUtils;
import android.util.DisplayMetrics;
import android.view.Display;
import android.view.KeyCharacterMap;
import android.view.KeyEvent;
import android.view.Surface;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.WindowManager;

import androidx.annotation.NonNull;

/**
 * dp、sp、px互相转、获取状态栏高度、或者虚拟按键高度<br>
 * <p>
 * <b>more:</b>更多请点 <a href="http://www.ttsea.com" target="_blank">这里</a> <br>
 * <b>date:</b> 2017/4/10 9:55 <br>
 * <b>author:</b> Jason <br>
 * <b>version:</b> 1.0 <br>
 */
public final class DisplayUtils {

    /**
     * 获取Display
     *
     * @param view 当前view
     * @return {@link android.view.Display}
     */
    public static Display getDisplay(@NonNull View view) {
        if (Build.VERSION.SDK_INT >= 17) {
            Display display = view.getDisplay();
            if (display != null) {
                return display;
            }
        }

        return getDisplay(view.getContext());
    }

    /**
     * 获取Display
     *
     * @param context 上下文
     * @return {@link android.view.Display}
     */
    public static Display getDisplay(Context context) {
        WindowManager wm = (WindowManager) context.getSystemService(Context.WINDOW_SERVICE);
        return wm.getDefaultDisplay();
    }

    /**
     * 获取旋转角度
     *
     * @param context 上下文
     * @return see <br>
     * {@link Surface#ROTATION_0}<br>
     * {@link Surface#ROTATION_90}<br>
     * {@link Surface#ROTATION_180}<br>
     * {@link Surface#ROTATION_270}<br>
     */
    public static int getRotation(@NonNull Context context) {
        Display display = getDisplay(context);
        if (display != null) {
            return display.getRotation();
        }

        return Surface.ROTATION_0;
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
     * 将px值转换为sp值，保证文字大小不变
     *
     * @param context 上下文
     * @param pxValue px
     * @return int sp
     */
    public static int px2sp(Context context, float pxValue) {
        final float fontScale = context.getResources().getDisplayMetrics().scaledDensity;
        return (int) (pxValue / fontScale + 0.5f);
    }

    /**
     * 将sp值转换为px值，保证文字大小不变
     *
     * @param context 上下文
     * @param spValue sp
     * @return int px
     */
    public static int sp2px(Context context, float spValue) {
        final float fontScale = context.getResources().getDisplayMetrics().scaledDensity;
        return (int) (spValue * fontScale + 0.5f);
    }

    /**
     * 返回屏幕宽度(px)
     *
     * @param context 上下文
     * @return 返回屏幕宽度(px)
     */
    public static int getWindowWidth(Context context) {
        DisplayMetrics dm = new DisplayMetrics();
        getDisplay(context).getMetrics(dm);
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
        getDisplay(context).getMetrics(dm);
        return dm.heightPixels;
    }

    /**
     * 获取虚拟按键的高度
     * 1. 全面屏下
     * 1.1 开启全面屏开关-返回0
     * 1.2 关闭全面屏开关-执行非全面屏下处理方式
     * 2. 非全面屏下
     * 2.1 没有虚拟键-返回0
     * 2.1 虚拟键隐藏-返回0
     * 2.2 虚拟键存在且未隐藏-返回虚拟键实际高度
     */
    public static int getNavigationBarHeight(Context context) {
        if (isOverallScreen(context)) {
            return 0;
        }
        return getNavigationHeight(context);
    }

    /**
     * 获取status bar高度
     *
     * @param context 上希望
     * @return status bar高度或者0
     */
    public static int getStatusBarHeight(Context context) {
        try {
            Resources res = context.getResources();
            int statusHeight = res.getIdentifier("status_bar_height", "dimen", "android");
            return res.getDimensionPixelSize(statusHeight);

        } catch (Exception e) {
            JCameraLog.w("Exception, e:" + e.getMessage());
        }
        return 0;
    }

    /**
     * 获取system bar高度
     *
     * @param context 上希望
     * @return system bar高度或者0
     */
    public static int getSystemBarHeight(Context context) {
        try {
            Resources res = context.getResources();
            int systemHeight = res.getIdentifier("system_bar_height", "dimen",
                    "android");
            return res.getDimensionPixelSize(systemHeight);

        } catch (Exception e) {
            JCameraLog.w("Exception, e:" + e.getMessage());
        }
        return 0;
    }

    /**
     * 全面屏（是否开启全面屏开关 0 关闭  1 开启）
     *
     * @param context
     * @return
     */
    public static boolean isOverallScreen(Context context) {
        int val = 0;
        if (Build.VERSION.SDK_INT >= 17) {
            val = Settings.Global.getInt(context.getContentResolver(), getOverallScreenKey(), 0);
        }
        return val != 0;
    }

    /**
     * 判断手机是否处于横屏状态
     *
     * @param context
     * @return true:处于横屏，false:处于竖屏
     */
    public static boolean isLandscape(Context context) {
        int rotation = getRotation(context);

        if (rotation == Surface.ROTATION_90 || rotation == Surface.ROTATION_270) {
            return true;
        }

        return false;
    }

    /**
     * 获取Navigation高度
     *
     * @param context 上希望
     * @return Navigation高度或者0
     */
    private static int getNavigationHeight(Context context) {
        //Navigation未显示
        if (!isNavigationBarShow(context)) {
            return 0;
        }

        String name = "navigation_bar_height";
        //横屏的时候值不一样
        if (isLandscape(context)) {
            name = "navigation_bar_height_landscape";
        }

        try {
            Resources res = context.getResources();
            int navigationHeight = res.getIdentifier(name, "dimen", "android");

            return res.getDimensionPixelSize(navigationHeight);

        } catch (Exception e) {
            JCameraLog.w("Exception, e:" + e.getMessage());
        }

        return 0;
    }

    /**
     * 判断虚拟按钮是否显示
     *
     * @param context 上下文
     * @return true:显示，false:未显示
     */
    private static boolean isNavigationBarShow(Context context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1) {
            Display display = getDisplay(context);
            Point size = new Point();
            Point realSize = new Point();
            display.getSize(size);
            display.getRealSize(realSize);

            if (isLandscape(context)) {
                return realSize.x != size.x;
            } else {
                return realSize.y != size.y;
            }

        } else {
            boolean menu = ViewConfiguration.get(context).hasPermanentMenuKey();
            boolean back = KeyCharacterMap.deviceHasKey(KeyEvent.KEYCODE_BACK);
            if (menu || back) {
                return false;
            } else {
                return true;
            }
        }
    }

    /**
     * 获取设备记录是否开启了全屏的key<br>
     * （目前支持几大主流的全面屏手机，亲测华为、小米、oppo、魅族、vivo都可以）
     *
     * @return String
     */
    private static String getOverallScreenKey() {
        String brand = Build.BRAND;
        if (TextUtils.isEmpty(brand)) {
            return "navigationbar_is_min";
        }
        if (brand.equalsIgnoreCase("HUAWEI")) {
            return "navigationbar_is_min";

        } else if (brand.equalsIgnoreCase("XIAOMI")) {
            return "force_fsg_nav_bar";

        } else if (brand.equalsIgnoreCase("VIVO")) {
            return "navigation_gesture_on";

        } else if (brand.equalsIgnoreCase("OPPO")) {
            return "navigation_gesture_on";

        } else {
            return "navigationbar_is_min";
        }
    }
}
