package xime.media.ui;

import android.content.res.ColorStateList;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.DecelerateInterpolator;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.RecyclerView;

import xime.imaging.Prism;
import xime.imaging.PrismOptions;
import xime.imaging.DiskCacheStrategy;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import xime.ui.common.IconButton;

import xime.R;
import xime.media.Metadata;
import xime.ui.layout.CardLayout;

public class MediaOrbitAdapter extends RecyclerView.Adapter<MediaOrbitAdapter.ViewHolder> {
    private static final ExecutorService diffExecutor = Executors.newSingleThreadExecutor();
    private static final PrismOptions PRISM_OPTIONS = new PrismOptions()
            .placeholder(R.drawable.xoeris_music_note)
            .error(R.drawable.xoeris_music_note)
            .diskCacheStrategy(DiskCacheStrategy.ALL)
            .preferRgb565()
            .centerCrop();

    protected List<Metadata> items = new ArrayList<>();
    private int lastAnimatedPosition = -1;
    private OnItemClickListener listener;
    private boolean isScrolling = false;
    
    private int primaryColor;
    private int surfaceColor;
    private int onSurfaceColor;
    private ColorStateList rippleColorList;
    private String playingId;
    private int playingIndex = -1;

    private boolean selectionMode = false;
    private final Set<String> selectedIds = new HashSet<>();
    private SelectionListener selectionListener;

    public interface SelectionListener {
        void onSelectionModeChanged(boolean mode);
        void onSelectionCountChanged(int count);
    }

    public void setSelectionListener(SelectionListener listener) {
        this.selectionListener = listener;
    }

    public void setSelectionMode(boolean mode) {
        if (this.selectionMode == mode) return;
        this.selectionMode = mode;
        if (!mode) selectedIds.clear();
        if (selectionListener != null) {
            selectionListener.onSelectionModeChanged(mode);
            selectionListener.onSelectionCountChanged(selectedIds.size());
        }
        notifyDataSetChanged();
    }

    public boolean isSelectionMode() {
        return selectionMode;
    }

    public void toggleSelection(String id) {
        if (selectedIds.contains(id)) {
            selectedIds.remove(id);
        } else {
            selectedIds.add(id);
        }
        if (selectionListener != null) {
            selectionListener.onSelectionCountChanged(selectedIds.size());
        }
        notifyDataSetChanged();
    }

    public void clearSelection() {
        selectedIds.clear();
        if (selectionListener != null) {
            selectionListener.onSelectionCountChanged(0);
        }
        notifyDataSetChanged();
    }

    public void selectAll() {
        for (Metadata item : items) {
            selectedIds.add(getItemIdentifier(item));
        }
        if (selectionListener != null) {
            selectionListener.onSelectionCountChanged(selectedIds.size());
        }
        notifyDataSetChanged();
    }

    public Set<String> getSelectedIds() {
        return selectedIds;
    }

    private String getItemIdentifier(Metadata item) {
        if (item.getPath() != null) return item.getPath();
        return item.getTitle() + "|" + (item.getArtist() != null ? item.getArtist() : "");
    }

    public interface OnItemClickListener {
        void onItemClick(Metadata metadata, int position);
        default void onItemMoreClick(Metadata metadata, View view) {}
        default void onItemLongClick(Metadata metadata, int position, View view) {}
    }

    public MediaOrbitAdapter(OnItemClickListener listener) {
        this.listener = listener;
        setHasStableIds(true);
    }

    public void setListener(OnItemClickListener listener) {
        this.listener = listener;
    }

    public void setScrolling(boolean scrolling) {
        this.isScrolling = scrolling;
    }

    public void setPlayingId(String playingId) {
        if (!Objects.equals(this.playingId, playingId)) {
            String oldId = this.playingId;
            this.playingId = playingId;
            
            for (int i = 0; i < items.size(); i++) {
                Metadata item = items.get(i);
                String id = item.getPath() != null ? item.getPath() : item.getTitle();
                if (Objects.equals(id, oldId) || Objects.equals(id, playingId) || i == playingIndex) {
                    notifyItemChanged(i, "PLAYING_STATE_CHANGED");
                }
            }
        }
    }

    public void setPlayingIndex(int index) {
        if (this.playingIndex != index) {
            int oldIndex = this.playingIndex;
            this.playingIndex = index;
            if (oldIndex != -1) notifyItemChanged(oldIndex, "PLAYING_STATE_CHANGED");
            if (playingIndex != -1) notifyItemChanged(playingIndex, "PLAYING_STATE_CHANGED");
        }
    }

    public void setItems(List<Metadata> newItems) {
        final List<Metadata> newList = newItems != null ? new ArrayList<>(newItems) : new ArrayList<>();
        final List<Metadata> oldList = new ArrayList<>(this.items);
        
        if (oldList.isEmpty()) {
            this.items = newList;
            notifyDataSetChanged();
            return;
        }

        diffExecutor.execute(() -> {
            DiffUtil.DiffResult result = DiffUtil.calculateDiff(new DiffUtil.Callback() {
                @Override public int getOldListSize() { return oldList.size(); }
                @Override public int getNewListSize() { return newList.size(); }
                @Override
                public boolean areItemsTheSame(int oldPos, int newPos) {
                    Metadata oldItem = oldList.get(oldPos);
                    Metadata newItem = newList.get(newPos);
                    String oldId = oldItem.getPath() != null ? oldItem.getPath() : oldItem.getTitle();
                    String newId = newItem.getPath() != null ? newItem.getPath() : newItem.getTitle();
                    return Objects.equals(oldId, newId);
                }
                @Override
                public boolean areContentsTheSame(int oldPos, int newPos) {
                    Metadata oldItem = oldList.get(oldPos);
                    Metadata newItem = newList.get(newPos);
                    return Objects.equals(oldItem.getTitle(), newItem.getTitle()) &&
                           Objects.equals(oldItem.getArtist(), newItem.getArtist());
                }
            });
            
            new Handler(Looper.getMainLooper()).post(() -> {
                this.items = newList;
                this.lastAnimatedPosition = -1;
                result.dispatchUpdatesTo(this);
            });
        });
    }

    public List<Metadata> getItems() { return items; }

    public void applyTheme(int primaryColor, int onPrimaryColor, int surfaceColor, int onSurfaceColor) {
        if (this.primaryColor == primaryColor && this.surfaceColor == surfaceColor && this.onSurfaceColor == onSurfaceColor) {
            return;
        }
        this.primaryColor = primaryColor;
        this.surfaceColor = surfaceColor;
        this.onSurfaceColor = onSurfaceColor;
        
        int alphaColor = (primaryColor & 0x00FFFFFF) | 0x15000000;
        int pressedColor = (primaryColor & 0x00FFFFFF) | 0x25000000;
        this.rippleColorList = new ColorStateList(
                new int[][]{new int[]{android.R.attr.state_pressed}, new int[]{}},
                new int[]{pressedColor, alphaColor}
        );
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.media_row_item_standard, parent, false);
        ViewHolder vh = new ViewHolder(view);
        setupViewHolderListeners(vh);
        return vh;
    }

    protected void setupViewHolderListeners(ViewHolder vh) {
        View clickableArea = vh.mediaRowContent != null ? vh.mediaRowContent : vh.itemView;
        clickableArea.setOnClickListener(v -> {
            int currentPos = vh.getBindingAdapterPosition();
            if (listener != null && currentPos != RecyclerView.NO_POSITION && currentPos < items.size()) {
                if (selectionMode) {
                    toggleSelection(getItemIdentifier(items.get(currentPos)));
                } else {
                    listener.onItemClick(items.get(currentPos), currentPos);
                }
            }
        });

        clickableArea.setOnLongClickListener(v -> {
            int currentPos = vh.getBindingAdapterPosition();
            if (listener != null && currentPos != RecyclerView.NO_POSITION && currentPos < items.size()) {
                if (!selectionMode) {
                    setSelectionMode(true);
                    toggleSelection(getItemIdentifier(items.get(currentPos)));
                    return true;
                }
                listener.onItemLongClick(items.get(currentPos), currentPos, v);
                return true;
            }
            return false;
        });

        if (vh.moreButton != null) {
            vh.moreButton.setOnClickListener(v -> {
                int currentPos = vh.getBindingAdapterPosition();
                if (listener != null && currentPos != RecyclerView.NO_POSITION && currentPos < items.size()) {
                    if (selectionMode) {
                        toggleSelection(getItemIdentifier(items.get(currentPos)));
                    } else {
                        listener.onItemMoreClick(items.get(currentPos), v);
                    }
                }
            });
        }
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder vh, int position, @NonNull List<Object> payloads) {
        if (position < 0 || position >= items.size()) {
            return;
        }
        if (!payloads.isEmpty() && (payloads.contains("PLAYING_STATE_CHANGED") || payloads.contains("PLAYING_ID_CHANGED"))) {
            updatePlayingState(vh, items.get(position), position);
        } else {
            super.onBindViewHolder(vh, position, payloads);
        }
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder vh, int position) {
        if (position < 0 || position >= items.size()) {
            return;
        }
        Metadata item = items.get(position);
        
        vh.title.setText(item.getTitle() != null ? item.getTitle() : "");
        if (vh.subtitle != null) vh.subtitle.setText(item.getArtist() != null ? item.getArtist() : "");
        
        if (vh.duration != null && item.getDurationMs() > 0) {
            long sec = item.getDurationMs() / 1000;
            vh.duration.setText(String.format(Locale.getDefault(), "%d:%02d", sec / 60, sec % 60));
            vh.duration.setVisibility(View.VISIBLE);
        } else if (vh.duration != null) {
            vh.duration.setVisibility(View.GONE);
        }

        updatePlayingState(vh, item, position);
        
        if (this.onSurfaceColor != 0) {
            vh.title.setTextColor(this.onSurfaceColor);
            if (vh.subtitle != null) vh.subtitle.setTextColor(this.onSurfaceColor);
            if (vh.duration != null) {
                vh.duration.setTextColor(this.onSurfaceColor);
                vh.duration.setAlpha(0.6f);
            }
            if (vh.extension != null) {
                vh.extension.setTextColor(primaryColor != 0 ? primaryColor : 0xFFD600);
            }
        }

        if (vh.moreButton instanceof IconButton) {
            IconButton mb = (IconButton) vh.moreButton;
            if (selectionMode) {
                mb.setVisibility(View.GONE);
            } else {
                mb.setVisibility(View.VISIBLE);
                mb.setImageResource(R.drawable.xoeris_more_vert);
                mb.setRotation(0);
            }
        }

        if (vh.checkbox instanceof IconButton) {
            IconButton cb = (IconButton) vh.checkbox;
            if (selectionMode) {
                cb.setVisibility(View.VISIBLE);
                boolean isSelected = selectedIds.contains(getItemIdentifier(item));
                cb.setImageResource(isSelected ? R.drawable.xoeris_check_box_marked : R.drawable.xoeris_check_box_blank);
            } else {
                cb.setVisibility(View.GONE);
            }
        }

        Object artSource = item.getArtUri() != null ? item.getArtUri() : item.getArtBytes();
        Prism.with(vh.itemView.getContext())
                .load(artSource)
                .apply(PRISM_OPTIONS)
                .into(vh.image);

        if (!isScrolling && position > lastAnimatedPosition) {
            vh.itemView.setAlpha(0f);
            vh.itemView.setTranslationY(50f);
            vh.itemView.animate().alpha(1f).translationY(0f).setDuration(300)
                    .setInterpolator(new DecelerateInterpolator()).start();
            lastAnimatedPosition = position;
        }
    }

    private void updatePlayingState(ViewHolder vh, Metadata item, int position) {
        String id = item.getPath() != null ? item.getPath() : item.getTitle();
        
        boolean isPlaying = false;
        if (playingIndex != -1) {
            isPlaying = (position == playingIndex);
        } else if (playingId != null) {
            isPlaying = Objects.equals(id, playingId);
        }
        
        View highlightView = vh.mediaRowContent != null ? vh.mediaRowContent : vh.mediaCard;
        if (highlightView != null) {
            if (isPlaying) {
                highlightView.setBackgroundColor(primaryColor != 0 ? (primaryColor & 0x33FFFFFF) | 0x33000000 : 0x33FFC107);
            } else {
                highlightView.setBackgroundColor(0);
            }
        }

        if (vh.mediaCard != null) {
            vh.mediaCard.setCardBackgroundColor(surfaceColor);
        }
    }

    @Override
    public int getItemCount() { return items.size(); }

    @Override
    public long getItemId(int position) {
        if (position < 0 || position >= items.size()) return RecyclerView.NO_ID;
        Metadata item = items.get(position);
        String id = item.getPath() != null ? item.getPath() : item.getTitle();
        return id != null ? id.hashCode() : RecyclerView.NO_ID;
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {
        public ImageView image;
        public TextView title;
        public TextView subtitle;
        public TextView duration;
        public TextView extension;
        public View moreButton;
        public View dragHandle;
        public View checkbox;
        public CardLayout mediaCard;
        public View mediaRowContent;

        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            image = itemView.findViewById(R.id.mediaImage);
            title = itemView.findViewById(R.id.mediaTitle);
            subtitle = itemView.findViewById(R.id.mediaSubtitle);
            duration = itemView.findViewById(R.id.mediaDuration);
            extension = itemView.findViewById(R.id.mediaExtension);
            moreButton = itemView.findViewById(R.id.mediaMoreButton);
            dragHandle = itemView.findViewById(R.id.mediaDragHandle);
            checkbox = itemView.findViewById(R.id.mediaCheckbox);
            mediaCard = (CardLayout) itemView.findViewById(R.id.mediaCard);
            mediaRowContent = itemView.findViewById(R.id.mediaRowContent);
        }
    }
}

