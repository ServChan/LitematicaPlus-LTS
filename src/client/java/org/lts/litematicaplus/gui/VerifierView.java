package org.lts.litematicaplus.gui;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Predicate;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.state.BlockState;
import org.lts.litematicaplus.LitematicaPlusConfig;
import org.lts.litematicaplus.engine.GhostEngine;
import org.lts.litematicaplus.engine.PlacementRuntime;
import org.lts.litematicaplus.placement.PlacementManager;
import org.lts.litematicaplus.placement.SchematicPlacement;
import org.lts.litematicaplus.render.BoxRenderer;
import org.lts.litematicaplus.verify.MismatchType;

final class VerifierView extends TabView {
    private static final int LIMIT = 200;

    private ListPanel<GhostEngine.Mismatch> list;
    private List<GhostEngine.Mismatch> mismatches = List.of();
    private int ticks;
    private int scroll;
    private List<Object> signature = List.of();
    private final Map<GhostEngine.Mismatch, RowText> rowText = new IdentityHashMap<>();

    private record RowText(String type, String where, String detail) {
    }

    VerifierView(HubScreen screen) {
        super(screen);
    }

    @Override
    Component summary() {
        SchematicPlacement placement = PlacementManager.get().selected();
        PlacementRuntime runtime = placement == null ? null : GhostEngine.get().runtime(placement.id());
        if (runtime == null || runtime.volume() == null) {
            return Component.empty();
        }
        return tr("litematicaplus.verifier.summary", runtime.knownSections(), runtime.slotCount(), runtime.errors());
    }

    @Override
    void init() {
        SchematicPlacement placement = PlacementManager.get().selected();
        if (placement == null) {
            add(FlatButton.primary(this.left + (this.width - 160) / 2, this.top + this.height / 2 + 30, 160,
                    tr("litematicaplus.overview.browse"), button -> this.screen.switchTab(HubScreen.Tab.SCHEMATICS)));
            return;
        }
        int columns = this.width >= 400 ? 4 : 2;
        int cell = (this.width - Theme.GAP * (columns - 1)) / columns;
        toggle(0, columns, cell, MismatchType.MISSING, c -> c.showMissing, c -> c.showMissing = !c.showMissing);
        toggle(1, columns, cell, MismatchType.WRONG_BLOCK, c -> c.showWrongBlock, c -> c.showWrongBlock = !c.showWrongBlock);
        toggle(2, columns, cell, MismatchType.WRONG_STATE, c -> c.showWrongState, c -> c.showWrongState = !c.showWrongState);
        toggle(3, columns, cell, MismatchType.EXTRA, c -> c.showExtra, c -> c.showExtra = !c.showExtra);
        int rows = 4 / columns;
        int listTop = this.top + rows * (Theme.CONTROL_HEIGHT + 4) + 2;
        int listBottom = this.top + this.height - Theme.CONTROL_HEIGHT - 4;
        this.list = add(new ListPanel<GhostEngine.Mismatch>(this.left, listTop, this.width, listBottom - listTop,
                Theme.ROW_HEIGHT, tr("litematicaplus.tab.verifier"), this::renderRow)
                .describe(mismatch -> tr("litematicaplus.mismatch." + mismatch.type().key()).getString() + " "
                        + mismatch.pos().toShortString())
                .onSelect(mismatch -> BoxRenderer.setFocus(mismatch.pos(), mismatch.type().color()))
                .empty(tr("litematicaplus.verifier.empty_title"), tr("litematicaplus.verifier.empty_hint")));
        FlatButton clear = add(FlatButton.secondary(this.left + this.width - 150, listBottom + 4, 150,
                tr("litematicaplus.verifier.clear_focus"), button -> BoxRenderer.setFocus(null, 0)));
        clear.active = true;
        recompute();
        this.list.setScroll(this.scroll);
    }

    private void toggle(int index, int columns, int cell, MismatchType type, Predicate<LitematicaPlusConfig> getter,
            Consumer<LitematicaPlusConfig> flip) {
        int x = this.left + index % columns * (cell + Theme.GAP);
        int y = this.top + index / columns * (Theme.CONTROL_HEIGHT + 4);
        add(FlatButton.toggle(x, y, cell, tr("litematicaplus.mismatch." + type.key()),
                () -> getter.test(LitematicaPlusConfig.current()), button -> {
                    LitematicaPlusConfig config = LitematicaPlusConfig.currentCopy();
                    flip.accept(config);
                    LitematicaPlusConfig.save(config);
                    recompute();
                }));
    }

    private boolean changed() {
        SchematicPlacement placement = PlacementManager.get().selected();
        GhostEngine engine = GhostEngine.get();
        PlacementRuntime runtime = placement == null ? null : engine.runtime(placement.id());
        LitematicaPlusConfig config = LitematicaPlusConfig.current();
        List<Object> signature = new ArrayList<>();
        signature.add(placement == null ? null : placement.id());
        signature.add(placement == null ? null : placement.loadState());
        signature.add(placement != null && placement.enabled());
        signature.add(runtime);
        signature.add(runtime == null ? null : runtime.volume());
        signature.add(engine.layer());
        signature.add(this.minecraft.player == null ? null : this.minecraft.player.position());
        signature.add(config.showMissing);
        signature.add(config.showWrongBlock);
        signature.add(config.showWrongState);
        signature.add(config.showExtra);
        signature.add(config.countVisibleOnly);
        signature.add(engine.jobsStarted());
        if (runtime != null) {
            signature.add(runtime.pendingSections());
            signature.add(runtime.verifiedSections());
            signature.add(runtime.knownSections());
            signature.add(runtime.scopePending());
            for (MismatchType type : MismatchType.VALUES) {
                signature.add(runtime.fullCount(type));
            }
        }
        boolean busy = engine.pending() > 0 || engine.inFlight() > 0;
        if (!busy && signature.equals(this.signature)) {
            return false;
        }
        this.signature = signature;
        return true;
    }

    private void recompute() {
        this.rowText.clear();
        SchematicPlacement placement = PlacementManager.get().selected();
        if (placement == null || this.list == null || this.minecraft.player == null) {
            this.mismatches = List.of();
            return;
        }
        LitematicaPlusConfig config = LitematicaPlusConfig.current();
        boolean[] enabled = new boolean[MismatchType.VALUES.length];
        enabled[MismatchType.MISSING.ordinal()] = config.showMissing;
        enabled[MismatchType.WRONG_BLOCK.ordinal()] = config.showWrongBlock;
        enabled[MismatchType.WRONG_STATE.ordinal()] = config.showWrongState;
        enabled[MismatchType.EXTRA.ordinal()] = config.showExtra;
        PlacementRuntime runtime = GhostEngine.get().runtime(placement.id());
        if (placement.loadState() == SchematicPlacement.LoadState.FAILED) {
            this.list.empty(tr("litematicaplus.state.failed_title"), tr("litematicaplus.state.failed_hint"));
        } else if (!placement.enabled()) {
            this.list.empty(tr("litematicaplus.state.hidden_title"), tr("litematicaplus.state.hidden_hint"));
        } else if (runtime == null || runtime.volume() == null || runtime.pendingSections() > 0 && runtime.verifiedSections() == 0) {
            this.list.empty(tr("litematicaplus.state.loading_title"), tr("litematicaplus.state.loading_hint"));
        } else {
            this.list.empty(tr("litematicaplus.verifier.empty_title"), tr("litematicaplus.verifier.empty_hint"));
        }
        GhostEngine.Mismatch previous = this.list.selectedItem();
        this.mismatches = GhostEngine.get().nearestMismatches(GhostEngine.get().runtime(placement.id()),
                this.minecraft.player.position(), LIMIT, enabled);
        this.list.setItems(this.mismatches);
        if (previous != null) {
            int index = -1;
            for (int i = 0; i < this.mismatches.size(); i++) {
                if (this.mismatches.get(i).pos().equals(previous.pos())) {
                    index = i;
                    break;
                }
            }
            int keep = this.list.scroll();
            this.list.select(index);
            this.list.setScroll(keep);
        }
    }

    @Override
    void tick() {
        if (this.list != null) {
            this.scroll = this.list.scroll();
            if (++this.ticks % 20 == 0) {
                this.rowText.clear();
                if (changed()) {
                    recompute();
                }
            }
        }
    }

    private void renderRow(GuiGraphicsExtractor graphics, Font rowFont, GhostEngine.Mismatch mismatch, int x, int y,
            int rowWidth, int rowHeight, boolean isSelected, boolean hovered) {
        int textY = y + (rowHeight - 8) / 2;
        graphics.fill(x + 7, y + 7, x + 15, y + rowHeight - 7, mismatch.type().color());
        RowText text = this.rowText.computeIfAbsent(mismatch, this::rowText);
        graphics.text(rowFont, text.type(), x + 21, textY, mismatch.type().textColor(), false);
        int typeWidth = Math.max(78, rowFont.width(text.type()) + 8);
        int whereWidth = rowFont.width(text.where());
        Ui.right(graphics, rowFont, text.where(), x + rowWidth - 6, textY, Theme.TEXT_DIM);
        Ui.clipped(graphics, rowFont, text.detail(), x + 21 + typeWidth, textY, rowWidth - 33 - typeWidth - whereWidth,
                Theme.TEXT);
    }

    private RowText rowText(GhostEngine.Mismatch mismatch) {
        BlockPos pos = mismatch.pos();
        String where = pos.getX() + " " + pos.getY() + " " + pos.getZ() + "  "
                + String.format(Locale.ROOT, "%.0f m", Math.sqrt(mismatch.distanceSquared()));
        BlockState found = this.minecraft.level == null ? null : this.minecraft.level.getBlockState(pos);
        return new RowText(tr("litematicaplus.mismatch." + mismatch.type().key()).getString(), where,
                describe(mismatch, found));
    }

    private static String describe(GhostEngine.Mismatch mismatch, BlockState found) {
        String expected = mismatch.expected().getBlock().getName().getString();
        if (mismatch.type() == MismatchType.MISSING || found == null) {
            return expected;
        }
        String actual = found.getBlock().getName().getString();
        if (mismatch.type() == MismatchType.EXTRA) {
            return actual;
        }
        if (mismatch.type() == MismatchType.WRONG_STATE) {
            return expected + " " + difference(mismatch.expected(), found);
        }
        return expected + " <- " + actual;
    }

    private static String difference(BlockState expected, BlockState found) {
        StringBuilder builder = new StringBuilder();
        expected.getValues().forEach(value -> {
            if (found.hasProperty(value.property()) && !found.getValue(value.property()).equals(value.value())) {
                if (builder.length() > 0) {
                    builder.append(", ");
                }
                builder.append(value.property().getName()).append('=').append(value.valueName());
            }
        });
        return builder.length() == 0 ? "" : "[" + builder + "]";
    }

    @Override
    void extract(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        SchematicPlacement placement = PlacementManager.get().selected();
        if (placement == null) {
            Ui.emptyState(graphics, this.font, this.left, this.top, this.width, this.height,
                    tr("litematicaplus.placements.empty_title"), tr("litematicaplus.verifier.no_placement_hint"));
            return;
        }
        PlacementRuntime runtime = GhostEngine.get().runtime(placement.id());
        if (runtime == null) {
            return;
        }
        int y = this.top + this.height - Theme.CONTROL_HEIGHT + 1;
        long unknown = runtime.count(MismatchType.UNKNOWN);
        Component line = unknown > 0L ? tr("litematicaplus.verifier.unknown", unknown)
                : tr("litematicaplus.verifier.shown", this.mismatches.size());
        Ui.wrapped(graphics, this.font, line, this.left, y, this.width - 160, 2,
                unknown > 0L ? Theme.WARNING : Theme.TEXT_DIM, mouseX, mouseY);
    }
}
