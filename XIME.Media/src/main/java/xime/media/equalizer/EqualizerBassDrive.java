package xime.media.equalizer;

import android.content.Context;
import android.content.SharedPreferences;
import android.media.audiofx.BassBoost;
import android.util.Log;

public class EqualizerBassDrive {
    private static final String TAG = "EqualizerBassDrive";
    private static final String PREF_NAME = "XoerisBassDrivePrefs";
    private static final String KEY_BASS_ENABLED = "bass_enabled";
    private static final String KEY_BASS_STRENGTH = "bass_strength";

    private static EqualizerBassDrive instance;
    private final Context context;
    private BassBoost bassBoost;
    private boolean enabled = false;
    private short strength = 0;

    private EqualizerBassDrive(Context context) {
        this.context = context.getApplicationContext();
        SharedPreferences prefs = this.context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        this.enabled = prefs.getBoolean(KEY_BASS_ENABLED, false);
        this.strength = (short) prefs.getInt(KEY_BASS_STRENGTH, 0);
    }

    public static synchronized EqualizerBassDrive getInstance(Context context) {
        if (instance == null) {
            instance = new EqualizerBassDrive(context);
        }
        return instance;
    }

    public synchronized void applyToSession(int sessionId) {
        if (sessionId == 0) return;
        try {
            if (bassBoost != null) {
                try { bassBoost.release(); } catch (Exception ignored) {}
            }
            bassBoost = new BassBoost(0, sessionId);
            bassBoost.setEnabled(enabled);
            if (bassBoost.getStrengthSupported()) {
                bassBoost.setStrength(strength);
            }
        } catch (Exception e) {
            Log.e(TAG, "Failed to initialize BassBoost effect", e);
        }
    }

    public synchronized void setEnabled(boolean enabled) {
        this.enabled = enabled;
        if (bassBoost != null) {
            try {
                bassBoost.setEnabled(enabled);
            } catch (Exception e) {
                Log.e(TAG, "Error toggling BassBoost", e);
            }
        }
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
                .edit().putBoolean(KEY_BASS_ENABLED, enabled).apply();
    }

    public synchronized boolean isEnabled() {
        return enabled;
    }

    public synchronized void setStrength(short strength) {
        this.strength = (short) Math.max(0, Math.min(1000, strength));
        if (bassBoost != null) {
            try {
                if (bassBoost.getStrengthSupported()) {
                    bassBoost.setStrength(this.strength);
                }
            } catch (Exception e) {
                Log.e(TAG, "Error setting BassBoost strength", e);
            }
        }
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
                .edit().putInt(KEY_BASS_STRENGTH, this.strength).apply();
    }

    public synchronized short getStrength() {
        return strength;
    }
}
