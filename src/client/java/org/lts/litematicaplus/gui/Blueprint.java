package org.lts.litematicaplus.gui;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;

public final class Blueprint {
    private Blueprint() {
    }

    public static void frame(GuiGraphicsExtractor graphics, int x, int y, int width, int height, int line, int tick) {
        graphics.outline(x, y, width, height, line);
        ticks(graphics, x, y, width, height, tick);
    }

    public static void ticks(GuiGraphicsExtractor graphics, int x, int y, int width, int height, int color) {
        int length = Math.max(2, Math.min(5, Math.min(width, height) / 4));
        int right = x + width;
        int bottom = y + height;
        graphics.fill(x, y, x + length, y + 1, color);
        graphics.fill(x, y, x + 1, y + length, color);
        graphics.fill(right - length, y, right, y + 1, color);
        graphics.fill(right - 1, y, right, y + length, color);
        graphics.fill(x, bottom - 1, x + length, bottom, color);
        graphics.fill(x, bottom - length, x + 1, bottom, color);
        graphics.fill(right - length, bottom - 1, right, bottom, color);
        graphics.fill(right - 1, bottom - length, right, bottom, color);
    }

    public static void dashed(GuiGraphicsExtractor graphics, int x, int y, int width, int color) {
        for (int dash = x; dash < x + width; dash += 4) {
            graphics.fill(dash, y, Math.min(dash + 2, x + width), y + 1, color);
        }
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

    public static void marker(GuiGraphicsExtractor graphics, int x, int y, int color) {
        graphics.fill(x, y, x + 3, y + 3, color);
    }

    public static void section(GuiGraphicsExtractor graphics, Font font, Component text, int x, int y, int width) {
        marker(graphics, x, y + 2, Theme.AMBER);
        graphics.text(font, text, x + 6, y, Theme.TEXT_DIM, true);
    }

    public static void toggle(GuiGraphicsExtractor graphics, int x, int y, boolean on, boolean active) {
        int width = 22;
        int height = 10;
        int line = !active ? Theme.DIVIDER : on ? Theme.ACCENT_DARK : Theme.BORDER;
        graphics.fill(x, y, x + width, y + height, Theme.TRACK);
        graphics.outline(x, y, width, height, line);
        int knobX = on ? x + width - 8 : x + 2;
        graphics.fill(knobX, y + 2, knobX + 6, y + height - 2, !active ? Theme.TEXT_DISABLED : on ? Theme.ACCENT : Theme.TEXT_DIM);
        if (on) {
            graphics.fill(x + 4, y + 4, x + 5, y + height - 4, active ? Theme.TEXT_BRIGHT : Theme.TEXT_DISABLED);
        } else {
            graphics.outline(x + width - 7, y + 3, 4, 4, active ? Theme.TEXT_DIM : Theme.TEXT_DISABLED);
        }
    }
}
