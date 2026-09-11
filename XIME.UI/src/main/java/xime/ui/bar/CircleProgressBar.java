package xime.ui.bar;

import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.MotionEvent;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import xime.R;
import xime.ui.event.ParentEvent;

import xime.ui.view.View;

public class CircleProgressBar extends View {

    private int progress = 0;
    private int max = 100;
    private int progressColor = 0xFFD81B60; // Pink
    private int trackColor = 0xFF333333; // Dark Grey
    private String label = "";
    private int labelColor = Color.WHITE;

    private Paint ringPaint;
    private Paint progressArcPaint;
    private Paint dotPaint;
    private Paint labelPaint;
    private Paint ticksPaint;

    private final RectF arcRect = new RectF();
    private final Rect labelBounds = new Rect();

    private float centerX;
    private float centerY;
    private float radius;

    private boolean isDragging = false;
    private OnSeekBarChangeListener onSeekBarChangeListener;

    public interface OnSeekBarChangeListener {
        void onProgressChanged(CircleProgressBar circularTrackBar, int progress, boolean fromUser);
        void onStartTrackingTouch(CircleProgressBar circularTrackBar);
        void onStopTrackingTouch(CircleProgressBar circularTrackBar);
    }

    public CircleProgressBar(Context context) {
        super(context);
        init(context, null);
    }

    public CircleProgressBar(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init(context, attrs);
    }

    public CircleProgressBar(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init(context, attrs);
    }

    private void init(Context context, AttributeSet attrs) {
        float density = getResources().getDisplayMetrics().density;

        if (attrs != null) {
            TypedArray a = context.obtainStyledAttributes(attrs, R.styleable.LinearProgressBar);
            try {
                progress = a.getInt(R.styleable.LinearProgressBar_xoerisProgress, 0);
                max = a.getInt(R.styleable.LinearProgressBar_xoerisMax, 100);
                progressColor = a.getColor(R.styleable.LinearProgressBar_xoerisProgressColor, 0xFFD81B60);
                trackColor = a.getColor(R.styleable.LinearProgressBar_xoerisTrackColor, 0xFF333333);
                label = a.getString(R.styleable.LinearProgressBar_xoerisLabel);
                if (label == null) label = "";
                labelColor = a.getColor(R.styleable.LinearProgressBar_xoerisLabelColor, Color.WHITE);
            } finally {
                a.recycle();
            }
        }

        ringPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        ringPaint.setColor(trackColor);
        ringPaint.setStyle(Paint.Style.STROKE);
        ringPaint.setStrokeWidth(4 * density);
        ringPaint.setStrokeCap(Paint.Cap.ROUND);

        progressArcPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        progressArcPaint.setColor(progressColor);
        progressArcPaint.setStyle(Paint.Style.STROKE);
        progressArcPaint.setStrokeWidth(6 * density);
        progressArcPaint.setStrokeCap(Paint.Cap.ROUND);

        dotPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        dotPaint.setColor(progressColor);
        dotPaint.setStyle(Paint.Style.FILL);

        labelPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        labelPaint.setColor(labelColor);
        labelPaint.setTextSize(12 * density);
        labelPaint.setTextAlign(Paint.Align.CENTER);

        ticksPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        ticksPaint.setColor(Color.argb(80, 255, 255, 255));
        ticksPaint.setStyle(Paint.Style.STROKE);
        ticksPaint.setStrokeWidth(1.5f * density);
    }

    public void setOnSeekBarChangeListener(OnSeekBarChangeListener l) {
        this.onSeekBarChangeListener = l;
    }

    @Override
    public boolean performClick() {
        return super.performClick();
    }

    private void requestDisallowIntercept(boolean disallow) {
        ParentEvent parent = getBaseParent();
        while (parent != null) {
            parent.requestDisallowInterceptTouchEvent(disallow);
            parent = parent.getBaseParent();
        }
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        float x = event.getX();
        float y = event.getY();

        switch (event.getAction()) {
            case MotionEvent.ACTION_DOWN:
                isDragging = true;
                requestDisallowIntercept(true);
                updateProgressFromTouch(x, y);
                if (onSeekBarChangeListener != null) onSeekBarChangeListener.onStartTrackingTouch(this);
                performClick();
                return true;
            case MotionEvent.ACTION_MOVE:
                requestDisallowIntercept(true);
                updateProgressFromTouch(x, y);
                return true;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                isDragging = false;
                requestDisallowIntercept(false);
                if (onSeekBarChangeListener != null) onSeekBarChangeListener.onStopTrackingTouch(this);
                invalidate();
                return true;
        }
        return super.onTouchEvent(event);
    }

    private void updateProgressFromTouch(float x, float y) {
        double angleRad = Math.atan2(y - centerY, x - centerX);
        double angleDeg = Math.toDegrees(angleRad);

        angleDeg += 90;
        if (angleDeg < 0) angleDeg += 360;

        double startAngle = 225;
        double currentAngle = angleDeg - startAngle;
        if (currentAngle < 0) currentAngle += 360;

        if (currentAngle > 270 + 45) {
            currentAngle = 0;
        } else if (currentAngle > 270) {
            currentAngle = 270;
        }

        float ratio = (float) (currentAngle / 270f);
        this.progress = Math.round(ratio * max);
        if (onSeekBarChangeListener != null) {
            onSeekBarChangeListener.onProgressChanged(this, progress, true);
        }
        invalidate();
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        float density = getResources().getDisplayMetrics().density;
        int size = MeasureSpec.getSize(widthMeasureSpec);
        if (size <= 0) size = (int) (120 * density);

        int h = size;
        if (!label.isEmpty()) {
            h += (24 * density);
        }

        setMeasuredDimension(size, h);
    }

    @Override
    protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        super.onLayout(changed, left, top, right, bottom);
        float density = getResources().getDisplayMetrics().density;

        centerX = getWidth() / 2f;
        float heightLimit = !label.isEmpty() ? (getHeight() - 24 * density) : getHeight();
        centerY = heightLimit / 2f;
        radius = (Math.min(getWidth(), heightLimit) - 30 * density) / 2f;

        arcRect.set(centerX - radius, centerY - radius, centerX + radius, centerY + radius);
    }

    @Override
    protected void onDraw(@NonNull Canvas canvas) {
        super.onDraw(canvas);
        float density = getResources().getDisplayMetrics().density;

        float startAngle = 135f;
        float sweepAngle = 270f;
        canvas.drawArc(arcRect, startAngle, sweepAngle, false, ringPaint);

        float progressSweep = ((float) progress / max) * sweepAngle;
        canvas.drawArc(arcRect, startAngle, progressSweep, false, progressArcPaint);

        Paint innerKnobPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        innerKnobPaint.setColor(0xFF1E1E1E);
        innerKnobPaint.setStyle(Paint.Style.FILL);
        canvas.drawCircle(centerX, centerY, radius - 8 * density, innerKnobPaint);

        innerKnobPaint.setColor(0xFF2E2E2E);
        innerKnobPaint.setStyle(Paint.Style.STROKE);
        innerKnobPaint.setStrokeWidth(1 * density);
        canvas.drawCircle(centerX, centerY, radius - 8 * density, innerKnobPaint);

        float dotAngle = startAngle + progressSweep;
        float dotRad = (float) Math.toRadians(dotAngle);
        float dotX = centerX + (radius - 16 * density) * (float) Math.cos(dotRad);
        float dotY = centerY + (radius - 16 * density) * (float) Math.sin(dotRad);
        canvas.drawCircle(dotX, dotY, 4 * density, dotPaint);

        int tickCount = 11;
        for (int i = 0; i < tickCount; i++) {
            float angle = startAngle + (i * (sweepAngle / (tickCount - 1)));
            float rad = (float) Math.toRadians(angle);
            float startX = centerX + (radius + 6 * density) * (float) Math.cos(rad);
            float startY = centerY + (radius + 6 * density) * (float) Math.sin(rad);
            float endX = centerX + (radius + 10 * density) * (float) Math.cos(rad);
            float endY = centerY + (radius + 10 * density) * (float) Math.sin(rad);
            
            if (angle <= startAngle + progressSweep) {
                ticksPaint.setColor(progressColor);
            } else {
                ticksPaint.setColor(Color.argb(80, 255, 255, 255));
            }
            canvas.drawLine(startX, startY, endX, endY, ticksPaint);
        }

        if (!label.isEmpty()) {
            float labelY = getHeight() - 8 * density;
            canvas.drawText(label, centerX, labelY, labelPaint);
        }
    }

    public void setProgress(int progress) {
        this.progress = Math.max(0, Math.min(progress, max));
        invalidate();
    }

    public int getProgress() {
        return progress;
    }

    public void setMax(int max) {
        this.max = Math.max(1, max);
        invalidate();
    }

    public int getMax() {
        return max;
    }

    public void setLabel(String label) {
        this.label = label != null ? label : "";
        requestLayout();
        invalidate();
    }

    public String getLabel() {
        return label;
    }

    public void setProgressColor(int color) {
        this.progressColor = color;
        progressArcPaint.setColor(color);
        dotPaint.setColor(color);
        invalidate();
    }
}
