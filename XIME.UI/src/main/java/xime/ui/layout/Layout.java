package xime.ui.layout;

import android.content.Context;
import android.content.res.TypedArray;
import android.util.AttributeSet;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import xime.R;
import xime.ui.event.ParentEvent;

public class Layout extends FrameLayout {

    /**
     * Shared across ALL glass/blur/crystal panels (BlurLayout, GlassLayout, CrystalLayout),
     * regardless of type. When one panel is mid-capture (copying its parent's pixels into a
     * bitmap to blur), every other panel must render a flat placeholder instead of its normal
     * blurred+distorted output. Otherwise sibling panels bake each other's already-blurred
     * output into their own capture, and during animation (frequent re-capture) this compounds
     * frame over frame into stacked ghosting/distortion that only clears on a fresh activity/
     * fragment (fresh bitmaps + counter reset to 0).
     */
    protected static int sGlassCaptureDepth = 0;

    public static int getGlassCaptureDepth() {
        return sGlassCaptureDepth;
    }

    public static void setGlassCaptureDepth(int depth) {
        sGlassCaptureDepth = depth;
        xime.graphics.shader.blur.LegacyBlur.setCaptureDepth(depth);
    }

    public static void incrementGlassCaptureDepth() {
        sGlassCaptureDepth++;
        xime.graphics.shader.blur.LegacyBlur.incrementCaptureDepth();
    }

    public static void decrementGlassCaptureDepth() {
        sGlassCaptureDepth--;
        if (sGlassCaptureDepth < 0) sGlassCaptureDepth = 0;
        xime.graphics.shader.blur.LegacyBlur.decrementCaptureDepth();
    }

    protected boolean mMeasureAllChildren = false;
    protected boolean mSquareAspect = false;
    protected int mGravity = android.view.Gravity.TOP | android.view.Gravity.START;

    public Layout(Context context) {
        super(context);
        init(context, null, 0);
    }

    public Layout(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init(context, attrs, 0);
    }

    public Layout(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init(context, attrs, defStyleAttr);
    }

    private void init(Context context, AttributeSet attrs, int defStyleAttr) {
        if (attrs != null) {
            TypedArray a = context.obtainStyledAttributes(attrs, R.styleable.Layout, defStyleAttr, 0);
            mMeasureAllChildren = a.getBoolean(R.styleable.Layout_xoerisMeasureAllChildren, false);
            mSquareAspect = a.getBoolean(R.styleable.Layout_xoerisSquareAspect, false);
            mGravity = a.getInt(R.styleable.Layout_xoerisGravity, android.view.Gravity.TOP | android.view.Gravity.START);
            a.recycle();
        }
    }

    private ParentEvent mBaseParent;

    public void assignParent(ParentEvent parent) {
        this.mBaseParent = parent;
    }

    public ParentEvent getBaseParent() {
        return mBaseParent;
    }

    protected int mBackgroundColor = 0;

    @Override
    public void setBackgroundColor(int color) {
        this.mBackgroundColor = color;
        super.setBackgroundColor(color);
    }

    @Override
    public void requestLayout() {
        super.requestLayout();
        if (mBaseParent != null) {
            mBaseParent.requestLayout();
        }
    }

    private boolean mLayoutSuppressed = false;

    @Override
    public void suppressLayout(boolean suppress) {
        this.mLayoutSuppressed = suppress;
        if (android.os.Build.VERSION.SDK_INT >= 29) {
            super.suppressLayout(suppress);
        }
    }

    public boolean isLayoutSuppressed() {
        return mLayoutSuppressed;
    }

    public void setMeasureAllChildren(boolean measureAllChildren) {
        if (this.mMeasureAllChildren != measureAllChildren) {
            this.mMeasureAllChildren = measureAllChildren;
            requestLayout();
        }
    }

    public boolean getMeasureAllChildren() {
        return mMeasureAllChildren;
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
        int newGravity = gravity;
        if (mGravity != newGravity) {
            if ((newGravity & android.view.Gravity.RELATIVE_HORIZONTAL_GRAVITY_MASK) == 0) {
                newGravity |= android.view.Gravity.START;
            }
            if ((newGravity & android.view.Gravity.VERTICAL_GRAVITY_MASK) == 0) {
                newGravity |= android.view.Gravity.TOP;
            }
            mGravity = newGravity;
            requestLayout();
        }
    }

    public int getGravity() {
        return mGravity;
    }

    @Override
    public boolean isLayoutRequested() {
        if (mLayoutSuppressed) return false;
        return super.isLayoutRequested();
    }

    @Override
    public void invalidate() {
        if (mBaseParent != null) {
            mBaseParent.invalidateChild(null, null);
        } else {
            super.invalidate();
        }
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        if (mMeasureAllChildren || mSquareAspect) {
            int maxWidth = 0;
            int maxHeight = 0;
            int childState = 0;

            int count = getChildCount();
            for (int i = 0; i < count; i++) {
                View child = getChildAt(i);
                if (mMeasureAllChildren || child.getVisibility() != GONE) {
                    measureChildWithMargins(child, widthMeasureSpec, 0, heightMeasureSpec, 0);
                    ViewGroup.LayoutParams lp = child.getLayoutParams();
                    int marginWidth = 0;
                    int marginHeight = 0;
                    if (lp instanceof MarginLayoutParams) {
                        marginWidth = ((MarginLayoutParams) lp).leftMargin + ((MarginLayoutParams) lp).rightMargin;
                        marginHeight = ((MarginLayoutParams) lp).topMargin + ((MarginLayoutParams) lp).bottomMargin;
                    }
                    maxWidth = Math.max(maxWidth, child.getMeasuredWidth() + marginWidth);
                    maxHeight = Math.max(maxHeight, child.getMeasuredHeight() + marginHeight);
                    childState = combineMeasuredStates(childState, child.getMeasuredState());
                }
            }

            maxWidth += getPaddingLeft() + getPaddingRight();
            maxHeight += getPaddingTop() + getPaddingBottom();

            maxWidth = Math.max(maxWidth, getSuggestedMinimumWidth());
            maxHeight = Math.max(maxHeight, getSuggestedMinimumHeight());

            int measuredWidth = resolveSizeAndState(maxWidth, widthMeasureSpec, childState);
            int measuredHeight = resolveSizeAndState(maxHeight, heightMeasureSpec, childState << MEASURED_HEIGHT_STATE_SHIFT);

            if (mSquareAspect) {
                int size = Math.max(measuredWidth, measuredHeight);
                setMeasuredDimension(size, size);
            } else {
                setMeasuredDimension(measuredWidth, measuredHeight);
            }
        } else {
            super.onMeasure(widthMeasureSpec, heightMeasureSpec);
        }
    }

    @Override
    protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        if (mLayoutSuppressed) return;
        layoutChildren(left, top, right, bottom);
    }

    void layoutChildren(int left, int top, int right, int bottom) {
        final int count = getChildCount();

        final int parentLeft = getPaddingLeft();
        final int parentRight = right - left - getPaddingRight();

        final int parentTop = getPaddingTop();
        final int parentBottom = bottom - top - getPaddingBottom();

        for (int i = 0; i < count; i++) {
            final View child = getChildAt(i);
            if (child.getVisibility() != GONE) {
                final ViewGroup.LayoutParams gLp = child.getLayoutParams();
                int gravity = -1;
                int leftMargin = 0;
                int rightMargin = 0;
                int topMargin = 0;
                int bottomMargin = 0;

                if (gLp instanceof MarginLayoutParams) {
                    MarginLayoutParams mlp = (MarginLayoutParams) gLp;
                    leftMargin = mlp.leftMargin;
                    rightMargin = mlp.rightMargin;
                    topMargin = mlp.topMargin;
                    bottomMargin = mlp.bottomMargin;
                }
                if (gLp instanceof LayoutParams) {
                    gravity = ((LayoutParams) gLp).gravity;
                } else if (gLp instanceof FrameLayout.LayoutParams) {
                    gravity = ((FrameLayout.LayoutParams) gLp).gravity;
                }

                final int width = child.getMeasuredWidth();
                final int height = child.getMeasuredHeight();

                int childLeft;
                int childTop;

                if (gravity == -1) {
                    gravity = mGravity;
                }

                final int layoutDirection = getLayoutDirection();
                final int absoluteGravity = android.view.Gravity.getAbsoluteGravity(gravity, layoutDirection);
                final int verticalGravity = gravity & android.view.Gravity.VERTICAL_GRAVITY_MASK;

                switch (absoluteGravity & android.view.Gravity.HORIZONTAL_GRAVITY_MASK) {
                    case android.view.Gravity.CENTER_HORIZONTAL:
                        childLeft = parentLeft + (parentRight - parentLeft - width) / 2 +
                                leftMargin - rightMargin;
                        break;
                    case android.view.Gravity.RIGHT:
                        childLeft = parentRight - width - rightMargin;
                        break;
                    case android.view.Gravity.LEFT:
                    default:
                        childLeft = parentLeft + leftMargin;
                }

                switch (verticalGravity) {
                    case android.view.Gravity.TOP:
                        childTop = parentTop + topMargin;
                        break;
                    case android.view.Gravity.CENTER_VERTICAL:
                        childTop = parentTop + (parentBottom - parentTop - height) / 2 +
                                topMargin - bottomMargin;
                        break;
                    case android.view.Gravity.BOTTOM:
                        childTop = parentBottom - height - bottomMargin;
                        break;
                    default:
                        childTop = parentTop + topMargin;
                }

                child.layout(childLeft, childTop, childLeft + width, childTop + height);
            }
        }
    }

    @Override
    protected LayoutParams generateDefaultLayoutParams() {
        return new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
    }

    @Override
    public LayoutParams generateLayoutParams(AttributeSet attrs) {
        return new LayoutParams(getContext(), attrs);
    }

    @Override
    protected ViewGroup.LayoutParams generateLayoutParams(ViewGroup.LayoutParams p) {
        return new LayoutParams(p);
    }

    @Override
    protected boolean checkLayoutParams(ViewGroup.LayoutParams p) {
        return p instanceof LayoutParams;
    }

    public static boolean isLowEndDevice(android.content.Context context) {
        android.app.ActivityManager am = (android.app.ActivityManager) context.getSystemService(android.content.Context.ACTIVITY_SERVICE);
        if (am == null) return false;
        return am.isLowRamDevice();
    }

    public static class LayoutParams extends FrameLayout.LayoutParams {
        public LayoutParams(Context c, AttributeSet attrs) {
            super(c, attrs);
        }

        public LayoutParams(int width, int height) {
            super(width, height);
        }

        public LayoutParams(int width, int height, int gravity) {
            super(width, height, gravity);
        }

        public LayoutParams(@NonNull ViewGroup.LayoutParams source) {
            super(source);
        }

        public LayoutParams(@NonNull MarginLayoutParams source) {
            super(source);
        }

        public LayoutParams(@NonNull FrameLayout.LayoutParams source) {
            super(source);
        }
    }
}
