package xime.ui.view;

import android.content.Context;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import xime.R;
import xime.core.model.MapLocation;
import xime.core.net.TileClient;
import xime.core.repo.MapRepository;
import xime.ui.drawable.AccuracyRadiusDrawable;
import xime.ui.drawable.PulsingDotDrawable;
import xime.ui.layout.Layout;

/**
 * [XIME-UI] MapView
 * 
 * LEGAL/ACCURACY BOUNDARIES:
 * 1. Self-Located Mode: Uses IP-geolocation to find the user's approximate area. 
 *    Labeled as IP-based. No GPS used.
 * 2. IP Lookup Mode: Accepts arbitrary IP. Renders as APPROXIMATE AREA using 
 *    an accuracy radius. Never implies street-level precision for 3rd parties.
 */
public class MapView extends Layout {
    public enum Mode { SELF_TRACKER, IP_LOOKUP }

    private Mode currentMode = Mode.SELF_TRACKER;
    private MapRepository repository;
    
    private MapTileView mapTileView;
    private TextView timeText, timezoneText;
    private TextView cityCountryText, addressText, distanceText;
    
    private PulsingDotDrawable pulsingDot;
    private AccuracyRadiusDrawable accuracyRadius;

    public MapView(@NonNull Context context) { this(context, null); }
    public MapView(@NonNull Context context, @Nullable AttributeSet attrs) { this(context, attrs, 0); }
    public MapView(@NonNull Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init(context);
    }

    private void init(Context context) {
        LayoutInflater.from(context).inflate(R.layout.widget_map, this, true);
        
        mapTileView = (MapTileView) findViewById(R.id.map_tile_view);
        if (mapTileView != null) mapTileView.setTheme(TileClient.Theme.DARK);
        
        timeText = (TextView) findViewById(R.id.map_time);
        timezoneText = (TextView) findViewById(R.id.map_timezone);
        cityCountryText = (TextView) findViewById(R.id.map_city_country);
        addressText = (TextView) findViewById(R.id.map_address);
        distanceText = (TextView) findViewById(R.id.map_distance);

        pulsingDot = new PulsingDotDrawable();
        accuracyRadius = new AccuracyRadiusDrawable();
    }

    public void setRepository(MapRepository repository) {
        this.repository = repository;
        if (mapTileView != null) mapTileView.setRepository(repository);
    }

    public void setMode(Mode mode) {
        this.currentMode = mode;
    }

    public void refreshSelf() {
        if (repository == null) return;
        repository.loadSelf(new MapRepository.Callback() {
            @Override
            public void onLocationLoaded(MapLocation location) {
                showLocation(location);
            }
            @Override
            public void onTileLoaded(int z, int x, int y, android.graphics.Bitmap b) {}
            @Override
            public void onError(String message) {}
        });
    }

    public void lookupIp(String ip) {
        if (repository == null) return;
        repository.loadForIp(ip, new MapRepository.Callback() {
            @Override
            public void onLocationLoaded(MapLocation location) {
                showLocation(location);
            }
            @Override
            public void onTileLoaded(int z, int x, int y, android.graphics.Bitmap b) {}
            @Override
            public void onError(String message) {}
        });
    }

    public void showLocation(MapLocation loc) {
        if (mapTileView != null) mapTileView.setLocation(loc, 17);
        
        cityCountryText.setText(loc.city + ", " + loc.country);
        addressText.setText(loc.addressLine != null ? loc.addressLine : "Approximate Location");
        
        View locationDot = findViewById(R.id.map_location_dot);
        if (currentMode == Mode.SELF_TRACKER) {
            distanceText.setText("0 km");
            if (locationDot != null) locationDot.setBackground(pulsingDot);
        } else {
            distanceText.setText("±" + (int)loc.accuracyRadiusKm + " km");
            if (locationDot != null) locationDot.setBackground(accuracyRadius);
            // In a real implementation, we'd calculate radius in pixels based on zoom
            accuracyRadius.setRadius(dpToPx(60)); 
        }
    }

    public void destroy() {
        if (pulsingDot != null) pulsingDot.stop();
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        destroy();
    }

    private int dpToPx(int dp) {
        return (int) (dp * getResources().getDisplayMetrics().density);
    }
}
