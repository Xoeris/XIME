package xime.media.music.manager;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import xime.media.entity.TrackEntity;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MusicCacheManager {
    
    private static final String TAG = "MusicCacheManager";
    private static final String CACHE_FILE_NAME = "music_cache.json";
    private static final String PREFS_NAME = "music_cache_prefs";
    private static final String KEY_LAST_SCAN_TIME = "last_scan_time";
    private static final String KEY_CACHE_VERSION = "cache_version";
    private static final int CURRENT_CACHE_VERSION = 1;
    
    private final Context context;
    private final Gson gson;
    private final SharedPreferences prefs;
    private final File cacheFile;
    private final ExecutorService ioExecutor;
    
    // Cache data
    private Map<String, CachedTrackInfo> cachedTracks;
    private boolean cacheLoaded = false;
    
    public MusicCacheManager(Context context) {
        this.context = context;
        this.gson = new Gson();
        this.prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        this.cacheFile = new File(context.getFilesDir(), CACHE_FILE_NAME);
        this.cachedTracks = new HashMap<>();
        this.ioExecutor = Executors.newSingleThreadExecutor();
    }
    
    public CompletableFuture<List<TrackEntity>> loadCachedTracksAsync() {
        return CompletableFuture.supplyAsync(() -> {
            synchronized (this) {
                if (!cacheLoaded) {
                    loadCacheFromFile();
                }
                
                List<TrackEntity> validTrackEntityEntities = new ArrayList<>();
                for (CachedTrackInfo info : cachedTracks.values()) {
                    String path = info.trackEntity.getPath();
                    if (path != null && new File(path).exists()) {
                        validTrackEntityEntities.add(info.trackEntity);
                    }
                }
                
                return validTrackEntityEntities;
            }
        }, ioExecutor);
    }
    
    /**
     * Loads cached tracks synchronously (for backward compatibility)
     */
    public synchronized List<TrackEntity> loadCachedTracks() {
        if (!cacheLoaded) {
            loadCacheFromFile();
        }
        
        List<TrackEntity> validTrackEntityEntities = new ArrayList<>();
        for (CachedTrackInfo info : cachedTracks.values()) {
            String path = info.trackEntity.getPath();
            if (path != null && new File(path).exists()) {
                validTrackEntityEntities.add(info.trackEntity);
            }
        }
        
        return validTrackEntityEntities;
    }
    
    /**
     * Saves trackEntityEntities to manager asynchronously
     */
    public CompletableFuture<Void> saveTracksAsync(List<TrackEntity> trackEntityEntities) {
        return CompletableFuture.runAsync(() -> {
            synchronized (this) {
                cachedTracks.clear();
                
                for (TrackEntity trackEntity : trackEntityEntities) {
                    String path = trackEntity.getPath();
                    if (path == null) continue;

                    File file = new File(path);
                    CachedTrackInfo cachedInfo = new CachedTrackInfo();
                    cachedInfo.trackEntity = trackEntity;
                    cachedInfo.lastModified = file.lastModified();
                    cachedInfo.fileSize = file.length();
                    
                    cachedTracks.put(path, cachedInfo);
                }
                
                saveCacheToFile();
                updateLastScanTime();
                
                Log.d(TAG, "Saved " + trackEntityEntities.size() + " trackEntityEntities to manager asynchronously");
            }
        }, ioExecutor);
    }
    
    /**
     * Saves trackEntityEntities to manager synchronously (for backward compatibility)
     */
    public synchronized void saveTracks(List<TrackEntity> trackEntityEntities) {
        cachedTracks.clear();
        
        for (TrackEntity trackEntity : trackEntityEntities) {
            String path = trackEntity.getPath();
            if (path == null) continue;

            File file = new File(path);
            CachedTrackInfo cachedInfo = new CachedTrackInfo();
            cachedInfo.trackEntity = trackEntity;
            cachedInfo.lastModified = file.lastModified();
            cachedInfo.fileSize = file.length();
            
            cachedTracks.put(path, cachedInfo);
        }
        
        saveCacheToFile();
        updateLastScanTime();
        
        Log.d(TAG, "Saved " + trackEntityEntities.size() + " trackEntityEntities to manager");
    }
    
    /**
     * Checks if a file has been modified since last manager
     */
    public synchronized boolean isFileModified(String filePath) {
        if (!cacheLoaded) {
            loadCacheFromFile();
        }
        
        CachedTrackInfo cachedInfo = cachedTracks.get(filePath);
        if (cachedInfo == null) {
            return true; // New file
        }
        
        File file = new File(filePath);
        return !file.exists() || 
               file.lastModified() != cachedInfo.lastModified || 
               file.length() != cachedInfo.fileSize;
    }
    
    /**
     * Gets list of cached file paths for quick lookup
     */
    public synchronized List<String> getCachedFilePaths() {
        if (!cacheLoaded) {
            loadCacheFromFile();
        }
        
        return new ArrayList<>(cachedTracks.keySet());
    }
    
    /**
     * Removes deleted files from manager
     */
    public synchronized void cleanupDeletedFiles() {
        if (!cacheLoaded) {
            loadCacheFromFile();
        }
        
        List<String> toRemove = new ArrayList<>();
        for (String path : cachedTracks.keySet()) {
            if (!new File(path).exists()) {
                toRemove.add(path);
            }
        }
        
        for (String path : toRemove) {
            cachedTracks.remove(path);
        }
        
        if (!toRemove.isEmpty()) {
            saveCacheToFile();
            Log.d(TAG, "Cleaned up " + toRemove.size() + " deleted files from manager");
        }
    }
    
    /**
     * Checks if manager exists and is valid
     */
    public boolean isCacheValid() {
        return cacheFile.exists() && 
               prefs.getInt(KEY_CACHE_VERSION, 0) == CURRENT_CACHE_VERSION;
    }
    
    /**
     * Gets the last scan time
     */
    public long getLastScanTime() {
        return prefs.getLong(KEY_LAST_SCAN_TIME, 0);
    }
    
    /**
     * Clears all cached data asynchronously
     */
    public CompletableFuture<Void> clearCacheAsync() {
        return CompletableFuture.runAsync(() -> {
            synchronized (this) {
                cachedTracks.clear();
                if (cacheFile.exists()) {
                    if (!cacheFile.delete()) {
                        Log.w(TAG, "Failed to delete manager file");
                    }
                }
                prefs.edit().clear().apply();
                cacheLoaded = false;
                
                Log.d(TAG, "Cache cleared asynchronously");
            }
        }, ioExecutor);
    }
    
    /**
     * Clears all cached data synchronously (for backward compatibility)
     */
    public synchronized void clearCache() {
        cachedTracks.clear();
        if (cacheFile.exists()) {
            if (!cacheFile.delete()) {
                Log.w(TAG, "Failed to delete manager file");
            }
        }
        prefs.edit().clear().apply();
        cacheLoaded = false;
        
        Log.d(TAG, "Cache cleared");
    }
    
    private void loadCacheFromFile() {
        try {
            if (cacheFile.exists()) {
                try (FileReader reader = new FileReader(cacheFile)) {
                    Type type = new TypeToken<Map<String, CachedTrackInfo>>(){}.getType();
                    Map<String, CachedTrackInfo> loadedCache = gson.fromJson(reader, type);
                    
                    if (loadedCache != null) {
                        cachedTracks = loadedCache;
                        Log.d(TAG, "Loaded manager from file with " + cachedTracks.size() + " entries");
                    }
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Error loading manager from file", e);
            cachedTracks.clear();
        }
        
        cacheLoaded = true;
    }
    
    private void saveCacheToFile() {
        try {
            try (FileWriter writer = new FileWriter(cacheFile)) {
                gson.toJson(cachedTracks, writer);
                
                // Update manager version
                prefs.edit()
                    .putInt(KEY_CACHE_VERSION, CURRENT_CACHE_VERSION)
                    .apply();
                
                Log.d(TAG, "Saved manager to file");
            }
        } catch (IOException e) {
            Log.e(TAG, "Error saving manager to file", e);
        }
    }
    
    /**
     * Cleanup method to shutdown executor service
     */
    public void shutdown() {
        if (ioExecutor != null && !ioExecutor.isShutdown()) {
            ioExecutor.shutdown();
            Log.d(TAG, "MusicCacheManager executor shutdown");
        }
    }
    
    private void updateLastScanTime() {
        prefs.edit()
            .putLong(KEY_LAST_SCAN_TIME, System.currentTimeMillis())
            .apply();
    }
    
    /**
     * Internal class to store cached trackEntity information
     */
    private static class CachedTrackInfo {
        TrackEntity trackEntity;
        long lastModified;
        long fileSize;
    }
}

