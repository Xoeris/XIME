package xime.media;

import android.content.Context;
import android.database.Cursor;
import android.media.MediaMetadataRetriever;
import android.net.Uri;
import android.provider.MediaStore;

public final class Metadata {
    public final String title;
    public final String artist;
    public final String albumName;
    public final String genre;
    public final String trackNumber;
    public final String trackCount;
    public final String discNumber;
    public final String discTotal;
    public final String lyrics;
    public final String comment;
    public final String albumArtist;
    public final String composer;
    public final String year;
    public final String bitrate;
    public final String language;
    public final String path;
    public final String coverImage; // Reference for the Graphics System
    public final long durationMs;
    public final byte[] artBytes;
    public final Uri mediaUri;

    private Metadata(Builder builder) {
        this.title = builder.title;
        this.artist = builder.artist;
        this.albumName = builder.albumName;
        this.genre = builder.genre;
        this.trackNumber = builder.trackNumber;
        this.trackCount = builder.trackCount;
        this.discNumber = builder.discNumber;
        this.discTotal = builder.discTotal;
        this.lyrics = builder.lyrics;
        this.comment = builder.comment;
        this.albumArtist = builder.albumArtist;
        this.composer = builder.composer;
        this.year = builder.year;
        this.bitrate = builder.bitrate;
        this.language = builder.language;
        this.path = builder.path;
        this.coverImage = builder.coverImage;
        this.durationMs = builder.durationMs;
        this.artBytes = builder.artBytes;
        this.mediaUri = builder.mediaUri;
    }

    // Getters for immutable fields
    public String getTitle() { return title; }
    public String getArtist() { return artist; }
    public String getAlbumName() { return albumName; }
    public String getAlbum() { return albumName; } // Compatibility alias
    public String getGenre() { return genre; }
    public String getTrackNumber() { return trackNumber; }
    public String getTrackCount() { return trackCount; }
    public String getDiscNumber() { return discNumber; }
    public String getDiscTotal() { return discTotal; }
    public String getLyrics() { return lyrics; }
    public String getComment() { return comment; }
    public String getAlbumArtist() { return albumArtist; }
    public String getComposer() { return composer; }
    public String getYear() { return year; }
    public String getBitrate() { return bitrate; }
    public String getLanguage() { return language; }
    public String getPath() { return path; }
    public String getCoverImage() { return coverImage; }
    public String getArtUri() { return coverImage; } // Compatibility alias
    public long getDurationMs() { return durationMs; }
    public long getDuration() { return durationMs; } // Compatibility alias
    public byte[] getArtBytes() { return artBytes; }
    public Uri getMediaUri() { return mediaUri; }

    public static Metadata fromFile(String path) {
        if (path == null) return null;
        return fromUri(null, android.net.Uri.fromFile(new java.io.File(path)));
    }

    public static Metadata fromUri(Context context, Uri uri) {
        if (uri == null) return null;

        android.util.Log.d("Metadata", "Extracting from URI: " + uri);

        String scheme = uri.getScheme();

        // Handle null scheme (raw file path) by re-parsing as file URI
        if (scheme == null) {
            return fromFile(uri.toString());
        }

        if ("file".equals(scheme) || "content".equals(scheme)) {
            String path = "file".equals(scheme) ? uri.getPath() : null;
            Builder builder = new Builder().setMediaUri(uri);
            if (path != null) {
                builder.setPath(path);
            }

            if ("content".equals(scheme) && context != null) {
                String[] projection = {
                        MediaStore.Audio.Media.TITLE,
                        MediaStore.Audio.Media.ARTIST,
                        MediaStore.Audio.Media.ALBUM,
                        MediaStore.Audio.Media.DURATION,
                        MediaStore.Audio.Media.DATA
                };
                try (Cursor cursor = context.getContentResolver().query(uri, projection, null, null, null)) {
                    if (cursor != null && cursor.moveToFirst()) {
                        builder.setTitle(cursor.getString(0))
                               .setArtist(cursor.getString(1))
                               .setAlbumName(cursor.getString(2))
                               .setDurationMs(cursor.getLong(3))
                               .setPath(cursor.getString(4));
                    }
                } catch (Exception ignored) {}
            }

            try (MediaMetadataRetriever retriever = new MediaMetadataRetriever()) {
                if (context != null) {
                    retriever.setDataSource(context, uri);
                } else {
                    retriever.setDataSource(path);
                }
                
                String mTitle = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE);
                if (mTitle != null) builder.setTitle(mTitle);

                String mArtist = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST);
                if (mArtist != null) builder.setArtist(mArtist);

                String mAlbum = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUM);
                if (mAlbum != null) builder.setAlbumName(mAlbum);

                builder.setGenre(retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_GENRE))
                       .setTrackNumber(retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_CD_TRACK_NUMBER))
                       .setComposer(retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_COMPOSER))
                       .setYear(retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_YEAR))
                       .setBitrate(retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_BITRATE))
                       .setAlbumArtist(retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUMARTIST))
                       .setDiscNumber(retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DISC_NUMBER));

                // Aggressive multi-key search for lyrics
                int[] prioritizedKeys = {
                    34,   // METADATA_KEY_LYRICS (API 33+)
                    1000, // Common OEM key (Sony, Samsung)
                    101,  // Samsung Synchronized Lyrics
                    102,  // Samsung Unsynchronized Lyrics
                    24,   // METADATA_KEY_COMMENT (ID3 USLT Fallback)
                    3001, // Extended Lyrics
                    13,   // METADATA_KEY_ALBUMARTIST (Fallback for mis-tagged files)
                    31,   // METADATA_KEY_AUTHOR
                    11,   // METADATA_KEY_TITLE
                    100   // General metadata
                };

                String lyrics = null;
                for (int key : prioritizedKeys) {
                    try {
                        String val = retriever.extractMetadata(key);
                        if (val != null && !val.trim().isEmpty()) {
                            // Avoid setting lyrics to artist/album name if it fell back to index 13
                            if (key == 13 && (val.equals(mArtist) || val.equals(mAlbum))) continue;
                            if (val.length() < 10 && (val.equals(mTitle) || val.equals(mArtist))) continue;
                            lyrics = val;
                            break;
                        }
                    } catch (Exception ignored) {}
                }
                
                android.util.Log.d("Metadata", "Final extracted lyrics: " + (lyrics != null ? "Length " + lyrics.length() : "null"));
                builder.setLyrics(lyrics);

                String duration = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION);
                if (duration != null) builder.setDurationMs(Long.parseLong(duration));
                
                builder.setArtBytes(retriever.getEmbeddedPicture());
            } catch (Exception e) {
                if (builder.title == null) {
                    builder.setTitle(uri.getLastPathSegment());
                }
            }
            return builder.build();
        }
        
        return new Builder().setMediaUri(uri).setPath(uri.toString()).build();
    }

    public static class Builder {
        private String title;
        private String artist;
        private String albumName;
        private String genre;
        private String trackNumber;
        private String trackCount;
        private String discNumber;
        private String discTotal;
        private String lyrics;
        private String comment;
        private String albumArtist;
        private String composer;
        private String year;
        private String bitrate;
        private String language;
        private String path;
        private String coverImage;
        private long durationMs;
        private byte[] artBytes;
        private Uri mediaUri;

        public Builder setTitle(String title) { this.title = title; return this; }
        public Builder setArtist(String artist) { this.artist = artist; return this; }
        public Builder setAlbumName(String albumName) { this.albumName = albumName; return this; }
        public Builder setAlbum(String album) { this.albumName = album; return this; } // Compatibility alias
        public Builder setGenre(String genre) { this.genre = genre; return this; }
        public Builder setTrackNumber(String trackNumber) { this.trackNumber = trackNumber; return this; }
        public Builder setTrackCount(String trackCount) { this.trackCount = trackCount; return this; }
        public Builder setDiscNumber(String discNumber) { this.discNumber = discNumber; return this; }
        public Builder setDiscTotal(String discTotal) { this.discTotal = discTotal; return this; }
        public Builder setLyrics(String lyrics) { this.lyrics = lyrics; return this; }
        public Builder setComment(String comment) { this.comment = comment; return this; }
        public Builder setAlbumArtist(String albumArtist) { this.albumArtist = albumArtist; return this; }
        public Builder setComposer(String composer) { this.composer = composer; return this; }
        public Builder setYear(String year) { this.year = year; return this; }
        public Builder setBitrate(String bitrate) { this.bitrate = bitrate; return this; }
        public Builder setLanguage(String language) { this.language = language; return this; }
        public Builder setPath(String path) { this.path = path; return this; }
        public Builder setCoverImage(String coverImage) { this.coverImage = coverImage; return this; }
        public Builder setDurationMs(long durationMs) { this.durationMs = durationMs; return this; }
        public Builder setDuration(long durationMs) { this.durationMs = durationMs; return this; } // Compatibility alias
        public Builder setArtBytes(byte[] artBytes) { this.artBytes = artBytes; return this; }
        public Builder setMediaUri(Uri mediaUri) { this.mediaUri = mediaUri; return this; }
        public Builder setArtUri(Uri artUri) { if (artUri != null) this.coverImage = artUri.toString(); return this; } // Compatibility alias

        public Metadata build() {
            return new Metadata(this);
        }
    }

    @Override
    public String toString() {
        return "Metadata{" + "title='" + title + '\'' + ", artist='" + artist + '\'' + '}';
    }
}

