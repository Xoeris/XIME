package xime.ui.drawable;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorFilter;
import android.graphics.DashPathEffect;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PathMeasure;
import android.graphics.PixelFormat;
import android.graphics.RadialGradient;
import android.graphics.Shader;
import android.graphics.drawable.Drawable;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

public class SunPathDrawable extends Drawable {
    private final Paint arcPaint;
    private final Paint sunPaint;
    private final Paint glowPaint;
    private final Path path;
    private final PathMeasure pathMeasure;
    private float progress = 0f;
    private final float[] pos = new float[2];

    public SunPathDrawable() {
        arcPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        arcPaint.setStyle(Paint.Style.STROKE);
        arcPaint.setStrokeWidth(3f);
        arcPaint.setColor(Color.parseColor("#80FFFFFF"));
        arcPaint.setPathEffect(new DashPathEffect(new float[]{10, 10}, 0));

        sunPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        sunPaint.setColor(Color.parseColor("#FFD600"));

        glowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        
        path = new Path();
        pathMeasure = new PathMeasure();
    }

    public void setProgress(float progress) {
        this.progress = Math.max(0f, Math.min(1f, progress));
        invalidateSelf();
    }

    public float getProgress() {
        return progress;
    }

    @Override
    public void draw(@NonNull Canvas canvas) {
        int w = getBounds().width();
        int h = getBounds().height();
        
        path.reset();
        path.moveTo(w * 0.1f, h * 0.8f);
        path.quadTo(w * 0.5f, -h * 0.2f, w * 0.9f, h * 0.8f);
        
        canvas.drawPath(path, arcPaint);

        pathMeasure.setPath(path, false);
        pathMeasure.getPosTan(pathMeasure.getLength() * progress, pos, null);

        float sunX = pos[0];
        float sunY = pos[1];
        float sunRadius = 12f;

        // Draw Glow
        Shader glow = new RadialGradient(sunX, sunY, sunRadius * 3,
                new int[]{Color.parseColor("#80FFD600"), Color.TRANSPARENT},
                null, Shader.TileMode.CLAMP);
        glowPaint.setShader(glow);
        canvas.drawCircle(sunX, sunY, sunRadius * 3, glowPaint);

        // Draw Sun Core
        canvas.drawCircle(sunX, sunY, sunRadius, sunPaint);
    }

    @Override
    public void setAlpha(int alpha) {
        arcPaint.setAlpha(alpha);
        sunPaint.setAlpha(alpha);
    }

    @Override
    public void setColorFilter(@Nullable ColorFilter colorFilter) {
        arcPaint.setColorFilter(colorFilter);
        sunPaint.setColorFilter(colorFilter);
    }

    @Override
    public int getOpacity() {
        return PixelFormat.TRANSLUCENT;
    }
}
