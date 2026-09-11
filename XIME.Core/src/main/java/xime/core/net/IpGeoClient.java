package xime.core.net;

import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import xime.core.model.MapLocation;

/**
 * IP Geolocation Client using ipapi.co (Free Tier).
 * 
 * NOTE: This provides APPROXIMATE location based on IP address blocks.
 * It is NOT GPS tracking. The results should be shown with an accuracy radius.
 */
public class IpGeoClient {
    private static final String TAG = "IpGeoClient";
    private static final String BASE_URL = "https://ipapi.co/";
    
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    public interface Callback {
        void onSuccess(MapLocation location);
        void onError(String message);
    }

    /**
     * Resolves the device's own public IP to a location.
     */
    public void lookupSelf(Callback callback) {
        lookup(null, callback);
    }

    /**
     * Resolves an arbitrary IP address to a location.
     */
    public void lookupIp(String ip, Callback callback) {
        lookup(ip, callback);
    }

    private void lookup(final String ip, final Callback callback) {
        executor.execute(() -> {
            try {
                String target = (ip == null || ip.isEmpty()) ? "json/" : ip + "/json/";
                URL url = new URL(BASE_URL + target);
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("GET");
                conn.setConnectTimeout(8000);
                conn.setReadTimeout(8000);
                conn.setRequestProperty("User-Agent", "XIME-Core/1.0 (Android; MapWidget)");

                int responseCode = conn.getResponseCode();
                if (responseCode == 200) {
                    BufferedReader in = new BufferedReader(new InputStreamReader(conn.getInputStream()));
                    StringBuilder response = new StringBuilder();
                    String line;
                    while ((line = in.readLine()) != null) response.append(line);
                    in.close();

                    JSONObject json = new JSONObject(response.toString());
                    
                    // Note: accuracy radius is often provided as 'accuracy' in miles or not at all.
                    // We'll use a conservative default if missing.
                    float radius = (float) json.optDouble("accuracy", 15.0); 
                    
                    final MapLocation loc = new MapLocation(
                        json.getDouble("latitude"),
                        json.getDouble("longitude"),
                        json.optString("city", "Unknown"),
                        json.optString("country_name", "Unknown"),
                        null, // Address line usually not available in standard IP geo
                        radius,
                        ip == null // If ip is null, it's 'Self' mode (Precise-looking relative to device)
                    );

                    mainHandler.post(() -> callback.onSuccess(loc));
                } else {
                    mainHandler.post(() -> callback.onError("HTTP " + responseCode));
                }
                conn.disconnect();
            } catch (Exception e) {
                Log.e(TAG, "IP lookup failed", e);
                mainHandler.post(() -> callback.onError(e.getMessage()));
            }
        });
    }
}
