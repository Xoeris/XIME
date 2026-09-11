package xime.media.metadata.manager;

import android.content.Context;
import xime.media.database.MusicDatabase;
import xime.media.entity.TrackEntity;
import xime.media.Metadata;
import xime.media.MetadataEditor;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MetadataManager {
    private static volatile MetadataManager INSTANCE;
    private final MusicDatabase database;
    private final ExecutorService executor;

    private MetadataManager(Context context) {
        this.database = MusicDatabase.getDatabase(context);
        this.executor = Executors.newSingleThreadExecutor();
    }

    public static MetadataManager getInstance(Context context) {
        if (INSTANCE == null) {
            synchronized (MetadataManager.class) {
                if (INSTANCE == null) {
                    INSTANCE = new MetadataManager(context);
                }
            }
        }
        return INSTANCE;
    }

    public void commitEditorChanges(TrackEntity track, MetadataEditor editor) {
        if (!editor.validate().isValid) return;
        
        Metadata committedSphere = editor.commit();
        
        track.setTitle(committedSphere.getTitle());
        track.setArtist(committedSphere.getArtist());
        track.setAlbum(committedSphere.getAlbumName());
        track.setGenre(committedSphere.getGenre());
        track.setYear(committedSphere.getYear());
        track.setTrackNumber(committedSphere.getTrackNumber());
        track.setTrackCount(committedSphere.getTrackCount());
        track.setDiscNumber(committedSphere.getDiscNumber());
        track.setDiscTotal(committedSphere.getDiscTotal());
        track.setComposer(committedSphere.getComposer());
        track.setAlbumArtist(committedSphere.getAlbumArtist());
        track.setLyrics(committedSphere.getLyrics());
        track.setComment(committedSphere.getComment());
        track.setBitrate(committedSphere.getBitrate());

        executor.execute(() -> {
            database.trackDao().updateTrack(track);
        });
    }

    /**
     * Updates the metadata for a track in the database.
     */
    public void updateTrackMetadata(TrackEntity track, String title, String artist, String album, String genre, String year, String trackNumber, String discNumber, String composer) {
        track.setTitle(title);
        track.setArtist(artist);
        track.setAlbum(album);
        track.setGenre(genre);
        track.setYear(year);
        track.setTrackNumber(trackNumber);
        track.setDiscNumber(discNumber);
        track.setComposer(composer);

        executor.execute(() -> {
            database.trackDao().updateTrack(track);
            // TODO: Implementation for writing tags to file (e.g., using jaudiotagger or platform-specific MediaStore updates)
        });
    }

    /**
     * Syncs database metadata with information extracted from the file.
     */
    public void syncMetadataWithFile(Context context, TrackEntity track) {
        executor.execute(() -> {
            Metadata fileMetadata = Metadata.fromUri(context, android.net.Uri.parse(track.getPath()));
            if (fileMetadata != null) {
                track.setTitle(fileMetadata.getTitle());
                track.setArtist(fileMetadata.getArtist());
                track.setAlbum(fileMetadata.getAlbum());
                track.setGenre(fileMetadata.getGenre());
                track.setYear(fileMetadata.getYear());
                track.setTrackNumber(fileMetadata.getTrackNumber());
                track.setDiscNumber(fileMetadata.getDiscNumber());
                track.setComposer(fileMetadata.getComposer());
                track.setLyrics(fileMetadata.getLyrics());

                database.trackDao().updateTrack(track);
            }
        });
    }
}

