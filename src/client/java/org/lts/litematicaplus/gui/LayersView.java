package org.lts.litematicaplus.gui;

import java.util.Locale;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import org.lts.litematicaplus.LitematicaPlusConfig;
import org.lts.litematicaplus.Session;
import org.lts.litematicaplus.engine.GhostEngine;
import org.lts.litematicaplus.engine.PlacementRuntime;
import org.lts.litematicaplus.hud.InfoHud;
import org.lts.litematicaplus.placement.PlacementManager;
import org.lts.litematicaplus.placement.SchematicPlacement;
import org.lts.litematicaplus.tool.ToolController;
import org.lts.litematicaplus.world.LayerRange;
import org.lts.litematicaplus.world.PlacementVolume;

final class LayersView extends TabView {
    private static final int MIN = -30000000;
    private static final int MAX = 30000000;
    private static final int ROW = Theme.CONTROL_HEIGHT + 4;

    private int layerTop;
    private int clipTop;
    private int scopeTop;
    private int footerTop;

    LayersView(HubScreen screen) {
        super(screen);
    }

    @Override
    Component summary() {
        LayerRange layer = GhostEngine.get().layer();
        if (layer.unrestricted()) {
            return tr("litematicaplus.layers.summary_all");
        }
        return InfoHud.layerText(layer);
    }

    @Override
    void init() {
        LayerRange layer = GhostEngine.get().layer();
        int third = (this.width - Theme.GAP * 2) / 3;
        this.layerTop = this.top + 12;
        int y = this.layerTop;
        add(FlatButton.secondary(this.left, y, third, tr("litematicaplus.layer.mode_button",
                tr("litematicaplus.layer.mode." + layer.mode().name().toLowerCase(Locale.ROOT))), button -> {
                    ToolController.get().cycleLayerMode(this.minecraft);
                    this.screen.refresh();
                }));
        add(FlatButton.secondary(this.left + third + Theme.GAP, y, third,
                tr("litematicaplus.layer.axis_button", layer.axis().getName().toUpperCase(Locale.ROOT)), button -> {
                    LayerRange current = GhostEngine.get().layer();
                    Direction.Axis[] axes = Direction.Axis.values();
                    Direction.Axis next = axes[(current.axis().ordinal() + 1) % axes.length];
                    int value = this.minecraft.player == null ? current.single()
                            : this.minecraft.player.blockPosition().get(next);
                    ToolController.get().applyLayer(new LayerRange(current.mode(), next, value, value - 2, value + 2));
                    this.screen.refresh();
                }));
        FlatButton here = add(FlatButton.secondary(this.left + (third + Theme.GAP) * 2, y,
                this.width - (third + Theme.GAP) * 2, tr("litematicaplus.layers.layer_here"), button -> layerHere()));
        here.active = this.minecraft.player != null && !layer.isAll();
        y += ROW;
        if (layer.mode() == LayerRange.Mode.RANGE) {
            int half = (this.width - Theme.GAP) / 2;
            add(new Stepper(this.left, y, half, tr("litematicaplus.layers.from"), () -> GhostEngine.get().layer().min(),
                    value -> {
                        LayerRange current = GhostEngine.get().layer();
                        ToolController.get().applyLayer(current.withBounds(value, Math.max(value, current.max())));
                    }, MIN, MAX, 1));
            add(new Stepper(this.left + half + Theme.GAP, y, this.width - half - Theme.GAP, tr("litematicaplus.layers.to"),
                    () -> GhostEngine.get().layer().max(), value -> {
                        LayerRange current = GhostEngine.get().layer();
                        ToolController.get().applyLayer(current.withBounds(Math.min(value, current.min()), value));
                    }, MIN, MAX, 1));
        } else {
            Stepper position = new Stepper(this.left, y, this.width, tr("litematicaplus.layer.position"),
                    () -> GhostEngine.get().layer().single(), value -> {
                        LayerRange current = GhostEngine.get().layer();
                        ToolController.get().applyLayer(current.shifted(value - current.single()));
                    }, MIN, MAX, 1);
            position.active = !layer.isAll();
            add(position);
        }
        y += ROW + 16;
        this.clipTop = y;
        LayerRange.Clip clip = layer.clip();
        int usable = this.width - Theme.GAP * 2;
        int masterWidth = usable * 44 / 100;
        int fitWidth = usable * 32 / 100;
        int resetWidth = usable - masterWidth - fitWidth;
        add(FlatButton.toggle(this.left, y, masterWidth, tr("litematicaplus.layers.clip_enabled"),
                () -> GhostEngine.get().clip().enabled(), button -> {
                    LayerRange.Clip current = GhostEngine.get().clip();
                    LayerRange.Clip next = current.withEnabled(!current.enabled());
                    if (next.enabled() && !next.xEnabled() && !next.zEnabled()) {
                        next = seeded(next, true, true);
                    }
                    applyClip(next);
                }));
        FlatButton fit = add(FlatButton.secondary(this.left + masterWidth + Theme.GAP, y, fitWidth,
                tr("litematicaplus.layers.fit_placement"), button -> fitToPlacement()));
        fit.active = selectedVolume() != null;
        add(FlatButton.secondary(this.left + this.width - resetWidth, y, resetWidth, tr("litematicaplus.layers.reset"),
                button -> applyClip(LayerRange.Clip.NONE)));
        y += ROW;
        axisRow(y, true, clip);
        y += ROW;
        axisRow(y, false, clip);
        y += ROW + 16;
        this.scopeTop = y;
        add(FlatButton.toggle(this.left, y, this.width, tr("litematicaplus.option.count_visible_only"),
                () -> LitematicaPlusConfig.current().countVisibleOnly, button -> {
                    LitematicaPlusConfig config = LitematicaPlusConfig.currentCopy();
                    config.countVisibleOnly = !config.countVisibleOnly;
                    LitematicaPlusConfig.save(config);
                }));
        this.footerTop = y + ROW + 4;
    }

    private void axisRow(int y, boolean xAxis, LayerRange.Clip clip) {
        int toggleWidth = 52;
        int hereWidth = Math.min(64, Math.max(40, this.width / 9));
        int stepperWidth = (this.width - toggleWidth - hereWidth * 2 - Theme.GAP * 4) / 2;
        boolean on = xAxis ? clip.xEnabled() : clip.zEnabled();
        String name = xAxis ? "X" : "Z";
        add(FlatButton.toggle(this.left, y, toggleWidth, Component.literal(name),
                () -> xAxis ? GhostEngine.get().clip().xEnabled() : GhostEngine.get().clip().zEnabled(), button -> {
                    LayerRange.Clip current = GhostEngine.get().clip();
                    boolean enable = !(xAxis ? current.xEnabled() : current.zEnabled());
                    LayerRange.Clip next = enable ? seeded(current, xAxis, !xAxis)
                            : xAxis ? current.withX(false, current.minX(), current.maxX())
                                    : current.withZ(false, current.minZ(), current.maxZ());
                    if (enable && !next.enabled()) {
                        next = next.withEnabled(true);
                    }
                    applyClip(next);
                }));
        int x = this.left + toggleWidth + Theme.GAP;
        Stepper low = new Stepper(x, y, stepperWidth, tr("litematicaplus.layers.min", name),
                () -> xAxis ? GhostEngine.get().clip().minX() : GhostEngine.get().clip().minZ(),
                value -> setBound(xAxis, true, value), MIN, MAX, 1).compact();
        low.setTooltip(Tooltip.create(tr("litematicaplus.layers.min", name)));
        low.active = on;
        add(low);
        x += stepperWidth + Theme.GAP;
        FlatButton lowHere = add(FlatButton.secondary(x, y, hereWidth, tr("litematicaplus.layers.here"),
                button -> setBound(xAxis, true, playerCoordinate(xAxis))));
        lowHere.active = on && this.minecraft.player != null;
        x += hereWidth + Theme.GAP;
        Stepper high = new Stepper(x, y, stepperWidth, tr("litematicaplus.layers.max", name),
                () -> xAxis ? GhostEngine.get().clip().maxX() : GhostEngine.get().clip().maxZ(),
                value -> setBound(xAxis, false, value), MIN, MAX, 1).compact();
        high.setTooltip(Tooltip.create(tr("litematicaplus.layers.max", name)));
        high.active = on;
        add(high);
        x += stepperWidth + Theme.GAP;
        FlatButton highHere = add(FlatButton.secondary(x, y, this.left + this.width - x, tr("litematicaplus.layers.here"),
                button -> setBound(xAxis, false, playerCoordinate(xAxis))));
        highHere.active = on && this.minecraft.player != null;
    }

    private int playerCoordinate(boolean xAxis) {
        if (this.minecraft.player == null) {
            return 0;
        }
        BlockPos pos = this.minecraft.player.blockPosition();
        return xAxis ? pos.getX() : pos.getZ();
    }

    private void setBound(boolean xAxis, boolean low, int value) {
        LayerRange.Clip clip = GhostEngine.get().clip();
        if (xAxis) {
            int min = low ? value : Math.min(clip.minX(), value);
            int max = low ? Math.max(clip.maxX(), value) : value;
            applyClip(clip.withX(clip.xEnabled(), min, max));
        } else {
            int min = low ? value : Math.min(clip.minZ(), value);
            int max = low ? Math.max(clip.maxZ(), value) : value;
            applyClip(clip.withZ(clip.zEnabled(), min, max));
        }
    }

    private LayerRange.Clip seeded(LayerRange.Clip clip, boolean xAxis, boolean zAxis) {
        PlacementVolume volume = selectedVolume();
        BlockPos pos = this.minecraft.player == null ? BlockPos.ZERO : this.minecraft.player.blockPosition();
        LayerRange.Clip next = clip;
        if (xAxis) {
            boolean unset = clip.minX() == 0 && clip.maxX() == 0;
            int min = unset ? volume != null ? volume.minX() : pos.getX() - 8 : clip.minX();
            int max = unset ? volume != null ? volume.maxX() : pos.getX() + 8 : clip.maxX();
            next = next.withX(true, min, max);
        }
        if (zAxis) {
            boolean unset = clip.minZ() == 0 && clip.maxZ() == 0;
            int min = unset ? volume != null ? volume.minZ() : pos.getZ() - 8 : clip.minZ();
            int max = unset ? volume != null ? volume.maxZ() : pos.getZ() + 8 : clip.maxZ();
            next = next.withZ(true, min, max);
        }
        return next;
    }

    private void fitToPlacement() {
        PlacementVolume volume = selectedVolume();
        if (volume == null) {
            return;
        }
        applyClip(new LayerRange.Clip(true, true, volume.minX(), volume.maxX(), true, volume.minZ(), volume.maxZ()));
    }

    private static PlacementVolume selectedVolume() {
        SchematicPlacement placement = PlacementManager.get().selected();
        PlacementRuntime runtime = placement == null ? null : GhostEngine.get().runtime(placement.id());
        return runtime == null ? null : runtime.volume();
    }

    private void layerHere() {
        if (this.minecraft.player == null) {
            return;
        }
        LayerRange current = GhostEngine.get().layer();
        int value = this.minecraft.player.blockPosition().get(current.axis());
        ToolController.get().applyLayer(current.mode() == LayerRange.Mode.RANGE
                ? current.withBounds(value, value + Math.max(0, current.max() - current.min()))
                : current.shifted(value - current.single()));
        this.screen.refresh();
    }

    private void applyClip(LayerRange.Clip clip) {
        GhostEngine.get().setClip(clip);
        Session.markDirty();
        this.screen.refresh();
    }

    @Override
    void extract(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        Ui.section(graphics, this.font, tr("litematicaplus.overview.layers"), this.left, this.layerTop - 11, this.width);
        Ui.section(graphics, this.font, tr("litematicaplus.layers.clip_title"), this.left, this.clipTop - 11, this.width);
        Ui.section(graphics, this.font, tr("litematicaplus.layers.scope_title"), this.left, this.scopeTop - 11, this.width);
        int lines = Math.min(5, (this.top + this.height - this.footerTop) / 10);
        if (lines > 0) {
            Ui.wrapped(graphics, this.font, tr("litematicaplus.layers.hint"), this.left, this.footerTop, this.width, lines,
                    Theme.TEXT_DIM, mouseX, mouseY);
        }
    }
}
