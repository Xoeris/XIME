package xime.media.equalizer;

import android.content.Context;
import android.content.SharedPreferences;
import android.media.audiofx.LoudnessEnhancer;
import android.util.Log;

public class EqualizerVolumeBoost {
    private static final String TAG = "EqualizerVolumeBoost";
    private static final String PREF_NAME = "XoerisVolumeBoostPrefs";
    private static final String KEY_BOOST_ENABLED = "boost_enabled";
    private static final String KEY_BOOST_GAIN = "boost_gain";

    private static EqualizerVolumeBoost instance;
    private final Context context;
    private LoudnessEnhancer loudnessEnhancer;
    private boolean enabled = false;
    private int gain = 0; // gain in mB (milliBel), 0 to 2000 (20 dB)

    private EqualizerVolumeBoost(Context context) {
        this.context = context.getApplicationContext();
        SharedPreferences prefs = this.context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        this.enabled = prefs.getBoolean(KEY_BOOST_ENABLED, false);
        this.gain = prefs.getInt(KEY_BOOST_GAIN, 0);
    }

    public static synchronized EqualizerVolumeBoost getInstance(Context context) {
        if (instance == null) {
            instance = new EqualizerVolumeBoost(context);
        }
        return instance;
    }

    public synchronized void applyToSession(int sessionId) {
        if (sessionId == 0) return;
        try {
            if (loudnessEnhancer != null) {
                try { loudnessEnhancer.release(); } catch (Exception ignored) {}
            }
            loudnessEnhancer = new LoudnessEnhancer(sessionId);
            loudnessEnhancer.setEnabled(enabled);
            loudnessEnhancer.setTargetGain(gain);
        } catch (Exception e) {
            Log.e(TAG, "Failed to initialize LoudnessEnhancer effect", e);
        }
    }

    public synchronized void setEnabled(boolean enabled) {
        this.enabled = enabled;
        if (loudnessEnhancer != null) {
            try {
                loudnessEnhancer.setEnabled(enabled);
            } catch (Exception e) {
                Log.e(TAG, "Error toggling LoudnessEnhancer", e);
            }
        }
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
                .edit().putBoolean(KEY_BOOST_ENABLED, enabled).apply();
    }

    public synchronized boolean isEnabled() {
        return enabled;
    }

    public synchronized void setGain(int gain) {
        this.gain = Math.max(0, Math.min(2000, gain));
        if (loudnessEnhancer != null) {
            try {
                loudnessEnhancer.setTargetGain(this.gain);
            } catch (Exception e) {
                Log.e(TAG, "Error setting LoudnessEnhancer gain", e);
            }
        }
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
                .edit().putInt(KEY_BOOST_GAIN, this.gain).apply();
    }

    public synchronized int getGain() {
        return gain;
    }
}
