package xime.core.net;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Fetches Map Tiles from OpenStreetMap.
 */
public class TileClient {
    private static final String TAG = "TileClient";
    private static final String OSM_LIGHT_URL = "https://tile.openstreetmap.org/%d/%d/%d.png";
    private static final String CARTO_DARK_URL = "https://a.basemaps.cartocdn.com/dark_all/%d/%d/%d.png";
    
    public enum Theme { LIGHT, DARK }
    
    private final ExecutorService executor = Executors.newFixedThreadPool(4);
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    public interface Callback {
        void onSuccess(Bitmap bitmap);
        void onError(String message);
    }

    public void fetchTile(final int z, final int x, final int y, final Theme theme, final Callback callback) {
        executor.execute(() -> {
            int retries = 0;
            String baseUrl = theme == Theme.DARK ? CARTO_DARK_URL : OSM_LIGHT_URL;
            String urlStr = String.format(baseUrl, z, x, y);
            Log.d(TAG, "Fetching tile: " + urlStr);
            while (retries < 3) {
                try {
                    URL url = new URL(urlStr);
                    HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                    conn.setRequestMethod("GET");
                    conn.setConnectTimeout(8000);
                    conn.setReadTimeout(8000);
                    // MANDATORY: OSM requires a descriptive User-Agent
                    conn.setRequestProperty("User-Agent", "XIME-Core/1.0 (Android; MapWidget; tile-fetching)");

                    if (conn.getResponseCode() == 200) {
                        InputStream in = conn.getInputStream();
                        Bitmap bitmap = BitmapFactory.decodeStream(in);
                        in.close();
                        
                        if (bitmap != null) {
                            mainHandler.post(() -> callback.onSuccess(bitmap));
                            return;
                        }
                    }
                    conn.disconnect();
                } catch (Exception e) {
                    Log.e(TAG, "Tile fetch failed (retry " + retries + ")", e);
                }
                retries++;
                try { Thread.sleep(500); } catch (InterruptedException ignored) {}
            }
            mainHandler.post(() -> callback.onError("Failed to fetch tile after retries"));
        });
    }
}
