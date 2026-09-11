package xime.ui.view;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.view.MotionEvent;

import androidx.annotation.NonNull;
import xime.ui.layout.RecyclerView;

public class OrbitScroller extends RecyclerView.ItemDecoration implements RecyclerView.OnItemTouchListener {

    private final Paint paint;
    private final RectF thumbRect = new RectF();
    private boolean isDragging = false;
    private boolean isScrolling = false;
    private float lastDownX, lastDownY;
    private final int widthDp;
    private final int marginDp;
    private int touchSlop;
    private RecyclerView recyclerView;

    private int topOffsetDp = 0;
    private int bottomOffsetDp = 0;

    public OrbitScroller(int widthDp, int marginDp) {
        this.widthDp = widthDp;
        this.marginDp = marginDp;
        paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        paint.setColor(Color.parseColor("#4DFFFFFF")); // semi-transparent white/gray
        paint.setStyle(Paint.Style.FILL);
    }

    public void setOffsets(int topDp, int bottomDp) {
        this.topOffsetDp = topDp;
        this.bottomOffsetDp = bottomDp;
        if (recyclerView != null) recyclerView.invalidate();
    }

    public void attachTo(RecyclerView rv) {
        if (this.recyclerView != null) {
            this.recyclerView.removeItemDecoration(this);
            this.recyclerView.removeOnItemTouchListener(this);
        }
        this.recyclerView = rv;
        if (rv != null) {
            rv.addItemDecoration(this);
            rv.addOnItemTouchListener(this);
            this.touchSlop = android.view.ViewConfiguration.get(rv.getContext()).getScaledTouchSlop();
        }
    }

    private int dp(float value) {
        if (recyclerView == null)
            return (int) value;
        return (int) (recyclerView.getContext().getResources().getDisplayMetrics().density * value);
    }

    @Override
    public void onDrawOver(@NonNull Canvas c, @NonNull androidx.recyclerview.widget.RecyclerView parent, @NonNull androidx.recyclerview.widget.RecyclerView.State state) {
        super.onDrawOver(c, parent, state);
        if (parent.getAdapter() == null || parent.getAdapter().getItemCount() == 0 || isDragging)
            return;

        drawScrollLine(c, parent);
    }

    private long lastOffsetTime = 0;
    private int cachedOffset = -1;
    private int cachedRange = -1;
    private int cachedExtent = -1;

    private void drawScrollLine(Canvas c, androidx.recyclerview.widget.RecyclerView parent) {
        long now = System.currentTimeMillis();
        int offset, range, extent;

        // Cache scroll measurements for 16ms to avoid redundant heavy calculations in a single frame
        if (now - lastOffsetTime < 16 && cachedOffset != -1) {
            offset = cachedOffset;
            range = cachedRange;
            extent = cachedExtent;
        } else {
            range = parent.computeVerticalScrollRange();
            extent = parent.computeVerticalScrollExtent();
            offset = parent.computeVerticalScrollOffset();
            
            cachedOffset = offset;
            cachedRange = range;
            cachedExtent = extent;
            lastOffsetTime = now;
        }

        if (range <= extent)
            return;

        float topOffset = dp(topOffsetDp);
        float bottomOffset = dp(bottomOffsetDp);
        float usableHeight = parent.getHeight() - topOffset - bottomOffset;

        if (usableHeight <= 0) return;

        float currentThumbHeight = Math.max(dp(40), usableHeight * ((float) extent / range));
        float currentThumbY = topOffset + (usableHeight - currentThumbHeight) * ((float) offset / (range - extent));

        int width = dp(widthDp);
        int margin = dp(marginDp);
        int right = parent.getWidth() - margin;
        int left = right - width;

        thumbRect.set(left, currentThumbY, right, currentThumbY + currentThumbHeight);

        paint.setColor(isDragging ? Color.parseColor("#80FFFFFF") : Color.parseColor("#4DFFFFFF"));
        c.drawRoundRect(thumbRect, width / 2f, width / 2f, paint);
    }

    @Override
    public boolean onInterceptTouchEvent(@NonNull androidx.recyclerview.widget.RecyclerView rv, @NonNull MotionEvent e) {
        int action = e.getActionMasked();
        float x = e.getX();
        float y = e.getY();

        if (action == MotionEvent.ACTION_DOWN) {
            int rightEdge = rv.getWidth();
            if (rightEdge <= 0) return false;

            int hitThreshold = dp(12); // Reduced from 20dp to avoid overlapping clickable items too much
            if (x >= rightEdge - hitThreshold) {
                isDragging = true;
                isScrolling = false;
                lastDownX = x;
                lastDownY = y;
                return false;
            }
        } else if (action == MotionEvent.ACTION_MOVE) {
            if (isDragging && !isScrolling) {
                float dx = Math.abs(x - lastDownX);
                float dy = Math.abs(y - lastDownY);
                if (dy > touchSlop || dx > touchSlop) {
                    isScrolling = true;
                }
            }
            if (isScrolling) {
                handleTouch(rv, y);
                return true;
            }
        } else if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
            isDragging = false;
            isScrolling = false;
        }
        return false;
    }

    @Override
    public void onTouchEvent(@NonNull androidx.recyclerview.widget.RecyclerView rv, @NonNull MotionEvent e) {
        if (!isDragging && !isScrolling)
            return;

        int action = e.getActionMasked();
        if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
            isDragging = false;
            isScrolling = false;
            rv.invalidate();
            return;
        }

        if (action == MotionEvent.ACTION_MOVE) {
            handleTouch(rv, e.getY());
        }
    }

    @Override
    public void onRequestDisallowInterceptTouchEvent(boolean disallowIntercept) {
    }

    private void handleTouch(androidx.recyclerview.widget.RecyclerView rv, float y) {
        int itemCount = rv.getAdapter().getItemCount();
        if (itemCount == 0 || rv.getAdapter() == null) return;

        float topOffset = dp(topOffsetDp);
        float bottomOffset = dp(bottomOffsetDp);
        float usableHeight = rv.getHeight() - topOffset - bottomOffset;
        
        if (usableHeight <= 0) return;

        float fraction = Math.max(0, Math.min(1, (y - topOffset) / usableHeight));
        int targetPosition = (int) (fraction * itemCount);
        
        rv.scrollToPosition(targetPosition);
        rv.invalidate();
    }
}

