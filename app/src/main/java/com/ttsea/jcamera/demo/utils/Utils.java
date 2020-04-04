package com.ttsea.jcamera.demo.utils;

import android.app.Activity;
import android.content.Context;
import android.content.res.AssetFileDescriptor;
import android.media.AudioManager;
import android.media.MediaPlayer;

import com.ttsea.jcamera.demo.debug.JLog;

import java.io.IOException;

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

    /**
     * 播放声音提示
     *
     * @param activity 上下文
     * @param resId    声音资源Id
     * @param listener 播放完成监听
     */
    public static void playSound(Activity activity, int resId, MediaPlayer.OnCompletionListener listener) {
        activity.setVolumeControlStream(AudioManager.STREAM_MUSIC);
        MediaPlayer player = new MediaPlayer();
        player.setAudioStreamType(AudioManager.STREAM_MUSIC);
        player.setOnCompletionListener(listener);

        try {
            AssetFileDescriptor file = activity.getResources()
                    .openRawResourceFd(resId);
            player.setDataSource(file.getFileDescriptor(),
                    file.getStartOffset(), file.getLength());
            file.close();
            player.setVolume(0.5f, 0.5f);
            player.prepare();
            player.start();

        } catch (IOException e) {
            JLog.e("IOException e:" + e.getMessage());
            e.printStackTrace();
            if (player != null) {
                player.release();
                player = null;
            }
        }
    }
}
