package org.lts.litematicaplus.gui;

import com.mojang.blaze3d.platform.InputConstants;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.BiConsumer;
import java.util.function.BooleanSupplier;
import java.util.function.Function;
import java.util.function.IntConsumer;
import java.util.function.IntSupplier;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.lts.litematicaplus.Hotkeys;
import org.lts.litematicaplus.LitematicaPlusConfig;
import org.lts.litematicaplus.compat.AxiomBridge;
import org.lts.litematicaplus.tool.Substitutes;

final class SettingsView extends TabView {
    private static final int HEADER_HEIGHT = 14;
    private static final int ROW_PITCH = Theme.CONTROL_HEIGHT + 4;
    private static final String[] HOTKEY_GROUPS = {"menus", "actions", "toggles", "other"};

    private enum Category {
        RENDERING("rendering"),
        VERIFY("verify"),
        HUD("hud"),
        TOOLS("tools"),
        EASY_PLACE("easy_place"),
        BREAKING("breaking"),
        MATERIALS("materials"),
        HOTKEYS("hotkeys"),
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

    private record Line(Component header, Row row, String search) {
    }

    private record Placed(int page, Line item, int x, int y, int width) {
    }

    private record Tab(Component label, Runnable select, BooleanSupplier active) {
    }

    private static Category category = Category.RENDERING;
    private static String extension;
    private static String query = "";

    private LitematicaPlusConfig working = LitematicaPlusConfig.currentCopy();
    private FlatButton saveButton;
    private FlatButton revertButton;
    private boolean dirty;
    private boolean toolItemValid = true;
    private String toolItemText = this.working.toolItem;
    private int page;
    private int pages = 1;
    private int optionsX;
    private int optionsWidth;
    private final List<Placed> headers = new ArrayList<>();
    private int toolItemX = -1;
    private int toolItemY;
    private int toolItemLabel;
    private int authorX = -1;
    private int authorY;
    private int authorLabel;
    private KeyMapping listening;
    private int listeningModifiers;
    private int pendingModifierKey = -1;
    private boolean bindingsChanged;
    private Component status = Component.empty();
    private int statusColor = Theme.TEXT_DIM;
    private EditBox searchBox;
    private int searchCursor = -1;
    private int matchCount;

    SettingsView(HubScreen screen) {
        super(screen);
    }

    @Override
    Component summary() {
        return searching() ? tr("litematicaplus.settings.search_count", this.matchCount)
                : tr("litematicaplus.settings.summary");
    }

    private static boolean searching() {
        return !query.isBlank();
    }

    @Override
    void init() {
        this.toolItemX = -1;
        this.authorX = -1;
        this.headers.clear();
        List<Tab> tabs = tabs();
        int labelWidth = 0;
        for (Tab tab : tabs) {
            labelWidth = Math.max(labelWidth, this.font.width(tab.label()));
        }
        int listWidth = Math.min(Math.max(64, labelWidth + 20), Math.max(64, this.width * 30 / 100));
        String previousQuery = query;
        this.searchBox = field(this.left, this.top, listWidth, query, tr("litematicaplus.settings.search"), 48);
        if (this.searchCursor >= 0) {
            this.searchBox.setCursorPosition(Math.min(this.searchCursor, query.length()));
            this.searchBox.setHighlightPos(this.searchBox.getCursorPosition());
        }
        this.searchBox.setResponder(value -> {
            if (value.equals(previousQuery)) {
                return;
            }
            query = value;
            this.page = 0;
            this.listening = null;
            this.searchCursor = this.searchBox.getCursorPosition();
            this.screen.rebuildNow(() -> this.searchBox);
        });
        hint(this.searchBox, "litematicaplus.settings.search_tip");
        int listTop = this.top + Theme.CONTROL_HEIGHT + 6;
        int listHeight = this.height - (listTop - this.top);
        int pitch = Math.max(13, Math.min(Theme.CONTROL_HEIGHT + 2, (listHeight + 2) / tabs.size()));
        int tabY = listTop;
        for (Tab tab : tabs) {
            FlatButton button = add(new FlatButton(this.left, tabY, listWidth, pitch - 2, tab.label(), FlatButton.Style.TAB,
                    pressed -> {
                        tab.select().run();
                        query = "";
                        this.searchCursor = -1;
                        this.page = 0;
                        this.listening = null;
                        this.screen.refresh();
                    }).withState(() -> !searching() && tab.active().getAsBoolean()));
            if (this.font.width(tab.label()) <= listWidth - 15) {
                button.setTooltip(null);
            }
            tabY += pitch;
        }
        this.optionsX = this.left + listWidth + 10;
        this.optionsWidth = this.left + this.width - this.optionsX;
        int bottomY = this.top + this.height - Theme.CONTROL_HEIGHT;
        List<Line> lines = rows();
        this.matchCount = 0;
        for (Line line : lines) {
            if (line.header() == null) {
                this.matchCount++;
            }
        }
        List<Placed> placed = layout(lines, this.optionsX, this.optionsWidth, this.top, bottomY - 14);
        this.pages = 1;
        for (Placed entry : placed) {
            this.pages = Math.max(this.pages, entry.page() + 1);
        }
        this.page = Math.max(0, Math.min(this.page, this.pages - 1));
        for (Placed entry : placed) {
            if (entry.page() != this.page) {
                continue;
            }
            if (entry.item().header() != null) {
                this.headers.add(entry);
            } else {
                entry.item().row().build(entry.x(), entry.y(), entry.width());
            }
        }
        if (this.pages > 1) {
            FlatButton previous = add(FlatButton.secondary(this.optionsX, bottomY, 24, Component.literal("<"), button -> {
                this.page--;
                this.screen.refresh();
            }));
            previous.active = this.page > 0;
            FlatButton next = add(FlatButton.secondary(this.optionsX + 70, bottomY, 24, Component.literal(">"), button -> {
                this.page++;
                this.screen.refresh();
            }));
            next.active = this.page < this.pages - 1;
        }
        int right = this.left + this.width;
        int pager = this.pages > 1 ? 94 + Theme.GAP : 0;
        int room = this.optionsWidth - pager - Theme.GAP;
        int saveWidth = Math.max(48, Math.min(90, room * 9 / 17));
        int revertWidth = Math.max(44, Math.min(80, room - saveWidth));
        this.saveButton = add(FlatButton.primary(right - saveWidth, bottomY, saveWidth, tr("litematicaplus.button.save"),
                button -> save()));
        this.revertButton = add(FlatButton.secondary(right - saveWidth - Theme.GAP - revertWidth, bottomY, revertWidth,
                tr("litematicaplus.button.revert"), button -> {
                    this.working = LitematicaPlusConfig.currentCopy();
                    this.dirty = false;
                    this.toolItemValid = true;
                    this.toolItemText = this.working.toolItem;
                    this.status = Component.empty();
                    this.screen.refresh();
                }));
        updateSave();
    }

    private static List<Placed> layout(List<Line> items, int x, int width, int top, int limit) {
        int columns = width >= 380 ? 2 : 1;
        int columnWidth = (width - Theme.GAP * (columns - 1)) / columns;
        List<Placed> placed = new ArrayList<>();
        int page = 0;
        int y = top;
        int column = 0;
        Line section = null;
        for (Line item : items) {
            if (item.header() != null) {
                if (column > 0) {
                    column = 0;
                    y += ROW_PITCH;
                }
                if (y > top && y + HEADER_HEIGHT + Theme.CONTROL_HEIGHT > limit) {
                    page++;
                    y = top;
                }
                section = item;
                placed.add(new Placed(page, item, x, y, width));
                y += HEADER_HEIGHT;
                continue;
            }
            if (column == 0 && y > top && y + Theme.CONTROL_HEIGHT > limit) {
                page++;
                y = top;
                if (section != null) {
                    placed.add(new Placed(page, section, x, y, width));
                    y += HEADER_HEIGHT;
                }
            }
            placed.add(new Placed(page, item, x + column * (columnWidth + Theme.GAP), y, columnWidth));
            if (++column == columns) {
                column = 0;
                y += ROW_PITCH;
            }
        }
        return placed;
    }

    private static List<Tab> tabs() {
        List<Tab> tabs = new ArrayList<>();
        for (Category entry : Category.values()) {
            tabs.add(new Tab(tr("litematicaplus.settings.category." + entry.key), () -> {
                category = entry;
                extension = null;
            }, () -> extension == null && category == entry));
        }
        List<SettingsExtensions.Page> pages = SettingsExtensions.pages();
        if (extension != null && pages.stream().noneMatch(page -> page.key().equals(extension))) {
            extension = null;
        }
        for (SettingsExtensions.Page entry : pages) {
            tabs.add(new Tab(tr("litematicaplus.settings.category." + entry.key()), () -> extension = entry.key(),
                    () -> entry.key().equals(extension)));
        }
        return tabs;
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
        this.status = tr("litematicaplus.settings.unsaved");
        this.statusColor = Theme.WARNING;
        updateSave();
    }

    @Override
    void closed() {
        if (this.dirty) {
            LitematicaPlusConfig.save(this.working);
            this.dirty = false;
        }
        if (this.bindingsChanged) {
            this.bindingsChanged = false;
            this.minecraft.options.save();
        }
    }

    private void save() {
        if (!this.toolItemValid) {
            return;
        }
        LitematicaPlusConfig.save(this.working).thenAccept(written -> this.minecraft.execute(() -> saved(written)));
        this.working = LitematicaPlusConfig.currentCopy();
        this.toolItemText = this.working.toolItem;
        this.dirty = false;
        this.status = Component.empty();
        this.screen.refresh();
    }

    private void saved(boolean written) {
        if (!written) {
            this.dirty = true;
            this.status = tr("litematicaplus.settings.save_failed");
            this.statusColor = Theme.DANGER;
        } else if (!this.dirty) {
            this.status = tr("litematicaplus.settings.saved");
            this.statusColor = Theme.SUCCESS;
        }
        updateSave();
    }

    private static Line header(String key) {
        Component title = tr("litematicaplus.settings.section." + key);
        return new Line(title, null, title.getString());
    }

    private static Line row(String key, Row row) {
        return new Line(null, row, searchText(key));
    }

    private static Line row(Component label, Row row) {
        return new Line(null, row, label.getString());
    }

    private static String searchText(String key) {
        String option = "litematicaplus.option." + key;
        String tip = option + ".tip";
        Language language = Language.getInstance();
        StringBuilder text = new StringBuilder(language.getOrDefault(option).replaceAll("%(\\d+\\$)?s", ""));
        if (language.has(tip)) {
            text.append(' ').append(language.getOrDefault(tip));
        }
        return text.append(' ').append(key.replace('_', ' ')).toString();
    }

    static boolean matches(String haystack, String needle) {
        String lower = haystack.toLowerCase(Locale.ROOT);
        for (String word : needle.toLowerCase(Locale.ROOT).trim().split("\\s+")) {
            if (!word.isEmpty() && !lower.contains(word)) {
                return false;
            }
        }
        return true;
    }

    private Line toggle(String key, Function<LitematicaPlusConfig, Boolean> getter,
            BiConsumer<LitematicaPlusConfig, Boolean> setter) {
        return row(key, (x, y, width) -> describe(add(FlatButton.toggle(x, y, width, tr("litematicaplus.option." + key),
                () -> getter.apply(this.working), button -> {
                    setter.accept(this.working, !getter.apply(this.working));
                    changed();
                })), key));
    }

    private List<Line> rows() {
        if (searching()) {
            return searchRows();
        }
        if (extension != null) {
            return extensionRows(extension);
        }
        return categoryRows(category);
    }

    private List<Line> extensionRows(String key) {
        List<Line> rows = new ArrayList<>();
        for (SettingsExtensions.Page entry : SettingsExtensions.pages()) {
            if (entry.key().equals(key)) {
                for (SettingsExtensions.Option option : entry.options().get()) {
                    rows.add(row(optionKey(option), extensionRow(option)));
                }
            }
        }
        return rows;
    }

    private static String optionKey(SettingsExtensions.Option option) {
        return switch (option) {
            case SettingsExtensions.Toggle toggle -> toggle.key();
            case SettingsExtensions.Number number -> number.key();
        };
    }

    private List<Line> searchRows() {
        List<Line> found = new ArrayList<>();
        for (Category entry : Category.values()) {
            collect(found, tr("litematicaplus.settings.category." + entry.key), categoryRows(entry));
        }
        for (SettingsExtensions.Page entry : SettingsExtensions.pages()) {
            collect(found, tr("litematicaplus.settings.category." + entry.key()), extensionRows(entry.key()));
        }
        return found;
    }

    private static void collect(List<Line> found, Component categoryTitle, List<Line> rows) {
        boolean categoryHit = matches(categoryTitle.getString(), query);
        boolean sectionHit = false;
        Component section = null;
        boolean opened = false;
        for (Line line : rows) {
            if (line.header() != null) {
                section = line.header();
                sectionHit = matches(line.search(), query);
                continue;
            }
            if (!categoryHit && !sectionHit && !matches(line.search(), query)) {
                continue;
            }
            if (!opened) {
                MutableComponent title = categoryTitle.copy();
                if (section != null && !categoryHit) {
                    title.append(" / ").append(section);
                }
                found.add(new Line(title, null, title.getString()));
                opened = true;
            }
            found.add(line);
        }
    }

    private List<Line> categoryRows(Category target) {
        List<Line> rows = new ArrayList<>();
        switch (target) {
            case RENDERING -> {
                rows.add(header("schematic"));
                rows.add(toggle("render_enabled", c -> c.renderEnabled, (c, v) -> c.renderEnabled = v));
                rows.add(toggle("render_ghost", c -> c.renderGhostBlocks, (c, v) -> c.renderGhostBlocks = v));
                rows.add(toggle("ghost_errors_only", c -> c.ghostErrorsOnly, (c, v) -> c.ghostErrorsOnly = v));
                rows.add(toggle("ghosts_loaded_only", c -> c.ghostsOnlyLoaded, (c, v) -> c.ghostsOnlyLoaded = v));
                rows.add(toggle("render_block_entities", c -> c.renderBlockEntities, (c, v) -> c.renderBlockEntities = v));
                rows.add(toggle("render_colliding", c -> c.renderCollidingBlocks, (c, v) -> c.renderCollidingBlocks = v));
                rows.add(percent("ghost_alpha", () -> Math.round(this.working.ghostAlpha * 100.0F),
                        v -> this.working.ghostAlpha = v / 100.0F, 20));
                rows.add(color("ghost_color", c -> c.ghostColor, (c, v) -> c.ghostColor = v));
                rows.add(number("render_distance", () -> this.working.renderDistance,
                        v -> this.working.renderDistance = v, 32, 2048, 32));
                rows.add(header("errors"));
                rows.add(toggle("render_overlay", c -> c.renderOverlay, (c, v) -> c.renderOverlay = v));
                rows.add(toggle("overlay_xray", c -> c.overlayThroughWalls, (c, v) -> c.overlayThroughWalls = v));
                rows.add(percent("overlay_fill_alpha", () -> Math.round(this.working.overlayFillAlpha * 100.0F),
                        v -> this.working.overlayFillAlpha = v / 100.0F, 0));
                rows.add(percent("overlay_line_alpha", () -> Math.round(this.working.overlayLineAlpha * 100.0F),
                        v -> this.working.overlayLineAlpha = v / 100.0F, 0));
                rows.add(color("missing_color", c -> c.missingColor, (c, v) -> c.missingColor = v));
                rows.add(color("wrong_block_color", c -> c.wrongBlockColor, (c, v) -> c.wrongBlockColor = v));
                rows.add(color("wrong_state_color", c -> c.wrongStateColor, (c, v) -> c.wrongStateColor = v));
                rows.add(color("extra_color", c -> c.extraColor, (c, v) -> c.extraColor = v));
                rows.add(row("colors_reset", this::colorsResetRow));
                rows.add(toggle("inventory_highlight", c -> c.inventoryHighlight, (c, v) -> c.inventoryHighlight = v));
                rows.add(header("frames"));
                rows.add(toggle("placement_boxes", c -> c.renderPlacementBoxes, (c, v) -> c.renderPlacementBoxes = v));
                rows.add(toggle("selection_boxes", c -> c.renderSelectionBoxes, (c, v) -> c.renderSelectionBoxes = v));
            }
            case VERIFY -> {
                rows.add(header("rules"));
                rows.add(toggle("lenient_states", c -> c.lenientStates, (c, v) -> c.lenientStates = v));
                rows.add(toggle("loose_blocks", c -> c.looseBlocks, (c, v) -> c.looseBlocks = v));
                rows.add(toggle("soil_interchangeable", c -> c.soilInterchangeable, (c, v) -> c.soilInterchangeable = v));
                rows.add(toggle("trust_hidden", c -> c.trustHiddenBlocks, (c, v) -> c.trustHiddenBlocks = v));
                rows.add(toggle("material_swap", c -> c.materialSwap, (c, v) -> c.materialSwap = v));
                rows.add(header("scope"));
                rows.add(toggle("count_visible_only", c -> c.countVisibleOnly, (c, v) -> c.countVisibleOnly = v));
                rows.add(toggle("world_cache", c -> c.useWorldCache, (c, v) -> c.useWorldCache = v));
            }
            case HUD -> {
                rows.add(toggle("hud_enabled", c -> c.hudEnabled, (c, v) -> c.hudEnabled = v));
                rows.add(row("hud_position", this::hudPositionRow));
                rows.add(number("hud_material_lines", () -> this.working.hudMaterialLines,
                        v -> this.working.hudMaterialLines = v, 0, 16, 1));
                rows.add(toggle("note_tuning_hud", c -> c.noteTuningHud, (c, v) -> c.noteTuningHud = v));
                rows.add(toggle("hud_diagnostics", c -> c.hudDiagnostics, (c, v) -> c.hudDiagnostics = v));
            }
            case TOOLS -> {
                rows.add(header("tool"));
                rows.add(toggle("tool_enabled", c -> c.toolEnabled, (c, v) -> c.toolEnabled = v));
                rows.add(row("tool_item", this::toolItemRow));
                rows.add(toggle("pick_block", c -> c.pickBlockEnabled, (c, v) -> c.pickBlockEnabled = v));
                rows.add(header("paste"));
                rows.add(row("paste_replace", this::pasteReplaceRow));
                rows.add(number("paste_commands", () -> this.working.pasteCommandsPerTick,
                        v -> this.working.pasteCommandsPerTick = v, 1, 16, 1));
                rows.add(toggle("paste_block_entities", c -> c.pasteBlockEntityData, (c, v) -> c.pasteBlockEntityData = v));
                rows.add(toggle("paste_entities", c -> c.pasteEntities, (c, v) -> c.pasteEntities = v));
                rows.add(header("schematics"));
                rows.add(toggle("schematic_preview", c -> c.schematicPreview, (c, v) -> c.schematicPreview = v));
                rows.add(toggle("preview_models", c -> c.previewModels, (c, v) -> c.previewModels = v));
                rows.add(row("default_author", this::authorRow));
                rows.add(header("together"));
                rows.add(toggle("syncmatica", c -> c.syncmaticaEnabled, (c, v) -> c.syncmaticaEnabled = v));
                rows.add(toggle("zone_hide_messages", c -> c.zoneHideMessages, (c, v) -> c.zoneHideMessages = v));
                if (AxiomBridge.installed()) {
                    rows.add(header("axiom"));
                    rows.add(toggle("axiom_editor_pause", c -> c.axiomEditorPause, (c, v) -> c.axiomEditorPause = v));
                }
            }
            case EASY_PLACE -> {
                rows.add(header("basics"));
                rows.add(toggle("easy_place", c -> c.easyPlaceEnabled, (c, v) -> c.easyPlaceEnabled = v));
                rows.add(toggle("easy_place_hold", c -> c.easyPlaceHold, (c, v) -> c.easyPlaceHold = v));
                rows.add(row("easy_place_preset", this::presetRow));
                rows.add(number("easy_place_delay", () -> this.working.easyPlaceDelayTicks,
                        v -> this.working.easyPlaceDelayTicks = v, 0, 20, 1));
                rows.add(toggle("easy_place_jitter", c -> c.easyPlaceJitter, (c, v) -> c.easyPlaceJitter = v));
                rows.add(toggle("easy_place_restrict", c -> c.easyPlaceRestrictArea, (c, v) -> c.easyPlaceRestrictArea = v));
                rows.add(toggle("placement_restriction", c -> c.placementRestriction, (c, v) -> c.placementRestriction = v));
                rows.add(toggle("easy_place_interaction", c -> c.easyPlaceAllowInteraction,
                        (c, v) -> c.easyPlaceAllowInteraction = v));
                rows.add(toggle("easy_place_diagnostics", c -> c.easyPlaceDiagnostics, (c, v) -> c.easyPlaceDiagnostics = v));
                rows.add(header("rotation"));
                rows.add(toggle("easy_place_fake_rotation", c -> c.easyPlaceFakeRotation,
                        (c, v) -> c.easyPlaceFakeRotation = v));
                rows.add(number("easy_place_lead", () -> this.working.easyPlaceRotationLeadTicks,
                        v -> this.working.easyPlaceRotationLeadTicks = v, 0, 3, 1));
                rows.add(toggle("easy_place_revert", c -> c.easyPlaceRotationRevert, (c, v) -> c.easyPlaceRotationRevert = v));
                rows.add(toggle("easy_place_strict", c -> c.easyPlaceStrictOrientation,
                        (c, v) -> c.easyPlaceStrictOrientation = v));
                rows.add(toggle("easy_place_air", c -> c.easyPlaceAirPlacement, (c, v) -> c.easyPlaceAirPlacement = v));
                rows.add(header("states"));
                rows.add(toggle("easy_place_finish", c -> c.easyPlaceFinishStates, (c, v) -> c.easyPlaceFinishStates = v));
                rows.add(toggle("easy_place_ignite", c -> c.easyPlaceIgnite, (c, v) -> c.easyPlaceIgnite = v));
                rows.add(toggle("easy_place_waterlog", c -> c.easyPlaceWaterlog, (c, v) -> c.easyPlaceWaterlog = v));
                rows.add(toggle("easy_place_observer", c -> c.easyPlaceObserverDetect,
                        (c, v) -> c.easyPlaceObserverDetect = v));
                rows.add(toggle("entity_placement", c -> c.entityPlacement, (c, v) -> c.entityPlacement = v));
                rows.add(header("items"));
                rows.add(toggle("easy_place_ignore_data", c -> c.easyPlaceIgnoreItemData,
                        (c, v) -> c.easyPlaceIgnoreItemData = v));
                rows.add(toggle("easy_place_substitutes", c -> c.easyPlaceSubstitutes, (c, v) -> c.easyPlaceSubstitutes = v));
                rows.add(new Line(null, this::substitutesRow,
                        searchText("substitutes_add") + " " + searchText("substitutes_clear")));
                rows.add(row("locked_slots", this::lockedSlotsRow));
            }
            case BREAKING -> {
                rows.add(header("guard"));
                rows.add(toggle("excavation_guard", c -> c.excavationGuard, (c, v) -> c.excavationGuard = v));
                rows.add(toggle("excavation_guard_outside", c -> c.excavationGuardOutside,
                        (c, v) -> c.excavationGuardOutside = v));
                rows.add(toggle("excavation_guard_correct", c -> c.excavationGuardCorrect,
                        (c, v) -> c.excavationGuardCorrect = v));
                rows.add(toggle("excavation_guard_hint", c -> c.excavationGuardHint, (c, v) -> c.excavationGuardHint = v));
                rows.add(toggle("tool_use_guard", c -> c.toolUseGuard, (c, v) -> c.toolUseGuard = v));
                rows.add(header("assist"));
                rows.add(toggle("auto_tool_switch", c -> c.autoToolSwitch, (c, v) -> c.autoToolSwitch = v));
                rows.add(toggle("break_wrong_blocks", c -> c.breakWrongBlocks, (c, v) -> c.breakWrongBlocks = v));
                rows.add(toggle("terrain_auto_replace", c -> c.terrainAutoReplace, (c, v) -> c.terrainAutoReplace = v));
            }
            case MATERIALS -> {
                rows.add(header("helper"));
                rows.add(toggle("material_helper", c -> c.materialHelper, (c, v) -> c.materialHelper = v));
                rows.add(toggle("material_panel", c -> c.materialPanel, (c, v) -> c.materialPanel = v));
                rows.add(toggle("material_exact", c -> c.materialExactTake, (c, v) -> c.materialExactTake = v));
                rows.add(toggle("material_tooltips", c -> c.materialTooltips, (c, v) -> c.materialTooltips = v));
                rows.add(toggle("material_follows_render", c -> c.materialHelperFollowsRender,
                        (c, v) -> c.materialHelperFollowsRender = v));
                rows.add(header("sources"));
                rows.add(toggle("ender_chest_materials", c -> c.enderChestMaterials, (c, v) -> c.enderChestMaterials = v));
                rows.add(toggle("container_memory", c -> c.containerMemory, (c, v) -> c.containerMemory = v));
                rows.add(toggle("container_highlight", c -> c.containerHighlight, (c, v) -> c.containerHighlight = v));
            }
            case HOTKEYS -> {
                for (String group : HOTKEY_GROUPS) {
                    boolean first = true;
                    for (Hotkeys.Entry entry : Hotkeys.entries()) {
                        if (!hotkeyGroup(entry.name()).equals(group)) {
                            continue;
                        }
                        if (first) {
                            rows.add(header(group));
                            first = false;
                        }
                        rows.add(row(Component.translatable(entry.mapping().getName()),
                                (x, y, width) -> hotkeyRow(entry, x, y, width)));
                    }
                }
            }
            case PERFORMANCE -> {
                rows.add(row("worker_threads", (x, y, width) -> {
                    Stepper stepper = add(new Stepper(x, y, width, tr("litematicaplus.option.worker_threads"),
                            () -> this.working.workerThreads, v -> {
                                this.working.workerThreads = v;
                                changed();
                            }, 0, 16, 1).format(v -> v == 0
                                    ? tr("litematicaplus.settings.auto", this.working.resolvedWorkerThreads()).getString()
                                    : Integer.toString(v)));
                    describe(stepper, "worker_threads");
                }));
                rows.add(number("mesh_uploads", () -> this.working.meshUploadsPerFrame,
                        v -> this.working.meshUploadsPerFrame = v, 1, 256, 4));
                rows.add(number("snapshot_budget", () -> this.working.snapshotBudgetMicros,
                        v -> this.working.snapshotBudgetMicros = v, 200, 10000, 100));
            }
        }
        return rows;
    }

    private static String hotkeyGroup(String name) {
        return switch (name) {
            case "open_menu", "edit_placement", "open_layers", "open_materials" -> "menus";
            case "tool_mode", "layer_mode", "layer_up", "layer_down", "toggle_clip", "take_materials", "hide_ghost",
                    "show_hidden_ghosts" -> "actions";
            default -> name.startsWith("toggle_") ? "toggles" : "other";
        };
    }

    private static void describe(net.minecraft.client.gui.components.AbstractWidget widget, String key) {
        String tip = "litematicaplus.option." + key + ".tip";
        if (Language.getInstance().has(tip)) {
            widget.setTooltip(Tooltip.create(tr("litematicaplus.option." + key).copy().append("\n").append(tr(tip))));
        }
    }

    private Row extensionRow(SettingsExtensions.Option option) {
        return switch (option) {
            case SettingsExtensions.Toggle toggle -> (x, y, width) -> describe(add(FlatButton.toggle(x, y, width,
                    tr("litematicaplus.option." + toggle.key()), toggle.getter(), button -> {
                        toggle.setter().accept(!toggle.getter().getAsBoolean());
                        this.screen.refresh();
                    })), toggle.key());
            case SettingsExtensions.Number number -> (x, y, width) -> describe(add(new Stepper(x, y, width,
                    tr("litematicaplus.option." + number.key()), number.getter(), number.setter(), number.min(),
                    number.max(), number.step())), number.key());
        };
    }

    private Line number(String key, IntSupplier getter, IntConsumer setter, int min, int max, int step) {
        return row(key, (x, y, width) -> describe(add(new Stepper(x, y, width, tr("litematicaplus.option." + key), getter,
                value -> {
                    setter.accept(value);
                    changed();
                }, min, max, step)), key));
    }

    private Line percent(String key, IntSupplier getter, IntConsumer setter, int min) {
        return row(key, (x, y, width) -> describe(add(new Stepper(x, y, width, tr("litematicaplus.option." + key), getter,
                value -> {
                    setter.accept(value);
                    changed();
                }, min, 100, 5).format(value -> value + "%")), key));
    }

    private Line color(String key, Function<LitematicaPlusConfig, String> getter,
            BiConsumer<LitematicaPlusConfig, String> setter) {
        return row(key, (x, y, width) -> {
            int fieldWidth = width >= 250 ? 52 : 0;
            int stripWidth = fieldWidth == 0 ? width : width - fieldWidth - Theme.GAP;
            IntSupplier current = () -> LitematicaPlusConfig.parseColor(getter.apply(this.working), 0xFFFFFF);
            describe(add(new ColorStrip(x, y, stripWidth, tr("litematicaplus.option." + key), current, rgb -> {
                setter.accept(this.working, LitematicaPlusConfig.hex(rgb));
                changed();
                this.screen.refresh();
            })), key);
            if (fieldWidth == 0) {
                return;
            }
            EditBox box = field(x + stripWidth + Theme.GAP, y, fieldWidth, getter.apply(this.working).substring(1),
                    tr("litematicaplus.option." + key), 7);
            box.setTooltip(Tooltip.create(tr("litematicaplus.settings.color_field")));
            box.setResponder(value -> {
                int parsed = LitematicaPlusConfig.parseColor(value, -1);
                box.setTextColor(parsed < 0 ? Theme.DANGER : Theme.TEXT);
                if (parsed >= 0) {
                    setter.accept(this.working, LitematicaPlusConfig.hex(parsed));
                    changed();
                }
            });
        });
    }

    private void colorsResetRow(int x, int y, int width) {
        LitematicaPlusConfig defaults = new LitematicaPlusConfig();
        boolean custom = !defaults.ghostColor.equals(this.working.ghostColor)
                || !defaults.missingColor.equals(this.working.missingColor)
                || !defaults.extraColor.equals(this.working.extraColor)
                || !defaults.wrongBlockColor.equals(this.working.wrongBlockColor)
                || !defaults.wrongStateColor.equals(this.working.wrongStateColor);
        FlatButton reset = add(FlatButton.secondary(x, y, width, tr("litematicaplus.option.colors_reset"), button -> {
            this.working.ghostColor = defaults.ghostColor;
            this.working.missingColor = defaults.missingColor;
            this.working.extraColor = defaults.extraColor;
            this.working.wrongBlockColor = defaults.wrongBlockColor;
            this.working.wrongStateColor = defaults.wrongStateColor;
            changed();
            this.screen.refresh();
        }));
        reset.active = custom;
        describe(reset, "colors_reset");
    }

    private void presetRow(int x, int y, int width) {
        LitematicaPlusConfig.PlacementPreset preset = this.working.easyPlacePreset;
        describe(add(FlatButton.secondary(x, y, width, tr("litematicaplus.option.easy_place_preset",
                tr("litematicaplus.preset." + preset.name().toLowerCase(Locale.ROOT)),
                preset.ticks(this.working.easyPlaceDelayTicks)), button -> {
                    this.working.easyPlacePreset = this.working.easyPlacePreset.next();
                    changed();
                    this.screen.refresh();
                })), "easy_place_preset");
    }

    private void substitutesRow(int x, int y, int width) {
        int half = (width - Theme.GAP) / 2;
        FlatButton addHeld = add(FlatButton.secondary(x, y, half, tr("litematicaplus.option.substitutes_add",
                Substitutes.size()), button -> {
                    Item held = this.minecraft.player == null ? Items.AIR : this.minecraft.player.getMainHandItem().getItem();
                    if (Substitutes.add(held)) {
                        this.status = tr("litematicaplus.settings.substitute_added", new ItemStack(held).getHoverName());
                        this.statusColor = Theme.SUCCESS;
                    } else {
                        this.status = tr("litematicaplus.settings.substitute_rejected");
                        this.statusColor = Theme.WARNING;
                    }
                    this.screen.refresh();
                }));
        addHeld.active = this.minecraft.player != null;
        describe(addHeld, "substitutes_add");
        FlatButton clear = add(FlatButton.danger(x + half + Theme.GAP, y, width - half - Theme.GAP,
                tr("litematicaplus.option.substitutes_clear"), button -> {
                    Substitutes.clear();
                    this.status = tr("litematicaplus.settings.substitutes_cleared");
                    this.statusColor = Theme.TEXT_DIM;
                    this.screen.refresh();
                }));
        clear.active = Substitutes.size() > 0;
    }

    private void hotkeyRow(Hotkeys.Entry entry, int x, int y, int width) {
        KeyMapping mapping = entry.mapping();
        boolean active = this.listening == mapping;
        int modifiers = Hotkeys.modifiers(entry.name());
        Component key = active ? Hotkeys.label(this.listeningModifiers, tr("litematicaplus.hotkeys.listening"))
                : mapping.isUnbound() ? tr("litematicaplus.hotkeys.unbound")
                : Hotkeys.label(modifiers, mapping.getTranslatedKeyMessage());
        List<Component> conflicts = new ArrayList<>();
        if (!mapping.isUnbound()) {
            for (KeyMapping other : this.minecraft.options.keyMappings) {
                if (other == mapping || !mapping.same(other)) {
                    continue;
                }
                Hotkeys.Entry ours = Hotkeys.entry(other);
                if (ours != null ? Hotkeys.modifiers(ours.name()) == modifiers : modifiers == 0) {
                    conflicts.add(Component.translatable(other.getName()));
                }
            }
        }
        FlatButton button = add(FlatButton.secondary(x, y, width,
                tr("litematicaplus.hotkeys.row", Component.translatable(mapping.getName()), key), pressed -> {
                    this.listening = active ? null : mapping;
                    this.listeningModifiers = 0;
                    this.pendingModifierKey = -1;
                    this.screen.refresh();
                }).withState(() -> this.listening == mapping).withWarning(() -> !conflicts.isEmpty()));
        MutableComponent tip = tr("litematicaplus.hotkeys.tip").copy();
        if (!conflicts.isEmpty()) {
            MutableComponent names = Component.empty();
            for (int i = 0; i < Math.min(4, conflicts.size()); i++) {
                names.append(i == 0 ? "" : ", ").append(conflicts.get(i));
            }
            tip.append("\n").append(tr("litematicaplus.hotkeys.conflict", names));
        }
        button.setTooltip(Tooltip.create(tip));
    }

    private void bind(InputConstants.Key key, int modifiers) {
        this.listening.setKey(key);
        KeyMapping.resetMapping();
        Hotkeys.Entry entry = Hotkeys.entry(this.listening);
        if (entry != null) {
            int mask = key == InputConstants.UNKNOWN ? 0 : modifiers;
            Hotkeys.setModifiers(entry.name(), mask);
            Hotkeys.apply(this.working, entry.name(), mask);
        }
        this.listening = null;
        this.listeningModifiers = 0;
        this.pendingModifierKey = -1;
        this.bindingsChanged = true;
        this.screen.refresh();
    }

    @Override
    boolean keyPressed(KeyEvent event) {
        if (this.listening == null) {
            return searchKey(event);
        }
        if (event.key() == InputConstants.KEY_ESCAPE) {
            bind(InputConstants.UNKNOWN, 0);
            return true;
        }
        int bit = Hotkeys.modifierBit(event.key());
        if (bit != 0) {
            this.listeningModifiers |= bit;
            this.pendingModifierKey = event.key();
            this.screen.refresh();
            return true;
        }
        bind(InputConstants.getKey(event), Hotkeys.modifiersOf(event));
        return true;
    }

    private boolean searchKey(KeyEvent event) {
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
            this.screen.setFocused(null);
            return event.key() == InputConstants.KEY_RETURN;
        }
        return false;
    }

    @Override
    boolean keyReleased(KeyEvent event) {
        if (this.listening == null || event.key() != this.pendingModifierKey) {
            return false;
        }
        bind(InputConstants.getKey(event), this.listeningModifiers & ~Hotkeys.modifierBit(event.key()));
        return true;
    }

    @Override
    boolean mouseClicked(MouseButtonEvent event) {
        if (this.listening == null) {
            return false;
        }
        if (event.button() == InputConstants.MOUSE_BUTTON_LEFT) {
            this.listening = null;
            this.listeningModifiers = 0;
            this.pendingModifierKey = -1;
            this.screen.refresh();
            return true;
        }
        bind(InputConstants.Type.MOUSE.getOrCreate(event.button()), Hotkeys.heldModifiers());
        return true;
    }

    private void toolItemRow(int x, int y, int width) {
        int labelWidth = Math.min(width / 2, this.font.width(tr("litematicaplus.option.tool_item")) + 12);
        this.toolItemX = x;
        this.toolItemY = y;
        this.toolItemLabel = labelWidth;
        EditBox box = field(x + labelWidth, y, width - labelWidth, this.toolItemText,
                tr("litematicaplus.option.tool_item"), 96);
        box.setTextColor(this.toolItemValid ? Theme.TEXT : Theme.DANGER);
        describe(box, "tool_item");
        box.setResponder(value -> {
            this.toolItemText = value;
            String clean = value.strip().toLowerCase(Locale.ROOT);
            Identifier id = Identifier.tryParse(clean);
            boolean valid = id != null && BuiltInRegistries.ITEM.getOptional(id)
                    .filter(item -> item != net.minecraft.world.item.Items.AIR).isPresent();
            this.toolItemValid = valid;
            box.setTextColor(valid ? Theme.TEXT : Theme.DANGER);
            if (valid) {
                this.working.toolItem = id.toString();
                changed();
            } else {
                this.dirty = true;
                this.status = tr("litematicaplus.error.tool_item");
                this.statusColor = Theme.DANGER;
                updateSave();
            }
        });
    }

    private void authorRow(int x, int y, int width) {
        int labelWidth = Math.min(width / 2, this.font.width(tr("litematicaplus.option.default_author")) + 12);
        this.authorX = x;
        this.authorY = y;
        this.authorLabel = labelWidth;
        EditBox box = field(x + labelWidth, y, width - labelWidth, this.working.defaultAuthor,
                tr("litematicaplus.option.default_author"), 64);
        describe(box, "default_author");
        box.setResponder(value -> {
            this.working.defaultAuthor = value;
            changed();
        });
    }

    private void hudPositionRow(int x, int y, int width) {
        describe(add(FlatButton.secondary(x, y, width, tr("litematicaplus.option.hud_position"), button ->
                this.minecraft.gui.setScreen(new HudLayoutScreen(this.screen, this.working.hudX, this.working.hudY,
                        (hudX, hudY) -> {
                            LitematicaPlusConfig applied = LitematicaPlusConfig.currentCopy();
                            applied.hudX = hudX;
                            applied.hudY = hudY;
                            LitematicaPlusConfig.save(applied);
                            this.working.hudX = hudX;
                            this.working.hudY = hudY;
                        })))), "hud_position");
    }

    private void lockedSlotsRow(int x, int y, int width) {
        describe(add(new SlotLockStrip(x, y, width, tr("litematicaplus.option.locked_slots"),
                () -> this.working.lockedHotbarSlots, value -> {
                    this.working.lockedHotbarSlots = value;
                    changed();
                })), "locked_slots");
    }

    private void pasteReplaceRow(int x, int y, int width) {
        describe(add(FlatButton.secondary(x, y, width, tr("litematicaplus.option.paste_replace",
                tr("litematicaplus.paste_replace." + this.working.pasteReplace.name().toLowerCase(Locale.ROOT))), button -> {
                    this.working.pasteReplace = this.working.pasteReplace.next();
                    changed();
                    this.screen.refresh();
                })), "paste_replace");
    }

    @Override
    void extract(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        graphics.fill(this.optionsX - 6, this.top, this.optionsX - 5, this.top + this.height, Theme.DIVIDER);
        for (Placed header : this.headers) {
            Component text = header.item().header();
            int textY = header.y() + 3;
            Ui.section(graphics, this.font, text, header.x(), textY, header.width());
            int lineX = header.x() + 12 + this.font.width(text);
            if (lineX < header.x() + header.width()) {
                Blueprint.dashed(graphics, lineX, textY + 4, header.x() + header.width() - lineX, Theme.DIVIDER);
            }
        }
        int bottomY = this.top + this.height - Theme.CONTROL_HEIGHT;
        if (searching() && this.matchCount == 0) {
            Ui.emptyState(graphics, this.font, this.optionsX, this.top, this.optionsWidth, bottomY - 16 - this.top,
                    tr("litematicaplus.settings.search_empty", query.strip()), tr("litematicaplus.settings.search_empty_hint"));
        }
        if (this.pages > 1) {
            graphics.centeredText(this.font, (this.page + 1) + " / " + this.pages, this.optionsX + 47, bottomY + 6,
                    Theme.TEXT_DIM);
        }
        if (this.toolItemX >= 0) {
            Ui.clipped(graphics, this.font, tr("litematicaplus.option.tool_item").getString(), this.toolItemX,
                    this.toolItemY + 6, this.toolItemLabel - 4, Theme.ACCENT);
        }
        if (this.authorX >= 0) {
            Ui.clipped(graphics, this.font, tr("litematicaplus.option.default_author").getString(), this.authorX,
                    this.authorY + 6, this.authorLabel - 4, Theme.ACCENT);
        }
        Ui.wrapped(graphics, this.font, this.status, this.optionsX, bottomY - 12, this.optionsWidth, 1, this.statusColor,
                mouseX, mouseY);
    }
}
