package xime.media.entity;

import androidx.room.Entity;
import androidx.room.ForeignKey;
import androidx.room.Ignore;
import androidx.room.Index;
import androidx.room.PrimaryKey;

@Entity(
    tableName = "playlist_tracks",
    foreignKeys = {
        @ForeignKey(entity = PlaylistEntity.class, parentColumns = "id", childColumns = "playlistId", onDelete = ForeignKey.CASCADE, onUpdate = ForeignKey.CASCADE),
        @ForeignKey(entity = TrackEntity.class, parentColumns = "id", childColumns = "trackId", onDelete = ForeignKey.CASCADE, onUpdate = ForeignKey.CASCADE)
    },
    indices = {
        @Index("playlistId"),
        @Index("trackId")
    }
)
public class PlaylistTrackEntity {
    @PrimaryKey(autoGenerate = true)
    private long id;
    
    private long playlistId;
    private long trackId;
    private int position;
    private long addedAt;
    
    public PlaylistTrackEntity() {}
    
    @Ignore
    public PlaylistTrackEntity(long playlistId, long trackId, int position) {
        this.playlistId = playlistId;
        this.trackId = trackId;
        this.position = position;
        this.addedAt = System.currentTimeMillis();
    }
    
    // Getters and Setters
    public long getId() { return id; }
    public void setId(long id) { this.id = id; }
    
    public long getPlaylistId() { return playlistId; }
    public void setPlaylistId(long playlistId) { this.playlistId = playlistId; }
    
    public long getTrackId() { return trackId; }
    public void setTrackId(long trackId) { this.trackId = trackId; }
    
    public int getPosition() { return position; }
    public void setPosition(int position) { this.position = position; }
    
    public long getAddedAt() { return addedAt; }
    public void setAddedAt(long addedAt) { this.addedAt = addedAt; }
}

