package com.ttsea.jcamera.core;

import android.app.Activity;
import android.content.Context;
import android.content.ContextWrapper;
import android.graphics.ImageFormat;
import android.graphics.Rect;
import android.hardware.Camera;
import android.media.CamcorderProfile;
import android.media.MediaRecorder;
import android.os.Build;
import android.os.Environment;
import android.os.Handler;
import android.os.HandlerThread;
import android.util.SparseIntArray;
import android.view.Surface;
import android.view.View;

import com.ttsea.jcamera.annotation.Facing;
import com.ttsea.jcamera.callbacks.CameraCallback;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.SortedSet;
import java.util.concurrent.atomic.AtomicBoolean;

import androidx.annotation.Nullable;
import androidx.collection.SparseArrayCompat;

@SuppressWarnings("deprecation")
class Camera1 extends BaseCamera {
    private final int DEFAULT_CAMERA_ID = -1;
    private final AspectRatio DEFAULT_RATIO = AspectRatio.parse("16:9");

    private Context mContext;

    private Camera mCamera;//当前Camera
    private int mCameraId;//当前Camera Id
    private Camera.Parameters mParams;

    private MediaRecorder mMediaRecorder;//用于录音

    private ISurface iSurface;//对应的SurfaceView
    private IMaskView iMaskView;//SurfaceViews上面的view
    private CameraCallback mCallback;

    private HandlerThread mHandlerThread;
    private Handler mChildHandler;

    //记录摄像头所支持的预览size
    private final SizeMap mPicSizeMap = new SizeMap();
    //记录摄像头所支持的图片size
    private final SizeMap mPreSizeMap = new SizeMap();
    //记录摄像头是否正在使用，正在拍照或者录像都代表真正使用
    private final AtomicBoolean isCameraInUsing = new AtomicBoolean(false);
    //记录是否正在预览，默认:false
    private final AtomicBoolean isShowingPreview = new AtomicBoolean(false);

    private static final SparseIntArray FACING_MAP = new SparseIntArray();

    static {
        FACING_MAP.put(Constants.FACING_BACK, Camera.CameraInfo.CAMERA_FACING_BACK);
        FACING_MAP.put(Constants.FACING_FRONT, Camera.CameraInfo.CAMERA_FACING_FRONT);
    }

    private static final SparseArrayCompat<String> FLASH_MODES = new SparseArrayCompat<>();

    static {
        FLASH_MODES.put(Constants.FLASH_OFF, Camera.Parameters.FLASH_MODE_OFF);
        FLASH_MODES.put(Constants.FLASH_ON, Camera.Parameters.FLASH_MODE_ON);
        FLASH_MODES.put(Constants.FLASH_AUTO, Camera.Parameters.FLASH_MODE_AUTO);
        FLASH_MODES.put(Constants.FLASH_TORCH, Camera.Parameters.FLASH_MODE_TORCH);
        FLASH_MODES.put(Constants.FLASH_RED_EYE, Camera.Parameters.FLASH_MODE_RED_EYE);
    }

    public Camera1(Context context, ISurface iSurface, IMaskView iMaskView) {
        super(context);

        this.mContext = context;
        this.iSurface = iSurface;
        this.iMaskView = iMaskView;
    }

    @Override
    public void setCameraCallback(CameraCallback callback) {
        mCallback = callback;
    }

    @Override
    public void openCamera() {
        openCamera(Constants.FACING_BACK);
    }

    @Override
    public void openCamera(@Facing final int facing) {
        if (mHandlerThread == null) {
            mHandlerThread = new HandlerThread("Camera1");
            mHandlerThread.start();
            mChildHandler = new Handler(mHandlerThread.getLooper());
            JCameraLog.d("Start a new handler thread:(" + mHandlerThread.getName()
                    + ":" + mHandlerThread.getId() + ")");
        }

        mChildHandler.post(new Runnable() {
            @Override
            public void run() {
                openCameraInThread(facing);
            }
        });
    }

    /**
     * see {@link #openCamera(int)}
     */
    private void openCameraInThread(@Facing int facing) {
        if (!Utils.checkCameraHardware(mContext)) {
            //设备不支持摄像头（或者没有摄像头）
            final String errorMsg = "Device has no camera.";
            if (mCallback != null) {
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        mCallback.onCameraError(CameraCallback.CODE_NO_CAMERA, errorMsg);
                    }
                });
            }
            resetStatus();
            JCameraLog.d(errorMsg);
            return;
        }

        if (isCameraInUsing.get()) {
            JCameraLog.d(getCameraStr(mCameraId) + " is in using, can not open another camera.");
            return;
        }

        final int cameraId = FACING_MAP.get(facing, DEFAULT_CAMERA_ID);

        //表示要打开的摄像头，已经打开
        if (mCamera != null && mCameraId == cameraId) {
            JCameraLog.d(getCameraStr(mCameraId) + " already opened...");
            return;
        }

        //释放已经打开的摄像头
        if (mCamera != null) {
            mChildHandler.removeCallbacksAndMessages(null);

            isCameraInUsing.set(false);
            isShowingPreview.set(false);

            mCamera.release();
            mCameraId = DEFAULT_CAMERA_ID;
            mCamera = null;
            mParams = null;
        }

        try {
            if (cameraId == DEFAULT_CAMERA_ID) {
                //打开默认的摄像头
                mCamera = Camera.open();
                mCameraId = cameraId;
            } else {
                //打开指定摄像头
                mCamera = Camera.open(cameraId);
                mCameraId = cameraId;
            }

        } catch (Exception e) {
            final String errorMsg = "Exception e:" + e.getMessage() + ", " + getCameraStr(cameraId);
            JCameraLog.e(errorMsg);
            e.printStackTrace();

            resetStatus();

            if (mCallback != null) {
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        mCallback.onCameraError(CameraCallback.CODE_OPEN_FAILED, errorMsg);
                    }
                });
            }
            return;
        }

        if (mCamera == null) {
            final String errorMsg = "Open camera failed, " + getCameraStr(cameraId);
            JCameraLog.e(errorMsg);

            resetStatus();

            if (mCallback != null) {
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        mCallback.onCameraError(CameraCallback.CODE_OPEN_FAILED, errorMsg);
                    }
                });
            }

            return;
        }

        mParams = mCamera.getParameters();

        //记录该摄像头所支持的预览尺寸
        mPreSizeMap.clear();
        for (Camera.Size size : mParams.getSupportedPreviewSizes()) {
            Size s = new Size(size.width, size.height);
            mPreSizeMap.addSize(s);
        }

        //记录该摄像头所支持的图片尺寸
        mPicSizeMap.clear();
        for (Camera.Size size : mParams.getSupportedPictureSizes()) {
            Size s = new Size(size.width, size.height);
            mPicSizeMap.addSize(s);
        }

        JCameraLog.d("Opened camera, " + getCameraStr(cameraId) + "\n"
                + "mPreSizeMap:" + mPreSizeMap + "\n"
                + "mPicSizeMap:" + mPicSizeMap);

        registerSensor();
        adjustCameraParams();

        if (setUpPreview()) {
            startPreview();
        }

        if (mCallback != null) {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    mCallback.onCameraOpened();
                }
            });
        }
    }

    @Override
    public void releaseCamera() {
        if (mCamera == null) {
            return;
        }

        unregisterSensor();

        mCamera.release();
        resetStatus();
        JCameraLog.d("release " + getCameraStr(mCameraId));

        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                iMaskView.clearAnimation();
                if (mCallback != null) {
                    mCallback.onCameraClosed();
                }
            }
        });
    }

    @Override
    protected void startAutoFocus() {
        if (mChildHandler == null || !isShowingPreview.get()) {
            return;
        }
        if (mChildHandler.getLooper().getThread() == Thread.currentThread()) {
            startAutoFocusInThread();
        } else {
            mChildHandler.post(new Runnable() {
                @Override
                public void run() {
                    startAutoFocusInThread();
                }
            });
        }
    }

    /** 在子线程中启动自动聚焦 */
    private void startAutoFocusInThread() {
        if (mCamera == null || mChildHandler == null || isCameraInUsing.get()) {
            return;
        }

        List<String> list = mParams.getSupportedFocusModes();
        //不支持自动聚焦
        if (list == null || list.isEmpty()) {
            JCameraLog.w(getCameraStr(mCameraId) + " not support focus.");
            return;
        }

        String focusMode = mParams.getFocusMode();
        focusMode = focusMode == null ? "" : focusMode;

        if (focusMode.equals(Camera.Parameters.FOCUS_MODE_INFINITY)
                || focusMode.equals(Camera.Parameters.FOCUS_MODE_FIXED)
                || focusMode.equals(Camera.Parameters.FOCUS_MODE_EDOF)) {
            JCameraLog.d("focusMode:" + focusMode + ", should not call autoFocus.");
            return;
        }

        //先取消正在执行的聚焦动作
        cancelAutoFocus();
        JCameraLog.d("start auto focus...");

        //开始聚焦
        try {
            mCamera.autoFocus(autoFocusCallback);

        } catch (Exception e) {
            JCameraLog.e("Exception e:" + e.getMessage());
            e.printStackTrace();
            mChildHandler.postDelayed(autoFocusRunnable, 500);
        }
    }

    /**
     * 取消自动聚焦
     */
    private void cancelAutoFocus() {
        if (mCamera == null || mChildHandler == null) {
            return;
        }

        mChildHandler.removeCallbacks(autoFocusRunnable);
        try {
            mCamera.cancelAutoFocus();
        } catch (Exception e) {
            //ignore error
            JCameraLog.w("Exception e:" + e.getMessage());
        }
    }

    @Override
    public void startPreview() {
        startPreview(true);
    }

    /**
     * 开启预览
     *
     * @param startAutoFocus 预览开启后是否执行自动聚焦
     */
    protected void startPreview(boolean startAutoFocus) {
        if (mCamera == null || !iSurface.isReady()) {
            return;
        }

        JCameraLog.d("startPreview...");
        //开始预览，开始自动聚焦
        isShowingPreview.set(true);
        mCamera.startPreview();

        if (mCallback != null) {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    mCallback.onStartPreview();
                }
            });
        }

        if (startAutoFocus) {
            startAutoFocus();
        }
    }

    @Override
    public void stopPreview() {
        if (mCamera == null) {
            return;
        }

        JCameraLog.d("stopPreview...");
        isShowingPreview.set(false);
        mCamera.stopPreview();
        if (mCallback != null) {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    mCallback.onStopPreview();
                }
            });
        }
    }

    @Override
    public int getFacing() {
        int index = FACING_MAP.indexOfValue(mCameraId);
        if (index > -1) {
            return FACING_MAP.keyAt(index);
        }
        return Constants.FACING_BACK;
    }

    @Override
    public boolean isShowingPreview() {
        return isShowingPreview.get();
    }

    @Override
    public Set<AspectRatio> getSupportedAspectRatios() {
        SizeMap idea = new SizeMap();
        idea.addAll(mPreSizeMap);

        //取mPreSizeMap和mPicSizeMap都支持的比例
        for (AspectRatio ratio : mPreSizeMap.keySet()) {
            if (mPicSizeMap.get(ratio) == null) {
                idea.remove(ratio);
            }
        }

        return idea.keySet();
    }

    @Override
    public boolean setAspectRatio(AspectRatio ratio) {
        if (isCameraInUsing.get()) {
            JCameraLog.d(getCameraStr(mCameraId) + " is in using, can not set ratio.");
            return false;
        }

        if (ratio.equals(getAspectRatio())) {
            return true;
        }

        Set<AspectRatio> ratios = getSupportedAspectRatios();
        if (!ratios.contains(ratio)) {
            JCameraLog.w(getCameraStr(mCameraId) + " unsupport ratio:" + ratio);
            return false;
        }

        SortedSet<Size> preSizes = mPreSizeMap.get(ratio);
        Size preSize = findPreviewSize(preSizes);

        SortedSet<Size> picSizes = mPicSizeMap.get(ratio);
        Size picSize = picSizes.last();

        mParams.setPreviewSize(preSize.width, preSize.height);
        mParams.setPictureSize(picSize.width, picSize.height);
        JCameraLog.d("setPreviewSize:" + preSize + ", setPictureSize:" + picSize);

        updateCameraParams(true);
        startAutoFocus();
        return true;
    }

    @Override
    public AspectRatio getAspectRatio() {
        if (mParams == null) {
            return DEFAULT_RATIO;
        }

        Camera.Size size = mParams.getPreviewSize();
        if (size == null || size.width <= 0 || size.height <= 0) {
            return DEFAULT_RATIO;
        }

        return AspectRatio.of(size.width, size.height);
    }

    @Override
    public void onActivityRotation(int rotation) {
        if (mCamera == null) {
            return;
        }

        int degrees = getCameraRotationDegrees(rotation);
        JCameraLog.d("onRotation, rotation:" + rotation + ", degrees:" + degrees);
        mCamera.setDisplayOrientation(degrees);

        //前置摄像头和后置摄像头的旋转角度相对屏幕来说是不一样的
        if (getFacing() == Constants.FACING_FRONT) {
            mParams.setRotation((degrees + 180) % 360);
        } else {
            mParams.setRotation(degrees);
        }
        updateCameraParams(false);
    }

    @Override
    public List<Integer> getSupportedFlashModes() {
        if (mCamera == null) {
            return new ArrayList<>();
        }
        List<Integer> supportList = new ArrayList<>();

        List<String> list = mParams.getSupportedFlashModes();
        if (list == null) {
            list = new ArrayList<>();
        }

        for (int i = 0; i < list.size(); i++) {
            String value = list.get(i);

            for (int j = 0; j < FLASH_MODES.size(); j++) {
                int key = FLASH_MODES.keyAt(j);
                if (value.equals(FLASH_MODES.get(key))) {
                    supportList.add(key);
                }
            }
        }

        return supportList;
    }

    @Override
    public int getFlashMode() {
        int flash = Constants.FLASH_OFF;

        if (mParams == null) {
            return flash;
        }

        String mode = mParams.getFlashMode();
        int index = FLASH_MODES.indexOfValue(mode);
        if (index >= 0) {
            flash = FLASH_MODES.keyAt(index);
        }

        return flash;
    }

    @Override
    public boolean setFlashMode(int flash) {
        if (mCamera == null) {
            return false;
        }

        if (isCameraInUsing.get()) {
            JCameraLog.d(getCameraStr(mCameraId) + " is in using, can not set flash.");
            return false;
        }

        if (getFlashMode() == flash) {
            return true;
        }

        List<Integer> list = getSupportedFlashModes();

        if (list.contains(flash)) {
            mParams.setFlashMode(FLASH_MODES.get(flash));
            updateCameraParams(false);
            return true;

        } else {
            JCameraLog.w(getCameraStr(mCameraId) + " unsupport the flash mode:" + FLASH_MODES.get(flash));
            return false;
        }
    }

    @Override
    public int getNumberOfCameras() {
        return Camera.getNumberOfCameras();
    }

    @Override
    public void takePhoto(final File outputFile) {
        if (mChildHandler.getLooper().getThread() == Thread.currentThread()) {
            takePhotoInThread(outputFile);

        } else {
            mChildHandler.post(new Runnable() {
                @Override
                public void run() {
                    takePhotoInThread(outputFile);
                }
            });
        }
    }

    /** 在子线程中拍照 */
    private void takePhotoInThread(final File outputFile) {
        if (mCamera == null || !isShowingPreview.get()) {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    if (mCallback != null) {
                        mCallback.onPictureTaken(null, "mCamera is null.");
                    }
                }
            });
            return;
        }

        JCameraLog.d("takePhoto...");

        //如果outputFile为空，则取默认文件
        File tmpFile = outputFile;
        if (tmpFile == null) {
            tmpFile = new File(mContext.getExternalFilesDir(Environment.DIRECTORY_PICTURES),
                    "IMG_" + Utils.getCurrentTime("yyyyMMddHHmmss") + ".jpg");
        }
        final File picFile = tmpFile;
        if (!picFile.getParentFile().exists()) {
            picFile.getParentFile().mkdirs();
        }

        Camera.ShutterCallback shutter = new Camera.ShutterCallback() {
            @Override
            public void onShutter() {
                isCameraInUsing.set(true);
                //这里可以响应快门声
            }
        };

        Camera.PictureCallback picCallback = new Camera.PictureCallback() {
            @Override
            public void onPictureTaken(byte[] data, Camera camera) {
                //拍完照，会自动停止预览
                isShowingPreview.set(false);
                isCameraInUsing.set(false);
                startPreview(false);

                try {
                    //将data保存到文件中
                    ByteUtils.saveData(data, picFile);
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            if (mCallback != null) {
                                mCallback.onPictureTaken(picFile, null);
                            }
                        }
                    });

                } catch (final Exception e) {
                    JCameraLog.e("Exception e:" + e.getMessage());
                    e.printStackTrace();

                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            if (mCallback != null) {
                                mCallback.onPictureTaken(null, e.getMessage());
                            }
                        }
                    });
                }
            }
        };

        try {
            mCamera.takePicture(shutter, null, picCallback);

        } catch (final Exception e) {
            JCameraLog.e("Exception e:" + e.getMessage());
            e.printStackTrace();

            isShowingPreview.set(false);
            isCameraInUsing.set(false);

            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    if (mCallback != null) {
                        mCallback.onPictureTaken(null, e.getMessage());
                    }
                }
            });
        }
    }

    @Override
    public void startRecord(File outputFile) {
        if (mCamera == null || isCameraInUsing.get()) {
            return;
        }
        JCameraLog.d("startRecord...");

        if (!outputFile.getParentFile().exists()) {
            outputFile.getParentFile().mkdirs();
        }

        try {
            mMediaRecorder = new MediaRecorder();

            mParams.setRecordingHint(true);
            updateCameraParams(false);
            mCamera.unlock();
            mMediaRecorder.setCamera(mCamera);

            //设置音源
            mMediaRecorder.setAudioSource(MediaRecorder.AudioSource.CAMCORDER);
            //设置视频源
            mMediaRecorder.setVideoSource(MediaRecorder.VideoSource.CAMERA);

            CamcorderProfile profile = getCamcorderProfile();
            profile.videoBitRate = 2000000;
            profile.videoFrameRate = 24;
            mMediaRecorder.setProfile(profile);
            JCameraLog.d("CamcorderProfile:" + getCamcorderProfileStr(profile));

            //设置输出文件
            mMediaRecorder.setOutputFile(outputFile.getAbsolutePath());

            //设置旋转角度
            int rotation = Utils.getRotation((View) iMaskView);
            int degrees = getCameraRotationDegrees(rotation);
            //前置摄像头和后置摄像头的旋转角度相对屏幕来说是不一样的
            if (getFacing() == Constants.FACING_FRONT) {
                degrees = (degrees + 180) % 360;
            }
            mMediaRecorder.setOrientationHint(degrees);

            mMediaRecorder.prepare();
            mMediaRecorder.start();
            isCameraInUsing.set(true);

        } catch (Exception e) {
            JCameraLog.e("Exception e:" + e.getMessage());
            e.printStackTrace();

            stopRecord();
        }
    }

    @Override
    public void stopRecord() {
        if (mCamera == null) {
            return;
        }

        try {
            if (mMediaRecorder != null) {
                mMediaRecorder.stop();
                mMediaRecorder.release();
            }
        } catch (Exception e) {
            //ignore error
        }

        isCameraInUsing.set(false);
        mCamera.lock();

        JCameraLog.d("stopRecord...");
    }

    @Override
    public void onSurfaceCreated() {
        if (mCamera == null) {
            return;
        }

        //打开完相机后，可以开始预览了
        if (setUpPreview()) {
            startPreview();
        }
    }

    @Override
    public boolean onSurfaceTapped(float x, float y) {
        if (mCamera == null || !isShowingPreview.get()) {
            return false;
        }

        //不支持区域聚焦
        if (mParams.getMaxNumFocusAreas() <= 0) {
            JCameraLog.w(getCameraStr(mCameraId) + " not support Area focus.");
            return false;
        }

        List<String> modesList = mParams.getSupportedFocusModes();
        if (modesList == null || modesList.isEmpty()) {
            JCameraLog.w(getCameraStr(mCameraId) + " has no focus mode.");
            return false;
        }

        String focusMode = "";
        if (modesList.contains(Camera.Parameters.FOCUS_MODE_MACRO)) {
            focusMode = Camera.Parameters.FOCUS_MODE_MACRO;

        } else if (modesList.contains(Camera.Parameters.FOCUS_MODE_AUTO)) {
            focusMode = Camera.Parameters.FOCUS_MODE_AUTO;
        }

        if (Utils.isEmpty(focusMode)) {
            JCameraLog.w(getCameraStr(mCameraId) + " not support MACRO/AUTO focus mode.");
            return false;
        }

        Rect rect = calculateTapArea(x, y);
        if (rect == null || rect.isEmpty()) {
            return false;
        }

        cancelAutoFocus();

        Camera.Area area = new Camera.Area(rect, 900);
        List<Camera.Area> list = new ArrayList<>();
        list.add(area);
        mParams.setFocusAreas(list);

        boolean flag = false;
        if (!focusMode.equals(mParams.getFocusMode())) {
            mParams.setFocusMode(focusMode);
            flag = true;
        }
        updateCameraParams(flag);

        JCameraLog.d("start focus areas:" + area.rect);

        //开始聚焦
        try {
            mChildHandler.post(new Runnable() {
                @Override
                public void run() {
                    mCamera.autoFocus(autoFocusCallback);
                }
            });

        } catch (Exception e) {
            //ignore error
        }

        return true;
    }

    @Override
    public boolean zoomIn(float value) {
        //不支持缩放，或者摄像头正在使用
        if (!mParams.isZoomSupported() || isCameraInUsing.get()) {
            return false;
        }

        int maxZoom = mParams.getMaxZoom();
        int currentZoom = mParams.getZoom();

        //已经达到了最大值
        if (currentZoom >= maxZoom) {
            return false;
        }

        if (value < 0) {
            int step = 4;//根据实际情况取 4 5 6 中的一个
            for (int i = 4; i < 7; i++) {
                if (maxZoom % i == 0) {
                    step = i;
                    break;
                }
            }
            //计算双击一下，放大多少
            step = maxZoom / step;

            value = currentZoom + step;
            if (value > maxZoom) {
                value = maxZoom;
            }

        } else {
            value = currentZoom + value * maxZoom;
            if (value > maxZoom) {
                value = maxZoom;
            }
        }

        if (((int) value) == currentZoom) {
            return false;
        }

        if (mParams.isSmoothZoomSupported()) {
            mCamera.startSmoothZoom((int) value);
        } else {
            mParams.setZoom((int) value);
            updateCameraParams(false);
        }

        startAutoFocus();

        return true;
    }

    @Override
    public boolean zoomOut(float value) {
        //不支持缩放，或者摄像头正在使用
        if (!mParams.isZoomSupported() || isCameraInUsing.get()) {
            return false;
        }

        int minZoom = 0;
        int maxZoom = mParams.getMaxZoom();
        int currentZoom = mParams.getZoom();

        //已经达到了最小值
        if (currentZoom <= minZoom) {
            return false;
        }

        if (value < 0) {
            int step = 4;//根据实际情况取 4 5 6 中的一个
            for (int i = 4; i < 7; i++) {
                if (maxZoom % i == 0) {
                    step = i;
                    break;
                }
            }
            //计算双击一下，缩小多少
            step = maxZoom / step;

            value = currentZoom - step;
            if (value < minZoom) {
                value = minZoom;
            }

        } else {
            value = currentZoom - value * maxZoom;
            if (value < minZoom) {
                value = minZoom;
            }
        }

        if (((int) value) == currentZoom) {
            return false;
        }

        if (mParams.isSmoothZoomSupported()) {
            mCamera.startSmoothZoom((int) value);
        } else {
            mParams.setZoom((int) value);
            updateCameraParams(false);
        }

        startAutoFocus();

        return true;
    }

    @Override
    public void setOneShotPreview() {
        if (mCamera == null || mParams == null) {
            return;
        }

        try {
            mCamera.setOneShotPreviewCallback(new Camera.PreviewCallback() {
                @Override
                public void onPreviewFrame(byte[] data, Camera camera) {
                    if (mCallback != null && mCamera != null) {
                        if (data == null) {
                            mCallback.oneShotFrameData(null, ImageFormat.UNKNOWN, 0, 0);
                            return;
                        }

                        int format = mParams.getPreviewFormat();
                        Camera.Size size = mParams.getPreviewSize();

                        int ration = Utils.getRotation((View) iMaskView);
                        if (ration == 0 || ration == 2) {
                            data = rotateYUV420Degree90(data, size.width, size.height);
                            mCallback.oneShotFrameData(data, format, size.height, size.width);
                            JCameraLog.d("onPreviewFrame, format:" + format
                                    + ", size:" + size.height + ":" + size.width);

                        } else {
                            mCallback.oneShotFrameData(data, format, size.width, size.height);
                            JCameraLog.d("onPreviewFrame, format:" + format
                                    + ", size:" + size.width + ":" + size.height);
                        }
                    }
                }
            });
        } catch (Exception e) {
            //ignore error
        }
    }

    /**
     * 适配相机参数
     */
    private void adjustCameraParams() {
        if (mCamera == null) {
            return;
        }

        //设置相对应Activity的旋转角度
        int rotation = Surface.ROTATION_0;
        Activity activity = getActivity();
        if (activity != null && activity.getWindowManager() != null
                && activity.getWindowManager().getDefaultDisplay() != null) {
            rotation = activity.getWindowManager().getDefaultDisplay().getRotation();
        }
        onActivityRotation(rotation);

        //设置比例
        Set<AspectRatio> ratioSet = getSupportedAspectRatios();
        AspectRatio ratio = DEFAULT_RATIO;
        if (!ratioSet.contains(ratio)) {
            for (AspectRatio r : ratioSet) {
                ratio = r;
            }
        }

        //在同比例的条件下，预览size总取最接近屏幕的尺寸
        Size preSize = findPreviewSize(mPreSizeMap.get(ratio));
        mParams.setPreviewSize(preSize.width, preSize.height);

        //在同比例的条件下，图片size总取最大的尺寸
        Size picSize = mPicSizeMap.get(ratio).last();
        mParams.setPictureSize(picSize.width, picSize.height);

        //设置闪光灯模式
        List<Integer> flashList = getSupportedFlashModes();
        if (flashList.contains(Constants.FLASH_AUTO)) {
            mParams.setFlashMode(FLASH_MODES.get(Constants.FLASH_AUTO));
        }

        //设置聚焦模式
        List<String> supportList = mParams.getSupportedFocusModes();
        if (supportList == null) {
            supportList = new ArrayList<>();
        }
        if (supportList.contains(Camera.Parameters.FOCUS_MODE_AUTO)) {
            mParams.setFocusMode(Camera.Parameters.FOCUS_MODE_AUTO);
        } else if (supportList.contains(Camera.Parameters.FOCUS_MODE_CONTINUOUS_PICTURE)) {
            mParams.setFocusMode(Camera.Parameters.FOCUS_MODE_CONTINUOUS_PICTURE);
        } else if (supportList.contains(Camera.Parameters.FOCUS_MODE_FIXED)) {
            mParams.setFocusMode(Camera.Parameters.FOCUS_MODE_FIXED);
        }

        if (Build.VERSION.SDK_INT > 14 && mParams.isVideoStabilizationSupported()) {
            mParams.setVideoStabilization(true);
        }

        JCameraLog.d("adjustCameraParams, ratio:" + ratio
                + ", preSize:" + preSize
                + ", picSize:" + picSize
                + ", flash:" + mParams.getFlashMode()
                + ", focus:" + mParams.getFocusMode());

        updateCameraParams(false);
    }

    /**
     * 更新参数到相机
     *
     * @param rePreview 如果这样预览，在设置完参数后是否重新启动预览<br>
     *                  true:重新启动预览，false:不重新启动预览
     */
    private void updateCameraParams(boolean rePreview) {
        if (mCamera == null) {
            return;
        }

        try {
            mCamera.setParameters(mParams);
        } catch (Exception e) {
            JCameraLog.w("Exception e:" + e.getMessage());
            e.printStackTrace();
        }

        if (rePreview && isShowingPreview.get()) {
            mCamera.stopPreview();
            mCamera.startPreview();
            JCameraLog.d("updateCameraParams, rePreview:" + true);
        } else {
            JCameraLog.d("updateCameraParams, rePreview:" + false);
        }
    }

    /**
     * 查找最合适的预览尺寸，总是找最接近手机屏幕的尺寸
     */
    private Size findPreviewSize(SortedSet<Size> sizes) {
        if (mCamera == null) {
            return sizes.first();
        }

        int width = iMaskView.getViewWidth();
        int height = iMaskView.getViewHeight();

        Size result = null;
        for (Size size : sizes) {
            if (width != 0 && height != 0) {
                if (width <= size.width && height <= size.height) {
                    return size;
                }
            }
            result = size;
        }

        return result;
    }

    /**
     * 设置预览view
     *
     * @return true:设置成功，false:设置失败
     */
    private boolean setUpPreview() {
        if (!iSurface.isReady()) {
            return false;
        }

        try {
            if (iSurface instanceof SurfaceViewPreview) {
                mCamera.setPreviewDisplay(((SurfaceViewPreview) iSurface).getHolder());
                return true;
            }

            if (iSurface instanceof SurfaceTexturePreview) {
                mCamera.setPreviewTexture(((SurfaceTexturePreview) iSurface).getSurfaceTexture());
                return true;
            }

        } catch (Exception e) {
            JCameraLog.e("IOException e:" + e.getMessage());
            e.printStackTrace();
            if (mCallback != null) {
                mCallback.onCameraError(CameraCallback.CODE_START_PREVIEW_FAILED, e.getMessage());
            }
            return false;
        }

        if (mCallback != null) {
            String errorMsg = "iSurface must instanceof SurfaceViewPreview or " +
                    "SurfaceTexturePreview, iSurface:" + iSurface;
            mCallback.onCameraError(CameraCallback.CODE_START_PREVIEW_FAILED, errorMsg);
        }
        return false;
    }

    /**
     * 重设一些常量的状态
     */
    private void resetStatus() {
        if (mChildHandler != null) {
            mChildHandler.removeCallbacksAndMessages(null);
            mChildHandler = null;
        }

        if (mHandlerThread != null) {
            JCameraLog.d("Quit handler thread:(" + mHandlerThread.getName()
                    + ":" + mHandlerThread.getId() + ")");
            mHandlerThread.quit();
            mHandlerThread = null;
        }

        mPreSizeMap.clear();
        mPicSizeMap.clear();

        isCameraInUsing.set(false);
        isShowingPreview.set(false);

        if (mCamera != null) {
            mCamera.release();
        }
        mCameraId = DEFAULT_CAMERA_ID;
        mCamera = null;
        mParams = null;
    }

    /**
     * 录像是，从到到低，获取摄像头所支持的最好的设置文件
     *
     * @return CamcorderProfile
     */
    private CamcorderProfile getCamcorderProfile() {
        int cameraId = Camera.CameraInfo.CAMERA_FACING_BACK;
        if (mCameraId == Camera.CameraInfo.CAMERA_FACING_FRONT) {
            cameraId = mCameraId;
        }

        List<Integer> list = new ArrayList<>();
        if (Build.VERSION.SDK_INT >= 21) {
            // list.add(CamcorderProfile.QUALITY_2160P);
        }
        list.add(CamcorderProfile.QUALITY_1080P);
        list.add(CamcorderProfile.QUALITY_720P);
        list.add(CamcorderProfile.QUALITY_480P);
        list.add(CamcorderProfile.QUALITY_HIGH);
        list.add(CamcorderProfile.QUALITY_LOW);

        for (int i = 0; i < list.size(); i++) {
            int quality = list.get(i);
            if (CamcorderProfile.hasProfile(cameraId, quality)) {
                return CamcorderProfile.get(cameraId, quality);
            }
        }

        return CamcorderProfile.get(cameraId, CamcorderProfile.QUALITY_HIGH);
    }

    /**
     * 将CamcorderProfile里面的常量拼接成String
     *
     * @param profile CamcorderProfile
     * @return String
     */
    private String getCamcorderProfileStr(CamcorderProfile profile) {
        if (profile == null) {
            return "";
        }
        String msg = "duration=" + profile.duration +
                ", quality=" + profile.quality +
                ", fileFormat=" + profile.fileFormat +
                ", videoCodec=" + profile.videoCodec +
                ", videoBitRate=" + profile.videoBitRate +
                ", videoFrameRate=" + profile.videoFrameRate +
                ", videoWidth=" + profile.videoFrameWidth +
                ", videoHeight=" + profile.videoFrameHeight +
                ", audioCodec=" + profile.audioCodec +
                ", audioBitRate=" + profile.audioBitRate +
                ", audioSampleRate=" + profile.audioSampleRate +
                ", audioChannels=" + profile.audioChannels;

        return "profile{" + msg + "}";
    }

    /**
     * 获取Camera String描述
     *
     * @param cameraId Camera Id
     * @return
     */
    private String getCameraStr(int cameraId) {
        if (cameraId == Camera.CameraInfo.CAMERA_FACING_BACK) {
            return "Camera(back)";

        }
        if (cameraId == Camera.CameraInfo.CAMERA_FACING_FRONT) {
            return "Camera(front)";
        }

        return "Camera(" + cameraId + ")";
    }

    /**
     * 获取当前view所在的Activity
     *
     * @return Activity or null
     */
    @Nullable
    private Activity getActivity() {
        if (mContext instanceof Activity) {
            return (Activity) mContext;
        }

        if (mContext instanceof ContextWrapper) {
            Context c = ((ContextWrapper) mContext).getBaseContext();
            if (c instanceof Activity) {

                return (Activity) c;
            }
        }
        return null;
    }

    /**
     * 计算出用户点击屏幕后需要聚焦的区域<br>
     * 注：摄像头的区域聚焦矩形是{-1000,-1000,1000,1000}，我们所点击的(x,y)，是在{0,0,屏宽,屏高}坐标系中<br>
     * 所以我们需要将(x,y)所在的坐标系换算成{-1000,-1000,1000,1000,}坐标系
     *
     * @param x
     * @param y
     * @return
     */
    private Rect calculateTapArea(float x, float y) {
        float focusAreaSize = iMaskView.getFocusAreaRadius();
        final int baseBound = 1000;

        //通过比例和平移，重新计算x的值
        if (iMaskView.getViewWidth() != 0) {
            x = x / iMaskView.getViewWidth() * (2 * baseBound);
        }
        x = x - baseBound;

        //通过比例和平移，重新计算y的值
        if (iMaskView.getViewHeight() != 0) {
            y = y / iMaskView.getViewHeight() * (2 * baseBound);
        }
        y = y - baseBound;//平移

        int left = (int) (x - focusAreaSize / 2);
        int top = (int) (y - focusAreaSize / 2);
        int right = (int) (x + focusAreaSize / 2);
        int bottom = (int) (y + focusAreaSize / 2);

        left = clamp(left, -baseBound, baseBound);
        top = clamp(top, -baseBound, baseBound);
        right = clamp(right, -baseBound, baseBound);
        bottom = clamp(bottom, -baseBound, baseBound);

        Rect rect = new Rect(left, top, right, bottom);
        return rect;
    }

    //不大于最大值，不小于最小值
    private int clamp(int x, int min, int max) {
        if (x > max) {
            return max;
        }
        if (x < min) {
            return min;
        }
        return x;
    }

    /**
     * 聚焦回调
     */
    private Camera.AutoFocusCallback autoFocusCallback = new Camera.AutoFocusCallback() {
        @Override
        public void onAutoFocus(boolean success, Camera camera) {
            JCameraLog.d("onFocus, success:" + success);
            if (success) {
                mChildHandler.removeCallbacks(autoFocusRunnable);

                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        iMaskView.clearAnimation();
                    }
                });

            } else {
                mChildHandler.postDelayed(autoFocusRunnable, 1000);
            }
        }
    };

    /**
     * 自动聚焦Runnable，用于不断执行聚焦动作
     */
    Runnable autoFocusRunnable = new Runnable() {
        @Override
        public void run() {
            startAutoFocus();
        }
    };
}
