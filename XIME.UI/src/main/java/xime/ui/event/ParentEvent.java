package xime.ui.event;

import android.graphics.Rect;
import xime.ui.view.View;

public interface ParentEvent {
    void requestLayout();
    boolean isLayoutRequested();
    void invalidateChild(View child, Rect dirty);
    void onDescendantInvalidated(View child, View target);
    void requestDisallowInterceptTouchEvent(boolean disallow);
    ParentEvent getBaseParent();
}
