package org.lts.litematicaplus.gui;

import com.mojang.blaze3d.platform.InputConstants;
import java.util.function.IntConsumer;
import java.util.function.IntFunction;
import java.util.function.IntSupplier;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarratedElementType;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.input.InputWithModifiers;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

public final class Stepper extends AbstractWidget {
    private static final int BUTTON_WIDTH = 18;

    private final IntSupplier getter;
    private final IntConsumer setter;
    private final int min;
    private final int max;
    private final int step;
    private IntFunction<String> formatter = Integer::toString;
    private boolean labelled = true;

    public Stepper(int x, int y, int width, Component label, IntSupplier getter, IntConsumer setter, int min, int max,
            int step) {
        super(x, y, width, Theme.CONTROL_HEIGHT, label);
        this.getter = getter;
        this.setter = setter;
        this.min = min;
        this.max = max;
        this.step = Math.max(1, step);
    }

    public Stepper format(IntFunction<String> function) {
        this.formatter = function;
        return this;
    }

    public Stepper compact() {
        this.labelled = false;
        return this;
    }

    private void change(int direction, InputWithModifiers input) {
        int amount = this.step * (input.hasShiftDown() ? 10 : 1);
        long next = (long) this.getter.getAsInt() + (long) direction * amount;
        this.setter.accept((int) Math.max(this.min, Math.min(this.max, next)));
    }

    @Override
    public void onClick(MouseButtonEvent event, boolean doubleClick) {
        int right = getX() + getWidth();
        if (event.x() >= right - BUTTON_WIDTH) {
            change(1, event);
        } else if (event.x() >= right - BUTTON_WIDTH * 2 - valueWidth() && event.x() < right - BUTTON_WIDTH - valueWidth()) {
            change(-1, event);
        }
    }

    private int valueWidth() {
        return this.labelled ? Math.min(64, Math.max(40, getWidth() / 4)) : getWidth() - BUTTON_WIDTH * 2;
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (!this.active || !isFocused()) {
            return false;
        }
        if (event.key() == InputConstants.KEY_LEFT) {
            change(-1, event);
            return true;
        }
        if (event.key() == InputConstants.KEY_RIGHT) {
            change(1, event);
            return true;
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
        int valueWidth = valueWidth();
        int plusX = x + width - BUTTON_WIDTH;
        int valueX = plusX - valueWidth;
        int minusX = valueX - BUTTON_WIDTH;
        int textY = y + (height - 8) / 2;
        if (this.labelled) {
            graphics.fill(x, y, minusX, y + height, Theme.SURFACE_RAISED);
            graphics.outline(x, y, width, height, Theme.BORDER);
            Ui.clipped(graphics, font, getMessage().getString(), x + 8, textY, minusX - x - 12,
                    this.active ? Theme.TEXT : Theme.TEXT_DISABLED);
        }
        boolean over = this.active && mouseY >= y && mouseY < y + height;
        boolean minusHover = over && mouseX >= minusX && mouseX < valueX;
        boolean plusHover = over && mouseX >= plusX && mouseX < plusX + BUTTON_WIDTH;
        int value = this.getter.getAsInt();
        graphics.fill(minusX, y, valueX, y + height, minusHover ? Theme.SURFACE_HOVER : Theme.CHIP);
        graphics.fill(valueX, y, plusX, y + height, Theme.TRACK);
        graphics.fill(plusX, y, plusX + BUTTON_WIDTH, y + height, plusHover ? Theme.SURFACE_HOVER : Theme.CHIP);
        graphics.outline(minusX, y, BUTTON_WIDTH * 2 + valueWidth, height, Theme.BORDER);
        graphics.centeredText(font, "-", minusX + BUTTON_WIDTH / 2, textY, value > this.min ? Theme.TEXT_BRIGHT : Theme.TEXT_DISABLED);
        graphics.centeredText(font, "+", plusX + BUTTON_WIDTH / 2, textY, value < this.max ? Theme.TEXT_BRIGHT : Theme.TEXT_DISABLED);
        graphics.centeredText(font, this.formatter.apply(value), valueX + valueWidth / 2, textY, Theme.TEXT_BRIGHT);
        if (isFocused()) {
            graphics.outline(x - 1, y - 1, width + 2, height + 2, Theme.FOCUS);
        }
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput output) {
        output.add(NarratedElementType.TITLE, Component.literal(getMessage().getString() + ": "
                + this.formatter.apply(this.getter.getAsInt())));
    }
}
