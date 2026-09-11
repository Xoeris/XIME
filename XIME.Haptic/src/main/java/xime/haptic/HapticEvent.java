package xime.haptic;

public class HapticEvent {
    private final HapticPattern pattern;
    private final HapticIntensity intensity;
    private final long delay;

    public HapticEvent(HapticPattern pattern, HapticIntensity intensity) {
        this(pattern, intensity, 0);
    }

    public HapticEvent(HapticPattern pattern, HapticIntensity intensity, long delay) {
        this.pattern = pattern;
        this.intensity = intensity;
        this.delay = delay;
    }

    public HapticPattern getPattern() {
        return pattern;
    }

    public HapticIntensity getIntensity() {
        return intensity;
    }

    public long getDelay() {
        return delay;
    }
}
