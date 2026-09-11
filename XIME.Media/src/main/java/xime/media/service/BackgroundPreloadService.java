package xime.media.service;

import android.app.Service;
import android.content.Intent;
import android.os.Binder;
import android.os.IBinder;

import androidx.annotation.Nullable;

import xime.media.entity.TrackEntity;
import xime.media.music.manager.AdvancedMusicCacheManager;
import xime.media.utils.AsyncAlbumArtExtractorUtils;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicBoolean;

public class BackgroundPreloadService extends Service {
    
    private static final String TAG = "BackgroundPreloadService";
    private static final int PRELOAD_BUFFER_SIZE = 5;
    
    private final IBinder binder = new PreloadBinder();
    private ExecutorService preloadExecutor;
    private AdvancedMusicCacheManager cacheManager;
    
    private final AtomicBoolean isPreloading = new AtomicBoolean(false);
    private Future<?> currentPreloadTask;
    
    public class PreloadBinder extends Binder {
        public BackgroundPreloadService getService() {
            return BackgroundPreloadService.this;
        }
    }
    
    @Override
    public void onCreate() {
        super.onCreate();
        preloadExecutor = Executors.newFixedThreadPool(2);
        cacheManager = new AdvancedMusicCacheManager(this);
    }
    
    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return binder;
    }
    
    public void startPreloading(TrackEntity currentTrackEntity, List<TrackEntity> playlist, int currentIndex) {
        if (isPreloading.get() && currentPreloadTask != null) {
            currentPreloadTask.cancel(true);
        }
        
        isPreloading.set(true);
        currentPreloadTask = preloadExecutor.submit(() -> {
            try {
                preloadAround(currentTrackEntity, playlist, currentIndex);
            } finally {
                isPreloading.set(false);
            }
        });
    }
    
    private void preloadAround(TrackEntity currentTrackEntity, List<TrackEntity> playlist, int currentIndex) {
        if (playlist == null || playlist.isEmpty()) return;
        
        preloadAlbumArt(currentTrackEntity);
        
        for (int i = 1; i <= PRELOAD_BUFFER_SIZE; i++) {
            if (Thread.currentThread().isInterrupted()) return;
            
            if (currentIndex + i < playlist.size()) {
                preloadTrack(playlist.get(currentIndex + i));
            }
            if (currentIndex - i >= 0) {
                preloadTrack(playlist.get(currentIndex - i));
            }
        }
    }
    
    private void preloadTrack(TrackEntity trackEntity) {
        cacheManager.saveTracksAsync(java.util.Arrays.asList(trackEntity), null);
        preloadAlbumArt(trackEntity);
    }
    
    private void preloadAlbumArt(TrackEntity trackEntity) {
        if (trackEntity.getAlbumArt() != null && !trackEntity.getAlbumArt().isEmpty()) return;
        
        AsyncAlbumArtExtractorUtils.extractAlbumArtAsync(this, trackEntity.getPath(), trackEntity.getAlbum(), trackEntity.getArtist())
            .thenAccept(result -> {
                if (result.success && result.artFile != null) {
                    trackEntity.setAlbumArt(result.artFile.getAbsolutePath());
                    cacheManager.saveTracksAsync(java.util.Arrays.asList(trackEntity), null);
                }
            });
    }

    @Override
    public void onDestroy() {
        if (preloadExecutor != null) preloadExecutor.shutdownNow();
        if (cacheManager != null) cacheManager.shutdown();
        super.onDestroy();
    }
}

