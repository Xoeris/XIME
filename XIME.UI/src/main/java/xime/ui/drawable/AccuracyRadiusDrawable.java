package xime.ui.drawable;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.drawable.Drawable;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * Translucent circle representing IP Geolocation accuracy radius.
 */
public class AccuracyRadiusDrawable extends Drawable {
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private float radiusPx = 0f;

    public AccuracyRadiusDrawable() {
        paint.setColor(Color.parseColor("#4285F4"));
        paint.setAlpha(40); // Soft transparency
        paint.setStyle(Paint.Style.FILL);
    }

    public void setRadius(float radiusPx) {
        this.radiusPx = radiusPx;
        invalidateSelf();
    }

    @Override
    public void draw(@NonNull Canvas canvas) {
        if (radiusPx <= 0) return;
        canvas.drawCircle(getBounds().centerX(), getBounds().centerY(), radiusPx, paint);
        
        // Optional stroke for clarity
        Paint stroke = new Paint(paint);
        stroke.setStyle(Paint.Style.STROKE);
        stroke.setStrokeWidth(2f);
        stroke.setAlpha(80);
        canvas.drawCircle(getBounds().centerX(), getBounds().centerY(), radiusPx, stroke);
    }

    @Override public void setAlpha(int alpha) { paint.setAlpha(alpha); }
    @Override public void setColorFilter(@Nullable ColorFilter colorFilter) { paint.setColorFilter(colorFilter); }
    @Override public int getOpacity() { return PixelFormat.TRANSLUCENT; }
}
