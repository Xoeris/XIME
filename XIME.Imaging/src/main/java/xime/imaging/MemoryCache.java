package xime.imaging;

import android.graphics.Bitmap;
import android.util.LruCache;

/**
 * LRU in-memory bitmap cache, sized against the runtime's
 * max heap, mirroring Glide's default memory cache sizing behavior.
 */
final class MemoryCache {

    private final LruCache<String, Bitmap> cache;

    MemoryCache() {
        int maxMemoryKb = (int) (Runtime.getRuntime().maxMemory() / 1024);
        int cacheSizeKb = maxMemoryKb / 8;
        cache = new LruCache<String, Bitmap>(cacheSizeKb) {
            @Override
            protected int sizeOf(String key, Bitmap bitmap) {
                return bitmap.getByteCount() / 1024;
            }
        };
    }

    Bitmap get(String key) {
        return cache.get(key);
    }

    void put(String key, Bitmap bitmap) {
        if (bitmap != null && get(key) == null) {
            cache.put(key, bitmap);
        }
    }

    void clear() {
        cache.evictAll();
    }
}

