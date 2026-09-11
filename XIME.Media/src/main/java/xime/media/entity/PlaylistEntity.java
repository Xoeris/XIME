package xime.media.entity;

import androidx.room.Entity;
import androidx.room.Ignore;
import androidx.room.PrimaryKey;

@Entity(tableName = "playlists")
public class PlaylistEntity {
    @PrimaryKey(autoGenerate = true)
    private long id;
    
    private String name;
    private String description;
    private String language;
    private long createdAt;
    private long updatedAt;
    private int trackCount;
    private String backgroundImageUri;
    
    public PlaylistEntity() {}
    
    @Ignore
    public PlaylistEntity(String name, String description) {
        this(name, description, "English");
    }

    @Ignore
    public PlaylistEntity(String name, String description, String language) {
        this.name = name;
        this.description = description;
        this.language = language;
        this.createdAt = System.currentTimeMillis();
        this.updatedAt = System.currentTimeMillis();
        this.trackCount = 0;
    }
    
    // Getters and Setters
    public long getId() { return id; }
    public void setId(long id) { this.id = id; }
    
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public String getLanguage() { return language; }
    public void setLanguage(String language) { this.language = language; }
    
    public long getCreatedAt() { return createdAt; }
    public void setCreatedAt(long createdAt) { this.createdAt = createdAt; }
    
    public long getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(long updatedAt) { this.updatedAt = updatedAt; }
    
    public int getTrackCount() { return trackCount; }
    public void setTrackCount(int trackCount) { this.trackCount = trackCount; }

    public String getBackgroundImageUri() { return backgroundImageUri; }
    public void setBackgroundImageUri(String backgroundImageUri) { this.backgroundImageUri = backgroundImageUri; }
}

