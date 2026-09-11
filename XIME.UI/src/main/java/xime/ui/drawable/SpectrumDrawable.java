package xime.ui.drawable;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorFilter;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.drawable.Drawable;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

public class SpectrumDrawable extends Drawable {
    private final Paint barPaint;
    private final Paint thumbPaint;
    private float progress = 0.5f;

    public SpectrumDrawable() {
        barPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        thumbPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        thumbPaint.setColor(Color.WHITE);
        thumbPaint.setShadowLayer(5, 0, 0, Color.parseColor("#40000000"));
    }

    public void setProgress(float progress) {
        this.progress = Math.max(0f, Math.min(1f, progress));
        invalidateSelf();
    }

    @Override
    public void draw(@NonNull Canvas canvas) {
        RectF rect = new RectF(getBounds());
        float r = rect.height() / 2f;
        Shader shader = new LinearGradient(rect.left, 0, rect.right, 0,
                new int[]{0xFF00E676, 0xFFFFD600, 0xFFFF3D00, 0xFFD500F9},
                null, Shader.TileMode.CLAMP);
        barPaint.setShader(shader);
        canvas.drawRoundRect(rect, r, r, barPaint);

        float thumbX = rect.left + rect.width() * progress;
        canvas.drawCircle(thumbX, rect.centerY(), r + 2, thumbPaint);
    }

    @Override
    public void setAlpha(int alpha) {
        barPaint.setAlpha(alpha);
        thumbPaint.setAlpha(alpha);
    }

    @Override
    public void setColorFilter(@Nullable ColorFilter colorFilter) {
        barPaint.setColorFilter(colorFilter);
        thumbPaint.setColorFilter(colorFilter);
    }

    @Override
    public int getOpacity() {
        return PixelFormat.TRANSLUCENT;
    }
}
