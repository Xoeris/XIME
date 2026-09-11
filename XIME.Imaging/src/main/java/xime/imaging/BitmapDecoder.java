package xime.imaging;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;

/**
 * Downsampling bitmap decoder.
 * Avoids full-resolution decodes for thumbnail-sized targets, which is the
 * single biggest OOM risk when a naive Glide replacement is dropped into a
 * RecyclerView-heavy screen.
 */
final class BitmapDecoder {

    private BitmapDecoder() {
    }

    static Bitmap decodeSampled(byte[] bytes, int reqWidth, int reqHeight, Bitmap.Config config) {
        BitmapFactory.Options bounds = new BitmapFactory.Options();
        bounds.inJustDecodeBounds = true;
        BitmapFactory.decodeByteArray(bytes, 0, bytes.length, bounds);

        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inSampleSize = calculateInSampleSize(bounds, reqWidth, reqHeight);
        options.inPreferredConfig = config != null ? config : Bitmap.Config.ARGB_8888;

        return BitmapFactory.decodeByteArray(bytes, 0, bytes.length, options);
    }

    static int calculateInSampleSize(BitmapFactory.Options options, int reqWidth, int reqHeight) {
        int height = options.outHeight;
        int width = options.outWidth;
        int inSampleSize = 1;

        if (reqWidth <= 0 || reqHeight <= 0) {
            // No known target size (e.g. view not yet measured) - decode at full size.
            return inSampleSize;
        }

        if (height > reqHeight || width > reqWidth) {
            int halfHeight = height / 2;
            int halfWidth = width / 2;

            while ((halfHeight / inSampleSize) >= reqHeight && (halfWidth / inSampleSize) >= reqWidth) {
                inSampleSize *= 2;
            }
        }
        return inSampleSize;
    }
}

