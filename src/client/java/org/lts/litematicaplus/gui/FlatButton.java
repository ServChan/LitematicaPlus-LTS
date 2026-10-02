package org.lts.litematicaplus.gui;

import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.narration.NarratedElementType;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.input.InputWithModifiers;
import net.minecraft.network.chat.Component;

public final class FlatButton extends AbstractButton {
    public enum Style {
        PRIMARY,
        SECONDARY,
        DANGER,
        TAB,
        TOGGLE
    }

    private final Consumer<FlatButton> action;
    private final Style style;
    private BooleanSupplier state = () -> false;
    private BooleanSupplier warning = () -> false;
    private boolean shiftHeld;
    private String index;

    public FlatButton(int x, int y, int width, int height, Component message, Style style, Consumer<FlatButton> action) {
        super(x, y, width, height, message);
        this.style = style;
        this.action = action;
        int reserved = style == Style.TOGGLE ? 40 : style == Style.TAB ? 34 : 8;
        if (Minecraft.getInstance().font.width(message) > width - reserved) {
            setTooltip(Tooltip.create(message));
        }
    }

    public static FlatButton primary(int x, int y, int width, Component message, Consumer<FlatButton> action) {
        return new FlatButton(x, y, width, Theme.CONTROL_HEIGHT, message, Style.PRIMARY, action);
    }

    public static FlatButton secondary(int x, int y, int width, Component message, Consumer<FlatButton> action) {
        return new FlatButton(x, y, width, Theme.CONTROL_HEIGHT, message, Style.SECONDARY, action);
    }

    public static FlatButton danger(int x, int y, int width, Component message, Consumer<FlatButton> action) {
        return new FlatButton(x, y, width, Theme.CONTROL_HEIGHT, message, Style.DANGER, action);
    }

    public static FlatButton toggle(int x, int y, int width, Component message, BooleanSupplier state,
            Consumer<FlatButton> action) {
        FlatButton button = new FlatButton(x, y, width, Theme.CONTROL_HEIGHT, message, Style.TOGGLE, action);
        button.state = state;
        return button;
    }

    public FlatButton withState(BooleanSupplier supplier) {
        this.state = supplier;
        return this;
    }

    public FlatButton withWarning(BooleanSupplier supplier) {
        this.warning = supplier;
        return this;
    }

    @Override
    public void onPress(InputWithModifiers input) {
        this.shiftHeld = input.hasShiftDown();
        this.action.accept(this);
    }

    public boolean shiftHeld() {
        return this.shiftHeld;
    }

    @Override
    protected void extractContents(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        Font font = Minecraft.getInstance().font;
        int x = getX();
        int y = getY();
        int width = getWidth();
        int height = getHeight();
        boolean hovered = this.active && isHoveredOrFocused();
        boolean on = this.state.getAsBoolean();
        int textY = y + (height - 8) / 2;
        switch (this.style) {
            case PRIMARY -> {
                graphics.fill(x, y, x + width, y + height, !this.active ? Theme.SURFACE
                        : hovered ? Theme.ACCENT : Theme.ACCENT_DARK);
                if (this.active) {
                    graphics.fill(x, y, x + width, y + 1, Theme.alpha(Theme.TEXT_BRIGHT, hovered ? 0xC0 : 0x70));
                    Blueprint.ticks(graphics, x - 2, y - 2, width + 4, height + 4, Theme.ACCENT);
                } else {
                    graphics.outline(x, y, width, height, Theme.DIVIDER);
                }
                centered(graphics, font, x, width, textY, this.active ? Theme.ACCENT_TEXT : Theme.TEXT_DISABLED);
            }
            case DANGER -> {
                graphics.fill(x, y, x + width, y + height, hovered ? Theme.DANGER_DARK : Theme.alpha(Theme.DANGER, 0x10));
                graphics.outline(x, y, width, height, this.active ? Theme.DANGER : Theme.DIVIDER);
                centered(graphics, font, x, width, textY, !this.active ? Theme.TEXT_DISABLED
                        : hovered ? Theme.TEXT_BRIGHT : Theme.DANGER);
            }
            case TAB -> {
                if (on) {
                    graphics.fill(x, y, x + width, y + height, Theme.SURFACE_SELECTED);
                    graphics.fill(x, y, x + 2, y + height, Theme.ACCENT);
                } else if (hovered) {
                    graphics.fill(x, y, x + width, y + height, Theme.SURFACE_HOVER);
                }
                int textX = x + 9;
                String title = getMessage().getString();
                if (this.index != null && font.width(this.index) + 5 + font.width(title) <= x + width - textX - 6) {
                    graphics.text(font, this.index, textX, textY, on ? Theme.AMBER : Theme.TEXT_DISABLED, false);
                    textX += font.width(this.index) + 5;
                }
                graphics.text(font, shorten(font, getMessage().getString(), x + width - textX - 6), textX, textY,
                        on ? Theme.TEXT_BRIGHT : hovered ? Theme.TEXT : Theme.TEXT_DIM, false);
            }
            case TOGGLE -> {
                graphics.fill(x, y, x + width, y + height, hovered ? Theme.SURFACE_HOVER : Theme.SURFACE);
                graphics.outline(x, y, width, height, !this.active ? Theme.DIVIDER : hovered ? Theme.ACCENT : Theme.BORDER);
                int switchX = x + width - 28;
                graphics.text(font, shorten(font, getMessage().getString(), switchX - x - 12), x + 8, textY,
                        !this.active ? Theme.TEXT_DISABLED : on ? Theme.TEXT_BRIGHT : Theme.TEXT, false);
                Blueprint.toggle(graphics, switchX, y + (height - 10) / 2, on, this.active);
            }
            default -> {
                boolean marked = on && this.style == Style.SECONDARY;
                graphics.fill(x, y, x + width, y + height, !this.active ? Theme.alpha(Theme.SURFACE, 0x60)
                        : marked ? Theme.SURFACE_SELECTED : hovered ? Theme.SURFACE_HOVER : Theme.SURFACE);
                graphics.outline(x, y, width, height, !this.active ? Theme.DIVIDER
                        : marked || hovered ? Theme.ACCENT : Theme.BORDER);
                if (marked) {
                    Blueprint.ticks(graphics, x, y, width, height, Theme.AMBER);
                }
                centered(graphics, font, x, width, textY, !this.active ? Theme.TEXT_DISABLED
                        : this.warning.getAsBoolean() ? Theme.WARNING : marked || hovered ? Theme.TEXT_BRIGHT : Theme.TEXT);
            }
        }
        if (isFocused()) {
            graphics.outline(x - 1, y - 1, width + 2, height + 2, Theme.FOCUS);
        }
    }

    private void centered(GuiGraphicsExtractor graphics, Font font, int x, int width, int y, int color) {
        String label = shorten(font, getMessage().getString(), width - 8);
        graphics.text(font, label, x + (width - font.width(label)) / 2, y, color, false);
    }

    public FlatButton withIndex(String value) {
        this.index = value;
        return this;
    }

    private static String shorten(Font font, String label, int width) {
        if (font.width(label) <= width) {
            return label;
        }
        return font.plainSubstrByWidth(label, Math.max(0, width - 6)) + "..";
    }

    @Override
    public void setMessage(Component message) {
        super.setMessage(message);
        int reserved = this.style == Style.TOGGLE ? 40 : this.style == Style.TAB ? 34 : 8;
        setTooltip(Minecraft.getInstance().font.width(message) > getWidth() - reserved ? Tooltip.create(message) : null);
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput output) {
        defaultButtonNarrationText(output);
        if (this.style == Style.TOGGLE) {
            output.add(NarratedElementType.HINT, Component.translatable(this.state.getAsBoolean()
                    ? "litematicaplus.state.on" : "litematicaplus.state.off"));
        }
    }
}
