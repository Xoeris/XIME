package xime.system.optimization.zenith;

import android.util.LruCache;

public final class MemoryZenith<K, V> {
    private final LruCache<K, V> cache;

    public MemoryZenith(int maxSize) {
        this.cache = new LruCache<>(maxSize);
    }

    public V get(K key) {
        synchronized (cache) {
            return cache.get(key);
        }
    }

    public void put(K key, V value) {
        synchronized (cache) {
            cache.put(key, value);
        }
    }

    public void clear() {
        synchronized (cache) {
            cache.evictAll();
        }
    }
}
