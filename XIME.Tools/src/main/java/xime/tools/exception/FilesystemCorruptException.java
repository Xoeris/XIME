package xime.tools.exception;

/**
 * Thrown when the native library detects that an image file is corrupt, truncated,
 * or has an inconsistent superblock/metadata structure.
 *
 * <p>The {@link #getNativeErrorCode()} typically maps to an {@code EUCLEAN} (errno 117)
 * or a library-specific error code such as {@code -EROFS_ERR_CORRUPT} from erofs-utils.
 */
public final class FilesystemCorruptException extends XimeToolsException {

    public FilesystemCorruptException(String message) {
        super(message);
    }

    public FilesystemCorruptException(String message, int nativeErrorCode) {
        super(message, nativeErrorCode);
    }

    public FilesystemCorruptException(String message, int nativeErrorCode, Throwable cause) {
        super(message, nativeErrorCode, cause);
    }
}
