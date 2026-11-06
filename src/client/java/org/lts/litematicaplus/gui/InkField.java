package org.lts.litematicaplus.gui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

public final class InkField extends EditBox {
    private static final int INSET = 6;
    private static final Identifier FIELD = Identifier.withDefaultNamespace("widget/text_field");
    private static final Identifier FIELD_HIGHLIGHTED = Identifier.withDefaultNamespace("widget/text_field_highlighted");

    private int outerX;
    private int outerY;
    private int outerWidth;
    private int outerHeight;
    private Component fullHint;

    public InkField(Font font, int x, int y, int width, int height, Component narration) {
        super(font, x + INSET, y + (height - 8) / 2, Math.max(1, width - INSET * 2), 8, narration);
        this.outerX = x;
        this.outerY = y;
        this.outerWidth = width;
        this.outerHeight = height;
        setBordered(false);
    }

    @Override
    public void setX(int x) {
        this.outerX = x;
        super.setX(x + INSET);
    }

    @Override
    public void setY(int y) {
        this.outerY = y;
        super.setY(y + (this.outerHeight - 8) / 2);
    }

    @Override
    public void setWidth(int width) {
        this.outerWidth = width;
        super.setWidth(Math.max(1, width - INSET * 2));
        if (this.fullHint != null) {
            super.setHint(fitted(this.fullHint));
        }
    }

    @Override
    public void setHint(Component hint) {
        this.fullHint = hint;
        super.setHint(hint == null ? null : fitted(hint));
    }

    private Component fitted(Component hint) {
        Font font = Minecraft.getInstance().font;
        String text = hint.getString();
        int width = getWidth();
        if (font.width(text) <= width) {
            return hint;
        }
        return Component.literal(font.plainSubstrByWidth(text, Math.max(0, width - font.width(".."))) + "..")
                .withStyle(hint.getStyle());
    }

    @Override
    public boolean isMouseOver(double mouseX, double mouseY) {
        return this.visible && mouseX >= this.outerX && mouseX < this.outerX + this.outerWidth
                && mouseY >= this.outerY && mouseY < this.outerY + this.outerHeight;
    }

    @Override
    public void extractWidgetRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        if (!this.visible) {
            return;
        }
        boolean focused = isFocused();
        boolean hovered = isMouseOver(mouseX, mouseY);
        int x = this.outerX;
        int y = this.outerY;
        int width = this.outerWidth;
        int height = this.outerHeight;
        graphics.blitSprite(RenderPipelines.GUI_TEXTURED, focused || hovered ? FIELD_HIGHLIGHTED : FIELD, x, y, width, height);
        super.extractWidgetRenderState(graphics, mouseX, mouseY, delta);
    }
}
