package xime.terminal;

/**
 * XIME.Terminal logging interface.
 */
public interface ILogger {
    void d(String tag, String message);
    void e(String tag, String message, Throwable throwable);
    void i(String tag, String message);
}
