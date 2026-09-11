package xime.animation;

import android.view.View;
import androidx.dynamicanimation.animation.SpringAnimation;
import androidx.dynamicanimation.animation.SpringForce;
import androidx.dynamicanimation.animation.DynamicAnimation;

public class SpringAnimator extends Animator {
    private float stiffness = SpringForce.STIFFNESS_MEDIUM;
    private float dampingRatio = SpringForce.DAMPING_RATIO_MEDIUM_BOUNCY;

    private SpringAnimator(Object target) {
        super(target);
    }

    public static SpringAnimator of(Object target) {
        return new SpringAnimator(target);
    }

    public SpringAnimator setStiffness(float stiffness) {
        this.stiffness = stiffness;
        return this;
    }

    public SpringAnimator setDampingRatio(float dampingRatio) {
        this.dampingRatio = dampingRatio;
        return this;
    }

    public SpringAnimator translateY(float finalPosition) {
        if (target instanceof View) {
            getAnimation(DynamicAnimation.TRANSLATION_Y, finalPosition).start();
        }
        return this;
    }

    public SpringAnimator translateX(float finalPosition) {
        if (target instanceof View) {
            getAnimation(DynamicAnimation.TRANSLATION_X, finalPosition).start();
        }
        return this;
    }

    @Override
    public SpringAnimator scaleX(float finalScale) {
        if (target instanceof View) {
            getAnimation(DynamicAnimation.SCALE_X, finalScale).start();
        }
        return this;
    }

    @Override
    public SpringAnimator scaleY(float finalScale) {
        if (target instanceof View) {
            getAnimation(DynamicAnimation.SCALE_Y, finalScale).start();
        }
        return this;
    }

    @Override
    public SpringAnimator alpha(float finalAlpha) {
        if (target instanceof View) {
            getAnimation(DynamicAnimation.ALPHA, finalAlpha).start();
        }
        return this;
    }

    private SpringAnimation getAnimation(DynamicAnimation.ViewProperty property, float finalPosition) {
        if (!(target instanceof View)) return null;
        SpringAnimation anim = new SpringAnimation((View) target, property, finalPosition);
        anim.getSpring().setStiffness(stiffness);
        anim.getSpring().setDampingRatio(dampingRatio);
        return anim;
    }
}
