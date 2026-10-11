package org.lts.litematicaplus.gui;

import com.mojang.blaze3d.platform.InputConstants;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.BiConsumer;
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
import org.lts.litematicaplus.tool.Substitutes;

final class SettingsView extends TabView {
    private enum Category {
        RENDERING("rendering"),
        TOOLS("tools"),
        EASY_PLACE("easy_place"),
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

    private record Tab(Component label, Runnable select, java.util.function.BooleanSupplier active) {
    }

    private static Category category = Category.RENDERING;
    private static String extension;

    private LitematicaPlusConfig working = LitematicaPlusConfig.currentCopy();
    private FlatButton saveButton;
    private FlatButton revertButton;
    private boolean dirty;
    private boolean toolItemValid = true;
    private String toolItemText = this.working.toolItem;
    private int page;
    private int pages = 1;
    private int toolItemX = -1;
    private int toolItemY;
    private int authorX = -1;
    private int authorY;
    private KeyMapping listening;
    private int listeningModifiers;
    private int pendingModifierKey = -1;
    private boolean bindingsChanged;
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
        this.toolItemX = -1;
        this.authorX = -1;
        List<Tab> tabs = tabs();
        int gap = this.width < 360 ? 3 : Theme.GAP;
        int textTotal = 0;
        for (Tab tab : tabs) {
            textTotal += this.font.width(tab.label());
        }
        int padding = Math.max(4, (this.width - gap * (tabs.size() - 1) - textTotal) / tabs.size());
        int tabX = this.left;
        for (int i = 0; i < tabs.size(); i++) {
            Tab tab = tabs.get(i);
            int width = i == tabs.size() - 1 ? this.left + this.width - tabX : this.font.width(tab.label()) + padding;
            add(new FlatButton(tabX, this.top, Math.max(12, width), Theme.CONTROL_HEIGHT, tab.label(),
                    FlatButton.Style.SECONDARY, button -> {
                        tab.select().run();
                        this.page = 0;
                        this.listening = null;
                        this.screen.refresh();
                    }).withState(tab.active()));
            tabX += width + gap;
        }
        List<Row> rows = rows();
        int rowsTop = this.top + Theme.CONTROL_HEIGHT + 8;
        int bottomY = this.top + this.height - Theme.CONTROL_HEIGHT;
        int columns = this.width >= 380 ? 2 : 1;
        int columnWidth = (this.width - Theme.GAP * (columns - 1)) / columns;
        int visibleRows = Math.max(1, (bottomY - 14 - rowsTop) / (Theme.CONTROL_HEIGHT + 4));
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
                    this.toolItemText = this.working.toolItem;
                    this.status = Component.empty();
                    this.screen.refresh();
                }));
        updateSave();
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

    private List<Row> rows() {
        List<Row> rows = new ArrayList<>();
        if (extension != null) {
            for (SettingsExtensions.Page entry : SettingsExtensions.pages()) {
                if (entry.key().equals(extension)) {
                    for (SettingsExtensions.Option option : entry.options().get()) {
                        rows.add(extensionRow(option));
                    }
                }
            }
            return rows;
        }
        switch (category) {
            case RENDERING -> {
                rows.add(toggle("render_enabled", c -> c.renderEnabled, (c, v) -> c.renderEnabled = v));
                rows.add(toggle("render_ghost", c -> c.renderGhostBlocks, (c, v) -> c.renderGhostBlocks = v));
                rows.add(toggle("ghosts_loaded_only", c -> c.ghostsOnlyLoaded, (c, v) -> c.ghostsOnlyLoaded = v));
                rows.add(percent("ghost_alpha", () -> Math.round(this.working.ghostAlpha * 100.0F),
                        v -> this.working.ghostAlpha = v / 100.0F, 20));
                rows.add(toggle("render_overlay", c -> c.renderOverlay, (c, v) -> c.renderOverlay = v));
                rows.add(percent("overlay_fill_alpha", () -> Math.round(this.working.overlayFillAlpha * 100.0F),
                        v -> this.working.overlayFillAlpha = v / 100.0F, 0));
                rows.add(percent("overlay_line_alpha", () -> Math.round(this.working.overlayLineAlpha * 100.0F),
                        v -> this.working.overlayLineAlpha = v / 100.0F, 0));
                rows.add(toggle("overlay_xray", c -> c.overlayThroughWalls, (c, v) -> c.overlayThroughWalls = v));
                rows.add(toggle("render_colliding", c -> c.renderCollidingBlocks, (c, v) -> c.renderCollidingBlocks = v));
                rows.add(toggle("render_block_entities", c -> c.renderBlockEntities, (c, v) -> c.renderBlockEntities = v));
                rows.add(toggle("lenient_states", c -> c.lenientStates, (c, v) -> c.lenientStates = v));
                rows.add(toggle("loose_blocks", c -> c.looseBlocks, (c, v) -> c.looseBlocks = v));
                rows.add(toggle("soil_interchangeable", c -> c.soilInterchangeable, (c, v) -> c.soilInterchangeable = v));
                rows.add(toggle("inventory_highlight", c -> c.inventoryHighlight, (c, v) -> c.inventoryHighlight = v));
                rows.add(toggle("trust_hidden", c -> c.trustHiddenBlocks, (c, v) -> c.trustHiddenBlocks = v));
                rows.add(toggle("world_cache", c -> c.useWorldCache, (c, v) -> c.useWorldCache = v));
                rows.add(toggle("count_visible_only", c -> c.countVisibleOnly, (c, v) -> c.countVisibleOnly = v));
                rows.add(toggle("placement_boxes", c -> c.renderPlacementBoxes, (c, v) -> c.renderPlacementBoxes = v));
                rows.add(toggle("selection_boxes", c -> c.renderSelectionBoxes, (c, v) -> c.renderSelectionBoxes = v));
                rows.add(number("render_distance", () -> this.working.renderDistance,
                        v -> this.working.renderDistance = v, 32, 2048, 32));
                rows.add(toggle("hud_enabled", c -> c.hudEnabled, (c, v) -> c.hudEnabled = v));
                rows.add(this::hudPositionRow);
                rows.add(number("hud_material_lines", () -> this.working.hudMaterialLines,
                        v -> this.working.hudMaterialLines = v, 0, 16, 1));
                rows.add(toggle("note_tuning_hud", c -> c.noteTuningHud, (c, v) -> c.noteTuningHud = v));
                rows.add(toggle("hud_diagnostics", c -> c.hudDiagnostics, (c, v) -> c.hudDiagnostics = v));
            }
            case TOOLS -> {
                rows.add(toggle("tool_enabled", c -> c.toolEnabled, (c, v) -> c.toolEnabled = v));
                rows.add(this::toolItemRow);
                rows.add(this::authorRow);
                rows.add(toggle("schematic_preview", c -> c.schematicPreview, (c, v) -> c.schematicPreview = v));
                rows.add(toggle("preview_models", c -> c.previewModels, (c, v) -> c.previewModels = v));
                rows.add(toggle("pick_block", c -> c.pickBlockEnabled, (c, v) -> c.pickBlockEnabled = v));
                rows.add(this::lockedSlotsRow);
                rows.add(toggle("placement_restriction", c -> c.placementRestriction, (c, v) -> c.placementRestriction = v));
                rows.add(toggle("material_helper", c -> c.materialHelper, (c, v) -> c.materialHelper = v));
                rows.add(toggle("material_follows_render", c -> c.materialHelperFollowsRender,
                        (c, v) -> c.materialHelperFollowsRender = v));
                rows.add(toggle("material_panel", c -> c.materialPanel, (c, v) -> c.materialPanel = v));
                rows.add(toggle("material_exact", c -> c.materialExactTake, (c, v) -> c.materialExactTake = v));
                rows.add(toggle("material_tooltips", c -> c.materialTooltips, (c, v) -> c.materialTooltips = v));
                rows.add(toggle("ender_chest_materials", c -> c.enderChestMaterials, (c, v) -> c.enderChestMaterials = v));
                rows.add(toggle("material_swap", c -> c.materialSwap, (c, v) -> c.materialSwap = v));
                rows.add(this::pasteReplaceRow);
                rows.add(toggle("paste_block_entities", c -> c.pasteBlockEntityData, (c, v) -> c.pasteBlockEntityData = v));
                rows.add(toggle("paste_entities", c -> c.pasteEntities, (c, v) -> c.pasteEntities = v));
                rows.add(toggle("syncmatica", c -> c.syncmaticaEnabled, (c, v) -> c.syncmaticaEnabled = v));
                rows.add(number("paste_commands", () -> this.working.pasteCommandsPerTick,
                        v -> this.working.pasteCommandsPerTick = v, 1, 16, 1));
            }
            case EASY_PLACE -> {
                rows.add(toggle("easy_place", c -> c.easyPlaceEnabled, (c, v) -> c.easyPlaceEnabled = v));
                rows.add(toggle("easy_place_hold", c -> c.easyPlaceHold, (c, v) -> c.easyPlaceHold = v));
                rows.add(this::presetRow);
                rows.add(number("easy_place_delay", () -> this.working.easyPlaceDelayTicks,
                        v -> this.working.easyPlaceDelayTicks = v, 0, 20, 1));
                rows.add(toggle("easy_place_jitter", c -> c.easyPlaceJitter, (c, v) -> c.easyPlaceJitter = v));
                rows.add(toggle("easy_place_fake_rotation", c -> c.easyPlaceFakeRotation,
                        (c, v) -> c.easyPlaceFakeRotation = v));
                rows.add(number("easy_place_lead", () -> this.working.easyPlaceRotationLeadTicks,
                        v -> this.working.easyPlaceRotationLeadTicks = v, 0, 3, 1));
                rows.add(toggle("easy_place_revert", c -> c.easyPlaceRotationRevert, (c, v) -> c.easyPlaceRotationRevert = v));
                rows.add(toggle("easy_place_strict", c -> c.easyPlaceStrictOrientation,
                        (c, v) -> c.easyPlaceStrictOrientation = v));
                rows.add(toggle("easy_place_air", c -> c.easyPlaceAirPlacement, (c, v) -> c.easyPlaceAirPlacement = v));
                rows.add(toggle("easy_place_finish", c -> c.easyPlaceFinishStates, (c, v) -> c.easyPlaceFinishStates = v));
                rows.add(toggle("easy_place_ignite", c -> c.easyPlaceIgnite, (c, v) -> c.easyPlaceIgnite = v));
                rows.add(toggle("easy_place_waterlog", c -> c.easyPlaceWaterlog, (c, v) -> c.easyPlaceWaterlog = v));
                rows.add(toggle("easy_place_interaction", c -> c.easyPlaceAllowInteraction,
                        (c, v) -> c.easyPlaceAllowInteraction = v));
                rows.add(toggle("easy_place_restrict", c -> c.easyPlaceRestrictArea, (c, v) -> c.easyPlaceRestrictArea = v));
                rows.add(toggle("easy_place_observer", c -> c.easyPlaceObserverDetect,
                        (c, v) -> c.easyPlaceObserverDetect = v));
                rows.add(toggle("easy_place_ignore_data", c -> c.easyPlaceIgnoreItemData,
                        (c, v) -> c.easyPlaceIgnoreItemData = v));
                rows.add(toggle("easy_place_diagnostics", c -> c.easyPlaceDiagnostics, (c, v) -> c.easyPlaceDiagnostics = v));
                rows.add(toggle("easy_place_substitutes", c -> c.easyPlaceSubstitutes, (c, v) -> c.easyPlaceSubstitutes = v));
                rows.add(this::substitutesRow);
                rows.add(toggle("entity_placement", c -> c.entityPlacement, (c, v) -> c.entityPlacement = v));
                rows.add(toggle("excavation_guard", c -> c.excavationGuard, (c, v) -> c.excavationGuard = v));
                rows.add(toggle("tool_use_guard", c -> c.toolUseGuard, (c, v) -> c.toolUseGuard = v));
                rows.add(toggle("excavation_guard_outside", c -> c.excavationGuardOutside,
                        (c, v) -> c.excavationGuardOutside = v));
                rows.add(toggle("excavation_guard_correct", c -> c.excavationGuardCorrect,
                        (c, v) -> c.excavationGuardCorrect = v));
                rows.add(toggle("excavation_guard_hint", c -> c.excavationGuardHint, (c, v) -> c.excavationGuardHint = v));
                rows.add(toggle("auto_tool_switch", c -> c.autoToolSwitch, (c, v) -> c.autoToolSwitch = v));
                rows.add(toggle("terrain_auto_replace", c -> c.terrainAutoReplace, (c, v) -> c.terrainAutoReplace = v));
                rows.add(toggle("break_wrong_blocks", c -> c.breakWrongBlocks, (c, v) -> c.breakWrongBlocks = v));
            }
            case HOTKEYS -> {
                for (Hotkeys.Entry entry : Hotkeys.entries()) {
                    rows.add((x, y, width) -> hotkeyRow(entry, x, y, width));
                }
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

    private static void describe(FlatButton button, String key) {
        String tip = "litematicaplus.option." + key + ".tip";
        if (Language.getInstance().has(tip)) {
            button.setTooltip(Tooltip.create(tr("litematicaplus.option." + key).copy().append("\n").append(tr(tip))));
        }
    }

    private Row toggle(String key, Function<LitematicaPlusConfig, Boolean> getter,
            BiConsumer<LitematicaPlusConfig, Boolean> setter) {
        return (x, y, width) -> describe(add(FlatButton.toggle(x, y, width, tr("litematicaplus.option." + key),
                () -> getter.apply(this.working), button -> {
                    setter.accept(this.working, !getter.apply(this.working));
                    changed();
                })), key);
    }

    private Row extensionRow(SettingsExtensions.Option option) {
        return switch (option) {
            case SettingsExtensions.Toggle toggle -> (x, y, width) -> describe(add(FlatButton.toggle(x, y, width,
                    tr("litematicaplus.option." + toggle.key()), toggle.getter(), button -> {
                        toggle.setter().accept(!toggle.getter().getAsBoolean());
                        this.screen.refresh();
                    })), toggle.key());
            case SettingsExtensions.Number number -> (x, y, width) -> {
                Stepper stepper = add(new Stepper(x, y, width, tr("litematicaplus.option." + number.key()),
                        number.getter(), number.setter(), number.min(), number.max(), number.step()));
                String tip = "litematicaplus.option." + number.key() + ".tip";
                if (Language.getInstance().has(tip)) {
                    stepper.setTooltip(Tooltip.create(tr(tip)));
                }
            };
        };
    }

    private Row number(String key, IntSupplier getter, IntConsumer setter, int min, int max, int step) {
        return (x, y, width) -> {
            Stepper stepper = add(new Stepper(x, y, width, tr("litematicaplus.option." + key), getter, value -> {
                setter.accept(value);
                changed();
            }, min, max, step));
            String tip = "litematicaplus.option." + key + ".tip";
            if (Language.getInstance().has(tip)) {
                stepper.setTooltip(Tooltip.create(tr(tip)));
            }
        };
    }

    private Row percent(String key, IntSupplier getter, IntConsumer setter, int min) {
        return (x, y, width) -> add(new Stepper(x, y, width, tr("litematicaplus.option." + key), getter, value -> {
            setter.accept(value);
            changed();
        }, min, 100, 5).format(value -> value + "%"));
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
            return false;
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
        EditBox box = field(x + labelWidth, y, width - labelWidth, this.toolItemText,
                tr("litematicaplus.option.tool_item"), 96);
        box.setTextColor(this.toolItemValid ? Theme.TEXT : Theme.DANGER);
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
        this.authorX = x;
        this.authorY = y;
        EditBox box = field(x + labelWidth, y, width - labelWidth, this.working.defaultAuthor,
                tr("litematicaplus.option.default_author"), 64);
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
        SlotLockStrip strip = add(new SlotLockStrip(x, y, width, tr("litematicaplus.option.locked_slots"),
                () -> this.working.lockedHotbarSlots, value -> {
                    this.working.lockedHotbarSlots = value;
                    changed();
                }));
        strip.setTooltip(Tooltip.create(tr("litematicaplus.option.locked_slots").copy().append("\n")
                .append(tr("litematicaplus.option.locked_slots.tip"))));
    }

    private void pasteReplaceRow(int x, int y, int width) {
        add(FlatButton.secondary(x, y, width, tr("litematicaplus.option.paste_replace",
                tr("litematicaplus.paste_replace." + this.working.pasteReplace.name().toLowerCase(Locale.ROOT))), button -> {
                    this.working.pasteReplace = this.working.pasteReplace.next();
                    changed();
                    this.screen.refresh();
                }));
    }

    @Override
    void extract(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        int bottomY = this.top + this.height - Theme.CONTROL_HEIGHT;
        graphics.centeredText(this.font, (this.page + 1) + " / " + this.pages, this.left + 47, bottomY + 6, Theme.TEXT_DIM);
        if (this.toolItemX >= 0) {
            graphics.text(this.font, tr("litematicaplus.option.tool_item"), this.toolItemX, this.toolItemY + 6,
                    Theme.ACCENT, false);
        }
        if (this.authorX >= 0) {
            graphics.text(this.font, tr("litematicaplus.option.default_author"), this.authorX, this.authorY + 6,
                    Theme.ACCENT, false);
        }
        Ui.wrapped(graphics, this.font, this.status, this.left, bottomY - 12, this.width, 1, this.statusColor,
                mouseX, mouseY);
    }
}
