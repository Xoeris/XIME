package xime.media;

import android.content.Context;
import android.content.SharedPreferences;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class MediaPrefs {
    private static final String PREF_NAME = "hyper_media_prefs";
    private static final String KEY_REPEAT_MODE = "repeat_mode";
    private static final String KEY_SHUFFLE_MODE = "shuffle_mode";
    private static final String KEY_LAST_SONG_URI = "last_song_uri";
    private static final String KEY_LAST_SONG_POSITION = "last_song_position";
    private static final String KEY_LAST_IS_PLAYING = "last_is_playing";
    private static final String KEY_SHOW_SPECTRUM = "show_spectrum";
    private static final String KEY_LAST_PLAYLIST = "last_playlist_paths";
    private static final String KEY_LAST_PLAYLIST_NAME = "last_playlist_name";
    private static final String KEY_LAST_TRACK_INDEX = "last_track_index";

    // Per-context keys – Spotify-like per-playlist / per-library shuffle & repeat
    private static final String PREFIX_SHUFFLE_CTX = "shuffle_mode_ctx_";
    private static final String PREFIX_REPEAT_CTX = "repeat_mode_ctx_";

    private final SharedPreferences prefs;

    public MediaPrefs(Context context) {
        this.prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
    }

    public void saveRepeatMode(Queue.RepeatMode mode) {
        prefs.edit().putInt(KEY_REPEAT_MODE, mode.ordinal()).apply();
    }

    public Queue.RepeatMode getRepeatMode() {
        int ordinal = prefs.getInt(KEY_REPEAT_MODE, Queue.RepeatMode.NONE.ordinal());
        return Queue.RepeatMode.values()[ordinal];
    }

    public void saveShuffleMode(boolean enabled) {
        prefs.edit().putBoolean(KEY_SHUFFLE_MODE, enabled).apply();
    }

    public boolean getShuffleMode() {
        return prefs.getBoolean(KEY_SHUFFLE_MODE, false);
    }

    // ===== Per-context (per-playlist / per-library) Shuffle & Repeat =====

    /**
     * Generates a stable context key for per-playlist / per-library storage.
     * - Playlist with valid id  => "playlist_<id>" (rename-safe)
     * - Library (id==-1 && name Library) => "library"
     * - Fallback generic queue => "queue"
     * - Other ad-hoc contexts (Artist, Album, etc.) => "ctx_<sanitizedName>"
     */
    public static String contextKeyFor(long playlistId, String playlistName) {
        if (playlistId != -1) return "playlist_" + playlistId;
        if (playlistName != null) {
            String trimmed = playlistName.trim();
            if (trimmed.equalsIgnoreCase("Library")) return "library";
            if (trimmed.equalsIgnoreCase("Queue") || trimmed.isEmpty()) return "queue";
            String sanitized = trimmed.replaceAll("[^a-zA-Z0-9]", "_");
            if (sanitized.length() > 40) sanitized = sanitized.substring(0, 40);
            return "ctx_" + sanitized;
        }
        return "queue";
    }

    /** Convenience overload for PlaylistEntity ids. */
    public static String contextKeyForPlaylist(long playlistId) {
        return "playlist_" + playlistId;
    }

    public static String libraryContextKey() { return "library"; }
    public static String queueContextKey() { return "queue"; }

    public void saveShuffleModeForContext(String contextKey, boolean enabled) {
        if (contextKey == null) return;
        prefs.edit().putBoolean(PREFIX_SHUFFLE_CTX + contextKey, enabled).apply();
        // Keep global as mirror for backward compat when context is queue/library
        if ("queue".equals(contextKey) || "library".equals(contextKey)) {
            prefs.edit().putBoolean(KEY_SHUFFLE_MODE, enabled).apply();
        }
    }

    public boolean getShuffleModeForContext(String contextKey) {
        if (contextKey == null) return getShuffleMode();
        String key = PREFIX_SHUFFLE_CTX + contextKey;
        if (prefs.contains(key)) return prefs.getBoolean(key, false);
        // Backwards compat: if per-context not yet stored, fall back to legacy global for queue/library
        if ("queue".equals(contextKey) || "library".equals(contextKey)) {
            if (prefs.contains(KEY_SHUFFLE_MODE)) return prefs.getBoolean(KEY_SHUFFLE_MODE, false);
        }
        return false;
    }

    public void saveRepeatModeForContext(String contextKey, Queue.RepeatMode mode) {
        if (contextKey == null || mode == null) return;
        prefs.edit().putInt(PREFIX_REPEAT_CTX + contextKey, mode.ordinal()).apply();
        if ("queue".equals(contextKey) || "library".equals(contextKey)) {
            prefs.edit().putInt(KEY_REPEAT_MODE, mode.ordinal()).apply();
        }
    }

    public Queue.RepeatMode getRepeatModeForContext(String contextKey) {
        if (contextKey == null) return getRepeatMode();
        String key = PREFIX_REPEAT_CTX + contextKey;
        if (prefs.contains(key)) {
            int ordinal = prefs.getInt(key, Queue.RepeatMode.NONE.ordinal());
            Queue.RepeatMode[] values = Queue.RepeatMode.values();
            if (ordinal >= 0 && ordinal < values.length) return values[ordinal];
            return Queue.RepeatMode.NONE;
        }
        if ("queue".equals(contextKey) || "library".equals(contextKey)) {
            if (prefs.contains(KEY_REPEAT_MODE)) {
                int ordinal = prefs.getInt(KEY_REPEAT_MODE, Queue.RepeatMode.NONE.ordinal());
                Queue.RepeatMode[] values = Queue.RepeatMode.values();
                if (ordinal >= 0 && ordinal < values.length) return values[ordinal];
            }
        }
        return Queue.RepeatMode.NONE;
    }

    public void clearContext(String contextKey) {
        if (contextKey == null) return;
        prefs.edit().remove(PREFIX_SHUFFLE_CTX + contextKey).remove(PREFIX_REPEAT_CTX + contextKey).apply();
    }

    public void saveLastSongUri(String uri) {
        prefs.edit().putString(KEY_LAST_SONG_URI, uri).apply();
    }

    public String getLastSongUri() {
        return prefs.getString(KEY_LAST_SONG_URI, null);
    }

    public void saveLastPosition(long position) {
        prefs.edit().putLong(KEY_LAST_SONG_POSITION, position).apply();
    }

    public long getLastPosition() {
        return prefs.getLong(KEY_LAST_SONG_POSITION, 0L);
    }

    public void saveIsPlaying(boolean playing) {
        prefs.edit().putBoolean(KEY_LAST_IS_PLAYING, playing).apply();
    }

    public boolean getLastIsPlaying() {
        return prefs.getBoolean(KEY_LAST_IS_PLAYING, false);
    }

    public void saveShowSpectrum(boolean show) {
        prefs.edit().putBoolean(KEY_SHOW_SPECTRUM, show).apply();
    }

    public boolean isShowSpectrum() {
        return prefs.getBoolean(KEY_SHOW_SPECTRUM, true);
    }

    public void saveLastPlaylist(List<String> paths, String name, int currentIndex) {
        String data = "";
        if (paths != null && !paths.isEmpty()) {
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < paths.size(); i++) {
                sb.append(paths.get(i));
                if (i < paths.size() - 1) sb.append("|");
            }
            data = sb.toString();
        }
        prefs.edit()
                .putString(KEY_LAST_PLAYLIST, data)
                .putString(KEY_LAST_PLAYLIST_NAME, name)
                .putInt(KEY_LAST_TRACK_INDEX, currentIndex)
                .apply();
    }

    public List<String> getLastPlaylistPaths() {
        String data = prefs.getString(KEY_LAST_PLAYLIST, "");
        if (data.isEmpty()) return new ArrayList<>();
        return new ArrayList<>(Arrays.asList(data.split("\\|")));
    }

    public String getLastPlaylistName() {
        return prefs.getString(KEY_LAST_PLAYLIST_NAME, "Queue");
    }

    public int getLastTrackIndex() {
        return prefs.getInt(KEY_LAST_TRACK_INDEX, -1);
    }
}

