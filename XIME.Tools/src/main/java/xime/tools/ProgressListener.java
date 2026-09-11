package xime.tools;

/**
 * Callback interface delivered to long-running XIME.Tools operations so callers can
 * track progress without polling.
 *
 * <p>Implementations of this interface are invoked from a JNI callback registered
 * with the native library. The native layer holds a JNI global reference to the
 * listener for the lifetime of the operation and releases it on completion or error.
 *
 * <p><strong>Threading</strong>: callbacks are delivered on whichever thread the
 * operation runs on (typically a background thread). Implementations must not assume
 * they are called on the main thread. Use {@code BackgroundZenith.runOnUiThread()} or
 * equivalent if UI updates are needed.
 */
public interface ProgressListener {

    /**
     * Called periodically as the operation makes progress.
     *
     * @param bytesProcessed Number of bytes processed so far. May be 0 if the native
     *                       library does not report byte-level progress for this operation.
     * @param totalBytes     Total expected bytes, or {@code -1} if the total is not known
     *                       in advance (e.g., during recursive directory packing where
     *                       the final image size is not pre-determined).
     */
    void onProgress(long bytesProcessed, long totalBytes);

    /**
     * Called exactly once when the operation completes successfully.
     * After this call the native layer releases its reference to this listener.
     */
    void onComplete();

    /**
     * Called if the operation fails. After this call the native layer releases
     * its reference to this listener. {@link #onComplete()} will not be called.
     *
     * @param message A human-readable description of the error. For structured error
     *                handling, callers should also catch {@link xime.tools.exception.XimeToolsException}
     *                from the enclosing {@link XimeTools} method.
     */
    void onError(String message);
}
