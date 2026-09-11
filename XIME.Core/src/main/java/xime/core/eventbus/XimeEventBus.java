package xime.core.eventbus;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import xime.core.dispatch.Dispatcher;

/**
 * XIME.Core, Lightweight typed event bus for cross-module pub/sub.
 *
 * <p>Decouples modules that cannot directly depend on each other, e.g. {@code XIME.Media}
 * posting track-change events that a {@code XIME.UI} mini-player widget receives, without either
 * module importing the other. Both instead depend only on {@code XIME.Core}.
 *
 * <h3>Usage</h3>
 * <pre>
 *   // Define an event type anywhere (inner class, dedicated file, etc.):
 *   public static class TrackChangedEvent {
 *       public final String title;
 *       public TrackChangedEvent(String title) { this.title = title; }
 *   }
 *
 *   // Post (from XIME.Media):
 *   XimeEventBus.get().post(new TrackChangedEvent("My Song"));
 *
 *   // Subscribe (from XIME.UI widget, in onAttachedToWindow):
 *   XimeEventBus.get().subscribe(TrackChangedEvent.class, event -> {
 *       titleView.setText(event.title);
 *   });
 *
 *   // Unsubscribe (in onDetachedFromWindow):
 *   XimeEventBus.get().unsubscribe(TrackChangedEvent.class, mySubscriber);
 * </pre>
 *
 * <h3>Delivery</h3>
 * All subscriber callbacks are delivered on the main thread via {@link Dispatcher#main(Runnable)}.
 *
 * <h3>Dead subscriber safety</h3>
 * Subscribers are held via {@link WeakReference}. Components that forget to call
 * {@link #unsubscribe} will have their dead references pruned silently on the next
 * {@link #post} delivery.
 *
 * <h3>Superclass matching</h3>
 * {@link #post} walks the event's class hierarchy, posting a {@code FooEvent extends BaseEvent}
 * will also notify subscribers registered for {@code BaseEvent}. This allows base event
 * contracts across modules.
 *
 * <h3>Lifecycle</h3>
 * Call {@link #init()} in {@code Application.onCreate()} before any module posts or subscribes.
 */
public final class XimeEventBus {

    // -------------------------------------------------------------------------
    // Subscriber interface
    // -------------------------------------------------------------------------

    /**
     * Receives a single event of type {@code E}. Delivered on the main thread.
     *
     * @param <E> the event type.
     */
    public interface Subscriber<E> {
        void onEvent(@NonNull E event);
    }

    // -------------------------------------------------------------------------
    // Singleton
    // -------------------------------------------------------------------------

    private static volatile XimeEventBus sInstance;

    /**
     * Returns the singleton. Must be called after {@link #init()}.
     *
     * @throws IllegalStateException if {@link #init()} has not been called.
     */
    @NonNull
    public static XimeEventBus get() {
        XimeEventBus instance = sInstance;
        if (instance == null) {
            throw new IllegalStateException(
                    "XimeEventBus.init() must be called before XimeEventBus.get(). "
                            + "Call it in Application.onCreate().");
        }
        return instance;
    }

    /**
     * Initialises the singleton. Safe to call multiple times, subsequent calls are no-ops.
     */
    public static void init() {
        if (sInstance == null) {
            synchronized (XimeEventBus.class) {
                if (sInstance == null) {
                    sInstance = new XimeEventBus();
                }
            }
        }
    }

    // -------------------------------------------------------------------------
    // Internal registry
    // -------------------------------------------------------------------------

    /**
     * Guards all access to {@link #mSubscribers}.
     */
    private final Object mLock = new Object();

    /**
     * Maps event class → ordered list of WeakReference-wrapped subscribers.
     * LinkedHashMap preserves subscription order for deterministic delivery.
     */
    private final Map<Class<?>, List<WeakReference<Subscriber<?>>>> mSubscribers =
            new LinkedHashMap<>();

    // -------------------------------------------------------------------------
    // Constructor
    // -------------------------------------------------------------------------

    private XimeEventBus() {}

    // -------------------------------------------------------------------------
    // Public API
    // -------------------------------------------------------------------------

    /**
     * Subscribes to events of type {@code eventClass}. The subscriber is held via a
     * {@link WeakReference}, if the subscriber is garbage-collected without calling
     * {@link #unsubscribe}, no leak occurs; the dead reference is pruned on the next delivery.
     *
     * <p>Safe to call from any thread.
     *
     * @param eventClass the exact event type to subscribe to. Superclass subscriptions also
     *                   receive subclass events posted via {@link #post(Object)}.
     * @param subscriber the callback. Will be called on the main thread.
     * @param <E>        the event type.
     */
    public <E> void subscribe(@NonNull Class<E> eventClass, @NonNull Subscriber<E> subscriber) {
        synchronized (mLock) {
            List<WeakReference<Subscriber<?>>> list = mSubscribers.get(eventClass);
            if (list == null) {
                list = new ArrayList<>();
                mSubscribers.put(eventClass, list);
            }
            // Avoid duplicate registration.
            for (WeakReference<Subscriber<?>> ref : list) {
                if (ref.get() == subscriber) return;
            }
            list.add(new WeakReference<>(subscriber));
        }
    }

    /**
     * Unsubscribes from events of type {@code eventClass}. Safe to call even if the subscriber
     * was never registered.
     *
     * @param eventClass the event type to stop subscribing to.
     * @param subscriber the subscriber to remove.
     * @param <E>        the event type.
     */
    public <E> void unsubscribe(@NonNull Class<E> eventClass, @NonNull Subscriber<E> subscriber) {
        synchronized (mLock) {
            List<WeakReference<Subscriber<?>>> list = mSubscribers.get(eventClass);
            if (list == null) return;
            Iterator<WeakReference<Subscriber<?>>> it = list.iterator();
            while (it.hasNext()) {
                Subscriber<?> s = it.next().get();
                if (s == null || s == subscriber) it.remove();
            }
        }
    }

    /**
     * Unsubscribes {@code subscriber} from ALL event types it is currently registered for.
     * Useful for a single teardown call in {@code onDetachedFromWindow()}.
     *
     * @param subscriber the subscriber to remove from all event types.
     */
    public void unsubscribeAll(@NonNull Subscriber<?> subscriber) {
        synchronized (mLock) {
            for (List<WeakReference<Subscriber<?>>> list : mSubscribers.values()) {
                Iterator<WeakReference<Subscriber<?>>> it = list.iterator();
                while (it.hasNext()) {
                    Subscriber<?> s = it.next().get();
                    if (s == null || s == subscriber) it.remove();
                }
            }
        }
    }

    /**
     * Posts an event. All subscribers registered for {@code event.getClass()} and any of its
     * superclasses/interfaces are notified on the main thread.
     *
     * <p>Safe to call from any thread.
     *
     * @param event the event to post. Must not be {@code null}.
     */
    public void post(@NonNull Object event) {
        final List<Subscriber<?>> snapshot = collectSubscribers(event.getClass());
        if (snapshot.isEmpty()) return;

        Dispatcher.main(() -> {
            for (Subscriber<?> sub : snapshot) {
                //noinspection unchecked, type safety guaranteed by subscribe()'s generic bound
                ((Subscriber<Object>) sub).onEvent(event);
            }
        });
    }

    // -------------------------------------------------------------------------
    // Internal helpers
    // -------------------------------------------------------------------------

    /**
     * Walks {@code eventClass}'s hierarchy (class + interfaces) and collects all live
     * subscribers. Dead WeakReferences are pruned as a side-effect.
     */
    @NonNull
    private List<Subscriber<?>> collectSubscribers(@NonNull Class<?> eventClass) {
        final List<Subscriber<?>> result = new ArrayList<>();
        synchronized (mLock) {
            Class<?> cls = eventClass;
            while (cls != null && cls != Object.class) {
                pruneAndCollect(cls, result);
                // Also walk interfaces of this class.
                for (Class<?> iface : cls.getInterfaces()) {
                    pruneAndCollect(iface, result);
                }
                cls = cls.getSuperclass();
            }
        }
        return result;
    }

    /**
     * Collects live subscribers for {@code cls} into {@code out}, pruning dead references.
     * Must be called under {@link #mLock}.
     */
    private void pruneAndCollect(@NonNull Class<?> cls, @NonNull List<Subscriber<?>> out) {
        List<WeakReference<Subscriber<?>>> list = mSubscribers.get(cls);
        if (list == null) return;
        Iterator<WeakReference<Subscriber<?>>> it = list.iterator();
        while (it.hasNext()) {
            Subscriber<?> s = it.next().get();
            if (s == null) {
                it.remove(); // prune dead reference
            } else {
                out.add(s);
            }
        }
    }
}
