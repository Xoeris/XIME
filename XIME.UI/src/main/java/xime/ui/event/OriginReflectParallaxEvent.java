package xime.ui.event;

import androidx.annotation.NonNull;
import xime.ui.view.View;
import xime.ui.utils.Views.ScrollAxis;

public interface OriginReflectParallaxEvent {

    boolean onStartNestedScroll(@NonNull View child, @NonNull View target, @ScrollAxis int axes);

    void onNestedScrollAccepted(@NonNull View child, @NonNull View target, @ScrollAxis int axes);

    void onStopNestedScroll(@NonNull View target);

    void onNestedScroll(@NonNull View target, int dxConsumed, int dyConsumed,
                        int dxUnconsumed, int dyUnconsumed);

    void onNestedPreScroll(@NonNull View target, int dx, int dy, @NonNull int[] consumed);

    boolean onNestedFling(@NonNull View target, float velocityX, float velocityY, boolean consumed);

    boolean onNestedPreFling(@NonNull View target, float velocityX, float velocityY);

    @ScrollAxis
    int getNestedScrollAxes();
}
