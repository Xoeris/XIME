package xime.media.dao;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.Query;

import xime.media.entity.DeletedPlaylistEntity;

import java.util.List;

@Dao
public interface DeletedPlaylistDao {
    
    @Insert
    long insertDeletedPlaylist(DeletedPlaylistEntity deletedPlaylistEntity);
    
    @Query("SELECT * FROM deleted_playlists ORDER BY deletedAt DESC")
    LiveData<List<DeletedPlaylistEntity>> getAllDeletedPlaylists();
    
    @Query("SELECT * FROM deleted_playlists ORDER BY deletedAt DESC")
    List<DeletedPlaylistEntity> getAllDeletedPlaylistsSync();
    
    @Query("SELECT * FROM deleted_playlists WHERE id = :id")
    DeletedPlaylistEntity getDeletedPlaylistById(long id);
    
    @Delete
    void permanentlyDeletePlaylist(DeletedPlaylistEntity deletedPlaylistEntity);
    
    @Query("DELETE FROM deleted_playlists WHERE id = :id")
    void permanentlyDeletePlaylistById(long id);
    
    @Query("DELETE FROM deleted_playlists")
    void clearTrashBin();
    
    @Query("DELETE FROM deleted_playlists WHERE deletedAt < :timestamp")
    void deleteOldPlaylists(long timestamp);
}

