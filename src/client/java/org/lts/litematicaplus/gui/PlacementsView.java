package org.lts.litematicaplus.gui;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import org.lts.litematicaplus.engine.GhostEngine;
import org.lts.litematicaplus.engine.PlacementRuntime;
import org.lts.litematicaplus.placement.PlacementManager;
import org.lts.litematicaplus.placement.SchematicPlacement;
import org.lts.litematicaplus.schematic.Schematic;
import org.lts.litematicaplus.tool.PasteController;
import org.lts.litematicaplus.world.PlacementVolume;

final class PlacementsView extends TabView {
    private ListPanel<SchematicPlacement> list;
    private boolean removeArmed;
    private int detailX;
    private int detailWidth;
    private int regionsTop;
    private int knownCount = -1;
    private int listScroll;

    PlacementsView(HubScreen screen) {
        super(screen);
    }

    @Override
    Component summary() {
        return tr("litematicaplus.placements.summary", PlacementManager.get().placements().size());
    }

    @Override
    void init() {
        PlacementManager manager = PlacementManager.get();
        List<SchematicPlacement> placements = new ArrayList<>(manager.placements());
        this.knownCount = placements.size();
        if (placements.isEmpty()) {
            add(FlatButton.primary(this.left + (this.width - 160) / 2, this.top + this.height / 2 + 30, 160,
                    tr("litematicaplus.overview.browse"), button -> this.screen.switchTab(HubScreen.Tab.SCHEMATICS)));
            return;
        }
        int listWidth = Math.max(110, this.width * 40 / 100);
        this.detailX = this.left + listWidth + Theme.GAP;
        this.detailWidth = this.width - listWidth - Theme.GAP;
        this.list = add(new ListPanel<SchematicPlacement>(this.left, this.top, listWidth, this.height, Theme.ROW_HEIGHT,
                tr("litematicaplus.tab.placements"), this::renderRow)
                .onSelect(placement -> {
                    manager.select(placement);
                    this.removeArmed = false;
                    this.screen.refresh();
                }));
        this.list.setItems(placements);
        SchematicPlacement selected = manager.selected();
        this.list.selectItem(selected);
        this.list.setScroll(this.listScroll);
        if (selected == null) {
            return;
        }
        int x = this.detailX;
        int y = this.top;
        EditBox name = field(x, y, this.detailWidth, selected.name(), tr("litematicaplus.placements.name"), 96);
        name.setResponder(value -> manager.rename(selected, value));
        y += Theme.CONTROL_HEIGHT + 4;
        boolean wide = this.detailWidth >= 264;
        int stepperWidth = wide ? (this.detailWidth - Theme.GAP * 2) / 3 : this.detailWidth;
        for (int axis = 0; axis < 3; axis++) {
            int index = axis;
            Stepper stepper = new Stepper(wide ? x + axis * (stepperWidth + Theme.GAP) : x, y, stepperWidth,
                    Component.literal(axis == 0 ? "X" : axis == 1 ? "Y" : "Z"),
                    () -> index == 0 ? selected.x() : index == 1 ? selected.y() : selected.z(),
                    value -> manager.move(selected, index == 0 ? value : selected.x(), index == 1 ? value : selected.y(),
                            index == 2 ? value : selected.z()), -30000000, 30000000, 1);
            stepper.active = !selected.locked();
            add(stepper);
            if (!wide) {
                y += Theme.CONTROL_HEIGHT + 2;
            }
        }
        if (wide) {
            y += Theme.CONTROL_HEIGHT + 2;
        }
        y += 2;
        int third = (this.detailWidth - Theme.GAP * 2) / 3;
        FlatButton here = add(FlatButton.secondary(x, y, third, tr("litematicaplus.placements.move_here"), button -> {
            if (this.minecraft.player != null) {
                BlockPos pos = this.minecraft.player.blockPosition();
                manager.move(selected, pos.getX(), pos.getY(), pos.getZ());
            }
        }));
        here.active = !selected.locked() && this.minecraft.player != null;
        FlatButton rotate = add(FlatButton.secondary(x + third + Theme.GAP, y, third,
                tr("litematicaplus.rotation." + selected.rotation().name().toLowerCase(Locale.ROOT)), button -> {
                    manager.rotate(selected, selected.rotation().getRotated(Rotation.CLOCKWISE_90));
                    this.screen.refresh();
                }));
        rotate.active = !selected.locked();
        FlatButton mirror = add(FlatButton.secondary(x + (third + Theme.GAP) * 2, y,
                this.detailWidth - (third + Theme.GAP) * 2,
                tr("litematicaplus.mirror." + selected.mirror().name().toLowerCase(Locale.ROOT)), button -> {
                    Mirror[] values = Mirror.values();
                    manager.mirror(selected, values[(selected.mirror().ordinal() + 1) % values.length]);
                    this.screen.refresh();
                }));
        mirror.active = !selected.locked();
        y += Theme.CONTROL_HEIGHT + 4;
        int half = (this.detailWidth - Theme.GAP) / 2;
        add(FlatButton.toggle(x, y, half, tr("litematicaplus.placements.enabled"), selected::enabled,
                button -> manager.setEnabled(selected, !selected.enabled())));
        add(FlatButton.toggle(x + half + Theme.GAP, y, this.detailWidth - half - Theme.GAP,
                tr("litematicaplus.placements.locked"), selected::locked, button -> {
                    manager.setLocked(selected, !selected.locked());
                    this.screen.refresh();
                }));
        y += Theme.CONTROL_HEIGHT + 4;
        int bottomY = this.top + this.height - Theme.CONTROL_HEIGHT;
        this.regionsTop = 0;
        Schematic schematic = selected.schematic();
        if (schematic != null && schematic.regions().size() > 1 && bottomY - y >= 60) {
            this.regionsTop = y + 12;
            ListPanel<String> regions = add(new ListPanel<String>(x, this.regionsTop, this.detailWidth,
                    bottomY - this.regionsTop - 4, 16, tr("litematicaplus.placements.regions"),
                    (graphics, rowFont, region, rowX, rowY, rowWidth, rowHeight, isSelected, hovered) -> {
                        boolean on = !selected.disabledRegions().contains(region);
                        Ui.clipped(graphics, rowFont, region, rowX + 6, rowY + 4, rowWidth - 44,
                                on ? Theme.TEXT : Theme.TEXT_DISABLED);
                        Ui.right(graphics, rowFont, tr(on ? "litematicaplus.state.on" : "litematicaplus.state.off").getString(),
                                rowX + rowWidth - 6, rowY + 4, on ? Theme.SUCCESS : Theme.DANGER);
                    })
                    .onSelect(region -> manager.toggleRegion(selected, region)));
            regions.setItems(new ArrayList<>(schematic.regions().keySet()));
            regions.active = !selected.locked();
        }
        FlatButton paste = add(FlatButton.primary(x, bottomY, third, tr("litematicaplus.placements.paste"), button -> {
            PasteController.startPaste(this.minecraft, GhostEngine.get().runtime(selected.id()));
            this.minecraft.gui.setScreen(null);
        }));
        paste.active = this.minecraft.player != null && this.minecraft.player.hasInfiniteMaterials()
                && !PasteController.active();
        add(FlatButton.secondary(x + third + Theme.GAP, bottomY, third, tr("litematicaplus.placements.reload"),
                button -> manager.reload(selected)));
        add(FlatButton.danger(x + (third + Theme.GAP) * 2, bottomY, this.detailWidth - (third + Theme.GAP) * 2,
                tr(this.removeArmed ? "litematicaplus.placements.remove_confirm" : "litematicaplus.placements.remove"),
                button -> {
                    if (this.removeArmed) {
                        this.removeArmed = false;
                        manager.remove(selected);
                    } else {
                        this.removeArmed = true;
                    }
                    this.screen.refresh();
                }));
    }

    @Override
    void tick() {
        if (this.list != null) {
            this.listScroll = this.list.scroll();
        }
        if (PlacementManager.get().placements().size() != this.knownCount) {
            this.screen.refresh();
        }
    }

    private void renderRow(GuiGraphicsExtractor graphics, Font rowFont, SchematicPlacement placement, int x, int y,
            int rowWidth, int rowHeight, boolean isSelected, boolean hovered) {
        int textY = y + (rowHeight - 8) / 2;
        String status;
        int color;
        PlacementRuntime runtime = GhostEngine.get().runtime(placement.id());
        if (placement.loadState() == SchematicPlacement.LoadState.FAILED) {
            status = tr("litematicaplus.placements.state_failed").getString();
            color = Theme.DANGER;
        } else if (!placement.enabled()) {
            status = tr("litematicaplus.state.off").getString();
            color = Theme.TEXT_DISABLED;
        } else if (runtime == null || runtime.volume() == null) {
            status = tr("litematicaplus.placements.state_loading").getString();
            color = Theme.WARNING;
        } else {
            status = String.format(Locale.ROOT, "%.0f%%", runtime.progress() * 100.0);
            color = runtime.progress() >= 0.9999 ? Theme.SUCCESS : Theme.ACCENT;
        }
        int statusWidth = rowFont.width(status);
        Ui.clipped(graphics, rowFont, placement.name(), x + 8, textY, rowWidth - statusWidth - 22,
                placement.enabled() ? Theme.TEXT_BRIGHT : Theme.TEXT_DIM);
        Ui.right(graphics, rowFont, status, x + rowWidth - 6, textY, color);
    }

    @Override
    void extract(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        PlacementManager manager = PlacementManager.get();
        if (manager.placements().isEmpty()) {
            Ui.emptyState(graphics, this.font, this.left, this.top, this.width, this.height,
                    tr("litematicaplus.placements.empty_title"), tr("litematicaplus.placements.empty_hint"));
            return;
        }
        SchematicPlacement selected = manager.selected();
        if (selected == null) {
            Ui.emptyState(graphics, this.font, this.detailX, this.top, this.detailWidth, this.height,
                    tr("litematicaplus.placements.none_selected"), tr("litematicaplus.placements.none_selected_hint"));
            return;
        }
        if (this.regionsTop > 0) {
            PlacementRuntime runtime = GhostEngine.get().runtime(selected.id());
            PlacementVolume volume = runtime == null ? null : runtime.volume();
            Component label = volume == null ? tr("litematicaplus.placements.regions")
                    : tr("litematicaplus.placements.regions_blocks", volume.totalBlocks());
            Ui.label(graphics, this.font, label, this.detailX, this.regionsTop - 11);
        }
        if (selected.loadState() == SchematicPlacement.LoadState.FAILED && this.regionsTop == 0) {
            int y = this.top + this.height - Theme.CONTROL_HEIGHT - 26;
            graphics.enableScissor(this.detailX, y, this.detailX + this.detailWidth, y + 22);
            graphics.textWithWordWrap(this.font, tr("litematicaplus.placement.failed", selected.error()), this.detailX, y,
                    this.detailWidth, Theme.DANGER, false);
            graphics.disableScissor();
        }
    }
}
