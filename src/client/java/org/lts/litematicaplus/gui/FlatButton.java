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
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

public final class FlatButton extends AbstractButton {
    private static final Identifier BUTTON = Identifier.withDefaultNamespace("widget/button");
    private static final Identifier BUTTON_DISABLED = Identifier.withDefaultNamespace("widget/button_disabled");
    private static final Identifier BUTTON_HIGHLIGHTED = Identifier.withDefaultNamespace("widget/button_highlighted");
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
    private Component hint;
    private final TextMemo label = new TextMemo();
    private final TextMemo fitted = new TextMemo();

    public FlatButton(int x, int y, int width, int height, Component message, Style style, Consumer<FlatButton> action) {
        super(x, y, width, height, message);
        this.style = style;
        this.action = action;
        updateTooltip();
    }

    public FlatButton withHint(Component text) {
        this.hint = text;
        updateTooltip();
        return this;
    }

    private void updateTooltip() {
        Component message = getMessage();
        int reserved = this.style == Style.TOGGLE ? 40 : this.style == Style.TAB ? 34 : 8;
        boolean clipped = Minecraft.getInstance().font.width(message) > getWidth() - reserved;
        if (this.hint != null) {
            setTooltip(Tooltip.create(clipped ? message.copy().append("\n").append(this.hint) : this.hint));
        } else {
            setTooltip(clipped ? Tooltip.create(message) : null);
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
            case TAB -> {
                if (on) {
                    graphics.fill(x, y, x + width, y + height, this.active && isFocused() ? Theme.FOCUS : Theme.BORDER_STRONG);
                    graphics.fill(x + 1, y + 1, x + width - 1, y + height - 1, 0xFF000000);
                } else if (hovered) {
                    graphics.fill(x, y, x + width, y + height, Theme.SURFACE_HOVER);
                }
                int textX = x + 7;
                graphics.text(font, fit(font, x + width - textX - 6), textX, textY,
                        on || hovered ? Theme.TEXT_BRIGHT : Theme.TEXT, true);
            }
            case TOGGLE -> {
                int switchWidth = Math.min(44, Math.max(24, width - font.width(label()) - 14));
                int switchX = x + width - switchWidth;
                if (hovered) {
                    graphics.fill(x, y, switchX - 2, y + height, Theme.alpha(0xFFFFFF, 0x14));
                }
                graphics.text(font, fit(font, switchX - x - 10), x + 4, textY,
                        !this.active ? Theme.TEXT_DISABLED : Theme.TEXT_BRIGHT, true);
                graphics.blitSprite(RenderPipelines.GUI_TEXTURED, sprite(hovered), switchX, y, switchWidth, height);
                Component state = Component.translatable(on ? "options.on" : "options.off");
                graphics.text(font, state, switchX + (switchWidth - font.width(state)) / 2, textY,
                        this.active ? Theme.TEXT_BRIGHT : Theme.TEXT_DIM, true);
            }
            default -> {
                boolean marked = on && this.style == Style.SECONDARY;
                graphics.blitSprite(RenderPipelines.GUI_TEXTURED, sprite(hovered || marked), x, y, width, height);
                int color = !this.active ? Theme.TEXT_DIM
                        : this.style == Style.DANGER ? Theme.DANGER
                        : this.warning.getAsBoolean() ? Theme.WARNING : Theme.TEXT_BRIGHT;
                centered(graphics, font, x, width, textY, color);
                if (marked) {
                    String shown = fit(font, width - 8);
                    int lineX = x + (width - font.width(shown)) / 2;
                    graphics.fill(lineX, textY + 9, lineX + font.width(shown), textY + 10, color);
                }
            }
        }
    }

    private Identifier sprite(boolean highlighted) {
        return !this.active ? BUTTON_DISABLED : highlighted ? BUTTON_HIGHLIGHTED : BUTTON;
    }

    private void centered(GuiGraphicsExtractor graphics, Font font, int x, int width, int y, int color) {
        String shown = fit(font, width - 8);
        graphics.text(font, shown, x + (width - font.width(shown)) / 2, y, color, true);
    }

    private String label() {
        Component message = getMessage();
        return this.label.stale(message) ? this.label.set(message.getString()) : this.label.text();
    }

    private String fit(Font font, int width) {
        String text = label();
        return this.fitted.stale(text, width, 0L, 0L) ? this.fitted.set(shorten(font, text, width)) : this.fitted.text();
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
        if (this.style != null) {
            updateTooltip();
        }
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
