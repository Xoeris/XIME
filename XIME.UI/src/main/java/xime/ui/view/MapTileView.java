package xime.ui.view;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.util.AttributeSet;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.HashMap;
import java.util.Map;

import xime.core.model.MapLocation;
import xime.core.net.TileClient;
import xime.core.repo.MapRepository;

/**
 * Renders stitched OpenStreetMap tiles.
 */
public class MapTileView extends View {
    private static final int TILE_SIZE = 256;
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Map<String, Bitmap> tiles = new HashMap<>();
    
    private MapRepository repository;
    private double centerLat, centerLon;
    private int zoom = 15;
    private TileClient.Theme theme = TileClient.Theme.LIGHT;

    public MapTileView(@NonNull Context context) { super(context); }
    public MapTileView(@NonNull Context context, @Nullable AttributeSet attrs) { super(context, attrs); }

    public void setRepository(MapRepository repository) {
        this.repository = repository;
    }

    public void setTheme(TileClient.Theme theme) {
        this.theme = theme;
        tiles.clear();
        loadTiles();
        invalidate();
    }

    public void setLocation(MapLocation loc, int zoom) {
        this.centerLat = loc.latitude;
        this.centerLon = loc.longitude;
        this.zoom = zoom;
        loadTiles();
        invalidate();
    }

    private void loadTiles() {
        if (repository == null) return;

        double xtile = lon2tile(centerLon, zoom);
        double ytile = lat2tile(centerLat, zoom);

        int centerX = (int) Math.floor(xtile);
        int centerY = (int) Math.floor(ytile);

        // Fetch a 7x7 grid around center to ensure full coverage on high-density displays
        for (int x = centerX - 3; x <= centerX + 3; x++) {
            for (int y = centerY - 3; y <= centerY + 3; y++) {
                final int curX = x;
                final int curY = y;
                repository.loadTile(zoom, curX, curY, theme, new MapRepository.Callback() {
                    @Override
                    public void onLocationLoaded(MapLocation location) {}
                    @Override
                    public void onTileLoaded(int z, int tx, int ty, Bitmap bitmap) {
                        if (z == zoom) {
                            tiles.put(tx + "/" + ty, bitmap);
                            postInvalidate();
                        }
                    }
                    @Override
                    public void onError(String message) {}
                });
            }
        }
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (centerLat == 0 && centerLon == 0) return;

        double xtile = lon2tile(centerLon, zoom);
        double ytile = lat2tile(centerLat, zoom);

        int centerX = (int) Math.floor(xtile);
        int centerY = (int) Math.floor(ytile);

        // Pixel offset of center coordinate within its tile
        double xOffset = (xtile - centerX) * TILE_SIZE;
        double yOffset = (ytile - centerY) * TILE_SIZE;

        int viewCenterX = getWidth() / 2;
        int viewCenterY = getHeight() / 2;

        for (int x = centerX - 3; x <= centerX + 3; x++) {
            for (int y = centerY - 3; y <= centerY + 3; y++) {
                Bitmap b = tiles.get(x + "/" + y);
                if (b != null) {
                    float drawX = (float) (viewCenterX - xOffset + (x - centerX) * TILE_SIZE);
                    float drawY = (float) (viewCenterY - yOffset + (y - centerY) * TILE_SIZE);
                    canvas.drawBitmap(b, drawX, drawY, paint);
                }
            }
        }
    }

    private double lon2tile(double lon, int zoom) {
        return (lon + 180) / 360 * (1 << zoom);
    }

    private double lat2tile(double lat, int zoom) {
        return (1 - Math.log(Math.tan(Math.toRadians(lat)) + 1 / Math.cos(Math.toRadians(lat))) / Math.PI) / 2 * (1 << zoom);
    }
}
