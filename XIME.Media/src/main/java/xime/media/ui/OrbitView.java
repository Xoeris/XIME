package xime.media.ui;

import android.content.Context;
import android.util.AttributeSet;
import android.view.View;

import androidx.recyclerview.widget.ConcatAdapter;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import xime.ui.layout.CycleLayout;
import xime.ui.view.OrbitScroller;

import java.util.List;

import xime.media.Metadata;
import xime.media.ui.MediaOrbitAdapter;
import xime.ui.adapter.SingleAdapter;

public class OrbitView extends CycleLayout {
    private ConcatAdapter concatAdapter;
    private SingleAdapter footerAdapter;
    private SingleAdapter headerAdapter;
    private MediaOrbitAdapter mediaAdapter;
    private OrbitScroller scrollLine;

    public OrbitView(Context context) {
        super(context);
        init(context);
    }

    public OrbitView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init(context);
    }

    public OrbitView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init(context);
    }

    private void init(Context context) {
        setClipToPadding(false);
        setOverScrollMode(View.OVER_SCROLL_NEVER);
        LinearLayoutManager lm = new LinearLayoutManager(context);
        setLayoutManager(lm);
        
        if (lm.getOrientation() == RecyclerView.VERTICAL) {
            this.scrollLine = new OrbitScroller(4, 4);
            this.scrollLine.attachTo(this);
        }
        
        this.concatAdapter = new ConcatAdapter();
        this.mediaAdapter = new MediaOrbitAdapter(null);
        this.concatAdapter.addAdapter(this.mediaAdapter);
        setAdapter(this.concatAdapter);
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
        int index = this.concatAdapter.getAdapters().indexOf(this.mediaAdapter);
        if (index >= 0) {
            this.concatAdapter.removeAdapter(this.mediaAdapter);
        }
        List<Metadata> currentItems = this.mediaAdapter.getItems();
        this.mediaAdapter = new MediaOrbitAdapter(listener);
        this.mediaAdapter.setItems(currentItems);
        if (index >= 0) {
            this.concatAdapter.addAdapter(index, this.mediaAdapter);
        } else {
            this.concatAdapter.addAdapter(this.mediaAdapter);
        }
    }

    public void applyTheme(int primaryColor, int onPrimaryColor, int surfaceColor, int onSurfaceColor) {
        this.mediaAdapter.applyTheme(primaryColor, onPrimaryColor, surfaceColor, onSurfaceColor);
    }
}

