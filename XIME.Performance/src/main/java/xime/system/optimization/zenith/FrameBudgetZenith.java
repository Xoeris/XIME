package xime.system.optimization.zenith;

public final class FrameBudgetZenith {
    private static final long FRAME_BUDGET_NS = 12_000_000; // 12ms target budget
    private static long frameStartTime = 0;

    private FrameBudgetZenith() {}

    public static void markFrameStart() {
        frameStartTime = System.nanoTime();
    }

    public static boolean isOverBudget() {
        if (frameStartTime == 0) return false;
        return (System.nanoTime() - frameStartTime) > FRAME_BUDGET_NS;
    }
}
