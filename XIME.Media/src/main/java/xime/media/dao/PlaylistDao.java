package xime.media.dao;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Query;
import androidx.room.Update;
import androidx.room.Upsert;

import xime.media.entity.PlaylistEntity;
import xime.media.entity.PlaylistTrackEntity;
import xime.media.entity.PlaylistWithArt;
import xime.media.entity.TrackEntity;

import java.util.List;

@Dao
public interface PlaylistDao {
    
    @Query("SELECT * FROM playlists ORDER BY name ASC")
    LiveData<List<PlaylistEntity>> getAllPlaylists();

    @Query("SELECT p.*, " +
           "(SELECT COALESCE(t.albumArt, t.trackArt) FROM tracks t " +
           "INNER JOIN playlist_tracks pt ON t.id = pt.trackId " +
           "WHERE pt.playlistId = p.id ORDER BY pt.position ASC LIMIT 1) as firstTrackArt " +
           "FROM playlists p ORDER BY p.name ASC")
    LiveData<List<PlaylistWithArt>> getAllPlaylistsWithArt();
    
    @Query("SELECT * FROM playlists ORDER BY name ASC")
    List<PlaylistEntity> getAllPlaylistsSync();
    
    @Query("SELECT * FROM playlists WHERE id = :id")
    LiveData<PlaylistEntity> getPlaylistById(long id);
    
    @Query("SELECT * FROM playlists WHERE id = :id")
    PlaylistEntity getPlaylistByIdSync(long id);
    
    @Query("SELECT t.* FROM tracks t " +
           "INNER JOIN playlist_tracks pt ON t.id = pt.trackId " +
           "WHERE pt.playlistId = :playlistId " +
           "ORDER BY pt.position ASC")
    LiveData<List<TrackEntity>> getTracksInPlaylist(long playlistId);
    
    @Query("SELECT t.* FROM tracks t " +
           "INNER JOIN playlist_tracks pt ON t.id = pt.trackId " +
           "WHERE pt.playlistId = :playlistId " +
           "ORDER BY pt.position ASC")
    List<TrackEntity> getTracksInPlaylistSync(long playlistId);
    
    @Query("SELECT COUNT(*) FROM playlist_tracks WHERE playlistId = :playlistId")
    LiveData<Integer> getPlaylistTrackCount(long playlistId);

    @Query("SELECT COUNT(*) FROM playlist_tracks WHERE playlistId = :playlistId")
    int getPlaylistTrackCountSync(long playlistId);
    
    @Upsert
    long insertPlaylist(PlaylistEntity playlistEntity);
    
    @Upsert
    void insertPlaylistTrack(PlaylistTrackEntity playlistTrackEntity);

    @Upsert
    void insertPlaylistTracks(List<PlaylistTrackEntity> playlistTrackEntities);
    
    @Update
    void updatePlaylist(PlaylistEntity playlistEntity);
    
    @Delete
    void deletePlaylist(PlaylistEntity playlistEntity);
    
    @Query("DELETE FROM playlist_tracks WHERE playlistId = :playlistId AND trackId = :trackId")
    void removeTrackFromPlaylist(long playlistId, long trackId);
    
    @Query("DELETE FROM playlist_tracks WHERE playlistId = :playlistId")
    void clearPlaylist(long playlistId);
    
    @Query("SELECT MAX(position) FROM playlist_tracks WHERE playlistId = :playlistId")
    int getMaxPositionInPlaylist(long playlistId);
    
    @Query("UPDATE playlist_tracks SET position = position - 1 " +
           "WHERE playlistId = :playlistId AND position > (SELECT position FROM playlist_tracks WHERE playlistId = :playlistId AND trackId = :trackId)")
    void updatePositionsAfterRemoval(long playlistId, long trackId);
}

