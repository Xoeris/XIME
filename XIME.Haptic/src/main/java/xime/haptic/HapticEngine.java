package xime.haptic;

import android.content.Context;
import android.os.SystemClock;
import android.view.View;

public class HapticEngine {
    private final HapticRuntime runtime;
    private final AnimatorHaptic animator;
    
    private static long lastTriggerTime = 0;
    private static final long DEBOUNCE_THRESHOLD = 80; // ms: prevent double triggers in a single event loop

    public HapticEngine(Context context) {
        this.runtime = HapticRuntime.getInstance(context);
        this.animator = new AnimatorHaptic(runtime.getController());
    }

    public synchronized void trigger(HapticPattern pattern, HapticIntensity intensity) {
        if (!runtime.isEnabled()) return;
        
        long currentTime = SystemClock.elapsedRealtime();
        if (currentTime - lastTriggerTime < DEBOUNCE_THRESHOLD) {
            return; // Ignore "Doubled" signals
        }
        
        lastTriggerTime = currentTime;
        runtime.getController().perform(pattern, intensity);
    }

    public void trigger(HapticProfile profile) {
        if (runtime.isEnabled()) {
            animator.animate(profile);
        }
    }

    public void triggerSuccess() {
        trigger(new HapticPattern(HapticPattern.SUCCESS), HapticIntensity.MEDIUM);
    }

    public void triggerError() {
        trigger(new HapticPattern(HapticPattern.ERROR), HapticIntensity.STRONG);
    }

    public void triggerClick() {
        trigger(new HapticPattern(HapticPattern.CLICK), HapticIntensity.LIGHT);
    }

    public void triggerTick() {
        trigger(new HapticPattern(HapticPattern.TICK), HapticIntensity.ULTRA_LIGHT);
    }

    public void onTap() {
        trigger(new HapticPattern(HapticPattern.MICRO_TICK), HapticIntensity.ULTRA_LIGHT);
    }

    public void onLongPress() {
        trigger(new HapticPattern(HapticPattern.PULSE_DEEP), HapticIntensity.MEDIUM);
    }

    public void onSineReflect() {
        trigger(new HapticPattern(HapticPattern.TICK), HapticIntensity.ULTRA_LIGHT);
    }

    public void onFluidExpand() {
        trigger(new HapticPattern(HapticPattern.FLUID_EXPAND), HapticIntensity.SOFT);
    }

    public static void feedback(View view, int feedbackConstant) {
        view.performHapticFeedback(feedbackConstant);
    }

    public void stop() {
        animator.stop();
    }
}
