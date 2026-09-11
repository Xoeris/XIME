package xime.media;

public interface PlaybackSession {
    /** Attaches the session to begin observing/controlling playback. */
    void attach();
    
    /** Detaches the session and releases resources. */
    void detach();

    /** Gets the current snapshot of playback state and metadata. */
    PlaybackSnapshot getSnapshot();

    /** Registers a listener for state and metadata changes. */
    void addListener(PlaybackSessionListener listener);

    /** Unregisters a listener. */
    void removeListener(PlaybackSessionListener listener);

    /** Commands the source to play. */
    void play();

    /** Commands the source to pause. */
    void pause();

    /** Commands the source to skip to the next track. */
    void next();

    /** Commands the source to skip to the previous track. */
    void previous();

    /** Commands the source to seek to a specific position. */
    void seekTo(long positionMs);

    /** Returns true if the session supports seeking. */
    boolean supportsSeek();

    /** Returns true if the session supports skipping to next/previous tracks. */
    boolean supportsNextPrevious();
}

