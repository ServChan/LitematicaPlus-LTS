package org.lts.litematicaplus.gui;

public final class Theme {
    public static final int BACKGROUND = 0xF00D141F;
    public static final int HUD_BACKGROUND = 0xE00D141F;
    public static final int HEADER = 0xFF182638;
    public static final int SIDEBAR = 0xFF111B29;
    public static final int SURFACE = 0xFF152231;
    public static final int SURFACE_RAISED = 0xFF1C2D42;
    public static final int SURFACE_HOVER = 0xFF233852;
    public static final int SURFACE_SELECTED = 0xFF1F4258;
    public static final int BORDER = 0xFF5F7DA3;
    public static final int BORDER_STRONG = 0xFF8FB0D6;
    public static final int DIVIDER = 0xFF2E435E;
    public static final int TRACK = 0xFF0A1019;
    public static final int CHIP = 0xFF22364E;
    public static final int SLOT = 0x80000000;
    public static final int SLOT_BORDER = 0xFF37474F;
    public static final int ACCENT = 0xFF00E5FF;
    public static final int ACCENT_DARK = 0xFF00A7BA;
    public static final int ACCENT_TEXT = 0xFF04222A;
    public static final int TEXT_BRIGHT = 0xFFFFFFFF;
    public static final int TEXT = 0xFFE0E0E0;
    public static final int TEXT_DIM = 0xFF9FB5CB;
    public static final int TEXT_DISABLED = 0xFF5C7088;
    public static final int SUCCESS = 0xFF55FF55;
    public static final int DANGER = 0xFFFF9090;
    public static final int DANGER_DARK = 0xFF8C2B2B;
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
