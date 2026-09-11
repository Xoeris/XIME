package xime.persistence.peroom;
public @interface ConflictStrategy {
    int REPLACE = 1;
    int IGNORE = 2;
    int ABORT = 3;
}
