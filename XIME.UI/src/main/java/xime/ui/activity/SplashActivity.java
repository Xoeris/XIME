package xime.ui.activity;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.os.Bundle;
import android.view.animation.AccelerateDecelerateInterpolator;

import androidx.annotation.Nullable;

import xime.R;
import xime.ui.bar.LinearProgressBar;
import xime.ui.view.ImageView;
import xime.ui.view.TextView;

public abstract class SplashActivity extends Activity {

    public static final long DEFAULT_SPLASH_DURATION = 500;

    protected ImageView logoView;
    protected TextView titleView;
    protected TextView subtitleView;
    protected TextView statusView;
    protected LinearProgressBar linearTrackBar;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.splash_activity);

        initViews();
        onPrepareSplash();
    }

    private void initViews() {
        logoView = findViewById(R.id.splash_logo);
        titleView = findViewById(R.id.splash_title);
        subtitleView = findViewById(R.id.splash_subtitle);
        statusView = findViewById(R.id.splash_status);
        linearTrackBar = (LinearProgressBar) findViewById(R.id.splash_indicator);
    }

    /**
     * Called after views are initialized. Subclasses should set their content here.
     */
    protected abstract void onPrepareSplash();

    /**
     * Starts a standard loading animation with the default duration.
     * @param onComplete Callback when the animation finishes.
     */
    protected void startLoadingAnimation(Runnable onComplete) {
        startLoadingAnimation(DEFAULT_SPLASH_DURATION, onComplete);
    }

    /**
     * Starts a standard loading animation.
     * @param duration Milliseconds for the animation to complete.
     * @param onComplete Callback when the animation finishes.
     */
    protected void startLoadingAnimation(long duration, Runnable onComplete) {
        ValueAnimator animator = ValueAnimator.ofInt(0, 100);
        animator.setDuration(duration);
        animator.setInterpolator(new AccelerateDecelerateInterpolator());
        animator.addUpdateListener(animation -> {
            int progress = (int) animation.getAnimatedValue();
            if (linearTrackBar != null) {
                linearTrackBar.setProgress(progress);
            }
            onProgressUpdate(progress);
        });

        animator.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                if (onComplete != null) onComplete.run();
            }
        });

        animator.start();
    }

    /**
     * Override to update status text or perform other logic based on progress.
     */
    protected void onProgressUpdate(int progress) {
        // Optional override
    }
}
