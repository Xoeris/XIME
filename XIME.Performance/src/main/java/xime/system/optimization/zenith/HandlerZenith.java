package xime.system.optimization.zenith;

import android.os.Handler;
import android.os.Looper;

public final class HandlerZenith {
    private static final Handler mainHandler = new Handler(Looper.getMainLooper());

    private HandlerZenith() {}

    public static void post(Runnable r) {
        mainHandler.post(r);
    }

    public static void postDelayed(Runnable r, long delayMillis) {
        mainHandler.postDelayed(r, delayMillis);
    }

    public static void removeCallbacks(Runnable r) {
        mainHandler.removeCallbacks(r);
    }
}
