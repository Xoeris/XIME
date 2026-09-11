package xime.imaging;

import android.graphics.Bitmap;
import android.graphics.BitmapShader;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Shader;

/** Crops the source bitmap into a circle, centered. */
public final class CircleTransformation implements Transformation {

    @Override
    public Bitmap transform(Bitmap source, int targetWidth, int targetHeight) {
        int size = Math.min(source.getWidth(), source.getHeight());
        Bitmap output = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);

        Canvas canvas = new Canvas(output);
        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        BitmapShader shader = new BitmapShader(source, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP);

        float dx = (size - source.getWidth()) / 2f;
        float dy = (size - source.getHeight()) / 2f;
        Matrix matrix = new Matrix();
        matrix.setTranslate(dx, dy);
        shader.setLocalMatrix(matrix);
        paint.setShader(shader);

        canvas.drawCircle(size / 2f, size / 2f, size / 2f, paint);
        return output;
    }

    @Override
    public String key() {
        return "circle";
    }
}

