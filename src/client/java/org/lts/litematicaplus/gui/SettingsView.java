package org.lts.litematicaplus.gui;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.function.IntConsumer;
import java.util.function.IntSupplier;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import org.lts.litematicaplus.LitematicaPlusConfig;

final class SettingsView extends TabView {
    private enum Category {
        RENDERING("rendering"),
        TOOLS("tools"),
        PERFORMANCE("performance");

        private final String key;

        Category(String key) {
            this.key = key;
        }
    }

    @FunctionalInterface
    private interface Row {
        void build(int x, int y, int width);
    }

    private static Category category = Category.RENDERING;

    private LitematicaPlusConfig working = LitematicaPlusConfig.currentCopy();
    private FlatButton saveButton;
    private FlatButton revertButton;
    private boolean dirty;
    private boolean toolItemValid = true;
    private int page;
    private int pages = 1;
    private Component status = Component.empty();
    private int statusColor = Theme.TEXT_DIM;

    SettingsView(HubScreen screen) {
        super(screen);
    }

    @Override
    Component summary() {
        return tr("litematicaplus.settings.summary");
    }

    @Override
    void init() {
        Category[] categories = Category.values();
        int tabWidth = (this.width - Theme.GAP * (categories.length - 1)) / categories.length;
        for (int i = 0; i < categories.length; i++) {
            Category entry = categories[i];
            add(new FlatButton(this.left + i * (tabWidth + Theme.GAP), this.top, tabWidth, Theme.CONTROL_HEIGHT,
                    tr("litematicaplus.settings.category." + entry.key), FlatButton.Style.SECONDARY, button -> {
                        category = entry;
                        this.page = 0;
                        this.screen.refresh();
                    }));
        }
        List<Row> rows = rows();
        int rowsTop = this.top + Theme.CONTROL_HEIGHT + 8;
        int bottomY = this.top + this.height - Theme.CONTROL_HEIGHT;
        int columns = this.width >= 380 ? 2 : 1;
        int columnWidth = (this.width - Theme.GAP * (columns - 1)) / columns;
        int visibleRows = Math.max(1, (bottomY - 6 - rowsTop) / (Theme.CONTROL_HEIGHT + 4));
        int perPage = visibleRows * columns;
        this.pages = Math.max(1, (rows.size() + perPage - 1) / perPage);
        this.page = Math.max(0, Math.min(this.page, this.pages - 1));
        int start = this.page * perPage;
        for (int i = start; i < Math.min(rows.size(), start + perPage); i++) {
            int slot = i - start;
            rows.get(i).build(this.left + slot % columns * (columnWidth + Theme.GAP),
                    rowsTop + slot / columns * (Theme.CONTROL_HEIGHT + 4), columnWidth);
        }
        FlatButton previous = add(FlatButton.secondary(this.left, bottomY, 24, Component.literal("<"), button -> {
            this.page--;
            this.screen.refresh();
        }));
        previous.active = this.page > 0;
        FlatButton next = add(FlatButton.secondary(this.left + 70, bottomY, 24, Component.literal(">"), button -> {
            this.page++;
            this.screen.refresh();
        }));
        next.active = this.page < this.pages - 1;
        int right = this.left + this.width;
        this.saveButton = add(FlatButton.primary(right - 90, bottomY, 90, tr("litematicaplus.button.save"), button -> save()));
        this.revertButton = add(FlatButton.secondary(right - 90 - Theme.GAP - 80, bottomY, 80,
                tr("litematicaplus.button.revert"), button -> {
                    this.working = LitematicaPlusConfig.currentCopy();
                    this.dirty = false;
                    this.toolItemValid = true;
                    this.status = Component.empty();
                    this.screen.refresh();
                }));
        updateSave();
    }

    private void updateSave() {
        if (this.saveButton != null) {
            this.saveButton.active = this.dirty && this.toolItemValid;
        }
        if (this.revertButton != null) {
            this.revertButton.active = this.dirty;
        }
    }

    private void changed() {
        this.dirty = true;
        this.status = Component.empty();
        updateSave();
    }

    private void save() {
        if (!this.toolItemValid) {
            return;
        }
        if (LitematicaPlusConfig.save(this.working)) {
            this.working = LitematicaPlusConfig.currentCopy();
            this.dirty = false;
            this.status = tr("litematicaplus.settings.saved");
            this.statusColor = Theme.SUCCESS;
        } else {
            this.status = tr("litematicaplus.error.save_failed");
            this.statusColor = Theme.DANGER;
        }
        this.screen.refresh();
    }

    private List<Row> rows() {
        List<Row> rows = new ArrayList<>();
        switch (category) {
            case RENDERING -> {
                rows.add(toggle("render_enabled", c -> c.renderEnabled, (c, v) -> c.renderEnabled = v));
                rows.add(toggle("render_ghost", c -> c.renderGhostBlocks, (c, v) -> c.renderGhostBlocks = v));
                rows.add(percent("ghost_alpha", () -> Math.round(this.working.ghostAlpha * 100.0F),
                        v -> this.working.ghostAlpha = v / 100.0F, 10));
                rows.add(toggle("render_overlay", c -> c.renderOverlay, (c, v) -> c.renderOverlay = v));
                rows.add(percent("overlay_fill_alpha", () -> Math.round(this.working.overlayFillAlpha * 100.0F),
                        v -> this.working.overlayFillAlpha = v / 100.0F, 0));
                rows.add(percent("overlay_line_alpha", () -> Math.round(this.working.overlayLineAlpha * 100.0F),
                        v -> this.working.overlayLineAlpha = v / 100.0F, 0));
                rows.add(toggle("overlay_xray", c -> c.overlayThroughWalls, (c, v) -> c.overlayThroughWalls = v));
                rows.add(toggle("render_colliding", c -> c.renderCollidingBlocks, (c, v) -> c.renderCollidingBlocks = v));
                rows.add(toggle("render_block_entities", c -> c.renderBlockEntities, (c, v) -> c.renderBlockEntities = v));
                rows.add(toggle("lenient_states", c -> c.lenientStates, (c, v) -> c.lenientStates = v));
                rows.add(toggle("placement_boxes", c -> c.renderPlacementBoxes, (c, v) -> c.renderPlacementBoxes = v));
                rows.add(toggle("selection_boxes", c -> c.renderSelectionBoxes, (c, v) -> c.renderSelectionBoxes = v));
                rows.add(number("render_distance", () -> this.working.renderDistance,
                        v -> this.working.renderDistance = v, 32, 1024, 16));
                rows.add(toggle("hud_enabled", c -> c.hudEnabled, (c, v) -> c.hudEnabled = v));
                rows.add(toggle("hud_right_side", c -> c.hudRightSide, (c, v) -> c.hudRightSide = v));
                rows.add(number("hud_material_lines", () -> this.working.hudMaterialLines,
                        v -> this.working.hudMaterialLines = v, 0, 16, 1));
            }
            case TOOLS -> {
                rows.add(this::toolItemRow);
                rows.add(this::authorRow);
                rows.add(toggle("easy_place", c -> c.easyPlaceEnabled, (c, v) -> c.easyPlaceEnabled = v));
                rows.add(toggle("easy_place_strict", c -> c.easyPlaceStrictOrientation,
                        (c, v) -> c.easyPlaceStrictOrientation = v));
                rows.add(toggle("easy_place_air", c -> c.easyPlaceAirPlacement, (c, v) -> c.easyPlaceAirPlacement = v));
                rows.add(number("easy_place_delay", () -> this.working.easyPlaceDelayTicks,
                        v -> this.working.easyPlaceDelayTicks = v, 0, 20, 1));
                rows.add(toggle("placement_restriction", c -> c.placementRestriction, (c, v) -> c.placementRestriction = v));
                rows.add(toggle("pick_block", c -> c.pickBlockEnabled, (c, v) -> c.pickBlockEnabled = v));
                rows.add(this::pasteReplaceRow);
                rows.add(toggle("paste_block_entities", c -> c.pasteBlockEntityData, (c, v) -> c.pasteBlockEntityData = v));
                rows.add(number("paste_commands", () -> this.working.pasteCommandsPerTick,
                        v -> this.working.pasteCommandsPerTick = v, 1, 400, 5));
            }
            case PERFORMANCE -> {
                rows.add((x, y, width) -> add(new Stepper(x, y, width, tr("litematicaplus.option.worker_threads"),
                        () -> this.working.workerThreads, v -> {
                            this.working.workerThreads = v;
                            changed();
                        }, 0, 16, 1).format(v -> v == 0
                                ? tr("litematicaplus.settings.auto", this.working.resolvedWorkerThreads()).getString()
                                : Integer.toString(v))));
                rows.add(number("mesh_uploads", () -> this.working.meshUploadsPerFrame,
                        v -> this.working.meshUploadsPerFrame = v, 1, 256, 4));
                rows.add(number("snapshot_budget", () -> this.working.snapshotBudgetMicros,
                        v -> this.working.snapshotBudgetMicros = v, 200, 10000, 100));
            }
        }
        return rows;
    }

    private Row toggle(String key, Function<LitematicaPlusConfig, Boolean> getter,
            BiConsumer<LitematicaPlusConfig, Boolean> setter) {
        return (x, y, width) -> add(FlatButton.toggle(x, y, width, tr("litematicaplus.option." + key),
                () -> getter.apply(this.working), button -> {
                    setter.accept(this.working, !getter.apply(this.working));
                    changed();
                }));
    }

    private Row number(String key, IntSupplier getter, IntConsumer setter, int min, int max, int step) {
        return (x, y, width) -> add(new Stepper(x, y, width, tr("litematicaplus.option." + key), getter, value -> {
            setter.accept(value);
            changed();
        }, min, max, step));
    }

    private Row percent(String key, IntSupplier getter, IntConsumer setter, int min) {
        return (x, y, width) -> add(new Stepper(x, y, width, tr("litematicaplus.option." + key), getter, value -> {
            setter.accept(value);
            changed();
        }, min, 100, 5).format(value -> value + "%"));
    }

    private void toolItemRow(int x, int y, int width) {
        int labelWidth = Math.min(width / 2, this.font.width(tr("litematicaplus.option.tool_item")) + 12);
        EditBox box = field(x + labelWidth, y, width - labelWidth, this.working.toolItem,
                tr("litematicaplus.option.tool_item"), 96);
        box.setTextColor(this.toolItemValid ? Theme.TEXT : Theme.DANGER);
        box.setResponder(value -> {
            String clean = value.strip().toLowerCase(Locale.ROOT);
            Identifier id = Identifier.tryParse(clean);
            boolean valid = id != null && BuiltInRegistries.ITEM.getOptional(id).isPresent();
            this.toolItemValid = valid;
            box.setTextColor(valid ? Theme.TEXT : Theme.DANGER);
            if (valid) {
                this.working.toolItem = id.toString();
                this.status = Component.empty();
            } else {
                this.status = tr("litematicaplus.error.tool_item");
                this.statusColor = Theme.DANGER;
            }
            this.dirty = true;
            updateSave();
        });
    }

    private void authorRow(int x, int y, int width) {
        int labelWidth = Math.min(width / 2, this.font.width(tr("litematicaplus.option.default_author")) + 12);
        EditBox box = field(x + labelWidth, y, width - labelWidth, this.working.defaultAuthor,
                tr("litematicaplus.option.default_author"), 64);
        box.setResponder(value -> {
            this.working.defaultAuthor = value;
            this.dirty = true;
            updateSave();
        });
    }

    private void pasteReplaceRow(int x, int y, int width) {
        add(FlatButton.secondary(x, y, width, tr("litematicaplus.option.paste_replace",
                tr("litematicaplus.paste_replace." + this.working.pasteReplace.name().toLowerCase(Locale.ROOT))), button -> {
                    this.working.pasteReplace = this.working.pasteReplace.next();
                    this.dirty = true;
                    this.screen.refresh();
                }));
    }

    @Override
    void extract(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        Category[] categories = Category.values();
        int tabWidth = (this.width - Theme.GAP * (categories.length - 1)) / categories.length;
        int markerX = this.left + category.ordinal() * (tabWidth + Theme.GAP);
        graphics.fill(markerX, this.top + Theme.CONTROL_HEIGHT + 1, markerX + tabWidth, this.top + Theme.CONTROL_HEIGHT + 3,
                Theme.ACCENT);
        int bottomY = this.top + this.height - Theme.CONTROL_HEIGHT;
        graphics.centeredText(this.font, (this.page + 1) + " / " + this.pages, this.left + 47, bottomY + 6, Theme.TEXT_DIM);
        if (category == Category.TOOLS && this.page == 0) {
            int rowsTop = this.top + Theme.CONTROL_HEIGHT + 8;
            int columns = this.width >= 380 ? 2 : 1;
            int columnWidth = (this.width - Theme.GAP * (columns - 1)) / columns;
            graphics.text(this.font, tr("litematicaplus.option.tool_item"), this.left, rowsTop + 6, Theme.ACCENT, false);
            int authorX = columns == 2 ? this.left + columnWidth + Theme.GAP : this.left;
            int authorY = columns == 2 ? rowsTop : rowsTop + Theme.CONTROL_HEIGHT + 4;
            graphics.text(this.font, tr("litematicaplus.option.default_author"), authorX, authorY + 6, Theme.ACCENT, false);
        }
        int statusX = this.left + 102;
        int statusWidth = this.width - 102 - 90 - Theme.GAP - 80 - 8;
        Ui.clipped(graphics, this.font, this.status.getString(), statusX, bottomY + 6, statusWidth, this.statusColor);
    }
}
