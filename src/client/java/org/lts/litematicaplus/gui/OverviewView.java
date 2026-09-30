package org.lts.litematicaplus.gui;

import java.util.Locale;
import java.util.function.Consumer;
import java.util.function.Predicate;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import org.lts.litematicaplus.LitematicaPlusConfig;
import org.lts.litematicaplus.engine.GhostEngine;
import org.lts.litematicaplus.engine.PlacementRuntime;
import org.lts.litematicaplus.placement.PlacementManager;
import org.lts.litematicaplus.placement.SchematicPlacement;
import org.lts.litematicaplus.tool.ToolController;
import org.lts.litematicaplus.verify.MismatchType;
import org.lts.litematicaplus.world.LayerRange;
import org.lts.litematicaplus.world.PlacementVolume;

final class OverviewView extends TabView {
    private static final int CARD_HEIGHT = 64;
    private int togglesTop;
    private int layerTop;
    private int maxToggles;
    private int toggleCount;

    OverviewView(HubScreen screen) {
        super(screen);
    }

    @Override
    Component summary() {
        int count = PlacementManager.get().placements().size();
        return tr("litematicaplus.overview.summary", count);
    }

    @Override
    void init() {
        SchematicPlacement placement = PlacementManager.get().selected();
        int buttonY = this.top + CARD_HEIGHT - 24;
        if (placement == null) {
            add(FlatButton.primary(this.left + 12, buttonY, 150, tr("litematicaplus.overview.browse"),
                    button -> this.screen.switchTab(HubScreen.Tab.SCHEMATICS)));
        } else {
            int right = this.left + this.width - 12;
            add(FlatButton.secondary(right - 96, this.top + 5, 96, tr("litematicaplus.overview.manage"),
                    button -> this.minecraft.gui.setScreen(new PlacementEditorScreen(this.screen, placement))));
        }
        this.togglesTop = this.top + CARD_HEIGHT + 18;
        this.toggleCount = 0;
        int columns = this.width >= 400 ? 3 : this.width >= 200 ? 2 : 1;
        int columnWidth = (this.width - (columns - 1) * Theme.GAP) / columns;
        int rowStep = Theme.CONTROL_HEIGHT + 4;
        this.maxToggles = Math.max(1, (this.top + this.height - this.togglesTop - 36 - Theme.CONTROL_HEIGHT) / rowStep) * columns;
        int index = 0;
        index = toggle(index, columns, columnWidth, "litematicaplus.option.render_enabled", c -> c.renderEnabled,
                c -> c.renderEnabled = !c.renderEnabled);
        index = toggle(index, columns, columnWidth, "litematicaplus.option.render_ghost", c -> c.renderGhostBlocks,
                c -> c.renderGhostBlocks = !c.renderGhostBlocks);
        index = toggle(index, columns, columnWidth, "litematicaplus.option.render_overlay", c -> c.renderOverlay,
                c -> c.renderOverlay = !c.renderOverlay);
        index = toggle(index, columns, columnWidth, "litematicaplus.option.overlay_xray", c -> c.overlayThroughWalls,
                c -> c.overlayThroughWalls = !c.overlayThroughWalls);
        index = toggle(index, columns, columnWidth, "litematicaplus.option.easy_place", c -> c.easyPlaceEnabled,
                c -> c.easyPlaceEnabled = !c.easyPlaceEnabled);
        index = toggle(index, columns, columnWidth, "litematicaplus.option.placement_restriction",
                c -> c.placementRestriction, c -> c.placementRestriction = !c.placementRestriction);
        int rows = (index + columns - 1) / columns;
        this.layerTop = this.togglesTop + rows * rowStep + 14;
        int third = (this.width - Theme.GAP * 2) / 3;
        add(FlatButton.secondary(this.left, this.layerTop, third, layerModeLabel(), button -> {
            ToolController.get().cycleLayerMode(this.minecraft);
            this.screen.refresh();
        }));
        add(FlatButton.secondary(this.left + third + Theme.GAP, this.layerTop, third, layerAxisLabel(), button -> {
            LayerRange layer = GhostEngine.get().layer();
            Direction.Axis[] axes = Direction.Axis.values();
            Direction.Axis next = axes[(layer.axis().ordinal() + 1) % axes.length];
            int value = this.minecraft.player == null ? layer.single() : this.minecraft.player.blockPosition().get(next);
            ToolController.get().applyLayer(new LayerRange(layer.mode(), next, value, value - 2, value + 2));
            this.screen.refresh();
        }));
        Stepper stepper = new Stepper(this.left + (third + Theme.GAP) * 2, this.layerTop,
                this.width - (third + Theme.GAP) * 2, tr("litematicaplus.layer.position"),
                () -> GhostEngine.get().layer().single(),
                value -> {
                    LayerRange layer = GhostEngine.get().layer();
                    ToolController.get().applyLayer(layer.shifted(value - layer.single()));
                }, -30000000, 30000000, 1).compact();
        stepper.active = !GhostEngine.get().layer().isAll();
        add(stepper);
    }

    private int toggle(int index, int columns, int columnWidth, String key,
            Predicate<LitematicaPlusConfig> getter, Consumer<LitematicaPlusConfig> flip) {
        if (index >= this.maxToggles) {
            return index;
        }
        this.toggleCount = index + 1;
        int x = this.left + index % columns * (columnWidth + Theme.GAP);
        int y = this.togglesTop + index / columns * (Theme.CONTROL_HEIGHT + 4);
        add(FlatButton.toggle(x, y, columnWidth, tr(key), () -> getter.test(LitematicaPlusConfig.current()), button -> {
            LitematicaPlusConfig config = LitematicaPlusConfig.currentCopy();
            flip.accept(config);
            LitematicaPlusConfig.save(config);
        }));
        return index + 1;
    }

    private static Component layerModeLabel() {
        LayerRange layer = GhostEngine.get().layer();
        return tr("litematicaplus.layer.mode_button",
                tr("litematicaplus.layer.mode." + layer.mode().name().toLowerCase(Locale.ROOT)));
    }

    private static Component layerAxisLabel() {
        return tr("litematicaplus.layer.axis_button",
                GhostEngine.get().layer().axis().getName().toUpperCase(Locale.ROOT));
    }

    @Override
    void extract(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        SchematicPlacement placement = PlacementManager.get().selected();
        Ui.raised(graphics, this.left, this.top, this.width, CARD_HEIGHT, Theme.ACCENT);
        int x = this.left + 12;
        if (placement == null) {
            Ui.heading(graphics, this.font, tr("litematicaplus.overview.empty_title"), x, this.top + 8, 1.5F,
                    Theme.TEXT_BRIGHT);
            Ui.wrapped(graphics, this.font, tr("litematicaplus.overview.empty_hint"), x, this.top + 25,
                    this.width - 180, 2, Theme.TEXT_DIM, mouseX, mouseY);
        } else {
            PlacementRuntime runtime = GhostEngine.get().runtime(placement.id());
            PlacementVolume volume = runtime == null ? null : runtime.volume();
            String name = this.font.plainSubstrByWidth(placement.name(), (int) ((this.width - 126) / 1.5F));
            Ui.heading(graphics, this.font, Component.literal(name), x, this.top + 8, 1.5F, Theme.TEXT_BRIGHT);
            if (placement.loadState() == SchematicPlacement.LoadState.FAILED) {
                Ui.wrapped(graphics, this.font, tr("litematicaplus.placement.failed", placement.error()), x,
                        this.top + 26, this.width - 24, 3, Theme.DANGER, mouseX, mouseY);
            } else if (volume == null) {
                graphics.text(this.font, tr(placement.enabled() ? "litematicaplus.hud.loading"
                        : "litematicaplus.hud.disabled"), x, this.top + 28, Theme.TEXT_DIM, false);
            } else {
                double progress = runtime.progress();
                Ui.progress(graphics, x, this.top + 26, this.width - 24, 6, progress,
                        progress >= 0.9999 ? Theme.SUCCESS : Theme.ACCENT);
                graphics.text(this.font, tr("litematicaplus.hud.progress",
                        String.format(Locale.ROOT, "%.1f", progress * 100.0), runtime.count(MismatchType.CORRECT),
                        volume.totalBlocks()), x, this.top + 35, Theme.TEXT, false);
                int chipX = x;
                int chipY = this.top + 47;
                chipX = chip(graphics, chipX, chipY, MismatchType.MISSING, runtime);
                chipX = chip(graphics, chipX, chipY, MismatchType.WRONG_BLOCK, runtime);
                chipX = chip(graphics, chipX, chipY, MismatchType.WRONG_STATE, runtime);
                chip(graphics, chipX, chipY, MismatchType.EXTRA, runtime);
            }
        }
        Ui.label(graphics, this.font, tr("litematicaplus.overview.quick_toggles"), this.left, this.togglesTop - 11);
        if (this.toggleCount < 6) {
            Ui.right(graphics, this.font, tr("litematicaplus.overview.more_toggles").getString(), this.left + this.width,
                    this.togglesTop - 11, Theme.TEXT_DIM);
        }
        Ui.label(graphics, this.font, tr("litematicaplus.overview.layers"), this.left, this.layerTop - 11);
        int footerTop = this.layerTop + Theme.CONTROL_HEIGHT + 6;
        int footerLines = Math.min(3, (this.top + this.height - footerTop) / 10);
        if (footerLines > 0) {
            Ui.wrapped(graphics, this.font, tr("litematicaplus.overview.footer", LitematicaPlusConfig.current().toolItem),
                    this.left, this.top + this.height - footerLines * 10, this.width, footerLines, Theme.TEXT_DIM,
                    mouseX, mouseY);
        }
    }

    private int chip(GuiGraphicsExtractor graphics, int x, int y, MismatchType type, PlacementRuntime runtime) {
        long count = runtime.count(type);
        Component text = tr("litematicaplus.mismatch." + type.key() + ".count", count);
        if (x + this.font.width(text) + 12 > this.left + this.width - 8) {
            return x;
        }
        return Ui.chip(graphics, this.font, x, y, text, count > 0L ? type.color() : Theme.TEXT_DIM,
                count > 0L ? type.textColor() : Theme.TEXT_DIM);
    }
}
