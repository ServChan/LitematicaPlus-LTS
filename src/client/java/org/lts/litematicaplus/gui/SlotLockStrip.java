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

public final class SlotLockStrip extends AbstractWidget {
    private static final int SLOTS = 9;
    private static final int GAP = 2;
    private static final int MAX_CELL = 20;
    private static final int MIN_CELL = 12;
    private static final int[] DIGITS = {InputConstants.KEY_1, InputConstants.KEY_2, InputConstants.KEY_3,
            InputConstants.KEY_4, InputConstants.KEY_5, InputConstants.KEY_6, InputConstants.KEY_7, InputConstants.KEY_8,
            InputConstants.KEY_9};

    private final IntSupplier getter;
    private final IntConsumer setter;
    private int cursor;

    public SlotLockStrip(int x, int y, int width, Component label, IntSupplier getter, IntConsumer setter) {
        super(x, y, width, Theme.CONTROL_HEIGHT, label);
        this.getter = getter;
        this.setter = setter;
    }

    private int cell() {
        Font font = Minecraft.getInstance().font;
        int room = getWidth() - Math.min(getWidth() / 2, font.width(getMessage()) + 16);
        return Math.max(MIN_CELL, Math.min(MAX_CELL, (room - GAP * (SLOTS - 1)) / SLOTS));
    }

    private int stripLeft() {
        int cell = cell();
        return getX() + getWidth() - cell * SLOTS - GAP * (SLOTS - 1);
    }

    private int slotAt(double mouseX) {
        int cell = cell();
        int offset = (int) Math.floor(mouseX) - stripLeft();
        if (offset < 0) {
            return -1;
        }
        int slot = offset / (cell + GAP);
        return slot < SLOTS && offset % (cell + GAP) < cell ? slot : -1;
    }

    private boolean locked(int slot) {
        return (this.getter.getAsInt() & 1 << slot) != 0;
    }

    private void toggle(int slot) {
        this.cursor = slot;
        this.setter.accept(this.getter.getAsInt() ^ 1 << slot);
    }

    @Override
    public void onClick(MouseButtonEvent event, boolean doubleClick) {
        int slot = slotAt(event.x());
        if (slot >= 0) {
            toggle(slot);
        }
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (!this.active || !isFocused()) {
            return false;
        }
        int key = event.key();
        if (key == InputConstants.KEY_LEFT) {
            this.cursor = (this.cursor + SLOTS - 1) % SLOTS;
            return true;
        }
        if (key == InputConstants.KEY_RIGHT) {
            this.cursor = (this.cursor + 1) % SLOTS;
            return true;
        }
        if (key == InputConstants.KEY_RETURN || key == InputConstants.KEY_NUMPADENTER || key == InputConstants.KEY_SPACE) {
            toggle(this.cursor);
            return true;
        }
        for (int slot = 0; slot < SLOTS; slot++) {
            if (key == DIGITS[slot]) {
                toggle(slot);
                return true;
            }
        }
        return false;
    }

    @Override
    protected void extractWidgetRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        Font font = Minecraft.getInstance().font;
        int x = getX();
        int y = getY();
        int width = getWidth();
        int height = getHeight();
        int cell = cell();
        int left = stripLeft();
        int textY = y + (height - 8) / 2;
        graphics.fill(x, y, left - GAP, y + height, Theme.SURFACE);
        graphics.outline(x, y, left - GAP - x, height, Theme.BORDER);
        Ui.clipped(graphics, font, getMessage().getString(), x + 8, textY, left - GAP - x - 12, Theme.TEXT);
        int hovered = isHovered() ? slotAt(mouseX) : -1;
        int cellTop = y + (height - cell) / 2;
        for (int slot = 0; slot < SLOTS; slot++) {
            int cellX = left + slot * (cell + GAP);
            boolean locked = locked(slot);
            graphics.fill(cellX, cellTop, cellX + cell, cellTop + cell, locked ? Theme.SURFACE_SELECTED
                    : slot == hovered ? Theme.SURFACE_HOVER : Theme.SURFACE);
            graphics.outline(cellX, cellTop, cell, cell, locked ? Theme.AMBER : slot == hovered ? Theme.BORDER_STRONG
                    : Theme.BORDER);
            if (locked) {
                graphics.fill(cellX + 2, cellTop + cell - 3, cellX + cell - 2, cellTop + cell - 2, Theme.AMBER);
            }
            String number = Integer.toString(slot + 1);
            graphics.text(font, number, cellX + (cell - font.width(number) + 1) / 2, cellTop + (cell - 8) / 2 - (locked ? 1 : 0),
                    locked ? Theme.TEXT_BRIGHT : Theme.TEXT_DIM, false);
            if (isFocused() && slot == this.cursor) {
                graphics.outline(cellX - 1, cellTop - 1, cell + 2, cell + 2, Theme.FOCUS);
            }
        }
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput output) {
        StringBuilder slots = new StringBuilder();
        for (int slot = 0; slot < SLOTS; slot++) {
            if (locked(slot)) {
                slots.append(slots.isEmpty() ? "" : ", ").append(slot + 1);
            }
        }
        output.add(NarratedElementType.TITLE, Component.literal(getMessage().getString() + ": ").append(slots.isEmpty()
                ? Component.translatable("litematicaplus.state.off") : Component.literal(slots.toString())));
        output.add(NarratedElementType.HINT, Component.translatable("litematicaplus.option.locked_slots.tip"));
    }
}
