package xime.media;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;

public final class MetadataEditor {
    private final Metadata source;
    private final Map<String, String> drafts = new HashMap<>();
    private boolean isDirty = false;
    private Consumer<MetadataEditor> onStateChanged;

    public MetadataEditor(Metadata source) {
        this.source = source;
    }

    public void setOnStateChangedListener(Consumer<MetadataEditor> listener) {
        this.onStateChanged = listener;
    }

    // --- Draft Management ---

    public String getTitle() { return drafts.getOrDefault("title", source.getTitle()); }
    public void setTitle(String title) { updateDraft("title", title); }

    public String getArtist() { return drafts.getOrDefault("artist", source.getArtist()); }
    public void setArtist(String artist) { updateDraft("artist", artist); }

    public String getAlbumName() { return drafts.getOrDefault("album", source.getAlbumName()); }
    public void setAlbumName(String album) { updateDraft("album", album); }

    public String getGenre() { return drafts.getOrDefault("genre", source.getGenre()); }
    public void setGenre(String genre) { updateDraft("genre", genre); }

    public String getYear() { return drafts.getOrDefault("year", source.getYear()); }
    public void setYear(String year) { updateDraft("year", year); }

    public String getTrackNumber() { return drafts.getOrDefault("track", source.getTrackNumber()); }
    public void setTrackNumber(String track) { updateDraft("track", track); }

    public String getTrackCount() { return drafts.getOrDefault("track_count", source.getTrackCount()); }
    public void setTrackCount(String count) { updateDraft("track_count", count); }

    public String getDiscNumber() { return drafts.getOrDefault("disc", source.getDiscNumber()); }
    public void setDiscNumber(String disc) { updateDraft("disc", disc); }

    public String getDiscTotal() { return drafts.getOrDefault("disc_total", source.getDiscTotal()); }
    public void setDiscTotal(String total) { updateDraft("disc_total", total); }

    public String getComposer() { return drafts.getOrDefault("composer", source.getComposer()); }
    public void setComposer(String composer) { updateDraft("composer", composer); }

    public String getAlbumArtist() { return drafts.getOrDefault("album_artist", source.getAlbumArtist()); }
    public void setAlbumArtist(String artist) { updateDraft("album_artist", artist); }

    public String getLyrics() { return drafts.getOrDefault("lyrics", source.getLyrics()); }
    public void setLyrics(String lyrics) { updateDraft("lyrics", lyrics); }

    public String getComment() { return drafts.getOrDefault("comment", source.getComment()); }
    public void setComment(String comment) { updateDraft("comment", comment); }

    public String getBitrate() { return drafts.getOrDefault("bitrate", source.getBitrate()); }
    public void setBitrate(String bitrate) { updateDraft("bitrate", bitrate); }

    public String getLanguage() { return drafts.getOrDefault("language", source.getLanguage()); }
    public void setLanguage(String language) { updateDraft("language", language); }

    private void updateDraft(String key, String value) {
        String current = drafts.containsKey(key) ? drafts.get(key) : getSourceValue(key);
        if (value != null && !value.equals(current)) {
            drafts.put(key, value);
            isDirty = true;
            notifyUI();
        }
    }

    private String getSourceValue(String key) {
        switch (key) {
            case "title": return source.getTitle();
            case "artist": return source.getArtist();
            case "album": return source.getAlbumName();
            case "genre": return source.getGenre();
            case "year": return source.getYear();
            case "track": return source.getTrackNumber();
            case "track_count": return source.getTrackCount();
            case "disc": return source.getDiscNumber();
            case "disc_total": return source.getDiscTotal();
            case "composer": return source.getComposer();
            case "album_artist": return source.getAlbumArtist();
            case "lyrics": return source.getLyrics();
            case "comment": return source.getComment();
            case "bitrate": return source.getBitrate();
            case "language": return source.getLanguage();
            default: return null;
        }
    }

    // --- State Notification ---

    public boolean isDirty() {
        return isDirty;
    }

    private void notifyUI() {
        if (onStateChanged != null) {
            onStateChanged.accept(this);
        }
    }

    // --- Validation Logic ---

    public ValidationResult validate() {
        // Example: Validate Year format
        String year = getYear();
        if (year != null && !year.isEmpty() && !year.matches("\\d{4}")) {
            return new ValidationResult(false, "Invalid year format (YYYY expected)");
        }

        // Example: Validate Track Number
        String track = getTrackNumber();
        if (track != null && !track.isEmpty()) {
            try {
                Integer.parseInt(track);
            } catch (NumberFormatException e) {
                return new ValidationResult(false, "Track number must be numeric");
            }
        }

        return new ValidationResult(true, null);
    }

    // --- Commitment Logic ---

    public Metadata commit() {
        if (!validate().isValid) return source;

        Metadata.Builder builder = new Metadata.Builder()
                .setTitle(getTitle())
                .setArtist(getArtist())
                .setAlbumName(getAlbumName())
                .setGenre(getGenre())
                .setYear(getYear())
                .setTrackNumber(getTrackNumber())
                .setTrackCount(getTrackCount())
                .setDiscNumber(getDiscNumber())
                .setDiscTotal(getDiscTotal())
                .setComposer(getComposer())
                .setAlbumArtist(getAlbumArtist())
                .setLyrics(getLyrics())
                .setComment(getComment())
                .setBitrate(getBitrate())
                .setPath(source.getPath())
                .setMediaUri(source.getMediaUri())
                .setArtBytes(source.getArtBytes())
                .setDurationMs(source.getDurationMs());
        
        // Retain other fields from source...
        
        isDirty = false;
        drafts.clear();
        return builder.build();
    }

    public static class ValidationResult {
        public final boolean isValid;
        public final String message;

        public ValidationResult(boolean isValid, String message) {
            this.isValid = isValid;
            this.message = message;
        }
    }
}

