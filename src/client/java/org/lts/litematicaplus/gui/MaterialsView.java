package org.lts.litematicaplus.gui;

import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.item.ItemStack;
import org.lts.litematicaplus.LitematicaPlusClient;
import org.lts.litematicaplus.engine.GhostEngine;
import org.lts.litematicaplus.engine.PlacementRuntime;
import org.lts.litematicaplus.hud.InfoHud;
import org.lts.litematicaplus.materials.MaterialExport;
import org.lts.litematicaplus.materials.MaterialList;
import org.lts.litematicaplus.materials.MaterialReport;
import org.lts.litematicaplus.placement.PlacementManager;
import org.lts.litematicaplus.placement.SchematicPlacement;
import org.lts.litematicaplus.util.Notify;

final class MaterialsView extends TabView {
    private static MaterialList.Sort sort = MaterialList.Sort.MISSING;
    private static boolean hideCompleted = true;

    private ListPanel<MaterialList.Entry> list;
    private List<MaterialList.Entry> entries = List.of();
    private long totalMissing;
    private int ticks;
    private int scroll;
    private int columnWidth;
    private boolean showStacks;
    private boolean exporting;
    private Component status;
    private int statusTicks;

    MaterialsView(HubScreen screen) {
        super(screen);
    }

    @Override
    Component summary() {
        return this.status != null ? this.status
                : tr("litematicaplus.materials.summary", this.entries.size(), this.totalMissing);
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
            return;
        }
        if (this.exporting) {
            exportRow();
        } else {
            int third = (this.width - Theme.GAP * 2) / 3;
            add(FlatButton.secondary(this.left, this.top, third, tr("litematicaplus.materials.sort",
                    tr("litematicaplus.materials.sort." + sort.name().toLowerCase(Locale.ROOT))), button -> {
                        sort = sort.next();
                        this.screen.refresh();
                    }));
            add(FlatButton.toggle(this.left + third + Theme.GAP, this.top, third,
                    tr("litematicaplus.materials.hide_completed"), () -> hideCompleted, button -> {
                        hideCompleted = !hideCompleted;
                        recompute();
                    }));
            add(FlatButton.secondary(this.left + (third + Theme.GAP) * 2, this.top, this.width - (third + Theme.GAP) * 2,
                    tr("litematicaplus.materials.export"), button -> {
                        this.exporting = true;
                        this.screen.refresh();
                    }));
        }
        int listTop = this.top + Theme.CONTROL_HEIGHT + 16;
        this.columnWidth = Math.max(44, Math.min(110, this.width / 5));
        this.showStacks = this.width >= 400;
        this.list = add(new ListPanel<MaterialList.Entry>(this.left, listTop, this.width, this.top + this.height - listTop,
                Theme.ROW_HEIGHT, tr("litematicaplus.tab.materials"), this::renderRow)
                .describe(entry -> InfoHud.stackOf(entry.item()).getHoverName().getString() + " " + entry.missing())
                .empty(tr("litematicaplus.materials.empty_title"), tr("litematicaplus.materials.empty_hint")));
        recompute();
        this.list.setScroll(this.scroll);
    }

    private void recompute() {
        SchematicPlacement placement = PlacementManager.get().selected();
        if (placement == null || this.list == null) {
            this.entries = List.of();
            return;
        }
        PlacementRuntime runtime = GhostEngine.get().runtime(placement.id());
        if (placement.loadState() == SchematicPlacement.LoadState.FAILED) {
            this.list.empty(tr("litematicaplus.state.failed_title"), tr("litematicaplus.state.failed_hint"));
        } else if (!placement.enabled()) {
            this.list.empty(tr("litematicaplus.state.hidden_title"), tr("litematicaplus.state.hidden_hint"));
        } else if (runtime == null || runtime.volume() == null || runtime.pendingSections() > 0 && runtime.verifiedSections() == 0) {
            this.list.empty(tr("litematicaplus.state.loading_title"), tr("litematicaplus.state.loading_hint"));
        } else {
            this.list.empty(tr("litematicaplus.materials.empty_title"), tr("litematicaplus.materials.empty_hint"));
        }
        List<MaterialList.Entry> all = MaterialList.compute(runtime,
                this.minecraft.player == null ? null : this.minecraft.player.getInventory(), sort);
        List<MaterialList.Entry> shown = new ArrayList<>();
        long missing = 0L;
        for (MaterialList.Entry entry : all) {
            missing += entry.missing();
            if (!hideCompleted || entry.missing() > 0L) {
                shown.add(entry);
            }
        }
        this.totalMissing = missing;
        this.entries = shown;
        this.list.setItems(shown);
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
        graphics.fakeItem(stack, x + 5, y + 3);
        int textY = y + (rowHeight - 8) / 2;
        int right = x + rowWidth - 6;
        int stackSize = stack.getMaxStackSize();
        boolean done = entry.missing() <= 0L;
        boolean enough = entry.available() >= entry.missing();
        Ui.right(graphics, rowFont, Long.toString(entry.available()), right, textY,
                done ? Theme.TEXT_DIM : enough ? Theme.SUCCESS : Theme.WARNING);
        Ui.right(graphics, rowFont, done ? tr("litematicaplus.materials.done").getString()
                : compact(entry.missing(), stackSize), right - this.columnWidth, textY, done ? Theme.SUCCESS : Theme.TEXT_BRIGHT);
        Ui.right(graphics, rowFont, Long.toString(entry.total()), right - this.columnWidth * 2 - 10, textY, Theme.TEXT_DIM);
        int nameWidth = rowWidth - 30 - this.columnWidth * 3 - 16;
        Ui.clipped(graphics, rowFont, stack.getHoverName().getString(), x + 26, textY, nameWidth,
                done ? Theme.TEXT_DIM : Theme.TEXT);
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
        int headerY = this.top + Theme.CONTROL_HEIGHT + 5;
        int right = this.left + this.width - 7 - (this.list != null && this.list.items().size() * Theme.ROW_HEIGHT
                > this.list.getHeight() ? 5 : 0);
        graphics.text(this.font, tr("litematicaplus.materials.column_item"), this.left + 27, headerY, Theme.ACCENT, false);
        Ui.right(graphics, this.font, tr("litematicaplus.materials.column_have").getString(), right, headerY, Theme.ACCENT);
        Ui.right(graphics, this.font, tr("litematicaplus.materials.column_missing").getString(), right - this.columnWidth,
                headerY, Theme.ACCENT);
        Ui.right(graphics, this.font, tr("litematicaplus.materials.column_total").getString(),
                right - this.columnWidth * 2 - 10, headerY, Theme.ACCENT);
    }
}
