package xime.core.cache;

import java.util.HashMap;
import java.util.Map;

/**
 * Basic memory cache, moved to Core to avoid circular dependencies.
 */
public class CacheManager {
    private static final long DEFAULT_TTL = 10 * 60 * 1000; // 10 minutes
    private final Map<String, CacheEntry> cache = new HashMap<>();

    private static class CacheEntry {
        final Object data;
        final long expiresAt;

        CacheEntry(Object data, long ttl) {
            this.data = data;
            this.expiresAt = System.currentTimeMillis() + ttl;
        }

        boolean isExpired() {
            return System.currentTimeMillis() > expiresAt;
        }
    }

    public void put(String key, Object data) {
        put(key, data, DEFAULT_TTL);
    }

    public void put(String key, Object data, long ttl) {
        cache.put(key, new CacheEntry(data, ttl));
    }

    @SuppressWarnings("unchecked")
    public <T> T get(String key) {
        CacheEntry entry = cache.get(key);
        if (entry != null && !entry.isExpired()) {
            return (T) entry.data;
        }
        cache.remove(key);
        return null;
    }
}
