package xime.tools;

/**
 * Represents the type of a filesystem image, as detected by magic-byte inspection
 * of the image superblock. No native call is required for detection; all logic is
 * implemented in pure Java by reading raw bytes at known superblock offsets.
 *
 * <p>Magic byte references:
 * <ul>
 *   <li>EROFS, 4-byte magic {@code 0xE0F5E1E2} at byte offset 1024 (superblock start).</li>
 *   <li>EXT4 , 2-byte magic {@code 0xEF53} at superblock offset 56 (absolute offset 1080,
 *               since the superblock begins at byte 1024).</li>
 * </ul>
 */
public enum FsType {

    /** EROFS (Enhanced Read-Only File System). */
    EROFS,

    /** ext4 / ext2 / ext3 (libext2fs-compatible). */
    EXT4,

    /** Could not determine the filesystem type from the image header. */
    UNKNOWN;

    // -------------------------------------------------------------------------
    // Superblock constants
    // -------------------------------------------------------------------------

    /** Byte offset at which both EROFS and ext4 superblocks start. */
    private static final int SUPERBLOCK_OFFSET = 1024;

    /** EROFS superblock magic: little-endian 0xE0F5E1E2. */
    private static final long EROFS_MAGIC = 0xE0F5E1E2L;

    /**
     * Ext4 superblock magic (little-endian {@code 0xEF53}) stored at superblock offset 56,
     * i.e., absolute file offset {@code SUPERBLOCK_OFFSET + 56 = 1080}.
     */
    private static final int EXT4_MAGIC = 0xEF53;
    private static final int EXT4_MAGIC_OFFSET_IN_SB = 56;

    /**
     * Minimum number of bytes needed from offset 0 to read the ext4 magic field
     * (1024 superblock offset + 56 within superblock + 2 magic bytes).
     */
    private static final int MIN_HEADER_BYTES = SUPERBLOCK_OFFSET + EXT4_MAGIC_OFFSET_IN_SB + 2;

    // -------------------------------------------------------------------------
    // Detection helpers
    // -------------------------------------------------------------------------

    /**
     * Detects the filesystem type from the first bytes of an image file.
     *
     * @param header At least {@value #MIN_HEADER_BYTES} bytes read from offset 0 of the image.
     *               Shorter arrays will return {@link #UNKNOWN} rather than throwing.
     * @return The detected {@link FsType}, or {@link #UNKNOWN} if the header is too short
     *         or does not match any known magic.
     */
    public static FsType detectFromBytes(byte[] header) {
        if (header == null || header.length < SUPERBLOCK_OFFSET + 4) {
            return UNKNOWN;
        }

        // --- EROFS check: 4-byte LE magic at offset 1024 ---
        long magic = readUInt32LE(header, SUPERBLOCK_OFFSET);
        if (magic == EROFS_MAGIC) {
            return EROFS;
        }

        // --- EXT4 check: 2-byte LE magic at absolute offset 1080 ---
        if (header.length >= MIN_HEADER_BYTES) {
            int ext4Magic = readUInt16LE(header, SUPERBLOCK_OFFSET + EXT4_MAGIC_OFFSET_IN_SB);
            if (ext4Magic == EXT4_MAGIC) {
                return EXT4;
            }
        }

        return UNKNOWN;
    }

    // -------------------------------------------------------------------------
    // Private byte-reading utilities
    // -------------------------------------------------------------------------

    private static long readUInt32LE(byte[] buf, int offset) {
        return (buf[offset]     & 0xFFL)
             | ((buf[offset + 1] & 0xFFL) << 8)
             | ((buf[offset + 2] & 0xFFL) << 16)
             | ((buf[offset + 3] & 0xFFL) << 24);
    }

    private static int readUInt16LE(byte[] buf, int offset) {
        return (buf[offset] & 0xFF) | ((buf[offset + 1] & 0xFF) << 8);
    }
}
