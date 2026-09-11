package xime.ui.event;

import androidx.annotation.NonNull;
import xime.ui.view.View;
import xime.ui.utils.Views.NestedScrollType;
import xime.ui.utils.Views.ScrollAxis;

public interface ReflectParallaxEvent extends OriginReflectParallaxEvent {

    boolean onStartNestedScroll(@NonNull View child, @NonNull View target, @ScrollAxis int axes,
                                @NestedScrollType int type);

    void onNestedScrollAccepted(@NonNull View child, @NonNull View target, @ScrollAxis int axes,
                                @NestedScrollType int type);

    void onStopNestedScroll(@NonNull View target, @NestedScrollType int type);

    void onNestedScroll(@NonNull View target, int dxConsumed, int dyConsumed,
                        int dxUnconsumed, int dyUnconsumed, @NestedScrollType int type);

    void onNestedPreScroll(@NonNull View target, int dx, int dy, @NonNull int[] consumed,
                           @NestedScrollType int type);
}
