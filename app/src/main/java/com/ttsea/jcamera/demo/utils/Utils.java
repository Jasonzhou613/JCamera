package com.ttsea.jcamera.demo.utils;

import android.content.Context;

public final class Utils {

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


    /** 判断str是否为空 */
    public static boolean isEmpty(String str) {
        if (str == null || str.length() < 1) {
            return true;
        }
        return false;
    }
}
