package xime.ui.bar;

import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.drawable.Drawable;
import android.os.Handler;
import android.os.Looper;
import android.util.AttributeSet;
import android.util.Log;
import android.view.MotionEvent;
import android.view.ViewConfiguration;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.graphics.drawable.DrawableCompat;

import xime.R;
import xime.ui.event.ParentEvent;
import xime.ui.view.View;

public class LinearProgressBar extends View {

    private int progress = 0;
    private int max = 100;
    private int progressStyle = 1;
    private int progressColor = 0xFFD81B60; // Pink
    private int trackColor = 0xFFFCE4EC; // Light Pink
    private String label = "";
    private int labelColor = -1; // -1 means follow progressColor
    private int indicatorColor = 0xFFD81B60;
    private int indicatorTextColor = Color.WHITE;
    private float barHeight;
    private float progressHeight = -1;
    private boolean showIndicator = true;
    private boolean labelAlignWithIndicator = false;
    private boolean isSeekable = false;
    private boolean isDragging = false;
    // Directional handoff (opt-in): lets a clearly-vertical drag fall through to the
    // parent (e.g. swipe-to-expand starting on the bar) instead of seeking.
    private boolean allowParentVerticalSteal = false;
    private float downX = 0f;
    private float downY = 0f;
    private boolean horizontalLocked = false;
    private boolean stealReleased = false;
    private boolean fallbackActive = false;
    private Drawable xoerisIcon;
    private int progressMode = 0; // 0 = Horizontal, 1 = Vertical

    private Paint trackPaint;
    private Paint progressPaint;
    private Paint labelPaint;
    private Paint indicatorPaint;
    private Paint indicatorTextPaint;

    private final RectF trackRect = new RectF();
    private final RectF progressRect = new RectF();
    private final Path indicatorPath = new Path();
    private final Rect textBounds = new Rect();
    private final Rect labelBounds = new Rect();

    private static final float PERCENT_TEXT_SIZE_DP = 10f;
    
    private float dragRatio = -1f;
    private OnSeekBarChangeListener onSeekBarChangeListener;

    private final Handler stateHandler = new Handler(Looper.getMainLooper());
    private final Runnable revertToNowPlayingTask = () -> setPlaybackState(PlaybackState.NOW_PLAYING);

    public enum PlaybackState {
        NONE(""),
        NOW_PLAYING("Now Playing"),
        PAUSED("Currently Paused"),
        PLAY_NEXT("Play Next"),
        PLAY_PREVIOUS("Play Previous");

        private final String label;
        PlaybackState(String label) { this.label = label; }
        public String getLabel() { return label; }
    }

    public interface OnSeekBarChangeListener {
        void onProgressChanged(LinearProgressBar linearTrackBar, int progress, boolean fromUser);
        void onStartTrackingTouch(LinearProgressBar linearTrackBar);
        void onStopTrackingTouch(LinearProgressBar linearTrackBar);
    }

    public LinearProgressBar(Context context) {
        super(context);
        init(context, null);
    }

    public LinearProgressBar(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init(context, attrs);
    }

    public LinearProgressBar(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init(context, attrs);
    }

    private void init(Context context, AttributeSet attrs) {
        float density = getResources().getDisplayMetrics().density;
        barHeight = 6 * density;

        if (attrs != null) {
            TypedArray a = context.obtainStyledAttributes(attrs, R.styleable.LinearProgressBar);
            try {
                progress = a.getInt(R.styleable.LinearProgressBar_xoerisProgress, 0);
                max = a.getInt(R.styleable.LinearProgressBar_xoerisMax, 100);
                progressColor = a.getColor(R.styleable.LinearProgressBar_xoerisProgressColor, 0xFFD81B60);
                trackColor = a.getColor(R.styleable.LinearProgressBar_xoerisTrackColor, 0xFFFCE4EC);
                label = a.getString(R.styleable.LinearProgressBar_xoerisLabel);
                if (label == null) label = "";
                labelColor = a.getColor(R.styleable.LinearProgressBar_xoerisLabelColor, -1);
                indicatorColor = a.getColor(R.styleable.LinearProgressBar_xoerisIndicatorColor, 0xFFD81B60);
                indicatorTextColor = a.getColor(R.styleable.LinearProgressBar_xoerisIndicatorTextColor, Color.WHITE);
                barHeight = a.getDimension(R.styleable.LinearProgressBar_xoerisBarHeight, 6 * density);
                progressHeight = a.getDimension(R.styleable.LinearProgressBar_xoerisProgressHeight, -1);
                showIndicator = a.getBoolean(R.styleable.LinearProgressBar_xoerisShowIndicator, true);
                labelAlignWithIndicator = a.getBoolean(R.styleable.LinearProgressBar_xoerisLabelAlignWithIndicator, false);
                xoerisIcon = a.getDrawable(R.styleable.LinearProgressBar_xoerisProgressIcon);
                progressStyle = a.getInt(R.styleable.LinearProgressBar_xoerisProgressStyle, 1);
                progressMode = a.getInt(R.styleable.LinearProgressBar_xoerisProgressMode, 0);
            } finally {
                a.recycle();
            }
        }

        trackPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        trackPaint.setColor(trackColor);
        trackPaint.setStyle(Paint.Style.FILL);

        progressPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        progressPaint.setColor(progressColor);
        progressPaint.setStyle(Paint.Style.FILL);

        labelPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        labelPaint.setColor(labelColor == -1 ? progressColor : labelColor);
        labelPaint.setTextSize(13 * density); 
        labelPaint.setAntiAlias(true);

        indicatorPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        indicatorPaint.setColor(indicatorColor);
        indicatorPaint.setStyle(Paint.Style.FILL);

        indicatorTextPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        indicatorTextPaint.setColor(indicatorTextColor);
        indicatorTextPaint.setTextSize(10 * density); 
        indicatorTextPaint.setTextAlign(Paint.Align.CENTER);
        indicatorTextPaint.setAntiAlias(true);
    }

    public void setBarHeight(float barHeight) {
        this.barHeight = barHeight;
        invalidate();
    }

    public void setTrackColor(int trackColor) {
        this.trackColor = trackColor;
        if (trackPaint != null) {
            trackPaint.setColor(trackColor);
        }
        invalidate();
    }

    public void setIndicatorColor(int indicatorColor) {
        this.indicatorColor = indicatorColor;
        if (indicatorPaint != null) {
            indicatorPaint.setColor(indicatorColor);
        }
        invalidate();
    }

    public void setIndicatorTextColor(int indicatorTextColor) {
        this.indicatorTextColor = indicatorTextColor;
        if (indicatorTextPaint != null) {
            indicatorTextPaint.setColor(indicatorTextColor);
        }
        invalidate();
    }

    public void setShowIndicator(boolean showIndicator) {
        this.showIndicator = showIndicator;
        invalidate();
    }

    public void setProgressIcon(Drawable icon) {
        this.xoerisIcon = icon;
        invalidate();
    }

    public void setOnSeekBarChangeListener(OnSeekBarChangeListener l) {
        this.onSeekBarChangeListener = l;
        this.isSeekable = (l != null);
    }

    @Override
    public boolean performClick() {
        return super.performClick();
    }

    private float getAppliedPaddingLeft() {
        return getPaddingLeft();
    }

    private float getAppliedPaddingRight() {
        return getPaddingRight();
    }

    private float getPercentTextWidth(float density) {
        String sample = "100%";
        indicatorTextPaint.setTextSize(PERCENT_TEXT_SIZE_DP * density);
        indicatorTextPaint.getTextBounds(sample, 0, sample.length(), textBounds);
        return textBounds.width() + (12 * density); // Add some padding
    }

    private void requestDisallowIntercept(boolean disallow) {
        ParentEvent parent = getBaseParent();
        while (parent != null) {
            parent.requestDisallowInterceptTouchEvent(disallow);
            parent = parent.getBaseParent();
        }
        // Also propagate through the real Android ViewParent chain. Ancestor ViewGroups
        // (PlayerView swipe/expand handling, pagers, scroll containers) only honor the
        // framework flag, without this they keep receiving onInterceptTouchEvent and
        // steal the seek gesture mid-drag, racing the swipe logic.
        try {
            android.view.ViewParent real = getParent();
            while (real != null) {
                real.requestDisallowInterceptTouchEvent(disallow);
                real = real.getParent();
            }
        } catch (Exception ignored) {}
    }

    public boolean isSeekable() {
        return isSeekable;
    }

    public void setAllowParentVerticalSteal(boolean allow) {
        this.allowParentVerticalSteal = allow;
    }

    /**
     * Direction check for horizontal bars with vertical-steal enabled.
     * @return true if this gesture was released back to the parent.
     */
    private boolean checkVerticalSteal(MotionEvent event) {
        if (!allowParentVerticalSteal || horizontalLocked || stealReleased) return false;
        float dx = Math.abs(event.getX() - downX);
        float dy = Math.abs(event.getY() - downY);
        int slop = ViewConfiguration.get(getContext()).getScaledTouchSlop();
        if (dx <= slop && dy <= slop) return false; // undecided yet
        if (dy > dx) {
            // Vertical intent: end the seek and hand the gesture back. Clearing the
            // disallow flag lets ancestors intercept again from the next event.
            stealReleased = true;
            isDragging = false;
            dragRatio = -1f;
            requestDisallowIntercept(false);
            invalidate();
            return true;
        }
        horizontalLocked = true; // committed to seeking; ignore later vertical wobble
        return false;
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (!isSeekable) return super.onTouchEvent(event);

        if (progressMode == 1) {
            float y = event.getY();
            float paddingTop = getPaddingTop();
            float paddingBottom = getPaddingBottom();
            float baseHeight = getHeight() - paddingTop - paddingBottom;
            float activeSeekHeight = baseHeight;

            float percentAreaHeight = 0;
            if (progressStyle == 2) {
                float density = getResources().getDisplayMetrics().density;
                percentAreaHeight = getPercentTextWidth(density);
                activeSeekHeight = baseHeight - percentAreaHeight;
            }

            // Standard vertical slider: 0% is at the bottom, 100% is at the top.
            // If percentAreaHeight is present (style 2), it is at the top.
            float touchY = y - paddingTop - (progressStyle == 2 ? percentAreaHeight : 0);
            if (touchY < 0) touchY = 0;
            if (touchY > activeSeekHeight) touchY = activeSeekHeight;

            float ratio = activeSeekHeight > 0 ? (activeSeekHeight - touchY) / activeSeekHeight : 0;
            int newProgress = (int) (ratio * max);

            switch (event.getAction()) {
                case MotionEvent.ACTION_DOWN:
                    isDragging = true;
                    requestDisallowIntercept(true);
                    updateProgressFromTouch(newProgress, ratio);
                    if (onSeekBarChangeListener != null) onSeekBarChangeListener.onStartTrackingTouch(this);
                    performClick();
                    return true;
                case MotionEvent.ACTION_MOVE:
                    requestDisallowIntercept(true);
                    updateProgressFromTouch(newProgress, ratio);
                    return true;
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    updateProgressFromTouch(newProgress, ratio);
                    if (onSeekBarChangeListener != null) onSeekBarChangeListener.onStopTrackingTouch(this);
                    isDragging = false;
                    dragRatio = -1f;
                    requestDisallowIntercept(false);
                    invalidate();
                    return true;
            }
            return super.onTouchEvent(event);
        }

        float x = event.getX();
        float paddingLeft = getAppliedPaddingLeft();
        float paddingRight = getAppliedPaddingRight();
        float baseWidth = getWidth() - paddingLeft - paddingRight;
        float activeSeekWidth = baseWidth;

        if (progressStyle == 2) {
            float density = getResources().getDisplayMetrics().density;
            activeSeekWidth = baseWidth - getPercentTextWidth(density);
        }

        float touchX = x - paddingLeft;
        if (touchX < 0) touchX = 0;
        if (touchX > activeSeekWidth) touchX = activeSeekWidth;

        float ratio = activeSeekWidth > 0 ? touchX / activeSeekWidth : 0;
        int newProgress = (int) (ratio * max);

        switch (event.getAction()) {
            case MotionEvent.ACTION_DOWN:
                Log.d("SeekBarTouch", "down seekable=" + isSeekable + " w=" + getWidth());
                isDragging = true;
                downX = event.getX();
                downY = event.getY();
                horizontalLocked = false;
                stealReleased = false;
                requestDisallowIntercept(true);
                updateProgressFromTouch(newProgress, ratio);
                if (onSeekBarChangeListener != null) onSeekBarChangeListener.onStartTrackingTouch(this);
                performClick();
                return true;
            case MotionEvent.ACTION_MOVE:
                if (checkVerticalSteal(event)) return false;
                requestDisallowIntercept(true);
                updateProgressFromTouch(newProgress, ratio);
                return true;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                updateProgressFromTouch(newProgress, ratio);
                if (onSeekBarChangeListener != null) onSeekBarChangeListener.onStopTrackingTouch(this);
                isDragging = false;
                dragRatio = -1f;
                horizontalLocked = false;
                stealReleased = false;
                fallbackActive = false;
                requestDisallowIntercept(false);
                invalidate();
                return true;
        }
        return super.onTouchEvent(event);
    }

    public boolean isDragging() {
        return isDragging;
    }

    /**
     * Host-driven fallback seek (e.g. PlayerView collapsed mode): drives the exact
     * same horizontal seek math when the host owns the gesture instead of this view.
     * Mutually exclusive with touch-driven dragging by single-touch-target dispatch;
     * the extra guards below make that exclusion explicit and safe.
     */
    public void beginFallbackSeek(float rawX) {
        if (!isSeekable || progressMode == 1 || isDragging || fallbackActive) return;
        fallbackActive = true;
        isDragging = true;
        dragRatio = -1f;
        requestDisallowIntercept(true);
        if (onSeekBarChangeListener != null) onSeekBarChangeListener.onStartTrackingTouch(this);
        moveFallbackSeek(rawX);
    }

    public void moveFallbackSeek(float rawX) {
        if (!fallbackActive || !isDragging) return;
        try {
            int[] loc = new int[2];
            getLocationOnScreen(loc);
            float density = getResources().getDisplayMetrics().density;
            float paddingLeft = getAppliedPaddingLeft();
            float paddingRight = getAppliedPaddingRight();
            float baseWidth = getWidth() - paddingLeft - paddingRight;
            float activeSeekWidth = baseWidth;
            if (progressStyle == 2) activeSeekWidth = baseWidth - getPercentTextWidth(density);
            float touchX = rawX - loc[0] - paddingLeft;
            if (touchX < 0) touchX = 0;
            if (touchX > activeSeekWidth) touchX = activeSeekWidth;
            float ratio = activeSeekWidth > 0 ? touchX / activeSeekWidth : 0;
            updateProgressFromTouch((int) (ratio * max), ratio);
        } catch (Exception ignored) {}
    }

    public void endFallbackSeek() {
        if (!fallbackActive) return;
        fallbackActive = false;
        if (onSeekBarChangeListener != null) onSeekBarChangeListener.onStopTrackingTouch(this);
        isDragging = false;
        dragRatio = -1f;
        requestDisallowIntercept(false);
        invalidate();
    }

    private void updateProgressFromTouch(int newProgress, float ratio) {
        this.dragRatio = ratio;
        this.progress = newProgress;
        if (onSeekBarChangeListener != null) {
            onSeekBarChangeListener.onProgressChanged(this, progress, true);
        }
        invalidate();
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        float density = getResources().getDisplayMetrics().density;
        
        if (progressMode == 1) {
            int height = MeasureSpec.getSize(heightMeasureSpec);
            float w;
            if (progressStyle == 2) {
                float activeSegmentWidth = progressHeight > 0 ? progressHeight : barHeight * 3.0f;
                w = activeSegmentWidth + getPaddingLeft() + getPaddingRight() + (8 * density);
                if (!label.isEmpty()) {
                    w += (20 * density);
                }
            } else if (progressStyle == 3) {
                float barWidthStyle3 = progressHeight > 0 ? progressHeight : barHeight + 4 * density;
                w = barWidthStyle3 + getPaddingLeft() + getPaddingRight() + (12 * density);
                if (!label.isEmpty()) {
                    w += (20 * density);
                }
            } else if (progressStyle == 4) {
                float thumbRadius = progressHeight > 0 ? progressHeight / 2f : barHeight * 1.2f;
                w = thumbRadius * 2 + getPaddingLeft() + getPaddingRight() + (12 * density);
                if (!label.isEmpty()) {
                    w += (20 * density);
                }
            } else if (progressStyle == 5) {
                float baseW = progressHeight > 0 ? progressHeight : barHeight * 2.5f;
                w = baseW + getPaddingLeft() + getPaddingRight() + (12 * density);
                if (!label.isEmpty()) {
                    w += (20 * density);
                }
            } else if (progressStyle == 6) {
                float baseW = progressHeight > 0 ? progressHeight : barHeight * 5.0f;
                w = baseW + getPaddingLeft() + getPaddingRight() + (12 * density);
                if (!label.isEmpty()) {
                    w += (20 * density);
                }
            } else if (progressStyle == 7 || progressStyle == 8 || progressStyle == 9) {
                float baseW = progressHeight > 0 ? progressHeight : barHeight * 4.0f;
                w = baseW + getPaddingLeft() + getPaddingRight() + (16 * density);
                if (!label.isEmpty()) {
                    w += (20 * density);
                }
            } else if (!showIndicator && label.isEmpty()) {
                float baseW = progressHeight > 0 ? progressHeight : barHeight;
                w = baseW + getPaddingLeft() + getPaddingRight() + (8 * density);
            } else if (label.isEmpty()) {
                float baseW = progressHeight > 0 ? progressHeight : barHeight;
                w = baseW + (42 * density) + getPaddingLeft() + getPaddingRight();
            } else {
                float baseW = progressHeight > 0 ? progressHeight : barHeight;
                w = baseW + (62 * density) + getPaddingLeft() + getPaddingRight();
            }
            setMeasuredDimension((int) w, height);
            return;
        }

        int width = MeasureSpec.getSize(widthMeasureSpec);
        float h;
        if (progressStyle == 2) {
            float activeSegmentHeight = progressHeight > 0 ? progressHeight : barHeight * 3.0f;
            h = activeSegmentHeight + getPaddingTop() + getPaddingBottom() + (8 * density);
            if (!label.isEmpty()) {
                h += (20 * density);
            }
        } else if (progressStyle == 3) {
            float barHeightStyle3 = progressHeight > 0 ? progressHeight : barHeight + 4 * density;
            h = barHeightStyle3 + getPaddingTop() + getPaddingBottom() + (12 * density);
            if (!label.isEmpty()) {
                h += (20 * density);
            }
        } else if (progressStyle == 4) {
            float thumbRadius = progressHeight > 0 ? progressHeight / 2f : barHeight * 1.2f;
            h = thumbRadius * 2 + getPaddingTop() + getPaddingBottom() + (12 * density);
            if (!label.isEmpty()) {
                h += (20 * density);
            }
        } else if (progressStyle == 5) {
            float baseH = progressHeight > 0 ? progressHeight : barHeight * 2.5f;
            h = baseH + getPaddingTop() + getPaddingBottom() + (12 * density);
            if (!label.isEmpty()) {
                h += (20 * density);
            }
        } else if (progressStyle == 6) {
            // Match the 5.0f multiplier used in drawStyle6
            float baseH = progressHeight > 0 ? progressHeight : barHeight * 5.0f;
            h = baseH + getPaddingTop() + getPaddingBottom() + (12 * density);
            if (!label.isEmpty()) {
                h += (20 * density);
            }
        } else if (progressStyle == 7 || progressStyle == 8 || progressStyle == 9) {
            float baseH = progressHeight > 0 ? progressHeight : barHeight * 4.0f;
            h = baseH + getPaddingTop() + getPaddingBottom() + (16 * density);
            if (!label.isEmpty()) {
                h += (20 * density);
            }
        } else if (!showIndicator && label.isEmpty()) {
            float baseH = progressHeight > 0 ? progressHeight : barHeight;
            h = baseH + getPaddingTop() + getPaddingBottom() + (8 * density);
        } else if (label.isEmpty()) {
            float baseH = progressHeight > 0 ? progressHeight : barHeight;
            h = baseH + (42 * density) + getPaddingTop() + getPaddingBottom();
        } else {
            float baseH = progressHeight > 0 ? progressHeight : barHeight;
            h = baseH + (62 * density) + getPaddingTop() + getPaddingBottom();
        }
        setMeasuredDimension(width, (int) h);
    }

    @Override
    protected void onDraw(@NonNull Canvas canvas) {
        super.onDraw(canvas);

        float density = getResources().getDisplayMetrics().density;
        float width = getWidth();
        float height = getHeight();
        
        float paddingLeft = getAppliedPaddingLeft();
        float paddingRight = getAppliedPaddingRight();
        float availableWidth = width - paddingLeft - paddingRight;

        // Visual ratio logic for smooth dragging regardless of playback state
        float visualRatio = (isDragging && dragRatio >= 0) ? dragRatio : (max > 0 ? (float) progress / max : 0f);

        if (progressMode == 1) {
            drawVertical(canvas, width, height, density, visualRatio);
            return;
        }

        if (progressStyle == 2) {
            drawStyle2(canvas, width, height, paddingLeft, paddingRight, density, visualRatio);
            return;
        }

        if (progressStyle == 3) {
            drawStyle3(canvas, width, height, paddingLeft, paddingRight, density, visualRatio);
            return;
        }

        if (progressStyle == 4) {
            drawStyle4(canvas, width, height, paddingLeft, paddingRight, density, visualRatio);
            return;
        }

        if (progressStyle == 5) {
            drawStyle5(canvas, width, height, paddingLeft, paddingRight, density, visualRatio);
            return;
        }

        if (progressStyle == 6) {
            drawStyle6(canvas, width, height, paddingLeft, paddingRight, density, visualRatio);
            return;
        }

        if (progressStyle == 7) {
            drawStyle7(canvas, width, height, paddingLeft, paddingRight, density, visualRatio);
            return;
        }

        if (progressStyle == 8) {
            drawStyle8(canvas, width, height, paddingLeft, paddingRight, density, visualRatio);
            return;
        }

        if (progressStyle == 9) {
            drawStyle9(canvas, width, height, paddingLeft, paddingRight, density, visualRatio);
            return;
        }

        float progressWidth = visualRatio * availableWidth;
        float indicatorX = paddingLeft + progressWidth;

        // Pre-calculate bubble metrics if needed for label
        float indicatorW = 0;
        float indicatorH = 0;
        float halfW = 0;
        float boxX = indicatorX;
        String progressText = "";
        if (showIndicator) {
            progressText = (int) (visualRatio * 100) + "%";
            indicatorTextPaint.getTextBounds(progressText, 0, progressText.length(), textBounds);
            indicatorW = textBounds.width() + (14 * density);
            indicatorH = textBounds.height() + (8 * density);
            halfW = indicatorW / 2f;
            
            // Bubble center clamping within View bounds
            if (boxX < halfW) boxX = halfW;
            if (boxX > width - halfW) boxX = width - halfW;
        }

        if (!label.isEmpty()) {
            float labelY = 16 * density + getPaddingTop();
            float labelX;
            
            if (labelAlignWithIndicator && showIndicator) {
                labelPaint.getTextBounds(label, 0, label.length(), labelBounds);
                
                // Set alignment to LEFT to handle interpolation manually
                labelPaint.setTextAlign(Paint.Align.LEFT);
                
                // We want to align the VISUAL edges of the text with the bubble box edges.
                // labelBounds.left/right are relative to the x coordinate passed to drawText.
                
                // Target for 0%: label's left visual edge at bubble's left edge
                float targetStartX = (boxX - halfW) - labelBounds.left;
                
                // Target for 100%: label's right visual edge at bubble's right edge
                float targetEndX = (boxX + halfW) - labelBounds.right;
                
                // Interpolate based on visualRatio (0.0 to 1.0)
                labelX = targetStartX + (targetEndX - targetStartX) * visualRatio;
                
                // Clamping: Allow the label to use the full base width,
                // just like the bubble does.
                if (labelX + labelBounds.left < 0) {
                    labelX = -labelBounds.left;
                }
                if (labelX + labelBounds.right > width) {
                    labelX = width - labelBounds.right;
                }
            } else {
                labelX = paddingLeft;
                labelPaint.setTextAlign(Paint.Align.LEFT);
            }
            
            canvas.drawText(label, labelX, labelY, labelPaint);
        }

        float currentBarHeight = progressHeight > 0 ? progressHeight : barHeight;
        float barY = height - currentBarHeight - (6 * density) - getPaddingBottom();
        
        trackRect.set(paddingLeft, barY, width - paddingRight, barY + currentBarHeight);
        canvas.drawRoundRect(trackRect, currentBarHeight / 2f, currentBarHeight / 2f, trackPaint);
        progressRect.set(paddingLeft, barY, paddingLeft + progressWidth, barY + currentBarHeight);
        canvas.drawRoundRect(progressRect, currentBarHeight / 2f, currentBarHeight / 2f, progressPaint);

        if (showIndicator) {
            float triangleSize = 5 * density;
            float indicatorBottomY = barY - (3 * density); 

            // Tip clamping within the bottom edge of the bubble
            // Reduced margin to allow the arrow to reach the "corners" when progress is 0% or 100%
            float tipMin = boxX - halfW + triangleSize;
            float tipMax = boxX + halfW - triangleSize;
            
            float tipX = indicatorX;
            if (tipX < tipMin) tipX = tipMin;
            if (tipX > tipMax) tipX = tipMax;

            // DRAW SHAPE
            drawTooltip(canvas, boxX, tipX, indicatorBottomY, indicatorW, indicatorH, triangleSize, density);

            // DRAW TEXT
            indicatorTextPaint.getTextBounds(progressText, 0, progressText.length(), textBounds);
            float textY = indicatorBottomY - triangleSize - (indicatorH / 2f) + (textBounds.height() / 2f);
            canvas.drawText(progressText, boxX, textY, indicatorTextPaint);
        }
    }

    private void drawTooltip(Canvas canvas, float boxX, float tipX, float y, float w, float h, float triangleSize, float density) {
        float radius = 6 * density;

        // 1. Draw bubble rectangle directly to canvas
        RectF rect = new RectF(boxX - w / 2f, y - h - triangleSize, boxX + w / 2f, y - triangleSize);
        canvas.drawRoundRect(rect, radius, radius, indicatorPaint);

        // 2. Draw pointer separately to avoid path intersection/subtraction artifacts (black areas)
        indicatorPath.reset();
        float arrowBaseY = y - triangleSize - radius; 
        indicatorPath.moveTo(tipX - triangleSize, arrowBaseY);
        indicatorPath.lineTo(tipX, y);
        indicatorPath.lineTo(tipX + triangleSize, arrowBaseY);
        indicatorPath.close();

        canvas.drawPath(indicatorPath, indicatorPaint);
    }

    private void drawStyle2(Canvas canvas, float width, float height, float paddingLeft, float paddingRight, float density, float progressRatio) {
        float segmentWidth = 3 * density;
        float segmentSpacing = 3 * density;
        float segmentHeight = progressHeight > 0 ? progressHeight / 2f : barHeight;
        float activeSegmentHeight = progressHeight > 0 ? progressHeight : barHeight * 3.0f;

        // Label logic
        float labelY = 16 * density + getPaddingTop();
        if (!label.isEmpty()) {
            canvas.drawText(label, paddingLeft, labelY, labelPaint);
        }
        
        float percentAreaWidth = getPercentTextWidth(density);
        float availableBarWidth = width - paddingLeft - paddingRight - percentAreaWidth;
        
        int numSegments = (int) (availableBarWidth / (segmentWidth + segmentSpacing));
        float actualBarWidth = numSegments * (segmentWidth + segmentSpacing) - segmentSpacing;
        
        int indicatorIndex = (int) (progressRatio * (numSegments - 1));

        float barY = label.isEmpty() ? (height / 2f) : (height - activeSegmentHeight / 2f - getPaddingBottom() - 4 * density);

        for (int i = 0; i < numSegments; i++) {
            float x = paddingLeft + i * (segmentWidth + segmentSpacing);
            
            boolean isIndicator = (i == indicatorIndex);
            boolean isActive = (i <= indicatorIndex);
            
            float h = isIndicator ? activeSegmentHeight : segmentHeight;
            float yOffset = h / 2f;
            
            trackRect.set(x, barY - yOffset, x + segmentWidth, barY + yOffset);
            
            if (isActive) {
                canvas.drawRoundRect(trackRect, density, density, progressPaint);
            } else {
                canvas.drawRoundRect(trackRect, density, density, trackPaint);
            }
        }
        
        String percentText = (int) (progressRatio * 100) + "%";
        indicatorTextPaint.setColor(progressColor);
        indicatorTextPaint.setTextSize(PERCENT_TEXT_SIZE_DP * density);
        indicatorTextPaint.setTextAlign(Paint.Align.RIGHT);
        
        indicatorTextPaint.getTextBounds(percentText, 0, percentText.length(), textBounds);
        
        float textX = paddingLeft + actualBarWidth + percentAreaWidth;
        float textY = barY + textBounds.height() / 2f;
        canvas.drawText(percentText, textX, textY, indicatorTextPaint);
    }

    private void drawStyle3(Canvas canvas, float width, float height, float paddingLeft, float paddingRight, float density, float progressRatio) {
        float availableWidth = width - paddingLeft - paddingRight;
        float progressWidth = progressRatio * availableWidth;
        
        float barHeightStyle3 = progressHeight > 0 ? progressHeight : barHeight + 4 * density;
        float barY = height / 2f - barHeightStyle3 / 2f;
        
        if (!label.isEmpty()) {
            float labelY = 16 * density + getPaddingTop();
            canvas.drawText(label, paddingLeft, labelY, labelPaint);
            barY = height - barHeightStyle3 - getPaddingBottom() - 8 * density;
        }

        // 1. Draw TrackEntity Outline
        trackRect.set(paddingLeft, barY, width - paddingRight, barY + barHeightStyle3);
        trackPaint.setStyle(Paint.Style.STROKE);
        trackPaint.setStrokeWidth(1.2f * density);
        trackPaint.setColor(progressColor);
        trackPaint.setAlpha(100);
        canvas.drawRoundRect(trackRect, barHeightStyle3 / 2f, barHeightStyle3 / 2f, trackPaint);

        // 2. Draw Progress Fill with Gradient
        if (progressWidth > 0) {
            progressRect.set(paddingLeft + 1.5f * density, barY + 1.5f * density, paddingLeft + progressWidth, barY + barHeightStyle3 - 1.5f * density);
            
            int startColor = progressColor;
            int endColor = Color.argb(80, Color.red(progressColor), Color.green(progressColor), Color.blue(progressColor));
            
            LinearGradient gradient = new LinearGradient(paddingLeft, barY, paddingLeft + progressWidth, barY, 
                    startColor, endColor, Shader.TileMode.CLAMP);
            
            progressPaint.setShader(gradient);
            progressPaint.setAlpha(255);
            canvas.drawRoundRect(progressRect, (barHeightStyle3 - 3 * density) / 2f, (barHeightStyle3 - 3 * density) / 2f, progressPaint);
            progressPaint.setShader(null);
        }

        // 3. Draw Vertical Thumb
        float thumbWidth = 3 * density;
        float thumbX = paddingLeft + progressWidth;
        if (thumbX < paddingLeft + thumbWidth/2f + 1.5f*density) thumbX = paddingLeft + thumbWidth/2f + 1.5f*density;
        if (thumbX > width - paddingRight - thumbWidth/2f - 1.5f*density) thumbX = width - paddingRight - thumbWidth/2f - 1.5f*density;

        RectF thumbRect = new RectF(thumbX - thumbWidth / 2f, barY + 1.5f * density, thumbX + thumbWidth / 2f, barY + barHeightStyle3 - 1.5f * density);
        progressPaint.setStyle(Paint.Style.FILL);
        progressPaint.setColor(progressColor);
        canvas.drawRoundRect(thumbRect, 1 * density, 1 * density, progressPaint);

        // 4. Draw Text
        String percentText = (int) (progressRatio * 100) + "%";
        indicatorTextPaint.setColor(progressColor);
        indicatorTextPaint.setAlpha(180);
        indicatorTextPaint.setTextSize(10 * density);
        indicatorTextPaint.setTextAlign(Paint.Align.LEFT);
        indicatorTextPaint.getTextBounds(percentText, 0, percentText.length(), textBounds);
        
        float textX = thumbX + thumbWidth + 4 * density;
        float textY = barY + barHeightStyle3 / 2f + textBounds.height() / 2f;
        
        // Clip text if it goes outside the track or handle it
        canvas.drawText(percentText, textX, textY, indicatorTextPaint);
        
        // Restore
        trackPaint.setStyle(Paint.Style.FILL);
        trackPaint.setColor(trackColor);
        trackPaint.setAlpha(255);
    }

    private void drawStyle4(Canvas canvas, float width, float height, float paddingLeft, float paddingRight, float density, float progressRatio) {
        float availableWidth = width - paddingLeft - paddingRight;
        float progressWidth = progressRatio * availableWidth;
        
        float thumbRadius = progressHeight > 0 ? progressHeight / 2f : barHeight * 1.1f;
        float barY = height / 2f;
        
        if (!label.isEmpty()) {
            float labelY = 16 * density + getPaddingTop();
            canvas.drawText(label, paddingLeft, labelY, labelPaint);
            barY = height - thumbRadius - getPaddingBottom() - 4 * density;
        }

        // 1. Draw Full TrackEntity
        trackRect.set(paddingLeft, barY - barHeight / 2f, width - paddingRight, barY + barHeight / 2f);
        trackPaint.setColor(trackColor);
        canvas.drawRoundRect(trackRect, barHeight / 2f, barHeight / 2f, trackPaint);

        // 2. Draw Progress with Gradient
        if (progressWidth > 0) {
            progressRect.set(paddingLeft, barY - barHeight / 2f, paddingLeft + progressWidth, barY + barHeight / 2f);
            
            // Darker shade of progress color for start of gradient
            int startColor = Color.argb(255, 
                (int)(Color.red(progressColor) * 0.7f), 
                (int)(Color.green(progressColor) * 0.7f), 
                (int)(Color.blue(progressColor) * 0.7f));
            int endColor = progressColor;
            
            LinearGradient gradient = new LinearGradient(paddingLeft, barY, paddingLeft + progressWidth, barY, 
                    startColor, endColor, Shader.TileMode.CLAMP);
            
            progressPaint.setShader(gradient);
            canvas.drawRoundRect(progressRect, barHeight / 2f, barHeight / 2f, progressPaint);
            progressPaint.setShader(null);
        }

        // 3. Draw Hollow Circle Thumb
        float thumbX = paddingLeft + progressWidth;
        
        // Stroke for the ring
        progressPaint.setStyle(Paint.Style.STROKE);
        progressPaint.setStrokeWidth(2 * density);
        progressPaint.setColor(progressColor);
        canvas.drawCircle(thumbX, barY, thumbRadius, progressPaint);
        
        // Fill for the center (white)
        progressPaint.setStyle(Paint.Style.FILL);
        progressPaint.setColor(Color.WHITE);
        canvas.drawCircle(thumbX, barY, thumbRadius - 1 * density, progressPaint);
        
        // Reset progressPaint style
        progressPaint.setStyle(Paint.Style.FILL);
    }

    private void drawStyle5(Canvas canvas, float width, float height, float paddingLeft, float paddingRight, float density, float progressRatio) {
        float availableWidth = width - paddingLeft - paddingRight;
        float progressWidth = progressRatio * availableWidth;

        float boxHeight = progressHeight > 0 ? progressHeight : barHeight * 2.5f;
        float barY = height / 2f - boxHeight / 2f;

        if (!label.isEmpty()) {
            float labelY = 16 * density + getPaddingTop();
            canvas.drawText(label, paddingLeft, labelY, labelPaint);
            barY = height - boxHeight - getPaddingBottom() - 4 * density;
        }

        // 1. Draw Rectangular Border
        trackRect.set(paddingLeft, barY, width - paddingRight, barY + boxHeight);
        trackPaint.setStyle(Paint.Style.STROKE);
        trackPaint.setStrokeWidth(1 * density);
        trackPaint.setColor(progressColor);
        trackPaint.setAlpha(150);
        canvas.drawRect(trackRect, trackPaint);

        // 2. Draw Progress with Chevron Tip
        if (progressWidth > 0) {
            float innerPadding = 2 * density;
            float innerLeft = paddingLeft + innerPadding;
            float innerTop = barY + innerPadding;
            float innerBottom = barY + boxHeight - innerPadding;
            float innerRight = paddingLeft + progressWidth;
            
            // Adjust innerRight so it doesn't exceed the border
            if (innerRight > width - paddingRight - innerPadding) {
                innerRight = width - paddingRight - innerPadding;
            }
            
            if (innerRight > innerLeft) {
                indicatorPath.reset();
                indicatorPath.moveTo(innerLeft, innerTop);
                
                float chevronSize = (innerBottom - innerTop) / 2f;
                
                if (innerRight > innerLeft + chevronSize) {
                    indicatorPath.lineTo(innerRight - chevronSize, innerTop);
                    indicatorPath.lineTo(innerRight, innerTop + chevronSize);
                    indicatorPath.lineTo(innerRight - chevronSize, innerBottom);
                } else {
                    indicatorPath.lineTo(innerRight, innerTop + chevronSize);
                }
                
                indicatorPath.lineTo(innerLeft, innerBottom);
                indicatorPath.close();

                int startColor = progressColor;
                int endColor = Color.argb(120, Color.red(progressColor), Color.green(progressColor), Color.blue(progressColor));
                
                LinearGradient gradient = new LinearGradient(innerLeft, barY, innerRight, barY, 
                        startColor, endColor, Shader.TileMode.CLAMP);
                
                progressPaint.setShader(gradient);
                progressPaint.setStyle(Paint.Style.FILL);
                progressPaint.setAlpha(255);
                canvas.drawPath(indicatorPath, progressPaint);
                progressPaint.setShader(null);
            }
        }

        // Restore
        trackPaint.setStyle(Paint.Style.FILL);
        trackPaint.setColor(trackColor);
        trackPaint.setAlpha(255);
    }

    private void drawStyle6(Canvas canvas, float width, float height, float paddingLeft, float paddingRight, float density, float progressRatio) {
        float thickBarHeight = progressHeight > 0 ? progressHeight : barHeight * 5.0f;
        float barY = height / 2f - thickBarHeight / 2f;
        float radius = thickBarHeight / 2f;

        if (!label.isEmpty()) {
            float labelY = 16 * density + getPaddingTop();
            canvas.drawText(label, paddingLeft, labelY, labelPaint);
            barY = height - thickBarHeight - getPaddingBottom() - 4 * density;
        }

        // 1. Draw Background TrackEntity (Rounded Pill)
        trackRect.set(paddingLeft, barY, width - paddingRight, barY + thickBarHeight);
        trackPaint.setColor(progressColor);
        trackPaint.setAlpha(30); 
        canvas.drawRoundRect(trackRect, radius, radius, trackPaint);

        // 2. Draw Progress Fill (Single Rounded Rect to avoid gaps)
        // Calculate thumb center based on progress, ensuring it stays within the track
        float minThumbX = paddingLeft + radius;
        float maxThumbX = width - paddingRight - radius;
        float thumbX = minThumbX + progressRatio * (maxThumbX - minThumbX);

        progressPaint.setColor(progressColor);
        progressPaint.setAlpha(255);
        
        // The progress bar ends at thumbX + radius, so its right semi-circle is centered at thumbX
        progressRect.set(paddingLeft, barY, thumbX + radius, barY + thickBarHeight);
        canvas.drawRoundRect(progressRect, radius, radius, progressPaint);

        // 3. Draw White Circle Thumb (Centered exactly on the progress tip)
        float thumbRadius = radius * 0.65f;
        progressPaint.setColor(Color.WHITE);
        progressPaint.setStyle(Paint.Style.FILL);
        canvas.drawCircle(thumbX, barY + radius, thumbRadius, progressPaint);
        
        // Restore
        trackPaint.setAlpha(255);
        trackPaint.setColor(trackColor);
        progressPaint.setColor(progressColor);
    }

    private void drawIndicatorIcon(Canvas canvas, float cx, float cy, float radius, int color) {
        if (xoerisIcon != null) {
            xoerisIcon.setBounds((int)(cx - radius), (int)(cy - radius), (int)(cx + radius), (int)(cy + radius));
            Drawable wrappedIcon = DrawableCompat.wrap(xoerisIcon).mutate();
            DrawableCompat.setTint(wrappedIcon, color);
            wrappedIcon.draw(canvas);
        } else {
            drawButterflyIcon(canvas, cx, cy, radius, color);
        }
    }

    private void drawButterflyIcon(Canvas canvas, float cx, float cy, float radius, int color) {
        progressPaint.setColor(color);
        progressPaint.setStyle(Paint.Style.FILL);
        
        float wingW = radius * 0.7f;
        float wingH = radius * 0.6f;
        float bodyW = radius * 0.15f;
        float bodyH = radius * 0.8f;

        // Wings
        indicatorPath.reset();
        // Left wings
        indicatorPath.addOval(cx - wingW, cy - wingH, cx, cy, Path.Direction.CW);
        indicatorPath.addOval(cx - wingW * 0.8f, cy, cx, cy + wingH * 0.8f, Path.Direction.CW);
        // Right wings
        indicatorPath.addOval(cx, cy - wingH, cx + wingW, cy, Path.Direction.CW);
        indicatorPath.addOval(cx, cy, cx + wingW * 0.8f, cy + wingH * 0.8f, Path.Direction.CW);
        
        canvas.drawPath(indicatorPath, progressPaint);

        // Body
        trackRect.set(cx - bodyW / 2f, cy - bodyH / 2f, cx + bodyW / 2f, cy + bodyH / 2f);
        canvas.drawRoundRect(trackRect, bodyW / 2f, bodyW / 2f, progressPaint);
    }

    private void drawStyle7(Canvas canvas, float width, float height, float paddingLeft, float paddingRight, float density, float progressRatio) {
        float availableWidth = width - paddingLeft - paddingRight;
        
        float iconRadius = 14 * density;
        float barAreaWidth = availableWidth - (iconRadius * 2 + 12 * density);
        float progressWidth = progressRatio * barAreaWidth;
        
        float thickBarHeight = progressHeight > 0 ? progressHeight : barHeight * 2.5f;
        float barY = height / 2f;
        
        if (!label.isEmpty()) {
            float labelY = 16 * density + getPaddingTop();
            canvas.drawText(label, paddingLeft, labelY, labelPaint);
            barY = height - thickBarHeight / 2f - getPaddingBottom() - 8 * density;
        }

        // 1. Draw Progress Pill with Outline and Gradient
        float pillW = progressWidth;
        if (pillW < thickBarHeight) pillW = thickBarHeight; // Ensure it looks like a pill

        trackRect.set(paddingLeft, barY - thickBarHeight / 2f, paddingLeft + pillW, barY + thickBarHeight / 2f);
        
        // Gradient
        int startColor = Color.argb(100, Color.red(progressColor), Color.green(progressColor), Color.blue(progressColor));
        int endColor = progressColor;
        LinearGradient gradient = new LinearGradient(paddingLeft, barY, paddingLeft + pillW, barY, startColor, endColor, Shader.TileMode.CLAMP);
        progressPaint.setShader(gradient);
        canvas.drawRoundRect(trackRect, thickBarHeight / 2f, thickBarHeight / 2f, progressPaint);
        progressPaint.setShader(null);

        // Outline
        trackPaint.setStyle(Paint.Style.STROKE);
        trackPaint.setStrokeWidth(1.2f * density);
        trackPaint.setColor(progressColor);
        canvas.drawRoundRect(trackRect, thickBarHeight / 2f, thickBarHeight / 2f, trackPaint);

        // Percentage Text
        String percentText = (int) (progressRatio * 100) + "%";
        indicatorTextPaint.setColor(Color.WHITE);
        indicatorTextPaint.setTextSize(10 * density);
        indicatorTextPaint.setTextAlign(Paint.Align.RIGHT);
        indicatorTextPaint.getTextBounds(percentText, 0, percentText.length(), textBounds);
        canvas.drawText(percentText, paddingLeft + pillW - 8 * density, barY + textBounds.height() / 2f, indicatorTextPaint);

        // 2. Draw Connection Line
        float lineStartX = paddingLeft + pillW;
        float lineEndX = paddingLeft + availableWidth - iconRadius * 2;
        if (lineEndX > lineStartX) {
            trackPaint.setStyle(Paint.Style.STROKE);
            trackPaint.setAlpha(150);
            canvas.drawLine(lineStartX, barY, lineEndX, barY, trackPaint);
        }

        // 3. Draw Icon Badge
        float iconCx = paddingLeft + availableWidth - iconRadius;
        float iconCy = barY;
        
        trackPaint.setStyle(Paint.Style.STROKE);
        trackPaint.setAlpha(255);
        canvas.drawCircle(iconCx, iconCy, iconRadius, trackPaint);
        
        drawIndicatorIcon(canvas, iconCx, iconCy, iconRadius * 0.6f, progressColor);

        trackPaint.setStyle(Paint.Style.FILL);
    }

    private void drawStyle8(Canvas canvas, float width, float height, float paddingLeft, float paddingRight, float density, float progressRatio) {
        float availableWidth = width - paddingLeft - paddingRight;
        float progressWidth = progressRatio * availableWidth;
        
        float thickBarHeight = progressHeight > 0 ? progressHeight : barHeight * 2.5f;
        float barY = height / 2f;
        
        if (!label.isEmpty()) {
            float labelY = 16 * density + getPaddingTop();
            canvas.drawText(label, paddingLeft, labelY, labelPaint);
            barY = height - thickBarHeight / 2f - getPaddingBottom() - 12 * density;
        }

        // 1. Draw Full TrackEntity Outline
        trackRect.set(paddingLeft, barY - thickBarHeight / 2f, width - paddingRight, barY + thickBarHeight / 2f);
        trackPaint.setStyle(Paint.Style.STROKE);
        trackPaint.setStrokeWidth(1.2f * density);
        trackPaint.setColor(progressColor);
        canvas.drawRoundRect(trackRect, thickBarHeight / 2f, thickBarHeight / 2f, trackPaint);

        // 2. Draw Progress Fill with Gradient
        if (progressWidth > 2 * density) {
            progressRect.set(paddingLeft + 2 * density, barY - thickBarHeight / 2f + 2 * density, 
                             paddingLeft + progressWidth - 2 * density, barY + thickBarHeight / 2f - 2 * density);
            
            int startColor = Color.argb(255, (int)(Color.red(progressColor)*0.8), (int)(Color.green(progressColor)*0.8), (int)(Color.blue(progressColor)*0.8));
            LinearGradient gradient = new LinearGradient(paddingLeft, barY, paddingLeft + progressWidth, barY, startColor, progressColor, Shader.TileMode.CLAMP);
            progressPaint.setShader(gradient);
            canvas.drawRoundRect(progressRect, (thickBarHeight - 4 * density) / 2f, (thickBarHeight - 4 * density) / 2f, progressPaint);
            progressPaint.setShader(null);

            // Percentage Text
            String percentText = (int) (progressRatio * 100) + "%";
            indicatorTextPaint.setColor(Color.WHITE);
            indicatorTextPaint.setTextSize(9 * density);
            indicatorTextPaint.setTextAlign(Paint.Align.RIGHT);
            indicatorTextPaint.getTextBounds(percentText, 0, percentText.length(), textBounds);
            if (progressWidth > textBounds.width() + 12 * density) {
                canvas.drawText(percentText, paddingLeft + progressWidth - 8 * density, barY + textBounds.height() / 2f, indicatorTextPaint);
            }
        }

        // 3. Draw Triangle Pointers
        float pointerX = paddingLeft + progressWidth;
        float pointerSize = 5 * density;
        
        indicatorPath.reset();
        // Top triangle
        indicatorPath.moveTo(pointerX - pointerSize, barY - thickBarHeight / 2f - 2 * density - pointerSize);
        indicatorPath.lineTo(pointerX + pointerSize, barY - thickBarHeight / 2f - 2 * density - pointerSize);
        indicatorPath.lineTo(pointerX, barY - thickBarHeight / 2f - 2 * density);
        indicatorPath.close();
        
        // Bottom triangle
        indicatorPath.moveTo(pointerX - pointerSize, barY + thickBarHeight / 2f + 2 * density + pointerSize);
        indicatorPath.lineTo(pointerX + pointerSize, barY + thickBarHeight / 2f + 2 * density + pointerSize);
        indicatorPath.lineTo(pointerX, barY + thickBarHeight / 2f + 2 * density);
        indicatorPath.close();
        
        progressPaint.setColor(progressColor);
        canvas.drawPath(indicatorPath, progressPaint);
        
        trackPaint.setStyle(Paint.Style.FILL);
    }

    private void drawStyle9(Canvas canvas, float width, float height, float paddingLeft, float paddingRight, float density, float progressRatio) {
        float availableWidth = width - paddingLeft - paddingRight;
        
        float iconSize = 20 * density;
        float barAreaWidth = availableWidth - (iconSize + 8 * density);
        float progressWidth = progressRatio * barAreaWidth;
        
        float thickBarHeight = progressHeight > 0 ? progressHeight : barHeight * 3.5f;
        float barY = height / 2f;
        
        if (!label.isEmpty()) {
            float labelY = 16 * density + getPaddingTop();
            canvas.drawText(label, paddingLeft, labelY, labelPaint);
            barY = height - thickBarHeight / 2f - getPaddingBottom() - 8 * density;
        }

        // 1. Draw Faded Full TrackEntity
        trackRect.set(paddingLeft, barY - thickBarHeight / 2f, paddingLeft + barAreaWidth, barY + thickBarHeight / 2f);
        trackPaint.setColor(progressColor);
        trackPaint.setAlpha(30);
        canvas.drawRoundRect(trackRect, thickBarHeight / 2f, thickBarHeight / 2f, trackPaint);

        // 2. Draw Progress Pill
        if (progressWidth > 0) {
            progressRect.set(paddingLeft, barY - thickBarHeight / 2f, paddingLeft + progressWidth, barY + thickBarHeight / 2f);
            progressPaint.setAlpha(255);
            progressPaint.setColor(progressColor);
            canvas.drawRoundRect(progressRect, thickBarHeight / 2f, thickBarHeight / 2f, progressPaint);
        }

        // 3. Draw Center Text
        String centerText = progress + " / " + max;
        indicatorTextPaint.setColor(progressColor);
        indicatorTextPaint.setTextSize(11 * density);
        indicatorTextPaint.setTextAlign(Paint.Align.CENTER);
        indicatorTextPaint.getTextBounds(centerText, 0, centerText.length(), textBounds);
        canvas.drawText(centerText, paddingLeft + barAreaWidth / 2f, barY + textBounds.height() / 2f, indicatorTextPaint);

        // 4. Draw Icon at the end
        float iconCx = paddingLeft + availableWidth - iconSize / 2f;
        float iconCy = barY;
        
        trackPaint.setAlpha(30);
        canvas.drawCircle(iconCx, iconCy, iconSize / 2f, trackPaint);
        
        drawIndicatorIcon(canvas, iconCx, iconCy, iconSize * 0.35f, progressColor);
        
        trackPaint.setAlpha(255);
    }

    public void setProgressStyle(int style) {
        this.progressStyle = style;
        invalidate();
    }

    public int getProgressStyle() {
        return progressStyle;
    }

    public void setProgress(int progress) {
        if (isDragging) return;
        this.progress = Math.min(progress, max);
        invalidate();
    }

    public int getProgress() {
        return progress;
    }

    public void setMax(int max) {
        this.max = Math.max(1, max);
        invalidate();
    }

    public void setPlaybackState(PlaybackState state) {
        stateHandler.removeCallbacks(revertToNowPlayingTask);
        setLabel(state.getLabel());
        
        if (state == PlaybackState.PLAY_NEXT || state == PlaybackState.PLAY_PREVIOUS) {
            stateHandler.postDelayed(revertToNowPlayingTask, 2000);
        }
    }

    public void setLabel(String label) {
        this.label = label != null ? label : "";
        requestLayout();
        invalidate();
    }

    public void setLabelAlignWithIndicator(boolean align) {
        this.labelAlignWithIndicator = align;
        invalidate();
    }

    public void setProgressHeight(float height) {
        this.progressHeight = height;
        requestLayout();
        invalidate();
    }

    public void setProgressColor(int color) {
        this.progressColor = color;
        if (progressPaint != null) progressPaint.setColor(color);
        invalidate();
    }

    public void setProgressMode(int mode) {
        this.progressMode = mode;
        requestLayout();
        invalidate();
    }

    public int getProgressMode() {
        return progressMode;
    }

    private void drawTooltipVertical(Canvas canvas, float boxY, float tipY, float x, float w, float h, float triangleSize, float density) {
        float radius = 6 * density;
        RectF rect = new RectF(x - triangleSize - w, boxY - h / 2f, x - triangleSize, boxY + h / 2f);
        canvas.drawRoundRect(rect, radius, radius, indicatorPaint);

        indicatorPath.reset();
        float arrowBaseX = x - triangleSize - 2 * density;
        indicatorPath.moveTo(arrowBaseX, tipY - triangleSize);
        indicatorPath.lineTo(x, tipY);
        indicatorPath.lineTo(arrowBaseX, tipY + triangleSize);
        indicatorPath.close();

        canvas.drawPath(indicatorPath, indicatorPaint);
    }

    private void drawVertical(Canvas canvas, float width, float height, float density, float visualRatio) {
        float paddingTop = getPaddingTop();
        float paddingBottom = getPaddingBottom();
        float availableHeight = height - paddingTop - paddingBottom;

        if (progressStyle == 2) {
            drawStyle2Vertical(canvas, width, height, paddingTop, paddingBottom, density, visualRatio);
            return;
        }

        if (progressStyle == 3) {
            drawStyle3Vertical(canvas, width, height, paddingTop, paddingBottom, density, visualRatio);
            return;
        }

        if (progressStyle == 4) {
            drawStyle4Vertical(canvas, width, height, paddingTop, paddingBottom, density, visualRatio);
            return;
        }

        if (progressStyle == 5) {
            drawStyle5Vertical(canvas, width, height, paddingTop, paddingBottom, density, visualRatio);
            return;
        }

        if (progressStyle == 6) {
            drawStyle6Vertical(canvas, width, height, paddingTop, paddingBottom, density, visualRatio);
            return;
        }

        if (progressStyle == 7) {
            drawStyle7Vertical(canvas, width, height, paddingTop, paddingBottom, density, visualRatio);
            return;
        }

        if (progressStyle == 8) {
            drawStyle8Vertical(canvas, width, height, paddingTop, paddingBottom, density, visualRatio);
            return;
        }

        if (progressStyle == 9) {
            drawStyle9Vertical(canvas, width, height, paddingTop, paddingBottom, density, visualRatio);
            return;
        }

        float progressHeightVal = visualRatio * availableHeight;
        float indicatorY = height - paddingBottom - progressHeightVal;

        float currentBarWidth = progressHeight > 0 ? progressHeight : barHeight;
        float paddingLeft = getPaddingLeft();
        float paddingRight = getPaddingRight();
        float barX = paddingLeft + (width - paddingLeft - paddingRight) / 2f;

        // Pre-calculate bubble metrics if needed for label
        float indicatorW = 0;
        float indicatorH = 0;
        float halfH = 0;
        float boxY = indicatorY;
        String progressText = "";
        if (showIndicator) {
            progressText = (int) (visualRatio * 100) + "%";
            indicatorTextPaint.setTextSize(PERCENT_TEXT_SIZE_DP * density);
            indicatorTextPaint.getTextBounds(progressText, 0, progressText.length(), textBounds);
            indicatorW = textBounds.width() + (14 * density);
            indicatorH = textBounds.height() + (8 * density);
            halfH = indicatorH / 2f;

            // Bubble center clamping within View bounds
            if (boxY < paddingTop + halfH) boxY = paddingTop + halfH;
            if (boxY > height - paddingBottom - halfH) boxY = height - paddingBottom - halfH;
        }

        if (!label.isEmpty()) {
            float labelX = paddingLeft;
            float labelY;

            if (labelAlignWithIndicator && showIndicator) {
                labelPaint.getTextBounds(label, 0, label.length(), labelBounds);
                labelPaint.setTextAlign(Paint.Align.LEFT);
                labelY = boxY + labelBounds.height() / 2f;

                if (labelY - labelBounds.height() < paddingTop) {
                    labelY = paddingTop + labelBounds.height();
                }
                if (labelY > height - paddingBottom) {
                    labelY = height - paddingBottom;
                }
            } else {
                labelY = paddingTop + 16 * density;
                labelPaint.setTextAlign(Paint.Align.LEFT);
            }

            canvas.drawText(label, labelX, labelY, labelPaint);
        }

        trackRect.set(barX - currentBarWidth / 2f, paddingTop, barX + currentBarWidth / 2f, height - paddingBottom);
        canvas.drawRoundRect(trackRect, currentBarWidth / 2f, currentBarWidth / 2f, trackPaint);
        progressRect.set(barX - currentBarWidth / 2f, indicatorY, barX + currentBarWidth / 2f, height - paddingBottom);
        canvas.drawRoundRect(progressRect, currentBarWidth / 2f, currentBarWidth / 2f, progressPaint);

        if (showIndicator) {
            float triangleSize = 5 * density;
            // The tip points to the left edge of the track
            float tipX = barX - currentBarWidth / 2f - 3 * density;

            float tipMin = boxY - halfH + triangleSize;
            float tipMax = boxY + halfH - triangleSize;

            float tipY = indicatorY;
            if (tipY < tipMin) tipY = tipMin;
            if (tipY > tipMax) tipY = tipMax;

            // DRAW SHAPE
            drawTooltipVertical(canvas, boxY, tipY, tipX, indicatorW, indicatorH, triangleSize, density);

            // DRAW TEXT
            indicatorTextPaint.setTextSize(PERCENT_TEXT_SIZE_DP * density);
            indicatorTextPaint.getTextBounds(progressText, 0, progressText.length(), textBounds);
            float textX = tipX - triangleSize - indicatorW / 2f;
            float textY = boxY + textBounds.height() / 2f;
            canvas.drawText(progressText, textX, textY, indicatorTextPaint);
        }
    }

    private void drawStyle2Vertical(Canvas canvas, float width, float height, float paddingTop, float paddingBottom, float density, float progressRatio) {
        float segmentLength = 3 * density;
        float segmentSpacing = 3 * density;
        float segmentWidth = progressHeight > 0 ? progressHeight / 2f : barHeight;
        float activeSegmentWidth = progressHeight > 0 ? progressHeight : barHeight * 3.0f;

        float paddingLeft = getPaddingLeft();
        float paddingRight = getPaddingRight();
        float barX = paddingLeft + (width - paddingLeft - paddingRight) / 2f;

        // Label logic
        float labelY = paddingTop + 16 * density;
        if (!label.isEmpty()) {
            canvas.drawText(label, paddingLeft, labelY, labelPaint);
        }

        float percentAreaHeight = getPercentTextWidth(density);
        float availableBarHeight = height - paddingTop - paddingBottom - percentAreaHeight;

        int numSegments = (int) (availableBarHeight / (segmentLength + segmentSpacing));

        int indicatorIndex = (int) (progressRatio * (numSegments - 1));

        for (int i = 0; i < numSegments; i++) {
            float y = height - paddingBottom - i * (segmentLength + segmentSpacing) - segmentLength;

            boolean isIndicator = (i == indicatorIndex);
            boolean isActive = (i <= indicatorIndex);

            float w = isIndicator ? activeSegmentWidth : segmentWidth;
            float xOffset = w / 2f;

            trackRect.set(barX - xOffset, y, barX + xOffset, y + segmentLength);

            if (isActive) {
                canvas.drawRoundRect(trackRect, 1.25f * density, 1.25f * density, progressPaint);
            } else {
                canvas.drawRoundRect(trackRect, 1.25f * density, 1.25f * density, trackPaint);
            }
        }

        String percentText = (int) (progressRatio * 100) + "%";
        indicatorTextPaint.setColor(progressColor);
        indicatorTextPaint.setTextSize(PERCENT_TEXT_SIZE_DP * density);
        indicatorTextPaint.setTextAlign(Paint.Align.CENTER);

        float textX = barX;
        float textY = paddingTop + percentAreaHeight - 4 * density;
        canvas.drawText(percentText, textX, textY, indicatorTextPaint);
    }

    private void drawStyle3Vertical(Canvas canvas, float width, float height, float paddingTop, float paddingBottom, float density, float progressRatio) {
        float paddingLeft = getPaddingLeft();
        float paddingRight = getPaddingRight();
        float availableHeight = height - paddingTop - paddingBottom;
        float progressHeightVal = progressRatio * availableHeight;

        float barWidthStyle3 = progressHeight > 0 ? progressHeight : barHeight + 4 * density;
        float barX = paddingLeft + (width - paddingLeft - paddingRight) / 2f;

        if (!label.isEmpty()) {
            float labelX = paddingLeft;
            canvas.drawText(label, labelX, paddingTop + 16 * density, labelPaint);
        }

        trackRect.set(barX - barWidthStyle3 / 2f, paddingTop, barX + barWidthStyle3 / 2f, height - paddingBottom);
        trackPaint.setStyle(Paint.Style.STROKE);
        trackPaint.setStrokeWidth(1.2f * density);
        trackPaint.setColor(progressColor);
        trackPaint.setAlpha(100);
        canvas.drawRoundRect(trackRect, barWidthStyle3 / 2f, barWidthStyle3 / 2f, trackPaint);

        if (progressHeightVal > 0) {
            progressRect.set(barX - barWidthStyle3 / 2f + 1.5f * density,
                             height - paddingBottom - progressHeightVal,
                             barX + barWidthStyle3 / 2f - 1.5f * density,
                             height - paddingBottom - 1.5f * density);

            int startColor = progressColor;
            int endColor = Color.argb(80, Color.red(progressColor), Color.green(progressColor), Color.blue(progressColor));

            LinearGradient gradient = new LinearGradient(barX, height - paddingBottom, barX, height - paddingBottom - progressHeightVal,
                    startColor, endColor, Shader.TileMode.CLAMP);

            progressPaint.setShader(gradient);
            progressPaint.setAlpha(255);
            canvas.drawRoundRect(progressRect, (barWidthStyle3 - 3 * density) / 2f, (barWidthStyle3 - 3 * density) / 2f, progressPaint);
            progressPaint.setShader(null);
        }

        float thumbHeight = 3 * density;
        float thumbY = height - paddingBottom - progressHeightVal;
        if (thumbY < paddingTop + thumbHeight / 2f + 1.5f * density) thumbY = paddingTop + thumbHeight / 2f + 1.5f * density;
        if (thumbY > height - paddingBottom - thumbHeight / 2f - 1.5f * density) thumbY = height - paddingBottom - thumbHeight / 2f - 1.5f * density;

        RectF thumbRect = new RectF(barX - barWidthStyle3 / 2f + 1.5f * density, thumbY - thumbHeight / 2f,
                                     barX + barWidthStyle3 / 2f - 1.5f * density, thumbY + thumbHeight / 2f);
        progressPaint.setStyle(Paint.Style.FILL);
        progressPaint.setColor(progressColor);
        canvas.drawRoundRect(thumbRect, 1 * density, 1 * density, progressPaint);

        String percentText = (int) (progressRatio * 100) + "%";
        indicatorTextPaint.setColor(progressColor);
        indicatorTextPaint.setAlpha(180);
        indicatorTextPaint.setTextSize(10 * density);
        indicatorTextPaint.setTextAlign(Paint.Align.LEFT);
        indicatorTextPaint.getTextBounds(percentText, 0, percentText.length(), textBounds);

        float textX = barX + barWidthStyle3 / 2f + 4 * density;
        float textY = thumbY + textBounds.height() / 2f;
        canvas.drawText(percentText, textX, textY, indicatorTextPaint);

        trackPaint.setStyle(Paint.Style.FILL);
        trackPaint.setColor(trackColor);
        trackPaint.setAlpha(255);
    }

    private void drawStyle4Vertical(Canvas canvas, float width, float height, float paddingTop, float paddingBottom, float density, float progressRatio) {
        float paddingLeft = getPaddingLeft();
        float paddingRight = getPaddingRight();
        float availableHeight = height - paddingTop - paddingBottom;
        float progressHeightVal = progressRatio * availableHeight;

        float thumbRadius = progressHeight > 0 ? progressHeight / 2f : barHeight * 1.1f;
        float barX = paddingLeft + (width - paddingLeft - paddingRight) / 2f;

        if (!label.isEmpty()) {
            float labelX = paddingLeft;
            canvas.drawText(label, labelX, paddingTop + 16 * density, labelPaint);
        }

        trackRect.set(barX - barHeight / 2f, paddingTop, barX + barHeight / 2f, height - paddingBottom);
        trackPaint.setColor(trackColor);
        canvas.drawRoundRect(trackRect, barHeight / 2f, barHeight / 2f, trackPaint);

        if (progressHeightVal > 0) {
            progressRect.set(barX - barHeight / 2f, height - paddingBottom - progressHeightVal, barX + barHeight / 2f, height - paddingBottom);

            int startColor = Color.argb(255,
                (int)(Color.red(progressColor) * 0.7f),
                (int)(Color.green(progressColor) * 0.7f),
                (int)(Color.blue(progressColor) * 0.7f));
            int endColor = progressColor;

            LinearGradient gradient = new LinearGradient(barX, height - paddingBottom, barX, height - paddingBottom - progressHeightVal,
                    startColor, endColor, Shader.TileMode.CLAMP);

            progressPaint.setShader(gradient);
            canvas.drawRoundRect(progressRect, barHeight / 2f, barHeight / 2f, progressPaint);
            progressPaint.setShader(null);
        }

        float thumbY = height - paddingBottom - progressHeightVal;

        progressPaint.setStyle(Paint.Style.STROKE);
        progressPaint.setStrokeWidth(2 * density);
        progressPaint.setColor(progressColor);
        canvas.drawCircle(barX, thumbY, thumbRadius, progressPaint);

        progressPaint.setStyle(Paint.Style.FILL);
        progressPaint.setColor(Color.WHITE);
        canvas.drawCircle(barX, thumbY, thumbRadius - 1 * density, progressPaint);

        progressPaint.setStyle(Paint.Style.FILL);
    }

    private void drawStyle5Vertical(Canvas canvas, float width, float height, float paddingTop, float paddingBottom, float density, float progressRatio) {
        float paddingLeft = getPaddingLeft();
        float paddingRight = getPaddingRight();
        float availableHeight = height - paddingTop - paddingBottom;
        float progressHeightVal = progressRatio * availableHeight;

        float boxWidth = progressHeight > 0 ? progressHeight : barHeight * 2.5f;
        float barX = paddingLeft + (width - paddingLeft - paddingRight) / 2f;

        if (!label.isEmpty()) {
            float labelX = paddingLeft;
            canvas.drawText(label, labelX, paddingTop + 16 * density, labelPaint);
        }

        trackRect.set(barX - boxWidth / 2f, paddingTop, barX + boxWidth / 2f, height - paddingBottom);
        trackPaint.setStyle(Paint.Style.STROKE);
        trackPaint.setStrokeWidth(1 * density);
        trackPaint.setColor(progressColor);
        trackPaint.setAlpha(150);
        canvas.drawRect(trackRect, trackPaint);

        if (progressHeightVal > 0) {
            float innerPadding = 2 * density;
            float innerLeft = barX - boxWidth / 2f + innerPadding;
            float innerRight = barX + boxWidth / 2f - innerPadding;
            float innerBottom = height - paddingBottom - innerPadding;
            float innerTop = height - paddingBottom - progressHeightVal;

            if (innerTop < paddingTop + innerPadding) {
                innerTop = paddingTop + innerPadding;
            }

            if (innerTop < innerBottom) {
                indicatorPath.reset();
                indicatorPath.moveTo(innerLeft, innerBottom);

                float chevronSize = (innerRight - innerLeft) / 2f;

                if (innerTop < innerBottom - chevronSize) {
                    indicatorPath.lineTo(innerLeft, innerTop + chevronSize);
                    indicatorPath.lineTo(innerLeft + chevronSize, innerTop);
                    indicatorPath.lineTo(innerRight, innerTop + chevronSize);
                } else {
                    indicatorPath.lineTo(innerLeft + chevronSize, innerTop);
                }

                indicatorPath.lineTo(innerRight, innerBottom);
                indicatorPath.close();

                int startColor = progressColor;
                int endColor = Color.argb(120, Color.red(progressColor), Color.green(progressColor), Color.blue(progressColor));

                LinearGradient gradient = new LinearGradient(barX, innerBottom, barX, innerTop,
                        startColor, endColor, Shader.TileMode.CLAMP);

                progressPaint.setShader(gradient);
                progressPaint.setStyle(Paint.Style.FILL);
                progressPaint.setAlpha(255);
                canvas.drawPath(indicatorPath, progressPaint);
                progressPaint.setShader(null);
            }
        }

        trackPaint.setStyle(Paint.Style.FILL);
        trackPaint.setColor(trackColor);
        trackPaint.setAlpha(255);
    }

    private void drawStyle6Vertical(Canvas canvas, float width, float height, float paddingTop, float paddingBottom, float density, float progressRatio) {
        float paddingLeft = getPaddingLeft();
        float paddingRight = getPaddingRight();

        float thickBarWidth = progressHeight > 0 ? progressHeight : barHeight * 5.0f;
        float barX = paddingLeft + (width - paddingLeft - paddingRight) / 2f;
        float radius = thickBarWidth / 2f;

        if (!label.isEmpty()) {
            float labelX = paddingLeft;
            canvas.drawText(label, labelX, paddingTop + 16 * density, labelPaint);
        }

        trackRect.set(barX - radius, paddingTop, barX + radius, height - paddingBottom);
        trackPaint.setColor(progressColor);
        trackPaint.setAlpha(30);
        canvas.drawRoundRect(trackRect, radius, radius, trackPaint);

        float minThumbY = height - paddingBottom - radius;
        float maxThumbY = paddingTop + radius;
        float thumbY = minThumbY - progressRatio * (minThumbY - maxThumbY);

        progressPaint.setColor(progressColor);
        progressPaint.setAlpha(255);

        progressRect.set(barX - radius, thumbY - radius, barX + radius, height - paddingBottom);
        canvas.drawRoundRect(progressRect, radius, radius, progressPaint);

        float thumbRadius = radius * 0.65f;
        progressPaint.setColor(Color.WHITE);
        progressPaint.setStyle(Paint.Style.FILL);
        canvas.drawCircle(barX, thumbY, thumbRadius, progressPaint);

        trackPaint.setAlpha(255);
        trackPaint.setColor(trackColor);
        progressPaint.setColor(progressColor);
    }

    private void drawStyle7Vertical(Canvas canvas, float width, float height, float paddingTop, float paddingBottom, float density, float progressRatio) {
        float paddingLeft = getPaddingLeft();
        float paddingRight = getPaddingRight();
        float availableHeight = height - paddingTop - paddingBottom;

        float iconRadius = 14 * density;
        float barAreaHeight = availableHeight - (iconRadius * 2 + 12 * density);
        float progressHeightVal = progressRatio * barAreaHeight;

        float thickBarWidth = progressHeight > 0 ? progressHeight : barHeight * 2.5f;
        float barX = paddingLeft + (width - paddingLeft - paddingRight) / 2f;

        if (!label.isEmpty()) {
            float labelX = paddingLeft;
            canvas.drawText(label, labelX, paddingTop + 16 * density, labelPaint);
        }

        float pillH = progressHeightVal;
        if (pillH < thickBarWidth) pillH = thickBarWidth;

        float pillTop = height - paddingBottom - pillH;
        trackRect.set(barX - thickBarWidth / 2f, pillTop, barX + thickBarWidth / 2f, height - paddingBottom);

        int startColor = Color.argb(100, Color.red(progressColor), Color.green(progressColor), Color.blue(progressColor));
        int endColor = progressColor;
        LinearGradient gradient = new LinearGradient(barX, height - paddingBottom, barX, pillTop, startColor, endColor, Shader.TileMode.CLAMP);
        progressPaint.setShader(gradient);
        canvas.drawRoundRect(trackRect, thickBarWidth / 2f, thickBarWidth / 2f, progressPaint);
        progressPaint.setShader(null);

        trackPaint.setStyle(Paint.Style.STROKE);
        trackPaint.setStrokeWidth(1.2f * density);
        trackPaint.setColor(progressColor);
        canvas.drawRoundRect(trackRect, thickBarWidth / 2f, thickBarWidth / 2f, trackPaint);

        String percentText = (int) (progressRatio * 100) + "%";
        indicatorTextPaint.setColor(Color.WHITE);
        indicatorTextPaint.setTextSize(10 * density);
        indicatorTextPaint.setTextAlign(Paint.Align.CENTER);
        indicatorTextPaint.getTextBounds(percentText, 0, percentText.length(), textBounds);
        canvas.drawText(percentText, barX, pillTop + 8 * density + textBounds.height() / 2f, indicatorTextPaint);

        float lineBottomY = pillTop;
        float lineTopY = paddingTop + iconRadius * 2;
        if (lineBottomY > lineTopY) {
            trackPaint.setStyle(Paint.Style.STROKE);
            trackPaint.setAlpha(150);
            canvas.drawLine(barX, lineBottomY, barX, lineTopY, trackPaint);
        }

        float iconCx = barX;
        float iconCy = paddingTop + iconRadius;

        trackPaint.setStyle(Paint.Style.STROKE);
        trackPaint.setAlpha(255);
        canvas.drawCircle(iconCx, iconCy, iconRadius, trackPaint);

        drawIndicatorIcon(canvas, iconCx, iconCy, iconRadius * 0.6f, progressColor);

        trackPaint.setStyle(Paint.Style.FILL);
    }

    private void drawStyle8Vertical(Canvas canvas, float width, float height, float paddingTop, float paddingBottom, float density, float progressRatio) {
        float paddingLeft = getPaddingLeft();
        float paddingRight = getPaddingRight();
        float availableHeight = height - paddingTop - paddingBottom;
        float progressHeightVal = progressRatio * availableHeight;

        float thickBarWidth = progressHeight > 0 ? progressHeight : barHeight * 2.5f;
        float barX = paddingLeft + (width - paddingLeft - paddingRight) / 2f;

        if (!label.isEmpty()) {
            float labelX = paddingLeft;
            canvas.drawText(label, labelX, paddingTop + 16 * density, labelPaint);
        }

        trackRect.set(barX - thickBarWidth / 2f, paddingTop, barX + thickBarWidth / 2f, height - paddingBottom);
        trackPaint.setStyle(Paint.Style.STROKE);
        trackPaint.setStrokeWidth(1.2f * density);
        trackPaint.setColor(progressColor);
        canvas.drawRoundRect(trackRect, thickBarWidth / 2f, thickBarWidth / 2f, trackPaint);

        if (progressHeightVal > 2 * density) {
            progressRect.set(barX - thickBarWidth / 2f + 2 * density,
                             height - paddingBottom - progressHeightVal + 2 * density,
                             barX + thickBarWidth / 2f - 2 * density,
                             height - paddingBottom - 2 * density);

            int startColor = Color.argb(255, (int)(Color.red(progressColor)*0.8), (int)(Color.green(progressColor)*0.8), (int)(Color.blue(progressColor)*0.8));
            LinearGradient gradient = new LinearGradient(barX, height - paddingBottom, barX, height - paddingBottom - progressHeightVal, startColor, progressColor, Shader.TileMode.CLAMP);
            progressPaint.setShader(gradient);
            canvas.drawRoundRect(progressRect, (thickBarWidth - 4 * density) / 2f, (thickBarWidth - 4 * density) / 2f, progressPaint);
            progressPaint.setShader(null);

            String percentText = (int) (progressRatio * 100) + "%";
            indicatorTextPaint.setColor(Color.WHITE);
            indicatorTextPaint.setTextSize(9 * density);
            indicatorTextPaint.setTextAlign(Paint.Align.CENTER);
            indicatorTextPaint.getTextBounds(percentText, 0, percentText.length(), textBounds);
            if (progressHeightVal > textBounds.height() + 12 * density) {
                canvas.drawText(percentText, barX, height - paddingBottom - progressHeightVal + 8 * density + textBounds.height() / 2f, indicatorTextPaint);
            }
        }

        float pointerY = height - paddingBottom - progressHeightVal;
        float pointerSize = 5 * density;

        indicatorPath.reset();
        indicatorPath.moveTo(barX - thickBarWidth / 2f - 2 * density - pointerSize, pointerY - pointerSize);
        indicatorPath.lineTo(barX - thickBarWidth / 2f - 2 * density - pointerSize, pointerY + pointerSize);
        indicatorPath.lineTo(barX - thickBarWidth / 2f - 2 * density, pointerY);
        indicatorPath.close();

        indicatorPath.moveTo(barX + thickBarWidth / 2f + 2 * density + pointerSize, pointerY - pointerSize);
        indicatorPath.lineTo(barX + thickBarWidth / 2f + 2 * density + pointerSize, pointerY + pointerSize);
        indicatorPath.lineTo(barX + thickBarWidth / 2f + 2 * density, pointerY);
        indicatorPath.close();

        progressPaint.setColor(progressColor);
        canvas.drawPath(indicatorPath, progressPaint);

        trackPaint.setStyle(Paint.Style.FILL);
    }

    private void drawStyle9Vertical(Canvas canvas, float width, float height, float paddingTop, float paddingBottom, float density, float progressRatio) {
        float paddingLeft = getPaddingLeft();
        float paddingRight = getPaddingRight();
        float availableHeight = height - paddingTop - paddingBottom;

        float iconSize = 20 * density;
        float barAreaHeight = availableHeight - (iconSize + 8 * density);
        float progressHeightVal = progressRatio * barAreaHeight;

        float thickBarWidth = progressHeight > 0 ? progressHeight : barHeight * 3.5f;
        float barX = paddingLeft + (width - paddingLeft - paddingRight) / 2f;

        if (!label.isEmpty()) {
            float labelX = paddingLeft;
            canvas.drawText(label, labelX, paddingTop + 16 * density, labelPaint);
        }

        float trackTop = paddingTop + iconSize + 8 * density;
        trackRect.set(barX - thickBarWidth / 2f, trackTop, barX + thickBarWidth / 2f, height - paddingBottom);
        trackPaint.setColor(progressColor);
        trackPaint.setAlpha(30);
        canvas.drawRoundRect(trackRect, thickBarWidth / 2f, thickBarWidth / 2f, trackPaint);

        if (progressHeightVal > 0) {
            float progressTop = height - paddingBottom - progressHeightVal;
            if (progressTop < trackTop) progressTop = trackTop;
            progressRect.set(barX - thickBarWidth / 2f, progressTop, barX + thickBarWidth / 2f, height - paddingBottom);
            progressPaint.setAlpha(255);
            progressPaint.setColor(progressColor);
            canvas.drawRoundRect(progressRect, thickBarWidth / 2f, thickBarWidth / 2f, progressPaint);
        }

        String centerText = progress + " / " + max;
        indicatorTextPaint.setColor(progressColor);
        indicatorTextPaint.setTextSize(11 * density);
        indicatorTextPaint.setTextAlign(Paint.Align.CENTER);
        indicatorTextPaint.getTextBounds(centerText, 0, centerText.length(), textBounds);
        canvas.drawText(centerText, barX, trackTop + (height - paddingBottom - trackTop) / 2f + textBounds.height() / 2f, indicatorTextPaint);

        float iconCx = barX;
        float iconCy = paddingTop + iconSize / 2f;

        trackPaint.setAlpha(30);
        canvas.drawCircle(iconCx, iconCy, iconSize / 2f, trackPaint);

        drawIndicatorIcon(canvas, iconCx, iconCy, iconSize * 0.35f, progressColor);

        trackPaint.setAlpha(255);
    }
}
