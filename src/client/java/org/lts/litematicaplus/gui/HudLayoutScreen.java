package org.lts.litematicaplus.gui;

import com.mojang.blaze3d.platform.InputConstants;
import java.util.function.BiConsumer;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import org.lts.litematicaplus.hud.InfoHud;

public final class HudLayoutScreen extends Screen {
    private static final int PLACEHOLDER_HEIGHT = 52;
    private static final int SNAP = 6;
    private static final int TOOLBAR_WIDTH = 236;

    private final Screen parent;
    private final BiConsumer<Float, Float> apply;
    private float fractionX;
    private float fractionY;
    private int panelHeight = PLACEHOLDER_HEIGHT;
    private boolean dragging;
    private double grabX;
    private double grabY;
    private int toolbarTop;

    public HudLayoutScreen(Screen parent, float fractionX, float fractionY, BiConsumer<Float, Float> apply) {
        super(Component.translatable("litematicaplus.hud_layout.title"));
        this.parent = parent;
        this.apply = apply;
        this.fractionX = fractionX;
        this.fractionY = fractionY;
    }

    @Override
    protected void init() {
        int left = (this.width - TOOLBAR_WIDTH) / 2;
        this.toolbarTop = this.height / 2 - 30;
        int half = (TOOLBAR_WIDTH - Theme.GAP) / 2;
        int y = this.toolbarTop + 24;
        corner(left, y, half, "top_left", 0.0F, 0.0F);
        corner(left + half + Theme.GAP, y, TOOLBAR_WIDTH - half - Theme.GAP, "top_right", 1.0F, 0.0F);
        y += Theme.CONTROL_HEIGHT + 4;
        corner(left, y, half, "bottom_left", 0.0F, 1.0F);
        corner(left + half + Theme.GAP, y, TOOLBAR_WIDTH - half - Theme.GAP, "bottom_right", 1.0F, 1.0F);
        y += Theme.CONTROL_HEIGHT + 4;
        addRenderableWidget(FlatButton.primary(left, y, TOOLBAR_WIDTH, Component.translatable("litematicaplus.button.done"),
                button -> onClose()));
    }

    private void corner(int x, int y, int width, String key, float fractionX, float fractionY) {
        addRenderableWidget(FlatButton.secondary(x, y, width, Component.translatable("litematicaplus.hud_layout." + key),
                button -> {
                    this.fractionX = fractionX;
                    this.fractionY = fractionY;
                }).withState(() -> this.fractionX == fractionX && this.fractionY == fractionY));
    }

    private int panelLeft() {
        return InfoHud.left(this.width, this.fractionX);
    }

    private int panelTop() {
        return InfoHud.top(this.height, this.panelHeight, this.fractionY);
    }

    private boolean overPanel(double mouseX, double mouseY) {
        int x = panelLeft();
        int y = panelTop();
        return mouseX >= x && mouseX < x + InfoHud.WIDTH && mouseY >= y && mouseY < y + this.panelHeight;
    }

    private static float fraction(double position, int room) {
        if (room <= 0) {
            return 0.0F;
        }
        double value = Math.max(0.0, Math.min(1.0, position / room));
        double pixels = value * room;
        if (pixels < SNAP) {
            return 0.0F;
        }
        if (pixels > room - SNAP) {
            return 1.0F;
        }
        if (Math.abs(pixels - room / 2.0) < SNAP) {
            return 0.5F;
        }
        return (float) value;
    }

    private int roomX() {
        return Math.max(0, this.width - InfoHud.WIDTH - 12);
    }

    private int roomY() {
        return Math.max(0, this.height - this.panelHeight - 12);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (event.button() == InputConstants.MOUSE_BUTTON_LEFT && overPanel(event.x(), event.y())) {
            setFocused(null);
            this.dragging = true;
            this.grabX = event.x() - panelLeft();
            this.grabY = event.y() - panelTop();
            return true;
        }
        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double dx, double dy) {
        if (this.dragging) {
            this.fractionX = fraction(event.x() - this.grabX - 6, roomX());
            this.fractionY = fraction(event.y() - this.grabY - 6, roomY());
            return true;
        }
        return super.mouseDragged(event, dx, dy);
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        this.dragging = false;
        return super.mouseReleased(event);
    }

    private void nudge(int dx, int dy) {
        int roomX = roomX();
        int roomY = roomY();
        if (roomX > 0) {
            this.fractionX = (float) Math.max(0.0, Math.min(1.0, (Math.round(this.fractionX * roomX) + dx) / (double) roomX));
        }
        if (roomY > 0) {
            this.fractionY = (float) Math.max(0.0, Math.min(1.0, (Math.round(this.fractionY * roomY) + dy) / (double) roomY));
        }
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        int step = event.hasShiftDown() ? 10 : 1;
        switch (event.key()) {
            case InputConstants.KEY_LEFT -> nudge(-step, 0);
            case InputConstants.KEY_RIGHT -> nudge(step, 0);
            case InputConstants.KEY_UP -> nudge(0, -step);
            case InputConstants.KEY_DOWN -> nudge(0, step);
            default -> {
                return super.keyPressed(event);
            }
        }
        return true;
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        int left = (this.width - TOOLBAR_WIDTH) / 2;
        Ui.panel(graphics, left - 8, this.toolbarTop - 8, TOOLBAR_WIDTH + 16, 24 + (Theme.CONTROL_HEIGHT + 4) * 3 + 12);
        Ui.wrapped(graphics, this.font, Component.translatable("litematicaplus.hud_layout.hint"), left, this.toolbarTop,
                TOOLBAR_WIDTH, 2, Theme.TEXT, mouseX, mouseY);
        int drawn = InfoHud.renderAt(this.minecraft, graphics, this.fractionX, this.fractionY);
        if (drawn <= 0) {
            this.panelHeight = PLACEHOLDER_HEIGHT;
            int x = panelLeft();
            int y = panelTop();
            graphics.fill(x, y, x + InfoHud.WIDTH, y + PLACEHOLDER_HEIGHT, Theme.HUD_BACKGROUND);
            graphics.outline(x, y, InfoHud.WIDTH, PLACEHOLDER_HEIGHT, Theme.BORDER);
            Blueprint.marker(graphics, x + 8, y + 8, Theme.AMBER);
            graphics.text(this.font, Component.translatable("litematicaplus.hud.title"), x + 14, y + 6, Theme.TEXT_DIM, false);
            Ui.wrapped(graphics, this.font, Component.translatable("litematicaplus.hud_layout.placeholder"), x + 8, y + 20,
                    InfoHud.WIDTH - 16, 3, Theme.TEXT_DIM, mouseX, mouseY);
        } else {
            this.panelHeight = drawn;
        }
        int x = panelLeft();
        int y = panelTop();
        boolean hot = this.dragging || overPanel(mouseX, mouseY);
        graphics.outline(x - 1, y - 1, InfoHud.WIDTH + 2, this.panelHeight + 2, hot ? Theme.FOCUS : Theme.ACCENT_DARK);
        super.extractRenderState(graphics, mouseX, mouseY, delta);
    }

    @Override
    public void onClose() {
        this.apply.accept(this.fractionX, this.fractionY);
        this.minecraft.gui.setScreen(this.parent);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
