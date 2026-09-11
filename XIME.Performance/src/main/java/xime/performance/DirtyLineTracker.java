package xime.performance;

import java.util.BitSet;

public final class DirtyLineTracker {
    private final BitSet dirtyLines;
    private int rowCount;

    public DirtyLineTracker(int rowCount) {
        this.rowCount = rowCount;
        this.dirtyLines = new BitSet(rowCount);
    }

    public void resize(int newRowCount) {
        this.rowCount = newRowCount;
        // BitSet grows automatically if needed, but we might want to clear it if logic changes
    }

    public void markDirty(int row) {
        if (row >= 0 && row < rowCount) {
            dirtyLines.set(row);
        }
    }

    public void markAllDirty() {
        dirtyLines.set(0, rowCount);
    }

    public boolean isDirty(int row) {
        return row >= 0 && row < rowCount && dirtyLines.get(row);
    }

    public void clear(int row) {
        if (row >= 0 && row < rowCount) {
            dirtyLines.clear(row);
        }
    }

    public void clearAll() {
        dirtyLines.clear();
    }
}
