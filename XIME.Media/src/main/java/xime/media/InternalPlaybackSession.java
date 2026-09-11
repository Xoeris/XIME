package xime.media;

import java.util.concurrent.CopyOnWriteArrayList;
import java.util.List;
import xime.media.music.Music;
import xime.media.music.MusicEngine;
import xime.media.Queue;

/**
 * InternalPlaybackSession wraps the XIME.Media internal Music engine
 * behind the PlaybackSession interface. It observes MusicEngine's live signals and
 * pushes accurate PlaybackSnapshot events to registered listeners.
 * This is a pure adapter, no internal playback logic is altered.
 */
public class InternalPlaybackSession implements PlaybackSession {

    private final Music music;
    private final MusicEngine engine;
    private final List<PlaybackSessionListener> listeners = new CopyOnWriteArrayList<>();

    private final Signal.Observer<Music.PlaybackState> stateObserver = state -> notifySnapshot();
    private final Signal.Observer<Music.Progress> progressObserver = progress -> notifySnapshot();
    private final Signal.Observer<Metadata> metadataObserver = metadata -> notifySnapshot();

    /** Constructs an InternalPlaybackSession from the live Music engine object. */
    public InternalPlaybackSession(Music music) {
        this.music = music;
        this.engine = null;
    }

    /** Constructs an InternalPlaybackSession from MusicEngine (preferred). */
    public InternalPlaybackSession(MusicEngine engine) {
        this.engine = engine;
        this.music = engine != null ? engine.getMusic() : null;
    }

    @Override
    public void attach() {
        if (music != null) {
            music.getStateSignal().observe(stateObserver);
            music.getProgressSignal().observe(progressObserver);
            music.getMetadataSignal().observe(metadataObserver);
        }
        notifyAvailability(true);
        notifySnapshot();
    }

    @Override
    public void detach() {
        if (music != null) {
            music.getStateSignal().removeObserver(stateObserver);
            music.getProgressSignal().removeObserver(progressObserver);
            music.getMetadataSignal().removeObserver(metadataObserver);
        }
        notifyAvailability(false);
    }

    @Override
    public PlaybackSnapshot getSnapshot() {
        if (music == null) return new PlaybackSnapshot.Builder().build();
        Music.Progress progress = music.getProgressSignal().getLastEvent();
        Metadata meta = music.getCurrentMetadata();
        Music.PlaybackState state = music.getCurrentState();
        PlaybackSnapshot.Builder builder = new PlaybackSnapshot.Builder()
                .setPlaying(state == Music.PlaybackState.PLAYING)
                .setPositionMs(progress != null ? progress.position : 0)
                .setDurationMs(progress != null ? progress.duration : 0)
                .setPlaybackSpeed(1.0f);
        if (meta != null) {
            builder.setTitle(meta.getTitle())
                   .setArtist(meta.getArtist())
                   .setAlbum(meta.getAlbum());
        }
        return builder.build();
    }

    @Override
    public void addListener(PlaybackSessionListener listener) {
        if (listener != null && !listeners.contains(listener)) {
            listeners.add(listener);
        }
    }

    @Override
    public void removeListener(PlaybackSessionListener listener) {
        listeners.remove(listener);
    }

    @Override
    public void play() {
        if (music != null) music.play();
    }

    @Override
    public void pause() {
        if (music != null) music.pause();
    }

    @Override
    public void next() {
        if (engine != null) {
            engine.playNext();
        } else if (music != null) {
            Metadata nextItem = music.getQueue().next();
            if (nextItem != null) music.play(nextItem);
        }
    }

    @Override
    public void previous() {
        if (engine != null) {
            engine.playPrevious();
        } else if (music != null) {
            Metadata prevItem = music.getQueue().previous();
            if (prevItem != null) music.play(prevItem);
        }
    }

    @Override
    public void seekTo(long positionMs) {
        if (music != null) music.seekTo((int) positionMs);
    }

    @Override
    public boolean supportsSeek() {
        return true;
    }

    @Override
    public boolean supportsNextPrevious() {
        return true;
    }

    private void notifySnapshot() {
        if (listeners.isEmpty()) return;
        PlaybackSnapshot snap = getSnapshot();
        for (PlaybackSessionListener l : listeners) {
            l.onSnapshotUpdated(snap);
        }
    }

    private void notifyAvailability(boolean available) {
        for (PlaybackSessionListener l : listeners) {
            l.onSessionAvailabilityChanged(available);
        }
    }
}

