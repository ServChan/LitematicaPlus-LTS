package org.lts.litematicaplus.gui;

import com.mojang.blaze3d.Blaze3D;
import java.nio.file.Path;
import java.util.List;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import org.lts.litematicaplus.LitematicaPlusClient;

public final class LitematicaConflictScreen extends Screen {
    private static final int MAX_WIDTH = 380;
    private static final int PADDING = 14;
    private static final int LINE = 10;

    private final Screen parent;
    private final Component body;
    private int panelX;
    private int panelY;
    private int panelWidth;
    private int panelHeight;
    private List<FormattedCharSequence> lines = List.of();

    public LitematicaConflictScreen(Screen parent, String litematicaVersion) {
        super(Component.translatable("litematicaplus.conflict.title"));
        this.parent = parent;
        this.body = Component.translatable("litematicaplus.conflict.body", litematicaVersion);
    }

    @Override
    protected void init() {
        this.panelWidth = Math.min(MAX_WIDTH, this.width - 32);
        int inner = this.panelWidth - PADDING * 2;
        this.lines = this.font.split(this.body, inner);
        int titleHeight = 30;
        this.panelHeight = PADDING + titleHeight + this.lines.size() * LINE + 14 + Theme.CONTROL_HEIGHT + PADDING;
        this.panelX = (this.width - this.panelWidth) / 2;
        this.panelY = Math.max(8, (this.height - this.panelHeight) / 2);
        int buttonsY = this.panelY + this.panelHeight - PADDING - Theme.CONTROL_HEIGHT;
        int half = (inner - Theme.GAP) / 2;
        int left = this.panelX + PADDING;
        addRenderableWidget(FlatButton.secondary(left, buttonsY, half,
                Component.translatable("litematicaplus.conflict.open_mods"), button -> openMods()));
        FlatButton proceed = addRenderableWidget(FlatButton.primary(left + half + Theme.GAP, buttonsY, inner - half - Theme.GAP,
                Component.translatable("litematicaplus.conflict.continue"), button -> onClose()));
        setInitialFocus(proceed);
    }

    private void openMods() {
        Path mods = FabricLoader.getInstance().getGameDir().resolve("mods");
        try {
            Blaze3D.openPath(mods);
        } catch (RuntimeException exception) {
            LitematicaPlusClient.LOGGER.warn("Could not open the mods folder {}", mods, exception);
        }
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        Ui.panel(graphics, this.panelX, this.panelY, this.panelWidth, this.panelHeight);
        graphics.fill(this.panelX, this.panelY, this.panelX + 3, this.panelY + this.panelHeight, Theme.WARNING);
        int x = this.panelX + PADDING;
        int y = this.panelY + PADDING;
        Component label = Component.translatable("litematicaplus.conflict.label");
        Ui.chip(graphics, this.font, x - 2, y - 3, label, Theme.WARNING);
        Ui.clipped(graphics, this.font, this.title.getString(), x, y + 14, this.panelWidth - PADDING * 2,
                Theme.TEXT_BRIGHT);
        y += 30;
        for (FormattedCharSequence line : this.lines) {
            graphics.text(this.font, line, x, y, Theme.TEXT, false);
            y += LINE;
        }
        super.extractRenderState(graphics, mouseX, mouseY, delta);
    }

    @Override
    public void onClose() {
        this.minecraft.gui.setScreen(this.parent);
    }
}
