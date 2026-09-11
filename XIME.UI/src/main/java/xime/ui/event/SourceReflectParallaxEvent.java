package xime.ui.event;

import androidx.annotation.NonNull;

import xime.ui.view.View;
import xime.ui.utils.Views;

public interface SourceReflectParallaxEvent extends ReflectParallaxEvent {

    void onNestedScroll(@NonNull View target, int dxConsumed, int dyConsumed, int dxUnconsumed,
                        int dyUnconsumed, @Views.NestedScrollType int type, @NonNull int[] consumed);
}
