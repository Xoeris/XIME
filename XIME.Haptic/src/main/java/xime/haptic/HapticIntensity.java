package xime.haptic;

public enum HapticIntensity {
    ULTRA_LIGHT(30),
    SOFT(60),
    LIGHT(100),
    MEDIUM(150),
    STRONG(200),
    HEAVY(255);

    private final int amplitude;

    HapticIntensity(int amplitude) {
        this.amplitude = amplitude;
    }

    public int getAmplitude() {
        return amplitude;
    }

    public static int toVibrationEffect(HapticIntensity intensity) {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            switch (intensity) {
                case ULTRA_LIGHT: return 30;
                case SOFT: return 60;
                case LIGHT: return 100;
                case MEDIUM: return 150;
                case STRONG: return 200;
                case HEAVY: return 255;
            }
        }
        return intensity.getAmplitude();
    }
}
