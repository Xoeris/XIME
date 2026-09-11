package xime.terminal.emulator;

import java.util.Arrays;

public final class TerminalRow {
    private char[] text;
    private long[] attributes;
    private int width;

    // Attribute Packing Constants
    private static final long FG_SET_MASK = 1L << 24;
    private static final long BG_SET_MASK = 1L << 56;
    private static final long FG_COLOR_MASK = 0xFFFFFFL;
    private static final long BG_COLOR_MASK = 0xFFFFFFL << 32;

    public TerminalRow(int width) {
        this.width = width;
        this.text = new char[width];
        this.attributes = new long[width];
        clear();
    }

    public void clear() {
        Arrays.fill(text, ' ');
        Arrays.fill(attributes, 0);
    }

    public void set(int col, char c, long attr) {
        if (col >= 0 && col < width) {
            text[col] = c;
            attributes[col] = attr;
        }
    }

    public char getChar(int col) {
        return (col >= 0 && col < width) ? text[col] : ' ';
    }

    public long getAttributes(int col) {
        return (col >= 0 && col < width) ? attributes[col] : 0;
    }

    /**
     * Packs colors into an attribute long.
     */
    public static long pack(int fg, boolean fgSet, int bg, boolean bgSet) {
        long attr = 0;
        if (fgSet) {
            attr |= (fg & 0xFFFFFFL);
            attr |= FG_SET_MASK;
        }
        if (bgSet) {
            attr |= ((long) (bg & 0xFFFFFF) << 32);
            attr |= BG_SET_MASK;
        }
        return attr;
    }

    public static int unpackFg(long attr) {
        return (attr & FG_SET_MASK) != 0 ? (int) (attr & FG_COLOR_MASK) | 0xFF000000 : -1;
    }

    public static int unpackBg(long attr) {
        return (attr & BG_SET_MASK) != 0 ? (int) ((attr & BG_COLOR_MASK) >> 32) | 0xFF000000 : -1;
    }

    public void resize(int newWidth) {
        char[] newText = new char[newWidth];
        long[] newAttrs = new long[newWidth];
        Arrays.fill(newText, ' ');
        
        int copyLen = Math.min(width, newWidth);
        System.arraycopy(text, 0, newText, 0, copyLen);
        System.arraycopy(attributes, 0, newAttrs, 0, copyLen);
        
        this.text = newText;
        this.attributes = newAttrs;
        this.width = newWidth;
    }
}
