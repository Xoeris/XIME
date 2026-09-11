package xime.imaging;

import android.graphics.Bitmap;
import android.graphics.BitmapShader;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Shader;

/** Rounds the corners of the source bitmap by {@code radiusPx}. */
public final class RoundedCornersTransformation implements Transformation {

    private final float radiusPx;

    public RoundedCornersTransformation(float radiusPx) {
        this.radiusPx = radiusPx;
    }

    @Override
    public Bitmap transform(Bitmap source, int targetWidth, int targetHeight) {
        Bitmap output = Bitmap.createBitmap(source.getWidth(), source.getHeight(), Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(output);

        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        BitmapShader shader = new BitmapShader(source, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP);
        paint.setShader(shader);

        RectF rect = new RectF(0, 0, source.getWidth(), source.getHeight());
        canvas.drawRoundRect(rect, radiusPx, radiusPx, paint);
        return output;
    }

    @Override
    public String key() {
        return "rounded_" + radiusPx;
    }
}

