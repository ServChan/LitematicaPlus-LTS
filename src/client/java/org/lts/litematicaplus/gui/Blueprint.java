package org.lts.litematicaplus.gui;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;

public final class Blueprint {
    private Blueprint() {
    }

    public static void frame(GuiGraphicsExtractor graphics, int x, int y, int width, int height, int line, int tick) {
        graphics.outline(x, y, width, height, line);
    }

    public static void dashed(GuiGraphicsExtractor graphics, int x, int y, int width, int color) {
        graphics.fill(x, y, x + width, y + 1, Theme.SEPARATOR_DARK);
        graphics.fill(x, y + 1, x + width, y + 2, Theme.SEPARATOR_LIGHT);
    }

    public static void progress(GuiGraphicsExtractor graphics, int x, int y, int width, int height, double value,
            int color) {
        int top = y + Math.max(0, (height - 3) / 2);
        graphics.fill(x, top, x + width, top + 3, Theme.TRACK);
        int filled = (int) Math.round(width * Math.max(0.0, Math.min(1.0, value)));
        if (filled > 0) {
            graphics.fill(x, top, x + filled, top + 3, Theme.alpha(color, 0xC0));
        }
    }

    public static void section(GuiGraphicsExtractor graphics, Font font, Component text, int x, int y, int width) {
        graphics.text(font, text, x, y, Theme.TEXT_BRIGHT, true);
    }

}
