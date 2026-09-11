package xime.animation;

import android.view.animation.Interpolator;

public class Animator {

    protected final Object target;
    protected Interpolator interpolator;
    protected long duration = 300;

    protected Animator(Object target) {
        this.target = target;
    }

    public static Animator with(Object target) {
        return new Animator(target);
    }

    public Animator curve(Interpolator interpolator) {
        this.interpolator = interpolator;
        return this;
    }

    public Animator duration(long duration) {
        this.duration = duration;
        return this;
    }

    public Animator alpha(float alpha) {
        if (target instanceof android.view.View) {
            ((android.view.View) target).animate().alpha(alpha).setDuration(duration).setInterpolator(interpolator).start();
        }
        return this;
    }

    public Animator scaleX(float scale) {
        if (target instanceof android.view.View) {
            ((android.view.View) target).animate().scaleX(scale).setDuration(duration).setInterpolator(interpolator).start();
        }
        return this;
    }

    public Animator scaleY(float scale) {
        if (target instanceof android.view.View) {
            ((android.view.View) target).animate().scaleY(scale).setDuration(duration).setInterpolator(interpolator).start();
        }
        return this;
    }

    public Animator fluid() {
        this.interpolator = MotionCurve.FLUID;
        return this;
    }

    public void start() {}

    public void popIn() {
        if (target instanceof android.view.View) {
            android.view.View v = (android.view.View) target;
            v.setAlpha(0f);
            v.setScaleX(0.8f);
            v.setScaleY(0.8f);
            v.animate().alpha(1f).scaleX(1f).scaleY(1f).setDuration(duration).setInterpolator(interpolator).start();
        }
    }
}
