package org.lts.litematicaplus.gui;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;

public final class InkField extends EditBox {
    private static final int INSET = 6;

    private int outerX;
    private int outerY;
    private int outerWidth;
    private int outerHeight;

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
        graphics.fill(x, y, x + width, y + height, Theme.TRACK);
        graphics.outline(x, y, width, height, focused ? Theme.ACCENT : hovered ? Theme.BORDER_STRONG : Theme.BORDER);
        graphics.fill(x + INSET - 2, y + height - 4, x + width - INSET + 2, y + height - 3,
                focused ? Theme.alpha(Theme.ACCENT, 0x90) : Theme.alpha(Theme.DIVIDER, 0xC0));
        if (focused) {
            Blueprint.ticks(graphics, x, y, width, height, Theme.AMBER);
        }
        super.extractWidgetRenderState(graphics, mouseX, mouseY, delta);
    }
}
