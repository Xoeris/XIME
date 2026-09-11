package xime.ui.layout;

import android.content.Context;
import android.content.res.TypedArray;
import android.os.Build;
import android.util.AttributeSet;
import android.view.Gravity;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.constraintlayout.widget.ConstraintSet;
import androidx.transition.Transition;
import androidx.transition.TransitionManager;

import xime.R;
import xime.animation.LayoutMorpher;

public class ConstraintLayout extends androidx.constraintlayout.widget.ConstraintLayout {

    private boolean isAnimating = false;
    private boolean mSquareAspect = false;
    private int mGravity = Gravity.TOP | Gravity.START;

    public ConstraintLayout(@NonNull Context context) {
        super(context);
        init(context, null, 0);
    }

    public ConstraintLayout(@NonNull Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init(context, attrs, 0);
    }

    public ConstraintLayout(@NonNull Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init(context, attrs, defStyleAttr);
    }

    private void init(Context context, AttributeSet attrs, int defStyleAttr) {
        if (attrs != null) {
            TypedArray a = context.obtainStyledAttributes(attrs, R.styleable.Layout, defStyleAttr, 0);
            mSquareAspect = a.getBoolean(R.styleable.Layout_xoerisSquareAspect, false);
            mGravity = a.getInt(R.styleable.Layout_xoerisGravity, Gravity.TOP | Gravity.START);
            a.recycle();
        }
    }

    public boolean isAnimating() {
        return isAnimating;
    }

    /**
     * Morphs the content of this ConstraintLayout between two layout states using ConstraintSets.
     * This preserves view identity and allows for smooth interpolation.
     */
    public void applyMorph(int layoutResId, int duration, @Nullable Runnable midTask) {
        if (isAnimating) return;

        ConstraintSet constraintSet = new ConstraintSet();
        constraintSet.clone(getContext(), layoutResId);

        Transition transition = LayoutMorpher.createMorphTransition(duration);
        transition.addListener(new Transition.TransitionListener() {
            @Override public void onTransitionStart(@NonNull Transition t) { isAnimating = true; }
            @Override public void onTransitionEnd(@NonNull Transition t) { isAnimating = false; }
            @Override public void onTransitionCancel(@NonNull Transition t) { isAnimating = false; }
            @Override public void onTransitionPause(@NonNull Transition t) {}
            @Override public void onTransitionResume(@NonNull Transition t) {}
        });

        TransitionManager.beginDelayedTransition(this, transition);
        
        if (midTask != null) midTask.run();
        
        constraintSet.applyTo(this);
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

    public void setGravity(int gravity) {
        if (this.mGravity != gravity) {
            this.mGravity = gravity;
            requestLayout();
        }
    }

    public int getGravity() {
        return mGravity;
    }

    @Override
    protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        if (Build.VERSION.SDK_INT >= 29 && isLayoutSuppressed()) return;
        super.onLayout(changed, left, top, right, bottom);
        applyGravityOffsets();
    }

    private void applyGravityOffsets() {
        if (mGravity == (Gravity.TOP | Gravity.START)) return;

        int minLeft = Integer.MAX_VALUE;
        int maxRight = Integer.MIN_VALUE;
        int minTop = Integer.MAX_VALUE;
        int maxBottom = Integer.MIN_VALUE;
        boolean hasVisibleChild = false;

        for (int i = 0; i < getChildCount(); i++) {
            View child = getChildAt(i);
            if (child.getVisibility() != GONE) {
                minLeft = Math.min(minLeft, child.getLeft());
                maxRight = Math.max(maxRight, child.getRight());
                minTop = Math.min(minTop, child.getTop());
                maxBottom = Math.max(maxBottom, child.getBottom());
                hasVisibleChild = true;
            }
        }

        if (!hasVisibleChild) return;

        int contentWidth = maxRight - minLeft;
        int contentHeight = maxBottom - minTop;

        int parentWidth = getWidth() - getPaddingLeft() - getPaddingRight();
        int parentHeight = getHeight() - getPaddingTop() - getPaddingBottom();

        int targetLeft;
        int targetTop;

        int layoutDirection = 0;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1) {
            layoutDirection = getLayoutDirection();
        }
        int absoluteGravity = 0;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.ICE_CREAM_SANDWICH) {
            absoluteGravity = Gravity.getAbsoluteGravity(mGravity, layoutDirection);
        }
        final int verticalGravity = mGravity & Gravity.VERTICAL_GRAVITY_MASK;

        switch (absoluteGravity & Gravity.HORIZONTAL_GRAVITY_MASK) {
            case Gravity.CENTER_HORIZONTAL:
                targetLeft = getPaddingLeft() + (parentWidth - contentWidth) / 2;
                break;
            case Gravity.RIGHT:
                targetLeft = getPaddingLeft() + parentWidth - contentWidth;
                break;
            case Gravity.LEFT:
            default:
                targetLeft = getPaddingLeft();
                break;
        }

        switch (verticalGravity) {
            case Gravity.CENTER_VERTICAL:
                targetTop = getPaddingTop() + (parentHeight - contentHeight) / 2;
                break;
            case Gravity.BOTTOM:
                targetTop = getPaddingTop() + parentHeight - contentHeight;
                break;
            case Gravity.TOP:
            default:
                targetTop = getPaddingTop();
                break;
        }

        int offsetX = targetLeft - minLeft;
        int offsetY = targetTop - minTop;

        if (offsetX != 0 || offsetY != 0) {
            for (int i = 0; i < getChildCount(); i++) {
                View child = getChildAt(i);
                if (child.getVisibility() != GONE) {
                    child.offsetLeftAndRight(offsetX);
                    child.offsetTopAndBottom(offsetY);
                }
            }
        }
    }

    @Override
    public void requestLayout() {
        if (Build.VERSION.SDK_INT >= 29 && isLayoutSuppressed()) return;
        super.requestLayout();
    }

    public boolean isLayoutSuppressed() {
        if (Build.VERSION.SDK_INT >= 29) {
            return super.isLayoutSuppressed();
        }
        return false;
    }
}
