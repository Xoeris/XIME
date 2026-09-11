package xime.ui.utils;

public final class Kinetics {
    private float mXVelocity;
    private float mYVelocity;

    private Kinetics() {}

    public static Kinetics obtain() {
        return new Kinetics();
    }

    public void recycle() {
        // Implementation
    }

    public void clear() {
        mXVelocity = 0;
        mYVelocity = 0;
    }

    public void addMovement(Motion event) {
        // Implementation
    }

    public void computeCurrentVelocity(int units) {
        computeCurrentVelocity(units, Float.MAX_VALUE);
    }

    public void computeCurrentVelocity(int units, float maxVelocity) {
        // Implementation
    }

    public float getXVelocity() {
        return mXVelocity;
    }

    public float getYVelocity() {
        return mYVelocity;
    }

    public float getXVelocity(int id) {
        return mXVelocity;
    }

    public float getYVelocity(int id) {
        return mYVelocity;
    }
}
