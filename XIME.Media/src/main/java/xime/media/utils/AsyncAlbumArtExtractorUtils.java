package xime.media.utils;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.media.MediaMetadataRetriever;
import android.util.Log;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class AsyncAlbumArtExtractorUtils {
    
    private static final String TAG = "AsyncAlbumArtExtractorUtils";
    private static final String CACHE_DIR_NAME = "album_art_cache";
    private static final ExecutorService ioExecutor = Executors.newCachedThreadPool();
    
    public static class AlbumArtResult {
        public final boolean success;
        public final String message;
        public final File artFile;
        public final Bitmap bitmap;
        
        public AlbumArtResult(boolean success, String message, File artFile, Bitmap bitmap) {
            this.success = success;
            this.message = message;
            this.artFile = artFile;
            this.bitmap = bitmap;
        }
    }

    public static CompletableFuture<AlbumArtResult> extractAlbumArtAsync(
            Context context, 
            String trackPath, 
            String albumName, 
            String artistName) {
        
        return CompletableFuture.supplyAsync(() -> {
            try {
                // Create manager directory
                File cacheDir = new File(context.getCacheDir(), CACHE_DIR_NAME);
                if (!cacheDir.exists()) {
                    if (!cacheDir.mkdirs()) {
                        return new AlbumArtResult(false, "Failed to create manager directory", null, null);
                    }
                }

                // Generate manager filename
                String cacheFileName = generateCacheFileName(albumName, artistName);
                File cacheFile = new File(cacheDir, cacheFileName);

                // Check if cached version exists
                if (cacheFile.exists()) {
                    Bitmap cachedBitmap = BitmapFactory.decodeFile(cacheFile.getAbsolutePath());
                    if (cachedBitmap != null) {
                        Log.d(TAG, "Using cached album art: " + cacheFileName);
                        return new AlbumArtResult(true, "Cached album art loaded", cacheFile, cachedBitmap);
                    }
                }

                // Extract album art from track
                Bitmap extractedBitmap = extractAlbumArtFromTrack(trackPath);
                if (extractedBitmap != null) {
                    // Save to manager
                    if (saveBitmapToCache(extractedBitmap, cacheFile)) {
                        Log.d(TAG, "Album art extracted and cached: " + cacheFileName);
                        return new AlbumArtResult(true, "Album art extracted and cached", cacheFile, extractedBitmap);
                    } else {
                        Log.w(TAG, "Album art extracted but failed to manager: " + cacheFileName);
                        return new AlbumArtResult(true, "Album art extracted (manager failed)", null, extractedBitmap);
                    }
                } else {
                    Log.d(TAG, "No album art found in track: " + trackPath);
                    return new AlbumArtResult(false, "No album art found", null, null);
                }

            } catch (Exception e) {
                Log.e(TAG, "Error extracting album art for: " + trackPath, e);
                return new AlbumArtResult(false, "Extraction failed: " + e.getMessage(), null, null);
            }
        }, ioExecutor);
    }

    /**
     * Load cached album art asynchronously
     */
    public static CompletableFuture<AlbumArtResult> loadCachedAlbumArtAsync(
            Context context, 
            String albumName, 
            String artistName) {
        
        return CompletableFuture.supplyAsync(() -> {
            try {
                File cacheDir = new File(context.getCacheDir(), CACHE_DIR_NAME);
                String cacheFileName = generateCacheFileName(albumName, artistName);
                File cacheFile = new File(cacheDir, cacheFileName);

                if (cacheFile.exists()) {
                    Bitmap cachedBitmap = BitmapFactory.decodeFile(cacheFile.getAbsolutePath());
                    if (cachedBitmap != null) {
                        Log.d(TAG, "Cached album art loaded: " + cacheFileName);
                        return new AlbumArtResult(true, "Cached album art loaded", cacheFile, cachedBitmap);
                    }
                }

                Log.d(TAG, "No cached album art found: " + cacheFileName);
                return new AlbumArtResult(false, "No cached album art found", null, null);

            } catch (Exception e) {
                Log.e(TAG, "Error loading cached album art", e);
                return new AlbumArtResult(false, "Cache load failed: " + e.getMessage(), null, null);
            }
        }, ioExecutor);
    }

    /**
     * Clear album art manager asynchronously
     */
    public static CompletableFuture<Boolean> clearAlbumArtCacheAsync(Context context) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                File cacheDir = new File(context.getCacheDir(), CACHE_DIR_NAME);
                if (cacheDir.exists()) {
                    return deleteDirectoryRecursively(cacheDir);
                }
                return true;
            } catch (Exception e) {
                Log.e(TAG, "Error clearing album art manager", e);
                return false;
            }
        }, ioExecutor);
    }

    /**
     * Get manager size asynchronously
     */
    public static CompletableFuture<Long> getCacheSizeAsync(Context context) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                File cacheDir = new File(context.getCacheDir(), CACHE_DIR_NAME);
                return calculateDirectorySize(cacheDir);
            } catch (Exception e) {
                Log.e(TAG, "Error calculating manager size", e);
                return 0L;
            }
        }, ioExecutor);
    }

    /**
     * Extract album art from track file
     */
    public static Bitmap extractAlbumArtFromTrack(String trackPath) {
        MediaMetadataRetriever retriever = null;
        try {
            retriever = new MediaMetadataRetriever();
            retriever.setDataSource(trackPath);
            
            byte[] artBytes = retriever.getEmbeddedPicture();
            if (artBytes != null) {
                return BitmapFactory.decodeByteArray(artBytes, 0, artBytes.length);
            }
            
        } catch (Exception e) {
            Log.e(TAG, "Error extracting album art from: " + trackPath, e);
        } finally {
            if (retriever != null) {
                try {
                    retriever.release();
                } catch (Exception e) {
                    Log.e(TAG, "Error releasing MediaMetadataRetriever", e);
                }
            }
        }
        return null;
    }

    /**
     * Save bitmap to manager file
     */
    private static boolean saveBitmapToCache(Bitmap bitmap, File cacheFile) {
        FileOutputStream outputStream = null;
        try {
            outputStream = new FileOutputStream(cacheFile);
            return bitmap.compress(Bitmap.CompressFormat.JPEG, 85, outputStream);
        } catch (IOException e) {
            Log.e(TAG, "Error saving bitmap to manager: " + cacheFile.getPath(), e);
            return false;
        } finally {
            if (outputStream != null) {
                try {
                    outputStream.close();
                } catch (IOException e) {
                    Log.e(TAG, "Error closing output stream", e);
                }
            }
        }
    }

    /**
     * Generate manager filename from album and artist
     */
    private static String generateCacheFileName(String albumName, String artistName) {
        String combined = (artistName != null ? artistName : "Unknown") + "_" + 
                         (albumName != null ? albumName : "Unknown");
        
        // Sanitize filename
        String sanitized = combined.replaceAll("[^a-zA-Z0-9\\s]", "_")
                                  .replaceAll("\\s+", "_")
                                  .toLowerCase();
        
        return sanitized + ".jpg";
    }

    /**
     * Delete directory and all its contents
     */
    private static boolean deleteDirectoryRecursively(File directory) {
        if (directory.isDirectory()) {
            File[] files = directory.listFiles();
            if (files != null) {
                for (File file : files) {
                    if (!deleteDirectoryRecursively(file)) {
                        return false;
                    }
                }
            }
        }
        boolean deleted = directory.delete();
        if (!deleted) {
            Log.w(TAG, "Failed to delete: " + directory.getPath());
        }
        return deleted;
    }

    /**
     * Calculate directory size recursively
     */
    private static long calculateDirectorySize(File directory) {
        long size = 0;
        if (directory.exists()) {
            if (directory.isDirectory()) {
                File[] files = directory.listFiles();
                if (files != null) {
                    for (File file : files) {
                        size += calculateDirectorySize(file);
                    }
                }
            } else {
                size = directory.length();
            }
        }
        return size;
    }

    /**
     * Shutdown the executor service
     */
    public static void shutdown() {
        if (ioExecutor != null && !ioExecutor.isShutdown()) {
            ioExecutor.shutdown();
            Log.d(TAG, "AsyncAlbumArtExtractorUtils executor shutdown");
        }
    }
}

