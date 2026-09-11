package xime.media.music.service;

import android.app.Service;
import android.content.Intent;
import android.os.Binder;
import android.os.Environment;
import android.os.FileObserver;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;

import androidx.annotation.Nullable;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import xime.media.database.MusicDatabase;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public abstract class MusicScannerService extends Service {
    
    private static final String TAG = "MusicScannerService";
    public static final String ACTION_MUSIC_UPDATED = "xime.media.MUSIC_UPDATED";
    
    private final IBinder binder = new XoerisMusicScannerBinder();
    protected ExecutorService executor;
    protected Handler mainHandler;
    protected MusicDatabase database;
    
    private Map<String, MusicDirectoryObserver> fileObservers = new HashMap<>();
    protected List<String> musicDirectories = new ArrayList<>();
    
    private boolean autoRefreshEnabled = true;
    private long refreshIntervalMs = 300000; 
    private Runnable autoRefreshRunnable;
    
    private Handler debounceHandler = new Handler(Looper.getMainLooper());
    private Runnable debounceRunnable;
    private static final long DEBOUNCE_DELAY_MS = 2000;
    private boolean hasPendingChanges = false;
    
    @Override
    public void onCreate() {
        super.onCreate();
        executor = Executors.newFixedThreadPool(2);
        mainHandler = new Handler(Looper.getMainLooper());
        database = MusicDatabase.getDatabase(this);
        
        initializeDirectories();
        startFileObservers();
        startAutoRefresh();
    }

    protected void initializeDirectories() {
        musicDirectories.clear();
        File musicDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC);
        if (musicDir.exists()) musicDirectories.add(musicDir.getAbsolutePath());
        
        File downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
        if (downloadsDir.exists()) musicDirectories.add(downloadsDir.getAbsolutePath());
    }
    
    private void startFileObservers() {
        for (String directory : musicDirectories) {
            MusicDirectoryObserver observer = new MusicDirectoryObserver(directory);
            observer.startWatching();
            fileObservers.put(directory, observer);
        }
    }
    
    private void stopFileObservers() {
        for (MusicDirectoryObserver observer : fileObservers.values()) {
            observer.stopWatching();
        }
        fileObservers.clear();
    }
    
    private void startAutoRefresh() {
        if (autoRefreshRunnable == null) {
            autoRefreshRunnable = () -> {
                if (autoRefreshEnabled) {
                    performScan();
                    mainHandler.postDelayed(autoRefreshRunnable, refreshIntervalMs);
                }
            };
        }
        mainHandler.postDelayed(autoRefreshRunnable, refreshIntervalMs);
    }

    protected abstract void performScan();
    
    protected void onFileChanged(String path, int event) {
        hasPendingChanges = true;
        if (debounceRunnable != null) debounceHandler.removeCallbacks(debounceRunnable);
        debounceRunnable = () -> {
            if (hasPendingChanges) {
                hasPendingChanges = false;
                processFileChange(path, event);
            }
        };
        debounceHandler.postDelayed(debounceRunnable, DEBOUNCE_DELAY_MS);
    }
    
    protected void processFileChange(String path, int event) {
        executor.execute(() -> {
            if (isMusicFile(path)) {
                if (event == FileObserver.CREATE || event == FileObserver.MOVED_TO) {
                    handleNewFile(path);
                } else if (event == FileObserver.DELETE || event == FileObserver.MOVED_FROM) {
                    handleRemovedFile(path);
                }
                notifyUpdate(path);
            }
        });
    }

    protected void handleNewFile(String path) {
        performScan();
    }

    protected void handleRemovedFile(String path) {
        database.trackDao().deleteTrackByPath(path);
    }

    protected boolean isMusicFile(String path) {
        if (path == null) return false;
        String lowerPath = path.toLowerCase();
        return lowerPath.endsWith(".mp3") || lowerPath.endsWith(".m4a") || 
               lowerPath.endsWith(".flac") || lowerPath.endsWith(".wav");
    }

    protected void notifyUpdate(String path) {
        Intent intent = new Intent(ACTION_MUSIC_UPDATED);
        intent.putExtra("file_path", path);
        LocalBroadcastManager.getInstance(this).sendBroadcast(intent);
    }
    
    private class MusicDirectoryObserver extends FileObserver {
        private String path;
        public MusicDirectoryObserver(String path) {
            super(path, CREATE | DELETE | MOVED_FROM | MOVED_TO);
            this.path = path;
        }
        @Override
        public void onEvent(int event, @Nullable String path) {
            if (path != null) onFileChanged(this.path + File.separator + path, event);
        }
    }
    
    public class XoerisMusicScannerBinder extends Binder {
        public MusicScannerService getService() { return MusicScannerService.this; }
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) { return binder; }

    @Override
    public void onDestroy() {
        stopFileObservers();
        if (autoRefreshRunnable != null) mainHandler.removeCallbacks(autoRefreshRunnable);
        executor.shutdown();
        super.onDestroy();
    }
}

