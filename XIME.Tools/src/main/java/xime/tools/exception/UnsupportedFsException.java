package xime.tools.exception;

/**
 * Thrown when the caller requests an operation on a filesystem type that XIME.Tools
 * cannot handle, for example, passing {@link xime.tools.FsType#UNKNOWN} to
 * {@link xime.tools.XimeTools#packImage}, or supplying an image whose magic bytes
 * do not correspond to any supported filesystem.
 */
public final class UnsupportedFsException extends XimeToolsException {

    public UnsupportedFsException(String message) {
        super(message);
    }

    public UnsupportedFsException(String message, int nativeErrorCode) {
        super(message, nativeErrorCode);
    }

    public UnsupportedFsException(String message, int nativeErrorCode, Throwable cause) {
        super(message, nativeErrorCode, cause);
    }
}
