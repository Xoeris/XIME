package xime.media;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class Queue {
    public enum RepeatMode { NONE, ONE, ALL }
    
    private final List<Metadata> originalList = new ArrayList<>();
    private final List<Metadata> activeQueue = new ArrayList<>();
    private int currentIndex = -1;
    private boolean shuffleEnabled = false;
    private RepeatMode repeatMode = RepeatMode.NONE;

    private String playlistName = "Queue";
    
    private final Signal<Void> queueSignal = new Signal<>();

    public Signal<Void> getQueueSignal() {
        return queueSignal;
    }

    public void setPlaylist(List<Metadata> list) {
        setPlaylist(list, "Queue");
    }

    public void setPlaylist(List<Metadata> list, String name) {
        this.playlistName = (name != null ? name : "Queue");
        originalList.clear();
        if (list != null) originalList.addAll(list);
        updateActiveQueue();
        queueSignal.emit(null);
    }

    public void setPlaylist(Playlist playlist) {
        if (playlist != null) {
            setPlaylist(playlist.getItems(), playlist.getName());
        }
    }

    public String getPlaylistName() {
        return playlistName;
    }

    public int getCurrentIndex() {
        return currentIndex;
    }

    public void setShuffle(boolean enabled) {
        this.shuffleEnabled = enabled;
        updateActiveQueue();
        queueSignal.emit(null);
    }

    public void setShuffleEnabled(boolean enabled) {
        setShuffle(enabled);
    }

    // Silent setters for per-context switches – avoid reshuffling old list before playlist replacement
    public void setShuffleEnabledSilently(boolean enabled) {
        this.shuffleEnabled = enabled;
    }

    public void setRepeatModeSilently(RepeatMode mode) {
        this.repeatMode = mode;
    }

    public boolean isShuffleEnabled() { return shuffleEnabled; }

    public void setRepeatMode(RepeatMode mode) {
        this.repeatMode = mode;
        queueSignal.emit(null);
    }

    public RepeatMode getRepeatMode() {
        return repeatMode;
    }

    public void jumpTo(int index) {
        if (index >= 0 && index < activeQueue.size()) {
            this.currentIndex = index;
        }
    }

    private void updateActiveQueue() {
        Metadata currentItem = getCurrentItem();
        activeQueue.clear();
        activeQueue.addAll(originalList);
        if (shuffleEnabled) {
            Collections.shuffle(activeQueue);
        }
        if (currentItem != null) {
            currentIndex = activeQueue.indexOf(currentItem);
        } else {
            currentIndex = activeQueue.isEmpty() ? -1 : 0;
        }
    }

    public Metadata peekNext() {
        if (activeQueue.isEmpty()) return null;
        if (repeatMode == RepeatMode.ONE) return getCurrentItem();
        
        int nextIndex = currentIndex + 1;
        if (nextIndex >= activeQueue.size()) {
            if (repeatMode == RepeatMode.ALL) {
                nextIndex = 0;
            } else {
                return null;
            }
        }
        return activeQueue.get(nextIndex);
    }

    public Metadata next() {
        if (activeQueue.isEmpty()) return null;
        if (repeatMode == RepeatMode.ONE) return getCurrentItem();
        
        currentIndex++;
        if (currentIndex >= activeQueue.size()) {
            if (repeatMode == RepeatMode.ALL) {
                currentIndex = 0;
            } else {
                currentIndex = activeQueue.size() - 1;
                return null;
            }
        }
        return activeQueue.get(currentIndex);
    }

    public Metadata previous() {
        if (activeQueue.isEmpty()) return null;
        
        currentIndex--;
        if (currentIndex < 0) {
            if (repeatMode == RepeatMode.ALL) {
                currentIndex = activeQueue.size() - 1;
            } else {
                currentIndex = 0;
            }
        }
        return activeQueue.get(currentIndex);
    }

    public Metadata getCurrentItem() {
        if (currentIndex >= 0 && currentIndex < activeQueue.size()) {
            return activeQueue.get(currentIndex);
        }
        return null;
    }

    public List<Metadata> getActiveQueue() {
        return Collections.unmodifiableList(activeQueue);
    }

    public void remove(int position) {
        if (position >= 0 && position < activeQueue.size()) {
            activeQueue.remove(position);
            if (position < currentIndex) {
                currentIndex--;
            } else if (position == currentIndex) {
                if (currentIndex >= activeQueue.size()) currentIndex = activeQueue.size() - 1;
            }
            queueSignal.emit(null);
        }
    }

    public void move(int from, int to) {
        if (from >= 0 && from < activeQueue.size() && to >= 0 && to < activeQueue.size()) {
            Metadata item = activeQueue.remove(from);
            activeQueue.add(to, item);
            
            // Adjust current index
            if (currentIndex == from) {
                currentIndex = to;
            } else if (from < currentIndex && to >= currentIndex) {
                currentIndex--;
            } else if (from > currentIndex && to <= currentIndex) {
                currentIndex++;
            }
            queueSignal.emit(null);
        }
    }
}

