package org.lts.litematicaplus.gui;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Locale;
import java.util.concurrent.RejectedExecutionException;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import org.lts.litematicaplus.LitematicaPlusClient;
import org.lts.litematicaplus.Session;
import org.lts.litematicaplus.compat.AxiomActions;
import org.lts.litematicaplus.compat.AxiomBridge;
import org.lts.litematicaplus.engine.GhostEngine;
import org.lts.litematicaplus.engine.PlacementRuntime;
import org.lts.litematicaplus.placement.PlacementGrid;
import org.lts.litematicaplus.placement.PlacementManager;
import org.lts.litematicaplus.placement.RegionPlacement;
import org.lts.litematicaplus.placement.SchematicPlacement;
import org.lts.litematicaplus.preview.PreviewService;
import org.lts.litematicaplus.schematic.EmbeddedPlacement;
import org.lts.litematicaplus.schematic.Schematic;
import org.lts.litematicaplus.schematic.SchematicIo;
import org.lts.litematicaplus.share.SyncmaticaClient;
import org.lts.litematicaplus.util.Notify;
import org.lts.litematicaplus.util.Workers;
import org.lts.litematicaplus.world.PlacementVolume;

final class PlacementsView extends TabView {
    private static final int ARM_TICKS = 80;
    private static final int REGION_ROW_HEIGHT = 20;
    private static final int INLINE_REGIONS_MIN_HEIGHT = 56;

    private ListPanel<SchematicPlacement> list;
    private int removeArmed;
    private boolean regionsPage;
    private boolean gridPage;
    private String regionPage;
    private int detailX;
    private int detailWidth;
    private int regionsTop;
    private int failTop;
    private int knownCount = -1;
    private SchematicPlacement.LoadState knownState;
    private boolean knownLocked;
    private Schematic knownSchematic;
    private int listScroll;
    private final Map<SchematicPlacement, TextMemo> statusText = new IdentityHashMap<>();
    private final TextMemo regionOn = new TextMemo();
    private final TextMemo regionOff = new TextMemo();
    private Component notice;
    private boolean embedding;
    private int noticeTicks;

    PlacementsView(HubScreen screen) {
        super(screen);
    }

    @Override
    Component summary() {
        if (this.notice != null) {
            return this.notice;
        }
        return tr("litematicaplus.placements.summary", PlacementManager.get().placements().size());
    }

    @Override
    void init() {
        PlacementManager manager = PlacementManager.get();
        List<SchematicPlacement> placements = new ArrayList<>(manager.placements());
        this.statusText.keySet().retainAll(placements);
        this.knownCount = placements.size();
        this.regionsTop = 0;
        this.failTop = 0;
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
                    this.regionsPage = false;
                    this.gridPage = false;
                    this.regionPage = null;
                    this.screen.refresh();
                }));
        Schematic previewed = manager.selected() == null ? null : manager.selected().schematic();
        int side = Math.min(listWidth, Math.min(this.height - Theme.ROW_HEIGHT * 3 - 6, this.height * 6 / 10));
        if (PreviewService.enabled() && previewed != null && side >= 64) {
            this.list.setHeight(this.height - side - 6);
            add(new PreviewWidget(this.left, this.top + this.height - side, listWidth, side,
                    PreviewService.Source.loaded(manager.selected().id().toString(), previewed),
                    manager.selected().name()));
        }
        this.list.setItems(placements);
        SchematicPlacement selected = manager.selected();
        this.list.selectItem(selected);
        this.list.setScroll(this.listScroll);
        this.knownState = selected == null ? null : selected.loadState();
        this.knownLocked = selected != null && selected.locked();
        this.knownSchematic = selected == null ? null : selected.schematic();
        if (selected == null) {
            return;
        }
        Schematic schematic = selected.schematic();
        boolean hasRegions = schematic != null && schematic.regions().size() > 1;
        int x = this.detailX;
        int bottomY = this.top + this.height - Theme.CONTROL_HEIGHT;
        if (this.regionPage != null && schematic != null && schematic.regions().containsKey(this.regionPage)) {
            addRegionEditor(manager, selected, this.regionPage, x);
            return;
        }
        this.regionPage = null;
        if (this.gridPage && schematic != null) {
            addGridEditor(manager, selected, x);
            return;
        }
        this.gridPage = false;
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
            add(hint(stepper, "litematicaplus.placements.coordinate_tip"));
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
        this.failTop = 0;
        if (selected.loadState() == SchematicPlacement.LoadState.FAILED) {
            this.failTop = y;
            y += 24;
        }
        if (schematic != null && bottomY - y >= (Theme.CONTROL_HEIGHT + 4) * 2) {
            add(FlatButton.secondary(x, bottomY - Theme.CONTROL_HEIGHT - 4, this.detailWidth, gridLabel(selected),
                    button -> {
                        this.gridPage = true;
                        this.screen.refresh();
                    }));
            bottomY -= Theme.CONTROL_HEIGHT + 4;
        }
        if (schematic != null && AxiomBridge.available() && bottomY - y >= (Theme.CONTROL_HEIGHT + 4) * 2) {
            int axiomY = bottomY - Theme.CONTROL_HEIGHT - 4;
            FlatButton send = add(FlatButton.secondary(x, axiomY, half, tr("litematicaplus.axiom.send"),
                    button -> notice(AxiomActions.sendPlacement(this.minecraft, selected))));
            send.setTooltip(Tooltip.create(tr("litematicaplus.axiom.send.tip")));
            send.active = !AxiomActions.busy();
            FlatButton select = add(FlatButton.secondary(x + half + Theme.GAP, axiomY, this.detailWidth - half - Theme.GAP,
                    tr("litematicaplus.axiom.select_placement"), button -> notice(AxiomActions.selectPlacement(selected))));
            select.setTooltip(Tooltip.create(tr("litematicaplus.axiom.select_placement.tip")));
            bottomY -= Theme.CONTROL_HEIGHT + 4;
        }
        if (schematic != null && bottomY - y >= (Theme.CONTROL_HEIGHT + 4) * 2) {
            Path file = PlacementManager.resolve(selected.file());
            boolean shared = selected.serverId() != null || file.startsWith(SyncmaticaClient.cacheDirectory());
            boolean supported = !shared && SchematicIo.canEmbedPlacement(file);
            FlatButton embed = add(FlatButton.secondary(x, bottomY - Theme.CONTROL_HEIGHT - 4, this.detailWidth,
                    tr("litematicaplus.placements.embed"), button -> embed(selected, button.shiftHeld())));
            embed.setTooltip(Tooltip.create(tr(shared ? "litematicaplus.placements.embed.shared" : supported
                    ? "litematicaplus.placements.embed.tip" : "litematicaplus.placements.embed.unsupported")));
            embed.active = supported && !this.embedding;
            bottomY -= Theme.CONTROL_HEIGHT + 4;
        }
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
        bottomY = this.top + this.height - Theme.CONTROL_HEIGHT;
        add(FlatButton.primary(x, bottomY, third, tr("litematicaplus.placements.edit"),
                button -> this.minecraft.gui.setScreen(new PlacementEditorScreen(this.screen, selected))));
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
                    boolean moved = !selected.regionPlacement(region).isIdentity();
                    String title = moved ? region + " *" : region;
                    Ui.clipped(graphics, rowFont, title, rowX + 6, textY, rowWidth - 44, on ? Theme.TEXT : Theme.TEXT_DIM);
                    TextMemo memo = on ? this.regionOn : this.regionOff;
                    String state = memo.stale(null) ? memo.set(tr(on ? "litematicaplus.state.on"
                            : "litematicaplus.state.off").getString()) : memo.text();
                    Ui.right(graphics, rowFont, state, rowX + rowWidth - 6, textY, on ? Theme.SUCCESS : Theme.DANGER);
                })
                .describe(region -> region)
                .onPress(region -> {
                    this.regionPage = region;
                    this.screen.refresh();
                }));
        regions.setItems(new ArrayList<>(schematic.regions().keySet()));
    }

    private Component gridLabel(SchematicPlacement placement) {
        PlacementGrid grid = placement.grid();
        if (!grid.active()) {
            return tr("litematicaplus.grid.button_off");
        }
        return tr("litematicaplus.grid.button_on", grid.negativeX() + grid.positiveX() + 1,
                grid.negativeY() + grid.positiveY() + 1, grid.negativeZ() + grid.positiveZ() + 1);
    }

    private int backRow(int x) {
        add(FlatButton.secondary(x, this.top, Math.min(90, this.detailWidth), tr("litematicaplus.placements.back"),
                button -> {
                    this.regionPage = null;
                    this.gridPage = false;
                    this.screen.refresh();
                }));
        return this.top + Theme.CONTROL_HEIGHT + 16;
    }

    private void addRegionEditor(PlacementManager manager, SchematicPlacement selected, String region, int x) {
        int y = backRow(x);
        boolean editable = !selected.locked();
        int half = (this.detailWidth - Theme.GAP) / 2;
        FlatButton enabled = add(FlatButton.toggle(x, y, this.detailWidth, tr("litematicaplus.region.enabled"),
                () -> !selected.disabledRegions().contains(region), button -> manager.toggleRegion(selected, region)));
        enabled.active = editable;
        y += Theme.CONTROL_HEIGHT + 16;
        boolean wide = this.detailWidth >= 264;
        int columns = wide ? 3 : 2;
        int stepperWidth = (this.detailWidth - Theme.GAP * (columns - 1)) / columns;
        for (int axis = 0; axis < 3; axis++) {
            int index = axis;
            Stepper stepper = new Stepper(x + axis % columns * (stepperWidth + Theme.GAP),
                    y + axis / columns * (Theme.CONTROL_HEIGHT + 2), stepperWidth,
                    Component.literal(axis == 0 ? "X" : axis == 1 ? "Y" : "Z"), () -> {
                        RegionPlacement value = selected.regionPlacement(region);
                        return index == 0 ? value.offsetX() : index == 1 ? value.offsetY() : value.offsetZ();
                    }, value -> {
                        RegionPlacement current = selected.regionPlacement(region);
                        manager.setRegionPlacement(selected, region, current.withOffset(
                                index == 0 ? value : current.offsetX(), index == 1 ? value : current.offsetY(),
                                index == 2 ? value : current.offsetZ()));
                    }, -30000, 30000, 1);
            stepper.active = editable;
            add(hint(stepper, "litematicaplus.region.offset_tip"));
        }
        y += (Theme.CONTROL_HEIGHT + 2) * (wide ? 1 : 2) + 4;
        RegionPlacement current = selected.regionPlacement(region);
        FlatButton rotate = add(FlatButton.secondary(x, y, half,
                tr("litematicaplus.rotation." + current.rotation().name().toLowerCase(Locale.ROOT)), button -> {
                    RegionPlacement value = selected.regionPlacement(region);
                    manager.setRegionPlacement(selected, region,
                            value.withRotation(value.rotation().getRotated(Rotation.CLOCKWISE_90)));
                    this.screen.refresh();
                }));
        rotate.active = editable;
        FlatButton mirror = add(FlatButton.secondary(x + half + Theme.GAP, y, this.detailWidth - half - Theme.GAP,
                tr("litematicaplus.mirror." + current.mirror().name().toLowerCase(Locale.ROOT)), button -> {
                    RegionPlacement value = selected.regionPlacement(region);
                    Mirror[] values = Mirror.values();
                    manager.setRegionPlacement(selected, region,
                            value.withMirror(values[(value.mirror().ordinal() + 1) % values.length]));
                    this.screen.refresh();
                }));
        mirror.active = editable;
        y += Theme.CONTROL_HEIGHT + 4;
        FlatButton reset = add(FlatButton.secondary(x, y, this.detailWidth, tr("litematicaplus.region.reset"),
                button -> {
                    manager.setRegionPlacement(selected, region, RegionPlacement.IDENTITY);
                    this.screen.refresh();
                }));
        reset.active = editable && !current.isIdentity();
    }

    private void addGridEditor(PlacementManager manager, SchematicPlacement selected, int x) {
        int y = backRow(x);
        boolean editable = !selected.locked();
        int third = (this.detailWidth - Theme.GAP * 2) / 3;
        String[] axes = {"X", "Y", "Z"};
        for (int axis = 0; axis < 3; axis++) {
            int index = axis;
            Stepper negative = new Stepper(x, y, third, Component.literal("-" + axes[axis]),
                    () -> selected.grid().repeat(index, false),
                    value -> manager.setGrid(selected, selected.grid().withRepeat(index, false, value)),
                    0, PlacementGrid.MAX_REPEAT, 1);
            Stepper positive = new Stepper(x + third + Theme.GAP, y, third, Component.literal("+" + axes[axis]),
                    () -> selected.grid().repeat(index, true),
                    value -> manager.setGrid(selected, selected.grid().withRepeat(index, true, value)),
                    0, PlacementGrid.MAX_REPEAT, 1);
            Stepper step = new Stepper(x + (third + Theme.GAP) * 2, y, this.detailWidth - (third + Theme.GAP) * 2,
                    tr("litematicaplus.grid.step", axes[axis]), () -> selected.grid().step(index),
                    value -> manager.setGrid(selected, selected.grid().withStep(index, value)),
                    0, PlacementGrid.MAX_STEP, 1).format(value -> value == 0
                            ? tr("litematicaplus.grid.auto").getString() : Integer.toString(value));
            negative.active = editable;
            positive.active = editable;
            step.active = editable;
            add(hint(negative, "litematicaplus.grid.repeat_negative_tip", axes[axis]));
            add(hint(positive, "litematicaplus.grid.repeat_positive_tip", axes[axis]));
            add(step);
            y += Theme.CONTROL_HEIGHT + 4;
        }
        FlatButton reset = add(FlatButton.secondary(x, y, this.detailWidth, tr("litematicaplus.grid.reset"),
                button -> {
                    manager.setGrid(selected, PlacementGrid.NONE);
                    this.screen.refresh();
                }));
        reset.active = editable && !selected.grid().equals(PlacementGrid.NONE);
    }

    @Override
    void tick() {
        if (this.noticeTicks > 0 && --this.noticeTicks == 0) {
            this.notice = null;
        }
        if (this.list != null) {
            this.listScroll = this.list.scroll();
        }
        boolean expired = false;
        if (this.removeArmed > 0 && --this.removeArmed == 0) {
            expired = true;
        }
        SchematicPlacement selected = PlacementManager.get().selected();
        boolean stateChanged = selected != null
                && (selected.loadState() != this.knownState || selected.schematic() != this.knownSchematic
                || selected.locked() != this.knownLocked);
        if (expired || stateChanged || PlacementManager.get().placements().size() != this.knownCount) {
            this.screen.refresh();
        }
    }

    private void renderRow(GuiGraphicsExtractor graphics, Font rowFont, SchematicPlacement placement, int x, int y,
            int rowWidth, int rowHeight, boolean isSelected, boolean hovered) {
        int textY = y + (rowHeight - 8) / 2;
        String key;
        int color;
        double progress = 0.0;
        PlacementRuntime runtime = GhostEngine.get().runtime(placement.id());
        if (placement.loadState() == SchematicPlacement.LoadState.FAILED) {
            key = "litematicaplus.placements.state_failed";
            color = Theme.DANGER;
        } else if (!placement.enabled()) {
            key = "litematicaplus.state.off";
            color = Theme.TEXT_DIM;
        } else if (runtime == null || runtime.volume() == null) {
            key = "litematicaplus.placements.state_loading";
            color = Theme.WARNING;
        } else {
            key = null;
            progress = runtime.progress();
            color = progress >= 0.9999 ? Theme.SUCCESS : Theme.ACCENT;
        }
        TextMemo memo = this.statusText.computeIfAbsent(placement, ignored -> new TextMemo());
        String status;
        if (!memo.stale(key, Double.doubleToLongBits(progress), 0L, 0L)) {
            status = memo.text();
        } else if (key == null) {
            status = memo.set(String.format(Locale.ROOT, "%.0f%%", progress * 100.0));
        } else {
            status = memo.set(tr(key).getString());
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
        if (this.regionPage != null) {
            Ui.label(graphics, this.font, tr("litematicaplus.region.title", this.regionPage), this.detailX,
                    this.top + Theme.CONTROL_HEIGHT + 5);
            Ui.label(graphics, this.font, tr("litematicaplus.region.offset"), this.detailX,
                    this.top + Theme.CONTROL_HEIGHT * 2 + 21);
            return;
        }
        if (this.gridPage) {
            long copies = selected.grid().copies();
            Ui.label(graphics, this.font, tr("litematicaplus.grid.title", copies), this.detailX,
                    this.top + Theme.CONTROL_HEIGHT + 5);
            int y = this.top + Theme.CONTROL_HEIGHT + 16 + (Theme.CONTROL_HEIGHT + 4) * 4 + 4;
            Ui.wrapped(graphics, this.font, tr(copies > PlacementGrid.MAX_COPIES ? "litematicaplus.grid.too_many"
                    : "litematicaplus.grid.hint", PlacementGrid.MAX_COPIES), this.detailX, y, this.detailWidth,
                    Math.max(0, (this.top + this.height - y) / 10), copies > PlacementGrid.MAX_COPIES ? Theme.DANGER
                    : Theme.TEXT_DIM, mouseX, mouseY);
            return;
        }
        if (this.regionsTop > 0) {
            PlacementRuntime runtime = GhostEngine.get().runtime(selected.id());
            PlacementVolume volume = runtime == null ? null : runtime.volume();
            Component label = volume == null ? tr("litematicaplus.placements.regions")
                    : tr("litematicaplus.placements.regions_blocks", volume.totalBlocks());
            Ui.label(graphics, this.font, label, this.detailX, this.regionsTop - 11);
        }
        if (selected.loadState() == SchematicPlacement.LoadState.FAILED && this.failTop > 0) {
            Ui.wrapped(graphics, this.font, tr("litematicaplus.placement.failed", selected.error()), this.detailX,
                    this.failTop, this.detailWidth, 2, Theme.DANGER, mouseX, mouseY);
        }
    }

    private void embed(SchematicPlacement placement, boolean workSettings) {
        Path file = PlacementManager.resolve(placement.file());
        if (this.embedding || placement.serverId() != null || file.startsWith(SyncmaticaClient.cacheDirectory())) {
            return;
        }
        EmbeddedPlacement saved = PlacementManager.embedded(placement, workSettings, Session.dimension(this.minecraft),
                Session.world(this.minecraft));
        this.embedding = true;
        this.screen.refresh();
        try {
            Workers.save().execute(() -> {
                Component message;
                try {
                    SchematicIo.embedPlacement(file, saved);
                    message = tr(workSettings && saved.hasWorkSettings() ? "litematicaplus.placements.embedded_all"
                            : "litematicaplus.placements.embedded", file.getFileName().toString());
                } catch (IOException | RuntimeException exception) {
                    LitematicaPlusClient.LOGGER.warn("Could not write the placement into {}", file, exception);
                    message = tr("litematicaplus.placements.embed_failed", Notify.describe(exception));
                }
                Component result = message;
                this.minecraft.execute(() -> {
                    this.embedding = false;
                    notice(result);
                    this.screen.refresh();
                });
            });
        } catch (RejectedExecutionException exception) {
            this.embedding = false;
            notice(tr("litematicaplus.placements.embed_failed", Notify.describe(exception)));
            this.screen.refresh();
        }
    }

    private void notice(Component message) {
        if (message != null) {
            this.notice = message;
            this.noticeTicks = 160;
        }
    }
}
