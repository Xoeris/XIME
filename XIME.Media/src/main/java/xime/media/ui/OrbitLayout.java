package xime.media.ui;

import android.content.Context;
import android.content.res.TypedArray;
import android.util.AttributeSet;
import android.view.View;

import androidx.recyclerview.widget.ConcatAdapter;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.List;
import java.util.Set;

import xime.ui.layout.CycleLayout;
import xime.ui.view.OrbitScroller;
import xime.media.Metadata;
import xime.media.ui.MediaOrbitAdapter;
import xime.ui.adapter.SingleAdapter;

public class OrbitLayout extends CycleLayout {
    private ConcatAdapter concatAdapter;
    private SingleAdapter footerAdapter;
    private SingleAdapter headerAdapter;
    private MediaOrbitAdapter mediaAdapter;
    private OrbitScroller scrollLine;

    public OrbitLayout(Context context) {
        super(context);
        init(context, null);
    }

    public OrbitLayout(Context context, AttributeSet attrs) {
        super(context, attrs);
        init(context, attrs);
    }

    public OrbitLayout(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init(context, attrs);
    }

    private void init(Context context, AttributeSet attrs) {
        setClipToPadding(false);
        setOverScrollMode(View.OVER_SCROLL_NEVER);
        
        int orientation = RecyclerView.VERTICAL;
        if (attrs != null) {
            int[] attrsArray = new int[] { android.R.attr.orientation };
            TypedArray a = context.obtainStyledAttributes(attrs, attrsArray);
            orientation = a.getInt(0, RecyclerView.VERTICAL);
            a.recycle();
        }

        LinearLayoutManager lm = new LinearLayoutManager(context, orientation, false);
        lm.setItemPrefetchEnabled(false);
        setLayoutManager(lm);
        setItemViewCacheSize(2);
        setItemAnimator(null);
        
        if (orientation == RecyclerView.VERTICAL) {
            this.scrollLine = new OrbitScroller(4, 4);
            this.scrollLine.attachTo(this);
        }
        
        ConcatAdapter.Config config = new ConcatAdapter.Config.Builder()
                .setIsolateViewTypes(true)
                .setStableIdMode(ConcatAdapter.Config.StableIdMode.ISOLATED_STABLE_IDS)
                .build();
        this.concatAdapter = new ConcatAdapter(config);
        
        this.mediaAdapter = new MediaOrbitAdapter(null);
        this.concatAdapter.addAdapter(this.mediaAdapter);
        setAdapter(this.concatAdapter);
    }

    public OrbitScroller getScrollLine() {
        return this.scrollLine;
    }

    public void setScrollOffsets(int topDp, int bottomDp) {
        if (this.scrollLine != null) {
            this.scrollLine.setOffsets(topDp, bottomDp);
        }
    }

    public void setHeaderView(View headerView) {
        if (this.headerAdapter != null) {
            this.concatAdapter.removeAdapter(this.headerAdapter);
        }
        if (headerView != null) {
            this.headerAdapter = new SingleAdapter(headerView);
            this.concatAdapter.addAdapter(0, this.headerAdapter);
        }
    }

    public View getHeaderView() {
        return this.headerAdapter != null ? this.headerAdapter.getView() : null;
    }

    public void setFooterView(View footerView) {
        if (this.footerAdapter != null) {
            this.concatAdapter.removeAdapter(this.footerAdapter);
        }
        if (footerView != null) {
            this.footerAdapter = new SingleAdapter(footerView);
            this.concatAdapter.addAdapter(this.concatAdapter.getAdapters().size(), this.footerAdapter);
        }
    }

    public void setMediaItems(List<Metadata> items) {
        this.mediaAdapter.setItems(items);
    }

    public void setOnItemClickListener(MediaOrbitAdapter.OnItemClickListener listener) {
        this.mediaAdapter.setListener(listener);
    }

    public void setPlayingId(String playingId) {
        this.mediaAdapter.setPlayingId(playingId);
    }

    public void applyTheme(int primaryColor, int onPrimaryColor, int surfaceColor, int onSurfaceColor) {
        this.mediaAdapter.applyTheme(primaryColor, onPrimaryColor, surfaceColor, onSurfaceColor);
    }

    public void setSelectionMode(boolean mode) {
        this.mediaAdapter.setSelectionMode(mode);
    }

    public boolean isSelectionMode() {
        return this.mediaAdapter.isSelectionMode();
    }

    public Set<String> getSelectedIds() {
        return this.mediaAdapter.getSelectedIds();
    }

    public void setSelectionListener(MediaOrbitAdapter.SelectionListener listener) {
        this.mediaAdapter.setSelectionListener(listener);
    }

    public void toggleSelection(String id) {
        this.mediaAdapter.toggleSelection(id);
    }

    public void selectAll() {
        this.mediaAdapter.selectAll();
    }

    public void clearSelection() {
        this.mediaAdapter.clearSelection();
    }
}

