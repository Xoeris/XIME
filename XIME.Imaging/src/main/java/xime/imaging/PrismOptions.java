package xime.imaging;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.drawable.Drawable;
import androidx.core.content.ContextCompat;

/**
 * Reusable, immutable-once-built options configuration mirroring Glide's RequestOptions.
 */
public final class PrismOptions {
    Drawable placeholder;
    Drawable error;
    Transformation transformation;
    Bitmap.Config preferredConfig = Bitmap.Config.ARGB_8888;
    DiskCacheStrategy diskCacheStrategy = DiskCacheStrategy.ALL;

    int placeholderResId;
    int errorResId;

    public PrismOptions placeholder(Drawable d) {
        this.placeholder = d;
        return this;
    }

    public PrismOptions placeholder(int resId) {
        this.placeholderResId = resId;
        return this;
    }

    public PrismOptions placeholder(int resId, Context c) {
        this.placeholder = ContextCompat.getDrawable(c, resId);
        return this;
    }

    public PrismOptions error(Drawable d) {
        this.error = d;
        return this;
    }

    public PrismOptions error(int resId) {
        this.errorResId = resId;
        return this;
    }

    public PrismOptions error(int resId, Context c) {
        this.error = ContextCompat.getDrawable(c, resId);
        return this;
    }

    public PrismOptions centerCrop() {
        this.transformation = new CenterCropTransformation();
        return this;
    }

    public PrismOptions preferRgb565() {
        this.preferredConfig = Bitmap.Config.RGB_565;
        return this;
    }

    public PrismOptions diskCacheStrategy(DiskCacheStrategy s) {
        this.diskCacheStrategy = s;
        return this;
    }
}

