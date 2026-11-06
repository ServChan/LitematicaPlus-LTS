package org.lts.litematicaplus.gui;

import com.mojang.blaze3d.platform.InputConstants;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Locale;
import java.util.Set;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import org.lts.litematicaplus.LitematicaPlusClient;
import org.lts.litematicaplus.LitematicaPlusConfig;
import org.lts.litematicaplus.Session;
import org.lts.litematicaplus.engine.GhostEngine;
import org.lts.litematicaplus.engine.PlacementRuntime;
import org.lts.litematicaplus.hud.InfoHud;
import org.lts.litematicaplus.materials.ContainerMemory;
import org.lts.litematicaplus.materials.CountParser;
import org.lts.litematicaplus.materials.EnderChestMemory;
import org.lts.litematicaplus.materials.MaterialExport;
import org.lts.litematicaplus.materials.MaterialList;
import org.lts.litematicaplus.materials.MaterialReport;
import org.lts.litematicaplus.placement.PlacementManager;
import org.lts.litematicaplus.placement.SchematicPlacement;
import org.lts.litematicaplus.recipes.RawMaterials;
import org.lts.litematicaplus.util.Notify;

final class MaterialsView extends TabView {
    private record RowText(String available, String missing, String total, String name, boolean hidden) {
    }

    private static MaterialList.Sort sort = MaterialList.Sort.MISSING;
    private static boolean hideCompleted = true;
    private static String query = "";

    private ListPanel<MaterialList.Entry> list;
    private List<MaterialList.Entry> entries = List.of();
    private long totalMissing;
    private boolean anyPacked;
    private int ticks;
    private int scroll;
    private int columnWidth;
    private boolean showStacks;
    private boolean exporting;
    private Component status;
    private int statusTicks;
    private final Map<MaterialList.Entry, RowText> rowText = new IdentityHashMap<>();
    private final Map<Item, Set<Block>> blocksByItem = new IdentityHashMap<>();
    private FlatButton hideGhosts;
    private EditBox searchBox;
    private EditBox collectedBox;
    private FlatButton markButton;
    private FlatButton clearMarks;
    private boolean syncing;
    private Item editing;
    private int markedCount;
    private int headerY;
    private int matchCount;
    private int typeCount;
    private final Map<Item, String> searchText = new IdentityHashMap<>();
    private final TextMemo[] headers = {new TextMemo(), new TextMemo(), new TextMemo()};

    MaterialsView(HubScreen screen) {
        super(screen);
    }

    @Override
    Component summary() {
        if (this.status != null) {
            return this.status;
        }
        Component summary = searching() ? tr("litematicaplus.materials.search_count", this.matchCount, this.typeCount)
                : tr("litematicaplus.materials.summary", this.entries.size(), this.totalMissing);
        if (this.markedCount > 0) {
            summary = summary.copy().append(" · ").append(tr("litematicaplus.materials.marked_summary", this.markedCount));
        }
        int hidden = GhostEngine.get().hiddenBlocks().size();
        if (hidden > 0) {
            summary = summary.copy().append(" · ").append(tr("litematicaplus.materials.hidden_summary", hidden));
        }
        if (this.anyPacked) {
            summary = summary.copy().append(" · ").append(tr("litematicaplus.materials.packed_hint"));
        }
        if (LitematicaPlusConfig.current().containerMemory && !this.entries.isEmpty()) {
            summary = summary.copy().append(" · ").append(tr("litematicaplus.materials.locate_hint"));
        }
        if (!EnderChestMemory.counted()) {
            return summary;
        }
        return summary.copy().append(" · ").append(tr(EnderChestMemory.known() ? "litematicaplus.materials.ender_counted"
                : "litematicaplus.materials.ender_unknown"));
    }

    private static boolean searching() {
        return !query.isBlank();
    }

    private void status(Component message) {
        this.status = message;
        this.statusTicks = 120;
    }

    @Override
    void init() {
        SchematicPlacement placement = PlacementManager.get().selected();
        if (placement == null) {
            add(FlatButton.primary(this.left + (this.width - 160) / 2, this.top + this.height / 2 + 30, 160,
                    tr("litematicaplus.overview.browse"), button -> this.screen.switchTab(HubScreen.Tab.SCHEMATICS)));
            add(hint(FlatButton.secondary(this.left + (this.width - 160) / 2, this.top + this.height / 2 + 30
                    + Theme.CONTROL_HEIGHT + 4, 160, tr("litematicaplus.raw.custom"), button -> this.minecraft.gui.setScreen(
                            new RawMaterialsScreen(this.screen, RawMaterialsScreen.Source.customList()))),
                    "litematicaplus.raw.custom.tip"));
            return;
        }
        if (this.exporting) {
            exportRow();
        } else {
            int fifth = (this.width - Theme.GAP * 4) / 5;
            add(FlatButton.secondary(this.left, this.top, fifth, tr("litematicaplus.materials.sort",
                    tr("litematicaplus.materials.sort." + sort.name().toLowerCase(Locale.ROOT))), button -> {
                        sort = sort.next();
                        this.screen.refresh();
                    }));
            add(FlatButton.toggle(this.left + fifth + Theme.GAP, this.top, fifth,
                    tr("litematicaplus.materials.hide_completed"), () -> hideCompleted, button -> {
                        hideCompleted = !hideCompleted;
                        recompute();
                    }));
            this.hideGhosts = add(FlatButton.toggle(this.left + (fifth + Theme.GAP) * 2, this.top, fifth,
                    tr("litematicaplus.materials.hide_ghosts"), this::selectedHidden, button -> toggleGhosts()));
            add(hint(FlatButton.secondary(this.left + (fifth + Theme.GAP) * 3, this.top, fifth,
                    tr("litematicaplus.raw.open"), button -> this.minecraft.gui.setScreen(new RawMaterialsScreen(this.screen,
                            RawMaterialsScreen.Source.placement(placement)))), "litematicaplus.raw.open.tip"));
            add(FlatButton.secondary(this.left + (fifth + Theme.GAP) * 4, this.top,
                    this.width - (fifth + Theme.GAP) * 4, tr("litematicaplus.materials.export"), button -> {
                        this.exporting = true;
                        this.screen.refresh();
                    }));
        }
        int searchY = this.top + Theme.CONTROL_HEIGHT + 4;
        this.searchBox = field(this.left, searchY, this.width, query, tr("litematicaplus.materials.search"), 64);
        this.searchBox.setResponder(value -> {
            if (!value.equals(query)) {
                query = value;
                recompute();
                this.list.setScroll(0);
            }
        });
        hint(this.searchBox, "litematicaplus.materials.search_tip");
        this.headerY = searchY + Theme.CONTROL_HEIGHT + 5;
        int listTop = searchY + Theme.CONTROL_HEIGHT + 16;
        this.columnWidth = Math.max(44, Math.min(110, this.width / 5));
        this.showStacks = this.width >= 400;
        int barY = this.top + this.height - Theme.CONTROL_HEIGHT;
        int third = (this.width - Theme.GAP * 2) / 3;
        this.collectedBox = field(this.left, barY, third, "", tr("litematicaplus.materials.collected"), 24);
        hint(this.collectedBox, "litematicaplus.materials.collected.tip");
        this.collectedBox.setResponder(this::collectedChanged);
        this.markButton = add(hint(FlatButton.toggle(this.left + third + Theme.GAP, barY, third,
                tr("litematicaplus.materials.mark"), this::selectedMarked, button -> toggleMark()),
                "litematicaplus.materials.mark.tip"));
        this.clearMarks = add(hint(FlatButton.secondary(this.left + (third + Theme.GAP) * 2, barY,
                this.width - (third + Theme.GAP) * 2, tr("litematicaplus.materials.clear_marks"), button -> clearMarks()),
                "litematicaplus.materials.clear_marks.tip"));
        this.list = add(new ListPanel<MaterialList.Entry>(this.left, listTop, this.width,
                barY - 4 - listTop, Theme.ROW_HEIGHT, tr("litematicaplus.tab.materials"), this::renderRow)
                .onSelect(entry -> syncEditor())
                .onPress(this::locate)
                .describe(entry -> InfoHud.stackOf(entry.item()).getHoverName().getString() + " " + entry.missing())
                .empty(tr("litematicaplus.materials.empty_title"), tr("litematicaplus.materials.empty_hint")));
        recompute();
        this.list.setScroll(this.scroll);
        syncEditor();
    }

    private MaterialList.Entry selectedEntry() {
        return this.list == null ? null : this.list.selectedItem();
    }

    private boolean selectedMarked() {
        MaterialList.Entry entry = selectedEntry();
        return entry != null && entry.marked();
    }

    private void syncEditor() {
        if (this.collectedBox == null) {
            return;
        }
        MaterialList.Entry entry = selectedEntry();
        this.editing = entry == null ? null : entry.item();
        this.syncing = true;
        this.collectedBox.setValue(entry == null || entry.marked() || entry.collected() <= 0L ? ""
                : Long.toString(entry.collected()));
        this.collectedBox.setEditable(entry != null);
        this.collectedBox.setTextColor(Theme.TEXT);
        this.syncing = false;
    }

    private void collectedChanged(String value) {
        if (this.syncing) {
            return;
        }
        SchematicPlacement placement = PlacementManager.get().selected();
        MaterialList.Entry entry = selectedEntry();
        if (placement == null || entry == null || entry.item() != this.editing) {
            return;
        }
        long amount = CountParser.parse(value, InfoHud.stackOf(entry.item()).getMaxStackSize());
        if (amount < 0L) {
            this.collectedBox.setTextColor(Theme.DANGER);
            return;
        }
        this.collectedBox.setTextColor(Theme.TEXT);
        if (amount == 0L && entry.marked()) {
            return;
        }
        PlacementManager.get().setCollected(placement, entry.item(), amount);
        recompute();
    }

    private void toggleMark() {
        SchematicPlacement placement = PlacementManager.get().selected();
        MaterialList.Entry entry = selectedEntry();
        if (placement == null || entry == null) {
            status(tr("litematicaplus.materials.mark_pick"));
            return;
        }
        boolean mark = !entry.marked();
        PlacementManager.get().setCollected(placement, entry.item(), mark ? MaterialList.Entry.ALL : 0L);
        Component name = InfoHud.stackOf(entry.item()).getHoverName();
        status(tr(mark ? "litematicaplus.panel.marked" : "litematicaplus.panel.unmarked", name));
        recompute();
        syncEditor();
    }

    private void clearMarks() {
        SchematicPlacement placement = PlacementManager.get().selected();
        if (placement == null || placement.collected().isEmpty()) {
            status(tr("litematicaplus.materials.no_marks"));
            return;
        }
        int count = placement.collected().size();
        PlacementManager.get().clearCollected(placement);
        status(tr("litematicaplus.materials.marks_cleared", count));
        recompute();
        syncEditor();
    }

    private void recompute() {
        SchematicPlacement placement = PlacementManager.get().selected();
        if (placement == null || this.list == null) {
            this.entries = List.of();
            return;
        }
        PlacementRuntime runtime = GhostEngine.get().runtime(placement.id());
        boolean ready = false;
        if (placement.loadState() == SchematicPlacement.LoadState.FAILED) {
            this.list.empty(tr("litematicaplus.state.failed_title"), tr("litematicaplus.state.failed_hint"));
        } else if (!placement.enabled()) {
            this.list.empty(tr("litematicaplus.state.hidden_title"), tr("litematicaplus.state.hidden_hint"));
        } else if (runtime == null || runtime.volume() == null || runtime.pendingSections() > 0 && runtime.verifiedSections() == 0) {
            this.list.empty(tr("litematicaplus.state.loading_title"), tr("litematicaplus.state.loading_hint"));
        } else {
            ready = true;
            this.list.empty(tr("litematicaplus.materials.empty_title"), tr("litematicaplus.materials.empty_hint"));
        }
        List<MaterialList.Entry> all = MaterialList.compute(runtime,
                this.minecraft.player == null ? null : this.minecraft.player.getInventory(), sort);
        this.blocksByItem.clear();
        if (runtime != null && runtime.volume() != null) {
            for (BlockState state : runtime.volume().palette()) {
                Item item = MaterialList.itemOf(state);
                if (item != Items.AIR) {
                    this.blocksByItem.computeIfAbsent(item, ignored -> new HashSet<>()).add(state.getBlock());
                }
            }
        }
        List<MaterialList.Entry> shown = new ArrayList<>();
        long missing = 0L;
        boolean packed = false;
        boolean searching = searching();
        int matched = 0;
        int doneHidden = 0;
        int marked = 0;
        for (MaterialList.Entry entry : all) {
            missing += entry.missing();
            packed |= entry.packed() > 0L;
            if (entry.marked() && entry.missing() > 0L) {
                marked++;
            }
            if (searching && !matchesQuery(entry)) {
                continue;
            }
            matched++;
            if (!hideCompleted || entry.missing() > 0L && !entry.marked()) {
                shown.add(entry);
            } else {
                doneHidden++;
            }
        }
        this.markedCount = marked;
        this.matchCount = matched;
        this.typeCount = all.size();
        if (ready && searching && shown.isEmpty()) {
            this.list.empty(tr("litematicaplus.materials.search_empty", query.strip()), tr(doneHidden > 0
                    ? "litematicaplus.materials.search_done_hint" : "litematicaplus.materials.search_empty_hint"));
        }
        this.totalMissing = missing;
        this.anyPacked = packed;
        this.entries = shown;
        this.rowText.clear();
        MaterialList.Entry previous = this.list.selectedItem();
        int keep = this.list.scroll();
        this.list.setItems(shown);
        if (previous != null) {
            int index = -1;
            for (int i = 0; i < shown.size(); i++) {
                if (shown.get(i).item() == previous.item()) {
                    index = i;
                    break;
                }
            }
            this.list.select(index);
            this.list.setScroll(keep);
        }
    }

    private boolean matchesQuery(MaterialList.Entry entry) {
        String text = this.searchText.computeIfAbsent(entry.item(), item -> InfoHud.stackOf(item).getHoverName().getString()
                + " " + BuiltInRegistries.ITEM.getKey(item));
        return SettingsView.matches(text, query);
    }

    @Override
    boolean keyPressed(KeyEvent event) {
        if (this.searchBox == null) {
            return false;
        }
        boolean focused = this.searchBox.isFocused();
        if (event.key() == InputConstants.KEY_F && event.hasControlDown() && !focused
                && !HubScreen.typing(this.screen.getFocused())) {
            this.screen.setFocused(this.searchBox);
            this.searchBox.moveCursorToEnd(false);
            return true;
        }
        if (focused && event.key() == InputConstants.KEY_ESCAPE && searching()) {
            this.searchBox.setValue("");
            return true;
        }
        if (focused && (event.key() == InputConstants.KEY_DOWN || event.key() == InputConstants.KEY_RETURN)) {
            this.searchBox.setFocused(false);
            this.screen.setFocused(this.list);
            return true;
        }
        return false;
    }

    private boolean hidden(MaterialList.Entry entry) {
        Set<Block> blocks = this.blocksByItem.get(entry.item());
        if (blocks == null || blocks.isEmpty()) {
            return false;
        }
        GhostEngine engine = GhostEngine.get();
        for (Block block : blocks) {
            if (!engine.hidden(block)) {
                return false;
            }
        }
        return true;
    }

    private boolean selectedHidden() {
        MaterialList.Entry entry = this.list == null ? null : this.list.selectedItem();
        return entry != null && hidden(entry);
    }

    private void toggleGhosts() {
        MaterialList.Entry entry = this.list == null ? null : this.list.selectedItem();
        Set<Block> blocks = entry == null ? null : this.blocksByItem.get(entry.item());
        if (blocks == null || blocks.isEmpty()) {
            status(tr("litematicaplus.materials.hide_ghosts_pick"));
            return;
        }
        boolean hide = !hidden(entry);
        GhostEngine.get().setHidden(blocks, hide);
        Session.markDirty();
        this.rowText.clear();
        Component name = InfoHud.stackOf(entry.item()).getHoverName();
        status(tr(hide ? "litematicaplus.message.ghost_hidden" : "litematicaplus.message.ghost_shown", name));
    }

    private void locate(MaterialList.Entry entry) {
        LitematicaPlusConfig config = LitematicaPlusConfig.current();
        if (!config.containerMemory) {
            status(tr("litematicaplus.materials.locate_off"));
            return;
        }
        Component name = InfoHud.stackOf(entry.item()).getHoverName();
        int found = ContainerMemory.toggleFocus(this.minecraft, entry.item());
        if (found < 0) {
            status(tr("litematicaplus.materials.locate_cleared"));
        } else if (found == 0) {
            status(tr(ContainerMemory.any() ? "litematicaplus.materials.locate_none"
                    : "litematicaplus.materials.locate_empty", name));
        } else {
            status(tr("litematicaplus.materials.locate_found", name, found));
        }
    }

    private void exportRow() {
        MaterialExport.Format[] formats = MaterialExport.Format.values();
        int back = 22;
        int count = formats.length + 1;
        int cell = (this.width - back - Theme.GAP * count) / count;
        int x = this.left;
        add(FlatButton.secondary(x, this.top, back, Component.literal("<"), button -> {
            this.exporting = false;
            this.screen.refresh();
        }));
        x += back + Theme.GAP;
        add(FlatButton.secondary(x, this.top, cell, tr("litematicaplus.materials.export_clipboard"), button -> {
            copy();
            this.exporting = false;
            this.screen.refresh();
        }));
        for (int i = 0; i < formats.length; i++) {
            MaterialExport.Format format = formats[i];
            x += cell + Theme.GAP;
            int width = i == formats.length - 1 ? this.left + this.width - x : cell;
            add(FlatButton.secondary(x, this.top, width, Component.literal(format.extension().toUpperCase(Locale.ROOT)),
                    button -> {
                        export(format);
                        this.exporting = false;
                        this.screen.refresh();
                    }));
        }
    }

    private void export(MaterialExport.Format format) {
        SchematicPlacement placement = PlacementManager.get().selected();
        PlacementRuntime runtime = placement == null ? null : GhostEngine.get().runtime(placement.id());
        List<MaterialList.Entry> all = MaterialList.compute(runtime,
                this.minecraft.player == null ? null : this.minecraft.player.getInventory(), sort);
        if (all.isEmpty()) {
            status(tr("litematicaplus.materials.export_empty"));
            return;
        }
        LocalDateTime now = LocalDateTime.now();
        MaterialReport report = MaterialReport.of(placement.name(), all, now);
        MaterialReport.RawSection raw = RawMaterials.sectionFor(all, LitematicaPlusConfig.current().rawScopeAll
                ? RawMaterials.Scope.ALL : RawMaterials.Scope.MISSING);
        if (raw != null) {
            report = report.withRaw(raw);
        }
        MaterialExport.export(report, MaterialExport.Labels.capture(), format, now).whenComplete((path, failure) ->
                this.minecraft.execute(() -> exported(path, failure)));
    }

    private void exported(Path path, Throwable failure) {
        if (failure != null || path == null) {
            LitematicaPlusClient.LOGGER.warn("Could not export the material list", failure);
            Component message = tr("litematicaplus.materials.export_failed", Notify.describe(failure));
            status(message);
            Notify.chat(message.copy().withStyle(ChatFormatting.RED));
            return;
        }
        Path absolute = path.toAbsolutePath();
        status(tr("litematicaplus.materials.export_saved", absolute.getFileName().toString()));
        MutableComponent file = Component.literal(absolute.getFileName().toString()).withStyle(style -> style
                .withColor(ChatFormatting.AQUA).withUnderlined(true).withClickEvent(new ClickEvent.OpenFile(absolute))
                .withHoverEvent(new HoverEvent.ShowText(Component.literal(absolute.toString()))));
        MutableComponent folder = tr("litematicaplus.materials.export_folder").copy().withStyle(style -> style
                .withColor(ChatFormatting.GRAY).withUnderlined(true)
                .withClickEvent(new ClickEvent.OpenFile(absolute.getParent())));
        Notify.chat(tr("litematicaplus.materials.export_saved", file).copy().append(" ").append(folder));
    }

    private void copy() {
        StringBuilder builder = new StringBuilder();
        builder.append(tr("litematicaplus.materials.column_item").getString()).append('\t')
                .append(tr("litematicaplus.materials.column_total").getString()).append('\t')
                .append(tr("litematicaplus.materials.column_missing").getString()).append('\t')
                .append(tr("litematicaplus.materials.column_have").getString()).append('\n');
        for (MaterialList.Entry entry : this.entries) {
            builder.append(InfoHud.stackOf(entry.item()).getHoverName().getString()).append('\t')
                    .append(entry.total()).append('\t').append(entry.missing()).append('\t')
                    .append(entry.available()).append('\n');
        }
        this.minecraft.keyboardHandler.setClipboard(builder.toString());
        status(tr("litematicaplus.materials.copied"));
    }

    @Override
    void tick() {
        if (this.list != null) {
            this.scroll = this.list.scroll();
            MaterialList.Entry selected = this.list.selectedItem();
            if (this.hideGhosts != null) {
                this.hideGhosts.active = selected != null && this.blocksByItem.containsKey(selected.item());
            }
            if (this.markButton != null) {
                this.markButton.active = selected != null;
            }
            if (this.clearMarks != null) {
                SchematicPlacement current = PlacementManager.get().selected();
                this.clearMarks.active = current != null && !current.collected().isEmpty();
            }
            if (this.collectedBox != null && (selected == null ? null : selected.item()) != this.editing
                    && !this.collectedBox.isFocused()) {
                syncEditor();
            }
            if (this.statusTicks > 0 && --this.statusTicks == 0) {
                this.status = null;
            }
            if (++this.ticks % 10 == 0) {
                recompute();
            }
        }
    }

    private void renderRow(GuiGraphicsExtractor graphics, Font rowFont, MaterialList.Entry entry, int x, int y,
            int rowWidth, int rowHeight, boolean isSelected, boolean hovered) {
        ItemStack stack = InfoHud.stackOf(entry.item());
        if (entry.item() == ContainerMemory.focus()) {
            graphics.fill(x + 1, y + 2, x + 3, y + rowHeight - 2, Theme.ACCENT);
        }
        graphics.fakeItem(stack, x + 5, y + 3);
        if (entry.marked()) {
            graphics.fill(x + 13, y + 11, x + 23, y + 19, Theme.SURFACE_SELECTED);
            Ui.tick(graphics, x + 14, y + 12, Theme.SUCCESS);
        }
        int textY = y + (rowHeight - 8) / 2;
        int right = x + rowWidth - 6;
        boolean done = entry.missing() <= 0L || entry.marked();
        boolean enough = entry.available() >= entry.missing();
        RowText text = this.rowText.computeIfAbsent(entry, ignored -> rowText(entry, stack, done));
        Ui.right(graphics, rowFont, text.available(), right, textY,
                done ? Theme.TEXT_DIM : enough ? Theme.SUCCESS : Theme.WARNING);
        Ui.right(graphics, rowFont, text.missing(), right - this.columnWidth, textY,
                done ? Theme.SUCCESS : entry.manual() ? Theme.ACCENT_HOVER : Theme.TEXT_BRIGHT);
        Ui.right(graphics, rowFont, text.total(), right - this.columnWidth * 2 - 10, textY, Theme.TEXT_DIM);
        int nameWidth = rowWidth - 30 - this.columnWidth * 3 - 16;
        Ui.clipped(graphics, rowFont, text.name(), x + 26, textY, nameWidth,
                done || text.hidden() ? Theme.TEXT_DIM : Theme.TEXT);
    }

    private RowText rowText(MaterialList.Entry entry, ItemStack stack, boolean done) {
        boolean hidden = hidden(entry);
        String name = stack.getHoverName().getString();
        if (hidden) {
            name = tr("litematicaplus.materials.ghosts_hidden", name).getString();
        }
        if (entry.manual()) {
            name = tr("litematicaplus.materials.collected_name", name, entry.collected()).getString();
        }
        String missingText = entry.marked() && entry.missing() > 0L ? tr("litematicaplus.materials.marked").getString()
                : done ? tr("litematicaplus.materials.done").getString()
                : entry.manual() ? compact(Math.max(0L, entry.missing() - entry.collected()), stack.getMaxStackSize())
                : compact(entry.missing(), stack.getMaxStackSize());
        return new RowText(entry.haveText(), missingText, Long.toString(entry.total()), name, hidden);
    }

    private String compact(long count, int stackSize) {
        if (!this.showStacks || stackSize <= 1 || count < stackSize) {
            return Long.toString(count);
        }
        long stacks = count / stackSize;
        long rest = count % stackSize;
        return tr(rest == 0L ? "litematicaplus.materials.stacks" : "litematicaplus.materials.stacks_rest",
                count, stacks, rest).getString();
    }

    @Override
    void extract(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        if (PlacementManager.get().selected() == null) {
            Ui.emptyState(graphics, this.font, this.left, this.top, this.width, this.height,
                    tr("litematicaplus.placements.empty_title"), tr("litematicaplus.materials.no_placement_hint"));
            return;
        }
        int headerY = this.headerY;
        int right = this.left + this.width - 7 - (this.list != null && this.list.items().size() * Theme.ROW_HEIGHT
                > this.list.getHeight() ? 5 : 0);
        graphics.text(this.font, tr("litematicaplus.materials.column_item"), this.left + 27, headerY, Theme.ACCENT, false);
        Ui.right(graphics, this.font, header(0, "litematicaplus.materials.column_have"), right, headerY, Theme.ACCENT);
        Ui.right(graphics, this.font, header(1, "litematicaplus.materials.column_missing"), right - this.columnWidth,
                headerY, Theme.ACCENT);
        Ui.right(graphics, this.font, header(2, "litematicaplus.materials.column_total"),
                right - this.columnWidth * 2 - 10, headerY, Theme.ACCENT);
    }

    private String header(int index, String key) {
        TextMemo memo = this.headers[index];
        return memo.stale(key) ? memo.set(tr(key).getString()) : memo.text();
    }
}
