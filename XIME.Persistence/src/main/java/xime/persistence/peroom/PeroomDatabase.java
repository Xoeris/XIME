package xime.persistence.peroom;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import androidx.annotation.NonNull;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.Map;
import java.util.HashMap;

public abstract class PeroomDatabase {
    protected SQLiteDatabase database;
    protected ExecutorService queryExecutor = Executors.newSingleThreadExecutor();
    protected PeroomInvalidationTracker invalidationTracker;
    private final Map<Class<?>, Object> daoCache = new HashMap<>();

    public void init(SQLiteDatabase db) {
        this.database = db;
        this.invalidationTracker = new PeroomInvalidationTracker(this);
    }

    public SQLiteDatabase getDatabase() {
        return database;
    }

    public ExecutorService getQueryExecutor() {
        return queryExecutor;
    }

    public PeroomInvalidationTracker getInvalidationTracker() {
        return invalidationTracker;
    }

    public void close() {
        if (database != null && database.isOpen()) {
            database.close();
        }
    }
    
    @SuppressWarnings("unchecked")
    protected <T> T createDao(Class<T> daoClass) {
        if (!daoCache.containsKey(daoClass)) {
            T dao = PeroomEngine.createDaoProxy(this, daoClass);
            daoCache.put(daoClass, dao);
        }
        return (T) daoCache.get(daoClass);
    }

    public static class Callback {
        public void onCreate(@NonNull SQLiteDatabase db) {}
        public void onOpen(@NonNull SQLiteDatabase db) {}
    }

    public static class Builder<T extends PeroomDatabase> {
        private final Context context;
        private final Class<T> klass;
        private final String name;
        private final List<PeMigration> migrations = new ArrayList<>();
        private final List<Callback> callbacks = new ArrayList<>();

        public Builder(Context context, Class<T> klass, String name) {
            this.context = context;
            this.klass = klass;
            this.name = name;
        }

        public Builder<T> addMigrations(PeMigration... migrations) {
            for (PeMigration m : migrations) {
                this.migrations.add(m);
            }
            return this;
        }

        public Builder<T> addCallback(Callback callback) {
            this.callbacks.add(callback);
            return this;
        }

        public T build() {
            try {
                // We assume the user has made their database class concrete with no-arg constructor
                // since java.lang.reflect.Proxy cannot proxy abstract classes without codegen.
                T dbInstance = klass.getDeclaredConstructor().newInstance();
                
                PeDatabase dbAnnotation = klass.getAnnotation(PeDatabase.class);
                if (dbAnnotation == null) throw new IllegalStateException("Missing @PeDatabase annotation");
                
                int version = dbAnnotation.version();
                
                SQLiteOpenHelper helper = new SQLiteOpenHelper(context, name, null, version) {
                    @Override
                    public void onCreate(SQLiteDatabase db) {
                        PeroomEngine.createTables(db, dbAnnotation.entities());
                        for (Callback cb : callbacks) {
                            cb.onCreate(db);
                        }
                    }

                    @Override
                    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
                        for (PeMigration migration : migrations) {
                            if (migration.startVersion >= oldVersion && migration.endVersion <= newVersion) {
                                migration.migrate(db);
                            }
                        }
                    }

                    @Override
                    public void onOpen(SQLiteDatabase db) {
                        for (Callback cb : callbacks) {
                            cb.onOpen(db);
                        }
                    }
                };
                
                dbInstance.init(helper.getWritableDatabase());
                return dbInstance;
            } catch (Exception e) {
                throw new RuntimeException("Failed to build PeroomDatabase. Ensure the class has a public no-arg constructor and is not abstract.", e);
            }
        }
    }
}
