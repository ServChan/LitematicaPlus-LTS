package org.lts.litematicaplus.gui;

import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.components.Tooltip;
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

    public FlatButton(int x, int y, int width, int height, Component message, Style style, Consumer<FlatButton> action) {
        super(x, y, width, height, message);
        this.style = style;
        this.action = action;
        int reserved = style == Style.TOGGLE ? 52 : style == Style.TAB ? 14 : 8;
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

    @Override
    public void onPress(InputWithModifiers input) {
        this.action.accept(this);
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
        int fill;
        int border;
        int text;
        switch (this.style) {
            case PRIMARY -> {
                fill = !this.active ? Theme.SURFACE : hovered ? Theme.ACCENT : Theme.ACCENT_DARK;
                border = !this.active ? Theme.BORDER : Theme.ACCENT;
                text = this.active ? Theme.ACCENT_TEXT : Theme.TEXT_DISABLED;
            }
            case DANGER -> {
                fill = !this.active ? Theme.SURFACE : hovered ? Theme.DANGER_DARK : Theme.SURFACE_RAISED;
                border = this.active ? Theme.DANGER : Theme.BORDER;
                text = this.active ? (hovered ? Theme.TEXT_BRIGHT : Theme.DANGER) : Theme.TEXT_DISABLED;
            }
            case TAB -> {
                fill = on ? Theme.SURFACE_SELECTED : hovered ? Theme.SURFACE_HOVER : Theme.SIDEBAR;
                border = 0;
                text = on ? Theme.TEXT_BRIGHT : hovered ? Theme.TEXT : Theme.TEXT_DIM;
            }
            default -> {
                boolean marked = on && this.style == Style.SECONDARY;
                fill = !this.active ? Theme.SURFACE : marked ? Theme.SURFACE_SELECTED
                        : hovered ? Theme.SURFACE_HOVER : Theme.SURFACE_RAISED;
                border = marked ? Theme.ACCENT : hovered ? Theme.BORDER_STRONG : Theme.BORDER;
                text = !this.active ? Theme.TEXT_DISABLED : marked ? Theme.TEXT_BRIGHT : Theme.TEXT;
            }
        }
        graphics.fill(x, y, x + width, y + height, fill);
        if (border != 0) {
            graphics.outline(x, y, width, height, border);
        }
        int textY = y + (height - 8) / 2;
        if (this.style == Style.TAB) {
            if (on) {
                graphics.fill(x, y, x + 2, y + height, Theme.ACCENT);
            }
            graphics.text(font, shorten(font, getMessage().getString(), width - 14), x + 10, textY, text, false);
        } else if (this.style == Style.TOGGLE) {
            Component badge = Component.translatable(on ? "litematicaplus.state.on" : "litematicaplus.state.off");
            int badgeWidth = font.width(badge) + 10;
            int badgeX = x + width - badgeWidth - 4;
            graphics.text(font, shorten(font, getMessage().getString(), width - badgeWidth - 16), x + 8, textY,
                    text, false);
            graphics.fill(badgeX, y + 4, badgeX + badgeWidth, y + height - 4, on ? 0xFF14452A : 0xFF4A1F1F);
            graphics.text(font, badge, badgeX + 5, textY, on ? Theme.SUCCESS : Theme.DANGER, false);
        } else {
            String label = shorten(font, getMessage().getString(), width - 8);
            graphics.text(font, label, x + (width - font.width(label)) / 2, textY, text, false);
        }
        if (isFocused()) {
            graphics.outline(x - 1, y - 1, width + 2, height + 2, Theme.FOCUS);
        }
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
        int reserved = this.style == Style.TOGGLE ? 52 : this.style == Style.TAB ? 14 : 8;
        setTooltip(Minecraft.getInstance().font.width(message) > getWidth() - reserved ? Tooltip.create(message) : null);
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput output) {
        defaultButtonNarrationText(output);
    }
}
