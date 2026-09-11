package xime.ui.layout;

import android.content.Context;
import android.content.res.TypedArray;
import android.util.AttributeSet;
import android.view.MotionEvent;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import xime.R;

public class FlowLayout extends NestedScrollView {

    private boolean mSquareAspect = false;

    public interface OnScrollStateChangeListener {
        /** Called when the scroll state changes (e.g., from Idle to Dragging). */
        void onScrollStateChanged(FlowLayout view, int newState);
    }

    public static final int SCROLL_STATE_IDLE = 0;
    public static final int SCROLL_STATE_DRAGGING = 1;
    public static final int SCROLL_STATE_SETTLING = 2;

    private int scrollState = SCROLL_STATE_IDLE;
    private OnScrollStateChangeListener scrollStateChangeListener;

    public FlowLayout(@NonNull Context context) {
        super(context);
        init(context, null, 0);
    }

    public FlowLayout(@NonNull Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init(context, attrs, 0);
    }

    public FlowLayout(@NonNull Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
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

    public void setOnScrollStateChangeListener(OnScrollStateChangeListener listener) {
        this.scrollStateChangeListener = listener;
    }

    @Override
    public boolean onTouchEvent(MotionEvent ev) {
        int action = ev.getActionMasked();
        switch (action) {
            case MotionEvent.ACTION_MOVE:
                setScrollState(SCROLL_STATE_DRAGGING);
                break;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                // Check if we are still flinging (settling) or actually idle
                // NestedScrollView doesn't expose a clean "isFlinging" but we can assume IDLE 
                // for now and let the computeScroll handle settling if needed.
                setScrollState(SCROLL_STATE_IDLE);
                break;
        }
        return super.onTouchEvent(ev);
    }

    @Override
    public void computeScroll() {
        super.computeScroll();
        // If the scroller is still moving, we are in SETTLING state
        if (!getScroller().isFinished()) {
            setScrollState(SCROLL_STATE_SETTLING);
        } else if (scrollState == SCROLL_STATE_SETTLING) {
            setScrollState(SCROLL_STATE_IDLE);
        }
    }

    private void setScrollState(int newState) {
        if (scrollState != newState) {
            scrollState = newState;
            if (scrollStateChangeListener != null) {
                scrollStateChangeListener.onScrollStateChanged(this, newState);
            }
        }
    }

    public int getScrollState() {
        return scrollState;
    }

    // Workaround to access the mScroller field in NestedScrollView if needed,
    // though getScroller() usually isn't public. We'll use a safer approach.
    private android.widget.OverScroller getScroller() {
        try {
            java.lang.reflect.Field field = androidx.core.widget.NestedScrollView.class.getDeclaredField("mScroller");
            field.setAccessible(true);
            return (android.widget.OverScroller) field.get(this);
        } catch (Exception e) {
            return new android.widget.OverScroller(getContext());
        }
    }
}

