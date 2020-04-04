package com.ttsea.jcamera.core;

import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Color;
import android.graphics.Rect;
import android.os.Build;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;
import android.widget.FrameLayout;

import com.ttsea.jcamera.R;
import com.ttsea.jcamera.annotation.Flash;
import com.ttsea.jcamera.callbacks.CameraCallback;

import java.io.File;
import java.util.List;

import androidx.annotation.Nullable;

public class CameraScanView extends FrameLayout implements CameraCallback {
    private Context mContext;
    private ICamera iCamera;
    private MaskScanView iMaskView;
    private OrientationDetector mOrientationDetector;
    private CameraCallback mCallback;

    /**
     * 开启或者关闭log
     *
     * @param enable true为开启log，false为关闭log
     */
    public static void enableLog(boolean enable) {
        JCameraLog.enableLog(enable);
    }

    public CameraScanView(Context context) {
        this(context, null);
    }

    public CameraScanView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public CameraScanView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);

        if (isInEditMode()) {
            return;
        }

        init(context, attrs);
    }

    private void init(Context context, AttributeSet attrs) {
        mContext = context;

//        if (Build.VERSION.SDK_INT >= 23) {
//            iSurface = new SurfaceViewPreview(context);
//        } else {
//            iSurface = new TextureViewPreview(context, this);
//        }
        ISurface iSurface = new SurfaceViewPreview(mContext);

        iMaskView = new MaskScanView(mContext);

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
            TypedArray a = context.obtainStyledAttributes(attrs, R.styleable.CameraScanView);

            String ratio = a.getString(R.styleable.CameraScanView_jScanRectRatio);
            int rectMarginLeftRight = a.getDimensionPixelOffset(R.styleable.CameraScanView_jRectMarginLeftRight, Utils.dip2px(mContext, 16));
            int rectMarginTopBottom = a.getDimensionPixelOffset(R.styleable.CameraScanView_jRectMarginTopBottom, Utils.dip2px(mContext, 16));
            int borderColor = a.getColor(R.styleable.CameraScanView_jBorderColor, getDefaultColor());
            int borderHeight = a.getDimensionPixelOffset(R.styleable.CameraScanView_jBorderHeight, Utils.dip2px(mContext, 1));
            int borderLength = a.getDimensionPixelOffset(R.styleable.CameraScanView_jBorderLength, Utils.dip2px(mContext, 20));

            boolean showMask = a.getBoolean(R.styleable.CameraScanView_jShowMask, true);
            int maskColor = a.getColor(R.styleable.CameraScanView_jMaskColor, getDefaultColor());
            int maskHeight = a.getDimensionPixelOffset(R.styleable.CameraScanView_jMaskHeight, Utils.dip2px(mContext, 1));
            int maskDuration = a.getInteger(R.styleable.CameraScanView_jMaskDuration, 3000);
            int maskMarginLeftRight = a.getDimensionPixelOffset(R.styleable.CameraScanView_jMaskMarginLeftRight, Utils.dip2px(mContext, 6));
            int maskMarginTopBottom = a.getDimensionPixelOffset(R.styleable.CameraScanView_jMaskMarginTopBottom, Utils.dip2px(mContext, 6));

            int shadowColor = a.getColor(R.styleable.CameraScanView_jShadowColor, Color.parseColor("#66000000"));

            a.recycle();

            iMaskView.setFocusOnTapped(true);
            iMaskView.setShowFocusArea(false);
            iMaskView.setCanZoom(true);
            iMaskView.setZoomInOnDoubleTap(false);

            if (ratio != null) {
                iMaskView.setScanRectRatio(AspectRatio.parse(ratio));
            }
            iMaskView.setPadding(rectMarginLeftRight, rectMarginTopBottom, rectMarginLeftRight, rectMarginTopBottom);

            iMaskView.setBorderColor(borderColor);
            iMaskView.setBorderHeight(borderHeight);
            iMaskView.setBorderLength(borderLength);

            iMaskView.setShowMask(showMask);
            iMaskView.setMaskColor(maskColor);
            iMaskView.setMaskHeight(maskHeight);
            iMaskView.setMaskDuration(maskDuration);
            iMaskView.setMaskMarginLeftRight(maskMarginLeftRight);
            iMaskView.setMaskMarginTopBottom(maskMarginTopBottom);

            iMaskView.setShadowColor(shadowColor);
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
    public boolean onInterceptTouchEvent(MotionEvent ev) {

        //在扫描区域外的操作一律拦截
        if (!iMaskView.getScanRect().contains((int) ev.getX(), (int) ev.getY())) {
            return true;
        }

        return super.onInterceptTouchEvent(ev);
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
        iMaskView.startMaskAnimation();
        if (mCallback != null) {
            mCallback.onStartPreview();
        }
    }

    @Override
    public void onStopPreview() {
        iMaskView.stopMaskAnimation();
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

    /**
     * 获取默认边框和扫描线的颜色
     *
     * @return
     */
    private int getDefaultColor() {
        return Color.parseColor("#D81B60");
    }

    //////////////////////////////////////////////////////////////////////////////////////////////
    // ---------------------------- 以下代码是间接调用iCamera ----------------------------
    //////////////////////////////////////////////////////////////////////////////////////////////

    /**
     * see {@link ICamera#openCamera(int)}
     */
    public void openCamera() {
        iCamera.openCamera(Constants.FACING_BACK);
    }

    /**
     * see {@link ICamera#releaseCamera()}
     */
    public void releaseCamera() {
        iCamera.releaseCamera();
    }

    /**
     * see {@link ICamera#startPreview()}
     */
    public void startPreview() {
        iCamera.startPreview();
    }

    /**
     * see {@link ICamera#stopPreview()}
     */
    public void stopPreview() {
        iCamera.stopPreview();
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
     * see {@link ICamera#setOneShotPreview()}
     */
    public void setOneShotPreview() {
        iCamera.setOneShotPreview();
    }

    //////////////////////////////////////////////////////////////////////////////////////////////
    // ---------------------------- 以下代码是间接调用iMaskView ----------------------------
    //////////////////////////////////////////////////////////////////////////////////////////////

    /**
     * {@inheritDoc}
     *
     * @return see {@link MaskScanView#getScanRect()}
     */
    public Rect getScanRect() {
        return iMaskView.getScanRect();
    }
}
