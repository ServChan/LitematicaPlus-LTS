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
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.InputWithModifiers;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Util;

public final class Stepper extends AbstractWidget {
    private static final int BUTTON_WIDTH = 18;
    private static final int MAX_DIGITS = 10;

    private final IntSupplier getter;
    private final IntConsumer setter;
    private final int min;
    private final int max;
    private final int step;
    private IntFunction<String> formatter = Integer::toString;
    private boolean labelled = true;
    private boolean buttons = true;
    private boolean editing;
    private boolean replaceOnType;
    private String buffer = "";

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

    public Stepper field() {
        this.buttons = false;
        return this;
    }

    public boolean editing() {
        return this.editing;
    }

    private int buttonWidth() {
        return this.buttons ? BUTTON_WIDTH : 0;
    }

    private void change(int direction, InputWithModifiers input) {
        commit();
        int amount = this.step * (input.hasShiftDown() ? 10 : 1);
        long next = (long) this.getter.getAsInt() + (long) direction * amount;
        this.setter.accept((int) Math.max(this.min, Math.min(this.max, next)));
    }

    private void beginEdit(boolean keepValue) {
        this.editing = true;
        this.buffer = keepValue ? Integer.toString(this.getter.getAsInt()) : "";
        this.replaceOnType = keepValue;
    }

    public void commit() {
        if (!this.editing) {
            return;
        }
        this.editing = false;
        this.replaceOnType = false;
        try {
            long parsed = Long.parseLong(this.buffer.strip());
            this.setter.accept((int) Math.max(this.min, Math.min(this.max, parsed)));
        } catch (NumberFormatException exception) {
            this.buffer = "";
        }
    }

    public void cancel() {
        this.editing = false;
        this.replaceOnType = false;
        this.buffer = "";
    }

    public void enter(int value) {
        this.editing = true;
        this.replaceOnType = false;
        this.buffer = Integer.toString(Math.max(this.min, Math.min(this.max, value)));
    }

    @Override
    public void onClick(MouseButtonEvent event, boolean doubleClick) {
        int right = getX() + getWidth();
        int valueRight = right - buttonWidth();
        int valueLeft = valueRight - valueWidth();
        if (this.buttons && event.x() >= valueRight) {
            change(1, event);
        } else if (this.buttons && event.x() >= valueLeft - BUTTON_WIDTH && event.x() < valueLeft) {
            change(-1, event);
        } else if (event.x() >= valueLeft && event.x() < valueRight || !this.buttons) {
            if (!this.editing) {
                beginEdit(true);
            }
        }
    }

    private int valueWidth() {
        if (!this.buttons) {
            return Math.max(10, getWidth() - (this.labelled ? 14 : 0));
        }
        return this.labelled ? Math.min(64, Math.max(40, getWidth() / 4)) : getWidth() - BUTTON_WIDTH * 2;
    }

    @Override
    public void setFocused(boolean focused) {
        boolean changed = focused != isFocused();
        super.setFocused(focused);
        if (changed) {
            Minecraft.getInstance().onTextInputFocusChange(this, focused);
        }
        if (!focused) {
            commit();
        }
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (!this.active || !isFocused()) {
            return false;
        }
        int key = event.key();
        if (this.editing) {
            if (key == InputConstants.KEY_RETURN || key == InputConstants.KEY_NUMPADENTER) {
                commit();
                return true;
            }
            if (key == InputConstants.KEY_ESCAPE) {
                cancel();
                return true;
            }
            if (key == InputConstants.KEY_BACKSPACE) {
                if (this.replaceOnType) {
                    this.buffer = "";
                } else if (!this.buffer.isEmpty()) {
                    this.buffer = this.buffer.substring(0, this.buffer.length() - 1);
                }
                this.replaceOnType = false;
                return true;
            }
            if (key == InputConstants.KEY_DELETE) {
                this.buffer = "";
                this.replaceOnType = false;
                return true;
            }
            if (key == InputConstants.KEY_LEFT || key == InputConstants.KEY_RIGHT || key == InputConstants.KEY_END
                    || key == InputConstants.KEY_HOME) {
                this.replaceOnType = false;
                return true;
            }
            return false;
        }
        if (key == InputConstants.KEY_LEFT) {
            change(-1, event);
            return true;
        }
        if (key == InputConstants.KEY_RIGHT) {
            change(1, event);
            return true;
        }
        if (key == InputConstants.KEY_RETURN || key == InputConstants.KEY_NUMPADENTER) {
            beginEdit(true);
            return true;
        }
        return false;
    }

    @Override
    public boolean charTyped(CharacterEvent event) {
        if (!this.active || !isFocused()) {
            return false;
        }
        int codepoint = event.codepoint();
        boolean digit = codepoint >= '0' && codepoint <= '9';
        boolean minus = codepoint == '-' && this.min < 0;
        if (!digit && !minus) {
            return false;
        }
        if (!this.editing) {
            beginEdit(false);
        }
        if (this.replaceOnType) {
            this.buffer = "";
            this.replaceOnType = false;
        }
        if (minus) {
            this.buffer = this.buffer.startsWith("-") ? this.buffer.substring(1) : "-" + this.buffer;
        } else if (this.buffer.length() < MAX_DIGITS) {
            this.buffer = this.buffer + (char) codepoint;
        }
        return true;
    }

    @Override
    protected void extractWidgetRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        Font font = Minecraft.getInstance().font;
        int x = getX();
        int y = getY();
        int width = getWidth();
        int height = getHeight();
        int valueWidth = valueWidth();
        int plusX = x + width - buttonWidth();
        int valueX = plusX - valueWidth;
        int minusX = valueX - buttonWidth();
        int textY = y + (height - 8) / 2;
        if (this.labelled) {
            graphics.fill(x, y, minusX, y + height, Theme.SURFACE_RAISED);
            graphics.outline(x, y, width, height, Theme.BORDER);
            int labelSpace = minusX - x;
            int inset = labelSpace < 40 ? 4 : 8;
            Ui.clipped(graphics, font, getMessage().getString(), x + inset, textY, labelSpace - inset - 2,
                    this.active ? Theme.TEXT : Theme.TEXT_DISABLED);
        }
        boolean over = this.active && mouseY >= y && mouseY < y + height;
        int value = this.getter.getAsInt();
        graphics.fill(valueX, y, plusX, y + height, Theme.TRACK);
        if (this.buttons) {
            boolean minusHover = over && mouseX >= minusX && mouseX < valueX;
            boolean plusHover = over && mouseX >= plusX && mouseX < plusX + BUTTON_WIDTH;
            graphics.fill(minusX, y, valueX, y + height, minusHover ? Theme.SURFACE_HOVER : Theme.CHIP);
            graphics.fill(plusX, y, plusX + BUTTON_WIDTH, y + height, plusHover ? Theme.SURFACE_HOVER : Theme.CHIP);
            graphics.centeredText(font, "-", minusX + BUTTON_WIDTH / 2, textY,
                    value > this.min ? Theme.TEXT_BRIGHT : Theme.TEXT_DISABLED);
            graphics.centeredText(font, "+", plusX + BUTTON_WIDTH / 2, textY,
                    value < this.max ? Theme.TEXT_BRIGHT : Theme.TEXT_DISABLED);
        }
        graphics.outline(minusX, y, buttonWidth() * 2 + valueWidth, height, this.editing ? Theme.ACCENT : Theme.BORDER);
        String shown;
        if (this.editing) {
            boolean caret = Util.getMillis() / 400L % 2L == 0L;
            shown = this.buffer + (caret ? "_" : " ");
        } else {
            shown = this.formatter.apply(value);
        }
        if (font.width(shown) > valueWidth - 4) {
            shown = font.plainSubstrByWidth(shown, valueWidth - 4, true);
        }
        if (this.editing && this.replaceOnType) {
            int selected = font.width(this.buffer);
            int start = valueX + valueWidth / 2 - font.width(shown) / 2;
            graphics.fill(start - 1, textY - 1, start + selected + 1, textY + 9, Theme.SURFACE_SELECTED);
        }
        graphics.centeredText(font, shown, valueX + valueWidth / 2, textY,
                this.active ? Theme.TEXT_BRIGHT : Theme.TEXT_DISABLED);
        if (isFocused()) {
            graphics.outline(x - 1, y - 1, width + 2, height + 2, Theme.FOCUS);
        }
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput output) {
        output.add(NarratedElementType.TITLE, Component.literal(getMessage().getString() + ": "
                + (this.editing ? this.buffer : this.formatter.apply(this.getter.getAsInt()))));
    }
}
