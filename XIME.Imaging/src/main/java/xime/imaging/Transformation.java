package xime.imaging;

import android.graphics.Bitmap;

/**
 * Applied on the background decode thread, before the
 * result is placed in the memory cache.
 */
public interface Transformation {

    /** Return the source bitmap itself if no change is made, or a new bitmap otherwise. */
    Bitmap transform(Bitmap source, int targetWidth, int targetHeight);

    /** Included in the cache key so transformed and untransformed variants don't collide. */
    String key();
}

