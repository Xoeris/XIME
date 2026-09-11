package xime.system.optimization.zenith;

import android.os.Handler;
import android.os.Looper;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class BackgroundZenith {
    private static final ExecutorService scheduler = Executors.newFixedThreadPool(
        Math.max(2, Runtime.getRuntime().availableProcessors())
    );
    private static final Handler uiHandler = new Handler(Looper.getMainLooper());

    private BackgroundZenith() {}

    public static void execute(Runnable runnable) {
        scheduler.execute(runnable);
    }

    public static void runOnUiThread(Runnable runnable) {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            runnable.run();
        } else {
            uiHandler.post(runnable);
        }
    }
}
