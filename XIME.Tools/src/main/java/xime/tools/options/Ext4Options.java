package xime.tools.options;

/**
 * Block/inode/feature options for creating an ext4 image via
 * {@link xime.tools.XimeTools#packImage}.
 *
 * <p>Use the {@link Builder} to construct instances:
 *
 * <pre>{@code
 * Ext4Options opts = new Ext4Options.Builder()
 *     .blockSize(4096)
 *     .inodeSize(256)
 *     .enableJournal(false)
 *     .build();
 * }</pre>
 */
public final class Ext4Options {

    /**
     * Bitmask constants for ext4 feature flags passed to {@code ext2fs_initialize()}.
     * Values correspond to the {@code EXT4_FEATURE_INCOMPAT_*} and
     * {@code EXT4_FEATURE_COMPAT_*} definitions in e2fsprogs' {@code ext2fs.h}.
     */
    public static final class Features {
        private Features() {}

        /** Extents (required for large files; enabled by default). */
        public static final long EXTENTS          = 1L << 6;
        /** Huge file support. */
        public static final long HUGE_FILE        = 1L << 9;
        /** 64-bit feature flag. */
        public static final long BIT64            = 1L << 10;
        /** Flexible block groups. */
        public static final long FLEX_BG          = 1L << 25;
        /** Directory indexing via HTree. */
        public static final long DIR_INDEX        = 1L << 5;
        /** Sparse superblocks (compat). */
        public static final long SPARSE_SUPER     = 1L << 2;
        /** Journal (compat). */
        public static final long HAS_JOURNAL      = 1L << 2; // compat bit for journal
    }

    private final int blockSize;
    private final int inodeSize;
    private final boolean enableJournal;
    private final long featureFlags;
    private final long volumeSizeBytes;

    private Ext4Options(Builder b) {
        this.blockSize = b.blockSize;
        this.inodeSize = b.inodeSize;
        this.enableJournal = b.enableJournal;
        this.featureFlags = b.featureFlags;
        this.volumeSizeBytes = b.volumeSizeBytes;
    }

    public int getBlockSize() { return blockSize; }
    public int getInodeSize() { return inodeSize; }
    public boolean isJournalEnabled() { return enableJournal; }
    /** Raw feature flags bitmask forwarded to {@code ext2fs_initialize()}. */
    public long getFeatureFlags() { return featureFlags; }
    /**
     * Size of the output image in bytes. 0 means the library should calculate the minimum
     * size that fits the source directory contents.
     */
    public long getVolumeSizeBytes() { return volumeSizeBytes; }

    /** Builder for {@link Ext4Options}. */
    public static final class Builder {
        private int blockSize = 4096;
        private int inodeSize = 256;
        private boolean enableJournal = false;
        private long featureFlags = Features.EXTENTS | Features.DIR_INDEX | Features.SPARSE_SUPER;
        private long volumeSizeBytes = 0;

        /**
         * Block size in bytes. Must be a power of two in [1024, 65536].
         * Default: 4096.
         */
        public Builder blockSize(int blockSize) {
            if (blockSize < 1024 || blockSize > 65536 || (blockSize & (blockSize - 1)) != 0) {
                throw new IllegalArgumentException(
                    "blockSize must be a power of two in [1024, 65536], got: " + blockSize);
            }
            this.blockSize = blockSize;
            return this;
        }

        /**
         * Inode size in bytes. Must be 128 or 256. Default: 256.
         */
        public Builder inodeSize(int inodeSize) {
            if (inodeSize != 128 && inodeSize != 256) {
                throw new IllegalArgumentException("inodeSize must be 128 or 256, got: " + inodeSize);
            }
            this.inodeSize = inodeSize;
            return this;
        }

        /**
         * Whether to include an ext3-style journal. Journaling is off by default for
         * ROM-flashing use cases where the image is mounted read-only.
         */
        public Builder enableJournal(boolean enableJournal) {
            this.enableJournal = enableJournal;
            return this;
        }

        /**
         * Replaces the default feature flags bitmask. Use {@link Features} constants.
         * Default: {@code EXTENTS | DIR_INDEX | SPARSE_SUPER}.
         */
        public Builder featureFlags(long featureFlags) {
            this.featureFlags = featureFlags;
            return this;
        }

        /**
         * Adds a feature flag to the current set without replacing other flags.
         */
        public Builder addFeature(long flag) {
            this.featureFlags |= flag;
            return this;
        }

        /**
         * Target volume size in bytes. 0 = auto-size (minimum to fit content). Default: 0.
         */
        public Builder volumeSizeBytes(long volumeSizeBytes) {
            if (volumeSizeBytes < 0) {
                throw new IllegalArgumentException("volumeSizeBytes must be >= 0");
            }
            this.volumeSizeBytes = volumeSizeBytes;
            return this;
        }

        /** Builds the {@link Ext4Options}. */
        public Ext4Options build() {
            return new Ext4Options(this);
        }
    }
}
