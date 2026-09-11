package xime.media;

import android.os.Handler;
import android.os.Looper;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

public class Signal<T> {
    public interface Observer<T> {
        void onEvent(T data);
    }

    private final List<Observer<T>> observers = new CopyOnWriteArrayList<>();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private T lastEvent;

    public void observe(Observer<T> observer) {
        if (observer != null && !observers.contains(observer)) {
            observers.add(observer);
            if (lastEvent != null) {
                observer.onEvent(lastEvent);
            }
        }
    }

    /**
     * Disconnects an observer.
     */
    public void disconnect(Observer<T> observer) {
        observers.remove(observer);
    }

    /**
     * Alias for disconnect.
     */
    public void removeObserver(Observer<T> observer) {
        disconnect(observer);
    }

    /**
     * Emits data to all connected observers.
     */
    public void emit(T data) {
        this.lastEvent = data;
        mainHandler.post(() -> {
            for (Observer<T> observer : observers) {
                observer.onEvent(data);
            }
        });
    }

    /**
     * Returns the last emitted event.
     */
    public T getLastEvent() {
        return lastEvent;
    }

    /**
     * Clears all observers.
     */
    public void clear() {
        observers.clear();
    }
    
    /**
     * Compatibility alias for connect.
     */
    public void connect(Observer<T> observer) {
        observe(observer);
    }
}

