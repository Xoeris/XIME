package xime.core.dispatch;

import android.os.Handler;
import android.os.Looper;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * XIME.Core Threading Dispatcher.
 */
public class Dispatcher {
    private static final ExecutorService backgroundExecutor = Executors.newFixedThreadPool(4);
    private static final Handler mainHandler = new Handler(Looper.getMainLooper());

    public static void io(Runnable runnable) {
        backgroundExecutor.execute(runnable);
    }

    public static void main(Runnable runnable) {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            runnable.run();
        } else {
            mainHandler.post(runnable);
        }
    }
}
