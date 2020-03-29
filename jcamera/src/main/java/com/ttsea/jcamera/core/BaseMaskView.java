package com.ttsea.jcamera.core;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.animation.LinearInterpolator;
import android.widget.FrameLayout;

import androidx.annotation.ColorInt;
import androidx.annotation.NonNull;

/**
 * SurfaceView上面那层view的基类，一些通用的功能会放在这里，如：<br>
 * 1.点击触发区域聚焦<br>
 * 2.双击或手势放大/缩小功能<br>
 */
abstract class BaseMaskView extends FrameLayout implements IMaskView {
    private Context mContext;
    private ICamera iCamera;

    //用户点击后记录下的矩形
    private final RectF tapAreaRect = new RectF();
    //tapAreaRect加弹簧震动时动态生成的矩形
    private final RectF tapAreaTmpRect = new RectF();
    //圆心
    private final RectF centerRect = new RectF();

    /** 区域聚焦被触发时，聚焦区域的动画振幅 */
    private float amplitude = 0;

    private Paint mAreaPaint;
    private Paint mCenterPaint;

    /** 数值产生器，用于区域聚焦动画 */
    private ValueAnimator mAnimator;
    /** 区域聚焦版本 */
    private int focusAreaRadius;
    /** 区域聚焦圆心半径 */
    private int focusCenterRadius;

    /** 是否支持手势放大缩小 */
    private boolean canZoom = true;
    /** 双击屏幕放大，只有在canZoom为true时才生效 */
    private boolean zoomInOnDoubleTap = false;
    /** 是否支持点击屏幕触发区域聚焦 */
    private boolean focusOnTapped = true;
    /** 当点击屏幕触发区域聚焦的时候，是否显示区域聚焦范围和动画，只有当focusOnTapped为true的时候才生效 */
    private boolean showFocusArea = true;

    private GestureDetector mGestureDetector;
    private ScaleGestureDetector mScaleDetector;
    private float mLastScaleFactor;

    private GestureDetector.SimpleOnGestureListener mGestureListener = new GestureDetector.SimpleOnGestureListener() {

        @Override
        public boolean onSingleTapUp(MotionEvent event) {
            if (iCamera == null) {
                return super.onSingleTapUp(event);
            }
            if (focusOnTapped && iCamera.onSurfaceTapped(event.getX(), event.getY())) {
                if (showFocusArea) {
                    startFocusAnimation(event.getX(), event.getY());
                }
                return true;
            }
            return super.onSingleTapUp(event);
        }

        @Override
        public boolean onDown(MotionEvent e) {
            return super.onDown(e);
        }

        @Override
        public boolean onDoubleTap(MotionEvent e) {
            if (iCamera == null) {
                return super.onDoubleTap(e);
            }

            if (canZoom && zoomInOnDoubleTap) {
                return iCamera.zoomIn(-1);
            }

            return super.onDoubleTap(e);
        }
    };

    private ScaleGestureDetector.OnScaleGestureListener mScaleListener = new ScaleGestureDetector.SimpleOnScaleGestureListener() {
        @Override
        public boolean onScale(ScaleGestureDetector detector) {
            if (canZoom) {
                float dx = Math.abs((detector.getScaleFactor() - mLastScaleFactor));
                mLastScaleFactor = detector.getScaleFactor();

                float value = detector.getScaleFactor() - 1.0f;
                if (value > 0) {
                    return iCamera.zoomIn(dx);
                } else if (value < 0) {
                    return iCamera.zoomOut(dx);
                }
            }

            return super.onScale(detector);
        }

        @Override
        public boolean onScaleBegin(ScaleGestureDetector detector) {
            mLastScaleFactor = detector.getScaleFactor();
            return super.onScaleBegin(detector);
        }

        @Override
        public void onScaleEnd(ScaleGestureDetector detector) {
            mLastScaleFactor = 0;
            super.onScaleEnd(detector);
        }
    };

    public BaseMaskView(@NonNull Context context) {
        super(context);

        this.mContext = context;

        init();
        setWillNotDraw(false);
    }

    private void init() {
        mAreaPaint = new Paint();
        mAreaPaint.setColor(Color.parseColor("#FFFFFF"));
        mAreaPaint.setStyle(Paint.Style.STROKE);
        mAreaPaint.setStrokeWidth(Utils.dip2px(mContext, 1));
        mAreaPaint.setAntiAlias(true);

        mCenterPaint = new Paint();
        mCenterPaint.setColor(Color.parseColor("#A0FFFFFF"));
        mCenterPaint.setStyle(Paint.Style.STROKE);
        mCenterPaint.setStrokeWidth(Utils.dip2px(mContext, 2.0f));
        mCenterPaint.setAntiAlias(true);

        amplitude = Utils.dip2px(mContext, 5);
        //动画
        mAnimator = ValueAnimator.ofFloat(1f, 0f, 1f);
        mAnimator.setInterpolator(new LinearInterpolator());
        mAnimator.setRepeatCount(-1);
        mAnimator.setDuration(800);
        mAnimator.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            @Override
            public void onAnimationUpdate(ValueAnimator animation) {
                float offset = ((float) animation.getAnimatedValue()) * amplitude;
                if (!tapAreaRect.isEmpty()) {
                    tapAreaTmpRect.left = tapAreaRect.left - offset;
                    tapAreaTmpRect.right = tapAreaRect.right + offset;
                    tapAreaTmpRect.top = tapAreaRect.top - offset;
                    tapAreaTmpRect.bottom = tapAreaRect.bottom + offset;

                    invalidate();
                }
            }
        });

        mGestureDetector = new GestureDetector(mContext, mGestureListener);
        mScaleDetector = new ScaleGestureDetector(mContext, mScaleListener);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        drawFocusRect(canvas);
    }

    /** 绘制聚焦框 */
    private void drawFocusRect(Canvas canvas) {
        if (!tapAreaRect.isEmpty()) {
            canvas.drawOval(tapAreaTmpRect, mAreaPaint);
            canvas.drawOval(centerRect, mCenterPaint);
        }
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (iCamera != null) {
            if (mGestureDetector.onTouchEvent(event)) {
                return true;
            }

            if (mScaleDetector.onTouchEvent(event)) {
                return true;
            }
        }

        return super.onTouchEvent(event);
    }

    /**
     * 开始聚焦动画
     *
     * @param x 用户点击屏幕的x坐标
     * @param y 用户点击屏幕的y坐标
     */
    private void startFocusAnimation(float x, float y) {
        float focusAreaSize = getFocusAreaRadius();

        //初始化区域聚焦范围
        tapAreaRect.left = x - focusAreaSize / 2;
        tapAreaRect.top = y - focusAreaSize / 2;
        tapAreaRect.right = x + focusAreaSize / 2;
        tapAreaRect.bottom = y + focusAreaSize / 2;

        //初始化区域聚焦圆心
        float ovWidth = getFocusCenterRadius();
        centerRect.left = x - ovWidth;
        centerRect.top = y - ovWidth;
        centerRect.right = x + ovWidth;
        centerRect.bottom = y + ovWidth;

        //启动动画
        mAnimator.start();
    }

    /**
     * 停止聚焦动画
     */
    private void stopFocusAnimation() {
        tapAreaRect.setEmpty();
        mAnimator.cancel();

        invalidate();
    }

    @Override
    public void setICamera(ICamera iCamera) {
        this.iCamera = iCamera;
    }

    @Override
    public void clearAnimation() {
        stopFocusAnimation();
        super.clearAnimation();
    }

    @Override
    public float getFocusAreaRadius() {
        if (focusAreaRadius <= 0) {
            focusAreaRadius = Utils.dip2px(mContext, 68);
        }
        return focusAreaRadius;
    }

    @Override
    public int getViewWidth() {
        return super.getWidth();
    }

    @Override
    public int getViewHeight() {
        return super.getHeight();
    }

    /**
     * 获取区域聚焦圆心的半径
     *
     * @return
     */
    private float getFocusCenterRadius() {
        if (focusCenterRadius <= 0) {
            focusCenterRadius = Utils.dip2px(mContext, 4);
        }
        return focusCenterRadius;
    }

    /**
     * 设置区域聚焦范围<br>
     * 只有在摄像头支持区域聚焦且，开启了区域聚焦才生效
     *
     * @param focusAreaRadius
     */
    protected void setFocusAreaRadius(int focusAreaRadius) {
        this.focusCenterRadius = focusAreaRadius;
    }

    /**
     * 设置区域聚焦边框的粗细
     *
     * @param strokeWidth
     */
    protected void setFocusAreaStrokeWidth(int strokeWidth) {
        if (strokeWidth > 0) {
            mAreaPaint.setStrokeWidth(strokeWidth);
        }
    }

    /**
     * 设置区域聚焦边框颜色
     *
     * @param strokeColor
     */
    protected void setFocusAreaStrokeColor(@ColorInt int strokeColor) {
        mAreaPaint.setColor(strokeColor);
    }


    /**
     * 设置区域聚焦圆心半径
     *
     * @param focusCenterRadius
     */
    protected void setFocusCircleRadius(int focusCenterRadius) {
        this.focusCenterRadius = focusCenterRadius;
    }

    /**
     * 设置区域聚焦圆心边框大小
     *
     * @param strokeWidth
     */
    protected void setFocusCircleStrokeWidth(int strokeWidth) {
        if (strokeWidth > 0) {
            mCenterPaint.setStrokeWidth(strokeWidth);
        }
    }

    /**
     * 设置区域聚焦圆心边框颜色
     *
     * @param strokeColor
     */
    protected void setFocusCircleStrokeColor(@ColorInt int strokeColor) {
        mCenterPaint.setColor(strokeColor);
    }

    /**
     * 点击屏幕是否触发区域聚焦
     *
     * @return true:点击屏幕将触发区域聚焦，false:点击屏幕不会触发区域聚焦
     */
    public boolean isFocusOnTapped() {
        return focusOnTapped;
    }

    /**
     * 设置点击屏幕是否触发区域聚焦
     *
     * @param focusOnTapped
     */
    public void setFocusOnTapped(boolean focusOnTapped) {
        this.focusOnTapped = focusOnTapped;
    }

    /**
     * 当点击屏幕触发区域聚焦的时候，是否显示区域聚焦范围和动画<br>
     * 只有当{@link #isFocusOnTapped()}为true的时候才生效
     *
     * @return true:显示，false:不显示
     */
    public boolean isShowFocusArea() {
        return showFocusArea;
    }

    /**
     * 当点击屏幕触发区域聚焦的时候，是否显示区域聚焦范围和动画<br>
     * 只有当{@link #isFocusOnTapped()}为true的时候才生效
     *
     * @param showFocusArea true:显示，false:不显示
     */
    public void setShowFocusArea(boolean showFocusArea) {
        this.showFocusArea = showFocusArea;
    }

    /**
     * 是否允许缩放
     *
     * @return
     */
    public boolean isCanZoom() {
        return canZoom;
    }

    /**
     * 设置是否允许缩放
     *
     * @param canZoom
     */
    public void setCanZoom(boolean canZoom) {
        this.canZoom = canZoom;
    }

    /**
     * 双击屏幕是否放大
     *
     * @return
     */
    public boolean isZoomInOnDoubleTap() {
        return canZoom && zoomInOnDoubleTap;
    }

    /**
     * 双击屏幕放大，只有在{@link #isCanZoom()}为true时才生效
     *
     * @param zoomInOnDoubleTap
     */
    public void setZoomInOnDoubleTap(boolean zoomInOnDoubleTap) {
        this.zoomInOnDoubleTap = zoomInOnDoubleTap;
    }
}
