package xime.imaging;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.Paint;

/**
 * Scales and center-crops the source bitmap to the target dimensions.
 */
public final class CenterCropTransformation implements Transformation {

    @Override
    public Bitmap transform(Bitmap source, int targetWidth, int targetHeight) {
        if (targetWidth <= 0 || targetHeight <= 0) {
            return source;
        }
        int sourceWidth = source.getWidth();
        int sourceHeight = source.getHeight();
        if (sourceWidth == targetWidth && sourceHeight == targetHeight) {
            return source;
        }

        float scale;
        float dx = 0, dy = 0;

        if (sourceWidth * targetHeight > targetWidth * sourceHeight) {
            scale = (float) targetHeight / (float) sourceHeight;
            dx = (targetWidth - sourceWidth * scale) * 0.5f;
        } else {
            scale = (float) targetWidth / (float) sourceWidth;
            dy = (targetHeight - sourceHeight * scale) * 0.5f;
        }

        Bitmap output = Bitmap.createBitmap(targetWidth, targetHeight, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(output);
        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);

        Matrix matrix = new Matrix();
        matrix.setScale(scale, scale);
        matrix.postTranslate(dx, dy);

        canvas.drawBitmap(source, matrix, paint);
        return output;
    }

    @Override
    public String key() {
        return "centerCrop";
    }
}

