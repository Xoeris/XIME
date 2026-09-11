package xime.media.ui;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Shader;
import android.util.AttributeSet;
import androidx.annotation.Nullable;
import xime.ui.view.View;
import xime.media.spectrum.Spectrum;

public class SpectrumView extends View implements Spectrum.OnSpectrumChangeListener {
    private final Paint barPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private int barColor = 0xFFFFFF00; 
    private int barCount = 4;
    private boolean isAnimating = false;
    private float density;
    private int lastHeight = -1;
    private final android.graphics.Matrix gradientMatrix = new android.graphics.Matrix();

    public SpectrumView(Context context) {
        this(context, null);
    }

    public SpectrumView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    private void init() {
        density = getResources().getDisplayMetrics().density;
        barPaint.setStyle(Paint.Style.FILL);
        barPaint.setColor(barColor);
    }

    public void setBarColor(int color) {
        this.barColor = color;
        barPaint.setColor(color);
        lastHeight = -1; // Force shader recreate
        invalidate();
    }

    public void startAnimation() {
        if (!isAnimating) {
            isAnimating = true;
            Spectrum.getInstance().addListener(this);
            invalidate();
        }
    }

    public void stopAnimation() {
        if (isAnimating) {
            isAnimating = false;
            Spectrum.getInstance().removeListener(this);
            invalidate();
        }
    }

    @Override
    public void onSpectrumUpdate() {
        if (isAnimating) {
            postInvalidateOnAnimation();
        }
    }

    @Override
    protected void onDraw(Canvas canvas) {
        float w = getWidth();
        float h = getHeight();

        if (w <= 0 || h <= 0) return;

        if (h != lastHeight) {
            lastHeight = (int) h;
            int bottomColor = barColor;
            int topColor = 0x80808080; // 50% Grey

            Shader shader = new LinearGradient(
                    0, 0, 0, 1,
                    new int[]{topColor, bottomColor},
                    new float[]{0.0f, 1.0f},
                    Shader.TileMode.CLAMP
            );
            barPaint.setShader(shader);
        }
        
        float barWidth = w / (barCount * 1.5f);
        float barGap = barWidth / 2f;
        
        Spectrum sphere = Spectrum.getInstance();
        
        for (int i = 0; i < barCount; i++) {
            float magnitude = isAnimating ? sphere.sample((float) i / barCount) : 0.2f;
            float barHeight = h * magnitude;
            
            float left = i * (barWidth + barGap);
            float top = h - barHeight;
            float right = left + barWidth;
            float bottom = h;
            
            if (barHeight > 0) {
                gradientMatrix.setScale(1, barHeight);
                gradientMatrix.postTranslate(0, top);
                Shader currentShader = barPaint.getShader();
                if (currentShader != null) {
                    currentShader.setLocalMatrix(gradientMatrix);
                    barPaint.setShader(currentShader);
                }
            }
            
            canvas.drawRoundRect(left, top, right, bottom, barWidth / 2f, barWidth / 2f, barPaint);
        }
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        if (isAnimating) {
            Spectrum.getInstance().addListener(this);
        }
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        Spectrum.getInstance().removeListener(this);
    }
}

