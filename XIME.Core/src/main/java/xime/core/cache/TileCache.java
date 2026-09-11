package xime.core.cache;

import android.graphics.Bitmap;
import android.util.LruCache;

/**
 * LRU In-memory cache for Map Tiles.
 */
public class TileCache {
    private final LruCache<String, Bitmap> cache;

    public TileCache(int maxTileCount) {
        this.cache = new LruCache<String, Bitmap>(maxTileCount) {
            @Override
            protected int sizeOf(String key, Bitmap value) {
                return 1; // Count by number of tiles
            }
        };
    }

    public void put(String key, Bitmap bitmap) {
        if (key != null && bitmap != null) {
            cache.put(key, bitmap);
        }
    }

    public Bitmap get(String key) {
        return cache.get(key);
    }

    public void clear() {
        cache.evictAll();
    }
}
