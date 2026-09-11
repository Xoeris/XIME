package xime.media.equalizer;

import android.content.Context;
import android.content.SharedPreferences;
import android.media.audiofx.Virtualizer;
import android.util.Log;

public class EqualizerSpatializer {
    private static final String TAG = "EqualizerSpatializer";
    private static final String PREF_NAME = "XoerisSpatializerPrefs";
    private static final String KEY_SPATIAL_ENABLED = "spatial_enabled";
    private static final String KEY_SPATIAL_STRENGTH = "spatial_strength";

    private static EqualizerSpatializer instance;
    private final Context context;
    private Virtualizer virtualizer;
    private boolean enabled = false;
    private short strength = 0;

    private EqualizerSpatializer(Context context) {
        this.context = context.getApplicationContext();
        SharedPreferences prefs = this.context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        this.enabled = prefs.getBoolean(KEY_SPATIAL_ENABLED, false);
        this.strength = (short) prefs.getInt(KEY_SPATIAL_STRENGTH, 0);
    }

    public static synchronized EqualizerSpatializer getInstance(Context context) {
        if (instance == null) {
            instance = new EqualizerSpatializer(context);
        }
        return instance;
    }

    public synchronized void applyToSession(int sessionId) {
        if (sessionId == 0) return;
        try {
            if (virtualizer != null) {
                try { virtualizer.release(); } catch (Exception ignored) {}
            }
            virtualizer = new Virtualizer(0, sessionId);
            virtualizer.setEnabled(enabled);
            if (virtualizer.getStrengthSupported()) {
                virtualizer.setStrength(strength);
            }
        } catch (Exception e) {
            Log.e(TAG, "Failed to initialize Virtualizer effect", e);
        }
    }

    public synchronized void setEnabled(boolean enabled) {
        this.enabled = enabled;
        if (virtualizer != null) {
            try {
                virtualizer.setEnabled(enabled);
            } catch (Exception e) {
                Log.e(TAG, "Error toggling Virtualizer", e);
            }
        }
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
                .edit().putBoolean(KEY_SPATIAL_ENABLED, enabled).apply();
    }

    public synchronized boolean isEnabled() {
        return enabled;
    }

    public synchronized void setStrength(short strength) {
        this.strength = (short) Math.max(0, Math.min(1000, strength));
        if (virtualizer != null) {
            try {
                if (virtualizer.getStrengthSupported()) {
                    virtualizer.setStrength(this.strength);
                }
            } catch (Exception e) {
                Log.e(TAG, "Error setting Virtualizer strength", e);
            }
        }
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
                .edit().putInt(KEY_SPATIAL_STRENGTH, this.strength).apply();
    }

    public synchronized short getStrength() {
        return strength;
    }
}
