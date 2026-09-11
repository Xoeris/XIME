package xime.imaging;

/**
 * Defines the caching strategy for the disk cache.
 */
public enum DiskCacheStrategy {
    /** Caches both remote data and transformed resources. */
    ALL,
    /** No disk caching. */
    NONE,
    /** Caches only decoded/transformed resources. */
    RESOURCE,
    /** Caches only downloaded/source data. */
    DATA
}

