package org.lts.litematicaplus.gui;

public final class Theme {
    public static final int BACKGROUND = 0x00000000;
    public static final int HUD_BACKGROUND = 0xC8101010;
    public static final int HEADER = 0x00000000;
    public static final int SIDEBAR = 0x70000000;
    public static final int SURFACE = 0x55000000;
    public static final int SURFACE_RAISED = 0xC8101010;
    public static final int SURFACE_HOVER = 0x30FFFFFF;
    public static final int SURFACE_SELECTED = 0xD0000000;
    public static final int BORDER = 0x40FFFFFF;
    public static final int BORDER_STRONG = 0xFF808080;
    public static final int DIVIDER = 0x33FFFFFF;
    public static final int TRACK = 0xFF1C1C1C;
    public static final int CHIP = 0x55000000;
    public static final int SLOT = 0xFF8B8B8B;
    public static final int SLOT_BORDER = 0xFF373737;
    public static final int ACCENT = 0xFFFFFFFF;
    public static final int ACCENT_DARK = 0xFF808080;
    public static final int ACCENT_HOVER = 0xFFC6C6C6;
    public static final int ACCENT_TEXT = 0xFFFFFFFF;
    public static final int AMBER = 0xFFFFAA00;
    public static final int TEXT_BRIGHT = 0xFFFFFFFF;
    public static final int TEXT = 0xFFE0E0E0;
    public static final int TEXT_DIM = 0xFFA0A0A0;
    public static final int TEXT_DISABLED = 0xFF707070;
    public static final int SUCCESS = 0xFF55FF55;
    public static final int DANGER = 0xFFFF5555;
    public static final int DANGER_DARK = 0x40FF5555;
    public static final int WARNING = 0xFFFFAA00;
    public static final int FOCUS = 0xFFFFFFFF;
    public static final int SEPARATOR_DARK = 0xBF000000;
    public static final int SEPARATOR_LIGHT = 0x33FFFFFF;

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
