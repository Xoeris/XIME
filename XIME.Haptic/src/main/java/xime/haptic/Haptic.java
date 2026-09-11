package xime.haptic;

public interface Haptic {
    void perform(HapticEvent event);
    void perform(HapticProfile profile);
    void perform(HapticPattern pattern, HapticIntensity intensity);
    void cancel();
    boolean isAvailable();
}
