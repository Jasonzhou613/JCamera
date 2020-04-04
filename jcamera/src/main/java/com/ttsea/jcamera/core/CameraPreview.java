package com.ttsea.jcamera.core;

import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Color;
import android.os.Build;
import android.util.AttributeSet;
import android.view.Surface;
import android.view.View;
import android.widget.FrameLayout;

import com.ttsea.jcamera.R;
import com.ttsea.jcamera.annotation.Flash;
import com.ttsea.jcamera.callbacks.CameraCallback;

import java.io.File;
import java.util.List;
import java.util.Set;

import androidx.annotation.Nullable;

public class CameraPreview extends FrameLayout implements CameraCallback {
    private Context mContext;
    private ICamera iCamera;
    private OrientationDetector mOrientationDetector;
    private CameraCallback mCallback;

    /** 当比例变化的时候，view的边框是否跟随比例变化 */
    private boolean adjustBounds;

    /**
     * 开启或者关闭log
     *
     * @param enable true为开启log，false为关闭log
     */
    public static void enableLog(boolean enable) {
        JCameraLog.enableLog(enable);
    }

    public CameraPreview(Context context) {
        this(context, null);
    }

    public CameraPreview(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public CameraPreview(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);

        if (isInEditMode()) {
            return;
        }

        init(context, attrs);
    }

    private void init(Context context, AttributeSet attrs) {
        if (getChildCount() > 0) {
            throw new IllegalArgumentException("CameraPreview could not include child view");
        }

        mContext = context;

//        if (Build.VERSION.SDK_INT >= 23) {
//            iSurface = new SurfaceViewPreview(context);
//        } else {
//            iSurface = new TextureViewPreview(context, this);
//        }
        ISurface iSurface = new SurfaceViewPreview(mContext);

        MaskPicView iMaskView = new MaskPicView(mContext);

        if (Build.VERSION.SDK_INT < 21 || !Utils.cameraSupportHighLevel(mContext)) {
            iCamera = new Camera1(mContext, iSurface, iMaskView);
        } else if (Build.VERSION.SDK_INT < 23) {
            iCamera = new Camera2(mContext, iSurface, iMaskView);
        } else {
            iCamera = new Camera2Api23(mContext, iSurface, iMaskView);
        }

        iCamera.setCameraCallback(this);
        iSurface.setICamera(iCamera);
        iMaskView.setICamera(iCamera);

        mOrientationDetector = new OrientationDetector(getContext()) {
            @Override
            void dispatchOrientationChanged(int rotation) {
                iCamera.onActivityRotation(rotation);
            }
        };

        if (attrs != null) {
            TypedArray a = context.obtainStyledAttributes(attrs, R.styleable.CameraPreview);

            adjustBounds = a.getBoolean(R.styleable.CameraPreview_jAdjustBounds, true);
            boolean canZoom = a.getBoolean(R.styleable.CameraPreview_jCanZoom, true);
            boolean zoomInOnDoubleTap = a.getBoolean(R.styleable.CameraPreview_jZoomInOnDoubleTap, false);
            boolean focusOnTapped = a.getBoolean(R.styleable.CameraPreview_jFocusOnTapped, true);
            boolean showFocusArea = a.getBoolean(R.styleable.CameraPreview_jShowFocusArea, true);
            boolean faceDetect = a.getBoolean(R.styleable.CameraPreview_jFaceDetect, true);

            boolean showGrid = a.getBoolean(R.styleable.CameraPreview_jShowGrid, false);
            int gridStrokeWidth = a.getDimensionPixelOffset(R.styleable.CameraPreview_jGridStrokeWidth, 0);
            int gridStrokeColor = a.getColor(R.styleable.CameraPreview_jGridStrokeColor, Color.parseColor("#A0FFFFFF"));

            int focusAreaRadius = a.getDimensionPixelSize(R.styleable.CameraPreview_jFocusAreaRadius, 0);
            int focusAreaStrokeWidth = a.getDimensionPixelSize(R.styleable.CameraPreview_jFocusAreaStrokeWidth, 0);
            int focusAreaStrokeColor = a.getColor(R.styleable.CameraPreview_jFocusAreaStrokeColor, Color.parseColor("#FFFFFF"));

            int focusCircleRadius = a.getDimensionPixelSize(R.styleable.CameraPreview_jFocusCircleRadius, 0);
            int focusCircleStrokeWidth = a.getDimensionPixelSize(R.styleable.CameraPreview_jFocusCircleStrokeWidth, 0);
            int focusCircleStrokeColor = a.getColor(R.styleable.CameraPreview_jFocusCircleStrokeColor, Color.parseColor("#A0FFFFFF"));

            a.recycle();

            iMaskView.setFocusOnTapped(focusOnTapped);
            iMaskView.setShowFocusArea(showFocusArea);
            iMaskView.setCanZoom(canZoom);
            iMaskView.setZoomInOnDoubleTap(zoomInOnDoubleTap);
            iMaskView.setShowGrid(showGrid);

            if (gridStrokeWidth > 0) {
                iMaskView.setGridStrokeWidth(gridStrokeWidth);
            }
            iMaskView.setGridStrokeColor(gridStrokeColor);

            if (focusAreaRadius > 0) {
                iMaskView.setFocusAreaRadius(focusAreaRadius);
            }
            if (focusAreaStrokeWidth > 0) {
                iMaskView.setFocusAreaStrokeWidth(focusAreaStrokeWidth);
            }
            iMaskView.setFocusAreaStrokeColor(focusAreaStrokeColor);

            if (focusCircleRadius > 0) {
                iMaskView.setFocusCircleRadius(focusCircleRadius);
            }
            if (focusCircleStrokeWidth > 0) {
                iMaskView.setFocusCircleStrokeWidth(focusCircleStrokeWidth);
            }
            iMaskView.setFocusCircleStrokeColor(focusCircleStrokeColor);
        }

        LayoutParams params = new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT);
        addView((View) iSurface, params);
        addView(iMaskView, params);
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        if (!isInEditMode()) {
            mOrientationDetector.enable(Utils.getDisplay(this));
        }
    }

    @Override
    protected void onDetachedFromWindow() {
        if (!isInEditMode()) {
            mOrientationDetector.disable();
        }
        super.onDetachedFromWindow();
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        if (isInEditMode()) {
            super.onMeasure(widthMeasureSpec, heightMeasureSpec);
            return;
        }

        final int widthMode = MeasureSpec.getMode(widthMeasureSpec);
        final int heightMode = MeasureSpec.getMode(heightMeasureSpec);
        int width = MeasureSpec.getSize(widthMeasureSpec);
        int height = MeasureSpec.getSize(heightMeasureSpec);

        if (widthMode == MeasureSpec.UNSPECIFIED || heightMode == MeasureSpec.UNSPECIFIED) {
            super.onMeasure(widthMeasureSpec, heightMeasureSpec);
            width = getMeasuredWidth();
            height = getMeasuredHeight();
        } else {
            setMeasuredDimension(width, height);
        }

        AspectRatio ratio = getAspectRatio();
        int rotation = Utils.getRotation(this);
        if (rotation % Surface.ROTATION_180 == 0) {
            ratio = ratio.inverse();
        }

        if (adjustBounds) {
            height = (int) (width / ratio.toFloat());
        }

        for (int i = 0; i < getChildCount(); i++) {
            View child = getChildAt(i);
            if (child == null || child.getVisibility() == GONE) {
                continue;
            }

            int wMode = MeasureSpec.makeMeasureSpec(width, MeasureSpec.EXACTLY);
            int hMode = MeasureSpec.makeMeasureSpec(height, MeasureSpec.EXACTLY);

            if (height > width / ratio.toFloat()) {
                width = (int) (height * ratio.toFloat());
                wMode = MeasureSpec.makeMeasureSpec(width, MeasureSpec.EXACTLY);

            } else {
                height = (int) (width / ratio.toFloat());
                hMode = MeasureSpec.makeMeasureSpec(height, MeasureSpec.EXACTLY);
            }

            measureChildWithMargins(child, wMode, 0, hMode, 0);
        }
    }

    @Override
    public void onCameraOpened() {
        if (mCallback != null) {
            mCallback.onCameraOpened();
        }
    }

    @Override
    public void onCameraClosed() {
        if (mCallback != null) {
            mCallback.onCameraClosed();
        }
    }

    @Override
    public void onCameraError(int errorCode, String msg) {
        if (mCallback != null) {
            mCallback.onCameraError(errorCode, msg);
        }
    }

    @Override
    public void onStartPreview() {
        if (mCallback != null) {
            mCallback.onStartPreview();
        }
    }

    @Override
    public void onStopPreview() {
        if (mCallback != null) {
            mCallback.onStopPreview();
        }
    }

    @Override
    public void onPictureTaken(@Nullable File picFile, String errorMsg) {
        if (mCallback != null) {
            mCallback.onPictureTaken(picFile, errorMsg);
        }
    }

    @Override
    public void oneShotFrameData(@Nullable byte[] data, int format, int width, int height) {
        if (mCallback != null) {
            mCallback.oneShotFrameData(data, format, width, height);
        }
    }

    @Override
    public void everyFrameData(@Nullable byte[] data, int format, int width, int height) {
        if (mCallback != null) {
            mCallback.everyFrameData(data, format, width, height);
        }
    }

    public void setCameraCallback(CameraCallback callback) {
        mCallback = callback;
    }

    //////////////////////////////////////////////////////////////////////////////////////////////
    // ---------------------------- 以下是setter代码 ----------------------------
    //////////////////////////////////////////////////////////////////////////////////////////////

    /**
     * 设置长宽是否跟随比例的变化而变化
     *
     * @param adjustBounds 是否适应边框
     */
    public void setAdjustBounds(boolean adjustBounds) {
        this.adjustBounds = adjustBounds;
    }

    //////////////////////////////////////////////////////////////////////////////////////////////
    // ---------------------------- 以下代码是间接调用iCamera ----------------------------
    //////////////////////////////////////////////////////////////////////////////////////////////

    /**
     * see {@link ICamera#openCamera()}
     */
    public void openCamera() {
        iCamera.openCamera();
    }

    /**
     * see {@link ICamera#openCamera(int)}
     *
     * @param facing see {@link ICamera#openCamera(int)}
     */
    public void openCamera(int facing) {
        iCamera.openCamera(facing);
    }

    /**
     * see {@link ICamera#releaseCamera()}
     */
    public void releaseCamera() {
        iCamera.releaseCamera();
    }

    /**
     * see {@link ICamera#getFacing()}
     *
     * @return see {@link ICamera#getFacing()}
     */
    public int getFacing() {
        return iCamera.getFacing();
    }

    /**
     * see {@link ICamera#isShowingPreview()}
     *
     * @return see {@link ICamera#isShowingPreview()}
     */
    public boolean isShowingPreview() {
        return iCamera.isShowingPreview();
    }

    /**
     * see {@link ICamera#getSupportedAspectRatios()}
     *
     * @return see {@link ICamera#getSupportedAspectRatios()}
     */
    public Set<AspectRatio> getSupportedAspectRatios() {
        return iCamera.getSupportedAspectRatios();
    }

    /**
     * see {@link ICamera#setAspectRatio(AspectRatio)}
     *
     * @param ratio
     * @return see {@link ICamera#setAspectRatio(AspectRatio)}
     */
    public boolean setAspectRatio(AspectRatio ratio) {
        boolean result = iCamera.setAspectRatio(ratio);
        if (result) {
            requestLayout();
        }
        return result;
    }

    /**
     * see {@link ICamera#getAspectRatio()}
     *
     * @return see {@link ICamera#getAspectRatio()}
     */
    public AspectRatio getAspectRatio() {
        return iCamera.getAspectRatio();
    }

    /**
     * see {@link ICamera#getSupportedFlashModes()}
     *
     * @return {@link ICamera#getSupportedFlashModes()}
     */
    public List<Integer> getSupportedFlashModes() {
        return iCamera.getSupportedFlashModes();
    }

    /**
     * see {@link ICamera#getFlashMode()}
     *
     * @return see {@link ICamera#getFlashMode()}
     */
    public int getFlashMode() {
        return iCamera.getFlashMode();
    }

    /**
     * see {@link ICamera#setFlashMode(int)}
     *
     * @param flash
     * @return see {@link ICamera#setFlashMode(int)}
     */
    public boolean setFlashMode(@Flash int flash) {
        return iCamera.setFlashMode(flash);
    }

    /**
     * see {@link ICamera#getNumberOfCameras()}
     *
     * @return 摄像头数目
     */
    public int getNumberOfCameras() {
        return iCamera.getNumberOfCameras();
    }

    /**
     * see {@link ICamera#takePhoto(File)} ()}
     */
    public void takePhoto(File outputFile) {
        iCamera.takePhoto(outputFile);
    }

    /**
     * see {@link ICamera#startRecord(File)}
     */
    public void startRecord(File outputFile) {
        iCamera.startRecord(outputFile);
    }

    /**
     * see {@link ICamera#stopRecord()}
     */
    public void stopRecord() {
        iCamera.stopRecord();
    }
}
