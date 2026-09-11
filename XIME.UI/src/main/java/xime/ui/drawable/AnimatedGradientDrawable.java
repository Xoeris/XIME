package xime.ui.drawable;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ArgbEvaluator;
import android.animation.ValueAnimator;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorFilter;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.drawable.Drawable;
import android.view.animation.LinearInterpolator;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.Random;

/**
 * Animated random non-stop gradient color drawable using high-performance Paint & LinearGradient
 * with continuous angle rotation and smooth HSL color morphing.
 */
public class AnimatedGradientDrawable extends Drawable {

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rectF = new RectF();
    private final Random random = new Random();
    private final ArgbEvaluator argbEvaluator = new ArgbEvaluator();

    private float cornerRadius = 0f;
    private int[] currentColors = new int[]{
            Color.parseColor("#7C3AED"),
            Color.parseColor("#3B82F6"),
            Color.parseColor("#06B6D4")
    };
    private int[] targetColors = generateRandomColors();
    private int[] interpolatedColors = new int[3];

    private ValueAnimator animator;
    private float progress = 0f;
    private float angle = 0f;
    private boolean isRunning = false;
    private long stepDuration = 2500L;

    public AnimatedGradientDrawable() {
        startAnimation();
    }

    public AnimatedGradientDrawable(float cornerRadiusPx) {
        this.cornerRadius = cornerRadiusPx;
        startAnimation();
    }

    public void setCornerRadius(float radiusPx) {
        this.cornerRadius = radiusPx;
        invalidateSelf();
    }

    private int[] generateRandomColors() {
        int[] colors = new int[3];
        float baseHue = random.nextFloat() * 360f;
        for (int i = 0; i < 3; i++) {
            float hue = (baseHue + (i * 120f) + (random.nextFloat() * 40f - 20f)) % 360f;
            float saturation = 0.8f + (random.nextFloat() * 0.2f);
            float lightness = 0.5f + (random.nextFloat() * 0.2f);
            colors[i] = Color.HSVToColor(new float[]{hue, saturation, lightness});
        }
        return colors;
    }

    public void startAnimation() {
        if (isRunning) return;
        isRunning = true;

        animator = ValueAnimator.ofFloat(0f, 1f);
        animator.setDuration(stepDuration);
        animator.setInterpolator(new LinearInterpolator());
        animator.setRepeatCount(ValueAnimator.INFINITE);
        animator.addUpdateListener(animation -> {
            progress = (float) animation.getAnimatedValue();
            angle = (angle + 2.0f) % 360f; // Continuous non-stop rotation!
            invalidateSelf();
        });

        animator.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationRepeat(Animator animation) {
                currentColors = targetColors.clone();
                targetColors = generateRandomColors();
            }
        });

        animator.start();
    }

    public void stopAnimation() {
        if (!isRunning) return;
        isRunning = false;
        if (animator != null) {
            animator.cancel();
            animator = null;
        }
    }

    @Override
    public void draw(@NonNull Canvas canvas) {
        Rect bounds = getBounds();
        if (bounds.width() <= 0 || bounds.height() <= 0) return;

        rectF.set(bounds);

        // Interpolate current to target colors
        for (int i = 0; i < currentColors.length; i++) {
            interpolatedColors[i] = (int) argbEvaluator.evaluate(progress, currentColors[i], targetColors[i]);
        }

        // Calculate rotating linear gradient endpoints
        double radians = Math.toRadians(angle);
        float halfW = rectF.width() / 2f;
        float halfH = rectF.height() / 2f;
        float x0 = (float) (rectF.centerX() - Math.cos(radians) * halfW);
        float y0 = (float) (rectF.centerY() - Math.sin(radians) * halfH);
        float x1 = (float) (rectF.centerX() + Math.cos(radians) * halfW);
        float y1 = (float) (rectF.centerY() + Math.sin(radians) * halfH);

        LinearGradient shader = new LinearGradient(x0, y0, x1, y1, interpolatedColors, null, Shader.TileMode.CLAMP);
        paint.setShader(shader);

        if (cornerRadius > 0f) {
            canvas.drawRoundRect(rectF, cornerRadius, cornerRadius, paint);
        } else {
            canvas.drawRect(rectF, paint);
        }
    }

    @Override
    public void setAlpha(int alpha) {
        paint.setAlpha(alpha);
        invalidateSelf();
    }

    @Override
    public void setColorFilter(@Nullable ColorFilter colorFilter) {
        paint.setColorFilter(colorFilter);
        invalidateSelf();
    }

    @Override
    public int getOpacity() {
        return PixelFormat.TRANSLUCENT;
    }

    @Override
    public boolean setVisible(boolean visible, boolean restart) {
        boolean changed = super.setVisible(visible, restart);
        if (visible) {
            if (restart || !isRunning) startAnimation();
        } else {
            stopAnimation();
        }
        return changed;
    }
}
