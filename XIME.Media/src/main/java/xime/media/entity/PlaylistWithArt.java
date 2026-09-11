package xime.media.entity;

import androidx.room.Embedded;

/**
 * POJO for Playlist with fallback art from first track.
 */
public class PlaylistWithArt {
    @Embedded
    private PlaylistEntity playlist;
    
    private String firstTrackArt;

    public PlaylistEntity getPlaylist() {
        return playlist;
    }

    public void setPlaylist(PlaylistEntity playlist) {
        this.playlist = playlist;
    }

    public String getFirstTrackArt() {
        return firstTrackArt;
    }

    public void setFirstTrackArt(String firstTrackArt) {
        this.firstTrackArt = firstTrackArt;
    }
}

