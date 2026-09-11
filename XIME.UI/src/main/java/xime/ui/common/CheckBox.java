package xime.ui.common;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Shader;
import android.util.AttributeSet;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import xime.haptic.HapticEngine;
import xime.ui.view.View;

/**
 * Custom Squircle CheckBox with Smooth Gradient & Checkmark Vector Path for XIME.UI.
 */
public class CheckBox extends View {

    public interface OnCheckedChangeListener {
        void onCheckedChanged(CheckBox checkBox, boolean isChecked);
    }

    private boolean mChecked = false;
    private OnCheckedChangeListener mListener;
    private HapticEngine mHapticEngine;

    private final Paint mBoxPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint mBorderPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint mCheckPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF mBounds = new RectF();
    private final Path mSquirclePath = new Path();
    private final Path mCheckPath = new Path();

    // Vibrant Blue/Purple Gradient Colors matching design image
    private final int mCheckedGradientStart = Color.parseColor("#7B8CFF"); // Top soft indigo/light blue
    private final int mCheckedGradientEnd = Color.parseColor("#4338CA");   // Bottom deep vibrant purple-blue
    private final int mUncheckedBgColor = Color.parseColor("#1C1C1E");     // Dark glass background
    private final int mUncheckedBorderColor = Color.parseColor("#3A3A3C"); // Dark border
    private final int mCheckedBorderColor = Color.parseColor("#80B0FF");   // Subtle top highlight border

    public CheckBox(@NonNull Context context) {
        super(context);
        init(context, null);
    }

    public CheckBox(@NonNull Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init(context, attrs);
    }

    public CheckBox(@NonNull Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init(context, attrs);
    }

    private void init(Context context, AttributeSet attrs) {
        setClickable(true);
        setFocusable(true);
        mHapticEngine = new HapticEngine(context);

        mBorderPaint.setStyle(Paint.Style.STROKE);
        mBorderPaint.setStrokeWidth(dpToPx(1.5f));

        mCheckPaint.setStyle(Paint.Style.STROKE);
        mCheckPaint.setColor(Color.WHITE);
        mCheckPaint.setStrokeCap(Paint.Cap.ROUND);
        mCheckPaint.setStrokeJoin(Paint.Join.ROUND);

        setOnClickListener(v -> {
            toggle();
        });
    }

    public void setChecked(boolean checked) {
        if (mChecked != checked) {
            mChecked = checked;
            invalidate();
            if (mListener != null) {
                mListener.onCheckedChanged(this, mChecked);
            }
        }
    }

    public boolean isChecked() {
        return mChecked;
    }

    public void toggle() {
        if (mHapticEngine != null) mHapticEngine.triggerClick();
        setChecked(!mChecked);
    }

    public void setOnCheckedChangeListener(OnCheckedChangeListener listener) {
        this.mListener = listener;
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int defaultSize = dpToPx(24);
        int width = resolveSize(defaultSize, widthMeasureSpec);
        int height = resolveSize(defaultSize, heightMeasureSpec);
        int size = Math.min(width, height);
        setMeasuredDimension(size, size);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        float w = getWidth();
        float h = getHeight();
        if (w <= 0 || h <= 0) return;

        float strokePad = dpToPx(1.5f);
        mBounds.set(strokePad, strokePad, w - strokePad, h - strokePad);

        float cornerRadius = Math.min(w, h) * 0.32f; // Smooth squircle ratio

        mSquirclePath.reset();
        mSquirclePath.addRoundRect(mBounds, cornerRadius, cornerRadius, Path.Direction.CW);

        if (mChecked) {
            // Checked State: Linear Gradient Fill
            LinearGradient gradient = new LinearGradient(
                    0, 0, 0, h,
                    mCheckedGradientStart, mCheckedGradientEnd,
                    Shader.TileMode.CLAMP
            );
            mBoxPaint.setShader(gradient);
            mBoxPaint.setStyle(Paint.Style.FILL);
            canvas.drawPath(mSquirclePath, mBoxPaint);

            // Subtle top highlight stroke
            mBorderPaint.setColor(mCheckedBorderColor);
            mBorderPaint.setAlpha(60);
            canvas.drawPath(mSquirclePath, mBorderPaint);

            // Draw Vector Checkmark
            mCheckPaint.setStrokeWidth(w * 0.12f);

            mCheckPath.reset();
            // Point 1: Left arm start (28% W, 50% H)
            mCheckPath.moveTo(w * 0.28f, h * 0.50f);
            // Point 2: Bottom vertex (44% W, 66% H)
            mCheckPath.lineTo(w * 0.44f, h * 0.66f);
            // Point 3: Right arm end (72% W, 34% H)
            mCheckPath.lineTo(w * 0.72f, h * 0.34f);

            canvas.drawPath(mCheckPath, mCheckPaint);
        } else {
            // Unchecked State: Dark Fill + Border
            mBoxPaint.setShader(null);
            mBoxPaint.setColor(mUncheckedBgColor);
            mBoxPaint.setStyle(Paint.Style.FILL);
            canvas.drawPath(mSquirclePath, mBoxPaint);

            mBorderPaint.setColor(mUncheckedBorderColor);
            mBorderPaint.setAlpha(255);
            canvas.drawPath(mSquirclePath, mBorderPaint);
        }
    }

    private int dpToPx(float dp) {
        return (int) (dp * getContext().getResources().getDisplayMetrics().density + 0.5f);
    }
}
