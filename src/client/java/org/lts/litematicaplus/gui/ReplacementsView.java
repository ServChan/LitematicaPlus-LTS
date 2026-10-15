package org.lts.litematicaplus.gui;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import org.lts.litematicaplus.LitematicaPlusConfig;
import org.lts.litematicaplus.hud.InfoHud;
import org.lts.litematicaplus.placement.PlacementManager;
import org.lts.litematicaplus.placement.SchematicPlacement;
import org.lts.litematicaplus.schematic.Schematic;
import org.lts.litematicaplus.schematic.SchematicRegion;
import org.lts.litematicaplus.verify.StateComparator;

final class ReplacementsView extends TabView {
    private record Choice(Block block, String name, String search) {
    }

    private record Candidate(Choice choice, boolean similar) {
    }

    private static List<Choice> catalogue;
    private static Language catalogueLanguage;
    private static Block source;
    private static String query = "";

    private ListPanel<Choice> sources;
    private ListPanel<Candidate> candidates;
    private Schematic indexedSchematic;
    private List<Choice> sourceRows = List.of();
    private Component status = Component.empty();

    ReplacementsView(HubScreen screen) {
        super(screen);
    }

    @Override
    Component summary() {
        if (!this.status.getString().isEmpty()) {
            return this.status;
        }
        SchematicPlacement placement = PlacementManager.get().selected();
        int count = placement == null ? 0 : placement.replacements().size();
        return tr(LitematicaPlusConfig.current().materialSwap ? "litematicaplus.replace.summary"
                : "litematicaplus.replace.summary_off", count);
    }

    private static boolean replaceable(Block block) {
        BlockState state = block.defaultBlockState();
        return !state.isAir() && block != Blocks.STRUCTURE_VOID && !state.hasBlockEntity() && block.asItem() != Items.AIR;
    }

    private static Choice choice(Block block) {
        String name = block.getName().getString();
        return new Choice(block, name, (name + " " + BuiltInRegistries.BLOCK.getKey(block)).toLowerCase(Locale.ROOT));
    }

    private static List<Choice> catalogue() {
        Language language = Language.getInstance();
        if (catalogue == null || catalogueLanguage != language) {
            List<Choice> all = new ArrayList<>();
            for (Block block : BuiltInRegistries.BLOCK) {
                if (replaceable(block)) {
                    all.add(choice(block));
                }
            }
            all.sort(Comparator.comparing(Choice::name));
            catalogue = List.copyOf(all);
            catalogueLanguage = language;
        }
        return catalogue;
    }

    @Override
    void init() {
        SchematicPlacement placement = PlacementManager.get().selected();
        if (placement == null) {
            add(FlatButton.primary(this.left + (this.width - 160) / 2, this.top + this.height / 2 + 30, 160,
                    tr("litematicaplus.overview.browse"), button -> this.screen.switchTab(HubScreen.Tab.SCHEMATICS)));
            return;
        }
        int half = (this.width - Theme.GAP) / 2;
        FlatButton enabled = add(FlatButton.toggle(this.left, this.top, half, tr("litematicaplus.option.material_swap"),
                () -> LitematicaPlusConfig.current().materialSwap, button -> {
                    LitematicaPlusConfig config = LitematicaPlusConfig.currentCopy();
                    config.materialSwap = !config.materialSwap;
                    LitematicaPlusConfig.save(config);
                }));
        enabled.setTooltip(Tooltip.create(tr("litematicaplus.option.material_swap.tip")));
        FlatButton reset = add(FlatButton.danger(this.left + half + Theme.GAP, this.top, this.width - half - Theme.GAP,
                tr("litematicaplus.replace.reset_all"), button -> {
                    PlacementManager.get().clearReplacements(placement);
                    this.status = tr("litematicaplus.replace.cleared");
                    this.screen.refresh();
                }));
        reset.active = !placement.replacements().isEmpty();
        int listTop = this.top + Theme.CONTROL_HEIGHT + 16;
        int listHeight = this.top + this.height - listTop;
        this.sources = add(new ListPanel<Choice>(this.left, listTop, half, listHeight, Theme.ROW_HEIGHT,
                tr("litematicaplus.replace.sources"), this::renderSource)
                .describe(row -> row.name() + describeTarget(placement, row.block()))
                .empty(tr(placement.schematic() == null ? "litematicaplus.state.loading_title"
                        : "litematicaplus.replace.no_blocks_title"), tr("litematicaplus.replace.no_blocks_hint"))
                .onSelect(row -> {
                    source = row.block();
                    updateCandidates(placement);
                    if (this.candidates != null) {
                        this.candidates.setScroll(0);
                    }
                }));
        this.sourceRows = sourceRows(placement);
        this.sources.setItems(this.sourceRows);
        if (source != null) {
            for (int i = 0; i < this.sourceRows.size(); i++) {
                if (this.sourceRows.get(i).block() == source) {
                    this.sources.select(i);
                }
            }
        }
        int rightX = this.left + half + Theme.GAP;
        int rightWidth = this.width - half - Theme.GAP;
        EditBox search = field(rightX, listTop, rightWidth, query, tr("litematicaplus.replace.search"), 64);
        search.setResponder(value -> {
            query = value;
            updateCandidates(placement);
        });
        int candidatesTop = listTop + Theme.CONTROL_HEIGHT + 4;
        this.candidates = add(new ListPanel<Candidate>(rightX, candidatesTop, rightWidth,
                this.top + this.height - candidatesTop, Theme.ROW_HEIGHT, tr("litematicaplus.replace.candidates"),
                (graphics, rowFont, row, x, y, rowWidth, rowHeight, selected, hovered) ->
                        renderCandidate(graphics, rowFont, placement, row, x, y, rowWidth, rowHeight))
                .describe(row -> row.choice() == null ? tr("litematicaplus.replace.none").getString() : row.choice().name())
                .onPress(row -> apply(placement, row)));
        updateCandidates(placement);
    }

    private static String describeTarget(SchematicPlacement placement, Block block) {
        Block target = placement.replacements().get(block);
        return target == null ? "" : " -> " + target.getName().getString();
    }

    private List<Choice> sourceRows(SchematicPlacement placement) {
        Schematic schematic = placement.schematic();
        if (schematic == null) {
            return List.of();
        }
        if (schematic == this.indexedSchematic && !this.sourceRows.isEmpty()) {
            return this.sourceRows;
        }
        Set<Block> blocks = new LinkedHashSet<>();
        for (SchematicRegion region : schematic.regions().values()) {
            for (BlockState state : region.palette()) {
                if (replaceable(state.getBlock())) {
                    blocks.add(state.getBlock());
                }
            }
        }
        List<Choice> rows = new ArrayList<>();
        for (Block block : blocks) {
            rows.add(choice(block));
        }
        rows.sort(Comparator.comparing(Choice::name));
        this.indexedSchematic = schematic;
        return List.copyOf(rows);
    }

    private void updateCandidates(SchematicPlacement placement) {
        if (this.candidates == null) {
            return;
        }
        if (source == null || this.sourceRows.stream().noneMatch(row -> row.block() == source)) {
            this.candidates.setItems(List.of());
            this.candidates.empty(tr("litematicaplus.replace.pick_title"), tr("litematicaplus.replace.pick_hint"));
            return;
        }
        String needle = query.strip().toLowerCase(Locale.ROOT);
        StateComparator comparator = StateComparator.get();
        List<Candidate> similar = new ArrayList<>();
        List<Candidate> rest = new ArrayList<>();
        for (Choice choice : catalogue()) {
            if (choice.block() == source || !needle.isEmpty() && !choice.search().contains(needle)) {
                continue;
            }
            if (comparator.sameGroup(source, choice.block())) {
                similar.add(new Candidate(choice, true));
            } else {
                rest.add(new Candidate(choice, false));
            }
        }
        List<Candidate> rows = new ArrayList<>(similar.size() + rest.size() + 1);
        if (placement.replacements().containsKey(source)) {
            rows.add(new Candidate(null, false));
        }
        rows.addAll(similar);
        rows.addAll(rest);
        this.candidates.empty(tr("litematicaplus.replace.nothing_found"), tr("litematicaplus.replace.nothing_found_hint"));
        this.candidates.setItems(rows);
    }

    private void apply(SchematicPlacement placement, Candidate row) {
        if (source == null) {
            return;
        }
        Block target = row.choice() == null ? null : row.choice().block();
        PlacementManager.get().setReplacement(placement, source, target);
        this.status = target == null ? tr("litematicaplus.replace.removed", source.getName())
                : tr("litematicaplus.replace.applied", source.getName(), target.getName());
        updateCandidates(placement);
        this.screen.refresh();
    }

    private void renderSource(GuiGraphicsExtractor graphics, Font rowFont, Choice row, int x, int y, int rowWidth,
            int rowHeight, boolean selected, boolean hovered) {
        SchematicPlacement placement = PlacementManager.get().selected();
        Block target = placement == null ? null : placement.replacements().get(row.block());
        graphics.fakeItem(InfoHud.stackOf(row.block().asItem()), x + 5, y + 3);
        int textY = y + (rowHeight - 8) / 2;
        int right = x + rowWidth - 6;
        if (target != null) {
            Item item = target.asItem();
            String arrow = "-> " + target.getName().getString();
            int arrowWidth = Math.min(rowFont.width(arrow), (rowWidth - 30) / 2);
            Ui.clipped(graphics, rowFont, arrow, right - arrowWidth - 18, textY, arrowWidth, Theme.ACCENT);
            graphics.fakeItem(InfoHud.stackOf(item), right - 16, y + 3);
            Ui.clipped(graphics, rowFont, row.name(), x + 26, textY, rowWidth - 30 - arrowWidth - 26, Theme.TEXT_DIM);
        } else {
            Ui.clipped(graphics, rowFont, row.name(), x + 26, textY, rowWidth - 32, selected ? Theme.TEXT_BRIGHT : Theme.TEXT);
        }
    }

    private void renderCandidate(GuiGraphicsExtractor graphics, Font rowFont, SchematicPlacement placement, Candidate row,
            int x, int y, int rowWidth, int rowHeight) {
        int textY = y + (rowHeight - 8) / 2;
        int right = x + rowWidth - 6;
        if (row.choice() == null) {
            Ui.clipped(graphics, rowFont, tr("litematicaplus.replace.none").getString(), x + 26, textY, rowWidth - 32,
                    Theme.WARNING);
            return;
        }
        Block block = row.choice().block();
        graphics.fakeItem(InfoHud.stackOf(block.asItem()), x + 5, y + 3);
        String tag = placement.replacements().get(source) == block ? tr("litematicaplus.replace.current").getString()
                : row.similar() ? tr("litematicaplus.replace.similar").getString() : "";
        int tagWidth = tag.isEmpty() ? 0 : rowFont.width(tag) + 8;
        if (!tag.isEmpty()) {
            Ui.right(graphics, rowFont, tag, right, textY, placement.replacements().get(source) == block ? Theme.SUCCESS
                    : Theme.TEXT_DIM);
        }
        Ui.clipped(graphics, rowFont, row.choice().name(), x + 26, textY, rowWidth - 32 - tagWidth, Theme.TEXT);
    }

    @Override
    void extract(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        if (PlacementManager.get().selected() == null) {
            Ui.emptyState(graphics, this.font, this.left, this.top, this.width, this.height,
                    tr("litematicaplus.placements.empty_title"), tr("litematicaplus.replace.no_placement_hint"));
            return;
        }
        int half = (this.width - Theme.GAP) / 2;
        int headerY = this.top + Theme.CONTROL_HEIGHT + 5;
        graphics.text(this.font, tr("litematicaplus.replace.sources"), this.left + 2, headerY, Theme.ACCENT, false);
        Ui.clipped(graphics, this.font, source == null ? tr("litematicaplus.replace.candidates").getString()
                : tr("litematicaplus.replace.candidates_for", source.getName()).getString(),
                this.left + half + Theme.GAP + 2, headerY, this.width - half - Theme.GAP - 4, Theme.ACCENT);
    }
}
