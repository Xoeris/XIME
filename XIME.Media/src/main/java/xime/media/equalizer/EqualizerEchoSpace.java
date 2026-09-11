package xime.media.equalizer;

import android.content.Context;
import android.content.SharedPreferences;
import android.media.audiofx.PresetReverb;
import android.util.Log;

public class EqualizerEchoSpace {
    private static final String TAG = "EqualizerEchoSpace";
    private static final String PREF_NAME = "XoerisEchoSpacePrefs";
    private static final String KEY_ECHO_ENABLED = "echo_enabled";
    private static final String KEY_ECHO_PRESET = "echo_preset";

    private static EqualizerEchoSpace instance;
    private final Context context;
    private PresetReverb presetReverb;
    private boolean enabled = false;
    private short preset = PresetReverb.PRESET_NONE; // PresetReverb.PRESET_SMALLROOM, etc.

    private EqualizerEchoSpace(Context context) {
        this.context = context.getApplicationContext();
        SharedPreferences prefs = this.context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        this.enabled = prefs.getBoolean(KEY_ECHO_ENABLED, false);
        this.preset = (short) prefs.getInt(KEY_ECHO_PRESET, PresetReverb.PRESET_NONE);
    }

    public static synchronized EqualizerEchoSpace getInstance(Context context) {
        if (instance == null) {
            instance = new EqualizerEchoSpace(context);
        }
        return instance;
    }

    public synchronized void applyToSession(int sessionId) {
        if (sessionId == 0) return;
        try {
            if (presetReverb != null) {
                try { presetReverb.release(); } catch (Exception ignored) {}
            }
            presetReverb = new PresetReverb(0, sessionId);
            presetReverb.setEnabled(enabled);
            presetReverb.setPreset(preset);
        } catch (Exception e) {
            Log.e(TAG, "Failed to initialize PresetReverb effect", e);
        }
    }

    public synchronized void setEnabled(boolean enabled) {
        this.enabled = enabled;
        if (presetReverb != null) {
            try {
                presetReverb.setEnabled(enabled);
            } catch (Exception e) {
                Log.e(TAG, "Error toggling PresetReverb", e);
            }
        }
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
                .edit().putBoolean(KEY_ECHO_ENABLED, enabled).apply();
    }

    public synchronized boolean isEnabled() {
        return enabled;
    }

    public synchronized void setPreset(short preset) {
        this.preset = preset;
        if (presetReverb != null) {
            try {
                presetReverb.setPreset(this.preset);
            } catch (Exception e) {
                Log.e(TAG, "Error setting PresetReverb preset", e);
            }
        }
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
                .edit().putInt(KEY_ECHO_PRESET, this.preset).apply();
    }

    public synchronized short getPreset() {
        return preset;
    }
}
