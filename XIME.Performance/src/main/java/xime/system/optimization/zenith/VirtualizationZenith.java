package xime.system.optimization.zenith;

import android.graphics.Rect;
import android.view.View;

public final class VirtualizationZenith {
    private VirtualizationZenith() {}

    public static boolean isViewVisibleInParent(View child, View parent) {
        if (child == null || parent == null) return false;
        Rect parentRect = new Rect();
        parent.getHitRect(parentRect);
        Rect childRect = new Rect();
        child.getHitRect(childRect);
        return Rect.intersects(parentRect, childRect);
    }
}
