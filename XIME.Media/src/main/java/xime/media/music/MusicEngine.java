package xime.media.music;

import android.content.ContentUris;
import android.content.Context;
import android.net.Uri;
import android.provider.MediaStore;
import android.util.Log;
import xime.media.database.MusicDatabase;
import xime.media.entity.TrackEntity;
import xime.media.MediaPrefs;
import xime.media.Metadata;
import xime.media.Playlist;
import xime.media.Queue;
import xime.media.Signal;

import java.io.File;

public class MusicEngine {
    private static final String TAG = "MusicEngine";
    private static MusicEngine instance;
    
    private final Music music;
    private final Queue queue;
    private final MediaPrefs prefs;
    private final Context context;
    
    public interface ActionHandler {
        void onTogglePlayback();
        void onPlayNext();
        void onPlayPrevious();
        void onSeekTo(int position);
        void onToggleShuffle();
        void onCycleRepeatMode();
    }
    
    private ActionHandler actionHandler;
    
    private boolean shuffleEnabled;
    private boolean isExternallySynced = false;
    private final Signal<Boolean> shuffleSignal = new Signal<>();
    private final Signal<Queue.RepeatMode> repeatSignal = new Signal<>();

    // Per-context (per-playlist / per-library) – Spotify-like isolation
    private String currentContextKey = MediaPrefs.queueContextKey();

    private MusicEngine(Context context) {
        Context appCtx = context.getApplicationContext();
        this.context = (appCtx != null) ? appCtx : context;
        this.music = new Music(this.context);
        this.prefs = new MediaPrefs(this.context);
        this.queue = this.music.getQueue();
        
        this.queue.setPlaylist(new Playlist("Default"));
        
        // Restore per-context state – defaults to queue context
        this.currentContextKey = MediaPrefs.queueContextKey();
        Queue.RepeatMode savedRepeat = prefs.getRepeatModeForContext(currentContextKey);
        boolean savedShuffle = prefs.getShuffleModeForContext(currentContextKey);
        this.queue.setRepeatModeSilently(savedRepeat);
        this.queue.setShuffleEnabledSilently(savedShuffle);
        this.shuffleEnabled = savedShuffle;
        
        // Initialize signals with restored state
        shuffleSignal.emit(shuffleEnabled);
        repeatSignal.emit(this.queue.getRepeatMode());
        
        setupListeners();
    }

    private void setupListeners() {
        // Auto-play next on completion
        this.music.getStateSignal().observe(state -> {
            if (state == null) return;
            Log.d(TAG, "State observed: " + state);

            if (!isExternallySynced && state == Music.PlaybackState.COMPLETED) {
                playNext();
            }
            
            if (state == Music.PlaybackState.PAUSED || state == Music.PlaybackState.PLAYING) {
                saveFullState();
            }
        });

        // Handle preloading of next track for gapless/crossfade
        this.music.getPreloadNextSignal().observe(v -> {
            if (!isExternallySynced) {
                preloadNext();
            }
        });

        // Listen for metadata changes and save state
        this.music.getMetadataSignal().observe(metadata -> {
            if (metadata != null) {
                if (queue.getCurrentItem() != metadata) {
                    int index = queue.getActiveQueue().indexOf(metadata);
                    if (index != -1) {
                        queue.jumpTo(index);
                    }
                }
                saveFullState();
            }
        });

        // Periodically save position (500ms cadence so resume-after-reopen is accurate)
        this.music.getProgressSignal().observe(progress -> {
            if (progress != null && !isExternallySynced) {
                long now = System.currentTimeMillis();
                if (now - lastPositionSaveTime > 500) {
                    prefs.saveLastPosition(progress.position);
                    lastPositionSaveTime = now;
                }
            }
        });
    }

    private long lastPositionSaveTime = 0;

    private synchronized void saveFullState() {
        Metadata current = queue.getCurrentItem();
        if (current != null) {
            prefs.saveLastSongUri(current.getPath());
            java.util.List<String> paths = new java.util.ArrayList<>();
            for (Metadata m : queue.getActiveQueue()) {
                paths.add(m.getPath());
            }
            prefs.saveLastPlaylist(paths, queue.getPlaylistName(), queue.getCurrentIndex());
            // Save position atomically with track/playlist so resume never mixes a new
            // track with an old position. Prefer a pending user seek, else the freshest
            // progress event (exact at pause time since emits stop when paused).
            long posToSave = music.getPendingSeekMs();
            if (posToSave < 0) {
                Music.Progress last = music.getProgressSignal().getLastEvent();
                if (last != null) posToSave = last.position;
            }
            if (posToSave >= 0) {
                prefs.saveLastPosition(posToSave);
                lastPositionSaveTime = System.currentTimeMillis();
            }
            // Also persist per-context settings are already saved via toggle/cycle
            prefs.saveIsPlaying(music.isPlaying());
        }
    }

    public static synchronized MusicEngine getInstance(Context context) {
        if (instance == null) {
            instance = new MusicEngine(context);
        }
        return instance;
    }

    public void play(Metadata metadata) {
        if (metadata == null) return;
        Log.d(TAG, "Playing track: " + metadata.getTitle() + " (Externally Synced: " + isExternallySynced + ")");
        
        if (!isExternallySynced) {
            music.play(metadata);
        }
    }

    private void preloadNext() {
        Metadata next = queue.peekNext();
        if (next != null) {
            Log.d(TAG, "Preloading next track: " + next.getTitle());
            music.prepareNext(next.getPath(), next);
        }
    }

    public void playNext() {
        if (actionHandler != null) {
            actionHandler.onPlayNext();
            return;
        }
        Metadata next = queue.next();
        if (next != null) {
            play(next);
        }
    }

    public void playPrevious() {
        if (actionHandler != null) {
            actionHandler.onPlayPrevious();
            return;
        }
        Metadata prev = queue.previous();
        if (prev != null) {
            play(prev);
        }
    }

    public void togglePlayback() {
        if (actionHandler != null) {
            actionHandler.onTogglePlayback();
            return;
        }
        if (music.isPlaying()) {
            music.pause();
        } else {
            music.play();
        }
    }

    public void toggleShuffle() {
        if (actionHandler != null) {
            actionHandler.onToggleShuffle();
            return;
        }
        this.shuffleEnabled = !this.shuffleEnabled;
        queue.setShuffleEnabled(this.shuffleEnabled);
        prefs.saveShuffleModeForContext(currentContextKey, this.shuffleEnabled);
        // Keep legacy global in sync for fallback
        prefs.saveShuffleMode(this.shuffleEnabled);
        shuffleSignal.emit(this.shuffleEnabled);
        saveFullState();
    }

    /**
     * Directly set shuffle for current context – used by per-context switch without toggling.
     */
    public void setShuffleEnabled(boolean enabled) {
        if (actionHandler != null) {
            // Delegate via toggle if handler present? For simplicity handle locally
        }
        this.shuffleEnabled = enabled;
        queue.setShuffleEnabled(enabled);
        prefs.saveShuffleModeForContext(currentContextKey, enabled);
        prefs.saveShuffleMode(enabled);
        shuffleSignal.emit(enabled);
        saveFullState();
    }

    public void cycleRepeatMode() {
        if (actionHandler != null) {
            actionHandler.onCycleRepeatMode();
            return;
        }
        Queue.RepeatMode current = queue.getRepeatMode();
        Queue.RepeatMode next;
        if (current == Queue.RepeatMode.NONE) next = Queue.RepeatMode.ALL;
        else if (current == Queue.RepeatMode.ALL) next = Queue.RepeatMode.ONE;
        else next = Queue.RepeatMode.NONE;

        queue.setRepeatMode(next);
        prefs.saveRepeatModeForContext(currentContextKey, next);
        prefs.saveRepeatMode(next);
        repeatSignal.emit(next);
    }

    public void setRepeatMode(Queue.RepeatMode mode) {
        if (mode == null) return;
        queue.setRepeatMode(mode);
        prefs.saveRepeatModeForContext(currentContextKey, mode);
        prefs.saveRepeatMode(mode);
        repeatSignal.emit(mode);
    }

    public void syncExternalState(boolean isPlaying, boolean shuffle, Queue.RepeatMode repeat, Metadata track, long position, long duration) {
        this.shuffleEnabled = shuffle;
        this.queue.setShuffleEnabledSilently(shuffle);
        this.queue.setRepeatModeSilently(repeat);
        // Also persist for current context if known
        prefs.saveShuffleModeForContext(currentContextKey, shuffle);
        prefs.saveRepeatModeForContext(currentContextKey, repeat);
        
        if (track != null) {
            music.updateMetadataSync(track, position, duration);
        }
        
        shuffleSignal.emit(shuffle);
        repeatSignal.emit(repeat);
        this.isExternallySynced = true;
    }

    // ===== Per-context Playlist handling – Spotify-like =====

    /**
     * Switches current context to the given key and applies its stored shuffle/repeat.
     * Used when starting playback from a specific playlist / library.
     */
    public synchronized void switchContext(String contextKey) {
        if (contextKey == null) return;
        if (contextKey.equals(currentContextKey)) {
            // Even if same, ensure queue reflects stored prefs (in case prefs changed externally)
            boolean s = prefs.getShuffleModeForContext(contextKey);
            Queue.RepeatMode r = prefs.getRepeatModeForContext(contextKey);
            if (s != shuffleEnabled || r != queue.getRepeatMode()) {
                shuffleEnabled = s;
                queue.setShuffleEnabledSilently(s);
                queue.setRepeatModeSilently(r);
                shuffleSignal.emit(s);
                repeatSignal.emit(r);
            }
            return;
        }
        currentContextKey = contextKey;
        boolean newShuffle = prefs.getShuffleModeForContext(contextKey);
        Queue.RepeatMode newRepeat = prefs.getRepeatModeForContext(contextKey);
        shuffleEnabled = newShuffle;
        queue.setShuffleEnabledSilently(newShuffle);
        queue.setRepeatModeSilently(newRepeat);
        shuffleSignal.emit(newShuffle);
        repeatSignal.emit(newRepeat);
        Log.d(TAG, "Switched context to " + contextKey + " shuffle=" + newShuffle + " repeat=" + newRepeat);
    }

    public String getCurrentContextKey() {
        return currentContextKey;
    }

    /**
     * Loads per-context shuffle/repeat and applies to queue before setting playlist.
     */
    private void applyContextForPlaylist(Playlist playlist, long playlistId) {
        String newKey = MediaPrefs.contextKeyFor(playlistId, playlist != null ? playlist.getName() : null);
        boolean newShuffle = prefs.getShuffleModeForContext(newKey);
        Queue.RepeatMode newRepeat = prefs.getRepeatModeForContext(newKey);
        currentContextKey = newKey;
        shuffleEnabled = newShuffle;
        queue.setShuffleEnabledSilently(newShuffle);
        queue.setRepeatModeSilently(newRepeat);
        shuffleSignal.emit(newShuffle);
        repeatSignal.emit(newRepeat);
        Log.d(TAG, "applyContextForPlaylist: key=" + newKey + " shuffle=" + newShuffle + " repeat=" + newRepeat);
    }

    public void setPlaylist(Playlist playlist) {
        // Generic queue context (fallback)
        setPlaylist(playlist, -1L);
    }

    public void setPlaylist(Playlist playlist, long playlistId) {
        if (playlist == null) {
            queue.setPlaylist((Playlist) null);
            saveFullState();
            return;
        }
        applyContextForPlaylist(playlist, playlistId);
        queue.setPlaylist(playlist);
        saveFullState();
    }

    public void setPlaylist(java.util.List<Metadata> items, String playlistName, long playlistId) {
        Playlist p = new Playlist(playlistName != null ? playlistName : "Queue");
        if (items != null) for (Metadata m : items) p.addItem(m);
        setPlaylist(p, playlistId);
    }

    public void setCrossfadeDuration(int seconds) {
        music.setCrossfadeDuration(seconds);
    }

    public void setNormalizationEnabled(boolean enabled) {
        music.setNormalizationEnabled(enabled);
    }

    public void setTargetLoudness(float loudness) {
        music.setTargetLoudness(loudness);
    }

    public Music getMusic() { return music; }
    public Queue getQueue() { return queue; }
    public Context getContext() { return context; }
    public Context getApplicationContext() { return context; }
    public boolean isShuffleEnabled() { return shuffleEnabled; }
    public Signal<Boolean> getShuffleSignal() { return shuffleSignal; }
    public Signal<Queue.RepeatMode> getRepeatSignal() { return repeatSignal; }

    public void setExternallySynced(boolean synced) {
        this.isExternallySynced = synced;
    }

    public void setActionHandler(ActionHandler actionHandler) {
        this.actionHandler = actionHandler;
    }

    public void restoreLastState() {
        java.util.List<String> lastPaths = prefs.getLastPlaylistPaths();
        int lastIndex = prefs.getLastTrackIndex();
        long lastPos = prefs.getLastPosition();
        String lastPlaylistName = prefs.getLastPlaylistName();

        if (lastPaths != null && !lastPaths.isEmpty() && lastIndex >= 0) {
            new Thread(() -> {
                try {
                    Thread.sleep(500); 
                    
                    java.util.List<TrackEntity> tracks = new java.util.ArrayList<>();
                    MusicDatabase db = MusicDatabase.getDatabase(context);
                    for (String path : lastPaths) {
                        TrackEntity t = db.trackDao().getTrackByPath(path);
                        if (t != null) tracks.add(t);
                    }

                    if (!tracks.isEmpty()) {
                        java.util.List<Metadata> metadataList = new java.util.ArrayList<>();
                        for (TrackEntity t : tracks) metadataList.add(convertToMetadata(t));
                        
                        Playlist playlist = new Playlist(lastPlaylistName);
                        for (Metadata m : metadataList) playlist.addItem(m);

                        synchronized (this) {
                            // Restore context for last playlist – infer from name (best effort)
                            String restoreKey = MediaPrefs.contextKeyFor(-1, lastPlaylistName);
                            // If name matches "Library" we get library, else queue/ctx
                            // Ideally we would restore exact playlist id if available, but paths don't contain id.
                            // So we restore via name-based key; for playlists original id unknown, they'll fallback to queue if renamed?
                            // Better to try to find playlist entity by name to get id
                            try {
                                java.util.List<xime.media.entity.PlaylistEntity> all = db.playlistDao().getAllPlaylistsSync();
                                for (xime.media.entity.PlaylistEntity pe : all) {
                                    if (pe.getName() != null && pe.getName().equals(lastPlaylistName)) {
                                        restoreKey = MediaPrefs.contextKeyFor(pe.getId(), pe.getName());
                                        break;
                                    }
                                }
                            } catch (Exception ignored) {}

                            boolean restoreShuffle = prefs.getShuffleModeForContext(restoreKey);
                            Queue.RepeatMode restoreRepeat = prefs.getRepeatModeForContext(restoreKey);
                            currentContextKey = restoreKey;
                            shuffleEnabled = restoreShuffle;
                            queue.setShuffleEnabledSilently(restoreShuffle);
                            queue.setRepeatModeSilently(restoreRepeat);
                            shuffleSignal.emit(restoreShuffle);
                            repeatSignal.emit(restoreRepeat);

                            queue.setPlaylist(playlist);
                            int indexToJump = Math.min(lastIndex, metadataList.size() - 1);
                            queue.jumpTo(indexToJump);
                            
                            Metadata current = metadataList.get(indexToJump);
                            // Restore state with saved position and metadata
                            music.updateMetadataSync(current, lastPos, current.getDurationMs());
                            music.seekTo(lastPos);
                        }
                        
                        // Force a sync to the service list
                        android.content.Intent syncIntent = new android.content.Intent("com.xasm.elarion.musify.SYNC_PLAYLIST");
                        syncIntent.putExtra("paths", (java.io.Serializable) lastPaths);
                        androidx.localbroadcastmanager.content.LocalBroadcastManager.getInstance(context).sendBroadcast(syncIntent);
                    }
                } catch (Exception e) {
                    Log.e(TAG, "Failed to restore last state", e);
                }
            }, "RestoreStateThread").start();
        }
    }

    public Metadata convertToMetadata(TrackEntity trackEntity) {
        if (trackEntity == null) return null;

        Uri mediaUri;
        if (trackEntity.getId() > 0) {
            mediaUri = ContentUris.withAppendedId(
                    MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, trackEntity.getId());
        } else {
            String path = trackEntity.getPath();
            mediaUri = path != null ? Uri.fromFile(new File(path)) : null;
        }

        // Prefer AlbumArt (MediaStore thumbnail URI) for massive performance gains during scrolling.
        // Fallback to TrackArt (raw MP3 path) if AlbumArt is unavailable.
        String artPath = trackEntity.getAlbumArt() != null ? trackEntity.getAlbumArt() : trackEntity.getTrackArt();

        return new Metadata.Builder()
                .setTitle(trackEntity.getTitle())
                .setArtist(trackEntity.getArtist())
                .setAlbum(trackEntity.getAlbum())
                .setDuration(trackEntity.getDuration())
                .setPath(trackEntity.getPath())
                .setLyrics(trackEntity.getLyrics())
                .setMediaUri(mediaUri)
                .setArtUri(artPath != null ? Uri.parse(artPath) : null)
                .build();
    }
}
