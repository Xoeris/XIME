package xime.media.spectrum;

import android.os.Handler;
import android.os.Looper;
import android.view.Choreographer;
import java.util.concurrent.CopyOnWriteArrayList;

public class Spectrum {
    private static final float BAND_DISTRIBUTION_CURVE = 2.0f;
    private static volatile Spectrum INSTANCE;

    private final float[] rawFrequencies = new float[64];
    private final float[] smoothedFrequencies = new float[64];
    private float systemVolumeRatio = 1.0f;
    private final CopyOnWriteArrayList<OnSpectrumChangeListener> listeners = new CopyOnWriteArrayList<>();
    
    private long lastUpdateTime = 0;
    private long lastFrameTime = 0;

    public interface OnSpectrumChangeListener {
        void onSpectrumUpdate();
    }

    private Spectrum() {
        // Initialize the animator heartbeat on the main thread
        new Handler(Looper.getMainLooper()).post(this::startHeartbeat);
    }

    public static Spectrum getInstance() {
        if (INSTANCE == null) {
            synchronized (Spectrum.class) {
                if (INSTANCE == null) {
                    INSTANCE = new Spectrum();
                }
            }
        }
        return INSTANCE;
    }

    public void addListener(OnSpectrumChangeListener listener) {
        if (!listeners.contains(listener)) {
            listeners.add(listener);
        }
    }

    public void removeListener(OnSpectrumChangeListener listener) {
        listeners.remove(listener);
    }

    public void reset() {
        synchronized (this) {
            for (int i = 0; i < rawFrequencies.length; i++) {
                rawFrequencies[i] = 0.0f;
            }
            lastUpdateTime = 0;
        }
    }

    public void updateFromAudio(float[] data) {
        if (data == null || data.length == 0) return;

        // Noise Gate: Ignore extremely low level signals to prevent jitter/glitching
        boolean hasSignal = false;
        for (float f : data) {
            if (f > 0.01f) {
                hasSignal = true;
                break;
            }
        }
        if (!hasSignal) return;

        synchronized (this) {
            int bands = rawFrequencies.length;
            int srcLen = data.length;

            if (srcLen == bands) {
                for (int i = 0; i < bands; i++) {
                    rawFrequencies[i] = data[i] * systemVolumeRatio;
                }
            } else {
                for (int b = 0; b < bands; b++) {
                    float startRatio = (float) b / bands;
                    float endRatio = (float) (b + 1) / bands;
                    int start = Math.max(0, (int) (startRatio * srcLen));
                    int end = Math.min(srcLen, Math.max(start + 1, (int) (endRatio * srcLen)));

                    float peak = 0f;
                    for (int i = start; i < end; i++) {
                        if (data[i] > peak) peak = data[i];
                    }
                    rawFrequencies[b] = peak * systemVolumeRatio;
                }
            }
            lastUpdateTime = System.currentTimeMillis();
        }
    }

    public void setSystemVolumeRatio(float ratio) {
        this.systemVolumeRatio = ratio;
    }

    public float sample(float position) {
        // High-precision sampling with Catmull-Rom spline interpolation
        float virtualIdx = position * (smoothedFrequencies.length - 1);
        int i1 = (int) virtualIdx;
        int i0 = Math.max(0, i1 - 1);
        int i2 = Math.min(smoothedFrequencies.length - 1, i1 + 1);
        int i3 = Math.min(smoothedFrequencies.length - 1, i1 + 2);

        float t = virtualIdx - i1;
        float t2 = t * t;
        float t3 = t2 * t;

        // Catmull-Rom weights
        float f0 = -0.5f * t3 + t2 - 0.5f * t;
        float f1 = 1.5f * t3 - 2.5f * t2 + 1.0f;
        float f2 = -1.5f * t3 + 2.0f * t2 + 0.5f * t;
        float f3 = 0.5f * t3 - 0.5f * t2;

        float val = smoothedFrequencies[i0] * f0 + smoothedFrequencies[i1] * f1 +
                    smoothedFrequencies[i2] * f2 + smoothedFrequencies[i3] * f3;

        return Math.max(0, val);
    }

    private void startHeartbeat() {
        Choreographer.getInstance().postFrameCallback(new Choreographer.FrameCallback() {
            @Override
            public void doFrame(long frameTimeNanos) {
                long now = System.currentTimeMillis();
                float dt = lastFrameTime == 0 ? 0.016f : (frameTimeNanos - lastFrameTime) / 1_000_000_000f;
                lastFrameTime = frameTimeNanos;

                // Safety clamp for dt
                if (dt > 0.1f) dt = 0.1f;

                synchronized (Spectrum.this) {
                    boolean isActive = (now - lastUpdateTime < 500);
                    for (int i = 0; i < smoothedFrequencies.length; i++) {
                        float target = isActive ? rawFrequencies[i] : 0.0f;

                        // Ultra-smooth attack/decay physics
                        float attackSpeed = 45f;
                        float decaySpeed = 12f;
                        float speed = (target > smoothedFrequencies[i]) ? attackSpeed : decaySpeed;

                        // Use exponential smoothing for more "natural" movement
                        float factor = 1.0f - (float) Math.exp(-speed * dt);
                        smoothedFrequencies[i] += (target - smoothedFrequencies[i]) * factor;

                        if (smoothedFrequencies[i] < 0.001f) smoothedFrequencies[i] = 0;
                        if (smoothedFrequencies[i] > 1.0f) smoothedFrequencies[i] = 1.0f;
                    }
                }

                for (OnSpectrumChangeListener listener : listeners) {
                    listener.onSpectrumUpdate();
                }
                Choreographer.getInstance().postFrameCallback(this);
            }
        });
    }
}
