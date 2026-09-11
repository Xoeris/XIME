package xime.media.equalizer;

import android.content.Context;
import android.content.SharedPreferences;
import android.media.audiofx.EnvironmentalReverb;
import android.util.Log;

public class EqualizerAcousticSpace {
    private static final String TAG = "EqualizerAcousticSpace";
    private static final String PREF_NAME = "XoerisAcousticSpacePrefs";
    private static final String KEY_ACOUSTIC_ENABLED = "acoustic_enabled";
    private static final String KEY_ACOUSTIC_ROOM = "acoustic_room";

    private static EqualizerAcousticSpace instance;
    private final Context context;
    private EnvironmentalReverb environmentalReverb;
    private boolean enabled = false;
    private short roomLevel = -1000; // room level in mB

    private EqualizerAcousticSpace(Context context) {
        this.context = context.getApplicationContext();
        SharedPreferences prefs = this.context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        this.enabled = prefs.getBoolean(KEY_ACOUSTIC_ENABLED, false);
        this.roomLevel = (short) prefs.getInt(KEY_ACOUSTIC_ROOM, -1000);
    }

    public static synchronized EqualizerAcousticSpace getInstance(Context context) {
        if (instance == null) {
            instance = new EqualizerAcousticSpace(context);
        }
        return instance;
    }

    public synchronized void applyToSession(int sessionId) {
        if (sessionId == 0) return;
        try {
            if (environmentalReverb != null) {
                try { environmentalReverb.release(); } catch (Exception ignored) {}
            }
            environmentalReverb = new EnvironmentalReverb(0, sessionId);
            environmentalReverb.setEnabled(enabled);
            environmentalReverb.setRoomLevel(roomLevel);
        } catch (Exception e) {
            Log.e(TAG, "Failed to initialize EnvironmentalReverb effect", e);
        }
    }

    public synchronized void setEnabled(boolean enabled) {
        this.enabled = enabled;
        if (environmentalReverb != null) {
            try {
                environmentalReverb.setEnabled(enabled);
            } catch (Exception e) {
                Log.e(TAG, "Error toggling EnvironmentalReverb", e);
            }
        }
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
                .edit().putBoolean(KEY_ACOUSTIC_ENABLED, enabled).apply();
    }

    public synchronized boolean isEnabled() {
        return enabled;
    }

    public synchronized void setRoomLevel(short roomLevel) {
        this.roomLevel = roomLevel;
        if (environmentalReverb != null) {
            try {
                environmentalReverb.setRoomLevel(this.roomLevel);
            } catch (Exception e) {
                Log.e(TAG, "Error setting EnvironmentalReverb room level", e);
            }
        }
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
                .edit().putInt(KEY_ACOUSTIC_ROOM, this.roomLevel).apply();
    }

    public synchronized short getRoomLevel() {
        return roomLevel;
    }
}
