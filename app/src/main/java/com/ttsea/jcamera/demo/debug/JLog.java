package com.ttsea.jcamera.demo.debug;

import android.util.Log;

/**
 * 用来打印想要输出的数据，默认为false，将DEBUG设为false后会根据{@link #LOG_TAG}来输错日志 <br>
 * 从高到低为ASSERT, ERROR, WARN, INFO, DEBUG, VERBOSE<br>
 * 使用adb shell setprop log.tag.{@link #LOG_TAG}来控制输出log等级<br>
 * <p>
 * <b>more:</b>更多请点 <a href="http://www.ttsea.com" target="_blank">这里</a> <br>
 * <b>date:</b> 2017/4/10 9:55 <br>
 * <b>author:</b> Jason <br>
 * <b>version:</b> 1.0.0 <br>
 */
public final class JLog {
    private static boolean DEBUG = true;
    /**
     * 输出日志等级，当DEBUG为false的时候会根据设置的等级来输出日志<br>
     * 从高到低为ASSERT, ERROR, WARN, INFO, DEBUG, VERBOSE<br>
     */
    private static String LOG_TAG = "loa.log.LEVEL";

    /**
     * 开启或者关闭log
     *
     * @param enable true为开启log，false为关闭log
     */
    public static void enableLog(boolean enable) {
        DEBUG = enable;
    }

    public static void v(String msg) {
        v(null, msg);
    }

    public static void v(String tag, String msg) {
        if (DEBUG || Log.isLoggable(LOG_TAG, Log.VERBOSE)) {
            msg = combineLogMsg(msg);
            Log.i(tag == null ? getTag() : tag, "" + msg);
        }
    }

    public static void d(String msg) {
        d(null, msg);
    }

    public static void d(String msg, boolean saveToFile) {
        d(null, msg);
    }

    public static void d(String tag, String msg) {
        if (DEBUG || Log.isLoggable(LOG_TAG, Log.DEBUG)) {
            msg = combineLogMsg(msg);
            Log.d(tag == null ? getTag() : tag, "" + msg);
        }
    }

    public static void i(String msg) {
        i(null, msg);
    }

    public static void i(String tag, String msg) {
        if (DEBUG || Log.isLoggable(LOG_TAG, Log.INFO)) {
            msg = combineLogMsg(msg);
            Log.i(tag == null ? getTag() : tag, "" + msg);
        }
    }

    public static void w(String msg) {
        w(null, msg);
    }

    public static void w(String tag, String msg) {
        if (DEBUG || Log.isLoggable(LOG_TAG, Log.WARN)) {
            msg = combineLogMsg(msg);
            Log.w(tag == null ? getTag() : tag, "" + msg);
        }
    }

    public static void e(String msg) {
        e(null, msg);
    }

    public static void e(String tag, String msg) {
        if (DEBUG || Log.isLoggable(LOG_TAG, Log.ERROR)) {
            msg = combineLogMsg(msg);
            Log.e(tag == null ? getTag() : tag, "" + msg);
        }
    }

    public static void m(String msg) {
        d("jason", msg);
    }

    /**
     * 当传入的tag为null时，默认获取类名来作为tag
     */
    private static String getTag() {
        StackTraceElement[] traces = new Throwable().fillInStackTrace()
                .getStackTrace();
        String callingClass = "";

        for (StackTraceElement trace : traces) {
            callingClass = trace.getClassName();
            if (!callingClass.equals(JLog.class.getName())) {
                if (callingClass.lastIndexOf('.') != -1) {
                    callingClass = callingClass.substring(callingClass
                            .lastIndexOf('.') + 1);
                }
                if (callingClass.lastIndexOf('$') != -1) {
                    callingClass = callingClass.substring(0,
                            callingClass.indexOf('$'));
                }
                break;
            }
        }

        return callingClass;
    }

    /**
     * 组装动态传参的字符串 将动态参数的字符串拼接成一个字符串
     */
    private static String combineLogMsg(String... msg) {
        StringBuilder sb = new StringBuilder();
        sb.append("[Thread:").append(Thread.currentThread().getId())
                .append("]");

        StackTraceElement[] traces = new Throwable().fillInStackTrace()
                .getStackTrace();
        String caller = "<unknown>";
        for (StackTraceElement trace : traces) {
            String callingClass = trace.getClassName();
            if (!callingClass.equals(JLog.class.getName())) {
                if (callingClass.lastIndexOf('.') != -1) {
                    callingClass = callingClass.substring(callingClass
                            .lastIndexOf('.') + 1);
                }
                caller = callingClass + "." + trace.getMethodName() + "(rows:"
                        + trace.getLineNumber() + ")";
                break;
            }
        }

        sb.append(caller).append(": ");
        if (null != msg) {
            for (String s : msg) {
                sb.append(s);
            }
        }

        return sb.toString();
    }
}

