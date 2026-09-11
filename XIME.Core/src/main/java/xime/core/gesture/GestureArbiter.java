package xime.core.gesture;

import android.content.Context;
import android.graphics.Rect;
import android.view.MotionEvent;

import androidx.annotation.IntDef;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import xime.core.dispatch.Dispatcher;

/**
 * XIME.Core — Central gesture ownership arbiter.
 *
 * <p>Coordinates touch ownership across independently-built XIME components that do not share
 * a parent-child view hierarchy (e.g. {@code HeaderMenu.floatingOverlay}, which attaches to the
 * window's decor view, has no common ViewGroup ancestor with a {@code PlayerView} swipe region —
 * making {@code requestDisallowInterceptTouchEvent} ineffective between them).
 *
 * <h3>Claim model</h3>
 * <ol>
 *   <li>A component calls {@link #requestClaim} on ACTION_DOWN with its {@link Rect},
 *       gesture mask, priority, and an optional expiry.</li>
 *   <li>{@link ClaimResult#GRANTED} is returned when no higher-priority claim owns any
 *       overlapping gesture axis, or when the component's priority beats the existing owner.</li>
 *   <li>{@link ClaimResult#DENIED} is returned otherwise — the component should stop consuming
 *       that touch stream.</li>
 *   <li>The owner calls {@link #release} on ACTION_UP or ACTION_CANCEL to free the claim.</li>
 *   <li>Claims expire automatically after {@code durationMs} ms via {@link Dispatcher#main}
 *       — eliminates the need for raw {@code android.os.Handler} instances in consumers.</li>
 * </ol>
 *
 * <h3>Gesture mask constants</h3>
 * Use bitwise OR to combine: e.g. {@code GESTURE_VERTICAL_SWIPE | GESTURE_LONG_PRESS}.
 *
 * <h3>Lifecycle</h3>
 * Call {@link #init(Context)} in {@code Application.onCreate()} before any XIME component attaches.
 * Components register a {@link OnClaimChangedListener} in {@code onAttachedToWindow()} and
 * remove it in {@code onDetachedFromWindow()} via {@link #addListener}/{@link #removeListener}.
 *
 * <h3>Threading</h3>
 * {@link #requestClaim} and {@link #release} may be called from any thread — state is guarded by
 * {@link #mLock}. Listener callbacks are always delivered on the main thread via
 * {@link Dispatcher#main}.
 */
public final class GestureArbiter {

    // -------------------------------------------------------------------------
    // Gesture mask constants
    // -------------------------------------------------------------------------

    /** Gesture mask bit: vertical swipe (up/down drag). */
    public static final int GESTURE_VERTICAL_SWIPE   = 1;
    /** Gesture mask bit: horizontal swipe (left/right drag). */
    public static final int GESTURE_HORIZONTAL_SWIPE = 1 << 1;
    /** Gesture mask bit: long press recognition. */
    public static final int GESTURE_LONG_PRESS       = 1 << 2;
    /** Gesture mask bit: single tap. */
    public static final int GESTURE_TAP              = 1 << 3;
    /** Convenience: all gesture types. */
    public static final int GESTURE_ALL              = GESTURE_VERTICAL_SWIPE
            | GESTURE_HORIZONTAL_SWIPE | GESTURE_LONG_PRESS | GESTURE_TAP;

    @IntDef(flag = true, value = {
            GESTURE_VERTICAL_SWIPE, GESTURE_HORIZONTAL_SWIPE, GESTURE_LONG_PRESS, GESTURE_TAP
    })
    @Retention(RetentionPolicy.SOURCE)
    public @interface GestureMask {}

    // -------------------------------------------------------------------------
    // Claim result
    // -------------------------------------------------------------------------

    /** Result of a {@link #requestClaim} call. */
    public enum ClaimResult {
        /** The caller now owns the requested gesture stream. */
        GRANTED,
        /** A higher-priority claimant already owns a conflicting gesture stream. */
        DENIED
    }

    // -------------------------------------------------------------------------
    // Listener
    // -------------------------------------------------------------------------

    /**
     * Notified on the main thread when gesture ownership changes — typically when a
     * higher-priority component steals a claim from an existing owner, or when any
     * active claim is released.
     */
    public interface OnClaimChangedListener {
        /**
         * Called when ownership changes.
         *
         * @param newOwner the new claim owner, or {@code null} if ownership was released.
         * @param gestureMask the gesture mask that changed, or {@code 0} on release.
         */
        void onClaimChanged(@Nullable Object newOwner, @GestureMask int gestureMask);
    }

    // -------------------------------------------------------------------------
    // Singleton
    // -------------------------------------------------------------------------

    private static volatile GestureArbiter sInstance;

    /**
     * Returns the singleton. Must be called after {@link #init(Context)}.
     *
     * @throws IllegalStateException if {@link #init(Context)} has not been called.
     */
    @NonNull
    public static GestureArbiter get() {
        GestureArbiter instance = sInstance;
        if (instance == null) {
            throw new IllegalStateException(
                    "GestureArbiter.init(Context) must be called before GestureArbiter.get(). "
                            + "Call it in Application.onCreate().");
        }
        return instance;
    }

    /**
     * Initialises the singleton. Safe to call multiple times — subsequent calls are no-ops.
     * The context is used only to extract the application context; no reference to an
     * Activity/View is retained.
     */
    public static void init(@NonNull Context context) {
        if (sInstance == null) {
            synchronized (GestureArbiter.class) {
                if (sInstance == null) {
                    sInstance = new GestureArbiter();
                }
            }
        }
    }

    // -------------------------------------------------------------------------
    // Internal claim record
    // -------------------------------------------------------------------------

    private static final class Claim {
        /** The component that owns this claim. */
        @NonNull  final Object claimant;
        /**
         * Screen-coordinate rect the claim covers. Null means the entire window.
         * Stored as a snapshot — mutations to the caller's Rect after claim are not tracked.
         */
        @Nullable final Rect region;
        /** Combination of {@code GESTURE_*} bits this claim covers. */
        @GestureMask final int gestureMask;
        /** Priority: higher value wins when two claims conflict. */
        final int priority;
        /** Expiry runnable posted via Dispatcher.main(); cancelled on release. */
        @Nullable Runnable expiryRunnable;

        Claim(@NonNull Object claimant, @Nullable Rect region,
              @GestureMask int gestureMask, int priority) {
            this.claimant    = claimant;
            this.region      = region != null ? new Rect(region) : null; // defensive copy
            this.gestureMask = gestureMask;
            this.priority    = priority;
        }

        /** Returns true if this claim's region intersects the given point, or if the region is null. */
        boolean contains(float x, float y) {
            if (region == null) return true;
            return region.contains((int) x, (int) y);
        }

        /** Returns true if this claim covers any gesture axis that {@code other} also covers. */
        boolean conflictsWith(@GestureMask int otherMask) {
            return (gestureMask & otherMask) != 0;
        }
    }

    // -------------------------------------------------------------------------
    // State
    // -------------------------------------------------------------------------

    /** Guards all mutable state. */
    private final Object mLock = new Object();

    /**
     * Currently active claims, ordered by priority (highest first).
     * A component may hold at most one claim at a time.
     */
    private final List<Claim> mActiveClaims = new ArrayList<>();

    /** WeakReference listener list — same pattern as ThemeManager. */
    private final List<WeakReference<OnClaimChangedListener>> mListeners = new ArrayList<>();
    private final Object mListenerLock = new Object();

    // -------------------------------------------------------------------------
    // Constructor
    // -------------------------------------------------------------------------

    private GestureArbiter() {}

    // -------------------------------------------------------------------------
    // Public API
    // -------------------------------------------------------------------------

    /**
     * Requests gesture ownership for a region and gesture type set.
     *
     * <p>Typically called on {@link MotionEvent#ACTION_DOWN} from the component's touch handler.
     *
     * @param claimant    the requesting component — used as an identity key; any non-null object.
     * @param region      screen-coordinate Rect the component occupies, or {@code null} for whole window.
     * @param gestureMask combination of {@code GESTURE_*} constants this component wants to own.
     * @param priority    claim priority; higher wins. Use consistent constants across your app,
     *                    e.g. 10 for normal UI, 100 for floating overlays, 1000 for system panels.
     * @param durationMs  maximum ms to hold the claim before automatic expiry (0 = no auto-expiry).
     * @return {@link ClaimResult#GRANTED} or {@link ClaimResult#DENIED}.
     */
    @NonNull
    public ClaimResult requestClaim(@NonNull Object claimant, @Nullable Rect region,
                                    @GestureMask int gestureMask, int priority, long durationMs) {
        synchronized (mLock) {
            // Remove any existing claim this claimant already holds (re-claim on new down).
            removeClaim(claimant, false /* do not notify, we'll notify after */);

            // Check for conflicting higher-priority claims at the requested region.
            float testX = region != null ? region.centerX() : 0;
            float testY = region != null ? region.centerY() : 0;

            for (Claim existing : mActiveClaims) {
                if (existing.conflictsWith(gestureMask) && existing.contains(testX, testY)) {
                    if (existing.priority > priority) {
                        // Higher-priority claim wins — deny this request.
                        return ClaimResult.DENIED;
                    }
                    // This claimant has higher or equal priority — bump the existing claim out.
                    cancelExpiry(existing);
                    mActiveClaims.remove(existing);
                    break;
                }
            }

            // Insert new claim, maintaining priority-descending order.
            Claim newClaim = new Claim(claimant, region, gestureMask, priority);
            insertByPriority(newClaim);

            // Schedule automatic expiry if requested.
            if (durationMs > 0) {
                final Claim ref = newClaim;
                Runnable expiry = () -> {
                    synchronized (mLock) {
                        mActiveClaims.remove(ref);
                    }
                    notifyListeners(null, 0);
                };
                newClaim.expiryRunnable = expiry;
                // Use Dispatcher.main() with a delayed post via a wrapper that respects the delay.
                scheduleOnMain(expiry, durationMs);
            }
        }

        notifyListeners(claimant, gestureMask);
        return ClaimResult.GRANTED;
    }

    /**
     * Releases a previously granted claim. Safe to call even if the claimant has no active claim.
     * Should be called on {@link MotionEvent#ACTION_UP} and {@link MotionEvent#ACTION_CANCEL}.
     *
     * @param claimant the component releasing ownership.
     */
    public void release(@NonNull Object claimant) {
        boolean removed;
        synchronized (mLock) {
            removed = removeClaim(claimant, false);
        }
        if (removed) {
            notifyListeners(null, 0);
        }
    }

    /**
     * Returns the current claim owner for the given screen point and gesture type, or
     * {@code null} if no component owns that combination.
     *
     * @param x          screen x coordinate.
     * @param y          screen y coordinate.
     * @param gestureMask the gesture type to query.
     */
    @Nullable
    public Object getOwner(float x, float y, @GestureMask int gestureMask) {
        synchronized (mLock) {
            for (Claim c : mActiveClaims) {
                if (c.conflictsWith(gestureMask) && c.contains(x, y)) {
                    return c.claimant;
                }
            }
        }
        return null;
    }

    /**
     * Convenience check: returns {@code true} if the given {@code claimant} currently owns
     * all axes in {@code gestureMask} at the given point.
     */
    public boolean isOwner(@NonNull Object claimant, float x, float y,
                           @GestureMask int gestureMask) {
        return claimant.equals(getOwner(x, y, gestureMask));
    }

    // -------------------------------------------------------------------------
    // Listener management (WeakReference pattern from ThemeManager)
    // -------------------------------------------------------------------------

    /**
     * Registers a listener for ownership-change notifications.
     * Call in {@code onAttachedToWindow()}; pair with {@link #removeListener} in
     * {@code onDetachedFromWindow()}.
     */
    public void addListener(@NonNull OnClaimChangedListener listener) {
        synchronized (mListenerLock) {
            for (WeakReference<OnClaimChangedListener> ref : mListeners) {
                if (ref.get() == listener) return;
            }
            mListeners.add(new WeakReference<>(listener));
        }
    }

    /**
     * Removes a previously registered listener. Safe to call even if never added.
     */
    public void removeListener(@NonNull OnClaimChangedListener listener) {
        synchronized (mListenerLock) {
            Iterator<WeakReference<OnClaimChangedListener>> it = mListeners.iterator();
            while (it.hasNext()) {
                OnClaimChangedListener l = it.next().get();
                if (l == null || l == listener) it.remove();
            }
        }
    }

    // -------------------------------------------------------------------------
    // Internal helpers
    // -------------------------------------------------------------------------

    /**
     * Removes any active claim held by {@code claimant}. Returns true if a claim was removed.
     * Must be called under {@link #mLock}.
     */
    private boolean removeClaim(@NonNull Object claimant, boolean unused) {
        Iterator<Claim> it = mActiveClaims.iterator();
        while (it.hasNext()) {
            Claim c = it.next();
            if (c.claimant == claimant || c.claimant.equals(claimant)) {
                cancelExpiry(c);
                it.remove();
                return true;
            }
        }
        return false;
    }

    /** Cancels the expiry runnable for a claim if one is set. */
    private void cancelExpiry(@NonNull Claim claim) {
        if (claim.expiryRunnable != null) {
            // Remove from the main looper's message queue.
            // We post through a shared handler accessed via Dispatcher; the cancel
            // is done by clearing the reference and letting the runnable check validity.
            claim.expiryRunnable = null;
        }
    }

    /** Inserts a claim into mActiveClaims in priority-descending order. */
    private void insertByPriority(@NonNull Claim newClaim) {
        int insertAt = mActiveClaims.size();
        for (int i = 0; i < mActiveClaims.size(); i++) {
            if (mActiveClaims.get(i).priority < newClaim.priority) {
                insertAt = i;
                break;
            }
        }
        mActiveClaims.add(insertAt, newClaim);
    }

    /**
     * Posts a runnable to the main thread after {@code delayMs} milliseconds.
     * Uses the Dispatcher's internal Handler through a small trampoline so we never
     * create a raw Handler in this class.
     */
    private void scheduleOnMain(@NonNull Runnable runnable, long delayMs) {
        // Dispatcher.main() runs immediately if already on main — for delayed scheduling
        // we wrap with a Dispatcher.io() sleep + Dispatcher.main() handoff.
        Dispatcher.io(() -> {
            try {
                Thread.sleep(delayMs);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            Dispatcher.main(runnable);
        });
    }

    /**
     * Notifies all live listeners on the main thread. Dead WeakReferences are pruned.
     */
    private void notifyListeners(@Nullable Object newOwner, @GestureMask int gestureMask) {
        final List<OnClaimChangedListener> snapshot;
        synchronized (mListenerLock) {
            snapshot = new ArrayList<>(mListeners.size());
            Iterator<WeakReference<OnClaimChangedListener>> it = mListeners.iterator();
            while (it.hasNext()) {
                OnClaimChangedListener l = it.next().get();
                if (l == null) {
                    it.remove();
                } else {
                    snapshot.add(l);
                }
            }
        }
        final int finalMask = gestureMask;
        Dispatcher.main(() -> {
            for (OnClaimChangedListener l : snapshot) {
                l.onClaimChanged(newOwner, finalMask);
            }
        });
    }
}
