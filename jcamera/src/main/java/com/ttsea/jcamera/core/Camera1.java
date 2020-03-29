package com.ttsea.jcamera.core;

import android.app.Activity;
import android.content.Context;
import android.content.ContextWrapper;
import android.graphics.Rect;
import android.hardware.Camera;
import android.os.Handler;
import android.util.SparseIntArray;
import android.view.Surface;

import com.ttsea.jcamera.annotation.Facing;
import com.ttsea.jcamera.callbacks.CameraCallback;

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
    private ISurface iSurface;//对应的SurfaceView
    private IMaskView iMaskView;//SurfaceViews上面的view
    private CameraCallback mCallback;
    private Camera.Parameters mParams;
    private Handler mHandler;

    //记录摄像头所支持的预览size
    private final SizeMap mPicSizeMap = new SizeMap();
    //记录摄像头所支持的图片size
    private final SizeMap mPreSizeMap = new SizeMap();
    //记录是否正在拍照
    private final AtomicBoolean isInCaptureProgress = new AtomicBoolean(false);
    //记录是否正在录像
    private final AtomicBoolean isInRecordInProgress = new AtomicBoolean(false);
    //记录是否正在预览，默认:false
    private boolean isShowingPreview = false;

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

        mHandler = new Handler();
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
    public void openCamera(@Facing int facing) {
        if (!Utils.checkCameraHardware(mContext)) {
            //设备不支持摄像头（或者没有摄像头）
            String errorMsg = "Device has no camera.";
            if (mCallback != null) {
                mCallback.onCameraError(CameraCallback.CODE_NO_CAMERA, errorMsg);
            }
            CameraxLog.d(errorMsg);
            return;
        }

        if (isInCaptureProgress.get() || isInRecordInProgress.get()) {
            CameraxLog.d(getCameraStr(mCameraId) + " is in using, can not open another camera.");
            return;
        }

        int cameraId = FACING_MAP.get(facing, DEFAULT_CAMERA_ID);

        //表示要打开的摄像头，已经打开
        if (mCamera != null && mCameraId == cameraId) {
            CameraxLog.d(getCameraStr(mCameraId) + " already opened...");
            return;
        }

        //释放已经打开的摄像头
        if (mCamera != null) {
            releaseCamera();
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
            String errorMsg = "Exception e:" + e.getMessage() + ", " + getCameraStr(cameraId);
            CameraxLog.e(errorMsg);
            e.printStackTrace();
            mCamera = null;
            mCameraId = DEFAULT_CAMERA_ID;

            if (mCallback != null) {
                mCallback.onCameraError(CameraCallback.CODE_OPEN_FAILED, errorMsg);
            }
            return;
        }

        if (mCamera == null) {
            String errorMsg = "Open camera failed, " + getCameraStr(cameraId);
            CameraxLog.e(errorMsg);
            if (mCallback != null) {
                mCallback.onCameraError(CameraCallback.CODE_OPEN_FAILED, errorMsg);
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

        CameraxLog.d("Opened camera, " + getCameraStr(cameraId) + "\n"
                + "mPreSizeMap:" + mPreSizeMap + "\n"
                + "mPicSizeMap:" + mPicSizeMap);

        registerSensor();
        adjustCameraParams();

        if (setUpPreview()) {
            startPreview();
        }

        if (mCallback != null) {
            mCallback.onCameraOpened();
        }
    }

    @Override
    public void releaseCamera() {
        if (mCamera == null) {
            return;
        }

        CameraxLog.d("release " + getCameraStr(mCameraId));
        unregisterSensor();
        iMaskView.clearAnimation();
        isShowingPreview = false;
        mCamera.release();
        mCamera = null;
        mParams = null;
        mCameraId = DEFAULT_CAMERA_ID;
        if (mCallback != null) {
            mCallback.onCameraClosed();
        }
    }

    @Override
    protected void startAutoFocus() {
        if (mCamera == null) {
            return;
        }

        List<String> list = mParams.getSupportedFocusModes();
        //不支持自动聚焦
        if (list == null || list.isEmpty()) {
            CameraxLog.w(getCameraStr(mCameraId) + " not support focus.");
            return;
        }

        String focusMode = mParams.getFocusMode();
        focusMode = focusMode == null ? "" : focusMode;

        if (focusMode.equals(Camera.Parameters.FOCUS_MODE_INFINITY)
                || focusMode.equals(Camera.Parameters.FOCUS_MODE_FIXED)
                || focusMode.equals(Camera.Parameters.FOCUS_MODE_EDOF)) {
            CameraxLog.d("focusMode:" + focusMode + ", should not call autoFocus.");
            return;
        }

        //先取消正在执行的聚焦动作
        cancelAutoFocus();
        CameraxLog.d("start auto focus...");
        //开始聚焦
        mCamera.autoFocus(autoFocusCallback);
    }

    /**
     * 取消自动聚焦
     */
    private void cancelAutoFocus() {
        if (mCamera == null) {
            return;
        }

        mHandler.removeCallbacks(autoFocusRunnable);
        try {
            mCamera.cancelAutoFocus();
        } catch (Exception e) {
            //ignore error
            CameraxLog.w("Exception e:" + e.getMessage());
        }
    }

    /**
     * 开启预览
     */
    protected void startPreview() {
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

        CameraxLog.d("startPreview...");
        //开始预览，开始自动聚焦
        isShowingPreview = true;
        mCamera.startPreview();
        if (startAutoFocus) {
            startAutoFocus();
        }
    }

    /**
     * 停止预览
     */
    protected void stopPreview() {
        if (mCamera == null) {
            return;
        }

        CameraxLog.d("stopPreview...");
        isShowingPreview = false;
        mCamera.stopPreview();
    }

    @Override
    public int getFacing() {
        int index = FACING_MAP.valueAt(mCameraId);
        if (index > -1) {
            return FACING_MAP.keyAt(index);
        }
        return Constants.FACING_BACK;
    }

    @Override
    public boolean isShowingPreview() {
        return isShowingPreview;
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
        if (ratio.equals(getAspectRatio())) {
            return true;
        }

        Set<AspectRatio> ratios = getSupportedAspectRatios();
        if (!ratios.contains(ratio)) {
            CameraxLog.w(getCameraStr(mCameraId) + " unsupport ratio:" + ratio);
            return false;
        }

        SortedSet<Size> preSizes = mPreSizeMap.get(ratio);
        Size preSize = findPreviewSize(preSizes);

        SortedSet<Size> picSizes = mPicSizeMap.get(ratio);
        Size picSize = picSizes.last();

        mParams.setPreviewSize(preSize.width, preSize.height);
        mParams.setPictureSize(picSize.width, picSize.height);
        CameraxLog.d("setPreviewSize:" + preSize + ", setPictureSize:" + picSize);

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

        int degrees = 0;
        switch (rotation) {
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

        CameraxLog.d("onRotation, rotation:" + rotation + ", degrees:" + degrees);
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

        if (getFlashMode() == flash) {
            return true;
        }

        List<Integer> list = getSupportedFlashModes();

        if (list.contains(flash)) {
            mParams.setFlashMode(FLASH_MODES.get(flash));
            updateCameraParams(false);
            return true;

        } else {
            CameraxLog.w(getCameraStr(mCameraId) + " unsupport the flash mode:" + FLASH_MODES.get(flash));
            return false;
        }
    }

    @Override
    public int getNumberOfCameras() {
        return Camera.getNumberOfCameras();
    }

    @Override
    public void takePhoto() {
        if (mCamera == null || !isShowingPreview) {
            if (mCallback != null) {
                mCallback.onPictureTaken(null);
            }
            return;
        }

        CameraxLog.d("takePhoto...");

        Camera.ShutterCallback shutter = new Camera.ShutterCallback() {
            @Override
            public void onShutter() {
                isInCaptureProgress.set(true);
                //这里可以响应快门声
            }
        };

        Camera.PictureCallback picCallback = new Camera.PictureCallback() {
            @Override
            public void onPictureTaken(byte[] data, Camera camera) {
                //拍完照，会自动停止预览
                isShowingPreview = false;

                startPreview(false);
                isInCaptureProgress.set(false);

                if (mCallback != null) {
                    mCallback.onPictureTaken(data);
                }
            }
        };

        try {
            mCamera.takePicture(shutter, null, picCallback);

        } catch (Exception e) {
            CameraxLog.e("Exception e:" + e.getMessage());
            e.printStackTrace();
            if (mCallback != null) {
                mCallback.onPictureTaken(null);
            }
        }
    }

    @Override
    public void startRecord() {
        isInCaptureProgress.set(true);
        CameraxLog.d("startRecord...");
    }

    @Override
    public void stopRecord() {
        isInCaptureProgress.set(false);
        CameraxLog.d("stopRecord...");
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
        if (mCamera == null || !isShowingPreview) {
            return false;
        }

        //不支持区域聚焦
        if (mParams.getMaxNumFocusAreas() <= 0) {
            CameraxLog.w(getCameraStr(mCameraId) + " not support Area focus.");
            return false;
        }

        List<String> modesList = mParams.getSupportedFocusModes();
        if (modesList == null || modesList.isEmpty()) {
            CameraxLog.w(getCameraStr(mCameraId) + " has no focus mode.");
            return false;
        }

        String focusMode = "";
        if (modesList.contains(Camera.Parameters.FOCUS_MODE_MACRO)) {
            focusMode = Camera.Parameters.FOCUS_MODE_MACRO;

        } else if (modesList.contains(Camera.Parameters.FOCUS_MODE_AUTO)) {
            focusMode = Camera.Parameters.FOCUS_MODE_AUTO;
        }

        if (Utils.isEmpty(focusMode)) {
            CameraxLog.w(getCameraStr(mCameraId) + " not support MACRO/AUTO focus mode.");
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

        CameraxLog.d("start focus areas:" + area.rect);
        //开始聚焦
        mCamera.autoFocus(autoFocusCallback);

        return true;
    }

    @Override
    public boolean zoomIn(float value) {
        //不支持缩放
        if (!mParams.isZoomSupported()) {
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
        //不支持缩放
        if (!mParams.isZoomSupported()) {
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

        CameraxLog.d("adjustCameraParams, ratio:" + ratio
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

        mCamera.setParameters(mParams);

        if (rePreview && isShowingPreview) {
            mCamera.stopPreview();
            mCamera.startPreview();
            CameraxLog.d("updateCameraParams, rePreview:" + true);
        } else {
            CameraxLog.d("updateCameraParams, rePreview:" + false);
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
            CameraxLog.e("IOException e:" + e.getMessage());
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
            CameraxLog.d("onFocus, success:" + success);
            if (success) {
                mHandler.removeCallbacks(autoFocusRunnable);
                iMaskView.clearAnimation();

            } else {
                mHandler.postDelayed(autoFocusRunnable, 1000);
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
