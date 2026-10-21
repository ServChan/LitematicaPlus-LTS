package org.lts.litematicaplus.gui;

import java.util.List;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.item.ItemStack;

public final class Ui {
    private Ui() {
    }

    public static final java.time.Duration HINT_DELAY = java.time.Duration.ofMillis(500);

    public static <T extends net.minecraft.client.gui.components.AbstractWidget> T hint(T widget, Component text) {
        if (widget instanceof FlatButton button) {
            button.withHint(text);
        } else {
            widget.setTooltip(net.minecraft.client.gui.components.Tooltip.create(text));
        }
        return widget;
    }

    public static void decorate(Iterable<? extends net.minecraft.client.gui.components.events.GuiEventListener> children) {
        net.minecraft.locale.Language language = net.minecraft.locale.Language.getInstance();
        for (net.minecraft.client.gui.components.events.GuiEventListener child : children) {
            if (!(child instanceof net.minecraft.client.gui.components.AbstractWidget widget)) {
                continue;
            }
            widget.setTooltipDelay(HINT_DELAY);
            if (!(widget.getMessage().getContents() instanceof net.minecraft.network.chat.contents.TranslatableContents label)) {
                continue;
            }
            String key = label.getKey();
            if (language.has(key + ".help")) {
                hint(widget, Component.translatable(key + ".help"));
            } else if (key.startsWith("litematicaplus.option.") && language.has(key + ".tip")) {
                hint(widget, Component.translatable(key + ".tip"));
            }
        }
    }

    public static void panel(GuiGraphicsExtractor graphics, int x, int y, int width, int height) {
        graphics.fill(x, y, x + width, y + height, Theme.SURFACE);
        graphics.outline(x, y, width, height, Theme.BORDER);
    }

    public static void raised(GuiGraphicsExtractor graphics, int x, int y, int width, int height, int spine) {
        graphics.fill(x, y, x + width, y + height, Theme.SURFACE_RAISED);
        Blueprint.frame(graphics, x, y, width, height, Theme.BORDER, Theme.BORDER_STRONG);
    }

    public static void progress(GuiGraphicsExtractor graphics, int x, int y, int width, int height, double value, int color) {
        Blueprint.progress(graphics, x, y, width, height, value, color);
    }

    public static int chip(GuiGraphicsExtractor graphics, Font font, int x, int y, Component text, int color) {
        return chip(graphics, font, x, y, text, color, color);
    }

    public static int chip(GuiGraphicsExtractor graphics, Font font, int x, int y, Component text, int color, int textColor) {
        int width = font.width(text) + 10;
        graphics.fill(x, y + 4, x + 5, y + 9, textColor == Theme.TEXT_DIM ? Theme.TEXT_DISABLED : color);
        graphics.text(font, text, x + 9, y + 3, textColor == Theme.TEXT_DIM ? Theme.TEXT_DIM : Theme.TEXT, false);
        return x + width + 8;
    }

    public static void label(GuiGraphicsExtractor graphics, Font font, Component text, int x, int y) {
        Blueprint.section(graphics, font, text, x, y, 0);
    }

    public static void section(GuiGraphicsExtractor graphics, Font font, Component text, int x, int y, int width) {
        Blueprint.section(graphics, font, text, x, y, width);
    }

    public static void heading(GuiGraphicsExtractor graphics, Font font, Component text, int x, int y, float scale, int color) {
        graphics.text(font, text, x, y, color, true);
    }

    public static void clipped(GuiGraphicsExtractor graphics, Font font, String text, int x, int y, int maxWidth, int color) {
        if (maxWidth <= 0) {
            return;
        }
        String shown = font.width(text) > maxWidth ? font.plainSubstrByWidth(text, Math.max(0, maxWidth - 6)) + ".." : text;
        graphics.text(font, shown, x, y, color, false);
    }

    public static int wrapped(GuiGraphicsExtractor graphics, Font font, Component text, int x, int y, int width,
            int maxLines, int color, int mouseX, int mouseY) {
        if (width <= 0 || maxLines <= 0) {
            return 0;
        }
        List<FormattedCharSequence> lines = font.split(text, width);
        int shown = Math.min(lines.size(), maxLines);
        for (int i = 0; i < shown; i++) {
            graphics.text(font, lines.get(i), x, y + i * 10, color, false);
        }
        if (lines.size() > shown && mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + shown * 10) {
            graphics.setTooltipForNextFrame(font.split(text, 240), mouseX, mouseY);
        }
        return shown;
    }

    public static void right(GuiGraphicsExtractor graphics, Font font, String text, int right, int y, int color) {
        graphics.text(font, text, right - font.width(text), y, color, false);
    }

    public static void slot(GuiGraphicsExtractor graphics, ItemStack stack, int x, int y) {
        graphics.fill(x, y, x + 18, y + 18, Theme.SLOT);
        graphics.fill(x, y, x + 17, y + 1, Theme.SLOT_BORDER);
        graphics.fill(x, y, x + 1, y + 17, Theme.SLOT_BORDER);
        graphics.fill(x + 1, y + 17, x + 18, y + 18, 0xFFFFFFFF);
        graphics.fill(x + 17, y + 1, x + 18, y + 18, 0xFFFFFFFF);
        graphics.fakeItem(stack, x + 1, y + 1);
    }

    public static void emptyState(GuiGraphicsExtractor graphics, Font font, int x, int y, int width, int height,
            Component title, Component hint) {
        panel(graphics, x, y, width, height);
        int centerY = y + height / 2;
        heading(graphics, font, title, x + (width - font.width(title)) / 2, centerY - 16, 1.0F, Theme.TEXT_BRIGHT);
        int hintWidth = Math.min(width - 32, 320);
        graphics.textWithWordWrap(font, hint, x + (width - hintWidth) / 2, centerY, hintWidth, Theme.TEXT_DIM, false);
    }
}
