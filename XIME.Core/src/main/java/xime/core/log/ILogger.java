package xime.core.log;

/**
 * XIME logging interface, placed in Core to avoid circular dependencies.
 */
public interface ILogger {
    void d(String tag, String message);
    void e(String tag, String message, Throwable throwable);
    void i(String tag, String message);
}
