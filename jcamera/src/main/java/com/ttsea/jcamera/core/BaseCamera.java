package com.ttsea.jcamera.core;

import android.content.Context;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Handler;
import android.os.Looper;
import android.view.Surface;

import com.ttsea.jcamera.annotation.Flash;

import static android.content.Context.SENSOR_SERVICE;

/**
 * Camera基类，所有Camera类都将继承这个类，一些通用的方法和功能将在这里实现<br>
 * 1.只需调用{@link #registerSensor()}和{@link #unregisterSensor()} 即可使用感应器<br>
 */
abstract class BaseCamera implements ICamera, SensorEventListener {
    private final float ALPHA = 0.8f;//用户计算加速度的固定值（不能随意改动）

    private final int SENSOR_PERIOD = 1000;//定义传感器多久响应一次(单位毫秒)
    //当xyz轴的任意一个方向的加速度大于这个值的时候，则触发自动聚焦(单位 m/s^2)
    private final float MAX_ACCELEROMETER = 0.35f;

    private Context mContext;
    private final SensorManager mSensorManager;
    private final Sensor mAccelerometer;//加速传感器
    private final Sensor mGravitySensor;//重力传感器

    private final float[] mGravity = new float[3];//用于记录重力加速度
    private final float[] mLastGravity = new float[3];//用于记录上次的重力加速度
    private long lastSensorTimestamp = 0;//记录上次传感器执行的时间

    private Handler mHandler;

    public BaseCamera(Context context) {
        mSensorManager = (SensorManager) context.getSystemService(SENSOR_SERVICE);
        mAccelerometer = mSensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        mGravitySensor = mSensorManager.getDefaultSensor(Sensor.TYPE_GRAVITY);

        init(context);
    }

    private void init(Context context) {
        mContext = context;

        mHandler = new Handler(Looper.getMainLooper());
    }

    /**
     * 注册传感器<br>
     * 在开启预览的时候调用<br>
     * 注册了传感器在不用的时候要反注册，否则会非常耗电
     */
    protected void registerSensor() {
        if (mSensorManager != null && mAccelerometer != null && mGravitySensor != null) {
            boolean result = mSensorManager.registerListener(this, mAccelerometer, SensorManager.SENSOR_DELAY_NORMAL);
            //如果注册失败，则尝试返注册一下，然后再次注册
//            if (!result) {
//                mSensorManager.unregisterListener(this, mAccelerometer);
//                result = mSensorManager.registerListener(this, mAccelerometer, SensorManager.SENSOR_DELAY_NORMAL);
//            }
            JCameraLog.d("register Accelerometer sensor, success:" + result);


            result = mSensorManager.registerListener(this, mGravitySensor, SensorManager.SENSOR_DELAY_NORMAL);
            //如果注册失败，则尝试返注册一下，然后再次注册
//            if (!result) {
//                mSensorManager.unregisterListener(this, mGravitySensor);
//                result = mSensorManager.registerListener(this, mGravitySensor, SensorManager.SENSOR_DELAY_NORMAL);
//            }
            JCameraLog.d("register GravitySensor sensor, success:" + result);
        }
    }

    /**
     * 反注册传感器<br>
     * 在关闭预览和释放相机的时候调用<br>
     * 注册了传感器在不用的时候要反注册，否则会非常耗电
     */
    protected void unregisterSensor() {
        if (mSensorManager != null) {
            JCameraLog.d("unregister sensors");
            try {
                mSensorManager.unregisterListener(this);
            } catch (Exception e) {
                //ignore error
            }
        }
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        //重力传感器回调
        if (event.sensor.getType() == Sensor.TYPE_GRAVITY) {
            mGravity[0] = event.values[0];
            mGravity[1] = event.values[1];
            mGravity[2] = event.values[2];
            return;
        }

        //加速度传感器回调
        if (event.sensor.getType() == Sensor.TYPE_ACCELEROMETER) {
            //这里控制传感器回调的频率，注意这里是时间单位是纳秒，要转换成毫秒
            if ((event.timestamp - lastSensorTimestamp) / 1000000 <= SENSOR_PERIOD) {
                return;
            }
            lastSensorTimestamp = event.timestamp;

            float x = ALPHA * mGravity[0] + (1 - ALPHA) * event.values[0];
            float y = ALPHA * mGravity[1] + (1 - ALPHA) * event.values[1];
            float z = ALPHA * mGravity[2] + (1 - ALPHA) * event.values[2];

            x = event.values[0] - x;
            y = event.values[0] - y;
            z = event.values[0] - z;

            //第一次初始化值
            if (mLastGravity[0] == 0 && mLastGravity[1] == 0 && mLastGravity[2] == 0) {
                mLastGravity[0] = x;
                mLastGravity[1] = y;
                mLastGravity[2] = z;
                return;
            }

            float xa = Math.abs(mLastGravity[0] - x);
            float ya = Math.abs(mLastGravity[1] - y);
            float za = Math.abs(mLastGravity[2] - z);

            //当xyz轴的任意一个方向的加速度大于这个值的时候，则触发自动聚焦
            if (xa > MAX_ACCELEROMETER || ya > MAX_ACCELEROMETER || za > MAX_ACCELEROMETER) {
                startAutoFocus();
            }

            mLastGravity[0] = x;
            mLastGravity[1] = y;
            mLastGravity[2] = z;

            return;
        }
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {
        //do nothing
    }

    /**
     * 开始自动聚焦<br>
     * 1.这里可以主动调用<br>
     * 2.如果有注册传感器，则在满足条件的时候会自动触发聚焦<br>
     */
    protected abstract void startAutoFocus();

    /**
     * 将runnable放置到主线程中执行
     *
     * @param action
     */
    protected void runOnUiThread(Runnable action) {
        if (Thread.currentThread() == Looper.getMainLooper().getThread()) {
            action.run();
        } else {
            mHandler.post(action);
        }
    }

    /**
     * 将YUV数据顺时针旋转90度
     *
     * @param data        待旋转的数据
     * @param imageWidth  data宽度
     * @param imageHeight data高度
     * @return 旋转后的数据
     */
    protected byte[] rotateYUV420Degree90(byte[] data, int imageWidth, int imageHeight) {
        byte[] yuv = new byte[imageWidth * imageHeight * 3 / 2];
        int i = 0;
        for (int x = 0; x < imageWidth; x++) {
            for (int y = imageHeight - 1; y >= 0; y--) {
                yuv[i] = data[y * imageWidth + x];
                i++;
            }
        }
        i = imageWidth * imageHeight * 3 / 2 - 1;
        for (int x = imageWidth - 1; x > 0; x = x - 2) {
            for (int y = 0; y < imageHeight / 2; y++) {
                yuv[i] = data[(imageWidth * imageHeight) + (y * imageWidth) + x];
                i--;
                yuv[i] = data[(imageWidth * imageHeight) + (y * imageWidth)
                        + (x - 1)];
                i--;
            }
        }
        return yuv;
    }

    /**
     * 获取闪关灯 String描述
     *
     * @param flash see{@link Constants}
     * @return
     */
    protected String getFlashStr(@Flash int flash) {
        String des = "unknown";
        switch (flash) {
            case Constants.FLASH_OFF:
                des = "off";
                break;
            case Constants.FLASH_ON:
                des = "on";
                break;
            case Constants.FLASH_AUTO:
                des = "auto";
                break;
            case Constants.FLASH_TORCH:
                des = "torch";
                break;
            case Constants.FLASH_RED_EYE:
                des = "red eye";
                break;
            case Constants.FLASH_ON_ALWAYS:
                des = "on always";
                break;
            case Constants.FLASH_ON_EXTERNAL:
                des = "on external";
                break;
        }

        return des;
    }

    /**
     * 通过Activity的选择角度得到Camera preview需要选中的角度
     *
     * @param activityRotation Activity的选择角度得到
     * @return 0/90/180/270
     */
    protected int getCameraRotationDegrees(int activityRotation) {
        int degrees = 0;
        switch (activityRotation) {
            case Surface.ROTATION_0:
                degrees = 90;
                break;

            case Surface.ROTATION_90:
                degrees = 0;
                break;

            case Surface.ROTATION_180:
                degrees = 270;
                break;

            case Surface.ROTATION_270:
                degrees = 180;
                break;
        }

        return degrees;
    }
}
