package xime.media;

import android.graphics.Bitmap;

public final class PlaybackSnapshot {
    public final boolean isPlaying;
    public final long positionMs;
    public final long durationMs;
    public final float playbackSpeed;
    
    public final String title;
    public final String artist;
    public final String album;
    public final Bitmap artwork;
    public final String sourceApp; // null for Internal

    private PlaybackSnapshot(Builder builder) {
        this.isPlaying = builder.isPlaying;
        this.positionMs = builder.positionMs;
        this.durationMs = builder.durationMs;
        this.playbackSpeed = builder.playbackSpeed;
        this.title = builder.title;
        this.artist = builder.artist;
        this.album = builder.album;
        this.artwork = builder.artwork;
        this.sourceApp = builder.sourceApp;
    }

    public static class Builder {
        private boolean isPlaying;
        private long positionMs;
        private long durationMs;
        private float playbackSpeed = 1.0f;
        
        private String title;
        private String artist;
        private String album;
        private Bitmap artwork;
        private String sourceApp;

        /** Default constructor for the Builder. */
        public Builder() {}

        public Builder setPlaying(boolean playing) { this.isPlaying = playing; return this; }
        public Builder setPositionMs(long positionMs) { this.positionMs = positionMs; return this; }
        public Builder setDurationMs(long durationMs) { this.durationMs = durationMs; return this; }
        public Builder setPlaybackSpeed(float speed) { this.playbackSpeed = speed; return this; }
        
        public Builder setTitle(String title) { this.title = title; return this; }
        public Builder setArtist(String artist) { this.artist = artist; return this; }
        public Builder setAlbum(String album) { this.album = album; return this; }
        public Builder setArtwork(Bitmap artwork) { this.artwork = artwork; return this; }
        public Builder setSourceApp(String sourceApp) { this.sourceApp = sourceApp; return this; }

        /** Builds the PlaybackSnapshot. */
        public PlaybackSnapshot build() {
            return new PlaybackSnapshot(this);
        }
    }
}

