package xime.media.ui;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Shader;
import android.util.AttributeSet;

import androidx.annotation.Nullable;
import xime.ui.layout.LayerLayout;
import xime.media.spectrum.Spectrum;

public class SpectrumLayout extends LayerLayout implements Spectrum.OnSpectrumChangeListener {
    private int barColor;
    private final Paint barPaint;
    private float density;
    private final Matrix gradientMatrix;
    private boolean isAnimating;
    private int lastHeight;
    private boolean spectrumEnabled;

    public SpectrumLayout(Context context) {
        this(context, null);
    }

    public SpectrumLayout(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        this.barPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        this.barColor = 0x30FFFFFF; // Reduced base alpha for more transparency
        this.isAnimating = false;
        this.spectrumEnabled = true;
        this.density = 1.0f;
        this.lastHeight = -1;
        this.gradientMatrix = new Matrix();
        setWillNotDraw(false);
        init();
    }

    private void init() {
        this.density = getResources().getDisplayMetrics().density;
        this.barPaint.setStyle(Paint.Style.FILL);
        updateShader();
    }

    public void setBarColor(int color) {
        // Ensure we preserve a level of transparency if the input is opaque
        if ((color >>> 24) == 0xFF) {
            this.barColor = (color & 0x00FFFFFF) | 0x40000000;
        } else {
            this.barColor = color;
        }
        updateShader();
        invalidate();
    }

    public void setSpectrumEnabled(boolean enabled) {
        this.spectrumEnabled = enabled;
        invalidate();
    }

    public void startAnimation() {
        this.isAnimating = true;
        invalidate();
    }

    public void stopAnimation() {
        this.isAnimating = false;
        invalidate();
    }

    @Override
    public void onSpectrumUpdate() {
        postInvalidateOnAnimation();
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        updateShader();
    }

    private void updateShader() {
        int h = getHeight();
        if (h > 0) {
            int colorRGB = this.barColor & 0x00FFFFFF;
            int maxAlpha = (this.barColor >>> 24);
            
            // Deep Fade: Fully transparent at top, very subtle until the bottom 30%
            int topColor = colorRGB; // 0 Alpha
            int midColor = colorRGB | ((int)(maxAlpha * 0.2f) << 24); // 20% of max alpha at stop
            int bottomColor = this.barColor; // Max alpha at bottom
            
            Shader shader = new LinearGradient(0.0f, 0.0f, 0.0f, h, 
                    new int[]{topColor, midColor, bottomColor}, 
                    new float[]{0.0f, 0.7f, 1.0f}, 
                    Shader.TileMode.CLAMP);
            this.barPaint.setShader(shader);
        }
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (!this.spectrumEnabled) return;

        boolean inEditMode = isInEditMode();
        float w = getWidth();
        float h = getHeight();
        
        if (w > 0.0f && h > 0.0f) {
            // Dynamic bar calculation to ensure edge-to-edge coverage
            float targetUnitWidth = this.density * 4.0f;
            int barCount = Math.max(1, (int) (w / targetUnitWidth));
            
            float unitWidth = w / (float) barCount;
            float barWidthPx = unitWidth * 0.7f;

            Spectrum sphere = Spectrum.getInstance();
            
            for (int i = 0; i < barCount; i++) {
                float position = (float) i / Math.max(1, barCount - 1);
                // Mirrored mapping for visual symmetry
                float mirroredPosition = position < 0.5f ? position * 2.0f : (1.0f - position) * 2.0f;

                float magnitude;
                if (inEditMode) {
                    magnitude = (float) ((Math.sin(i * 0.2f) * 0.3) + 0.4);
                } else {
                    magnitude = sphere.sample(mirroredPosition);
                }
                
                // Minimum bar height for visibility, max 85% of view height
                float barHeight = Math.max(this.density * 2f, 0.85f * h * magnitude);
                float left = i * unitWidth + (unitWidth - barWidthPx) / 2.0f;
                float top = h - barHeight;
                float right = left + barWidthPx;
                float radius = barWidthPx / 2.0f;
                
                canvas.drawRoundRect(left, top, right, h, radius, radius, this.barPaint);
            }
        }
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        Spectrum.getInstance().addListener(this);
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        Spectrum.getInstance().removeListener(this);
    }
}

