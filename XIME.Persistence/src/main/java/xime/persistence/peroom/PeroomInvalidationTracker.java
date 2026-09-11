package xime.persistence.peroom;

import androidx.lifecycle.LiveData;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;

public class PeroomInvalidationTracker {
    private final PeroomDatabase database;
    private final Set<Runnable> observers = new CopyOnWriteArraySet<>();

    public PeroomInvalidationTracker(PeroomDatabase database) {
        this.database = database;
    }

    public void addObserver(Runnable observer) {
        observers.add(observer);
    }

    public void removeObserver(Runnable observer) {
        observers.remove(observer);
    }

    public void notifyChange() {
        for (Runnable observer : observers) {
            observer.run();
        }
    }
}
