package com.ttsea.jcamera.demo.camera.views;

import android.content.Context;
import android.graphics.drawable.Drawable;
import android.util.AttributeSet;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

public class ActionView extends FrameLayout {

    public ActionView(@NonNull Context context) {
        this(context, null);
    }

    public ActionView(@NonNull Context context, @Nullable AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public ActionView(@NonNull Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);

        init();
    }

    private void init() {
        ImageView view = new ImageView(getContext());

        LayoutParams params = new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT);
        int margin = dip2px(getContext(), 12);
        params.setMargins(0, 0, margin, 0);
        addView(view, params);
    }

    @Override
    public void setBackgroundDrawable(Drawable background) {
        if (getChildCount() <= 0) {
            return;
        }
        View child = getChildAt(0);
        child.setBackgroundDrawable(background);
    }

    private int dip2px(Context context, float dipValue) {
        final float scale = context.getResources().getDisplayMetrics().density;
        return (int) (dipValue * scale + 0.5f);
    }
}
