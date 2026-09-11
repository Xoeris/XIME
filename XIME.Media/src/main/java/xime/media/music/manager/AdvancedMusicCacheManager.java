package xime.media.music.manager;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.util.LruCache;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import xime.media.entity.TrackEntity;
import xime.media.utils.AsyncAlbumArtExtractorUtils;

import java.io.File;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class AdvancedMusicCacheManager {
    
    private static final String TAG = "AdvancedMusicCache";
    private static final String CACHE_FILE_NAME = "advanced_music_cache.json";
    private static final String PREFS_NAME = "advanced_music_cache_prefs";
    private static final String KEY_LAST_SCAN_TIME = "last_scan_time";
    private static final String KEY_CACHE_VERSION = "cache_version";
    private static final int CURRENT_CACHE_VERSION = 2;
    
    // Cache sizes
    private static final int MEMORY_CACHE_SIZE = 50 * 1024 * 1024; // 50MB for album art
    private static final int TRACK_MEMORY_CACHE_SIZE = 1000; // Keep 1000 tracks in memory
    private static final int PRELOAD_BATCH_SIZE = 50; // Preload 50 tracks at a time
    
    private final Gson gson;
    private final SharedPreferences prefs;
    private final File cacheFile;
    private final File albumArtCacheDir;
    
    // Multi-level caching
    private final LruCache<String, Bitmap> albumArtMemoryCache;
    private final LruCache<String, TrackEntity> trackMemoryCache;
    private final ConcurrentHashMap<String, CachedTrackInfo> diskCache;
    
    // Threading
    private final ExecutorService cacheExecutor;
    private final ExecutorService preloadExecutor;
    private final Handler mainHandler;
    
    // State
    private volatile boolean cacheLoaded = false;
    private volatile boolean isPreloading = false;
    private volatile boolean isShutdown = false;
    
    public AdvancedMusicCacheManager(Context context) {
        this.gson = new Gson();
        this.prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        this.cacheFile = new File(context.getFilesDir(), CACHE_FILE_NAME);
        this.albumArtCacheDir = new File(context.getCacheDir(), "advanced_album_art");
        this.diskCache = new ConcurrentHashMap<>();
        this.mainHandler = new Handler(Looper.getMainLooper());
        
        // Create manager directories
        if (!albumArtCacheDir.exists()) {
            albumArtCacheDir.mkdirs();
        }
        
        // Initialize thread pools
        this.cacheExecutor = Executors.newFixedThreadPool(2);
        this.preloadExecutor = Executors.newFixedThreadPool(3);
        
        // Initialize memory caches
        this.albumArtMemoryCache = new LruCache<String, Bitmap>(MEMORY_CACHE_SIZE) {
            @Override
            protected int sizeOf(String key, Bitmap value) {
                return value.getByteCount();
            }

            @Override
            protected void entryRemoved(boolean evicted, String key, Bitmap oldValue, Bitmap newValue) {
                if (evicted && oldValue != null && !oldValue.isRecycled()) {
                    oldValue.recycle();
                }
            }
        };
        
        this.trackMemoryCache = new LruCache<>(TRACK_MEMORY_CACHE_SIZE);
        
        // Load disk cache into memory asynchronously
        loadCacheFromDisk();
    }

    private void loadCacheFromDisk() {
        cacheExecutor.execute(() -> {
            synchronized (diskCache) {
                if (cacheFile.exists()) {
                    try (FileReader reader = new FileReader(cacheFile)) {
                        Type type = new TypeToken<ConcurrentHashMap<String, CachedTrackInfo>>() {}.getType();
                        ConcurrentHashMap<String, CachedTrackInfo> loaded = gson.fromJson(reader, type);
                        if (loaded != null) {
                            diskCache.putAll(loaded);
                        }
                        cacheLoaded = true;
                        Log.d(TAG, "Cache loaded from disk: " + diskCache.size() + " tracks");
                    } catch (Exception e) {
                        Log.e(TAG, "Error loading cache from disk", e);
                        cacheFile.delete(); // Corrupt cache
                    }
                } else {
                    cacheLoaded = true;
                }
            }
        });
    }

    public void getTracksAsync(int offset, int limit, TrackCacheCallback callback) {
        cacheExecutor.execute(() -> {
            try {
                ensureCacheLoaded();
                List<TrackEntity> tracks = new ArrayList<>();
                List<String> allPaths = new ArrayList<>(diskCache.keySet());
                
                int start = Math.min(offset, allPaths.size());
                int end = Math.min(offset + limit, allPaths.size());
                
                for (int i = start; i < end; i++) {
                    TrackEntity t = getTrackFromCache(allPaths.get(i));
                    if (t != null) tracks.add(t);
                }
                
                // Predictive preloading
                if (!isPreloading && end < allPaths.size()) {
                    startPreloading(end, Math.min(end + PRELOAD_BATCH_SIZE, allPaths.size()));
                }

                mainHandler.post(() -> callback.onTracksLoaded(tracks, offset, tracks.size()));
            } catch (Exception e) {
                mainHandler.post(() -> callback.onError(e));
            }
        });
    }

    public void getAlbumArtAsync(String trackPath, String artKey, AlbumArtCallback callback) {
        Bitmap cached = albumArtMemoryCache.get(artKey);
        if (cached != null) {
            callback.onAlbumArtLoaded(cached, true);
            return;
        }

        preloadExecutor.execute(() -> {
            try {
                // Try L3 - Disk Art
                File artFile = new File(albumArtCacheDir, artKey + ".jpg");
                if (artFile.exists()) {
                    Bitmap diskBitmap = BitmapFactory.decodeFile(artFile.getAbsolutePath());
                    if (diskBitmap != null) {
                        albumArtMemoryCache.put(artKey, diskBitmap);
                        mainHandler.post(() -> callback.onAlbumArtLoaded(diskBitmap, false));
                        return;
                    }
                }

                // Try L4 - Extraction
                Bitmap extracted = AsyncAlbumArtExtractorUtils.extractAlbumArtFromTrack(trackPath);
                if (extracted != null) {
                    saveAlbumArtToDisk(artKey, extracted);
                    albumArtMemoryCache.put(artKey, extracted);
                    mainHandler.post(() -> callback.onAlbumArtLoaded(extracted, false));
                } else {
                    mainHandler.post(() -> callback.onError(new Exception("No artwork found")));
                }
            } catch (Exception e) {
                mainHandler.post(() -> callback.onError(e));
            }
        });
    }

    public void saveTracksAsync(List<TrackEntity> tracks, CacheOperationCallback callback) {
        cacheExecutor.execute(() -> {
            try {
                for (TrackEntity t : tracks) {
                    if (t.getPath() != null) {
                        diskCache.put(t.getPath(), new CachedTrackInfo(t));
                        trackMemoryCache.put(t.getPath(), t);
                    }
                }
                saveCacheToFile();
                if (callback != null) mainHandler.post(callback::onSuccess);
            } catch (Exception e) {
                if (callback != null) mainHandler.post(() -> callback.onError(e));
            }
        });
    }

    public void updateTrackAsync(TrackEntity track, CacheOperationCallback callback) {
        cacheExecutor.execute(() -> {
            try {
                if (track.getPath() != null) {
                    diskCache.put(track.getPath(), new CachedTrackInfo(track));
                    trackMemoryCache.put(track.getPath(), track);
                }
                saveCacheToFile();
                if (callback != null) mainHandler.post(callback::onSuccess);
            } catch (Exception e) {
                if (callback != null) mainHandler.post(() -> callback.onError(e));
            }
        });
    }

    public void removeTrackAsync(String path, CacheOperationCallback callback) {
        cacheExecutor.execute(() -> {
            try {
                if (path != null) {
                    diskCache.remove(path);
                    trackMemoryCache.remove(path);
                }
                saveCacheToFile();
                if (callback != null) mainHandler.post(callback::onSuccess);
            } catch (Exception e) {
                if (callback != null) mainHandler.post(() -> callback.onError(e));
            }
        });
    }

    private void startPreloading(int start, int end) {
        isPreloading = true;
        preloadExecutor.execute(() -> {
            try {
                List<String> allPaths = new ArrayList<>(diskCache.keySet());
                for (int i = start; i < end && !isShutdown; i++) {
                    String path = allPaths.get(i);
                    TrackEntity t = getTrackFromCache(path);
                    if (t != null) {
                        String artKey = (t.getAlbum() != null) ? t.getAlbum() : String.valueOf(t.getId());
                        if (albumArtMemoryCache.get(artKey) == null) {
                            getAlbumArtAsync(t.getPath(), artKey, new AlbumArtCallback() {
                                @Override public void onAlbumArtLoaded(Bitmap b, boolean c) {}
                                @Override public void onError(Exception e) {}
                            });
                        }
                    }
                    Thread.yield(); // Be nice to UI thread
                }
            } finally {
                isPreloading = false;
            }
        });
    }

    private TrackEntity getTrackFromCache(String path) {
        TrackEntity cached = trackMemoryCache.get(path);
        if (cached != null) return cached;
        
        CachedTrackInfo info = diskCache.get(path);
        if (info != null) {
            TrackEntity t = info.toEntity();
            trackMemoryCache.put(path, t);
            return t;
        }
        return null;
    }

    private void ensureCacheLoaded() {
        while (!cacheLoaded && !isShutdown) {
            try { Thread.sleep(10); } catch (InterruptedException e) { break; }
        }
    }

    private void saveCacheToFile() throws IOException {
        synchronized (diskCache) {
            try (FileWriter writer = new FileWriter(cacheFile)) {
                gson.toJson(diskCache, writer);
            }
        }
        prefs.edit().putLong(KEY_LAST_SCAN_TIME, System.currentTimeMillis()).apply();
        prefs.edit().putInt(KEY_CACHE_VERSION, CURRENT_CACHE_VERSION).apply();
    }

    private void saveAlbumArtToDisk(String key, Bitmap bitmap) {
        File artFile = new File(albumArtCacheDir, key + ".jpg");
        try (FileOutputStream out = new FileOutputStream(artFile)) {
            bitmap.compress(Bitmap.CompressFormat.JPEG, 85, out);
        } catch (IOException e) {
            Log.e(TAG, "Error saving art to disk", e);
        }
    }

    public void shutdown() {
        isShutdown = true;
        cacheExecutor.shutdownNow();
        preloadExecutor.shutdownNow();
        albumArtMemoryCache.evictAll();
        trackMemoryCache.evictAll();
    }

    // --- Interfaces & Internal Data Classes ---

    public interface TrackCacheCallback {
        void onTracksLoaded(List<TrackEntity> tracks, int offset, int count);
        void onError(Exception e);
    }

    public interface AlbumArtCallback {
        void onAlbumArtLoaded(Bitmap bitmap, boolean fromCache);
        void onError(Exception e);
    }

    public interface CacheOperationCallback {
        void onSuccess();
        void onError(Exception e);
    }

    private static class CachedTrackInfo {
        public long id;
        public String title;
        public String artist;
        public String album;
        public long duration;
        public String path;
        public String albumArt;
        public long dateAdded;
        public long size;

        public CachedTrackInfo(TrackEntity t) {
            this.id = t.getId();
            this.title = t.getTitle();
            this.artist = t.getArtist();
            this.album = t.getAlbum();
            this.duration = t.getDuration();
            this.path = t.getPath();
            this.albumArt = t.getAlbumArt();
            this.dateAdded = t.getDateAdded();
            this.size = t.getSize();
        }

        public TrackEntity toEntity() {
            TrackEntity t = new TrackEntity();
            t.setId(id);
            t.setTitle(title);
            t.setArtist(artist);
            t.setAlbum(album);
            t.setDuration(duration);
            t.setPath(path);
            t.setAlbumArt(albumArt);
            t.setDateAdded(dateAdded);
            t.setSize(size);
            return t;
        }
    }
}

