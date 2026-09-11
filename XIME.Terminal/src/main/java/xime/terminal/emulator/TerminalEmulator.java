package xime.terminal.emulator;

public final class TerminalEmulator {
    private final TerminalBuffer buffer;
    private int cursorRow = 0;
    private int cursorCol = 0;
    
    // Attribute State
    private int currentFgColor = 0xFFFFFFFF;
    private boolean currentFgSet = false;
    private int currentBgColor = 0xFF000000;
    private boolean currentBgSet = false;
    private int savedCursorRow = 0;
    private int savedCursorCol = 0;

    private static final int STATE_GROUND = 0;
    private static final int STATE_ESC = 1;
    private static final int STATE_CSI = 2;
    private static final int STATE_DEC_PRIVATE = 3;
    private int state = STATE_GROUND;
    
    private final StringBuilder csiParams = new StringBuilder();

    public TerminalEmulator(TerminalBuffer buffer) {
        this.buffer = buffer;
    }

    public void append(byte[] data, int len) {
        for (int i = 0; i < len; i++) {
            processByte(data[i] & 0xFF);
        }
    }

    private void processByte(int b) {
        switch (state) {
            case STATE_GROUND:
                if (b == 0x1B) {
                    state = STATE_ESC;
                } else if (b == '\n') {
                    lineFeed();
                } else if (b == '\r') {
                    cursorCol = 0;
                } else if (b == 0x08) { // Backspace
                    cursorCol = Math.max(0, cursorCol - 1);
                } else if (b == 0x09) { // Tab
                    cursorCol = (cursorCol / 8 + 1) * 8;
                    if (cursorCol >= buffer.getWidth()) cursorCol = buffer.getWidth() - 1;
                } else if (b >= 0x20) {
                    printChar((char) b);
                }
                break;
            case STATE_ESC:
                if (b == '[') {
                    state = STATE_CSI;
                    csiParams.setLength(0);
                } else if (b == '7') { // Save cursor
                    savedCursorRow = cursorRow;
                    savedCursorCol = cursorCol;
                    state = STATE_GROUND;
                } else if (b == '8') { // Restore cursor
                    cursorRow = savedCursorRow;
                    cursorCol = savedCursorCol;
                    state = STATE_GROUND;
                } else {
                    state = STATE_GROUND;
                }
                break;
            case STATE_CSI:
                if (b == '?') {
                    state = STATE_DEC_PRIVATE;
                } else if (b >= 0x30 && b <= 0x3F || b == ';') {
                    csiParams.append((char) b);
                } else {
                    handleCsi(b);
                    state = STATE_GROUND;
                }
                break;
            case STATE_DEC_PRIVATE:
                if (b >= 0x30 && b <= 0x3F || b == ';') {
                    csiParams.append((char) b);
                } else {
                    handleDecPrivate(b);
                    state = STATE_GROUND;
                }
                break;
        }
    }

    private void printChar(char c) {
        if (cursorCol >= buffer.getWidth()) {
            lineFeed();
        }
        long attr = TerminalRow.pack(currentFgColor, currentFgSet, currentBgColor, currentBgSet);
        buffer.setChar(cursorRow, cursorCol, c, attr);
        cursorCol++;
    }

    private void lineFeed() {
        cursorCol = 0;
        if (cursorRow < buffer.getHeight() - 1) {
            cursorRow++;
        } else {
            buffer.scroll();
        }
    }
    
    public void resize(int newCols, int newRows) {
        buffer.resize(newCols, newRows);
        cursorRow = Math.min(cursorRow, newRows - 1);
        cursorCol = Math.min(cursorCol, newCols - 1);
    }

    private void handleCsi(int command) {
        String[] params = csiParams.toString().split(";");
        switch (command) {
            case 'H': // Cup
            case 'f':
                // Move cursor
                if (params.length >= 2 && !params[0].isEmpty() && !params[1].isEmpty()) {
                    try {
                        cursorRow = Math.max(0, Math.min(buffer.getHeight() - 1, Integer.parseInt(params[0]) - 1));
                        cursorCol = Math.max(0, Math.min(buffer.getWidth() - 1, Integer.parseInt(params[1]) - 1));
                    } catch (NumberFormatException ignored) {}
                } else {
                    cursorRow = 0;
                    cursorCol = 0;
                }
                break;
            case 'A': // CUU - Cursor Up
                int up = (params.length > 0 && !params[0].isEmpty()) ? Integer.parseInt(params[0]) : 1;
                cursorRow = Math.max(0, cursorRow - up);
                break;
            case 'B': // CUD - Cursor Down
                int down = (params.length > 0 && !params[0].isEmpty()) ? Integer.parseInt(params[0]) : 1;
                cursorRow = Math.min(buffer.getHeight() - 1, cursorRow + down);
                break;
            case 'C': // CUF - Cursor Forward
                int right = (params.length > 0 && !params[0].isEmpty()) ? Integer.parseInt(params[0]) : 1;
                cursorCol = Math.min(buffer.getWidth() - 1, cursorCol + right);
                break;
            case 'D': // CUB - Cursor Back
                int left = (params.length > 0 && !params[0].isEmpty()) ? Integer.parseInt(params[0]) : 1;
                cursorCol = Math.max(0, cursorCol - left);
                break;
            case 'm': // SGR
                handleSgr(params);
                break;
            case 'J': // Erase in Display
                int modeJ = (params.length > 0 && !params[0].isEmpty()) ? Integer.parseInt(params[0]) : 0;
                if (modeJ == 2) {
                    for (int r = 0; r < buffer.getHeight(); r++) {
                        TerminalRow row = buffer.getRow(r);
                        if (row != null) row.clear();
                    }
                    cursorRow = 0;
                    cursorCol = 0;
                }
                break;
            case 'K': // Erase in Line
                int modeK = (params.length > 0 && !params[0].isEmpty()) ? Integer.parseInt(params[0]) : 0;
                TerminalRow rowK = buffer.getRow(cursorRow);
                if (rowK != null) {
                    if (modeK == 0) { // Clear from cursor to end
                        for (int c = cursorCol; c < buffer.getWidth(); c++) rowK.set(c, ' ', 0);
                    } else if (modeK == 1) { // Clear from start to cursor
                        for (int c = 0; c <= cursorCol; c++) rowK.set(c, ' ', 0);
                    } else if (modeK == 2) { // Clear whole line
                        rowK.clear();
                    }
                }
                break;
            case 's': // Save cursor
                savedCursorRow = cursorRow;
                savedCursorCol = cursorCol;
                break;
            case 'u': // Restore cursor
                cursorRow = savedCursorRow;
                cursorCol = savedCursorCol;
                break;
        }
    }
    
    private void handleDecPrivate(int command) {
        String params = csiParams.toString();
        if (command == 'h') { // DECSET
            if (params.equals("1049")) {
                buffer.useAltScreen(true);
            }
        } else if (command == 'l') { // DECRST
            if (params.equals("1049")) {
                buffer.useAltScreen(false);
            }
        }
    }

    private void handleSgr(String[] params) {
        if (params.length == 0 || (params.length == 1 && (params[0].isEmpty() || params[0].equals("0")))) {
            currentFgSet = false;
            currentBgSet = false;
            return;
        }

        for (int i = 0; i < params.length; i++) {
            if (params[i].isEmpty()) continue;
            try {
                int code = Integer.parseInt(params[i]);
                if (code == 0) {
                    currentFgSet = false;
                    currentBgSet = false;
                } else if (code >= 30 && code <= 37) {
                    currentFgColor = TerminalColors.resolve256(code - 30);
                    currentFgSet = true;
                } else if (code >= 40 && code <= 47) {
                    currentBgColor = TerminalColors.resolve256(code - 40);
                    currentBgSet = true;
                } else if (code >= 90 && code <= 97) {
                    currentFgColor = TerminalColors.resolve256(code - 90 + 8);
                    currentFgSet = true;
                } else if (code >= 100 && code <= 107) {
                    currentBgColor = TerminalColors.resolve256(code - 100 + 8);
                    currentBgSet = true;
                } else if (code == 38 || code == 48) {
                    // Extended colors
                    boolean isFg = (code == 38);
                    if (i + 2 < params.length) {
                        int type = Integer.parseInt(params[i + 1]);
                        if (type == 5) { // 256 colors
                            int index = Integer.parseInt(params[i + 2]);
                            int color = TerminalColors.resolve256(index);
                            if (isFg) { currentFgColor = color; currentFgSet = true; }
                            else { currentBgColor = color; currentBgSet = true; }
                            i += 2;
                        } else if (type == 2 && i + 4 < params.length) { // Truecolor
                            int r = Integer.parseInt(params[i + 2]);
                            int g = Integer.parseInt(params[i + 3]);
                            int b = Integer.parseInt(params[i + 4]);
                            int color = 0xFF000000 | ((r & 0xFF) << 16) | ((g & 0xFF) << 8) | (b & 0xFF);
                            if (isFg) { currentFgColor = color; currentFgSet = true; }
                            else { currentBgColor = color; currentBgSet = true; }
                            i += 4;
                        }
                    }
                } else if (code == 39) {
                    currentFgSet = false;
                } else if (code == 49) {
                    currentBgSet = false;
                }
            } catch (NumberFormatException ignored) {}
        }
    }

    public int getCursorRow() { return cursorRow; }
    public int getCursorCol() { return cursorCol; }
}
