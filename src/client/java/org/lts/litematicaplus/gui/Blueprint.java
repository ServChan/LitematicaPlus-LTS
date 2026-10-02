package org.lts.litematicaplus.gui;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;

public final class Blueprint {
    public static final int MINOR = 8;
    public static final int MAJOR = 32;

    private Blueprint() {
    }

    public static void grid(GuiGraphicsExtractor graphics, int x, int y, int width, int height) {
        int right = x + width;
        int bottom = y + height;
        for (int line = (x + MINOR - 1) / MINOR * MINOR; line < right; line += MINOR) {
            graphics.fill(line, y, line + 1, bottom, line % MAJOR == 0 ? Theme.GRID_MAJOR : Theme.GRID_MINOR);
        }
        for (int line = (y + MINOR - 1) / MINOR * MINOR; line < bottom; line += MINOR) {
            graphics.fill(x, line, right, line + 1, line % MAJOR == 0 ? Theme.GRID_MAJOR : Theme.GRID_MINOR);
        }
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

    public static void dimension(GuiGraphicsExtractor graphics, int x, int y, int width, int color) {
        graphics.fill(x, y, x + width, y + 1, color);
        graphics.fill(x, y - 2, x + 1, y + 3, color);
        graphics.fill(x + width - 1, y - 2, x + width, y + 3, color);
    }

    public static void ruler(GuiGraphicsExtractor graphics, int x, int y, int height, int color) {
        graphics.fill(x, y, x + 1, y + height, color);
        for (int step = 0; step < height; step += 4) {
            int length = step % 32 == 0 ? 5 : step % 16 == 0 ? 3 : 1;
            graphics.fill(x + 1, y + step, x + 1 + length, y + step + 1, color);
        }
    }

    public static void progress(GuiGraphicsExtractor graphics, int x, int y, int width, int height, double value,
            int color) {
        int middle = y + height / 2;
        graphics.fill(x, y, x + width, y + height, Theme.alpha(Theme.TRACK, 0x99));
        dimension(graphics, x, middle, width, Theme.DIVIDER);
        int filled = (int) Math.round(width * Math.max(0.0, Math.min(1.0, value)));
        if (filled > 0) {
            graphics.fill(x, y, x + filled, y + height, color);
            graphics.fill(x + filled - 1, y - 2, x + filled, y + height + 2, Theme.AMBER);
        }
    }

    public static void marker(GuiGraphicsExtractor graphics, int x, int y, int color) {
        graphics.fill(x, y, x + 3, y + 3, color);
    }

    public static void section(GuiGraphicsExtractor graphics, Font font, Component text, int x, int y, int width) {
        marker(graphics, x, y + 2, Theme.AMBER);
        graphics.text(font, text, x + 6, y, Theme.ACCENT, false);
        int ruleX = x + 6 + font.width(text) + 6;
        if (width > 0 && ruleX < x + width) {
            dashed(graphics, ruleX, y + 4, x + width - ruleX, Theme.DIVIDER);
        }
    }

    public static void toggle(GuiGraphicsExtractor graphics, int x, int y, boolean on, boolean active) {
        int width = 22;
        int height = 10;
        int line = !active ? Theme.TEXT_DISABLED : on ? Theme.ACCENT : Theme.BORDER;
        graphics.fill(x, y, x + width, y + height, on && active ? Theme.alpha(Theme.ACCENT, 0x40) : Theme.TRACK);
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
