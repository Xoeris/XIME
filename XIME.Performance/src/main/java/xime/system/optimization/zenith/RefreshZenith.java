package xime.system.optimization.zenith;

public final class RefreshZenith {
    private static final long DEFAULT_THROTTLE_MS = 300;
    private static long lastRefreshTime = 0;

    private RefreshZenith() {}

    public static boolean shouldRefresh() {
        return shouldRefresh(DEFAULT_THROTTLE_MS);
    }

    public static boolean shouldRefresh(long throttleMs) {
        long now = System.currentTimeMillis();
        if (now - lastRefreshTime > throttleMs) {
            lastRefreshTime = now;
            return true;
        }
        return false;
    }
}
