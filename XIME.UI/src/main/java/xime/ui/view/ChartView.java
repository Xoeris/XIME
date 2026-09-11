package xime.ui.view;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Shader;
import android.util.AttributeSet;
import android.view.animation.DecelerateInterpolator;
import androidx.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;

/**
 * ChartView - Multi-mode analytics chart widget extending xime.ui.view.View.
 * Default card rounded corner radius is 25dp with glassmorphic border outline.
 * Supports LINE_CURVE (cubic spline), BAR (rounded vertical bars), and PILL_TRACK (capsule bars with full background tracks).
 */
public class ChartView extends View {

    public enum ChartMode {
        LINE_CURVE, // Cubic spline smooth curve line
        BAR,        // Rounded vertical bar chart
        PILL_TRACK  // Vertical capsule bars with background tracks
    }

    public static class DataPoint {
        public float value;
        public String label;
        public boolean isHighlighted;

        public DataPoint(float value, String label) {
            this.value = value;
            this.label = label;
            this.isHighlighted = false;
        }

        public DataPoint(float value, String label, boolean isHighlighted) {
            this.value = value;
            this.label = label;
            this.isHighlighted = isHighlighted;
        }
    }

    private boolean mShowLabels = true;  // Enables X-axis labels by default
    private boolean mDrawCardBackground = true;
    private boolean mShowBorder = true; // Enables border outline by default
    private float mCornerRadius = 25f;  // Default card corner radius is 25dp
    private float mBorderWidth = 1f;    // 1dp border stroke width
    private int mBorderColor = Color.parseColor("#25FFFFFF"); // Subtle glass border outline

    private ChartMode mChartMode = ChartMode.LINE_CURVE;
    private final List<DataPoint> mDataPoints = new ArrayList<>();

    private final Paint mLinePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint mFillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint mDotPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint mTextPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint mAxisPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint mBarPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint mTrackPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint mGridPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint mBgPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint mBorderPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    private final Path mLinePath = new Path();
    private final Path mFillPath = new Path();
    private final RectF mBarRect = new RectF();
    private final RectF mBgRect = new RectF();
    private final RectF mBorderRect = new RectF();

    private int mLineColor = Color.parseColor("#FFD600"); // Vibrant yellow gold
    private int mAccentColor = Color.parseColor("#38BDF8"); // Sky blue accent
    private int mCardBackgroundColor = Color.parseColor("#121214");
    private float mAnimProgress = 1.0f;
    private ValueAnimator mAnimator;

    public ChartView(Context context) {
        super(context);
        init();
    }

    public ChartView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public ChartView(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        float density = getResources().getDisplayMetrics().density;

        mLinePaint.setStyle(Paint.Style.STROKE);
        mLinePaint.setStrokeWidth(3.5f * density);
        mLinePaint.setColor(mLineColor);
        mLinePaint.setStrokeCap(Paint.Cap.ROUND);
        mLinePaint.setStrokeJoin(Paint.Join.ROUND);

        mFillPaint.setStyle(Paint.Style.FILL);

        mDotPaint.setStyle(Paint.Style.FILL);
        mDotPaint.setColor(mLineColor);

        mAxisPaint.setStyle(Paint.Style.STROKE);
        mAxisPaint.setStrokeWidth(1.0f * density);
        mAxisPaint.setColor(Color.parseColor("#2C2C2E"));

        mGridPaint.setStyle(Paint.Style.STROKE);
        mGridPaint.setStrokeWidth(1.0f * density);
        mGridPaint.setColor(Color.parseColor("#1F1F24"));

        mBarPaint.setStyle(Paint.Style.FILL);
        mTrackPaint.setStyle(Paint.Style.FILL);
        mTrackPaint.setColor(Color.parseColor("#15FFFFFF"));

        mBgPaint.setStyle(Paint.Style.FILL);
        mBgPaint.setColor(mCardBackgroundColor);

        mBorderPaint.setStyle(Paint.Style.STROKE);
        mBorderPaint.setStrokeWidth(mBorderWidth * density);
        mBorderPaint.setColor(mBorderColor);

        mTextPaint.setColor(Color.parseColor("#8E8E93"));
        mTextPaint.setTextSize(10f * density);
        mTextPaint.setTextAlign(Paint.Align.CENTER);

        // Initially collapsed until data points are supplied
        setVisibility(GONE);
    }

    public void setCornerRadius(float cornerRadiusDp) {
        this.mCornerRadius = cornerRadiusDp;
        invalidate();
    }

    public float getCornerRadius() {
        return mCornerRadius;
    }

    public void setShowBorder(boolean showBorder) {
        this.mShowBorder = showBorder;
        invalidate();
    }

    public boolean getShowBorder() {
        return mShowBorder;
    }

    public void setBorderColor(int color) {
        this.mBorderColor = color;
        mBorderPaint.setColor(color);
        invalidate();
    }

    public void setBorderWidth(float widthDp) {
        this.mBorderWidth = widthDp;
        float density = getResources().getDisplayMetrics().density;
        mBorderPaint.setStrokeWidth(widthDp * density);
        invalidate();
    }

    public void setDrawCardBackground(boolean drawCardBackground) {
        this.mDrawCardBackground = drawCardBackground;
        invalidate();
    }

    public void setShowLabels(boolean showLabels) {
        this.mShowLabels = showLabels;
        invalidate();
    }

    public void setChartMode(ChartMode mode) {
        this.mChartMode = mode;
        animateChart();
    }

    public ChartMode getChartMode() {
        return mChartMode;
    }

    public void setDataPoints(List<DataPoint> points) {
        mDataPoints.clear();
        if (points != null && !points.isEmpty()) {
            mDataPoints.addAll(points);
            setVisibility(VISIBLE);
            animateChart();
        } else {
            setVisibility(GONE);
            invalidate();
        }
    }

    public void setLineColor(int color) {
        this.mLineColor = color;
        mLinePaint.setColor(color);
        mDotPaint.setColor(color);
        invalidate();
    }

    public void animateChart() {
        if (mAnimator != null && mAnimator.isRunning()) {
            mAnimator.cancel();
        }
        mAnimator = ValueAnimator.ofFloat(0f, 1f);
        mAnimator.setDuration(800);
        mAnimator.setInterpolator(new DecelerateInterpolator());
        mAnimator.addUpdateListener(anim -> {
            mAnimProgress = (float) anim.getAnimatedValue();
            invalidate();
        });
        mAnimator.start();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        int width = getWidth();
        int height = getHeight();

        if (width <= 0 || height <= 0 || mDataPoints.isEmpty()) return;

        float density = getResources().getDisplayMetrics().density;

        if (mDrawCardBackground) {
            float cornerPx = mCornerRadius * density;
            mBgRect.set(0, 0, width, height);
            canvas.drawRoundRect(mBgRect, cornerPx, cornerPx, mBgPaint);

            if (mShowBorder) {
                float halfStroke = (mBorderWidth * density) / 2f;
                mBorderRect.set(halfStroke, halfStroke, width - halfStroke, height - halfStroke);
                canvas.drawRoundRect(mBorderRect, cornerPx, cornerPx, mBorderPaint);
            }
        }

        float paddingLeft = 16f * density;
        float paddingRight = 16f * density;
        float paddingTop = 24f * density;
        float paddingBottom = 30f * density;

        float chartWidth = width - paddingLeft - paddingRight;
        float chartHeight = height - paddingTop - paddingBottom;
        float baselineY = height - paddingBottom;

        int numPoints = mDataPoints.size();
        float stepX = chartWidth / Math.max(1, numPoints - 1);

        switch (mChartMode) {
            case BAR:
                drawBarChart(canvas, paddingLeft, paddingRight, paddingTop, chartWidth, chartHeight, baselineY, numPoints, stepX, density);
                break;
            case PILL_TRACK:
                drawPillTrackChart(canvas, paddingLeft, paddingRight, paddingTop, chartWidth, chartHeight, baselineY, numPoints, stepX, density);
                break;
            case LINE_CURVE:
            default:
                drawLineCurveChart(canvas, paddingLeft, paddingRight, paddingTop, chartWidth, chartHeight, baselineY, numPoints, stepX, density);
                break;
        }
    }

    private void drawLineCurveChart(Canvas canvas, float paddingLeft, float paddingRight, float paddingTop, float chartWidth, float chartHeight, float baselineY, int numPoints, float stepX, float density) {
        mLinePath.reset();
        mFillPath.reset();

        float[] pointsX = new float[numPoints];
        float[] pointsY = new float[numPoints];

        for (int i = 0; i < numPoints; i++) {
            DataPoint dp = mDataPoints.get(i);
            float normY = Math.max(0f, Math.min(1f, dp.value));

            float targetY = paddingTop + ((1.0f - normY) * chartHeight);
            float currentY = baselineY - ((baselineY - targetY) * mAnimProgress);

            pointsX[i] = paddingLeft + (i * stepX);
            pointsY[i] = currentY;

            if (i == 0) {
                mLinePath.moveTo(pointsX[i], pointsY[i]);
                mFillPath.moveTo(pointsX[i], baselineY);
                mFillPath.lineTo(pointsX[i], pointsY[i]);
            } else {
                float prevX = pointsX[i - 1];
                float prevY = pointsY[i - 1];
                float cx = (prevX + pointsX[i]) / 2f;
                mLinePath.cubicTo(cx, prevY, cx, pointsY[i], pointsX[i], pointsY[i]);
                mFillPath.cubicTo(cx, prevY, cx, pointsY[i], pointsX[i], pointsY[i]);
            }
        }

        mFillPath.lineTo(pointsX[numPoints - 1], baselineY);
        mFillPath.close();

        // Area Gradient Fill
        LinearGradient fillGrad = new LinearGradient(
                0, paddingTop, 0, baselineY,
                Color.parseColor("#40FFD600"), Color.parseColor("#00FFD600"),
                Shader.TileMode.CLAMP
        );
        mFillPaint.setShader(fillGrad);
        canvas.drawPath(mFillPath, mFillPaint);

        // Draw X-axis Baseline line
        canvas.drawLine(paddingLeft, baselineY, getWidth() - paddingRight, baselineY, mAxisPaint);

        // Draw Line Curve
        canvas.drawPath(mLinePath, mLinePaint);

        // Draw Circular Dots & Labels
        float dotRadius = 4.5f * density;
        int stride = numPoints > 10 ? 3 : (numPoints > 6 ? 2 : 1);
        for (int i = 0; i < numPoints; i++) {
            canvas.drawCircle(pointsX[i], pointsY[i], dotRadius, mDotPaint);

            DataPoint dp = mDataPoints.get(i);
            if (mShowLabels && dp.label != null) {
                if (i % stride == 0 || i == numPoints - 1) {
                    canvas.drawText(dp.label, pointsX[i], baselineY + 16f * density, mTextPaint);
                }
            }
        }
    }

    private void drawBarChart(Canvas canvas, float paddingLeft, float paddingRight, float paddingTop, float chartWidth, float chartHeight, float baselineY, int numPoints, float stepX, float density) {
        // Horizontal grid guide lines
        for (int row = 0; row <= 3; row++) {
            float gy = paddingTop + (row * (chartHeight / 3f));
            canvas.drawLine(paddingLeft, gy, getWidth() - paddingRight, gy, mGridPaint);
        }

        float barWidth = Math.max(8f * density, stepX * 0.50f);
        float halfBar = barWidth / 2f;

        for (int i = 0; i < numPoints; i++) {
            DataPoint dp = mDataPoints.get(i);
            float normY = Math.max(0.04f, Math.min(1f, dp.value));

            float cx = paddingLeft + (i * stepX);
            float targetHeight = normY * chartHeight;
            float currentHeight = targetHeight * mAnimProgress;
            float topY = baselineY - currentHeight;

            mBarRect.set(cx - halfBar, topY, cx + halfBar, baselineY);

            // Highlight color vs secondary bar color
            if (dp.isHighlighted || dp.value > 0.85f) {
                mBarPaint.setColor(mLineColor);
            } else {
                mBarPaint.setColor(Color.parseColor("#45FFD600"));
            }

            float rx = barWidth / 2f;
            canvas.drawRoundRect(mBarRect, rx, rx, mBarPaint);

            if (mShowLabels && dp.label != null) {
                canvas.drawText(dp.label, cx, baselineY + 16f * density, mTextPaint);
            }
        }
    }

    private void drawPillTrackChart(Canvas canvas, float paddingLeft, float paddingRight, float paddingTop, float chartWidth, float chartHeight, float baselineY, int numPoints, float stepX, float density) {
        float barWidth = Math.max(10f * density, stepX * 0.45f);
        float halfBar = barWidth / 2f;
        float rx = barWidth / 2f;

        // Baseline line
        canvas.drawLine(paddingLeft, baselineY, getWidth() - paddingRight, baselineY, mAxisPaint);

        for (int i = 0; i < numPoints; i++) {
            DataPoint dp = mDataPoints.get(i);
            float normY = Math.max(0.06f, Math.min(1f, dp.value));

            float cx = paddingLeft + (i * stepX);

            // 1. Full-height background track
            mBarRect.set(cx - halfBar, paddingTop, cx + halfBar, baselineY);
            canvas.drawRoundRect(mBarRect, rx, rx, mTrackPaint);

            // 2. Rising filled progress capsule
            float targetHeight = normY * chartHeight;
            float currentHeight = targetHeight * mAnimProgress;
            float topY = baselineY - currentHeight;

            mBarRect.set(cx - halfBar, topY, cx + halfBar, baselineY);

            if (dp.isHighlighted || i == 1) {
                mBarPaint.setColor(mAccentColor);
            } else {
                mBarPaint.setColor(Color.parseColor("#4038BDF8"));
            }

            canvas.drawRoundRect(mBarRect, rx, rx, mBarPaint);

            // Draw checkmark badge on highlighted top bar
            if (dp.isHighlighted || i == 1) {
                canvas.drawCircle(cx, topY + halfBar, halfBar * 0.85f, mDotPaint);
            }

            if (mShowLabels && dp.label != null) {
                canvas.drawText(dp.label, cx, baselineY + 16f * density, mTextPaint);
            }
        }
    }
}
