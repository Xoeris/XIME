package xime.media;

public interface PlaybackSessionListener {
    /** Invoked when the playback state or metadata changes. */
    void onSnapshotUpdated(PlaybackSnapshot snapshot);

    /** Invoked when the session becomes active or inactive. */
    void onSessionAvailabilityChanged(boolean available);
}

