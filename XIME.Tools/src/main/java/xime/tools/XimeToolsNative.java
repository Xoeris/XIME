package xime.tools;

import xime.tools.options.ErofsOptions;
import xime.tools.options.Ext4Options;

/**
 * Package-private JNI declaration class.
 *
 * <p>Mirrors the pattern established by {@code xime.terminal.pty.TerminalNative}: a
 * final class with a {@code static {}} initializer that loads the native library, plus
 * {@code static native} method declarations matching JNI symbols in
 * {@code xime_tools_jni.cpp}.
 *
 * <p>Callers should use {@link XimeTools} rather than this class directly; the public
 * facade handles error translation and provides a stable API.
 */
final class XimeToolsNative {

    static {
        NativeLibraryLoader.load();
    }

    private XimeToolsNative() {}

    // -------------------------------------------------------------------------
    // Filesystem detection (native fallback; Java superblock parsing is preferred)
    // -------------------------------------------------------------------------

    /**
     * Native filesystem type detection via libext2fs/liberofs probing.
     *
     * @param imagePath Absolute path to the image file.
     * @return The ordinal of the matching {@link FsType} value, or
     *         {@code FsType.UNKNOWN.ordinal()} if the type cannot be determined.
     */
    static native int nativeDetectFs(String imagePath);

    // -------------------------------------------------------------------------
    // EROFS operations (in-process via liberofs)
    // -------------------------------------------------------------------------

    /**
     * Extracts an EROFS image to {@code outputDir} in-process via liberofs.
     *
     * @param imagePath  Absolute path to the source EROFS image.
     * @param outputDir  Absolute path to the target extraction directory.
     * @param listener   Optional {@link ProgressListener}; may be {@code null}.
     * @return 0 on success, or a negative native error code on failure.
     */
    static native int nativeExtractErofs(String imagePath, String outputDir, ProgressListener listener);

    /**
     * Creates an EROFS image from a source directory in-process via liberofs.
     *
     * @param sourceDir  Absolute path to the source directory tree.
     * @param imagePath  Absolute path for the output EROFS image.
     * @param opts       Compression and feature options.
     * @param listener   Optional {@link ProgressListener}; may be {@code null}.
     * @return 0 on success, or a negative native error code on failure.
     */
    static native int nativeCreateErofs(String sourceDir, String imagePath,
                                        ErofsOptions opts, ProgressListener listener);

    // -------------------------------------------------------------------------
    // EXT4 operations (in-process via libext2fs)
    // -------------------------------------------------------------------------

    /**
     * Extracts an ext4 image to {@code outputDir} in-process via libext2fs.
     *
     * @param imagePath  Absolute path to the source ext4 image.
     * @param outputDir  Absolute path to the target extraction directory.
     * @param listener   Optional {@link ProgressListener}; may be {@code null}.
     * @return 0 on success, or a negative native error code on failure.
     */
    static native int nativeExtractExt4(String imagePath, String outputDir, ProgressListener listener);

    /**
     * Creates an ext4 image from a source directory in-process via libext2fs.
     *
     * @param sourceDir  Absolute path to the source directory tree.
     * @param imagePath  Absolute path for the output ext4 image.
     * @param opts       Block/inode/feature options.
     * @param listener   Optional {@link ProgressListener}; may be {@code null}.
     * @return 0 on success, or a negative native error code on failure.
     */
    static native int nativePackExt4(String sourceDir, String imagePath,
                                     Ext4Options opts, ProgressListener listener);

    // -------------------------------------------------------------------------
    // Subprocess binary mode (for tools that cannot be JNI-linked)
    // -------------------------------------------------------------------------

    /**
     * Returns the absolute path to a provisioned subprocess binary for the given tool,
     * or {@code null} if the binary has not been bootstrapped yet.
     *
     * @param toolName  Name of the tool (e.g. {@code "fsck.erofs"}, {@code "resize2fs"}).
     * @return Absolute path string, or {@code null}.
     */
    static native String nativeGetSubprocessBinaryPath(String toolName);
}
