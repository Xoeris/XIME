package xime.animation;

import android.view.animation.Interpolator;

public class MotionAnimator extends Animator {

    protected MotionAnimator(Object target) {
        super(target);
    }

    public static MotionAnimator with(Object target) {
        return new MotionAnimator(target);
    }

    public MotionAnimator translationX(float translationX) {
        if (target instanceof android.view.View) {
            ((android.view.View) target).animate().translationX(translationX).setDuration(duration).setInterpolator(interpolator).start();
        }
        return this;
    }

    public MotionAnimator translationY(float translationY) {
        if (target instanceof android.view.View) {
            ((android.view.View) target).animate().translationY(translationY).setDuration(duration).setInterpolator(interpolator).start();
        }
        return this;
    }

    public MotionAnimator translationZ(float translationZ) {
        if (target instanceof android.view.View) {
            ((android.view.View) target).animate().translationZ(translationZ).setDuration(duration).setInterpolator(interpolator).start();
        }
        return this;
    }

    public MotionAnimator rotation(float rotation) {
        if (target instanceof android.view.View) {
            ((android.view.View) target).animate().rotation(rotation).setDuration(duration).setInterpolator(interpolator).start();
        }
        return this;
    }

    public MotionAnimator rotationX(float rotationX) {
        if (target instanceof android.view.View) {
            ((android.view.View) target).animate().rotationX(rotationX).setDuration(duration).setInterpolator(interpolator).start();
        }
        return this;
    }

    public MotionAnimator rotationY(float rotationY) {
        if (target instanceof android.view.View) {
            ((android.view.View) target).animate().rotationY(rotationY).setDuration(duration).setInterpolator(interpolator).start();
        }
        return this;
    }

    @Override
    public MotionAnimator curve(Interpolator interpolator) {
        super.curve(interpolator);
        return this;
    }

    @Override
    public MotionAnimator duration(long duration) {
        super.duration(duration);
        return this;
    }

    @Override
    public MotionAnimator alpha(float alpha) {
        super.alpha(alpha);
        return this;
    }

    @Override
    public MotionAnimator scaleX(float scale) {
        super.scaleX(scale);
        return this;
    }

    @Override
    public MotionAnimator scaleY(float scale) {
        super.scaleY(scale);
        return this;
    }

    @Override
    public MotionAnimator fluid() {
        super.fluid();
        return this;
    }
}
