package xime.system.optimization.zenith;

import java.util.concurrent.ConcurrentHashMap;

public final class RAMSingletonZenith {
    private static final ConcurrentHashMap<String, Object> ramStore = new ConcurrentHashMap<>();

    private RAMSingletonZenith() {}

    public static void put(String key, Object value) {
        if (key != null && value != null) {
            ramStore.put(key, value);
        }
    }

    public static Object get(String key) {
        if (key == null) return null;
        return ramStore.get(key);
    }

    @SuppressWarnings("unchecked")
    public static <T> T get(String key, Class<T> clazz) {
        Object val = get(key);
        if (val != null && clazz.isInstance(val)) {
            return (T) val;
        }
        return null;
    }

    public static void clear() {
        ramStore.clear();
    }
}
