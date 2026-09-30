package org.lts.litematicaplus.gui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Renderable;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.narration.NarratableEntry;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

abstract class TabView {
    protected final HubScreen screen;
    protected final Minecraft minecraft;
    protected final Font font;
    protected int left;
    protected int top;
    protected int width;
    protected int height;

    TabView(HubScreen screen) {
        this.screen = screen;
        this.minecraft = Minecraft.getInstance();
        this.font = this.minecraft.font;
    }

    final void bounds(int x, int y, int w, int h) {
        this.left = x;
        this.top = y;
        this.width = w;
        this.height = h;
    }

    abstract void init();

    abstract void extract(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta);

    Component summary() {
        return Component.empty();
    }

    void tick() {
    }

    void closed() {
    }

    boolean keyPressed(KeyEvent event) {
        return false;
    }

    boolean mouseClicked(MouseButtonEvent event) {
        return false;
    }

    protected <T extends GuiEventListener & Renderable & NarratableEntry> T add(T widget) {
        return this.screen.addWidgetPublic(widget);
    }

    protected EditBox field(int x, int y, int w, String value, Component hint, int maxLength) {
        EditBox box = new EditBox(this.font, x, y, w, Theme.CONTROL_HEIGHT, hint);
        box.setMaxLength(maxLength);
        box.setValue(value == null ? "" : value);
        box.setHint(hint);
        box.setTextColor(Theme.TEXT);
        return add(box);
    }

    protected static Component tr(String key, Object... args) {
        return Component.translatable(key, args);
    }
}
