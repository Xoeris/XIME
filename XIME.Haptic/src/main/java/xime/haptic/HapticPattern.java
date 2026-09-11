package xime.haptic;

public class HapticPattern {
    public static final long[] TICK = {0, 10};
    public static final long[] MICRO_TICK = {0, 7};
    public static final long[] CLICK = {0, 20};
    public static final long[] DOUBLE_CLICK = {0, 20, 100, 20};
    public static final long[] SUCCESS = {0, 20, 100, 30, 50, 40};
    public static final long[] WARNING = {0, 40, 120, 40, 120, 40};
    public static final long[] ERROR = {0, 60, 100, 60, 100, 60, 100, 60};
    public static final long[] LONG_PRESS = {0, 50};
    
    // Specialized patterns for UX interactions
    public static final long[] PULSE_LIGHT = {0, 15};
    public static final long[] PULSE_DEEP = {0, 45};
    public static final long[] FLUID_EXPAND = {0, 10, 20, 25};
    public static final long[] SINE_REFLECT = {0, 5, 15, 5, 15, 5};

    private final long[] timings;
    private final int[] amplitudes;

    public HapticPattern(long[] timings) {
        this.timings = timings;
        this.amplitudes = null;
    }

    public HapticPattern(long[] timings, int[] amplitudes) {
        this.timings = timings;
        this.amplitudes = amplitudes;
    }

    public long[] getTimings() {
        return timings;
    }

    public int[] getAmplitudes() {
        return amplitudes;
    }
}
