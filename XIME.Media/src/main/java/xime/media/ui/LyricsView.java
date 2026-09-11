package xime.media.ui;

import android.content.Context;
import android.graphics.Typeface;
import android.util.AttributeSet;
import android.graphics.RenderEffect;
import android.graphics.Shader;
import android.os.Build;
import android.view.Gravity;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;

import xime.ui.layout.CycleLayout;
import xime.ui.view.TextView;
import xime.media.music.lyrics.Lyrics;

import android.text.style.StyleSpan;
import android.text.Spannable;
import android.text.SpannableStringBuilder;
import android.text.style.ForegroundColorSpan;
import androidx.recyclerview.widget.LinearSmoothScroller;

public class LyricsView extends CycleLayout {

    private int activeLineIndex = -1;
    private final int inactiveColor = 0x60FFFFFF;
    private final int highlightColor = 0xFFFFFFFF;
    private final int futureColor = 0x80FFFFFF;
    private int primaryColor = 0xFF00FFFF;
    private LyricsAdapter adapter;

    public interface OnActiveLineChangedListener {
        void onActiveLineChanged(int position, int top, int height);
    }
    private OnActiveLineChangedListener activeLineListener;

    public void setOnActiveLineChangedListener(OnActiveLineChangedListener listener) {
        this.activeLineListener = listener;
    }

    public LyricsView(@NonNull Context context) {
        super(context);
        init(context);
    }

    public LyricsView(@NonNull Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init(context);
    }

    public LyricsView(@NonNull Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init(context);
    }

    private void init(Context context) {
        setLayoutManager(new LinearLayoutManager(context));
        this.adapter = new LyricsAdapter();
        setAdapter(this.adapter);
        setClipToPadding(false);
        setOverScrollMode(OVER_SCROLL_NEVER);
        
        try {
            primaryColor = androidx.core.content.ContextCompat.getColor(context, xime.R.color.xoeris_primary);
        } catch (Exception ignored) {}
    }

    public void setLyrics(@Nullable Lyrics lyrics) {
        if (lyrics != null) {
            this.adapter.setLines(lyrics.getLines());
        } else {
            this.adapter.setLines(new ArrayList<>());
        }
        this.activeLineIndex = -1;
    }

    /**
     * Pushes an updated set of lines whose per-word timings have been refined
     * live (e.g. by AsrEngine/LyricsAligner as offline ASR recognizes more of
     * the track). Unlike setLyrics(), this preserves the current active line
     * and scroll position instead of resetting them, since it represents a
     * timing refinement of the same song rather than a new song being loaded.
     */
    public void setAlignedLines(@Nullable List<Lyrics.LyricLine> lines) {
        if (lines == null) return;
        this.adapter.setLinesPreservingPosition(lines);
    }

    public void setActiveLine(int index) {
        if (index == this.activeLineIndex || index < 0 || index >= this.adapter.getItemCount()) {
            return;
        }
        int oldIndex = this.activeLineIndex;
        this.activeLineIndex = index;
        this.adapter.resetWordIndex();
        
        if (oldIndex != -1) {
            this.adapter.notifyItemChanged(oldIndex);
        }
        this.adapter.notifyItemChanged(this.activeLineIndex);

        // Auto-scroll the list so the newly active line stays visible/centered.
        // Without this, the adapter data updates but the fragment never moves,
        // which is why lyrics only appeared to "sync" after a manual swipe
        // forced RecyclerView to re-layout.
        scrollToActiveLine(index);
        
        if (activeLineListener != null) {
            post(() -> {
                RecyclerView.ViewHolder holder = findViewHolderForAdapterPosition(index);
                if (holder != null) {
                    activeLineListener.onActiveLineChanged(index, holder.itemView.getTop(), holder.itemView.getHeight());
                }
            });
        }
    }

    private void scrollToActiveLine(int index) {
        RecyclerView.LayoutManager lm = getLayoutManager();
        if (!(lm instanceof LinearLayoutManager)) return;

        LinearSmoothScroller smoothScroller = new LinearSmoothScroller(getContext()) {
            @Override
            protected int getVerticalSnapPreference() {
                return LinearSmoothScroller.SNAP_TO_START;
            }

            @Override
            public int calculateDyToMakeVisible(android.view.View view, int snapPreference) {
                // Center the target line in the fragment instead of the default
                // top/bottom-edge snapping, so the active lyric sits mid-screen.
                final RecyclerView.LayoutManager layoutManager = getLayoutManager();
                if (layoutManager == null) {
                    return super.calculateDyToMakeVisible(view, snapPreference);
                }
                int viewTop = layoutManager.getDecoratedTop(view);
                int viewHeight = layoutManager.getDecoratedMeasuredHeight(view);
                int viewCenter = viewTop + viewHeight / 2;
                int containerCenter = (layoutManager.getHeight() - layoutManager.getPaddingTop() - layoutManager.getPaddingBottom()) / 2 + layoutManager.getPaddingTop();
                return viewCenter - containerCenter;
            }
        };
        smoothScroller.setTargetPosition(index);
        lm.startSmoothScroll(smoothScroller);
    }

    public void updateProgress(long timeMs, @Nullable Lyrics lyrics) {
        if (lyrics == null) return;
        int index = lyrics.getLineIndexForTime(timeMs);
        if (index != -1) {
            if (index != activeLineIndex) {
                setActiveLine(index);
            }
            adapter.setCurrentTime(timeMs, index);
        }
    }

    private class LyricsAdapter extends RecyclerView.Adapter<LyricViewHolder> {
        private List<Lyrics.LyricLine> lines = new ArrayList<>();
        private long currentTime = -1;
        private long lastUpdateTime = 0;

        public void setLines(List<Lyrics.LyricLine> lines) {
            this.lines = lines;
            this.lastUpdateTime = 0;
            notifyDataSetChanged();
        }

        public void setLinesPreservingPosition(List<Lyrics.LyricLine> lines) {
            this.lines = lines;
            this.lastUpdateTime = 0;
            // Refresh word-highlight payload only, no scroll/active-line reset,
            // since this is a timing refinement of the same song.
            if (activeLineIndex >= 0 && activeLineIndex < lines.size()) {
                notifyItemChanged(activeLineIndex, "WORD_SYNC");
            }
        }

        public void resetWordIndex() {
            this.lastUpdateTime = 0;
        }

        public void setCurrentTime(long time, int activeIndex) {
            if (activeIndex < 0 || activeIndex >= lines.size()) return;
            this.currentTime = time;
            
            long now = System.currentTimeMillis();
            if (now - lastUpdateTime > 50) { // ~20fps for power efficiency and stability
                lastUpdateTime = now;
                notifyItemChanged(activeIndex, "WORD_SYNC");
            }
        }

        @NonNull
        @Override
        public LyricViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            TextView textView = new TextView(parent.getContext());
            textView.setLayoutParams(new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            textView.setPadding(48, 32, 48, 32);
            textView.setGravity(Gravity.CENTER);
            textView.setTextSize(24);
            textView.setSingleLine(false);
            textView.setEllipsize(null);
            textView.setSelected(false);
            textView.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
            return new LyricViewHolder(textView);
        }

        @Override
        public void onBindViewHolder(@NonNull LyricViewHolder holder, int position) {
            onBindViewHolder(holder, position, new ArrayList<>());
        }

        @Override
        public void onBindViewHolder(@NonNull LyricViewHolder holder, int position, @NonNull List<Object> payloads) {
            Lyrics.LyricLine line = this.lines.get(position);
            
            if (payloads.contains("WORD_SYNC") && position == activeLineIndex) {
                updateWordHighlighting(holder.textView, line);
                return;
            }

            if (position == activeLineIndex) {
                updateWordHighlighting(holder.textView, line);
                holder.textView.setAlpha(1.0f);
                holder.textView.setScaleX(1.05f);
                holder.textView.setScaleY(1.05f);
                holder.textView.setTypeface(Typeface.DEFAULT_BOLD);
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    holder.textView.setRenderEffect(null);
                }
            } else {
                holder.textView.setText(line.text);
                holder.textView.setTextColor(inactiveColor);
                holder.textView.setAlpha(0.4f);
                holder.textView.setScaleX(0.95f);
                holder.textView.setScaleY(0.95f);
                holder.textView.setTypeface(Typeface.DEFAULT);
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    holder.textView.setRenderEffect(RenderEffect.createBlurEffect(8f, 8f, Shader.TileMode.CLAMP));
                }
            }
        }

        private void updateWordHighlighting(TextView textView, Lyrics.LyricLine line) {
            if (line.words.isEmpty()) {
                textView.setText(line.text);
                textView.setTextColor(currentTime >= line.time ? highlightColor : futureColor);
                return;
            }

            SpannableStringBuilder ssb = new SpannableStringBuilder(line.text);
            int len = ssb.length();
            textView.setTextColor(futureColor);
            
            for (Lyrics.LyricLine.Word word : line.words) {
                int start = Math.max(0, Math.min(word.charStart, len));
                int end = Math.max(0, Math.min(word.charEnd, len));
                if (start >= end) continue;
                
                int color;
                boolean active = false;
                
                // Use a small 50ms duration if word.duration is 0 to allow the active state to fire
                long duration = Math.max(50, word.duration);
                
                if (currentTime >= word.startTime + duration) {
                    color = highlightColor; // Past
                } else if (currentTime >= word.startTime) {
                    color = primaryColor; // Present
                    active = true;
                } else {
                    color = futureColor; // Future
                }
                
                ssb.setSpan(new ForegroundColorSpan(color), start, end, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
                if (active) {
                    ssb.setSpan(new StyleSpan(Typeface.BOLD), start, end, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
                }
            }
            textView.setText(ssb);
        }

        @Override
        public int getItemCount() {
            return this.lines.size();
        }
    }

    private static class LyricViewHolder extends RecyclerView.ViewHolder {
        TextView textView;

        LyricViewHolder(TextView itemView) {
            super(itemView);
            this.textView = itemView;
        }
    }
}
