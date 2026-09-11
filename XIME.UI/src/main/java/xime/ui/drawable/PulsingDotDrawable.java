package xime.ui.drawable;

import android.animation.ValueAnimator;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.RadialGradient;
import android.graphics.Shader;
import android.graphics.drawable.Drawable;
import android.view.animation.AccelerateDecelerateInterpolator;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * Animated glowing blue dot for precise location.
 */
public class PulsingDotDrawable extends Drawable {
    private final Paint dotPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint glowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private float pulseScale = 1.0f;
    private ValueAnimator animator;

    public PulsingDotDrawable() {
        dotPaint.setColor(Color.parseColor("#4285F4"));
        dotPaint.setStyle(Paint.Style.FILL);
        
        startAnimation();
    }

    private void startAnimation() {
        animator = ValueAnimator.ofFloat(1.0f, 1.8f);
        animator.setDuration(1500);
        animator.setRepeatMode(ValueAnimator.REVERSE);
        animator.setRepeatCount(ValueAnimator.INFINITE);
        animator.setInterpolator(new AccelerateDecelerateInterpolator());
        animator.addUpdateListener(animation -> {
            pulseScale = (float) animation.getAnimatedValue();
            invalidateSelf();
        });
        animator.start();
    }

    @Override
    public void draw(@NonNull Canvas canvas) {
        float cx = getBounds().centerX();
        float cy = getBounds().centerY();
        float radius = Math.min(getBounds().width(), getBounds().height()) / 4f;

        // Draw Glow
        glowPaint.setShader(new RadialGradient(cx, cy, radius * pulseScale * 1.5f,
                new int[]{Color.argb(100, 66, 133, 244), Color.TRANSPARENT},
                null, Shader.TileMode.CLAMP));
        canvas.drawCircle(cx, cy, radius * pulseScale * 1.5f, glowPaint);

        // Draw Inner Dot
        canvas.drawCircle(cx, cy, radius, dotPaint);
        
        // Draw White border
        Paint border = new Paint(Paint.ANTI_ALIAS_FLAG);
        border.setColor(Color.WHITE);
        border.setStyle(Paint.Style.STROKE);
        border.setStrokeWidth(2f);
        canvas.drawCircle(cx, cy, radius, border);
    }

    public void stop() {
        if (animator != null) animator.cancel();
    }

    @Override public void setAlpha(int alpha) { dotPaint.setAlpha(alpha); }
    @Override public void setColorFilter(@Nullable ColorFilter colorFilter) { dotPaint.setColorFilter(colorFilter); }
    @Override public int getOpacity() { return PixelFormat.TRANSLUCENT; }
}
