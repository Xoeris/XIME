package xime.media.music.manager;

import android.content.ContentUris;
import android.content.Context;
import android.net.Uri;
import android.provider.MediaStore;

import java.io.File;
import java.util.List;

import xime.media.entity.TrackEntity;
import xime.media.music.MusicEngine;
import xime.media.Metadata;
import xime.media.Playlist;

public class MusicManager {
    private static final String TAG = "MusicManager";
    private static MusicManager instance;
    private final MusicEngine engine;
    private final Context context;

    private MusicManager(Context context) {
        this.context = context.getApplicationContext();
        this.engine = MusicEngine.getInstance(this.context);
    }

    public static synchronized MusicManager getInstance(Context context) {
        if (instance == null) {
            instance = new MusicManager(context);
        }
        return instance;
    }

    public void play(Metadata metadata) {
        engine.play(metadata);
    }

    public void play(TrackEntity trackEntity) {
        if (trackEntity == null) return;
        play(convertToMetadata(trackEntity));
    }

    public void play(List<TrackEntity> trackEntityEntities, int index) {
        play(trackEntityEntities, index, "Queue", -1L);
    }

    public void play(List<TrackEntity> trackEntityEntities, int index, String playlistName, long playlistId) {
        if (trackEntityEntities == null || trackEntityEntities.isEmpty() || index < 0 || index >= trackEntityEntities.size()) return;

        Playlist playlist = new Playlist(playlistName != null ? playlistName : "Queue");
        for (TrackEntity t : trackEntityEntities) {
            playlist.addItem(convertToMetadata(t));
        }

        engine.setPlaylist(playlist, playlistId);
        engine.getQueue().jumpTo(index);
        engine.play(playlist.getItems().get(index));
    }

    public MusicEngine getEngine() {
        return engine;
    }

    public Metadata convertToMetadata(TrackEntity trackEntity) {
        if (trackEntity == null) return null;

        Uri mediaUri;
        if (trackEntity.getId() > 100) {
            mediaUri = ContentUris.withAppendedId(
                    MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, trackEntity.getId());
        } else {
            String path = trackEntity.getPath();
            mediaUri = path != null ? Uri.fromFile(new File(path)) : null;
        }

        // Framework should decide which art to use or provide both
        // For now, let's use trackEntity art if available, fallback to album art
        String artPath = trackEntity.getTrackArt() != null ? trackEntity.getTrackArt() : trackEntity.getAlbumArt();

        return new Metadata.Builder()
                .setTitle(trackEntity.getTitle())
                .setArtist(trackEntity.getArtist())
                .setAlbum(trackEntity.getAlbum())
                .setDuration(trackEntity.getDuration())
                .setPath(trackEntity.getPath())
                .setMediaUri(mediaUri)
                .setArtUri(artPath != null ? Uri.parse(artPath) : null)
                .setLyrics(trackEntity.getLyrics())
                .build();
    }

    public void playNext() { engine.playNext(); }
    public void playPrevious() { engine.playPrevious(); }
    public void togglePlayback() { engine.togglePlayback(); }
    public void toggleShuffle() { engine.toggleShuffle(); }
    public void cycleRepeatMode() { engine.cycleRepeatMode(); }
}

