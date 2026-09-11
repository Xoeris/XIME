package xime.system.optimization.zenith;

import java.util.concurrent.Future;

public final class AsynZenith {
    private AsynZenith() {}

    public static Future<?> submit(Runnable runnable) {
        return java.util.concurrent.Executors.newSingleThreadExecutor().submit(runnable);
    }
}
