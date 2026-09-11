package xime.system.optimization.zenith;

import android.view.View;
import android.view.ViewGroup;

public final class InvisibleCullZenith {
    private InvisibleCullZenith() {}

    public static void cullChildren(ViewGroup viewGroup) {
        if (viewGroup == null) return;
        int count = viewGroup.getChildCount();
        for (int i = 0; i < count; i++) {
            View child = viewGroup.getChildAt(i);
            if (child != null) {
                if (child.getVisibility() == View.VISIBLE && !child.isShown()) {
                    child.setVisibility(View.INVISIBLE);
                } else if (child.getVisibility() == View.INVISIBLE && child.isShown()) {
                    child.setVisibility(View.VISIBLE);
                }
            }
        }
    }
}
