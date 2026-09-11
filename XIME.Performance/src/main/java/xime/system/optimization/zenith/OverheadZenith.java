package xime.system.optimization.zenith;

import android.util.Log;

public final class OverheadZenith {
    private OverheadZenith() {}

    public static void measure(String label, Runnable runnable) {
        long start = System.nanoTime();
        runnable.run();
        long end = System.nanoTime();
        double durationMs = (end - start) / 1_000_000.0;
        if (durationMs > 2.0) {
            Log.d("OverheadZenith", "[" + label + "] execution overhead: " + durationMs + " ms");
        }
    }
}
