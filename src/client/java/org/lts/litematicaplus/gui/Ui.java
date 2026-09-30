package org.lts.litematicaplus.gui;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

public final class Ui {
    private Ui() {
    }

    public static void panel(GuiGraphicsExtractor graphics, int x, int y, int width, int height) {
        graphics.fill(x, y, x + width, y + height, Theme.SURFACE);
        graphics.outline(x, y, width, height, Theme.BORDER);
    }

    public static void raised(GuiGraphicsExtractor graphics, int x, int y, int width, int height, int spine) {
        graphics.fill(x, y, x + width, y + height, Theme.SURFACE_RAISED);
        graphics.outline(x, y, width, height, Theme.BORDER);
        if (spine != 0) {
            graphics.fill(x, y, x + 3, y + height, spine);
        }
    }

    public static void progress(GuiGraphicsExtractor graphics, int x, int y, int width, int height, double value, int color) {
        graphics.fill(x, y, x + width, y + height, Theme.TRACK);
        int filled = (int) Math.round(width * Math.max(0.0, Math.min(1.0, value)));
        if (filled > 0) {
            graphics.fill(x, y, x + filled, y + height, color);
        }
        graphics.outline(x, y, width, height, Theme.BORDER);
    }

    public static int chip(GuiGraphicsExtractor graphics, Font font, int x, int y, Component text, int color) {
        int width = font.width(text) + 12;
        graphics.fill(x, y, x + width, y + 13, Theme.alpha(color, 0x30));
        graphics.outline(x, y, width, 13, Theme.alpha(color, 0xB0));
        graphics.text(font, text, x + 6, y + 3, color, false);
        return x + width + 5;
    }

    public static void label(GuiGraphicsExtractor graphics, Font font, Component text, int x, int y) {
        graphics.text(font, text, x, y, Theme.ACCENT, false);
    }

    public static void heading(GuiGraphicsExtractor graphics, Font font, Component text, int x, int y, float scale, int color) {
        graphics.pose().pushMatrix();
        graphics.pose().translate(x, y);
        graphics.pose().scale(scale, scale);
        graphics.text(font, text, 0, 0, color, false);
        graphics.pose().popMatrix();
    }

    public static void clipped(GuiGraphicsExtractor graphics, Font font, String text, int x, int y, int maxWidth, int color) {
        if (maxWidth <= 0) {
            return;
        }
        String shown = font.width(text) > maxWidth ? font.plainSubstrByWidth(text, Math.max(0, maxWidth - 6)) + ".." : text;
        graphics.text(font, shown, x, y, color, false);
    }

    public static void right(GuiGraphicsExtractor graphics, Font font, String text, int right, int y, int color) {
        graphics.text(font, text, right - font.width(text), y, color, false);
    }

    public static void slot(GuiGraphicsExtractor graphics, ItemStack stack, int x, int y) {
        graphics.fill(x, y, x + 18, y + 18, Theme.SLOT);
        graphics.outline(x, y, 18, 18, Theme.SLOT_BORDER);
        graphics.fakeItem(stack, x + 1, y + 1);
    }

    public static void emptyState(GuiGraphicsExtractor graphics, Font font, int x, int y, int width, int height,
            Component title, Component hint) {
        panel(graphics, x, y, width, height);
        int centerY = y + height / 2;
        heading(graphics, font, title, x + (width - Math.round(font.width(title) * 1.5F)) / 2, centerY - 22, 1.5F,
                Theme.TEXT_BRIGHT);
        int hintWidth = Math.min(width - 32, 320);
        graphics.textWithWordWrap(font, hint, x + (width - hintWidth) / 2, centerY, hintWidth, Theme.TEXT_DIM, false);
    }
}
