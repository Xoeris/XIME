package xime.core.log;

import android.util.Log;

/**
 * Concrete ILogger implementation that logs to Logcat.
 */
public class LogcatLogger implements ILogger {
    @Override
    public void d(String tag, String message) {
        Log.d(tag, message);
    }

    @Override
    public void e(String tag, String message, Throwable throwable) {
        Log.e(tag, message, throwable);
    }

    @Override
    public void i(String tag, String message) {
        Log.i(tag, message);
    }
}
