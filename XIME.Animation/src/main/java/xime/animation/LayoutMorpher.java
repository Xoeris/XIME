package xime.animation;

import android.view.ViewGroup;
import android.view.animation.PathInterpolator;

import androidx.transition.ArcMotion;
import androidx.transition.ChangeBounds;
import androidx.transition.ChangeImageTransform;
import androidx.transition.ChangeTransform;
import androidx.transition.Fade;
import androidx.transition.Transition;
import androidx.transition.TransitionManager;
import androidx.transition.TransitionSet;

public class LayoutMorpher {

    public static final int DEFAULT_DURATION = 350;

    public static void morph(ViewGroup root, Runnable task) {
        morph(root, DEFAULT_DURATION, task);
    }

    public static void morph(ViewGroup root, int duration, Runnable task) {
        TransitionSet set = new TransitionSet();
        ChangeBounds changeBounds = new ChangeBounds();
        changeBounds.setPathMotion(new ArcMotion());
        
        set.addTransition(changeBounds);
        set.addTransition(new Fade(Fade.IN));
        set.addTransition(new Fade(Fade.OUT));
        
        set.setOrdering(TransitionSet.ORDERING_TOGETHER);
        set.setDuration(duration);
        set.setInterpolator(new PathInterpolator(0.2f, 0.0f, 0.2f, 1.0f));
        
        TransitionManager.beginDelayedTransition(root, set);
        task.run();
    }

    public static Transition createMorphTransition() {
        return createMorphTransition(DEFAULT_DURATION);
    }

    public static Transition createMorphTransition(int duration) {
        TransitionSet set = new TransitionSet();
        set.addTransition(new ChangeBounds());
        set.addTransition(new ChangeTransform());
        set.addTransition(new ChangeImageTransform());
        set.addTransition(new Fade());
        
        set.setOrdering(TransitionSet.ORDERING_TOGETHER);
        set.setDuration(duration);
        set.setInterpolator(new PathInterpolator(0.4f, 0f, 0.2f, 1f));
        set.setPathMotion(new ArcMotion());

        // Enable matching by transitionName for cross-layout morphing
        set.setMatchOrder(Transition.MATCH_NAME, Transition.MATCH_ID, Transition.MATCH_INSTANCE);
        
        return set;
    }

    public static Transition createBounceTransition(int duration) {
        TransitionSet set = new TransitionSet();
        set.addTransition(new ChangeBounds());
        set.addTransition(new ChangeTransform());
        set.addTransition(new ChangeImageTransform());
        set.addTransition(new Fade());
        
        set.setOrdering(TransitionSet.ORDERING_TOGETHER);
        set.setDuration(duration);
        // Fast -> Linear -> Slow curve (FastOutSlowIn)
        set.setInterpolator(new PathInterpolator(0.4f, 0f, 0.2f, 1f));
        set.setPathMotion(new ArcMotion());

        set.setMatchOrder(Transition.MATCH_NAME, Transition.MATCH_ID, Transition.MATCH_INSTANCE);
        
        return set;
    }
}
