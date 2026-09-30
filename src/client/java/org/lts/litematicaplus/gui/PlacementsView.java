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
    private static final int ARM_TICKS = 80;
    private static final int REGION_ROW_HEIGHT = 20;
    private static final int INLINE_REGIONS_MIN_HEIGHT = 56;

    private ListPanel<SchematicPlacement> list;
    private int removeArmed;
    private int pasteArmed;
    private boolean regionsPage;
    private int detailX;
    private int detailWidth;
    private int regionsTop;
    private int knownCount = -1;
    private SchematicPlacement.LoadState knownState;
    private Schematic knownSchematic;
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
        this.regionsTop = 0;
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
                .describe(SchematicPlacement::name)
                .onSelect(placement -> {
                    manager.select(placement);
                    this.removeArmed = 0;
                    this.pasteArmed = 0;
                    this.regionsPage = false;
                    this.screen.refresh();
                }));
        this.list.setItems(placements);
        SchematicPlacement selected = manager.selected();
        this.list.selectItem(selected);
        this.list.setScroll(this.listScroll);
        this.knownState = selected == null ? null : selected.loadState();
        this.knownSchematic = selected == null ? null : selected.schematic();
        if (selected == null) {
            return;
        }
        Schematic schematic = selected.schematic();
        boolean hasRegions = schematic != null && schematic.regions().size() > 1;
        int x = this.detailX;
        int bottomY = this.top + this.height - Theme.CONTROL_HEIGHT;
        if (this.regionsPage && hasRegions) {
            add(FlatButton.secondary(x, this.top, Math.min(90, this.detailWidth), tr("litematicaplus.placements.back"),
                    button -> {
                        this.regionsPage = false;
                        this.screen.refresh();
                    }));
            this.regionsTop = this.top + Theme.CONTROL_HEIGHT + 16;
            addRegions(manager, selected, schematic, x, this.regionsTop, this.top + this.height - this.regionsTop);
            return;
        }
        this.regionsPage = false;
        int y = this.top;
        EditBox name = field(x, y, this.detailWidth, selected.name(), tr("litematicaplus.placements.name"), 96);
        name.setResponder(value -> manager.rename(selected, value));
        y += Theme.CONTROL_HEIGHT + 4;
        boolean wide = this.detailWidth >= 264;
        int columns = wide ? 3 : 2;
        int stepperWidth = (this.detailWidth - Theme.GAP * (columns - 1)) / columns;
        for (int axis = 0; axis < 3; axis++) {
            int index = axis;
            Stepper stepper = new Stepper(x + axis % columns * (stepperWidth + Theme.GAP),
                    y + axis / columns * (Theme.CONTROL_HEIGHT + 2), stepperWidth,
                    Component.literal(axis == 0 ? "X" : axis == 1 ? "Y" : "Z"),
                    () -> index == 0 ? selected.x() : index == 1 ? selected.y() : selected.z(),
                    value -> manager.move(selected, index == 0 ? value : selected.x(), index == 1 ? value : selected.y(),
                            index == 2 ? value : selected.z()), -30000000, 30000000, 1);
            stepper.active = !selected.locked();
            add(stepper);
        }
        int third = (this.detailWidth - Theme.GAP * 2) / 3;
        int hereX = wide ? x : x + stepperWidth + Theme.GAP;
        int hereY = wide ? y + Theme.CONTROL_HEIGHT + 4 : y + Theme.CONTROL_HEIGHT + 2;
        int hereWidth = wide ? third : this.detailWidth - stepperWidth - Theme.GAP;
        FlatButton here = add(FlatButton.secondary(hereX, hereY, hereWidth, tr("litematicaplus.placements.move_here"),
                button -> {
                    if (this.minecraft.player != null) {
                        BlockPos pos = this.minecraft.player.blockPosition();
                        manager.move(selected, pos.getX(), pos.getY(), pos.getZ());
                    }
                }));
        here.active = !selected.locked() && this.minecraft.player != null;
        y += wide ? Theme.CONTROL_HEIGHT + 4 : (Theme.CONTROL_HEIGHT + 2) * 2 + 2;
        int turnX = wide ? x + third + Theme.GAP : x;
        int turnWidth = wide ? third : (this.detailWidth - Theme.GAP) / 2;
        FlatButton rotate = add(FlatButton.secondary(turnX, y, turnWidth,
                tr("litematicaplus.rotation." + selected.rotation().name().toLowerCase(Locale.ROOT)), button -> {
                    manager.rotate(selected, selected.rotation().getRotated(Rotation.CLOCKWISE_90));
                    this.screen.refresh();
                }));
        rotate.active = !selected.locked();
        int mirrorX = turnX + turnWidth + Theme.GAP;
        FlatButton mirror = add(FlatButton.secondary(mirrorX, y, x + this.detailWidth - mirrorX,
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
        if (hasRegions) {
            if (bottomY - y - 16 >= INLINE_REGIONS_MIN_HEIGHT) {
                this.regionsTop = y + 12;
                addRegions(manager, selected, schematic, x, this.regionsTop, bottomY - this.regionsTop - 4);
            } else if (bottomY - y >= Theme.CONTROL_HEIGHT + 2) {
                add(FlatButton.secondary(x, y, this.detailWidth,
                        tr("litematicaplus.placements.regions_button", schematic.regions().size()), button -> {
                            this.regionsPage = true;
                            this.screen.refresh();
                        }));
            } else {
                name.setWidth(this.detailWidth - 96 - Theme.GAP);
                add(FlatButton.secondary(x + this.detailWidth - 96, this.top, 96,
                        tr("litematicaplus.placements.regions_button", schematic.regions().size()), button -> {
                            this.regionsPage = true;
                            this.screen.refresh();
                        }));
            }
        }
        FlatButton paste = add(FlatButton.primary(x, bottomY, third,
                tr(this.pasteArmed > 0 ? "litematicaplus.placements.paste_confirm" : "litematicaplus.placements.paste"),
                button -> {
                    if (this.pasteArmed > 0) {
                        this.pasteArmed = 0;
                        PasteController.startPaste(this.minecraft, GhostEngine.get().runtime(selected.id()));
                        this.minecraft.gui.setScreen(null);
                    } else {
                        this.pasteArmed = ARM_TICKS;
                        this.removeArmed = 0;
                        this.screen.refresh();
                    }
                }));
        PlacementRuntime runtime = GhostEngine.get().runtime(selected.id());
        paste.active = this.minecraft.player != null && this.minecraft.player.hasInfiniteMaterials()
                && !PasteController.active() && runtime != null && runtime.volume() != null;
        add(FlatButton.secondary(x + third + Theme.GAP, bottomY, third, tr("litematicaplus.placements.reload"),
                button -> manager.reload(selected)));
        add(FlatButton.danger(x + (third + Theme.GAP) * 2, bottomY, this.detailWidth - (third + Theme.GAP) * 2,
                tr(this.removeArmed > 0 ? "litematicaplus.placements.remove_confirm" : "litematicaplus.placements.remove"),
                button -> {
                    if (this.removeArmed > 0) {
                        this.removeArmed = 0;
                        manager.remove(selected);
                    } else {
                        this.removeArmed = ARM_TICKS;
                        this.pasteArmed = 0;
                    }
                    this.screen.refresh();
                }));
    }

    private void addRegions(PlacementManager manager, SchematicPlacement selected, Schematic schematic, int x, int y,
            int panelHeight) {
        ListPanel<String> regions = add(new ListPanel<String>(x, y, this.detailWidth, panelHeight, REGION_ROW_HEIGHT,
                tr("litematicaplus.placements.regions"),
                (graphics, rowFont, region, rowX, rowY, rowWidth, rowHeight, isSelected, hovered) -> {
                    boolean on = !selected.disabledRegions().contains(region);
                    int textY = rowY + (rowHeight - 8) / 2;
                    Ui.clipped(graphics, rowFont, region, rowX + 6, textY, rowWidth - 44, on ? Theme.TEXT : Theme.TEXT_DIM);
                    Ui.right(graphics, rowFont, tr(on ? "litematicaplus.state.on" : "litematicaplus.state.off").getString(),
                            rowX + rowWidth - 6, textY, on ? Theme.SUCCESS : Theme.DANGER);
                })
                .describe(region -> region)
                .onPress(region -> manager.toggleRegion(selected, region)));
        regions.setItems(new ArrayList<>(schematic.regions().keySet()));
        regions.active = !selected.locked();
    }

    @Override
    void tick() {
        if (this.list != null) {
            this.listScroll = this.list.scroll();
        }
        boolean expired = false;
        if (this.removeArmed > 0 && --this.removeArmed == 0) {
            expired = true;
        }
        if (this.pasteArmed > 0 && --this.pasteArmed == 0) {
            expired = true;
        }
        SchematicPlacement selected = PlacementManager.get().selected();
        boolean stateChanged = selected != null
                && (selected.loadState() != this.knownState || selected.schematic() != this.knownSchematic);
        if (expired || stateChanged || PlacementManager.get().placements().size() != this.knownCount) {
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
            color = Theme.TEXT_DIM;
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
            int y = this.top + this.height - Theme.CONTROL_HEIGHT - 24;
            Ui.wrapped(graphics, this.font, tr("litematicaplus.placement.failed", selected.error()), this.detailX, y,
                    this.detailWidth, 2, Theme.DANGER, mouseX, mouseY);
        }
    }
}
