package org.lts.litematicaplus.gui;

public final class Theme {
    public static final int BACKGROUND = 0xF20B2440;
    public static final int HUD_BACKGROUND = 0xE00A2140;
    public static final int HEADER = 0xFF081B33;
    public static final int SIDEBAR = 0x99061629;
    public static final int SURFACE = 0x8C0A1E38;
    public static final int SURFACE_RAISED = 0xB3102C4C;
    public static final int SURFACE_HOVER = 0x2E62E3FF;
    public static final int SURFACE_SELECTED = 0x4762E3FF;
    public static final int BORDER = 0xFF4C7BA8;
    public static final int BORDER_STRONG = 0xFFA9D2F5;
    public static final int DIVIDER = 0xFF25507A;
    public static final int TRACK = 0xD906142A;
    public static final int CHIP = 0x99143457;
    public static final int SLOT = 0x99061629;
    public static final int SLOT_BORDER = 0xFF25507A;
    public static final int GRID_MINOR = 0x0FA8D4FF;
    public static final int GRID_MAJOR = 0x1AA8D4FF;
    public static final int ACCENT = 0xFF62E3FF;
    public static final int ACCENT_DARK = 0xFF26A3C4;
    public static final int ACCENT_TEXT = 0xFF04202B;
    public static final int AMBER = 0xFFFFB84D;
    public static final int TEXT_BRIGHT = 0xFFF4FAFF;
    public static final int TEXT = 0xFFDCEBFA;
    public static final int TEXT_DIM = 0xFF92B5D6;
    public static final int TEXT_DISABLED = 0xFF5A7594;
    public static final int SUCCESS = 0xFF7CF29A;
    public static final int DANGER = 0xFFFF8F85;
    public static final int DANGER_DARK = 0x66FF5A50;
    public static final int WARNING = 0xFFFFC857;
    public static final int FOCUS = 0xFFFFFFFF;

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
