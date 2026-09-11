package xime.animation;

import android.graphics.Color;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.PathInterpolator;
import android.util.Pair;

import androidx.annotation.IdRes;
import androidx.fragment.app.DialogFragment;
import androidx.interpolator.view.animation.FastOutSlowInInterpolator;
import androidx.transition.TransitionManager;
import androidx.transition.TransitionSet;

import com.google.android.material.transition.MaterialArcMotion;
import com.google.android.material.transition.MaterialContainerTransform;

public class ViewportMorpher {

    public static void setup(DialogFragment fragment, @IdRes int startViewId, String transitionName, @IdRes int drawingViewId) {
        // Enter Transition
        MaterialContainerTransform enterTransform = new MaterialContainerTransform();
        enterTransform.setStartViewId(startViewId);
        enterTransform.setEndViewId(android.R.id.content);
        enterTransform.addTarget(android.R.id.content);
        enterTransform.setDrawingViewId(drawingViewId);
        
        enterTransform.setDuration(600);
        enterTransform.setPathMotion(new MaterialArcMotion());
        enterTransform.setInterpolator(new PathInterpolator(0.2f, 0.0f, 0.2f, 1.0f));
        enterTransform.setFadeMode(MaterialContainerTransform.FADE_MODE_THROUGH);
        
        // Performance: Use simple transparency for transition container
        enterTransform.setContainerColor(Color.TRANSPARENT);
        enterTransform.setScrimColor(Color.TRANSPARENT);
        enterTransform.setTransitionDirection(MaterialContainerTransform.TRANSITION_DIRECTION_ENTER);
        
        fragment.setEnterTransition(enterTransform);
        fragment.setSharedElementEnterTransition(enterTransform);

        // Return Transition (Reverse morph)
        MaterialContainerTransform returnTransform = new MaterialContainerTransform();
        returnTransform.setStartViewId(android.R.id.content);
        returnTransform.setEndViewId(startViewId);
        returnTransform.addTarget(startViewId);
        returnTransform.setDrawingViewId(drawingViewId);
        
        returnTransform.setDuration(500);
        returnTransform.setPathMotion(new MaterialArcMotion());
        returnTransform.setInterpolator(new PathInterpolator(0.32f, 0.94f, 0.6f, 1.0f));
        returnTransform.setFadeMode(MaterialContainerTransform.FADE_MODE_THROUGH);
        
        returnTransform.setContainerColor(Color.TRANSPARENT);
        returnTransform.setScrimColor(Color.TRANSPARENT);
        
        fragment.setReturnTransition(returnTransform);
        fragment.setSharedElementReturnTransition(returnTransform);
    }

    /**
     * Configures a DialogFragment to morph from a specific transition name.
     * 
     * @param fragment The fragment to apply the transition to.
     * @param transitionName The unique name shared with the start base.
     * @param drawingViewId The ID of the base to draw the transition in (usually android.R.id.content).
     */
    public static void setup(DialogFragment fragment, String transitionName, @IdRes int drawingViewId) {
        MaterialContainerTransform transform = new MaterialContainerTransform();
        
        // 1. Target the specific shared element name
        transform.addTarget(transitionName);

        // 2. Core Configuration
        transform.setDrawingViewId(drawingViewId);
        transform.setDuration(500);
        transform.setInterpolator(new PathInterpolator(0.32f, 0.94f, 0.6f, 1.0f));
        
        // 3. Visual Style (Transparent container for AmbientGlass effect)
        transform.setFadeMode(MaterialContainerTransform.FADE_MODE_THROUGH);
        transform.setContainerColor(Color.TRANSPARENT);
        transform.setScrimColor(Color.TRANSPARENT);
        
        // 4. Setup Transitions
        fragment.setEnterTransition(transform);
        fragment.setReturnTransition(transform);
    }

    public static void setup(DialogFragment fragment, String transitionName) {
        setup(fragment, transitionName, android.R.id.content);
    }

    /**
     * Executes manual morphs for multiple views from an Activity to a DialogFragment.
     * This bypasses the Window barrier of DialogFragments by explicitly setting start and end views.
     */
    @SafeVarargs
    public static void executeElementMorphs(@IdRes int drawingViewId, Pair<View, View>... viewPairs) {
        if (viewPairs == null || viewPairs.length == 0) return;
        
        ViewGroup sceneRoot = (ViewGroup) viewPairs[0].second.getParent();
        if (sceneRoot == null) return;

        TransitionSet set = new TransitionSet();
        set.setDuration(500);
        set.setInterpolator(new FastOutSlowInInterpolator());

        for (Pair<View, View> pair : viewPairs) {
            if (pair.first == null || pair.second == null) continue;

            MaterialContainerTransform transform = new MaterialContainerTransform();
            transform.setStartView(pair.first);
            transform.setEndView(pair.second);
            transform.addTarget(pair.second);
            transform.setDrawingViewId(drawingViewId);
            transform.setScrimColor(Color.TRANSPARENT);
            transform.setContainerColor(Color.TRANSPARENT);
            transform.setFadeMode(MaterialContainerTransform.FADE_MODE_CROSS);
            set.addTransition(transform);
        }

        TransitionManager.beginDelayedTransition(sceneRoot, set);
    }
}

