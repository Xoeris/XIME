package xime.tools.exception;

/**
 * Base exception thrown by {@link xime.tools.XimeTools} operations when a native or
 * Java-layer error occurs. Carries the raw native error code for diagnostic purposes.
 *
 * <p>Subclasses provide more specific error categories:
 * <ul>
 *   <li>{@link FilesystemCorruptException} — image data is corrupt or truncated.</li>
 *   <li>{@link UnsupportedFsException} — the filesystem type is not supported by this backend.</li>
 * </ul>
 */
public class XimeToolsException extends Exception {

    /**
     * Sentinel value indicating the error code was not set by a native layer
     * (e.g. a pure-Java validation failure).
     */
    public static final int NO_NATIVE_CODE = Integer.MIN_VALUE;

    private final int nativeErrorCode;

    public XimeToolsException(String message) {
        super(message);
        this.nativeErrorCode = NO_NATIVE_CODE;
    }

    public XimeToolsException(String message, int nativeErrorCode) {
        super(message);
        this.nativeErrorCode = nativeErrorCode;
    }

    public XimeToolsException(String message, Throwable cause) {
        super(message, cause);
        this.nativeErrorCode = NO_NATIVE_CODE;
    }

    public XimeToolsException(String message, int nativeErrorCode, Throwable cause) {
        super(message, cause);
        this.nativeErrorCode = nativeErrorCode;
    }

    /**
     * Returns the raw error code returned by the native library, or
     * {@link #NO_NATIVE_CODE} if the exception originated in Java code.
     */
    public int getNativeErrorCode() {
        return nativeErrorCode;
    }
}
