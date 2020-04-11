package com.ttsea.jcamera.core;

import android.annotation.TargetApi;
import android.app.Activity;
import android.content.Context;
import android.content.ContextWrapper;
import android.graphics.ImageFormat;
import android.graphics.SurfaceTexture;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CaptureFailure;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.CaptureResult;
import android.hardware.camera2.TotalCaptureResult;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.media.ImageReader;
import android.os.Handler;
import android.os.HandlerThread;
import android.util.SparseIntArray;
import android.view.Surface;
import android.view.SurfaceHolder;

import com.ttsea.jcamera.annotation.Facing;
import com.ttsea.jcamera.annotation.Flash;
import com.ttsea.jcamera.callbacks.CameraCallback;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.SortedSet;
import java.util.concurrent.atomic.AtomicBoolean;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

@TargetApi(21)
class Camera2 extends BaseCamera {
    private final int DEFAULT_FACING = -1;
    private final AspectRatio DEFAULT_RATIO = AspectRatio.parse("16:9");

    private Context mContext;
    private final CameraManager mManager;
    private CameraCharacteristics mCharacter;
    private CameraDevice mCamera;
    private CameraCaptureSession mSession;
    private CaptureRequest.Builder mPreviewRequest;
    private ImageReader mImageReader;

    private ISurface iSurface;//对应的SurfaceView
    private IMaskView iMaskView;//SurfaceViews上面的view
    private CameraCallback mCallback;//相机回调

    private Handler mChildHandler;
    private HandlerThread mHandlerThread;

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
        FACING_MAP.put(Constants.FACING_BACK, CameraCharacteristics.LENS_FACING_BACK);
        FACING_MAP.put(Constants.FACING_FRONT, CameraCharacteristics.LENS_FACING_FRONT);
    }

    private static final SparseIntArray FLASH_MODES = new SparseIntArray();

    static {
        FLASH_MODES.put(Constants.FLASH_OFF, CameraCharacteristics.CONTROL_AE_MODE_OFF);
        FLASH_MODES.put(Constants.FLASH_ON, CameraCharacteristics.CONTROL_AE_MODE_ON);
        FLASH_MODES.put(Constants.FLASH_AUTO, CameraCharacteristics.CONTROL_AE_MODE_ON_AUTO_FLASH);
        FLASH_MODES.put(Constants.FLASH_RED_EYE, CameraCharacteristics.CONTROL_AE_MODE_ON_AUTO_FLASH_REDEYE);
        FLASH_MODES.put(Constants.FLASH_ON_ALWAYS, CameraCharacteristics.CONTROL_AE_MODE_ON_ALWAYS_FLASH);
        //FLASH_MODES.put(Constants.FLASH_ON_EXTERNAL, CameraCharacteristics.CONTROL_AE_MODE_ON_EXTERNAL_FLASH);
    }

    public Camera2(Context context, ISurface iSurface, IMaskView iMaskView) {
        super(context);

        this.mContext = context;
        this.iSurface = iSurface;
        this.iMaskView = iMaskView;

        mManager = (CameraManager) mContext.getSystemService(Context.CAMERA_SERVICE);
    }

    @Override
    public void setCameraCallback(CameraCallback callback) {
        mCallback = callback;
    }

    @Override
    public void onSurfaceCreated() {
        if (mCamera == null) {
            return;
        }

        createSession();
    }

    @Override
    public void openCamera() {
        openCamera(Constants.FACING_BACK);
    }

    @Override
    public void openCamera(@Facing final int facing) {
        if (mHandlerThread == null) {
            mHandlerThread = new HandlerThread("Camera2");
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
     * 在子线程中开启摄像头<br>
     */
    @SuppressWarnings("all")
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
            JCameraLog.e(errorMsg);
            resetStatus();
            quitHandlerThread();
            return;
        }

        if (isCameraInUsing.get()) {
            JCameraLog.d(getCameraStr(mCamera) + " is in using, can not open another camera.");
            return;
        }

        //通过facing找到对应的cameraId
        String cameraId = null;
        try {
            String[] ids = mManager.getCameraIdList();
            for (String id : ids) {
                CameraCharacteristics characteristics = mManager.getCameraCharacteristics(id);
                Integer internalFacing = characteristics.get(CameraCharacteristics.LENS_FACING);
                //找到朝向与想要打开的Camera朝向一样的摄像头
                if (internalFacing != null && FACING_MAP.get(facing, DEFAULT_FACING) == internalFacing) {
                    cameraId = id;
                    break;
                }
            }

            //遍历完后还是没有找到对应的camera，则尝试取第一个（且忽略facing）
            if (Utils.isEmpty(cameraId) && ids.length > 0) {
                cameraId = ids[0];
            }

        } catch (Exception e) {
            final String errorMsg = "Exception e:" + e.getMessage();
            JCameraLog.e(errorMsg);
            e.printStackTrace();

            resetStatus();
            quitHandlerThread();

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

        //表示要打开的摄像头，已经打开
        if (mCamera != null && mCamera.getId().equals(cameraId)) {
            JCameraLog.d(getCameraStr(mCamera) + " already opened...");
            return;
        }

        //如果到这里还是没有找到对应的camera，则表示打开失败
        if (Utils.isEmpty(cameraId)) {
            final String errorMsg = "No camera in face:" + facing;
            JCameraLog.e(errorMsg);

            resetStatus();
            quitHandlerThread();

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

        //释放已经打开的摄像头
        if (mCamera != null) {
            resetStatus();
        }

        try {
            mCharacter = mManager.getCameraCharacteristics(cameraId);

            StreamConfigurationMap map = mCharacter.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
            if (map != null) {
                //记录该摄像头所支持的预览尺寸
                mPreSizeMap.clear();

                Class<?> outputClass = iSurface.getClass();
                if (iSurface instanceof SurfaceViewPreview) {
                    outputClass = SurfaceHolder.class;
                }

                if (iSurface instanceof SurfaceTexturePreview) {
                    outputClass = SurfaceTexture.class;
                }

                if (outputClass != SurfaceHolder.class && outputClass != SurfaceTexture.class) {
                    if (mCallback != null) {
                        final String errorMsg = "iSurface must instanceof SurfaceViewPreview or " +
                                "SurfaceTexturePreview, iSurface:" + iSurface;
                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                mCallback.onCameraError(CameraCallback.CODE_CONFIG_SIZE_FAILED, errorMsg);
                            }
                        });
                    }
                    return;
                }

                android.util.Size[] sizes = map.getOutputSizes(outputClass);
                if (sizes != null) {
                    for (android.util.Size size : sizes) {
                        mPreSizeMap.addSize(new Size(size.getWidth(), size.getHeight()));
                    }
                }

                //记录该摄像头所支持的图片尺寸
                mPicSizeMap.clear();
                sizes = map.getOutputSizes(ImageFormat.JPEG);
                if (sizes != null) {
                    for (android.util.Size size : sizes) {
                        mPicSizeMap.addSize(new Size(size.getWidth(), size.getHeight()));
                    }
                }
            }

            if (mPreSizeMap.isEmpty() || mPicSizeMap.isEmpty()) {
                if (mCallback != null) {
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            mCallback.onCameraError(CameraCallback.CODE_CONFIG_SIZE_FAILED,
                                    "mPreSizeMap or mPicSizeMap is null");
                        }
                    });
                }
                return;
            }

            mManager.openCamera(cameraId, mDeviceStateCallback, mChildHandler);

            registerSensor();

        } catch (Exception e) {
            final String errorMsg = "Exception e:" + e.getMessage();
            JCameraLog.e(errorMsg);
            e.printStackTrace();

            resetStatus();
            quitHandlerThread();

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
    }

    @Override
    public void releaseCamera() {
        if (mCamera == null) {
            return;
        }

        unregisterSensor();
        resetStatus();
        quitHandlerThread();
    }

    @Override
    protected void startAutoFocus() {

    }

    @Override
    public void startPreview() {
        startPreview(true);
    }

    private void startPreview(boolean startAutoFocus) {
        if (mSession == null) {
            return;
        }

        try {
            JCameraLog.d("startPreview...");
            mSession.setRepeatingRequest(mPreviewRequest.build(), mCaptureCallback, mChildHandler);
            //开始预览，开始自动聚焦
            isShowingPreview.set(true);

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

        } catch (Exception e) {
            isShowingPreview.set(false);

            final String errorMsg = e.getMessage();
            JCameraLog.e("startPreview error, " + e.getClass() + ":" + errorMsg);

            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    if (mCallback != null) {
                        mCallback.onCameraError(CameraCallback.CODE_START_PREVIEW_FAILED, errorMsg);
                    }
                }
            });
        }
    }

    @Override
    public void stopPreview() {

    }

    /**
     * 创建session
     */
    private void createSession() {
        if (mCamera == null || !iSurface.isReady()) {
            return;
        }
        try {
            if (mSession == null) {
                if (mImageReader != null) {
                    mImageReader.close();
                }
                Size largest = mPicSizeMap.get(getAspectRatio()).last();
                mImageReader = ImageReader.newInstance(largest.width, largest.height, ImageFormat.JPEG, 2);
                mImageReader.setOnImageAvailableListener(mOnImageAvailableListener, mChildHandler);

                if (mPreviewRequest == null) {
                    mPreviewRequest = mCamera.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
                    mPreviewRequest.addTarget(iSurface.getSurface());
                }

                adjustCameraParams();

                mCamera.createCaptureSession(Arrays.asList(iSurface.getSurface(),
                        mImageReader.getSurface()), mSessionCallback, mChildHandler);
            } else {
                startPreview();
            }

        } catch (Exception e) {
            final String errorMsg = "Create capture session failed, e:" + e.getMessage();
            JCameraLog.e(errorMsg);
            e.printStackTrace();

            releaseCamera();

            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    if (mCallback != null) {
                        mCallback.onCameraError(CameraCallback.CODE_START_PREVIEW_FAILED, errorMsg);
                    }
                }
            });

            return;
        }
    }

    @Override
    public int getFacing() {
        if (mCharacter == null) {
            return Constants.FACING_BACK;
        }

        Integer facing = mCharacter.get(CameraCharacteristics.LENS_FACING);
        if (facing != null && facing == CameraCharacteristics.LENS_FACING_FRONT) {
            return Constants.FACING_FRONT;
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
        if (ratio.equals(getAspectRatio())) {
            return true;
        }

        Set<AspectRatio> ratios = getSupportedAspectRatios();
        if (!ratios.contains(ratio)) {
            JCameraLog.w("Unsupported ratio:" + ratio);
            return false;
        }

        SortedSet<Size> preSizes = mPreSizeMap.get(ratio);
        Size preSize = findPreviewSize(preSizes);

        SortedSet<Size> picSizes = mPicSizeMap.get(ratio);
        Size picSize = picSizes.last();

//        mParams.setPreviewSize(preSize.width, preSize.height);
//        mParams.setPictureSize(picSize.width, picSize.height);
//        JCameraLog.d("setPreviewSize:" + preSize + ", setPictureSize:" + picSize);
//
//        updateCameraParams(true);
//        startAutoFocus();
        return true;
    }

    @Override
    public AspectRatio getAspectRatio() {
//        if (mCharacter == null) {
//            return DEFAULT_RATIO;
//        }
//
//        Camera.Size size = mParams.getPreviewSize();
//        if (size == null || size.width <= 0 || size.height <= 0) {
//            return DEFAULT_RATIO;
//        }
//
//        return AspectRatio.of(size.width, size.height);

        return DEFAULT_RATIO;
    }

    @Override
    public void onActivityRotation(int rotation) {

    }

    @Override
    public List<Integer> getSupportedFlashModes() {
        List<Integer> list = new ArrayList<>();
        if (mCharacter == null) {
            return list;
        }

        //获取所有闪光灯模式
        int[] modes = mCharacter.get(CameraCharacteristics.CONTROL_AE_AVAILABLE_MODES);

        if (modes == null || modes.length == 0) {
            return list;
        }

        for (int i = 0; i < modes.length; i++) {
            int mode = modes[i];

            int index = FLASH_MODES.indexOfValue(mode);
            if (index >= 0 && index < FLASH_MODES.size()) {
                int key = FLASH_MODES.keyAt(index);
                list.add(key);
            }
        }

        return list;
    }

    @Override
    public int getFlashMode() {
        int flash = Constants.FLASH_OFF;

        if (mPreviewRequest == null) {
            return flash;
        }

        Integer mode = mPreviewRequest.build().get(CaptureRequest.CONTROL_AE_MODE);

        if (mode == null) {
            return flash;
        }

        int index = FLASH_MODES.indexOfValue(mode);
        if (index >= 0 && index < FLASH_MODES.size()) {
            flash = FLASH_MODES.keyAt(index);
        }

        return flash;
    }

    @Override
    public boolean setFlashMode(@Flash int flash) {
        if (mPreviewRequest == null || mSession == null) {
            return false;
        }

        if (getFlashMode() == flash) {
            return true;
        }

        List<Integer> list = getSupportedFlashModes();

        if (list.contains(flash)) {
            try {
                mPreviewRequest.set(CaptureRequest.CONTROL_AE_MODE, FLASH_MODES.get(flash));
                mSession.setRepeatingRequest(mPreviewRequest.build(), null, mChildHandler);

                JCameraLog.d("setFlashMode:" + getFlashStr(flash));

                return true;

            } catch (Exception e) {
                JCameraLog.e("Exception e:" + e.getMessage());
                e.printStackTrace();
            }

        } else {
            JCameraLog.w("Unsupported the flash mode:" + FLASH_MODES.get(flash));
        }

        return false;
    }

    @Override
    public int getNumberOfCameras() {
        String[] ids = null;
        try {
            ids = mManager.getCameraIdList();
        } catch (CameraAccessException e) {
            JCameraLog.e("CameraAccessException e:" + e.getMessage());
            e.printStackTrace();
        }

        return ids == null ? 0 : ids.length;
    }

    @Override
    public void takePhoto(File outputFile) {

    }

    @Override
    public void startRecord(File outputFile) {

    }

    @Override
    public void stopRecord() {

    }

    @Override
    public boolean onSurfaceTapped(float x, float y) {
        return false;
    }

    @Override
    public boolean zoomIn(float value) {
        return false;
    }

    @Override
    public boolean zoomOut(float value) {
        return false;
    }

    @Override
    public void setOneShotPreview() {

    }

    /**
     * 适配相机参数
     */
    private void adjustCameraParams() {
        if (mCamera == null) {
            return;
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
     * 重设一些常量的状态
     */
    private void resetStatus() {
        if (mChildHandler != null) {
            mChildHandler.removeCallbacksAndMessages(null);
        }

        mPreSizeMap.clear();
        mPicSizeMap.clear();

        isCameraInUsing.set(false);
        isShowingPreview.set(false);

        if (mSession != null) {
            mSession.close();
            mSession = null;
        }
        if (mImageReader != null) {
            mImageReader.close();
            mImageReader = null;
        }
        if (mCamera != null) {
            JCameraLog.d("release " + getCameraStr(mCamera));
            mCamera.close();
            mCamera = null;
        }

        mCharacter = null;
        mPreviewRequest = null;
    }

    /**
     * 退出HandlerThread，只有在{@link #releaseCamera()}或者发生异常的时候才调用<br>
     * 注：在退出HandlerThread之前，一般都要调用{@link #resetStatus()}重置其他常量的状态
     */
    private void quitHandlerThread() {
        if (mChildHandler != null) {
            mChildHandler.removeCallbacksAndMessages(null);
            mChildHandler = null;
        }

        if (mHandlerThread != null) {
            JCameraLog.d("Quit handler thread:(" + mHandlerThread.getName()
                    + ":" + mHandlerThread.getId() + ")");
            mHandlerThread.quitSafely();
            mHandlerThread = null;
        }
    }

    /**
     * 获取Camera String描述
     *
     * @param camera
     * @return
     */
    private String getCameraStr(CameraDevice camera) {
        String des = camera == null ? "" : camera.getId();

        if (des.equals("1")) {
            des = "FRONT";
        } else if (des.equals("0")) {
            des = "BACK";
        }

        return "Camera(" + des + ")";
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

    //////////////////////////////////////////////////////////////////////////////////////////////
    // ---------------------------- 以下是Camera2对应的一些回调 ----------------------------
    //////////////////////////////////////////////////////////////////////////////////////////////

    /** 打开摄像头回调 */
    CameraDevice.StateCallback mDeviceStateCallback = new CameraDevice.StateCallback() {
        @Override
        public void onOpened(@NonNull CameraDevice camera) {
            mCamera = camera;

            JCameraLog.d("Opened camera, " + getCameraStr(camera) + "\n"
                    + "mPreSizeMap:" + mPreSizeMap + "\n"
                    + "mPicSizeMap:" + mPicSizeMap);

            createSession();

            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    if (mCallback != null) {
                        mCallback.onCameraOpened();
                    }
                }
            });
        }

        @Override
        public void onDisconnected(@NonNull CameraDevice camera) {
            JCameraLog.w(getCameraStr(camera) + " is disconnected...");

            releaseCamera();
        }

        @Override
        public void onClosed(@NonNull CameraDevice camera) {
            JCameraLog.d(getCameraStr(camera) + " closed.");

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
        public void onError(@NonNull CameraDevice camera, final int error) {
            JCameraLog.e("onError " + getCameraStr(camera) + ", errorCode:" + error);

            resetStatus();
            quitHandlerThread();

            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    if (mCallback != null) {
                        mCallback.onCameraError(CameraCallback.CODE_OPEN_FAILED, "errorCode:" + error);
                    }
                }
            });
        }
    };

    /** 创建CaptureSession回调 */
    private final CameraCaptureSession.StateCallback mSessionCallback = new CameraCaptureSession.StateCallback() {

        @Override
        public void onConfigured(@NonNull CameraCaptureSession session) {
            JCameraLog.d("on session configured...");
            mSession = session;
            startPreview();
        }

        @Override
        public void onConfigureFailed(@NonNull CameraCaptureSession session) {
            JCameraLog.w("on session configure failed...");
        }

        @Override
        public void onClosed(@NonNull CameraCaptureSession session) {
            JCameraLog.d("on session closed...");
            if (mSession != null && mSession.equals(session)) {
                mSession = null;
            }
        }
    };

    /** 拍照回调 */
    private CameraCaptureSession.CaptureCallback mCaptureCallback = new CameraCaptureSession.CaptureCallback() {
        @Override
        public void onCaptureStarted(@NonNull CameraCaptureSession session, @NonNull CaptureRequest request, long timestamp, long frameNumber) {
            super.onCaptureStarted(session, request, timestamp, frameNumber);
        }

        @Override
        public void onCaptureProgressed(@NonNull CameraCaptureSession session, @NonNull CaptureRequest request, @NonNull CaptureResult partialResult) {
            super.onCaptureProgressed(session, request, partialResult);
        }

        @Override
        public void onCaptureCompleted(@NonNull CameraCaptureSession session, @NonNull CaptureRequest request, @NonNull TotalCaptureResult result) {
            super.onCaptureCompleted(session, request, result);
        }

        @Override
        public void onCaptureFailed(@NonNull CameraCaptureSession session, @NonNull CaptureRequest request, @NonNull CaptureFailure failure) {
            super.onCaptureFailed(session, request, failure);
        }

        @Override
        public void onCaptureSequenceCompleted(@NonNull CameraCaptureSession session, int sequenceId, long frameNumber) {
            super.onCaptureSequenceCompleted(session, sequenceId, frameNumber);
        }

        @Override
        public void onCaptureSequenceAborted(@NonNull CameraCaptureSession session, int sequenceId) {
            super.onCaptureSequenceAborted(session, sequenceId);
        }

        @Override
        public void onCaptureBufferLost(@NonNull CameraCaptureSession session, @NonNull CaptureRequest request, @NonNull Surface target, long frameNumber) {
            super.onCaptureBufferLost(session, request, target, frameNumber);
        }
    };

    private ImageReader.OnImageAvailableListener mOnImageAvailableListener = new ImageReader.OnImageAvailableListener() {
        @Override
        public void onImageAvailable(ImageReader reader) {

        }
    };
}