package xime.core.theme;

import android.content.ComponentCallbacks2;
import android.content.Context;
import android.content.res.Configuration;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import xime.core.dispatch.Dispatcher;

/**
 * XIME.Core — Central theme state manager.
 *
 * <p>Single source of truth for dark/light mode across all XIME System Apps. Replaces the
 * previous architecture where each {@code BlurLayout} independently read
 * {@code Configuration.uiMode} and {@code Crystal.ThemeMode} was used as a rendering-level
 * state signal rather than a rendering-only hint.
 *
 * <h3>Usage</h3>
 * <pre>
 *   // In Application.onCreate() or the very first Activity:
 *   ThemeManager.init(context);
 *
 *   // Read current mode:
 *   boolean dark = ThemeManager.get().isDark();
 *
 *   // Subscribe (e.g. in a View's onAttachedToWindow):
 *   ThemeManager.get().addListener(listener);
 *
 *   // Unsubscribe (e.g. in onDetachedFromWindow):
 *   ThemeManager.get().removeListener(listener);
 *
 *   // Force override (Settings.apk equivalent):
 *   ThemeManager.get().setForceMode(ThemeManager.Mode.DARK);
 *   ThemeManager.get().clearForceMode(); // back to AUTO
 * </pre>
 *
 * <h3>Threading</h3>
 * <ul>
 *   <li>{@link #getMode()} / {@link #isDark()} are safe to call from any thread
 *       ({@code volatile} field).</li>
 *   <li>{@link OnThemeChangedListener#onThemeChanged} is always delivered on the main
 *       thread via {@link Dispatcher#main(Runnable)}.</li>
 * </ul>
 */
public final class ThemeManager {

    // -------------------------------------------------------------------------
    // Public API
    // -------------------------------------------------------------------------

    /**
     * Resolved theme mode. {@code DARK} and {@code LIGHT} are the only runtime values
     * returned by {@link #getMode()}. The AUTO policy (follow system) is an internal
     * implementation detail, not exposed here.
     */
    public enum Mode {
        DARK,
        LIGHT
    }

    /** Callback delivered on the main thread whenever the effective mode changes. */
    public interface OnThemeChangedListener {
        void onThemeChanged(@NonNull Mode newMode);
    }

    // -------------------------------------------------------------------------
    // Singleton
    // -------------------------------------------------------------------------

    private static volatile ThemeManager sInstance;

    /**
     * Returns the singleton. Must be called after {@link #init(Context)}.
     *
     * @throws IllegalStateException if {@link #init(Context)} has not been called yet.
     */
    @NonNull
    public static ThemeManager get() {
        ThemeManager instance = sInstance;
        if (instance == null) {
            throw new IllegalStateException(
                    "ThemeManager.init(Context) must be called before ThemeManager.get(). "
                            + "Call it in Application.onCreate() or the earliest available Context.");
        }
        return instance;
    }

    /**
     * Initialises the singleton. Safe to call multiple times — subsequent calls are no-ops.
     * Accepts any {@link Context}; the Application context is extracted internally so no
     * Activity or View reference is retained.
     */
    public static void init(@NonNull Context context) {
        if (sInstance == null) {
            synchronized (ThemeManager.class) {
                if (sInstance == null) {
                    sInstance = new ThemeManager(context.getApplicationContext());
                }
            }
        }
    }

    // -------------------------------------------------------------------------
    // State
    // -------------------------------------------------------------------------

    private final Context mAppContext;

    /** Effective resolved mode. volatile so reads from any thread are safe. */
    private volatile Mode mEffectiveMode;

    /**
     * When non-null, overrides the system-detected mode. Controlled by
     * {@link #setForceMode(Mode)} / {@link #clearForceMode()}.
     */
    @Nullable
    private volatile Mode mForcedMode;

    /**
     * Weak-reference listener list. Using {@link WeakReference} means Views/Activities that
     * forget to call {@link #removeListener} do not cause memory leaks — dead references are
     * purged on the next {@link #notifyListeners} pass.
     */
    private final List<WeakReference<OnThemeChangedListener>> mListeners = new ArrayList<>();

    /** Guards access to {@link #mListeners} across add/remove/notify. */
    private final Object mListenerLock = new Object();

    // -------------------------------------------------------------------------
    // Constructor
    // -------------------------------------------------------------------------

    private ThemeManager(@NonNull Context appContext) {
        mAppContext = appContext;
        mEffectiveMode = resolveSystemMode(appContext);
        registerConfigurationCallbacks();
    }

    // -------------------------------------------------------------------------
    // Public accessors
    // -------------------------------------------------------------------------

    /**
     * Returns the current effective mode. Always {@link Mode#DARK} or {@link Mode#LIGHT} —
     * never {@code null}. Safe to call from any thread.
     */
    @NonNull
    public Mode getMode() {
        return mEffectiveMode;
    }

    /**
     * Shorthand for {@code getMode() == Mode.DARK}. Safe to call from any thread.
     */
    public boolean isDark() {
        return mEffectiveMode == Mode.DARK;
    }

    /**
     * Forces a specific mode, overriding the system dark-mode setting. Pass {@link Mode#DARK}
     * or {@link Mode#LIGHT}. This is the hook for a HyperOS-style Settings.apk: once a forced
     * mode is set, system dark-mode changes are ignored until {@link #clearForceMode()} is
     * called.
     *
     * <p>Notifies all registered listeners if the effective mode changes.
     */
    public void setForceMode(@NonNull Mode mode) {
        mForcedMode = mode;
        applyMode(mode);
    }

    /**
     * Clears any forced override and reverts to following the system dark-mode setting.
     * Notifies all registered listeners if the effective mode changes.
     */
    public void clearForceMode() {
        mForcedMode = null;
        applyMode(resolveSystemMode(mAppContext));
    }

    // -------------------------------------------------------------------------
    // Listener management
    // -------------------------------------------------------------------------

    /**
     * Registers a listener. The listener is held via a {@link WeakReference}, so if the caller
     * is garbage-collected without calling {@link #removeListener}, no leak occurs — the dead
     * reference is silently pruned on the next notification cycle.
     *
     * <p>Call this in {@code onAttachedToWindow()} and pair with {@link #removeListener} in
     * {@code onDetachedFromWindow()}.
     */
    public void addListener(@NonNull OnThemeChangedListener listener) {
        synchronized (mListenerLock) {
            // Avoid duplicates — check if the same listener is already registered.
            for (WeakReference<OnThemeChangedListener> ref : mListeners) {
                if (ref.get() == listener) return;
            }
            mListeners.add(new WeakReference<>(listener));
        }
    }

    /**
     * Removes a previously registered listener. Safe to call even if the listener was never
     * added.
     */
    public void removeListener(@NonNull OnThemeChangedListener listener) {
        synchronized (mListenerLock) {
            Iterator<WeakReference<OnThemeChangedListener>> it = mListeners.iterator();
            while (it.hasNext()) {
                OnThemeChangedListener l = it.next().get();
                if (l == null || l == listener) {
                    it.remove();
                }
            }
        }
    }

    // -------------------------------------------------------------------------
    // Internal
    // -------------------------------------------------------------------------

    /**
     * Reads the current system UI night mode from the given context's configuration.
     * Returns {@link Mode#DARK} if the system is in night mode, {@link Mode#LIGHT} otherwise.
     */
    @NonNull
    private static Mode resolveSystemMode(@NonNull Context context) {
        int nightMode = context.getResources().getConfiguration().uiMode
                & Configuration.UI_MODE_NIGHT_MASK;
        return nightMode == Configuration.UI_MODE_NIGHT_YES ? Mode.DARK : Mode.LIGHT;
    }

    /**
     * Applies a new effective mode: updates {@link #mEffectiveMode} and notifies listeners only
     * if the mode actually changed.
     */
    private void applyMode(@NonNull Mode newMode) {
        if (newMode != mEffectiveMode) {
            mEffectiveMode = newMode;
            notifyListeners(newMode);
        }
    }

    /**
     * Delivers the mode change to all registered (still-alive) listeners on the main thread.
     * Dead {@link WeakReference}s are pruned during iteration.
     */
    private void notifyListeners(@NonNull Mode newMode) {
        // Snapshot the live listeners under lock, then dispatch outside the lock
        // so listener callbacks can safely call addListener/removeListener.
        final List<OnThemeChangedListener> snapshot;
        synchronized (mListenerLock) {
            snapshot = new ArrayList<>(mListeners.size());
            Iterator<WeakReference<OnThemeChangedListener>> it = mListeners.iterator();
            while (it.hasNext()) {
                OnThemeChangedListener l = it.next().get();
                if (l == null) {
                    it.remove(); // prune dead reference
                } else {
                    snapshot.add(l);
                }
            }
        }

        Dispatcher.main(() -> {
            for (OnThemeChangedListener listener : snapshot) {
                listener.onThemeChanged(newMode);
            }
        });
    }

    /**
     * Registers a {@link ComponentCallbacks2} on the Application context. This receives
     * {@link ComponentCallbacks2#onConfigurationChanged} whenever the system configuration
     * changes (including UI night mode toggling), without needing an Activity context.
     */
    private void registerConfigurationCallbacks() {
        mAppContext.registerComponentCallbacks(new ComponentCallbacks2() {
            @Override
            public void onConfigurationChanged(@NonNull Configuration newConfig) {
                // If a forced mode is active, system changes are intentionally ignored.
                if (mForcedMode != null) return;

                int nightMask = newConfig.uiMode & Configuration.UI_MODE_NIGHT_MASK;
                boolean systemIsDark = nightMask == Configuration.UI_MODE_NIGHT_YES;
                applyMode(systemIsDark ? Mode.DARK : Mode.LIGHT);
            }

            @Override
            public void onLowMemory() {
                // No-op: ThemeManager holds no bitmaps or large allocations.
            }

            @Override
            public void onTrimMemory(int level) {
                // No-op.
            }
        });
    }
}
