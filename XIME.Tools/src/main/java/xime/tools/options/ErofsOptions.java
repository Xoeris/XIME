package xime.tools.options;

/**
 * Compression and feature options for creating an EROFS image via
 * {@link xime.tools.XimeTools#packImage}.
 *
 * <p>Use the {@link Builder} to construct instances.
 *
 * <pre>{@code
 * ErofsOptions opts = new ErofsOptions.Builder()
 *     .compression(ErofsOptions.Compression.LZ4HC)
 *     .blockSize(4096)
 *     .build();
 * }</pre>
 */
public final class ErofsOptions {

    /** Supported compression algorithms for EROFS inline data. */
    public enum Compression {
        /** LZ4 default (fast, moderate ratio). */
        LZ4,
        /** LZ4 high-compression variant. */
        LZ4HC,
        /** LZMA (highest ratio, slowest). */
        LZMA,
        /** No compression (store only). */
        NONE
    }

    /** Timestamp handling policy applied to all inodes during image creation. */
    public enum TimestampPolicy {
        /** Preserve original file timestamps from the source directory. */
        PRESERVE,
        /** Zero all timestamps (produces reproducible images). */
        ZERO,
        /** Use current system time for all inodes. */
        NOW
    }

    private final Compression compression;
    private final int blockSize;
    private final TimestampPolicy timestampPolicy;
    private final int lz4HcLevel;

    private ErofsOptions(Builder b) {
        this.compression = b.compression;
        this.blockSize = b.blockSize;
        this.timestampPolicy = b.timestampPolicy;
        this.lz4HcLevel = b.lz4HcLevel;
    }

    public Compression getCompression() { return compression; }
    public int getBlockSize() { return blockSize; }
    public TimestampPolicy getTimestampPolicy() { return timestampPolicy; }
    /** LZ4HC compression level [1–12]. Only used when compression is {@link Compression#LZ4HC}. */
    public int getLz4HcLevel() { return lz4HcLevel; }

    /** Builder for {@link ErofsOptions}. */
    public static final class Builder {
        private Compression compression = Compression.LZ4;
        private int blockSize = 4096;
        private TimestampPolicy timestampPolicy = TimestampPolicy.PRESERVE;
        private int lz4HcLevel = 9;

        /** Sets the compression algorithm. Default: {@link Compression#LZ4}. */
        public Builder compression(Compression compression) {
            if (compression == null) throw new IllegalArgumentException("compression must not be null");
            this.compression = compression;
            return this;
        }

        /**
         * Sets the filesystem block size in bytes. Must be a power of two between 512 and 65536.
         * Default: 4096.
         */
        public Builder blockSize(int blockSize) {
            if (blockSize < 512 || blockSize > 65536 || (blockSize & (blockSize - 1)) != 0) {
                throw new IllegalArgumentException(
                    "blockSize must be a power of two in [512, 65536], got: " + blockSize);
            }
            this.blockSize = blockSize;
            return this;
        }

        /** Sets the timestamp policy. Default: {@link TimestampPolicy#PRESERVE}. */
        public Builder timestampPolicy(TimestampPolicy policy) {
            if (policy == null) throw new IllegalArgumentException("timestampPolicy must not be null");
            this.timestampPolicy = policy;
            return this;
        }

        /**
         * Sets the LZ4HC compression level [1–12]. Only relevant when compression is
         * {@link Compression#LZ4HC}. Default: 9.
         */
        public Builder lz4HcLevel(int level) {
            if (level < 1 || level > 12) {
                throw new IllegalArgumentException("lz4HcLevel must be in [1, 12], got: " + level);
            }
            this.lz4HcLevel = level;
            return this;
        }

        /** Builds the {@link ErofsOptions}. */
        public ErofsOptions build() {
            return new ErofsOptions(this);
        }
    }
}
