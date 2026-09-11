package xime.core.repo;

import android.graphics.Bitmap;

import xime.core.model.MapLocation;
import xime.core.net.IpGeoClient;
import xime.core.net.TileClient;
import xime.core.cache.TileCache;

/**
 * Repository for Map data. Coordinates IP Geolocation and Tile fetching.
 */
public class MapRepository {
    private final IpGeoClient geoClient;
    private final TileClient tileClient;
    private final TileCache tileCache;

    public interface Callback {
        void onLocationLoaded(MapLocation location);
        void onTileLoaded(int z, int x, int y, Bitmap bitmap);
        void onError(String message);
    }

    public MapRepository(IpGeoClient geoClient, TileClient tileClient, TileCache tileCache) {
        this.geoClient = geoClient;
        this.tileClient = tileClient;
        this.tileCache = tileCache;
    }

    public void loadSelf(final Callback callback) {
        geoClient.lookupSelf(new IpGeoClient.Callback() {
            @Override
            public void onSuccess(MapLocation location) {
                callback.onLocationLoaded(location);
            }

            @Override
            public void onError(String message) {
                callback.onError(message);
            }
        });
    }

    public void loadForIp(String ip, final Callback callback) {
        geoClient.lookupIp(ip, new IpGeoClient.Callback() {
            @Override
            public void onSuccess(MapLocation location) {
                callback.onLocationLoaded(location);
            }

            @Override
            public void onError(String message) {
                callback.onError(message);
            }
        });
    }

    public void loadTile(final int z, final int x, final int y, final TileClient.Theme theme, final Callback callback) {
        String key = theme.name() + "/" + z + "/" + x + "/" + y;
        Bitmap cached = tileCache.get(key);
        if (cached != null) {
            callback.onTileLoaded(z, x, y, cached);
            return;
        }

        tileClient.fetchTile(z, x, y, theme, new TileClient.Callback() {
            @Override
            public void onSuccess(Bitmap bitmap) {
                tileCache.put(key, bitmap);
                callback.onTileLoaded(z, x, y, bitmap);
            }

            @Override
            public void onError(String message) {
                callback.onError(message);
            }
        });
    }
}
