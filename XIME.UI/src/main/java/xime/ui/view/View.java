package xime.ui.view;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.drawable.Drawable;
import android.util.AttributeSet;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import xime.ui.event.AccessibilityEvent;
import xime.ui.event.ParentEvent;
import xime.ui.event.DrawableEvent;
import xime.ui.event.KeyEvent;

public class View extends android.view.View implements AccessibilityEvent, DrawableEvent, KeyEvent {

    protected Context mContext;

    public View(Context context) {
        super(context);
        this.mContext = context;
    }

    public View(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        this.mContext = context;
    }

    public View(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        this.mContext = context;
    }

    public View(Context context, @Nullable AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
        this.mContext = context;
    }

    private ParentEvent mBaseParent;

    public void assignParent(ParentEvent parent) {
        this.mBaseParent = parent;
    }

    public ParentEvent getBaseParent() {
        return mBaseParent;
    }

    @Override
    public void requestLayout() {
        super.requestLayout();
        if (mBaseParent != null) {
            mBaseParent.requestLayout();
        }
    }

    @Override
    public void invalidate() {
        if (mBaseParent != null) {
            mBaseParent.invalidateChild(this, null);
        } else {
            super.invalidate();
        }
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
    }

    @Override
    protected void onDraw(@NonNull Canvas canvas) {
        super.onDraw(canvas);
    }

    // AccessibilityEvent implementation
    @Override
    public void sendAccessibilityEvent(int eventType) {
        super.sendAccessibilityEvent(eventType);
    }

    @Override
    public void sendAccessibilityEventUnchecked(android.view.accessibility.AccessibilityEvent event) {
        super.sendAccessibilityEventUnchecked(event);
    }

    // DrawableEvent implementation
    @Override
    public void invalidateDrawable(@NonNull Drawable drawable) {
        super.invalidateDrawable(drawable);
    }

    @Override
    public void scheduleDrawable(@NonNull Drawable who, @NonNull Runnable what, long when) {
        super.scheduleDrawable(who, what, when);
    }

    @Override
    public void unscheduleDrawable(@NonNull Drawable who, @NonNull Runnable what) {
        super.unscheduleDrawable(who, what);
    }

    // KeyEvent implementation
    @Override
    public boolean onKeyDown(int keyCode, android.view.KeyEvent event) {
        return super.onKeyDown(keyCode, event);
    }

    @Override
    public boolean onKeyLongPress(int keyCode, android.view.KeyEvent event) {
        return super.onKeyLongPress(keyCode, event);
    }

    @Override
    public boolean onKeyUp(int keyCode, android.view.KeyEvent event) {
        return super.onKeyUp(keyCode, event);
    }

    @Override
    public boolean onKeyMultiple(int keyCode, int count, android.view.KeyEvent event) {
        return super.onKeyMultiple(keyCode, count, event);
    }
}
