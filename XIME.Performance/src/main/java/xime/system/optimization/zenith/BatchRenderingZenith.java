package xime.system.optimization.zenith;

import android.view.View;

public final class BatchRenderingZenith {
    private BatchRenderingZenith() {}

    public static void runBatched(View view, Runnable action) {
        if (view == null || action == null) return;
        view.postOnAnimation(action);
    }
}
