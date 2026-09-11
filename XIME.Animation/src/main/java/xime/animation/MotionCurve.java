package xime.animation;

import android.view.animation.Interpolator;
import android.view.animation.PathInterpolator;

public class MotionCurve {
    public static final Interpolator FLUID = new PathInterpolator(0.4f, 0f, 0.2f, 1f);

    /**
     * STANDARD — Standard Material-like curve.
     */
    public static final Interpolator STANDARD = new PathInterpolator(0.4f, 0f, 0.2f, 1f);
}

