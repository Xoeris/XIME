package xime.media.entity;

import android.os.Parcel;
import android.os.Parcelable;
import androidx.room.Entity;
import androidx.room.Ignore;
import androidx.room.Index;
import androidx.room.PrimaryKey;

import java.io.Serializable;

@Entity(tableName = "tracks",
        indices = {
            @Index(value = {"title"}, name = "index_tracks_title"),
            @Index(value = {"artist"}, name = "index_tracks_artist"),
            @Index(value = {"album"}, name = "index_tracks_album"),
            @Index(value = {"path"}, name = "index_tracks_path", unique = true),
            @Index(value = {"dateAdded"}, name = "index_tracks_date_added"),
            @Index(value = {"artist", "album"}, name = "index_tracks_artist_album"),
            @Index(value = {"title", "artist"}, name = "index_tracks_title_artist")
        })
public class TrackEntity implements Parcelable, Serializable {
    @PrimaryKey
    private long id;
    
    private String title;
    private String artist;
    private String album;
    private String path;
    private String language;
    private long duration;
    private String albumArt;
    private String trackArt;
    private String genre;
    private String year;
    private String trackNumber;
    private String trackCount;
    private String discNumber;
    private String discTotal;
    private String composer;
    private String albumArtist;
    private String lyrics;
    private String comment;
    private String bitrate;
    private long dateAdded;
    private long size;
    
    public TrackEntity() {}
    
    @Ignore
    public TrackEntity(String title, String artist, String album, String path, long duration, String albumArt, String trackArt, String genre, long dateAdded, long size) {
        this(title, artist, album, path, "English", duration, albumArt, trackArt, genre, dateAdded, size);
    }

    @Ignore
    public TrackEntity(String title, String artist, String album, String path, String language, long duration, String albumArt, String trackArt, String genre, long dateAdded, long size) {
        this.title = title;
        this.artist = artist;
        this.album = album;
        this.path = path;
        this.language = language;
        this.duration = duration;
        this.albumArt = albumArt;
        this.trackArt = trackArt;
        this.genre = genre;
        this.dateAdded = dateAdded;
        this.size = size;
    }

    @Ignore
    public TrackEntity(String title, String artist, String album, String path, long duration, String albumArt, String trackArt, String genre, String year, String trackNumber, String trackCount, String discNumber, String discTotal, String composer, String albumArtist, String lyrics, String comment, String bitrate, long dateAdded, long size) {
        this(title, artist, album, path, "English", duration, albumArt, trackArt, genre, year, trackNumber, trackCount, discNumber, discTotal, composer, albumArtist, lyrics, comment, bitrate, dateAdded, size);
    }

    @Ignore
    public TrackEntity(String title, String artist, String album, String path, String language, long duration, String albumArt, String trackArt, String genre, String year, String trackNumber, String trackCount, String discNumber, String discTotal, String composer, String albumArtist, String lyrics, String comment, String bitrate, long dateAdded, long size) {
        this.title = title;
        this.artist = artist;
        this.album = album;
        this.path = path;
        this.language = language;
        this.duration = duration;
        this.albumArt = albumArt;
        this.trackArt = trackArt;
        this.genre = genre;
        this.year = year;
        this.trackNumber = trackNumber;
        this.trackCount = trackCount;
        this.discNumber = discNumber;
        this.discTotal = discTotal;
        this.composer = composer;
        this.albumArtist = albumArtist;
        this.lyrics = lyrics;
        this.comment = comment;
        this.bitrate = bitrate;
        this.dateAdded = dateAdded;
        this.size = size;
    }
    
    // Getters and Setters
    public long getId() { return id; }
    public void setId(long id) { this.id = id; }
    
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    
    public String getArtist() { return artist; }
    public void setArtist(String artist) { this.artist = artist; }
    
    public String getAlbum() { return album; }
    public void setAlbum(String album) { this.album = album; }
    
    public String getPath() { return path; }
    public void setPath(String path) { this.path = path; }

    public String getLanguage() { return language; }
    public void setLanguage(String language) { this.language = language; }
    
    public long getDuration() { return duration; }
    public void setDuration(long duration) { this.duration = duration; }
    
    public String getAlbumArt() { return albumArt; }
    public void setAlbumArt(String albumArt) { this.albumArt = albumArt; }

    public String getTrackArt() { return trackArt; }
    public void setTrackArt(String trackArt) { this.trackArt = trackArt; }

    public String getGenre() { return genre; }
    public void setGenre(String genre) { this.genre = genre; }

    public String getYear() { return year; }
    public void setYear(String year) { this.year = year; }

    public String getTrackNumber() { return trackNumber; }
    public void setTrackNumber(String trackNumber) { this.trackNumber = trackNumber; }

    public String getTrackCount() { return trackCount; }
    public void setTrackCount(String trackCount) { this.trackCount = trackCount; }

    public String getDiscNumber() { return discNumber; }
    public void setDiscNumber(String discNumber) { this.discNumber = discNumber; }

    public String getDiscTotal() { return discTotal; }
    public void setDiscTotal(String discTotal) { this.discTotal = discTotal; }

    public String getComposer() { return composer; }
    public void setComposer(String composer) { this.composer = composer; }

    public String getAlbumArtist() { return albumArtist; }
    public void setAlbumArtist(String albumArtist) { this.albumArtist = albumArtist; }

    public String getLyrics() { return lyrics; }
    public void setLyrics(String lyrics) { this.lyrics = lyrics; }

    public String getComment() { return comment; }
    public void setComment(String comment) { this.comment = comment; }

    public String getBitrate() { return bitrate; }
    public void setBitrate(String bitrate) { this.bitrate = bitrate; }
    
    public long getDateAdded() { return dateAdded; }
    public void setDateAdded(long dateAdded) { this.dateAdded = dateAdded; }
    
    public long getSize() { return size; }
    public void setSize(long size) { this.size = size; }
    
    public String getFormattedDuration() {
        // Handle invalid duration values
        if (duration < 0 || duration == Long.MIN_VALUE || duration > 86400000) { // > 24 hours
            return "0:00";
        }
        
        long minutes = duration / 60000;
        long seconds = (duration % 60000) / 1000;
        return String.format("%d:%02d", minutes, seconds);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        TrackEntity trackEntity = (TrackEntity) o;
        return id == trackEntity.id || (path != null && path.equals(trackEntity.path));
    }

    @Override
    public int hashCode() {
        return java.util.Objects.hash(id, path);
    }

    // Parcelable implementation
    protected TrackEntity(Parcel in) {
        id = in.readLong();
        title = in.readString();
        artist = in.readString();
        album = in.readString();
        path = in.readString();
        language = in.readString();
        duration = in.readLong();
        albumArt = in.readString();
        trackArt = in.readString();
        genre = in.readString();
        year = in.readString();
        trackNumber = in.readString();
        trackCount = in.readString();
        discNumber = in.readString();
        discTotal = in.readString();
        composer = in.readString();
        albumArtist = in.readString();
        lyrics = in.readString();
        comment = in.readString();
        bitrate = in.readString();
        dateAdded = in.readLong();
        size = in.readLong();
    }

    public static final Creator<TrackEntity> CREATOR = new Creator<TrackEntity>() {
        @Override
        public TrackEntity createFromParcel(Parcel in) {
            return new TrackEntity(in);
        }

        @Override
        public TrackEntity[] newArray(int size) {
            return new TrackEntity[size];
        }
    };

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeLong(id);
        dest.writeString(title);
        dest.writeString(artist);
        dest.writeString(album);
        dest.writeString(path);
        dest.writeString(language);
        dest.writeLong(duration);
        dest.writeString(albumArt);
        dest.writeString(trackArt);
        dest.writeString(genre);
        dest.writeString(year);
        dest.writeString(trackNumber);
        dest.writeString(trackCount);
        dest.writeString(discNumber);
        dest.writeString(discTotal);
        dest.writeString(composer);
        dest.writeString(albumArtist);
        dest.writeString(lyrics);
        dest.writeString(comment);
        dest.writeString(bitrate);
        dest.writeLong(dateAdded);
        dest.writeLong(size);
    }
}

