package xime.animation;

import android.view.animation.Interpolator;

/**
 * A smooth spring-like interpolator to mimic HyperOS / iOS feel.
 */
public class SpringInterpolator implements Interpolator {
    
    private final float factor;

    public SpringInterpolator() {
        this(0.4f);
    }

    public SpringInterpolator(float factor) {
        this.factor = factor;
    }

    @Override
    public float getInterpolation(float input) {
        return (float) (Math.pow(2, -10 * input) * Math.sin((input - factor / 4) * (2 * Math.PI) / factor) + 1);
    }
}
