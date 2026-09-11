package xime.tools;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;

import xime.tools.exception.FilesystemCorruptException;
import xime.tools.exception.UnsupportedFsException;
import xime.tools.exception.XimeToolsException;
import xime.tools.options.PackOptions;

/**
 * Public facade for XIME.Tools filesystem operations.
 *
 * <p>This is the primary entry point for callers. Internally it dispatches to
 * {@link XimeToolsNative} JNI calls for in-process operations via liberofs/libext2fs,
 * translating native error codes into the typed exception hierarchy rather than
 * exposing raw integers.
 *
 * <p><strong>Threading</strong>: all methods block the calling thread for the duration
 * of the operation. Callers are responsible for dispatching to a background thread —
 * e.g. via {@code BackgroundZenith.execute()} — before calling any method on this class.
 * This matches the XIME convention established in {@code ShellBootstrap} and
 * {@code xime.terminal}.
 *
 * <p><strong>Null safety</strong>: {@code File} parameters must not be {@code null}.
 * A {@code null} {@link ProgressListener} is legal and simply disables progress reporting.
 *
 * <h2>Usage example</h2>
 * <pre>{@code
 * BackgroundZenith.execute(() -> {
 *     try {
 *         FsType type = XimeTools.detectFilesystemType(new File("/sdcard/system.img"));
 *         XimeTools.extractImage(
 *             new File("/sdcard/system.img"),
 *             new File("/sdcard/system_out/"),
 *             new ProgressListener() {
 *                 public void onProgress(long done, long total) {
 *                     BackgroundZenith.runOnUiThread(() -> progressBar.setProgress((int)(done * 100 / total)));
 *                 }
 *                 public void onComplete() { ... }
 *                 public void onError(String msg) { ... }
 *             });
 *     } catch (XimeToolsException e) {
 *         // handle error
 *     }
 * });
 * }</pre>
 */
public final class XimeTools {

    // Number of bytes to read for superblock magic detection.
    // Must cover the ext4 magic field at absolute offset 1082 (1024 + 56 + 2).
    private static final int DETECTION_HEADER_SIZE = 1082;

    /**
     * Native error code range boundaries used for exception mapping.
     * Ranges correspond to the XIME.Tools native error code table defined in
     * {@code xime_tools_jni.cpp} (XIME_ERR_* constants).
     */
    private static final int ERR_CORRUPT_MIN = -200;
    private static final int ERR_CORRUPT_MAX = -100;
    private static final int ERR_UNSUPPORTED  = -300;

    private XimeTools() {}

    // -------------------------------------------------------------------------
    // Public API
    // -------------------------------------------------------------------------

    /**
     * Detects the filesystem type of an image file by reading its superblock magic bytes.
     *
     * <p>This is a pure-Java operation and does not invoke any JNI code.
     *
     * @param image The image file to inspect. Must exist and be readable.
     * @return The detected {@link FsType}, or {@link FsType#UNKNOWN} if the magic bytes
     *         do not match any supported filesystem.
     * @throws XimeToolsException if the file cannot be read.
     * @throws IllegalArgumentException if {@code image} is {@code null}.
     */
    public static FsType detectFilesystemType(File image) throws XimeToolsException {
        if (image == null) throw new IllegalArgumentException("image must not be null");
        byte[] header = readHeader(image, DETECTION_HEADER_SIZE);
        return FsType.detectFromBytes(header);
    }

    /**
     * Extracts the contents of a filesystem image to a directory.
     *
     * <p>The filesystem type is auto-detected from the image's superblock. For
     * finer control, use the type-specific native methods via {@link XimeToolsNative}
     * (package-private).
     *
     * @param image     Source image file. Must exist, be readable, and contain a
     *                  supported filesystem (EROFS or EXT4).
     * @param outputDir Destination directory. Will be created if it does not exist.
     * @param listener  Optional progress callback. Pass {@code null} to disable.
     * @throws UnsupportedFsException     if the image's filesystem type cannot be detected.
     * @throws FilesystemCorruptException if the native library reports corrupt image data.
     * @throws XimeToolsException         for all other native or IO errors.
     * @throws IllegalArgumentException   if {@code image} or {@code outputDir} is {@code null}.
     */
    public static void extractImage(File image, File outputDir, ProgressListener listener)
            throws XimeToolsException {
        if (image == null)     throw new IllegalArgumentException("image must not be null");
        if (outputDir == null) throw new IllegalArgumentException("outputDir must not be null");

        if (!outputDir.exists() && !outputDir.mkdirs()) {
            throw new XimeToolsException(
                "Could not create output directory: " + outputDir.getAbsolutePath());
        }

        FsType type = detectFilesystemType(image);
        int result;

        switch (type) {
            case EROFS:
                result = XimeToolsNative.nativeExtractErofs(
                    image.getAbsolutePath(), outputDir.getAbsolutePath(), listener);
                break;
            case EXT4:
                result = XimeToolsNative.nativeExtractExt4(
                    image.getAbsolutePath(), outputDir.getAbsolutePath(), listener);
                break;
            default:
                throw new UnsupportedFsException(
                    "Cannot extract image: unrecognized filesystem type in " + image.getName());
        }

        checkNativeResult(result, "extract", image.getName());
    }

    /**
     * Packs a source directory into a filesystem image.
     *
     * @param sourceDir   Directory to pack. Must exist and be readable.
     * @param outputImage Destination image path. Parent directories must exist.
     * @param options     Filesystem type and backend-specific options. Use
     *                    {@link PackOptions#forErofs(xime.tools.options.ErofsOptions)} or
     *                    {@link PackOptions#forExt4(xime.tools.options.Ext4Options)}.
     * @param listener    Optional progress callback. Pass {@code null} to disable.
     * @throws UnsupportedFsException   if {@code options.getFsType()} is {@link FsType#UNKNOWN}.
     * @throws XimeToolsException       for native or IO errors.
     * @throws IllegalArgumentException if any required argument is {@code null}.
     */
    public static void packImage(File sourceDir, File outputImage,
                                 PackOptions options, ProgressListener listener)
            throws XimeToolsException {
        if (sourceDir == null)   throw new IllegalArgumentException("sourceDir must not be null");
        if (outputImage == null) throw new IllegalArgumentException("outputImage must not be null");
        if (options == null)     throw new IllegalArgumentException("options must not be null");

        int result;

        switch (options.getFsType()) {
            case EROFS:
                result = XimeToolsNative.nativeCreateErofs(
                    sourceDir.getAbsolutePath(),
                    outputImage.getAbsolutePath(),
                    options.getErofsOptions(),
                    listener);
                break;
            case EXT4:
                result = XimeToolsNative.nativePackExt4(
                    sourceDir.getAbsolutePath(),
                    outputImage.getAbsolutePath(),
                    options.getExt4Options(),
                    listener);
                break;
            default:
                throw new UnsupportedFsException(
                    "Cannot pack image: FsType.UNKNOWN is not a valid target type.");
        }

        checkNativeResult(result, "pack", outputImage.getName());
    }

    // -------------------------------------------------------------------------
    // Internal helpers
    // -------------------------------------------------------------------------

    /**
     * Maps a native return code to the appropriate exception, or returns normally on success.
     *
     * @param result    The raw native return value (0 = success, negative = error).
     * @param operation Human-readable operation name for error messages.
     * @param fileName  Image or directory name for error context.
     */
    private static void checkNativeResult(int result, String operation, String fileName)
            throws XimeToolsException {
        if (result == 0) return;

        if (result >= ERR_CORRUPT_MIN && result <= ERR_CORRUPT_MAX) {
            throw new FilesystemCorruptException(
                "Native " + operation + " failed: image '" + fileName
                + "' appears to be corrupt or truncated (native code " + result + ")",
                result);
        }

        if (result == ERR_UNSUPPORTED) {
            throw new UnsupportedFsException(
                "Native " + operation + " failed: filesystem not supported by this backend (native code "
                + result + ")",
                result);
        }

        throw new XimeToolsException(
            "Native " + operation + " failed for '" + fileName
            + "' with error code " + result,
            result);
    }

    /**
     * Reads up to {@code maxBytes} from the start of {@code file} for superblock inspection.
     */
    private static byte[] readHeader(File file, int maxBytes) throws XimeToolsException {
        FileInputStream fis = null;
        try {
            fis = new FileInputStream(file);
            int available = (int) Math.min(file.length(), maxBytes);
            byte[] buf = new byte[available];
            int read = 0;
            while (read < available) {
                int n = fis.read(buf, read, available - read);
                if (n < 0) break;
                read += n;
            }
            if (read < available) {
                // Truncate to actual bytes read
                byte[] trimmed = new byte[read];
                System.arraycopy(buf, 0, trimmed, 0, read);
                return trimmed;
            }
            return buf;
        } catch (IOException e) {
            throw new XimeToolsException(
                "Could not read image header from " + file.getAbsolutePath() + ": " + e.getMessage(), e);
        } finally {
            if (fis != null) {
                try { fis.close(); } catch (IOException ignored) {}
            }
        }
    }
}
