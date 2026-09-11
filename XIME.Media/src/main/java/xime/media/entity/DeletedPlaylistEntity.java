package xime.media.entity;

import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "deleted_playlists")
public class DeletedPlaylistEntity {
    @PrimaryKey(autoGenerate = true)
    private long id;
    
    private String name;
    private long originalId;
    private long createdAt;
    private long updatedAt;
    private long deletedAt;
    private String playlistData; // JSON string containing playlist tracks data
    
    public DeletedPlaylistEntity() {}
    
    public DeletedPlaylistEntity(PlaylistEntity originalPlaylistEntity, String playlistData) {
        this.name = originalPlaylistEntity.getName();
        this.originalId = originalPlaylistEntity.getId();
        this.createdAt = originalPlaylistEntity.getCreatedAt();
        this.updatedAt = originalPlaylistEntity.getUpdatedAt();
        this.deletedAt = System.currentTimeMillis();
        this.playlistData = playlistData;
    }
    
    // Getters and Setters
    public long getId() { return id; }
    public void setId(long id) { this.id = id; }
    
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    
    public long getOriginalId() { return originalId; }
    public void setOriginalId(long originalId) { this.originalId = originalId; }
    
    public long getCreatedAt() { return createdAt; }
    public void setCreatedAt(long createdAt) { this.createdAt = createdAt; }
    
    public long getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(long updatedAt) { this.updatedAt = updatedAt; }
    
    public long getDeletedAt() { return deletedAt; }
    public void setDeletedAt(long deletedAt) { this.deletedAt = deletedAt; }
    
    public String getPlaylistData() { return playlistData; }
    public void setPlaylistData(String playlistData) { this.playlistData = playlistData; }
}

