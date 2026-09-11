package xime.persistence.peroom;

import android.database.sqlite.SQLiteDatabase;

public abstract class PeMigration {
    public final int startVersion;
    public final int endVersion;

    public PeMigration(int startVersion, int endVersion) {
        this.startVersion = startVersion;
        this.endVersion = endVersion;
    }

    public abstract void migrate(SQLiteDatabase database);
}
