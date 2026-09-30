package org.lts.litematicaplus.gui;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import org.lts.litematicaplus.engine.GhostEngine;
import org.lts.litematicaplus.engine.PlacementRuntime;
import org.lts.litematicaplus.hud.InfoHud;
import org.lts.litematicaplus.materials.MaterialList;
import org.lts.litematicaplus.placement.PlacementManager;
import org.lts.litematicaplus.placement.SchematicPlacement;

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
    private FlatButton copyButton;
    private int copiedTicks;

    MaterialsView(HubScreen screen) {
        super(screen);
    }

    @Override
    Component summary() {
        return tr("litematicaplus.materials.summary", this.entries.size(), this.totalMissing);
    }

    @Override
    void init() {
        SchematicPlacement placement = PlacementManager.get().selected();
        if (placement == null) {
            add(FlatButton.primary(this.left + (this.width - 160) / 2, this.top + this.height / 2 + 30, 160,
                    tr("litematicaplus.overview.browse"), button -> this.screen.switchTab(HubScreen.Tab.SCHEMATICS)));
            return;
        }
        int third = (this.width - Theme.GAP * 2) / 3;
        add(FlatButton.secondary(this.left, this.top, third, tr("litematicaplus.materials.sort",
                tr("litematicaplus.materials.sort." + sort.name().toLowerCase(Locale.ROOT))), button -> {
                    sort = sort.next();
                    this.screen.refresh();
                }));
        add(FlatButton.toggle(this.left + third + Theme.GAP, this.top, third, tr("litematicaplus.materials.hide_completed"),
                () -> hideCompleted, button -> {
                    hideCompleted = !hideCompleted;
                    recompute();
                }));
        this.copyButton = add(FlatButton.secondary(this.left + (third + Theme.GAP) * 2, this.top,
                this.width - (third + Theme.GAP) * 2, tr("litematicaplus.materials.copy"), button -> copy()));
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
        if (this.copyButton != null) {
            this.copyButton.setMessage(tr("litematicaplus.materials.copied"));
            this.copiedTicks = 40;
        }
    }

    @Override
    void tick() {
        if (this.list != null) {
            this.scroll = this.list.scroll();
            if (this.copiedTicks > 0 && --this.copiedTicks == 0 && this.copyButton != null) {
                this.copyButton.setMessage(tr("litematicaplus.materials.copy"));
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
