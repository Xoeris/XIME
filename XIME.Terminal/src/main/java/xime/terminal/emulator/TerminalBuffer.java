package xime.terminal.emulator;

import java.util.ArrayList;
import java.util.List;
import xime.performance.DirtyLineTracker;

public final class TerminalBuffer {
    private List<TerminalRow> rows;
    private final List<TerminalRow> history;
    private List<TerminalRow> altRows;
    private boolean isAltScreen = false;
    
    private int width;
    private int height;
    private final int scrollbackLimit;
    private DirtyLineTracker dirtyTracker;

    public TerminalBuffer(int width, int height, int scrollbackLimit) {
        this.width = width;
        this.height = height;
        this.scrollbackLimit = scrollbackLimit;
        this.rows = new ArrayList<>(height);
        this.history = new ArrayList<>();
        for (int i = 0; i < height; i++) {
            rows.add(new TerminalRow(width));
        }
        this.dirtyTracker = new DirtyLineTracker(height);
    }

    public int getWidth() { return width; }
    public int getHeight() { return height; }
    public int getHistorySize() { return history.size(); }

    public TerminalRow getRow(int index) {
        if (index >= 0 && index < rows.size()) {
            return rows.get(index);
        }
        return null;
    }
    
    public TerminalRow getHistoryRow(int index) {
        if (index >= 0 && index < history.size()) {
            return history.get(index);
        }
        return null;
    }

    public void setChar(int row, int col, char c, long attr) {
        TerminalRow r = getRow(row);
        if (r != null) {
            r.set(col, c, attr);
            dirtyTracker.markDirty(row);
        }
    }

    public void scroll() {
        if (!isAltScreen) {
            history.add(rows.remove(0));
            if (history.size() > scrollbackLimit) {
                history.remove(0);
            }
        } else {
            rows.remove(0);
        }
        rows.add(new TerminalRow(width));
        dirtyTracker.markAllDirty();
    }
    
    public void useAltScreen(boolean enable) {
        if (isAltScreen == enable) return;
        
        if (enable) {
            altRows = new ArrayList<>(height);
            for (int i = 0; i < height; i++) {
                altRows.add(new TerminalRow(width));
            }
            List<TerminalRow> temp = rows;
            rows = altRows;
            altRows = temp; // Save primary rows
        } else {
            rows = altRows; // Restore primary rows
            altRows = null;
        }
        isAltScreen = enable;
        dirtyTracker.markAllDirty();
    }

    public void resize(int newWidth, int newHeight) {
        // Resize visible rows
        for (TerminalRow row : rows) {
            row.resize(newWidth);
        }
        // Resize history
        for (TerminalRow row : history) {
            row.resize(newWidth);
        }
        // Resize alt buffer if exists
        if (altRows != null) {
            for (TerminalRow row : altRows) {
                row.resize(newWidth);
            }
        }
        
        if (newHeight > height) {
            for (int i = height; i < newHeight; i++) {
                rows.add(new TerminalRow(newWidth));
            }
        } else if (newHeight < height) {
            while (rows.size() > newHeight) {
                rows.remove(rows.size() - 1);
            }
        }
        
        this.width = newWidth;
        this.height = newHeight;
        this.dirtyTracker.resize(newHeight);
        this.dirtyTracker.markAllDirty();
    }

    public DirtyLineTracker getDirtyTracker() {
        return dirtyTracker;
    }
}
