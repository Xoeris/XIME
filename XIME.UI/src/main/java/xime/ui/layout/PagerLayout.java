package xime.ui.layout;

import android.content.Context;
import android.content.res.TypedArray;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.VelocityTracker;
import android.view.View;
import android.view.ViewConfiguration;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.dynamicanimation.animation.FloatValueHolder;
import androidx.dynamicanimation.animation.SpringAnimation;
import androidx.dynamicanimation.animation.SpringForce;
import androidx.viewpager2.widget.ViewPager2;
import java.util.ArrayList;
import java.util.List;
import xime.R;
import xime.ui.utils.Window;

/**
 * Custom Pager using SineReflectFoundation for smooth physics.
 * Supports both custom View-based paging and standard ViewPager2 via setAdapter.
 */
public class PagerLayout extends ReflectLayout {
    private final List<View> pages = new ArrayList<>();
    private ViewPager2 viewPager;
    private int currentPage = 0;
    private float lastX;
    private float initialX;
    private float lastY;
    private float initialY;
    private int touchSlop;
    private int cachedWidth;
    private int cachedHeight;
    private boolean pagingEnabled = true;
    private VelocityTracker velocityTracker;
    private SpringAnimation scrollSpring;
    private float currentScrollX = 0f;
    private int pagerOrientation = ORIENTATION_HORIZONTAL;

    public interface OnPagerStateChangeListener {
        void onDraggingStarted();
        void onDraggingStopped();
    }
    private OnPagerStateChangeListener stateChangeListener;

    public interface OnPageChangeListener {
        void onPageSelected(int page);
    }
    private OnPageChangeListener pageChangeListener;

    public void setOnPageChangeListener(OnPageChangeListener listener) {
        this.pageChangeListener = listener;
    }

    public PagerLayout(@NonNull Context context) {
        this(context, null);
    }

    public PagerLayout(@NonNull Context context, @Nullable AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public PagerLayout(@NonNull Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        
        TypedArray a = context.obtainStyledAttributes(attrs, R.styleable.PagerLayout, defStyleAttr, 0);
        pagingEnabled = a.getBoolean(R.styleable.PagerLayout_xoerisPagingEnabled, true);
        
        // Check both PagerLayout specific and ReflectLayout orientation attributes
        if (a.hasValue(R.styleable.PagerLayout_xoerisPagerOrientation)) {
            pagerOrientation = a.getInt(R.styleable.PagerLayout_xoerisPagerOrientation, ORIENTATION_HORIZONTAL);
        } else {
            // Fallback to inherited orientation from ReflectLayout if possible
            TypedArray sa = context.obtainStyledAttributes(attrs, R.styleable.ReflectLayout, defStyleAttr, 0);
            pagerOrientation = sa.getInt(R.styleable.ReflectLayout_xoerisOrientation, ORIENTATION_HORIZONTAL);
            sa.recycle();
        }
        a.recycle();
        
        setOrientation(pagerOrientation);
        init(context);
    }

    private void init(Context context) {
        setMeasureAllChildren(true);
        touchSlop = ViewConfiguration.get(context).getScaledTouchSlop();
        cachedWidth = Window.getWidth(context);
        cachedHeight = Window.getHeight(context);
        
        setupScrollSpring();
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        this.mMeasureAllChildren = MeasureSpec.getMode(heightMeasureSpec) == MeasureSpec.EXACTLY;
        if (!this.mMeasureAllChildren) {
            // Compact mode: strictly hide non-current pages to save space/measurements
            for (int i = 0; i < pages.size(); i++) {
                pages.get(i).setVisibility(i == currentPage ? VISIBLE : GONE);
            }
        }
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
    }

    private void setupScrollSpring() {
        scrollSpring = new SpringAnimation(new FloatValueHolder(0));
        scrollSpring.setSpring(new SpringForce()
                .setStiffness(SpringForce.STIFFNESS_MEDIUM)
                .setDampingRatio(SpringForce.DAMPING_RATIO_NO_BOUNCY));
        
        scrollSpring.addUpdateListener((animation, value, velocity) -> {
            currentScrollX = value;
            updatePagePositions();
        });
        
        scrollSpring.addEndListener((animation, canceled, value, velocity) -> {
            if (stateChangeListener != null) stateChangeListener.onDraggingStopped();
            for (View p : pages) p.setLayerType(LAYER_TYPE_NONE, null);
        });
    }

    public void setStateChangeListener(OnPagerStateChangeListener l) {
        this.stateChangeListener = l;
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();
        pages.clear();
        for (int i = 0; i < getChildCount(); i++) {
            pages.add(getChildAt(i));
        }
    }

    @Override
    public void addView(View child, int index, android.view.ViewGroup.LayoutParams params) {
        super.addView(child, index, params);
        if (!pages.contains(child)) {
            if (index < 0 || index >= pages.size()) {
                pages.add(child);
            } else {
                pages.add(index, child);
            }
        }
        updatePagePositions();
    }

    public void addPage(View view) {
        if (!pages.contains(view)) {
            pages.add(view);
        }
        if (view.getParent() != this) {
            addView(view);
        }
        updatePagePositions();
    }

    public void addPage(View view, int index) {
        if (index < 0) index = 0;
        if (index > pages.size()) index = pages.size();
        if (!pages.contains(view)) {
            pages.add(index, view);
        }
        if (view.getParent() != this) {
            addView(view, index);
        }
        updatePagePositions();
    }

    @Override
    public void removeView(View view) {
        super.removeView(view);
        pages.remove(view);
        updatePagePositions();
    }

    public void clearPages() {
        pages.clear();
        removeAllViews();
        currentPage = 0;
        currentScrollX = 0f;
        updatePagePositions();
    }

    @Override
    protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        super.onLayout(changed, left, top, right, bottom);
        updatePagePositions();
    }

    private void updatePagePositions() {
        int size = pagerOrientation == ORIENTATION_HORIZONTAL ? getWidth() : getHeight();
        if (size == 0) size = pagerOrientation == ORIENTATION_HORIZONTAL ? cachedWidth : cachedHeight;
        
        for (int i = 0; i < pages.size(); i++) {
            View page = pages.get(i);
            float trans = (i * size) - currentScrollX;
            
            if (pagerOrientation == ORIENTATION_HORIZONTAL) {
                page.setTranslationX(trans);
                page.setTranslationY(0);
            } else {
                page.setTranslationY(trans);
                page.setTranslationX(0);
            }
            
            // Performance: Hide pages that are definitely off-screen
            if (trans < -size || trans > size) {
                page.setVisibility(mMeasureAllChildren ? INVISIBLE : GONE);
            } else {
                page.setVisibility(VISIBLE);
            }
        }
    }

    public void setPagingEnabled(boolean enabled) {
        this.pagingEnabled = enabled;
        if (!enabled && currentPage != 0) {
            currentPage = 0;
            snapToPage(0);
        }
    }

    @Override
    public boolean onInterceptTouchEvent(MotionEvent ev) {
        if (!pagingEnabled || viewPager != null || pages.isEmpty()) return false;
        
        switch (ev.getAction()) {
            case MotionEvent.ACTION_DOWN:
                lastX = ev.getX();
                initialX = ev.getX();
                lastY = ev.getY();
                initialY = ev.getY();
                if (scrollSpring.isRunning()) {
                    scrollSpring.cancel();
                    if (stateChangeListener != null) stateChangeListener.onDraggingStarted();
                    if (getParent() != null) getParent().requestDisallowInterceptTouchEvent(true);
                    return true;
                }
                break;
            case MotionEvent.ACTION_MOVE:
                float dx = ev.getX() - initialX;
                float dy = ev.getY() - initialY;
                float diffX = Math.abs(dx);
                float diffY = Math.abs(dy);
                
                float diff = pagerOrientation == ORIENTATION_HORIZONTAL ? diffX : diffY;
                float d = pagerOrientation == ORIENTATION_HORIZONTAL ? dx : dy;
                
                if (diff > touchSlop && diff > (pagerOrientation == ORIENTATION_HORIZONTAL ? diffY : diffX)) {
                    // Check boundaries
                    boolean atStart = currentPage <= 0;
                    boolean atEnd = currentPage >= pages.size() - 1;
                    
                    // If we are at the start and swiping forward (d > 0), OR at the end and swiping backward (d < 0)
                    // we allow the parent to potentially intercept the event.
                    if ((atStart && d > 0) || (atEnd && d < 0)) {
                        return false; 
                    }

                    if (getParent() != null) getParent().requestDisallowInterceptTouchEvent(true);
                    if (stateChangeListener != null) stateChangeListener.onDraggingStarted();
                    lastX = ev.getX();
                    lastY = ev.getY();
                    for (View p : pages) p.setLayerType(LAYER_TYPE_HARDWARE, null);
                    return true;
                }
                break;
        }
        return super.onInterceptTouchEvent(ev);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (!pagingEnabled) return super.onTouchEvent(event);
        
        if (velocityTracker == null) {
            velocityTracker = VelocityTracker.obtain();
        }
        velocityTracker.addMovement(event);

        int size = pagerOrientation == ORIENTATION_HORIZONTAL ? getWidth() : getHeight();
        if (size == 0) size = pagerOrientation == ORIENTATION_HORIZONTAL ? cachedWidth : cachedHeight;

        switch (event.getAction()) {
            case MotionEvent.ACTION_DOWN:
                lastX = event.getX();
                lastY = event.getY();
                return true;
                
            case MotionEvent.ACTION_MOVE:
                float delta = pagerOrientation == ORIENTATION_HORIZONTAL ? (lastX - event.getX()) : (lastY - event.getY());
                float newScroll = currentScrollX + delta;
                
                // Resistance at edges
                if (newScroll < 0) {
                    newScroll = currentScrollX + delta * 0.4f;
                } else if (newScroll > (pages.size() - 1) * size) {
                    newScroll = currentScrollX + delta * 0.4f;
                }
                
                currentScrollX = newScroll;
                lastX = event.getX();
                lastY = event.getY();
                updatePagePositions();
                break;
                
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                velocityTracker.computeCurrentVelocity(1000);
                float velocity = pagerOrientation == ORIENTATION_HORIZONTAL ? velocityTracker.getXVelocity() : velocityTracker.getYVelocity();
                float totalDiff = pagerOrientation == ORIENTATION_HORIZONTAL ? (event.getX() - initialX) : (event.getY() - initialY);
                
                int targetPage = currentPage;
                if (Math.abs(velocity) > 500) {
                    if (velocity < 0 && currentPage < pages.size() - 1) {
                        targetPage++;
                    } else if (velocity > 0 && currentPage > 0) {
                        targetPage--;
                    }
                } else {
                    if (Math.abs(totalDiff) > (float) size / 3) {
                        if (totalDiff < 0 && currentPage < pages.size() - 1) {
                            targetPage++;
                        } else if (totalDiff > 0 && currentPage > 0) {
                            targetPage--;
                        }
                    }
                }
                
                currentPage = targetPage;
                snapToPage(velocity);
                
                if (velocityTracker != null) {
                    velocityTracker.recycle();
                    velocityTracker = null;
                }
                
                if (Math.abs(totalDiff) < touchSlop) {
                    performClick();
                }
                break;
        }
        return true;
    }

    @Override
    public boolean performClick() {
        return super.performClick();
    }

    private void snapToPage(float velocity) {
        int size = pagerOrientation == ORIENTATION_HORIZONTAL ? getWidth() : getHeight();
        if (size == 0) size = pagerOrientation == ORIENTATION_HORIZONTAL ? cachedWidth : cachedHeight;
        
        float target = currentPage * size;
        scrollSpring.setStartValue(currentScrollX);
        scrollSpring.setStartVelocity(-velocity);
        scrollSpring.getSpring().setFinalPosition(target);
        scrollSpring.start();
        if (pageChangeListener != null) {
            pageChangeListener.onPageSelected(currentPage);
        }
    }
    
    public int getCurrentPage() {
        return viewPager != null ? viewPager.getCurrentItem() : currentPage;
    }
    
    public void setCurrentPage(int page) {
        if (viewPager != null) {
            viewPager.setCurrentItem(page);
        } else {
            this.currentPage = Math.max(0, Math.min(page, pages.size() - 1));
            snapToPage(0);
        }
    }

    public void setAdapter(androidx.recyclerview.widget.RecyclerView.Adapter adapter) {
        ensureViewPager();
        viewPager.setAdapter(adapter);
    }

    public ViewPager2 getViewPager() {
        ensureViewPager();
        return viewPager;
    }

    private void ensureViewPager() {
        if (viewPager == null) {
            viewPager = new ViewPager2(getContext());
            viewPager.setOrientation(pagerOrientation == ORIENTATION_HORIZONTAL ? ViewPager2.ORIENTATION_HORIZONTAL : ViewPager2.ORIENTATION_VERTICAL);
            viewPager.setLayoutParams(new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));
            
            // Disable clipping for advanced zoom/pan
            viewPager.setClipChildren(false);
            viewPager.setClipToPadding(false);
            if (viewPager.getChildAt(0) instanceof androidx.recyclerview.widget.RecyclerView) {
                androidx.recyclerview.widget.RecyclerView rv = (androidx.recyclerview.widget.RecyclerView) viewPager.getChildAt(0);
                rv.setClipChildren(false);
                rv.setClipToPadding(false);
            }

            addView(viewPager);
            // Disable custom paging logic when ViewPager2 is present
            pages.clear();
        }
    }
}

