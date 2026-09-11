package xime.system.optimization.zenith;

import java.lang.ref.SoftReference;
import java.util.concurrent.ConcurrentHashMap;

public final class CachingZenith<K, V> {
    private final ConcurrentHashMap<K, SoftReference<V>> softCache = new ConcurrentHashMap<>();
    private final MemoryZenith<K, V> strongCache;

    public CachingZenith(int strongCacheSize) {
        this.strongCache = new MemoryZenith<>(strongCacheSize);
    }

    public V get(K key) {
        V val = strongCache.get(key);
        if (val != null) return val;

        SoftReference<V> softRef = softCache.get(key);
        if (softRef != null) {
            val = softRef.get();
            if (val != null) {
                strongCache.put(key, val);
                return val;
            }
            softCache.remove(key);
        }
        return null;
    }

    public void put(K key, V value) {
        if (value == null) return;
        strongCache.put(key, value);
        softCache.put(key, new SoftReference<>(value));
    }

    public void clear() {
        strongCache.clear();
        softCache.clear();
    }
}
