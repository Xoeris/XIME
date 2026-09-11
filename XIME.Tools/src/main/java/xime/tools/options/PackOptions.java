package xime.tools.options;

import xime.tools.FsType;

/**
 * Thin wrapper that holds either {@link ErofsOptions} or {@link Ext4Options}, selected
 * by the caller when using {@link xime.tools.XimeTools#packImage}.
 *
 * <p>Exactly one of {@link #getErofsOptions()} or {@link #getExt4Options()} will be
 * non-null; the other will return {@code null}. The active type is reported by
 * {@link #getFsType()}.
 */
public final class PackOptions {

    private final FsType fsType;
    private final ErofsOptions erofsOptions;
    private final Ext4Options ext4Options;

    private PackOptions(FsType fsType, ErofsOptions erofsOptions, Ext4Options ext4Options) {
        this.fsType = fsType;
        this.erofsOptions = erofsOptions;
        this.ext4Options = ext4Options;
    }

    /**
     * Creates {@link PackOptions} for an EROFS image.
     *
     * @param opts Must not be {@code null}.
     */
    public static PackOptions forErofs(ErofsOptions opts) {
        if (opts == null) throw new IllegalArgumentException("ErofsOptions must not be null");
        return new PackOptions(FsType.EROFS, opts, null);
    }

    /**
     * Creates {@link PackOptions} for an ext4 image.
     *
     * @param opts Must not be {@code null}.
     */
    public static PackOptions forExt4(Ext4Options opts) {
        if (opts == null) throw new IllegalArgumentException("Ext4Options must not be null");
        return new PackOptions(FsType.EXT4, null, opts);
    }

    /**
     * Returns the target filesystem type ({@link FsType#EROFS} or {@link FsType#EXT4}).
     */
    public FsType getFsType() { return fsType; }

    /**
     * Returns the EROFS-specific options, or {@code null} if this is an EXT4 pack.
     */
    public ErofsOptions getErofsOptions() { return erofsOptions; }

    /**
     * Returns the ext4-specific options, or {@code null} if this is an EROFS pack.
     */
    public Ext4Options getExt4Options() { return ext4Options; }
}
