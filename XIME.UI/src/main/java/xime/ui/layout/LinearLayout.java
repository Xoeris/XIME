package xime.ui.layout;

import android.content.Context;
import android.content.res.TypedArray;
import android.util.AttributeSet;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import xime.R;
import xime.graphics.shader.blur.LegacyBlur;

public class LinearLayout extends android.widget.LinearLayout {

    private boolean mSquareAspect = false;

    public enum ThemeMode {
        AUTO,
        DARK,
        LIGHT
    }

    public LinearLayout(@NonNull Context context) {
        super(context);
        init(context, null, 0);
    }

    public LinearLayout(@NonNull Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init(context, attrs, 0);
    }

    public LinearLayout(@NonNull Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init(context, attrs, defStyleAttr);
    }

    private void init(Context context, AttributeSet attrs, int defStyleAttr) {
        if (attrs != null) {
            TypedArray a = context.obtainStyledAttributes(attrs, R.styleable.Layout, defStyleAttr, 0);
            mSquareAspect = a.getBoolean(R.styleable.Layout_xoerisSquareAspect, false);
            a.recycle();
        }
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
        if (mSquareAspect) {
            int width = getMeasuredWidth();
            int height = getMeasuredHeight();
            int size = Math.max(width, height);
            setMeasuredDimension(size, size);
        }
    }

    public void setSquareAspect(boolean squareAspect) {
        if (this.mSquareAspect != squareAspect) {
            this.mSquareAspect = squareAspect;
            requestLayout();
            invalidate();
        }
    }

    public boolean getSquareAspect() {
        return mSquareAspect;
    }

    protected ThemeMode getCurrentSystemTheme() {
        int nightMode = getContext().getResources().getConfiguration().uiMode & android.content.res.Configuration.UI_MODE_NIGHT_MASK;
        return nightMode == android.content.res.Configuration.UI_MODE_NIGHT_YES ? ThemeMode.DARK : ThemeMode.LIGHT;
    }

    public ThemeMode getActiveTheme() {
        return getCurrentSystemTheme();
    }

    public void setBlurRootView(View view) {
    }

    public void setSkipBlur(boolean skip) {
    }

    public void setPauseUpdates(boolean pause) {
    }

    public void refreshBlur() {
    }

    protected void updateTheme(LegacyBlur.ThemeMode mode) {
    }
}

