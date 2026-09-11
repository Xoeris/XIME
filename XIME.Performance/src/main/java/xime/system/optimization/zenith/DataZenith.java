package xime.system.optimization.zenith;

import xime.media.entity.TrackEntity;
import xime.media.Metadata;

public final class DataZenith {
    private DataZenith() {}

    public static Metadata convertTrack(TrackEntity track) {
        if (track == null) return null;
        Metadata.Builder builder = new Metadata.Builder()
            .setTitle(track.getTitle())
            .setArtist(track.getArtist())
            .setAlbumName(track.getAlbum())
            .setPath(track.getPath())
            .setDurationMs(track.getDuration())
            .setLyrics(track.getLyrics());
        
        String art = track.getAlbumArt() != null ? track.getAlbumArt() : track.getTrackArt();
        if (art != null) {
            builder.setCoverImage(art);
        }
        return builder.build();
    }
}
