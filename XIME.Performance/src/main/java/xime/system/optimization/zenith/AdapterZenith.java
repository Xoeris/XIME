package xime.system.optimization.zenith;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import xime.imaging.Prism;
import androidx.recyclerview.widget.ConcatAdapter;
import xime.media.ui.MediaOrbitAdapter;
import java.util.List;
import java.util.Objects;

public final class AdapterZenith {
    private AdapterZenith() {}

    public static <T> boolean areListsIdentical(List<T> list1, List<T> list2) {
        if (list1 == list2) return true;
        if (list1 == null || list2 == null) return false;
        if (list1.size() != list2.size()) return false;
        for (int i = 0; i < list1.size(); i++) {
            if (!Objects.equals(list1.get(i), list2.get(i))) return false;
        }
        return true;
    }

    public static void setupSmoothScroll(RecyclerView recyclerView) {
        if (recyclerView == null) return;
        recyclerView.addOnScrollListener(new RecyclerView.OnScrollListener() {
            private void updateScrollingState(boolean isScrolling) {
                RecyclerView.Adapter<?> adapter = recyclerView.getAdapter();
                if (adapter instanceof MediaOrbitAdapter) {
                    ((MediaOrbitAdapter) adapter).setScrolling(isScrolling);
                } else if (adapter instanceof ConcatAdapter) {
                    for (RecyclerView.Adapter<?> subAdapter : ((ConcatAdapter) adapter).getAdapters()) {
                        if (subAdapter instanceof MediaOrbitAdapter) {
                            ((MediaOrbitAdapter) subAdapter).setScrolling(isScrolling);
                        }
                    }
                }
            }

            @Override
            public void onScrollStateChanged(@NonNull RecyclerView recyclerView, int newState) {
                super.onScrollStateChanged(recyclerView, newState);
                try {
                    boolean isScrolling = (newState == RecyclerView.SCROLL_STATE_DRAGGING || newState == RecyclerView.SCROLL_STATE_SETTLING);
                    updateScrollingState(isScrolling);

                    if (isScrolling) {
                        Prism.with(recyclerView.getContext()).pauseRequests();
                    } else if (newState == RecyclerView.SCROLL_STATE_IDLE) {
                        Prism.with(recyclerView.getContext()).resumeRequests();
                    }
                } catch (Exception ignored) {}
            }
        });
    }
}
