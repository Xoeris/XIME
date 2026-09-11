package xime.system.optimization.zenith;

import java.util.ArrayList;
import java.util.List;

public final class ObjectPoolingZenith<T> {
    private final List<T> pool;
    private final Creator<T> creator;
    private final int maxPoolSize;

    public interface Creator<T> {
        T create();
    }

    public ObjectPoolingZenith(int maxPoolSize, Creator<T> creator) {
        this.maxPoolSize = maxPoolSize;
        this.creator = creator;
        this.pool = new ArrayList<>(maxPoolSize);
    }

    public T acquire() {
        synchronized (pool) {
            if (!pool.isEmpty()) {
                return pool.remove(pool.size() - 1);
            }
        }
        return creator.create();
    }

    public void release(T instance) {
        if (instance == null) return;
        synchronized (pool) {
            if (pool.size() < maxPoolSize) {
                pool.add(instance);
            }
        }
    }
}
