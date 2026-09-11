package xime.media.database;

import android.content.Context;

import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;

import xime.media.dao.DeletedPlaylistDao;
import xime.media.dao.PlaylistDao;
import xime.media.dao.TrackDao;
import xime.media.entity.DeletedPlaylistEntity;
import xime.media.entity.PlaylistEntity;
import xime.media.entity.PlaylistTrackEntity;
import xime.media.entity.TrackEntity;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Database(
    entities = {TrackEntity.class, PlaylistEntity.class, PlaylistTrackEntity.class, DeletedPlaylistEntity.class},
    version = 11,
    exportSchema = false
)
public abstract class MusicDatabase extends RoomDatabase {
    
    private static volatile MusicDatabase INSTANCE;
    private static final int NUMBER_OF_THREADS = 4;
    public static final ExecutorService databaseWriteExecutor =
            Executors.newFixedThreadPool(NUMBER_OF_THREADS);
    
    public abstract TrackDao trackDao();
    public abstract PlaylistDao playlistDao();
    public abstract DeletedPlaylistDao deletedPlaylistDao();
    
    public static MusicDatabase getDatabase(final Context context) {
        if (INSTANCE == null) {
            synchronized (MusicDatabase.class) {
                if (INSTANCE == null) {
                    INSTANCE = Room.databaseBuilder(
                        context.getApplicationContext(),
                        MusicDatabase.class,
                        "music_database"
                    ).fallbackToDestructiveMigration()
                    .build();
                }
            }
        }
        return INSTANCE;
    }
}

