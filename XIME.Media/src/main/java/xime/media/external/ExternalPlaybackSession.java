package xime.media.external;

import xime.media.PlaybackSession;
import xime.media.PlaybackSessionListener;
import xime.media.PlaybackSnapshot;

public class ExternalPlaybackSession implements PlaybackSession {
    private final PlaybackCaptureBridge captureBridge;
    private PlaybackSessionListener listener;

    ExternalPlaybackSession(PlaybackCaptureBridge bridge) {
        this.captureBridge = bridge;
    }

    @Override
    public void attach() {
        if (listener != null) {
            listener.onSessionAvailabilityChanged(true);
        }
        // Link up capture bridge callbacks to the listener here
    }

    @Override
    public void detach() {
        if (listener != null) {
            listener.onSessionAvailabilityChanged(false);
        }
        captureBridge.release();
    }

    @Override
    public PlaybackSnapshot getSnapshot() {
        return captureBridge.getCurrentSnapshot();
    }

    @Override
    public void addListener(PlaybackSessionListener listener) {
        this.listener = listener;
    }

    @Override
    public void removeListener(PlaybackSessionListener listener) {
        if (this.listener == listener) {
            this.listener = null;
        }
    }

    @Override
    public void play() {
        captureBridge.play();
    }

    @Override
    public void pause() {
        captureBridge.pause();
    }

    @Override
    public void next() {
        captureBridge.next();
    }

    @Override
    public void previous() {
        captureBridge.previous();
    }

    @Override
    public void seekTo(long positionMs) {
        captureBridge.seekTo(positionMs);
    }

    @Override
    public boolean supportsSeek() {
        return captureBridge.supportsSeek();
    }

    @Override
    public boolean supportsNextPrevious() {
        return captureBridge.supportsNextPrevious();
    }
}
