package xime.ui.layout;

import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Outline;
import android.util.AttributeSet;
import android.view.View;
import android.view.ViewOutlineProvider;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Canvas;
import xime.R;

public class CardLayout extends Layout {
    private float mRadius;
    private int mStrokeColor;
    private float mStrokeWidth;
    private Paint mStrokePaint;
    private final RectF mStrokeRect = new RectF();

    public CardLayout(@NonNull Context context) {
        this(context, null);
    }

    public CardLayout(@NonNull Context context, @Nullable AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public CardLayout(@NonNull Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init(context, attrs);
    }

    private void init(Context context, AttributeSet attrs) {
        if (attrs != null) {
            TypedArray a = context.obtainStyledAttributes(attrs, R.styleable.CardView);
            mRadius = a.getDimension(R.styleable.CardView_xoerisCornerRadius, 0);
            mStrokeColor = a.getColor(R.styleable.CardView_xoerisStrokeColor, 0);
            mStrokeWidth = a.getDimension(R.styleable.CardView_xoerisStrokeWidth, 0);
            a.recycle();
        }
        setupOutline();
        setWillNotDraw(false);
    }

    private void setupOutline() {
        setOutlineProvider(new ViewOutlineProvider() {
            @Override
            public void getOutline(View view, Outline outline) {
                outline.setRoundRect(0, 0, view.getWidth(), view.getHeight(), mRadius);
            }
        });
        setClipToOutline(mRadius > 0);
    }

    public void setRadius(float radius) {
        this.mRadius = radius;
        setupOutline();
        invalidate();
    }

    public float getRadius() { return mRadius; }

    public void setStrokeColor(int color) {
        this.mStrokeColor = color;
        invalidate();
    }

    public void setStrokeWidth(int width) {
        this.mStrokeWidth = (float) width;
        invalidate();
    }

    public void setStrokeWidth(float width) {
        this.mStrokeWidth = width;
        invalidate();
    }

    @Override
    protected void dispatchDraw(@NonNull Canvas canvas) {
        super.dispatchDraw(canvas);
        if (mStrokeWidth > 0 && mStrokeColor != 0) {
            if (mStrokePaint == null) {
                mStrokePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
                mStrokePaint.setStyle(Paint.Style.STROKE);
            }
            mStrokePaint.setColor(mStrokeColor);
            mStrokePaint.setStrokeWidth(mStrokeWidth);

            float halfStroke = mStrokeWidth / 2f;
            mStrokeRect.set(halfStroke, halfStroke, getWidth() - halfStroke, getHeight() - halfStroke);
            float drawRadius = Math.max(0, mRadius - halfStroke);
            canvas.drawRoundRect(mStrokeRect, drawRadius, drawRadius, mStrokePaint);
        }
    }
    public void setCardBackgroundColor(int color) { setBackgroundColor(color); }
    public static class ColorStateListBridge {
        private int color;
        public ColorStateListBridge(int c) { this.color = c; }
        public int getDefaultColor() { return color; }
    }

    public ColorStateListBridge getCardBackgroundColor() {
        return new ColorStateListBridge(mBackgroundColor);
    }
    public void setRippleColor(android.content.res.ColorStateList color) {}

    public void setCardElevation(float elevation) {
        setElevation(elevation);
    }

    public void setRippleColorResource(int resId) {}
    @Override
    public void setClipToOutline(boolean clip) {
        super.setClipToOutline(clip);
    }
}
