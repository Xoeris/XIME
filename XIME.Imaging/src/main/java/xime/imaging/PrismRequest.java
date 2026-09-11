package xime.imaging;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.view.View;
import android.widget.ImageView;

import androidx.core.content.ContextCompat;

import java.lang.ref.WeakReference;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Fluent request builder for a single Prism load.
 * Mirrors the subset of Glide's RequestBuilder API used across XIME.*.
 */
public final class PrismRequest {

    private static final int TAG_KEY = xime.imaging.R.id.prism_request_id;
    private static final AtomicLong REQUEST_ID = new AtomicLong(0);

    private final Object rawSource;
    private Context context;
    private Drawable placeholder;
    private Drawable error;
    private int placeholderResId = 0;
    private int errorResId = 0;
    private Transformation transformation;
    private boolean skipMemoryCache = false;
    private boolean skipDiskCache = false;
    private boolean crossFade = true;
    private int overrideWidth = -1;
    private int overrideHeight = -1;
    private Bitmap.Config preferredConfig = Bitmap.Config.ARGB_8888;
    private DiskCacheStrategy diskCacheStrategy = DiskCacheStrategy.ALL;

    PrismRequest(Object rawSource) {
        this.rawSource = rawSource;
    }

    PrismRequest(Object rawSource, Context context) {
        this.rawSource = rawSource;
        this.context = context;
    }

    public PrismRequest apply(PrismOptions options) {
        if (options != null) {
            if (options.placeholder != null) {
                this.placeholder = options.placeholder;
            }
            if (options.placeholderResId != 0) {
                this.placeholderResId = options.placeholderResId;
            }
            if (options.error != null) {
                this.error = options.error;
            }
            if (options.errorResId != 0) {
                this.errorResId = options.errorResId;
            }
            if (options.transformation != null) {
                this.transformation = options.transformation;
            }
            if (options.preferredConfig != null) {
                this.preferredConfig = options.preferredConfig;
            }
            if (options.diskCacheStrategy != null) {
                this.diskCacheStrategy = options.diskCacheStrategy;
                if (this.diskCacheStrategy == DiskCacheStrategy.NONE) {
                    this.skipDiskCache = true;
                }
            }
        }
        return this;
    }

    public PrismRequest placeholder(Drawable drawable) {
        this.placeholder = drawable;
        return this;
    }

    public PrismRequest placeholder(int resId, Context context) {
        this.placeholder = ContextCompat.getDrawable(context, resId);
        return this;
    }

    public PrismRequest error(Drawable drawable) {
        this.error = drawable;
        return this;
    }

    public PrismRequest error(int resId, Context context) {
        this.error = ContextCompat.getDrawable(context, resId);
        return this;
    }

    /** Applies a post-decode transform (e.g. CircleTransformation) on the background thread. */
    public PrismRequest transform(Transformation transformation) {
        this.transformation = transformation;
        return this;
    }

    /** Forces a decode target size instead of measuring the destination ImageView. */
    public PrismRequest override(int width, int height) {
        this.overrideWidth = width;
        this.overrideHeight = height;
        return this;
    }

    public PrismRequest skipMemoryCache(boolean skip) {
        this.skipMemoryCache = skip;
        return this;
    }

    public PrismRequest skipDiskCache(boolean skip) {
        this.skipDiskCache = skip;
        return this;
    }

    /** Enabled by default; cross-fades from the current drawable into the loaded bitmap. */
    public PrismRequest crossFade(boolean enabled) {
        this.crossFade = enabled;
        return this;
    }

    private String getSourceString(Object source) {
        if (source instanceof String) {
            return (String) source;
        } else if (source instanceof Uri) {
            return source.toString();
        } else if (source instanceof byte[]) {
            return "bytes_" + source.hashCode();
        } else if (source instanceof Integer) {
            return "res_" + source;
        }
        return source != null ? source.toString() : "";
    }

    /**
     * Executes the request as a preloading task to warm the cache, with no destination view.
     */
    public void preload() {
        if (rawSource == null) {
            return;
        }
        Context targetContext = context;
        if (targetContext == null) {
            Prism prism = Prism.getInstance(null);
            if (prism != null) {
                targetContext = prism.context();
            }
        }
        if (targetContext == null) {
            return;
        }
        Prism prism = Prism.getInstance(targetContext);
        String sourceStr = getSourceString(rawSource);
        String cacheKey = CacheKeys.build(sourceStr, transformation, overrideWidth, overrideHeight);

        if (placeholder == null && placeholderResId != 0) {
            placeholder = ContextCompat.getDrawable(targetContext, placeholderResId);
        }
        if (error == null && errorResId != 0) {
            error = ContextCompat.getDrawable(targetContext, errorResId);
        }

        PrismLoadTask task = new PrismLoadTask(
                prism, rawSource, cacheKey, transformation,
                overrideWidth > 0 ? overrideWidth : -1,
                overrideHeight > 0 ? overrideHeight : -1,
                skipMemoryCache, skipDiskCache,
                0, TAG_KEY, null,
                placeholder, error, false,
                preferredConfig, diskCacheStrategy, true
        );
        prism.executor().execute(task);
    }

    /**
     * Executes the request against the target ImageView. Safe to call repeatedly from a
     * RecyclerView binder: each call stamps a fresh request id on the view, so a slow,
     * stale result arriving after the view has been rebound is detected and dropped.
     */
    public void into(ImageView target) {
        if (target == null) {
            return;
        }

        Context targetContext = target.getContext();
        Prism prism = Prism.getInstance(targetContext);

        if (placeholder == null && placeholderResId != 0) {
            placeholder = ContextCompat.getDrawable(targetContext, placeholderResId);
        }
        if (error == null && errorResId != 0) {
            error = ContextCompat.getDrawable(targetContext, errorResId);
        }

        if (rawSource == null) {
            target.setTag(TAG_KEY, null);
            if (error != null) {
                target.setImageDrawable(error);
            } else if (placeholder != null) {
                target.setImageDrawable(placeholder);
            }
            return;
        }

        if (placeholder != null) {
            target.setImageDrawable(placeholder);
        }

        long requestId = REQUEST_ID.incrementAndGet();
        target.setTag(TAG_KEY, requestId);

        String sourceStr = getSourceString(rawSource);
        String cacheKey = CacheKeys.build(sourceStr, transformation, overrideWidth, overrideHeight);

        Bitmap cached = prism.memoryCache().get(cacheKey);
        if (cached != null && !cached.isRecycled()) {
            target.setImageBitmap(cached);
            return;
        }

        int reqWidth = overrideWidth > 0 ? overrideWidth : target.getWidth();
        int reqHeight = overrideHeight > 0 ? overrideHeight : target.getHeight();

        PrismLoadTask task = new PrismLoadTask(
                prism, rawSource, cacheKey, transformation,
                reqWidth, reqHeight, skipMemoryCache, skipDiskCache,
                requestId, TAG_KEY, new WeakReference<>(target),
                placeholder, error, crossFade,
                preferredConfig, diskCacheStrategy, false
        );
        prism.executor().execute(task);
    }
}

