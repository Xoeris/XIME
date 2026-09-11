package xime.media.dao;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Update;
import androidx.room.Upsert;

import xime.media.entity.TrackEntity;

import java.util.List;

@Dao
public interface TrackDao {
    
    @Query("SELECT * FROM tracks ORDER BY title ASC")
    LiveData<List<TrackEntity>> getAllTracks();
    @Query("SELECT * FROM tracks ORDER BY title ASC")
    List<TrackEntity> getAllTracksSync();
    
    // Optimized pagination queries
    @Query("SELECT * FROM tracks ORDER BY title ASC LIMIT :limit OFFSET :offset")
    List<TrackEntity> getTracksPaginated(int limit, int offset);
    
    @Query("SELECT * FROM tracks ORDER BY artist ASC, album ASC, title ASC LIMIT :limit OFFSET :offset")
    List<TrackEntity> getTracksPaginatedByArtist(int limit, int offset);
    
    @Query("SELECT * FROM tracks ORDER BY dateAdded DESC LIMIT :limit OFFSET :offset")
    List<TrackEntity> getTracksPaginatedByDateAdded(int limit, int offset);
    
    @Query("SELECT * FROM tracks ORDER BY duration DESC LIMIT :limit OFFSET :offset")
    List<TrackEntity> getTracksPaginatedByDuration(int limit, int offset);
    
    // Optimized search with pagination
    @Query("SELECT * FROM tracks WHERE title LIKE '%' || :query || '%' OR artist LIKE '%' || :query || '%' OR album LIKE '%' || :query || '%' ORDER BY title ASC LIMIT :limit OFFSET :offset")
    List<TrackEntity> searchTracksPaginated(String query, int limit, int offset);
    
    @Query("SELECT * FROM tracks WHERE id = :id")
    LiveData<TrackEntity> getTrackById(long id);
    
    @Query("SELECT * FROM tracks WHERE title LIKE '%' || :query || '%' OR artist LIKE '%' || :query || '%' OR album LIKE '%' || :query || '%'")
    LiveData<List<TrackEntity>> searchTracks(String query);
    
    @Query("SELECT * FROM tracks WHERE title LIKE '%' || :query || '%' OR artist LIKE '%' || :query || '%' OR album LIKE '%' || :query || '%'")
    List<TrackEntity> searchTracksSync(String query);
    
    @Query("SELECT * FROM tracks WHERE artist = :artist ORDER BY title ASC")
    LiveData<List<TrackEntity>> getTracksByArtist(String artist);
    
    @Query("SELECT * FROM tracks WHERE album = :album ORDER BY title ASC")
    LiveData<List<TrackEntity>> getTracksByAlbum(String album);
    
    // Optimized batch queries
    @Query("SELECT * FROM tracks WHERE artist = :artist ORDER BY album ASC, title ASC LIMIT :limit OFFSET :offset")
    List<TrackEntity> getTracksByArtistPaginated(String artist, int limit, int offset);
    
    @Query("SELECT * FROM tracks WHERE album = :album ORDER BY title ASC LIMIT :limit OFFSET :offset")
    List<TrackEntity> getTracksByAlbumPaginated(String album, int limit, int offset);
    
    @Query("SELECT DISTINCT artist FROM tracks ORDER BY artist ASC")
    LiveData<List<String>> getAllArtists();
    
    @Query("SELECT DISTINCT album FROM tracks ORDER BY album ASC")
    LiveData<List<String>> getAllAlbums();
    
    // Batch operations for better performance
    @Upsert
    void insertTrack(TrackEntity trackEntity);
    
    @Upsert
    void insertTracks(List<TrackEntity> trackEntities);
    
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    long[] insertTracksWithIds(List<TrackEntity> trackEntities);
    
    @Update
    void updateTrack(TrackEntity trackEntity);
    
    @Update
    void updateTracks(List<TrackEntity> trackEntities);
    
    @Delete
    void deleteTrack(TrackEntity trackEntity);
    
    @Query("DELETE FROM tracks")
    void deleteAllTracks();
    
    @Query("DELETE FROM tracks WHERE path = :path")
    void deleteTrackByPath(String path);
    
    @Query("DELETE FROM tracks WHERE path IN (:paths)")
    void deleteTracksByPaths(List<String> paths);
    
    @Query("SELECT COUNT(*) FROM tracks")
    LiveData<Integer> getTrackCount();
    
    @Query("SELECT COUNT(*) FROM tracks")
    int getTrackCountSync();
    
    // Optimized queries for specific use cases
    @Query("SELECT * FROM tracks WHERE path = :path LIMIT 1")
    TrackEntity getTrackByPath(String path);
    
    @Query("SELECT path FROM tracks")
    List<String> getAllTrackPaths();
    
    @Query("SELECT * FROM tracks WHERE dateAdded > :timestamp ORDER BY dateAdded DESC")
    List<TrackEntity> getTracksAddedAfter(long timestamp);
    
    // Performance optimized queries with specific column selection
    @Query("SELECT id, title, artist, album, duration, albumArt, trackArt, path, dateAdded, size, lyrics FROM tracks ORDER BY title ASC LIMIT :limit OFFSET :offset")
    List<TrackEntity> getTracksMinimalPaginated(int limit, int offset);

    // Language queries
    @Query("SELECT DISTINCT language FROM tracks WHERE language IS NOT NULL AND language != '' ORDER BY language ASC")
    LiveData<List<String>> getDistinctLanguages();

    @Query("SELECT DISTINCT language FROM tracks WHERE language IS NOT NULL AND language != '' ORDER BY language ASC")
    List<String> getDistinctLanguagesSync();
}

