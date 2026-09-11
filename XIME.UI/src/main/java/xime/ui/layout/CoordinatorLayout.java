package xime.ui.layout;

import android.content.Context;
import android.content.res.TypedArray;
import android.util.AttributeSet;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import xime.R;

public class CoordinatorLayout extends androidx.coordinatorlayout.widget.CoordinatorLayout {

    private boolean mSquareAspect = false;

    public CoordinatorLayout(@NonNull Context context) {
        super(context);
        init(context, null, 0);
    }

    public CoordinatorLayout(@NonNull Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init(context, attrs, 0);
    }

    public CoordinatorLayout(@NonNull Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
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
}
