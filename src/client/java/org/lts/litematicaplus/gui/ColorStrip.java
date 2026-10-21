package org.lts.litematicaplus.gui;

import com.mojang.blaze3d.platform.InputConstants;
import java.util.function.IntConsumer;
import java.util.function.IntSupplier;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarratedElementType;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import org.lts.litematicaplus.LitematicaPlusConfig;

public final class ColorStrip extends AbstractWidget {
    public static final int[] PRESETS = {0xFFFFFF, 0x8FA7BF, 0x2FC7FF, 0x5B8CFF, 0x55FF55, 0xFFD166, 0xFFA62B, 0xFF4444,
            0xFF4FD8};
    private static final int GAP = 2;
    private static final int MAX_CELL = 12;
    private static final int MIN_CELL = 6;
    private static final int SWATCH = 8;
    private static final int LABEL_RESERVE = 132;

    private final IntSupplier getter;
    private final IntConsumer setter;
    private int cursor;

    public ColorStrip(int x, int y, int width, Component label, IntSupplier getter, IntConsumer setter) {
        super(x, y, width, Theme.CONTROL_HEIGHT, label);
        this.getter = getter;
        this.setter = setter;
        int current = selected();
        this.cursor = Math.max(current, 0);
    }

    private int selected() {
        int rgb = this.getter.getAsInt() & 0xFFFFFF;
        for (int i = 0; i < PRESETS.length; i++) {
            if (PRESETS[i] == rgb) {
                return i;
            }
        }
        return -1;
    }

    private int cell() {
        int room = getWidth() - LABEL_RESERVE;
        return Math.max(MIN_CELL, Math.min(MAX_CELL, (room - GAP * (PRESETS.length - 1)) / PRESETS.length));
    }

    private int stripLeft() {
        int cell = cell();
        return getX() + getWidth() - cell * PRESETS.length - GAP * (PRESETS.length - 1);
    }

    private int presetAt(double mouseX) {
        int cell = cell();
        int offset = (int) Math.floor(mouseX) - stripLeft();
        if (offset < 0) {
            return -1;
        }
        int index = offset / (cell + GAP);
        return index < PRESETS.length && offset % (cell + GAP) < cell ? index : -1;
    }

    private void pick(int index) {
        this.cursor = index;
        this.setter.accept(PRESETS[index]);
    }

    @Override
    public void onClick(MouseButtonEvent event, boolean doubleClick) {
        int index = presetAt(event.x());
        if (index >= 0) {
            pick(index);
        }
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (!this.active || !isFocused()) {
            return false;
        }
        int key = event.key();
        if (key == InputConstants.KEY_LEFT) {
            this.cursor = (this.cursor + PRESETS.length - 1) % PRESETS.length;
            return true;
        }
        if (key == InputConstants.KEY_RIGHT) {
            this.cursor = (this.cursor + 1) % PRESETS.length;
            return true;
        }
        if (key == InputConstants.KEY_RETURN || key == InputConstants.KEY_NUMPADENTER || key == InputConstants.KEY_SPACE) {
            pick(this.cursor);
            return true;
        }
        return false;
    }

    @Override
    protected void extractWidgetRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        Font font = Minecraft.getInstance().font;
        int x = getX();
        int y = getY();
        int height = getHeight();
        int cell = cell();
        int left = stripLeft();
        int textY = y + (height - 8) / 2;
        int labelRight = left - GAP;
        graphics.fill(x, y, labelRight, y + height, Theme.SURFACE);
        graphics.outline(x, y, labelRight - x, height, Theme.BORDER);
        int swatchY = y + (height - SWATCH) / 2;
        graphics.fill(x + 6, swatchY, x + 6 + SWATCH, swatchY + SWATCH, 0xFF000000 | this.getter.getAsInt());
        graphics.outline(x + 5, swatchY - 1, SWATCH + 2, SWATCH + 2, Theme.BORDER_STRONG);
        Ui.clipped(graphics, font, getMessage().getString(), x + SWATCH + 12, textY, labelRight - x - SWATCH - 16,
                Theme.TEXT);
        int selected = selected();
        int hovered = isHovered() ? presetAt(mouseX) : -1;
        int cellTop = y + (height - cell) / 2;
        for (int i = 0; i < PRESETS.length; i++) {
            int cellX = left + i * (cell + GAP);
            graphics.fill(cellX, cellTop, cellX + cell, cellTop + cell, 0xFF000000 | PRESETS[i]);
            if (i == selected) {
                graphics.outline(cellX, cellTop, cell, cell, Theme.TEXT_BRIGHT);
                graphics.outline(cellX + 1, cellTop + 1, cell - 2, cell - 2, 0xFF000000);
                graphics.fill(cellX + 3, cellTop + cell - 4, cellX + cell - 3, cellTop + cell - 2, 0xFF000000);
            } else {
                graphics.outline(cellX, cellTop, cell, cell, i == hovered ? Theme.TEXT_BRIGHT : Theme.SLOT_BORDER);
            }
            if (isFocused() && i == this.cursor) {
                graphics.outline(cellX - 1, cellTop - 1, cell + 2, cell + 2, Theme.FOCUS);
            }
        }
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput output) {
        output.add(NarratedElementType.TITLE, Component.literal(getMessage().getString() + ": "
                + LitematicaPlusConfig.hex(this.getter.getAsInt())));
        output.add(NarratedElementType.HINT, Component.translatable("litematicaplus.settings.color_hint"));
    }
}
