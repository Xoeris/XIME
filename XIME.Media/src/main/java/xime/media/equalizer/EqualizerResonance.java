package xime.media.equalizer;

import android.content.Context;
import android.content.SharedPreferences;

public class EqualizerResonance {
    private static final String PREF_NAME = "XoerisResonancePrefs";
    private static final String KEY_RESONANCE_LEVEL = "resonance_level";
    private static final String KEY_RESONANCE_ENABLED = "resonance_enabled";

    private static EqualizerResonance instance;
    private final Context context;
    private int level = 0;
    private boolean enabled = false;

    private double b0, b1, b2, a1, a2;
    private double x1_l, x2_l, y1_l, y2_l;
    private double x1_r, x2_r, y1_r, y2_r;

    private EqualizerResonance(Context context) {
        this.context = context.getApplicationContext();
        SharedPreferences prefs = this.context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        this.level = prefs.getInt(KEY_RESONANCE_LEVEL, 0);
        this.enabled = prefs.getBoolean(KEY_RESONANCE_ENABLED, false);
        recalculateCoefficients();
    }

    public static synchronized EqualizerResonance getInstance(Context context) {
        if (instance == null) {
            instance = new EqualizerResonance(context);
        }
        return instance;
    }

    public synchronized void setEnabled(boolean enabled) {
        this.enabled = enabled;
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
                .edit().putBoolean(KEY_RESONANCE_ENABLED, enabled).apply();
    }

    public synchronized boolean isEnabled() {
        return enabled;
    }

    public synchronized void setLevel(int level) {
        this.level = Math.max(0, Math.min(100, level));
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
                .edit().putInt(KEY_RESONANCE_LEVEL, this.level).apply();
        recalculateCoefficients();
    }

    public synchronized int getLevel() {
        return level;
    }

    private void recalculateCoefficients() {
        double frequency = 1500.0;
        double sampleRate = 44100.0;
        double omega = 2.0 * Math.PI * frequency / sampleRate;
        double sinOmega = Math.sin(omega);
        double cosOmega = Math.cos(omega);
        
        double q = 0.5 + (level / 100.0) * 4.0;
        double alpha = sinOmega / (2.0 * q);

        double a0 = 1.0 + alpha;
        b0 = alpha / a0;
        b1 = 0.0;
        b2 = -alpha / a0;
        a1 = -2.0 * cosOmega / a0;
        a2 = (1.0 - alpha) / a0;
    }

    public synchronized byte[] process(byte[] pcmData) {
        if (!enabled || level == 0 || pcmData == null) return pcmData;

        int len = pcmData.length / 2;
        for (int i = 0; i < len; i += 2) {
            int lowL = pcmData[2 * i] & 0xFF;
            int highL = pcmData[2 * i + 1];
            double sampleL = (short) ((highL << 8) | lowL);

            double outL = b0 * sampleL + b1 * x1_l + b2 * x2_l - a1 * y1_l - a2 * y2_l;
            x2_l = x1_l;
            x1_l = sampleL;
            y2_l = y1_l;
            y1_l = outL;

            if (outL > 32767) outL = 32767;
            else if (outL < -32768) outL = -32768;

            short outShortL = (short) outL;
            pcmData[2 * i] = (byte) (outShortL & 0xFF);
            pcmData[2 * i + 1] = (byte) ((outShortL >> 8) & 0xFF);

            if (2 * i + 3 < pcmData.length) {
                int lowR = pcmData[2 * i + 2] & 0xFF;
                int highR = pcmData[2 * i + 3];
                double sampleR = (short) ((highR << 8) | lowR);

                double outR = b0 * sampleR + b1 * x1_r + b2 * x2_r - a1 * y1_r - a2 * y2_r;
                x2_r = x1_r;
                x1_r = sampleR;
                y2_r = y1_r;
                y1_r = outR;

                if (outR > 32767) outR = 32767;
                else if (outR < -32768) outR = -32768;

                short outShortR = (short) outR;
                pcmData[2 * i + 2] = (byte) (outShortR & 0xFF);
                pcmData[2 * i + 3] = (byte) ((outShortR >> 8) & 0xFF);
            }
        }
        return pcmData;
    }
}
