package xime.media.music.service;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.graphics.Bitmap;
import android.os.Binder;
import android.os.Build;
import android.os.IBinder;
import android.util.Log;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import xime.media.entity.TrackEntity;
import xime.media.music.Music;
import xime.media.music.MusicEngine;
import xime.media.Metadata;
import xime.media.music.manager.MusicManager;
import xime.media.Playlist;
import xime.media.Queue;
import xime.media.Session;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

public abstract class MusicService extends Service {

    private static final String TAG = "MusicService";
    private static final String CHANNEL_ID = "music_playback_channel";
    private static final int NOTIFICATION_ID = 1;

    public static final String ACTION_PLAYBACK_STATE_CHANGED = "xime.media.PLAYBACK_STATE_CHANGED";
    public static final String ACTION_TRACK_CHANGED = "xime.media.TRACK_CHANGED";
    public static final String ACTION_POSITION_CHANGED = "xime.media.POSITION_CHANGED";
    
    public static final String EXTRA_IS_PLAYING = "is_playing";
    public static final String EXTRA_TRACK = "track";
    public static final String EXTRA_POSITION = "position";
    public static final String EXTRA_DURATION = "duration";
    public static final String EXTRA_TRACK_INDEX = "track_index";

    protected MusicEngine libEngine;
    public Music musicSphere;
    protected Session sessionSphere;
    protected NotificationManager notificationManager;
    
    protected List<TrackEntity> currentPlaylist = new ArrayList<>();
    protected List<Metadata> currentMetadataList = new ArrayList<>();
    protected TrackEntity currentTrackEntity;
    protected int currentTrackIndex = -1;
    protected int currentQueueIndex = -1;
    protected long currentPlaylistId = -1;
    private int mAdditionalServiceTypes = 0;

    private final MusicBinder binder = new MusicBinder();

    public class MusicBinder extends Binder {
        public MusicService getService() { return MusicService.this; }

        public void setAdditionalForegroundServiceTypes(int types) {
            mAdditionalServiceTypes = types;
            updateNotification();
        }

        public void play() { musicSphere.play(); }
        public void pause() { musicSphere.pause(); }
        public boolean isPlaying() { return musicSphere.isPlaying(); }
        
        public void playTracks(List<TrackEntity> trackEntityEntities, int startIndex) {
            playTracks(trackEntityEntities, startIndex, "Queue", -1);
        }

        public void playTracks(List<TrackEntity> trackEntityEntities, int startIndex, String playlistName, long playlistId) {
            if (trackEntityEntities == null || trackEntityEntities.isEmpty()) return;
            currentPlaylist.clear();
            currentPlaylist.addAll(trackEntityEntities);
            currentMetadataList.clear();
            currentTrackIndex = startIndex;
            currentTrackEntity = trackEntityEntities.get(startIndex);
            currentPlaylistId = playlistId;

            Playlist libPlaylist = new Playlist(playlistName);
            MusicManager mm = MusicManager.getInstance(MusicService.this);
            for (TrackEntity t : trackEntityEntities) {
                Metadata ms = mm.convertToMetadata(t);
                libPlaylist.addItem(ms);
                currentMetadataList.add(ms);
            }

            // Per-context (per-playlist / per-library) shuffle & repeat – Spotify-like
            // Switch engine context before setting playlist so queue shuffles with correct mode
            libEngine.setPlaylist(libPlaylist, playlistId);
            musicSphere.getQueue().jumpTo(startIndex);
            
            Metadata currentMetadata = libPlaylist.getItems().get(startIndex);
            musicSphere.play(currentMetadata);
            
            updateNotification();
        }

        public void playNext() { libEngine.playNext(); }
        public void playPrevious() { libEngine.playPrevious(); }
        public void seekTo(long position) { musicSphere.seekTo((int) position); }
        
        public void toggleShuffle() { libEngine.toggleShuffle(); }
        public void toggleRepeatMode() { libEngine.cycleRepeatMode(); }
        
        public TrackEntity getCurrentTrack() { return currentTrackEntity; }
        public int getCurrentTrackIndex() { return currentTrackIndex; }
        public int getCurrentQueueIndex() { return currentQueueIndex; }
        public long getCurrentPlaylistId() { return currentPlaylistId; }
        public List<TrackEntity> getCurrentPlaylist() {
            List<Metadata> activeQueue = musicSphere.getQueue().getActiveQueue();
            List<TrackEntity> result = new ArrayList<>();
            for (Metadata ms : activeQueue) {
                for (TrackEntity te : currentPlaylist) {
                    if (te.getPath().equals(ms.getPath())) {
                        result.add(te);
                        break;
                    }
                }
            }
            return result;
        }
        public boolean isShuffleEnabled() { return libEngine.isShuffleEnabled(); }
        public Queue.RepeatMode getRepeatMode() { return musicSphere.getQueue().getRepeatMode(); }

        public void moveTrack(int from, int to) {
            musicSphere.getQueue().move(from, to);
            broadcastState();
        }

        public void removeTrack(int position) {
            List<Metadata> active = musicSphere.getQueue().getActiveQueue();
            if (position >= 0 && position < active.size()) {
                Metadata ms = active.get(position);
                // Remove from original list too to keep them in sync
                for (int i = 0; i < currentPlaylist.size(); i++) {
                    if (currentPlaylist.get(i).getPath().equals(ms.getPath())) {
                        currentPlaylist.remove(i);
                        break;
                    }
                }
                musicSphere.getQueue().remove(position);
                broadcastState();
            }
        }
    }

    @Override
    public void onCreate() {
        super.onCreate();
        libEngine = MusicEngine.getInstance(this);
        musicSphere = libEngine.getMusic();
        notificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        
        createNotificationChannel();
        initializeMediaSession();
        setupMusicSphereListeners();
        
        // Restore last playback state (crash recovery)
        libEngine.restoreLastState();
    }

    private void initializeMediaSession() {
        sessionSphere = new Session(this, "xoeris_music", new Session.Callback() {
            @Override public void onPlay() { binder.play(); }
            @Override public void onPause() { binder.pause(); }
            @Override public void onSkipToNext() { binder.playNext(); }
            @Override public void onSkipToPrevious() { binder.playPrevious(); }
            @Override public void onSeekTo(long pos) { binder.seekTo(pos); }
            @Override public void onStop() { musicSphere.stop(); }
        });
        
        sessionSphere.setSessionActivity(getSessionActivityIntent());
    }

    protected abstract PendingIntent getSessionActivityIntent();
    protected abstract int getSmallIconResId();
    protected abstract int getPlayIconResId();
    protected abstract int getPauseIconResId();
    protected abstract int getNextIconResId();
    protected abstract int getPreviousIconResId();

    private long lastMediaSessionSyncTime = 0;
    private long lastMediaSessionSyncPosition = -1;

    protected void setupMusicSphereListeners() {
        LocalBroadcastManager.getInstance(this).registerReceiver(new android.content.BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                if ("com.xasm.elarion.musify.SYNC_PLAYLIST".equals(intent.getAction())) {
                    java.util.List<String> paths = (java.util.List<String>) intent.getSerializableExtra("paths");
                    if (paths != null) {
                        new Thread(() -> {
                            java.util.List<TrackEntity> tracks = new java.util.ArrayList<>();
                            xime.media.database.MusicDatabase db = xime.media.database.MusicDatabase.getDatabase(context);
                            for (String p : paths) {
                                TrackEntity t = db.trackDao().getTrackByPath(p);
                                if (t != null) tracks.add(t);
                            }
                            new android.os.Handler(android.os.Looper.getMainLooper()).post(() -> {
                                currentPlaylist.clear();
                                currentPlaylist.addAll(tracks);
                                if (musicSphere.getCurrentMetadata() != null) {
                                    syncCurrentTrack(musicSphere.getCurrentMetadata());
                                }
                            });
                        }).start();
                    }
                }
            }
        }, new android.content.IntentFilter("com.xasm.elarion.musify.SYNC_PLAYLIST"));

        musicSphere.getStateSignal().observe(state -> {
            updateNotification();
            broadcastState();
            // Force MediaSession state sync on state change
            if (sessionSphere != null) {
                long pos = musicSphere.getProgressSignal().getLastEvent() != null ? musicSphere.getProgressSignal().getLastEvent().position : 0;
                sessionSphere.updatePlaybackState(musicSphere.isPlaying(), pos);
                lastMediaSessionSyncTime = System.currentTimeMillis();
                lastMediaSessionSyncPosition = pos;
            }
        });
        
        musicSphere.getMetadataSignal().observe(metadata -> {
            syncCurrentTrack(metadata);
            updateNotification();
            broadcastState();
            // Reset position in MediaSession on track change
            if (sessionSphere != null) {
                sessionSphere.updateMetadata(metadata, null);
                long pos = musicSphere.getProgressSignal().getLastEvent() != null ? musicSphere.getProgressSignal().getLastEvent().position : 0;
                sessionSphere.updatePlaybackState(musicSphere.isPlaying(), pos);
                lastMediaSessionSyncTime = System.currentTimeMillis();
                lastMediaSessionSyncPosition = pos;
            }
        });
        
        musicSphere.getProgressSignal().observe(progress -> {
            broadcastPosition(progress.position, progress.duration);
            // Throttle IPC to MediaSession to prevent UI thread lag (once every 10 seconds)
            if (sessionSphere != null) {
                long now = System.currentTimeMillis();
                boolean isSignificantChange = Math.abs(progress.position - lastMediaSessionSyncPosition) > 1500;
                if (!musicSphere.isPlaying() || isSignificantChange || (now - lastMediaSessionSyncTime > 10000)) {
                    sessionSphere.updatePlaybackState(musicSphere.isPlaying(), progress.position);
                    lastMediaSessionSyncTime = now;
                    lastMediaSessionSyncPosition = progress.position;
                }
            }
        });
    }

    protected void syncCurrentTrack(Metadata metadata) {
        if (metadata == null) {
            currentTrackEntity = null;
            currentTrackIndex = -1;
            currentQueueIndex = -1;
            return;
        }

        currentQueueIndex = musicSphere.getQueue().getCurrentIndex();

        // 1. Precise Match: Search by object identity in current session's metadata list
        for (int i = 0; i < currentMetadataList.size(); i++) {
            if (currentMetadataList.get(i) == metadata) {
                currentTrackEntity = currentPlaylist.get(i);
                currentTrackIndex = i;
                return;
            }
        }

        // 2. Loose Match: Search by path (fallback for restored states or external insertions)
        for (int i = 0; i < currentPlaylist.size(); i++) {
            if (currentPlaylist.get(i).getPath().equals(metadata.getPath())) {
                currentTrackEntity = currentPlaylist.get(i);
                currentTrackIndex = i;
                return;
            }
        }

        // Fallback for restored tracks or tracks outside the current playlist
        currentTrackEntity = new TrackEntity();
        currentTrackEntity.setTitle(metadata.getTitle());
        currentTrackEntity.setArtist(metadata.getArtist());
        currentTrackEntity.setAlbum(metadata.getAlbumName());
        currentTrackEntity.setPath(metadata.getPath());
        currentTrackEntity.setDuration(metadata.getDurationMs());
        currentTrackEntity.setTrackArt(metadata.getArtUri());
        currentTrackEntity.setAlbumArt(metadata.getArtUri());
        currentTrackIndex = -1;
    }

    protected void updateNotification() {
        if (currentTrackEntity == null) return;
        
        // Handle artwork loading - can be overridden by app for optimization (e.g. Glide)
        loadArtwork(currentTrackEntity, new ArtworkCallback() {
            @Override
            public void onArtworkLoaded(@Nullable Bitmap bitmap) {
                Notification notification = createNotification(currentTrackEntity, bitmap);
                
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    int type = ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK;
                    type |= mAdditionalServiceTypes;
                    try {
                        startForeground(NOTIFICATION_ID, notification, type);
                    } catch (SecurityException e) {
                        Log.e(TAG, "Failed to start foreground with requested types: " + type + ". Falling back to mediaPlayback only.", e);
                        // Fallback: remove additional types and try again
                        mAdditionalServiceTypes = 0;
                        startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK);
                    }
                } else {
                    startForeground(NOTIFICATION_ID, notification);
                }
                
                sessionSphere.updateMetadata(musicSphere.getCurrentMetadata(), bitmap);
                sessionSphere.updatePlaybackState(musicSphere.isPlaying(), musicSphere.getProgressSignal().getLastEvent() != null ? musicSphere.getProgressSignal().getLastEvent().position : 0);
            }
        });
    }

    protected interface ArtworkCallback {
        void onArtworkLoaded(@Nullable Bitmap bitmap);
    }

    /**
     * Apps should override this to use their image loading library (Glide, Coil, etc.)
     */
    protected void loadArtwork(TrackEntity trackEntity, ArtworkCallback callback) {
        callback.onArtworkLoaded(null);
    }

    private Notification createNotification(TrackEntity trackEntity, @Nullable Bitmap artwork) {
        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle(trackEntity.getTitle())
                .setContentText(trackEntity.getArtist())
                .setSmallIcon(getSmallIconResId())
                .setLargeIcon(artwork)
                .setContentIntent(getSessionActivityIntent())
                .setOngoing(musicSphere.isPlaying())
                .setCategory(NotificationCompat.CATEGORY_TRANSPORT)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .setStyle(new androidx.media.app.NotificationCompat.MediaStyle()
                        .setMediaSession(sessionSphere.getSessionToken())
                        .setShowActionsInCompactView(0, 1, 2));

        builder.addAction(getPreviousIconResId(), "Previous", createPlaybackIntent("PREVIOUS"));
        if (musicSphere.isPlaying()) {
            builder.addAction(getPauseIconResId(), "Pause", createPlaybackIntent("PAUSE"));
        } else {
            builder.addAction(getPlayIconResId(), "Play", createPlaybackIntent("PLAY"));
        }
        builder.addAction(getNextIconResId(), "Next", createPlaybackIntent("NEXT"));

        return builder.build();
    }

    private PendingIntent createPlaybackIntent(String action) {
        Intent intent = new Intent(this, this.getClass());
        intent.setAction(action);
        return PendingIntent.getService(this, 0, intent, PendingIntent.FLAG_IMMUTABLE);
    }

    protected void broadcastState() {
        Intent intent = new Intent(ACTION_PLAYBACK_STATE_CHANGED);
        intent.putExtra(EXTRA_IS_PLAYING, musicSphere.isPlaying());
        intent.putExtra(EXTRA_TRACK, (Serializable) currentTrackEntity);
        intent.putExtra(EXTRA_TRACK_INDEX, currentTrackIndex);
        LocalBroadcastManager.getInstance(this).sendBroadcast(intent);
    }

    protected void broadcastPosition(long position, long duration) {
        Intent intent = new Intent(ACTION_POSITION_CHANGED);
        intent.putExtra(EXTRA_POSITION, position);
        intent.putExtra(EXTRA_DURATION, duration);
        LocalBroadcastManager.getInstance(this).sendBroadcast(intent);
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(CHANNEL_ID, "Music Playback", NotificationManager.IMPORTANCE_LOW);
            notificationManager.createNotificationChannel(channel);
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && intent.getAction() != null) {
            switch (intent.getAction()) {
                case "PLAY": binder.play(); break;
                case "PAUSE": binder.pause(); break;
                case "NEXT": binder.playNext(); break;
                case "PREVIOUS": binder.playPrevious(); break;
            }
        }
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        if (sessionSphere != null) sessionSphere.release();
        super.onDestroy();
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return binder;
    }
}
