package com.ttsea.jcamera.demo.camera.views;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.animation.LinearInterpolator;
import android.widget.FrameLayout;

import com.ttsea.jcamera.demo.R;
import com.ttsea.jcamera.demo.utils.Utils;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

public class RecordButton extends FrameLayout {
    private final int ANIMATOR_DURATION = 500;

    private Context mContext;

    private GestureCallback mCallback;
    /** 缩小动画(即按下后按钮变小动画) */
    private ValueAnimator mZoomOutAnimator;
    /** 放大(即松手后回弹动画) */
    private ValueAnimator mZoomInAnimator;

    private RectF outRect;//外圆
    private RectF inRect;//内圆
    private RectF dynamicRect;//动态圆
    private RectF progressRect;

    private Paint outPaint;//外圆画笔
    private Paint inPaint;//内圆画笔

    private int amplitude;
    private int progressWidth;

    private boolean hasActionUp = false;
    private boolean animatorEnd = false;

    public RecordButton(@NonNull Context context) {
        this(context, null);
    }

    public RecordButton(@NonNull Context context, @Nullable AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public RecordButton(@NonNull Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);

        mContext = context;
        init();
    }

    private void init() {
        setWillNotDraw(false);

        amplitude = Utils.dip2px(mContext, 10);
        progressWidth = Utils.dip2px(mContext, 10);

        outRect = new RectF();
        inRect = new RectF();
        dynamicRect = new RectF();
        progressRect = new RectF();

        outPaint = new Paint();
        outPaint.setColor(mContext.getResources().getColor(R.color.colorPrimary));
        outPaint.setStyle(Paint.Style.FILL);
        outPaint.setAntiAlias(true);

        inPaint = new Paint();
        inPaint.setColor(mContext.getResources().getColor(R.color.colorPrimaryDark));
        inPaint.setStyle(Paint.Style.FILL);
        inPaint.setAntiAlias(true);

        mZoomOutAnimator = ValueAnimator.ofFloat(0f, 1f);
        mZoomOutAnimator.setInterpolator(new LinearInterpolator());
        mZoomOutAnimator.setDuration(ANIMATOR_DURATION);
        mZoomOutAnimator.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            @Override
            public void onAnimationUpdate(ValueAnimator animation) {
                float offset = ((float) animation.getAnimatedValue()) * amplitude;

                dynamicRect.left = outRect.left + offset;
                dynamicRect.right = outRect.right - offset;
                dynamicRect.top = outRect.top + offset;
                dynamicRect.bottom = outRect.bottom - offset;

                invalidate();
            }
        });

        mZoomOutAnimator.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationStart(Animator animation) {
                animatorEnd = false;
            }

            @Override
            public void onAnimationEnd(Animator animation) {
                if (!hasActionUp) {
                    if (mCallback != null) {
                        mCallback.startRecord();
                    }
                }
                animatorEnd = true;
                inRect.left = dynamicRect.left;
                inRect.top = dynamicRect.top;
                inRect.right = dynamicRect.right;
                inRect.bottom = dynamicRect.bottom;
            }
        });

        mZoomInAnimator = ValueAnimator.ofFloat(0f, 1f);
        mZoomInAnimator.setInterpolator(new LinearInterpolator());
        mZoomInAnimator.setDuration(250);
        mZoomInAnimator.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            @Override
            public void onAnimationUpdate(ValueAnimator animation) {
                float offset = ((float) animation.getAnimatedValue()) * amplitude;

                dynamicRect.left = inRect.left - offset;
                dynamicRect.right = inRect.right + offset;
                dynamicRect.top = inRect.top - offset;
                dynamicRect.bottom = inRect.bottom + offset;

                if (outRect.contains(dynamicRect)) {
                } else {
                    dynamicRect.setEmpty();
                }
                invalidate();
            }
        });
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);

        if (getMeasuredWidth() == 0 || getMeasuredHeight() == 0) {
            return;
        }

        outRect.left = 0;
        outRect.top = 0;
        outRect.right = getMeasuredWidth();
        outRect.bottom = getMeasuredHeight();

        inRect.left = amplitude;
        inRect.top = amplitude;
        inRect.right = getMeasuredWidth() - amplitude;
        inRect.bottom = getMeasuredHeight() - amplitude;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        if (isInEditMode() || getWidth() == 0 || getHeight() == 0) {
            return;
        }

        canvas.drawOval(outRect, outPaint);

        if (dynamicRect.isEmpty()) {
            canvas.drawOval(outRect, inPaint);
        } else {
            canvas.drawOval(dynamicRect, inPaint);
        }
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent ev) {
        return super.dispatchTouchEvent(ev);
    }

    @Override
    public boolean onInterceptTouchEvent(MotionEvent ev) {
        return super.onInterceptTouchEvent(ev);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (gestureDetect(event)) {
            return true;
        }
        return super.onTouchEvent(event);
    }

    private boolean gestureDetect(MotionEvent event) {
        if (getWidth() == 0 || getHeight() == 0) {
            return false;
        }

        if (event.getAction() == MotionEvent.ACTION_DOWN) {
            hasActionUp = false;
            if (mZoomInAnimator.isRunning()) {
                mZoomInAnimator.cancel();
            }
            if (mZoomOutAnimator.isRunning()) {
                mZoomOutAnimator.cancel();
            }
            mZoomOutAnimator.start();

            return true;
        }

        if (event.getAction() == MotionEvent.ACTION_UP) {
            hasActionUp = true;
            if (!animatorEnd) {
                if (mCallback != null) {
                    mCallback.takePhoto();
                }
            } else {
                if (mCallback != null) {
                    mCallback.stopRecord();
                }
            }

            if (mZoomInAnimator.isRunning()) {
                mZoomInAnimator.cancel();
            }
            if (mZoomOutAnimator.isRunning()) {
                mZoomOutAnimator.cancel();
            }
            mZoomInAnimator.start();
        }

        if (event.getAction() == MotionEvent.ACTION_CANCEL) {

        }
        return false;
    }

    public void setCallback(GestureCallback callback) {
        this.mCallback = callback;
    }

    public interface GestureCallback {
        /** 拍照 */
        void takePhoto();

        /** 开始录像 */
        void startRecord();

        /** 停止录像 */
        void stopRecord();
    }

}
