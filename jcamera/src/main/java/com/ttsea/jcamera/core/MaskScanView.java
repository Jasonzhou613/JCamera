package com.ttsea.jcamera.core;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.view.View;
import android.view.animation.AlphaAnimation;
import android.view.animation.Animation;
import android.view.animation.LinearInterpolator;

import androidx.annotation.ColorInt;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

class MaskScanView extends BaseMaskView {
    /** 扫描框长宽比，为null的时候表示全屏 */
    private AspectRatio scanRectRatio = null;
    @ColorInt
    private int borderColor;//边框颜色
    private int borderHeight;//边框高度
    private int borderLength;//边框长度

    private boolean showMask = true;//是否显示扫描线
    @ColorInt
    private int maskColor;//扫描线颜色
    private int maskHeight;//扫描线高度
    private int maskMarginLeftRight;//扫描线距离左右两边的距离
    private int maskMarginTopBottom;//扫描线距离上下两边的距离
    private int maskDuration;//扫描动画时长
    private int shadowColor;//扫描框之前的(阴影部分颜色)

    //padding
    private int paddingLeft;
    private int paddingTop;
    private int paddingRight;
    private int paddingBottom;

    private final Rect mScanRect = new Rect();//扫描框
    private View maskView;//扫描线

    private Paint mBorderPaint;
    private Paint mMaskPaint;
    private Paint mShadowPaint;

    private Animation mMaskAnimator;

    public MaskScanView(@NonNull Context context) {
        super(context);

        init();
    }

    private void init() {
        mBorderPaint = new Paint();
        mBorderPaint.setColor(borderColor);
        mBorderPaint.setStyle(Paint.Style.STROKE);
        mBorderPaint.setStrokeWidth(borderHeight);
        mBorderPaint.setAntiAlias(true);

        mMaskPaint = new Paint();
        mMaskPaint.setColor(maskColor);
        mMaskPaint.setStyle(Paint.Style.STROKE);
        mMaskPaint.setStrokeWidth(maskHeight);
        mMaskPaint.setAntiAlias(true);

        mShadowPaint = new Paint();
        mShadowPaint.setColor(shadowColor);
        mShadowPaint.setStyle(Paint.Style.FILL);
        mShadowPaint.setAntiAlias(true);

        paddingLeft = getPaddingLeft();
        paddingTop = getPaddingTop();
        paddingRight = getPaddingRight();
        paddingBottom = getPaddingBottom();
        super.setPadding(0, 0, 0, 0);

        setWillNotDraw(false);
        calcScanRect();
    }

    @Override
    protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        super.onLayout(changed, left, top, right, bottom);

        calcScanRect();
        layoutMask();
    }

    /**
     * 初始化扫描线
     */
    private void layoutMask() {
        int left = mScanRect.left + maskMarginLeftRight;
        int right = mScanRect.right - maskMarginLeftRight;
        int top = mScanRect.top + maskMarginTopBottom;
        int bottom = mScanRect.bottom - maskMarginTopBottom;
        //使扫描线显示在中间
        top = top + ((bottom - top) / 2);

        if (maskView == null) {
            maskView = new View(getContext());

            LayoutParams params = new LayoutParams(right - left, maskHeight);
            addView(maskView, params);
            maskView.layout(left, top, right, top + maskHeight);

        } else {
            LayoutParams params = (LayoutParams) maskView.getLayoutParams();
            params.width = right - left;
            params.height = maskHeight;
            maskView.setLayoutParams(params);
            maskView.layout(left, top, right, top + maskHeight);
        }

        maskView.setBackgroundColor(maskColor);
        maskView.setVisibility(showMask ? VISIBLE : GONE);

        //mMaskAnimator = new TranslateAnimation(0, 0, top - bottom, bottom - top);
        mMaskAnimator = new AlphaAnimation(1.0f, 0.2f);
        mMaskAnimator.setRepeatCount(-1);
        mMaskAnimator.setDuration(maskDuration);
        mMaskAnimator.setInterpolator(new LinearInterpolator());
        mMaskAnimator.setFillEnabled(true);
        mMaskAnimator.setFillAfter(true);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        //这里要谨慎打印log，打印log会导致频繁GC，从而影响性能
        if (getScanRectRatioFloat() != 0) {
            drawShadow(canvas);
            drawBoarder(canvas);
        }
    }

    /**
     * 计算扫描框
     */
    private void calcScanRect() {
        int left = getLeft() + paddingLeft;
        int top = getTop() + paddingTop;
        int right = getRight() - paddingRight;
        int bottom = getBottom() - paddingBottom;

        float ratio = getScanRectRatioFloat();
//        if (ratio > 0 && Utils.getDisplay(this) != null) {
//            int rotation = Utils.getDisplay(this).getRotation();
//            if (rotation % Surface.ROTATION_180 != 0) {
//                ratio = 1 / ratio;
//            }
//        }

        if (ratio <= 0) {
            left = getLeft();
            top = getTop();
            right = getRight();
            bottom = getBottom();
        }

        int width = right - left;
        int height = bottom - top;

        if (((float) width / height) > ratio) {
            int newWidth = (int) (height * ratio);
            int widthOffset = width < newWidth ? 0 : ((width - newWidth) / 2);
            left = left + widthOffset;
            right = right - widthOffset;

        } else {
            int newHeight = (int) (width / ratio);
            int heightOffset = (height - newHeight) / 2;
            top = top + heightOffset;
            bottom = bottom - heightOffset;
        }

        mScanRect.left = left;
        mScanRect.top = top;
        mScanRect.right = right;
        mScanRect.bottom = bottom;

        JCameraLog.d("mScanRect:" + mScanRect);
    }

    /**
     * 绘制边框之外的阴影
     *
     * @param canvas
     */
    private void drawShadow(Canvas canvas) {
        //top rect
        canvas.drawRect(getLeft(), getTop(), getRight(), mScanRect.top, mShadowPaint);

        //bottom rect
        canvas.drawRect(getLeft(), mScanRect.bottom, getRight(), getBottom(), mShadowPaint);

        //left rect
        canvas.drawRect(getLeft(), mScanRect.top, mScanRect.left, mScanRect.bottom, mShadowPaint);

        //right rect
        canvas.drawRect(mScanRect.right, mScanRect.top, getRight(), mScanRect.bottom, mShadowPaint);
    }

    /**
     * 绘制四个角的边框
     *
     * @param canvas
     */
    private void drawBoarder(Canvas canvas) {
        int offset = maskHeight / 2;//这个offset是用来填充角之间的空隙
        // Top-left corner
        canvas.drawLine(mScanRect.left - offset, mScanRect.top, mScanRect.left + borderLength - offset, mScanRect.top, mBorderPaint);
        canvas.drawLine(mScanRect.left, mScanRect.top - offset, mScanRect.left, mScanRect.top + borderLength - offset, mBorderPaint);

        // Top-right corner
        canvas.drawLine(mScanRect.right - borderLength + offset, mScanRect.top, mScanRect.right + offset, mScanRect.top, mBorderPaint);
        canvas.drawLine(mScanRect.right, mScanRect.top - offset, mScanRect.right, mScanRect.top + borderLength - offset, mBorderPaint);

        // Bottom-left corner
        canvas.drawLine(mScanRect.left - offset, mScanRect.bottom, mScanRect.left + borderLength - offset, mScanRect.bottom, mBorderPaint);
        canvas.drawLine(mScanRect.left, mScanRect.bottom - borderLength + offset, mScanRect.left, mScanRect.bottom + offset, mBorderPaint);

        // Bottom-right corner
        canvas.drawLine(mScanRect.right - borderLength + offset, mScanRect.bottom, mScanRect.right + offset, mScanRect.bottom, mBorderPaint);
        canvas.drawLine(mScanRect.right, mScanRect.bottom - borderLength + offset, mScanRect.right, mScanRect.bottom + offset, mBorderPaint);
    }

    /**
     * 启动扫描线动画，如果已经启动，则忽略
     */
    public void startMaskAnimation() {
        if (!showMask || isAnimatorRunning()) {
            return;
        }
        maskView.startAnimation(mMaskAnimator);
    }

    /**
     * 启动扫描线动画，如果已经启动则项停止，在启动
     */
    public void reStartMaskAnimation() {
        if (isAnimatorRunning()) {
            stopMaskAnimation();
        }
        startMaskAnimation();
    }

    /**
     * 停止扫描线动画
     */
    public void stopMaskAnimation() {
        if (maskView != null) {
            maskView.clearAnimation();
        }
        if (mMaskAnimator != null) {
            mMaskAnimator.cancel();
        }
    }

    public boolean isAnimatorRunning() {
        return mMaskAnimator != null && mMaskAnimator.hasStarted() && !mMaskAnimator.hasEnded();
    }

    public Rect getScanRect() {
        return mScanRect;
    }

    @Nullable
    public View getMaskView() {
        return maskView;
    }

    public boolean isShowMask() {
        return showMask;
    }

    /**
     * 得到float类型的比例值，如果scanRectRatio为null则返回0，表示全屏
     *
     * @return float比例值
     */
    private float getScanRectRatioFloat() {
        if (scanRectRatio == null) {
            return 0;
        }

        return scanRectRatio.toFloat();
    }

    //////////////////////////////////////////////////////////////////////////////////////////////
    // ---------------------------- 以下是 setter 代码 ----------------------------
    //////////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public void setPadding(int left, int top, int right, int bottom) {
        paddingLeft = left;
        paddingTop = top;
        paddingRight = right;
        paddingBottom = bottom;
        super.setPadding(0, 0, 0, 0);
    }

    /**
     * 设置扫描框的长宽比，null的时候表示全屏
     *
     * @param ratio 长宽比，可以为null
     */
    public void setScanRectRatio(AspectRatio ratio) {
        setScanRectRatio(ratio, false);
    }

    /**
     * 设置扫描框的长宽比，null的时候表示全屏
     *
     * @param ratio 长宽比，可以为null
     * @param force true:即使当前比例与将要设置的比例相同，也会重新设置; false:当前比例与将要设置的比例相同，则不会再设置
     */
    public void setScanRectRatio(AspectRatio ratio, boolean force) {
        if (!force) {
            float r = ratio == null ? 0 : ratio.toFloat();
            if (r == getScanRectRatioFloat()) {
                return;
            }
        }

        //记录是否正在扫描
        final boolean isAnimatorRunning = isAnimatorRunning();
        stopMaskAnimation();

        this.scanRectRatio = ratio;

        calcScanRect();
        layoutMask();

        if (isAnimatorRunning) {
            postDelayed(new Runnable() {
                @Override
                public void run() {
                    startMaskAnimation();
                }
            }, 100);
        }
    }

    public void setBorderColor(int borderColor) {
        this.borderColor = borderColor;
        if (mBorderPaint != null) {
            mBorderPaint.setColor(borderColor);
        }
    }

    public void setBorderHeight(int borderHeight) {
        this.borderHeight = borderHeight;
        if (mBorderPaint != null) {
            mBorderPaint.setStrokeWidth(borderHeight);
        }
    }

    public void setBorderLength(int borderLength) {
        this.borderLength = borderLength;
    }

    public void setShowMask(boolean showMask) {
        this.showMask = showMask;
    }

    public void setMaskColor(int maskColor) {
        this.maskColor = maskColor;
        mMaskPaint.setColor(maskColor);
    }

    public void setMaskHeight(int maskHeight) {
        this.maskHeight = maskHeight;
        mMaskPaint.setStrokeWidth(maskHeight);
    }

    public void setMaskMarginLeftRight(int maskMarginLeftRight) {
        this.maskMarginLeftRight = maskMarginLeftRight;
    }

    public void setMaskMarginTopBottom(int maskMarginTopBottom) {
        this.maskMarginTopBottom = maskMarginTopBottom;
    }

    public void setMaskDuration(int maskDuration) {
        this.maskDuration = maskDuration;
        if (mMaskAnimator != null) {
            mMaskAnimator.setDuration(maskDuration);
        }
    }

    public void setShadowColor(int shadowColor) {
        this.shadowColor = shadowColor;
        mShadowPaint.setColor(shadowColor);
    }
}
