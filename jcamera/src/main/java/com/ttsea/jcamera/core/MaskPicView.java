package com.ttsea.jcamera.core;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;

import androidx.annotation.ColorInt;
import androidx.annotation.NonNull;

/**
 * 拍照时的view，该view会放在SurfaceView的上面
 */
class MaskPicView extends BaseMaskView {
    //网格数
    private final int gridCount = 3;
    //是否显示网格
    private boolean showGrid;

    private Paint mGridPaint;

    public MaskPicView(@NonNull Context context) {
        super(context);

        init();
    }

    private void init() {
        mGridPaint = new Paint();
        mGridPaint.setColor(Color.parseColor("#A0FFFFFF"));
        mGridPaint.setStyle(Paint.Style.STROKE);
        mGridPaint.setStrokeWidth(1);
        mGridPaint.setAntiAlias(true);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        if (showGrid) {
            drawGrid(canvas);
        }
    }

    //画网格
    private void drawGrid(Canvas canvas) {
        if (getWidth() == 0 || getHeight() == 0) {
            return;
        }

        for (int i = 1; i <= gridCount - 1; i++) {
            canvas.drawLine(i * getWidth() / gridCount, 0, i * getWidth() / gridCount, getHeight(), mGridPaint);
            canvas.drawLine(0, i * getHeight() / gridCount, getWidth(), i * getHeight() / gridCount, mGridPaint);
        }
    }

    /**
     * 设置是否显示网格
     *
     * @param showGrid true:显示网格，false:不显示网格
     */
    protected void setShowGrid(boolean showGrid) {
        this.showGrid = showGrid;
    }

    /**
     * 设置网格线的粗细
     *
     * @param strokeWidth
     */
    protected void setGridStrokeWidth(int strokeWidth) {
        mGridPaint.setStrokeWidth(strokeWidth);
    }

    /**
     * 设置网格线的颜色
     *
     * @param strokeColor
     */
    protected void setGridStrokeColor(@ColorInt int strokeColor) {
        mGridPaint.setColor(strokeColor);
    }
}
