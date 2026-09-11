package xime.media.equalizer;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

public class Equalizer {
    private static final String TAG = "Equalizer";
    private static final String PREF_NAME = "XoerisEqualizerPrefs";
    private static final String KEY_EQ_ENABLED = "eq_enabled";
    private static final String KEY_EQ_BAND_PREFIX = "eq_band_";
    private static final String KEY_EQ_PRESET = "eq_preset";

    private static Equalizer instance;
    private final Context context;
    private android.media.audiofx.Equalizer equalizer;
    private int audioSessionId = 0;
    private boolean enabled = false;
    private short currentPreset = -1;

    private Equalizer(Context context) {
        this.context = context.getApplicationContext();
        SharedPreferences prefs = this.context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        this.enabled = prefs.getBoolean(KEY_EQ_ENABLED, false);
        this.currentPreset = (short) prefs.getInt(KEY_EQ_PRESET, -1);
    }

    public static synchronized Equalizer getInstance(Context context) {
        if (instance == null) {
            instance = new Equalizer(context);
        }
        return instance;
    }

    public synchronized void applyToSession(int sessionId) {
        this.audioSessionId = sessionId;
        if (sessionId == 0) return;

        try {
            if (equalizer != null) {
                try { equalizer.release(); } catch (Exception ignored) {}
            }
            equalizer = new android.media.audiofx.Equalizer(0, sessionId);
            equalizer.setEnabled(enabled);

            if (currentPreset >= 0 && currentPreset < equalizer.getNumberOfPresets()) {
                equalizer.usePreset(currentPreset);
            } else {
                SharedPreferences prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
                for (short i = 0; i < equalizer.getNumberOfBands(); i++) {
                    int level = prefs.getInt(KEY_EQ_BAND_PREFIX + i, 0);
                    equalizer.setBandLevel(i, (short) level);
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Failed to initialize Equalizer effect", e);
        }
    }

    public synchronized void setEnabled(boolean enabled) {
        this.enabled = enabled;
        if (equalizer != null) {
            try {
                equalizer.setEnabled(enabled);
            } catch (Exception e) {
                Log.e(TAG, "Error toggling equalizer", e);
            }
        }
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
                .edit().putBoolean(KEY_EQ_ENABLED, enabled).apply();
    }

    public synchronized boolean isEnabled() {
        return enabled;
    }

    public synchronized void setBandLevel(short band, short level) {
        currentPreset = -1;
        if (equalizer != null) {
            try {
                equalizer.setBandLevel(band, level);
            } catch (Exception e) {
                Log.e(TAG, "Error setting band level", e);
            }
        }
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
                .edit()
                .putInt(KEY_EQ_BAND_PREFIX + band, level)
                .putInt(KEY_EQ_PRESET, -1)
                .apply();
    }

    public synchronized short getBandLevel(short band) {
        if (equalizer != null) {
            try {
                return equalizer.getBandLevel(band);
            } catch (Exception e) {
                Log.e(TAG, "Error getting band level", e);
            }
        }
        return (short) context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
                .getInt(KEY_EQ_BAND_PREFIX + band, 0);
    }

    public synchronized short getNumberOfBands() {
        if (equalizer != null) {
            try {
                return equalizer.getNumberOfBands();
            } catch (Exception e) {
                Log.e(TAG, "Error getting number of bands", e);
            }
        }
        return 5;
    }

    public synchronized int getCenterFreq(short band) {
        if (equalizer != null) {
            try {
                return equalizer.getCenterFreq(band);
            } catch (Exception e) {
                Log.e(TAG, "Error getting center freq", e);
            }
        }
        return 0;
    }

    public synchronized short[] getBandLevelRange() {
        if (equalizer != null) {
            try {
                return equalizer.getBandLevelRange();
            } catch (Exception e) {
                Log.e(TAG, "Error getting band level range", e);
            }
        }
        return new short[]{-1500, 1500};
    }

    public synchronized void usePreset(short preset) {
        this.currentPreset = preset;
        if (equalizer != null) {
            try {
                equalizer.usePreset(preset);
            } catch (Exception e) {
                Log.e(TAG, "Error setting preset", e);
            }
        }
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
                .edit().putInt(KEY_EQ_PRESET, preset).apply();
    }

    public synchronized short getPreset() {
        return currentPreset;
    }

    public synchronized String getPresetName(short preset) {
        if (equalizer != null) {
            try {
                return equalizer.getPresetName(preset);
            } catch (Exception e) {
                Log.e(TAG, "Error getting preset name", e);
            }
        }
        return "";
    }

    public synchronized short getNumberOfPresets() {
        if (equalizer != null) {
            try {
                return equalizer.getNumberOfPresets();
            } catch (Exception e) {
                Log.e(TAG, "Error getting number of presets", e);
            }
        }
        return 0;
    }

    public synchronized void resetAll() {
        this.currentPreset = -1;
        setEnabled(false);
        SharedPreferences.Editor editor = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE).edit();
        editor.putInt(KEY_EQ_PRESET, -1);
        short bands = getNumberOfBands();
        for (short i = 0; i < bands; i++) {
            setBandLevel(i, (short) 0);
            editor.putInt(KEY_EQ_BAND_PREFIX + i, 0);
        }
        editor.apply();
    }
}
