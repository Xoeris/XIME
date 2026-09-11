package xime.terminal.emulator;

import android.graphics.Color;

/**
 * Utility for resolving xterm 256-color palette and standard ANSI colors.
 */
public final class TerminalColors {
    private static final int[] PALETTE = new int[256];

    static {
        // 1. Standard 16 colors (indices 0-15)
        PALETTE[0]  = 0xFF000000; // Black
        PALETTE[1]  = 0xFFCD0000; // Red
        PALETTE[2]  = 0xFF00CD00; // Green
        PALETTE[3]  = 0xFFCDCD00; // Yellow
        PALETTE[4]  = 0xFF0000EE; // Blue
        PALETTE[5]  = 0xFFCD00CD; // Magenta
        PALETTE[6]  = 0xFF00CDCD; // Cyan
        PALETTE[7]  = 0xFFE5E5E5; // White
        PALETTE[8]  = 0xFF7F7F7F; // Bright Black (Gray)
        PALETTE[9]  = 0xFFFF0000; // Bright Red
        PALETTE[10] = 0xFF00FF00; // Bright Green
        PALETTE[11] = 0xFFFFFF00; // Bright Yellow
        PALETTE[12] = 0xFF5C5CFF; // Bright Blue
        PALETTE[13] = 0xFFFF00FF; // Bright Magenta
        PALETTE[14] = 0xFF00FFFF; // Bright Cyan
        PALETTE[15] = 0xFFFFFFFF; // Bright White

        // 2. 6x6x6 Color Cube (indices 16-231)
        int[] cubeLevels = {0, 95, 135, 175, 215, 255};
        int index = 16;
        for (int r = 0; r < 6; r++) {
            for (int g = 0; g < 6; g++) {
                for (int b = 0; b < 6; b++) {
                    PALETTE[index++] = 0xFF000000 | (cubeLevels[r] << 16) | (cubeLevels[g] << 8) | cubeLevels[b];
                }
            }
        }

        // 3. Grayscale ramp (indices 232-255)
        for (int i = 0; i < 24; i++) {
            int level = 8 + i * 10;
            PALETTE[index++] = 0xFF000000 | (level << 16) | (level << 8) | level;
        }
    }

    private TerminalColors() {}

    public static int resolve256(int index) {
        if (index < 0 || index > 255) return Color.WHITE;
        return PALETTE[index];
    }
}
