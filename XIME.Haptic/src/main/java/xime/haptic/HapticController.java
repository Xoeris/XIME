package xime.haptic;

import android.content.Context;
import android.media.AudioAttributes;
import android.os.Build;
import android.os.Looper;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.os.VibratorManager;

import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

public class HapticController implements Haptic {
    
    private final Vibrator vibrator;
    private static final Executor asyncExecutor = Executors.newSingleThreadExecutor();

    public HapticController(Context context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            VibratorManager vibratorManager = (VibratorManager) context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE);
            this.vibrator = (vibratorManager != null) ? vibratorManager.getDefaultVibrator() : null;
        } else {
            this.vibrator = (Vibrator) context.getSystemService(Context.VIBRATOR_SERVICE);
        }
    }

    @Override
    public void perform(HapticEvent event) {
        if (vibrator == null || !vibrator.hasVibrator()) return;

        long[] timings = event.getPattern().getTimings();
        HapticIntensity intensity = event.getIntensity();

        // 1. High-Fidelity Primitive Composition (Android 11 / API 30+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            if (tryComposition(timings, intensity)) return;
        }

        // 2. Predefined Effect Layer (Android 10 / API 29+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            if (tryPredefined(timings)) return;
        }

        // 3. Amplitude-Modulated Waveform Layer (Android 8 / API 26+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            performWaveform(event);
        } else {
            // 4. Legacy Layer (Pre-Oreo)
            vibrator.vibrate(timings, -1);
        }
    }

    private boolean tryComposition(long[] timings, HapticIntensity intensity) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return false;

        VibrationEffect.Composition composition = VibrationEffect.startComposition();
        float scale = mapIntensityToScale(intensity);

        if (timings == HapticPattern.MICRO_TICK) {
            // Use LOW_TICK (API 31+) or fall back within the composition
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && 
                vibrator.areAllPrimitivesSupported(VibrationEffect.Composition.PRIMITIVE_LOW_TICK)) {
                composition.addPrimitive(VibrationEffect.Composition.PRIMITIVE_LOW_TICK, scale * 0.4f);
            } else if (vibrator.areAllPrimitivesSupported(VibrationEffect.Composition.PRIMITIVE_TICK)) {
                composition.addPrimitive(VibrationEffect.Composition.PRIMITIVE_TICK, scale * 0.2f);
            } else {
                return false;
            }
        } else if (timings == HapticPattern.TICK) {
            if (vibrator.areAllPrimitivesSupported(VibrationEffect.Composition.PRIMITIVE_TICK)) {
                composition.addPrimitive(VibrationEffect.Composition.PRIMITIVE_TICK, scale * 0.6f);
            } else {
                return false;
            }
        } else if (timings == HapticPattern.CLICK) {
            if (vibrator.areAllPrimitivesSupported(VibrationEffect.Composition.PRIMITIVE_CLICK)) {
                composition.addPrimitive(VibrationEffect.Composition.PRIMITIVE_CLICK, scale);
            } else {
                return false;
            }
        } else if (timings == HapticPattern.LONG_PRESS) {
            if (vibrator.areAllPrimitivesSupported(VibrationEffect.Composition.PRIMITIVE_QUICK_RISE, VibrationEffect.Composition.PRIMITIVE_TICK)) {
                composition.addPrimitive(VibrationEffect.Composition.PRIMITIVE_QUICK_RISE, scale * 0.5f);
                composition.addPrimitive(VibrationEffect.Composition.PRIMITIVE_TICK, scale, 50);
            } else {
                return false;
            }
        } else {
            return false;
        }

        vibrate(composition.compose());
        return true;
    }

    private boolean tryPredefined(long[] timings) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return false;

        int effectId = -1;
        if (timings == HapticPattern.CLICK) effectId = VibrationEffect.EFFECT_CLICK;
        else if (timings == HapticPattern.TICK) effectId = VibrationEffect.EFFECT_TICK;
        else if (timings == HapticPattern.MICRO_TICK) effectId = VibrationEffect.EFFECT_TICK;

        if (effectId != -1) {
            try {
                vibrate(VibrationEffect.createPredefined(effectId));
                return true;
            } catch (Exception ignored) {}
        }
        return false;
    }

    private void performWaveform(HapticEvent event) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return;

        long[] timings = event.getPattern().getTimings();
        int intensityValue = HapticIntensity.toVibrationEffect(event.getIntensity());
        
        VibrationEffect effect;
        if (timings.length == 2 && timings[0] == 0) {
            long duration = Math.max(timings[1], 10); 
            effect = VibrationEffect.createOneShot(duration, intensityValue);
        } else {
            int[] amplitudes = new int[timings.length];
            for (int i = 0; i < timings.length; i++) {
                amplitudes[i] = (i % 2 == 0) ? 0 : intensityValue;
            }
            effect = VibrationEffect.createWaveform(timings, amplitudes, -1);
        }
        vibrate(effect);
    }

    private void vibrate(VibrationEffect effect) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            AudioAttributes attributes = new AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build();
            vibrator.vibrate(effect, attributes);
        }
    }

    private float mapIntensityToScale(HapticIntensity intensity) {
        switch (intensity) {
            case LIGHT: return 0.4f;
            case MEDIUM: return 0.7f;
            case STRONG: return 1.0f;
            default: return 0.6f;
        }
    }

    public void performAsync(HapticEvent event) {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            asyncExecutor.execute(() -> perform(event));
        } else {
            perform(event);
        }
    }

    @Override
    public void perform(HapticProfile profile) {
        for (HapticEvent event : profile.getEvents()) {
            perform(event);
        }
    }

    @Override
    public void perform(HapticPattern pattern, HapticIntensity intensity) {
        perform(new HapticEvent(pattern, intensity));
    }

    @Override
    public void cancel() {
        if (vibrator != null) vibrator.cancel();
    }

    @Override
    public boolean isAvailable() {
        return vibrator != null && vibrator.hasVibrator();
    }
}
