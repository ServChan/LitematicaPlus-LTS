package org.lts.litematicaplus.gui;

public final class Theme {
    public static final int BACKGROUND = 0xF2121A24;
    public static final int HUD_BACKGROUND = 0xD9121A24;
    public static final int HEADER = 0xFF0E151E;
    public static final int SIDEBAR = 0xFF0F1720;
    public static final int SURFACE = 0xFF17212D;
    public static final int SURFACE_RAISED = 0xFF1A2633;
    public static final int SURFACE_HOVER = 0xFF1F2D3C;
    public static final int SURFACE_SELECTED = 0xFF233548;
    public static final int BORDER = 0xFF2E4053;
    public static final int BORDER_STRONG = 0xFF4A627B;
    public static final int DIVIDER = 0xFF24323F;
    public static final int TRACK = 0xFF0D141C;
    public static final int CHIP = 0xFF1D2A38;
    public static final int SLOT = 0xFF0F1720;
    public static final int SLOT_BORDER = 0xFF2E4053;
    public static final int ACCENT = 0xFF6FB7C9;
    public static final int ACCENT_DARK = 0xFF3E7F90;
    public static final int ACCENT_HOVER = 0xFF4A90A2;
    public static final int ACCENT_TEXT = 0xFFF2F6FA;
    public static final int AMBER = 0xFFC9A05A;
    public static final int TEXT_BRIGHT = 0xFFE8EDF2;
    public static final int TEXT = 0xFFC9D3DD;
    public static final int TEXT_DIM = 0xFF8A99A8;
    public static final int TEXT_DISABLED = 0xFF566473;
    public static final int SUCCESS = 0xFF7CC98F;
    public static final int DANGER = 0xFFE58A82;
    public static final int DANGER_DARK = 0x40E58A82;
    public static final int WARNING = 0xFFD9B35B;
    public static final int FOCUS = 0xFF9FB8CC;

    public static final int HEADER_HEIGHT = 30;
    public static final int SIDEBAR_WIDTH = 112;
    public static final int PADDING = 12;
    public static final int GAP = 6;
    public static final int CONTROL_HEIGHT = 20;
    public static final int ROW_HEIGHT = 22;

    private Theme() {
    }

    public static int alpha(int color, int alpha) {
        return (alpha & 0xFF) << 24 | color & 0xFFFFFF;
    }
}
