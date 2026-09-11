package xime.imaging;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.TransitionDrawable;
import android.net.Uri;
import android.widget.ImageView;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.lang.ref.WeakReference;

/**
 * Background unit of work for a single Prism request:
 * disk cache lookup -> network fetch -> downsampled decode -> transform ->
 * memory cache store -> main-thread dispatch, guarded against stale/recycled
 * targets.
 */
final class PrismLoadTask implements Runnable {

    private final Prism prism;
    private final Object rawSource;
    private final String cacheKey;
    private final Transformation transformation;
    private final int reqWidth;
    private final int reqHeight;
    private final boolean skipMemoryCache;
    private final boolean skipDiskCache;
    private final long requestId;
    private final int tagKey;
    private final WeakReference<ImageView> targetRef;
    private final Drawable placeholder;
    private final Drawable errorDrawable;
    private final boolean crossFade;
    private final Bitmap.Config preferredConfig;
    private final DiskCacheStrategy diskCacheStrategy;
    private final boolean isPreload;

    PrismLoadTask(Prism prism, Object rawSource, String cacheKey, Transformation transformation,
                  int reqWidth, int reqHeight, boolean skipMemoryCache, boolean skipDiskCache,
                  long requestId, int tagKey, WeakReference<ImageView> targetRef,
                  Drawable placeholder, Drawable errorDrawable, boolean crossFade,
                  Bitmap.Config preferredConfig, DiskCacheStrategy diskCacheStrategy, boolean isPreload) {
        this.prism = prism;
        this.rawSource = rawSource;
        this.cacheKey = cacheKey;
        this.transformation = transformation;
        this.reqWidth = reqWidth;
        this.reqHeight = reqHeight;
        this.skipMemoryCache = skipMemoryCache;
        this.skipDiskCache = skipDiskCache;
        this.requestId = requestId;
        this.tagKey = tagKey;
        this.targetRef = targetRef;
        this.placeholder = placeholder;
        this.errorDrawable = errorDrawable;
        this.crossFade = crossFade;
        this.preferredConfig = preferredConfig;
        this.diskCacheStrategy = diskCacheStrategy;
        this.isPreload = isPreload;
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

    @Override
    public void run() {
        if (!isStillCurrent()) {
            return;
        }

        // Handle pause gating
        if (prism.isPaused()) {
            synchronized (prism.getPauseLock()) {
                while (prism.isPaused()) {
                    try {
                        prism.getPauseLock().wait();
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        return;
                    }
                }
            }
        }

        if (!isStillCurrent()) {
            return;
        }

        try {
            String sourceStr = getSourceString(rawSource);
            byte[] bytes = null;
            Bitmap bitmap = null;

            // Check if we can read the transformed resource from disk
            boolean canReadResource = (diskCacheStrategy == DiskCacheStrategy.ALL || diskCacheStrategy == DiskCacheStrategy.RESOURCE) && !skipDiskCache;
            if (canReadResource) {
                byte[] resourceBytes = prism.diskCache().get(cacheKey);
                if (resourceBytes != null) {
                    bitmap = BitmapFactory.decodeByteArray(resourceBytes, 0, resourceBytes.length);
                }
            }

            if (bitmap == null && rawSource instanceof Integer) {
                int resId = (Integer) rawSource;
                BitmapFactory.Options options = new BitmapFactory.Options();
                options.inPreferredConfig = preferredConfig != null ? preferredConfig : Bitmap.Config.ARGB_8888;
                if (reqWidth > 0 && reqHeight > 0) {
                    options.inJustDecodeBounds = true;
                    BitmapFactory.decodeResource(prism.context().getResources(), resId, options);
                    options.inSampleSize = BitmapDecoder.calculateInSampleSize(options, reqWidth, reqHeight);
                    options.inJustDecodeBounds = false;
                }
                bitmap = BitmapFactory.decodeResource(prism.context().getResources(), resId, options);
            }

            if (bitmap == null) {
                if (rawSource instanceof byte[]) {
                    bytes = (byte[]) rawSource;
                } else {
                    boolean canReadData = (diskCacheStrategy == DiskCacheStrategy.ALL || diskCacheStrategy == DiskCacheStrategy.DATA) && !skipDiskCache;
                    if (canReadData) {
                        bytes = prism.diskCache().get(sourceStr);
                    }
                    if (bytes == null) {
                        if (rawSource instanceof Uri) {
                            Uri uri = (Uri) rawSource;
                            if ("http".equals(uri.getScheme()) || "https".equals(uri.getScheme())) {
                                bytes = Downloader.fetch(uri.toString());
                            } else {
                                bytes = extractEmbeddedArtFromUri(uri);
                                if (bytes == null) {
                                    bytes = readFromUri(uri);
                                }
                            }
                        } else if (rawSource instanceof java.io.File) {
                            java.io.File file = (java.io.File) rawSource;
                            bytes = extractEmbeddedArt(file.getAbsolutePath());
                            if (bytes == null) {
                                bytes = readFromUri(Uri.fromFile(file));
                            }
                        } else if (rawSource instanceof String) {
                            String str = (String) rawSource;
                            if (str.startsWith("http://") || str.startsWith("https://")) {
                                bytes = Downloader.fetch(str);
                            } else {
                                try {
                                    Uri uri = Uri.parse(str);
                                    bytes = extractEmbeddedArtFromUri(uri);
                                } catch (Exception ignored) {}
                                if (bytes == null) {
                                    bytes = extractEmbeddedArt(str);
                                }
                                if (bytes == null) {
                                    try {
                                        bytes = readFromUri(Uri.parse(str));
                                    } catch (Exception e) {
                                        java.io.File file = new java.io.File(str);
                                        if (file.exists()) {
                                            bytes = readFromUri(Uri.fromFile(file));
                                        }
                                    }
                                }
                            }
                        }
                        boolean canWriteData = (diskCacheStrategy == DiskCacheStrategy.ALL || diskCacheStrategy == DiskCacheStrategy.DATA) && !skipDiskCache;
                        if (bytes != null && canWriteData) {
                            prism.diskCache().put(sourceStr, bytes);
                        }
                    }
                }

                if (bytes == null) {
                    dispatchError();
                    return;
                }

                bitmap = BitmapDecoder.decodeSampled(bytes, reqWidth, reqHeight, preferredConfig);
            }

            if (bitmap == null) {
                dispatchError();
                return;
            }

            boolean transformedNow = false;
            if (transformation != null && !canReadResource) {
                Bitmap transformed = transformation.transform(bitmap, reqWidth, reqHeight);
                if (transformed != bitmap) {
                    bitmap.recycle();
                    bitmap = transformed;
                    transformedNow = true;
                }
            }

            boolean canWriteResource = (diskCacheStrategy == DiskCacheStrategy.ALL || diskCacheStrategy == DiskCacheStrategy.RESOURCE) && !skipDiskCache;
            if (canWriteResource && (transformedNow || !canReadResource)) {
                try (ByteArrayOutputStream bos = new ByteArrayOutputStream()) {
                    bitmap.compress(Bitmap.CompressFormat.PNG, 100, bos);
                    prism.diskCache().put(cacheKey, bos.toByteArray());
                } catch (Exception e) {
                    prism.logger().e(Prism.TAG, "Failed to write resource to disk cache", e);
                }
            }

            if (!skipMemoryCache) {
                prism.memoryCache().put(cacheKey, bitmap);
            }

            if (!isPreload) {
                dispatchSuccess(bitmap);
            }
        } catch (Exception e) {
            prism.logger().e(Prism.TAG, "Prism load failed for " + getSourceString(rawSource), e);
            dispatchError();
        }
    }

    private byte[] readFromUri(Uri uri) {
        try (InputStream in = prism.context().getContentResolver().openInputStream(uri);
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[8192];
            int read;
            while ((read = in.read(buffer)) != -1) {
                out.write(buffer, 0, read);
            }
            return out.toByteArray();
        } catch (Exception e) {
            prism.logger().e(Prism.TAG, "Failed to read from Uri: " + uri, e);
            return null;
        }
    }

    private byte[] extractEmbeddedArt(String path) {
        android.media.MediaMetadataRetriever retriever = null;
        try {
            retriever = new android.media.MediaMetadataRetriever();
            retriever.setDataSource(path);
            return retriever.getEmbeddedPicture();
        } catch (Exception e) {
            return null;
        } finally {
            if (retriever != null) {
                try {
                    retriever.release();
                } catch (Exception ignored) {}
            }
        }
    }

    private byte[] extractEmbeddedArtFromUri(Uri uri) {
        android.media.MediaMetadataRetriever retriever = null;
        try {
            retriever = new android.media.MediaMetadataRetriever();
            retriever.setDataSource(prism.context(), uri);
            return retriever.getEmbeddedPicture();
        } catch (Exception e) {
            return null;
        } finally {
            if (retriever != null) {
                try {
                    retriever.release();
                } catch (Exception ignored) {}
            }
        }
    }

    private boolean isStillCurrent() {
        if (isPreload) {
            return true;
        }
        ImageView target = targetRef != null ? targetRef.get() : null;
        if (target == null) {
            return false;
        }
        Object currentTag = target.getTag(tagKey);
        return (currentTag instanceof Long) && (Long) currentTag == requestId;
    }

    private void dispatchSuccess(Bitmap bitmap) {
        prism.mainHandler().post(() -> {
            if (!isStillCurrent()) {
                return;
            }
            ImageView target = targetRef != null ? targetRef.get() : null;
            if (target == null) {
                return;
            }
            if (crossFade && target.getDrawable() != null) {
                Drawable[] layers = new Drawable[]{
                        target.getDrawable(),
                        new BitmapDrawable(target.getResources(), bitmap)
                };
                TransitionDrawable transition = new TransitionDrawable(layers);
                target.setImageDrawable(transition);
                transition.startTransition(150);
            } else {
                target.setImageBitmap(bitmap);
            }
        });
    }

    private void dispatchError() {
        if (isPreload || errorDrawable == null) {
            return;
        }
        prism.mainHandler().post(() -> {
            if (!isStillCurrent()) {
                return;
            }
            ImageView target = targetRef != null ? targetRef.get() : null;
            if (target != null) {
                target.setImageDrawable(errorDrawable);
            }
        });
    }
}

