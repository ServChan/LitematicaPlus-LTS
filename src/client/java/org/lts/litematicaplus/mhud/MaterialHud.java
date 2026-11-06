package org.lts.litematicaplus.mhud;

import com.mojang.blaze3d.platform.InputConstants;
import it.unimi.dsi.fastutil.objects.Reference2LongMap;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BooleanSupplier;
import java.util.function.IntSupplier;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.Screens;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.core.component.DataComponents;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemContainerContents;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import org.lts.litematicaplus.Hotkeys;
import org.lts.litematicaplus.LitematicaPlusClient;
import org.lts.litematicaplus.Session;
import org.lts.litematicaplus.engine.GhostEngine;
import org.lts.litematicaplus.engine.PlacementRuntime;
import org.lts.litematicaplus.gui.FlatButton;
import org.lts.litematicaplus.gui.HudLayoutScreen;
import org.lts.litematicaplus.gui.SettingsExtensions;
import org.lts.litematicaplus.gui.Theme;
import org.lts.litematicaplus.hud.InfoHud;
import org.lts.litematicaplus.materials.ContainerHelper;
import org.lts.litematicaplus.materials.MaterialList;
import org.lts.litematicaplus.mixin.AbstractContainerScreenAccessor;
import org.lts.litematicaplus.placement.PlacementManager;
import org.lts.litematicaplus.placement.SchematicPlacement;
import org.lts.litematicaplus.util.Notify;
import org.lts.litematicaplus.util.Workers;

public final class MaterialHud {
    private static final int REFRESH_TICKS = 10;
    private static final int AUTO_DELAY_TICKS = 4;
    private static final int SAVE_TICKS = 200;

    record State(String title, String placement, List<MaterialList.Entry> entries, Map<Item, Long> shortfall,
            long total, long placed, boolean pinned, String pinnedFrom) {
        static final State EMPTY = new State("", "", List.of(), Map.of(), 0L, 0L, false, "");
    }

    private static State state = State.EMPTY;
    private static State snapshot;
    private static String snapshotDimension = "";
    private static int page;
    private static int ticks;
    private static int inventoryTicks;
    private static boolean refreshNow;
    private static AbstractContainerScreen<?> autoScreen;
    private static int autoTicks;
    private static boolean autoDone;
    private static FlatButton transfer;
    private static FlatButton auto;

    private MaterialHud() {
    }

    public static boolean enabled() {
        return MaterialHudConfig.current().enabled;
    }

    public static void register() {
        MaterialHudConfig.load();
        try {
            Workers.io().execute(() -> StorageIndex.get().preload());
        } catch (RuntimeException exception) {
            LitematicaPlusClient.LOGGER.warn("Could not load the storage index in the background", exception);
        }
        registerSettings();
        Hotkeys.register("mh_toggle_panel", InputConstants.KEY_Y, minecraft -> togglePanel());
        Hotkeys.register("mh_page_up", InputConstants.KEY_PAGEUP, minecraft -> turnPage(-1));
        Hotkeys.register("mh_page_down", InputConstants.KEY_PAGEDOWN, minecraft -> turnPage(1));
        Hotkeys.register("mh_search", InputConstants.KEY_U, minecraft -> toggleSearch());
        Hotkeys.register("mh_clear_search", 0, minecraft -> clearSearch());
        Hotkeys.register("mh_position", 0, minecraft -> openPosition(minecraft, null));
        Hotkeys.register("mh_search_all", 0, minecraft -> toggleAllChests());
        Hotkeys.register("mh_ghost_inventory", 0, minecraft -> toggleInventoryGhosts(minecraft));
        ClientTickEvents.END_CLIENT_TICK.register(MaterialHud::tick);
        HudElementRegistry.addLast(Identifier.fromNamespaceAndPath(LitematicaPlusClient.MOD_ID, "material_hud"),
                (graphics, counter) -> renderHud(graphics));
        ScreenEvents.AFTER_INIT.register(MaterialHud::afterInit);
        ClientLifecycleEvents.CLIENT_STOPPING.register(client -> StorageIndex.get().saveIfDirty());
    }

    private static void registerSettings() {
        SettingsExtensions.register(new SettingsExtensions.Page("material_hud", () -> List.of(
                toggle("mh_enabled", () -> MaterialHudConfig.current().enabled, (c, v) -> c.enabled = v),
                toggle("mh_panel_visible", () -> MaterialHudConfig.current().panelVisible, (c, v) -> c.panelVisible = v),
                toggle("mh_only_missing", () -> MaterialHudConfig.current().onlyMissing, (c, v) -> c.onlyMissing = v),
                toggle("mh_sort_by_name", () -> MaterialHudConfig.current().sortByName, (c, v) -> c.sortByName = v),
                toggle("mh_show_progress", () -> MaterialHudConfig.current().showProgress, (c, v) -> c.showProgress = v),
                toggle("mh_show_shortfall", () -> MaterialHudConfig.current().showShortfall,
                        (c, v) -> c.showShortfall = v),
                toggle("mh_count_in_boxes", () -> MaterialHudConfig.current().countInBoxes,
                        (c, v) -> c.countInBoxes = v),
                number("mh_items_per_page", () -> MaterialHudConfig.current().itemsPerPage,
                        (c, v) -> c.itemsPerPage = v, 1, 30, 1),
                number("mh_scale", () -> MaterialHudConfig.current().scalePercent, (c, v) -> c.scalePercent = v,
                        50, 300, 25),
                toggle("mh_pin_across_dimensions", () -> MaterialHudConfig.current().pinAcrossDimensions,
                        (c, v) -> c.pinAcrossDimensions = v),
                toggle("mh_beside_container", () -> MaterialHudConfig.current().besideContainer,
                        (c, v) -> c.besideContainer = v),
                toggle("mh_dock_right", () -> MaterialHudConfig.current().dockRight, (c, v) -> c.dockRight = v),
                toggle("mh_transfer_button", () -> MaterialHudConfig.current().transferButton,
                        (c, v) -> c.transferButton = v),
                toggle("mh_transfer_auto", () -> MaterialHudConfig.current().transferAuto, (c, v) -> c.transferAuto = v),
                toggle("mh_highlight_in_container", () -> MaterialHudConfig.current().highlightInContainer,
                        (c, v) -> c.highlightInContainer = v),
                number("mh_search_radius", () -> MaterialHudConfig.current().searchRadius, (c, v) -> c.searchRadius = v,
                        16, 256, 16),
                toggle("mh_search_item_frames", () -> MaterialHudConfig.current().searchItemFrames,
                        (c, v) -> c.searchItemFrames = v),
                toggle("mh_search_follows_page", () -> MaterialHudConfig.current().searchFollowsPage,
                        (c, v) -> c.searchFollowsPage = v),
                toggle("mh_skip_owned", () -> MaterialHudConfig.current().skipOwned, (c, v) -> c.skipOwned = v),
                toggle("mh_search_markers", () -> MaterialHudConfig.current().searchMarkers,
                        (c, v) -> c.searchMarkers = v),
                toggle("mh_highlight_labels", () -> MaterialHudConfig.current().highlightLabels,
                        (c, v) -> c.highlightLabels = v),
                number("mh_highlight_glow", () -> MaterialHudConfig.current().highlightGlow,
                        (c, v) -> c.highlightGlow = v, 10, 100, 5),
                toggle("mh_own_color", () -> MaterialHudConfig.current().ownColor, (c, v) -> c.ownColor = v),
                number("mh_hue", () -> MaterialHudConfig.current().hue, (c, v) -> c.hue = v, 0, 359, 10),
                toggle("mh_search_all", () -> MaterialHudConfig.current().searchAllChests, (c, v) -> {
                    c.searchAllChests = v;
                    if (!v) {
                        StorageSearch.get().clear();
                    }
                }),
                toggle("mh_count_entities", () -> MaterialHudConfig.current().countEntities,
                        (c, v) -> c.countEntities = v),
                toggle("mh_ghost_inventory", () -> MaterialHudConfig.current().ghostInventoryOnly,
                        (c, v) -> c.ghostInventoryOnly = v))));
    }

    private interface Setter<T> {
        void set(MaterialHudConfig config, T value);
    }

    private static SettingsExtensions.Toggle toggle(String key, BooleanSupplier getter,
            Setter<Boolean> setter) {
        return new SettingsExtensions.Toggle(key, getter, value -> {
            MaterialHudConfig.update(config -> setter.set(config, value));
            refreshNow = true;
        });
    }

    private static SettingsExtensions.Number number(String key, IntSupplier getter,
            Setter<Integer> setter, int min, int max, int step) {
        return new SettingsExtensions.Number(key, getter, value -> {
            MaterialHudConfig.update(config -> setter.set(config, value));
            refreshNow = true;
        }, min, max, step);
    }

    public static void refresh() {
        refreshNow = true;
    }

    public static void setEnabled(boolean value) {
        MaterialHudConfig.update(config -> config.enabled = value);
        if (!value) {
            StorageSearch.get().clear();
            GhostEngine.get().setAutoHidden(Set.of());
        }
        refreshNow = true;
    }

    private static void togglePanel() {
        if (!enabled()) {
            return;
        }
        boolean value = !MaterialHudConfig.current().panelVisible;
        MaterialHudConfig.update(config -> config.panelVisible = value);
        Notify.actionBar(value ? "litematicaplus.mhud.panel_on" : "litematicaplus.mhud.panel_off");
    }

    private static void turnPage(int delta) {
        if (!enabled()) {
            return;
        }
        int pages = pages();
        page = Math.floorMod(page + delta, pages);
        if (MaterialHudConfig.current().searchFollowsPage) {
            StorageSearch.get().follow(pageEntries());
        }
    }

    private static void toggleSearch() {
        if (!enabled()) {
            return;
        }
        List<MaterialList.Entry> entries = pageEntries();
        if (entries.isEmpty()) {
            Notify.actionBar("litematicaplus.mhud.search_empty");
            return;
        }
        int marked = StorageSearch.get().toggle(entries);
        Notify.actionBar(!StorageSearch.get().active() ? "litematicaplus.mhud.search_off"
                : marked == 0 ? "litematicaplus.mhud.search_armed" : "litematicaplus.mhud.search_on", marked);
    }

    private static void toggleAllChests() {
        if (!enabled()) {
            return;
        }
        boolean value = !MaterialHudConfig.current().searchAllChests;
        MaterialHudConfig.update(config -> config.searchAllChests = value);
        if (!value) {
            StorageSearch.get().clear();
        }
        refreshNow = true;
        Notify.actionBar(value ? "litematicaplus.mhud.all_chests_on" : "litematicaplus.mhud.all_chests_off");
    }

    private static void toggleInventoryGhosts(Minecraft minecraft) {
        if (!enabled()) {
            return;
        }
        boolean value = !MaterialHudConfig.current().ghostInventoryOnly;
        MaterialHudConfig.update(config -> config.ghostInventoryOnly = value);
        inventoryTicks = 0;
        boolean creative = minecraft.player != null && minecraft.player.isCreative();
        Notify.actionBar(!value ? "litematicaplus.mhud.ghost_inventory_off" : creative
                ? "litematicaplus.mhud.ghost_inventory_creative" : "litematicaplus.mhud.ghost_inventory_on");
    }

    private static void clearSearch() {
        if (!enabled()) {
            return;
        }
        StorageSearch.get().clear();
        Notify.actionBar("litematicaplus.mhud.search_cleared");
    }

    public static void openPosition(Minecraft minecraft, Screen parent) {
        MaterialHudConfig config = MaterialHudConfig.current();
        minecraft.gui.setScreen(new HudLayoutScreen(parent, config.x, config.y,
                (x, y) -> MaterialHudConfig.update(next -> {
                    next.x = x;
                    next.y = y;
                }), new HudLayoutScreen.Panel() {
                    @Override
                    public int width() {
                        return Math.max(60, HudPanel.width(Minecraft.getInstance().font, view()));
                    }

                    @Override
                    public int render(GuiGraphicsExtractor graphics, float fractionX, float fractionY) {
                        return renderFree(graphics, fractionX, fractionY);
                    }
                }));
    }

    static int pages() {
        int per = MaterialHudConfig.current().itemsPerPage;
        return Math.max(1, (state.entries().size() + per - 1) / per);
    }

    static int page() {
        return Math.min(page, pages() - 1);
    }

    public static List<MaterialList.Entry> pageEntries() {
        int per = MaterialHudConfig.current().itemsPerPage;
        int from = page() * per;
        List<MaterialList.Entry> entries = state.entries();
        return from >= entries.size() ? List.of() : List.copyOf(entries.subList(from, Math.min(entries.size(), from + per)));
    }

    static State view() {
        return state;
    }

    private static void tick(Minecraft minecraft) {
        if (!enabled()) {
            state = State.EMPTY;
            GhostEngine.get().setAutoHidden(Set.of());
            return;
        }
        if (--inventoryTicks <= 0) {
            inventoryTicks = 20;
            inventoryGhosts(minecraft);
        }
        ticks++;
        if (ticks % SAVE_TICKS == 0) {
            StorageIndex.get().saveIfDirty();
        }
        if (refreshNow || ticks % REFRESH_TICKS == 0) {
            refreshNow = false;
            int before = page();
            List<Item> beforeItems = itemsOf(pageEntries());
            state = compute(minecraft);
            page = Math.min(page, pages() - 1);
            if (MaterialHudConfig.current().searchFollowsPage && (before != page() || !itemsOf(pageEntries()).equals(beforeItems))) {
                StorageSearch.get().follow(pageEntries());
            }
        }
        if (MaterialHudConfig.current().searchAllChests) {
            StorageSearch.get().searchAll(allNeeded());
        }
        StorageSearch.get().tick(minecraft);
        autoTick(minecraft);
    }

    private static List<Item> allNeeded() {
        List<Item> items = new ArrayList<>();
        for (MaterialList.Entry entry : state.entries()) {
            if (entry.missing() > 0L) {
                items.add(entry.item());
            }
        }
        return items;
    }

    private static void inventoryGhosts(Minecraft minecraft) {
        if (!MaterialHudConfig.current().ghostInventoryOnly || minecraft.player == null || minecraft.player.isCreative()) {
            GhostEngine.get().setAutoHidden(Set.of());
            return;
        }
        Reference2LongMap<Item> owned = MaterialList.countTopLevel(minecraft.player.getInventory());
        Set<Block> hidden = new HashSet<>();
        for (PlacementRuntime runtime : GhostEngine.get().runtimes()) {
            if (runtime.volume() == null) {
                continue;
            }
            for (BlockState palette : runtime.volume().palette()) {
                if (palette.isAir() || hidden.contains(palette.getBlock())) {
                    continue;
                }
                boolean[] needs = {false, false};
                MaterialList.requirements(palette, (item, count) -> {
                    needs[0] = true;
                    if (owned.getLong(item) > 0L) {
                        needs[1] = true;
                    }
                });
                if (needs[0] && !needs[1]) {
                    hidden.add(palette.getBlock());
                }
            }
        }
        GhostEngine.get().setAutoHidden(hidden);
    }

    private static List<Item> itemsOf(List<MaterialList.Entry> entries) {
        List<Item> items = new ArrayList<>(entries.size());
        for (MaterialList.Entry entry : entries) {
            items.add(entry.item());
        }
        return items;
    }

    private static State compute(Minecraft minecraft) {
        MaterialHudConfig config = MaterialHudConfig.current();
        SchematicPlacement placement = PlacementManager.get().visibleSelection();
        if (minecraft.player == null) {
            return State.EMPTY;
        }
        String scope = StorageIndex.scope(minecraft);
        if (placement == null) {
            if (config.pinAcrossDimensions && snapshot != null && !snapshotDimension.equals(Session.dimension(minecraft))) {
                return live(snapshot, minecraft, scope);
            }
            return State.EMPTY;
        }
        PlacementRuntime runtime = GhostEngine.get().runtime(placement.id());
        if (runtime == null || runtime.volume() == null) {
            return State.EMPTY;
        }
        List<MaterialList.Entry> all = MaterialList.compute(runtime, minecraft.player.getInventory(),
                config.sortByName ? MaterialList.Sort.NAME : MaterialList.Sort.MISSING);
        List<MaterialList.Entry> shown = new ArrayList<>();
        for (MaterialList.Entry entry : all) {
            if (entry.marked() || config.onlyMissing && entry.missing() <= 0L) {
                continue;
            }
            shown.add(entry);
        }
        long total = runtime.totalBlocks();
        long placed = Math.round(total * Math.max(0.0, Math.min(1.0, runtime.progress())));
        State result = new State(InfoHud.layerText(GhostEngine.get().layer()).getString(), placement.name(),
                List.copyOf(shown), shortfalls(shown, scope), total, placed, false, "");
        snapshot = result;
        snapshotDimension = Session.dimension(minecraft);
        return result;
    }

    private static State live(State pinned, Minecraft minecraft, String scope) {
        Reference2LongMap<Item> inventory = MaterialList.countInventory(minecraft.player.getInventory());
        List<MaterialList.Entry> entries = new ArrayList<>();
        for (MaterialList.Entry entry : pinned.entries()) {
            entries.add(new MaterialList.Entry(entry.item(), entry.total(), entry.missing(), inventory.getLong(entry.item()),
                    inventory.getLong(entry.item()), entry.collected()));
        }
        return new State(pinned.title(), pinned.placement(), List.copyOf(entries), shortfalls(entries, scope),
                pinned.total(), pinned.placed(), true, dimensionName(snapshotDimension));
    }

    private static String dimensionName(String id) {
        String key = "litematicaplus.dimension." + id.replace(':', '.');
        return Language.getInstance().has(key) ? Component.translatable(key).getString() : id;
    }

    private static Map<Item, Long> shortfalls(List<MaterialList.Entry> entries, String scope) {
        Map<Item, Long> result = new HashMap<>();
        for (MaterialList.Entry entry : entries) {
            long stored = StorageIndex.get().stored(scope, StorageIndex.itemId(entry.item()));
            long need = Math.max(0L, entry.shortfall() - stored);
            if (need > 0L) {
                result.put(entry.item(), need);
            }
        }
        return result;
    }

    private static boolean visible() {
        MaterialHudConfig config = MaterialHudConfig.current();
        return config.enabled && config.panelVisible && !state.entries().isEmpty() || config.enabled && config.panelVisible
                && state.total() > 0L;
    }

    private static void renderHud(GuiGraphicsExtractor graphics) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.gui.screen() != null) {
            return;
        }
        StorageHighlight.labels(graphics);
        if (!visible()) {
            return;
        }
        MaterialHudConfig config = MaterialHudConfig.current();
        renderFree(graphics, config.x, config.y);
    }

    private static int renderFree(GuiGraphicsExtractor graphics, float fractionX, float fractionY) {
        Minecraft minecraft = Minecraft.getInstance();
        State view = state;
        float scale = MaterialHudConfig.current().scalePercent / 100.0F;
        int width = Math.round(HudPanel.width(minecraft.font, view) * scale);
        int height = Math.round(HudPanel.height(minecraft.font, view) * scale);
        int screenWidth = minecraft.getWindow().getGuiScaledWidth();
        int screenHeight = minecraft.getWindow().getGuiScaledHeight();
        int x = InfoHud.left(screenWidth, width, fractionX);
        int y = InfoHud.top(screenHeight, height, fractionY);
        HudPanel.draw(graphics, minecraft.font, view, x, y, scale);
        return height;
    }

    private static void afterInit(Minecraft client, Screen screen, int width, int height) {
        if (!(screen instanceof AbstractContainerScreen<?> container)) {
            return;
        }
        transfer = null;
        auto = null;
        if (enabled() && ContainerHelper.isStorage(container)) {
            autoScreen = container;
            autoTicks = 0;
            autoDone = !MaterialHudConfig.current().transferAuto;
            transfer = FlatButton.secondary(0, 0, 10, Component.translatable("litematicaplus.mhud.transfer"),
                    button -> transfer(container, true));
            auto = FlatButton.secondary(0, 0, 10, Component.translatable("litematicaplus.mhud.transfer_auto"),
                    button -> {
                        boolean value = !MaterialHudConfig.current().transferAuto;
                        MaterialHudConfig.update(config -> config.transferAuto = value);
                        Notify.actionBar(value ? "litematicaplus.mhud.auto_on" : "litematicaplus.mhud.auto_off");
                        if (value) {
                            transfer(container, false);
                        }
                    }).withState(() -> MaterialHudConfig.current().transferAuto);
            transfer.withHint(Component.translatable("litematicaplus.mhud.transfer.tip"));
            auto.withHint(Component.translatable("litematicaplus.mhud.transfer_auto.tip"));
            transfer.visible = false;
            auto.visible = false;
            Screens.getWidgets(screen).add(transfer);
            Screens.getWidgets(screen).add(auto);
        }
        FlatButton ownTransfer = transfer;
        FlatButton ownAuto = auto;
        ScreenEvents.beforeExtract(screen).register((current, graphics, mouseX, mouseY, delta) ->
                place(container, ownTransfer, ownAuto));
        ScreenEvents.afterExtract(screen).register((current, graphics, mouseX, mouseY, delta) -> {
            if (!enabled()) {
                return;
            }
            highlightSlots(container, graphics);
            dock(container, graphics);
        });
        ScreenEvents.remove(screen).register(current -> {
            if (autoScreen == current) {
                autoScreen = null;
            }
        });
    }

    private static final float MIN_SCALE = 0.5F;
    private static final float MIN_SHRINK = 0.5F;

    private record Dock(int x, int y, float scale, boolean button) {
    }

    private static float fit(int room, int size) {
        return room <= 0 || size <= 0 ? 0.0F : (float) (Math.floor(room / (double) size * 100.0) / 100.0);
    }

    private static Dock dockOf(AbstractContainerScreen<?> screen) {
        MaterialHudConfig config = MaterialHudConfig.current();
        if (!enabled() || !config.besideContainer || !config.panelVisible || !visible()) {
            return null;
        }
        Minecraft minecraft = Minecraft.getInstance();
        AbstractContainerScreenAccessor bounds = (AbstractContainerScreenAccessor) screen;
        float scale = config.scalePercent / 100.0F;
        int layoutWidth = HudPanel.width(minecraft.font, state);
        int layoutHeight = HudPanel.height(minecraft.font, state);
        boolean button = config.transferButton && ContainerHelper.isStorage(screen) && ContainerHelper.open(screen);
        int reserve = button ? Theme.CONTROL_HEIGHT + 2 : 0;
        int left = bounds.litematicaplus$leftPos();
        int right = left + bounds.litematicaplus$imageWidth();
        int roomRight = screen.width - 2 - right - 4;
        int roomLeft = left - 4 - 2;
        int roomFirst = config.dockRight ? roomRight : roomLeft;
        int roomSecond = config.dockRight ? roomLeft : roomRight;
        boolean first;
        if (Math.ceil(layoutWidth * scale) <= roomFirst) {
            first = true;
        } else if (Math.ceil(layoutWidth * scale) <= roomSecond) {
            first = false;
        } else {
            float min = Math.max(MIN_SCALE, scale * MIN_SHRINK);
            float fitFirst = fit(roomFirst, layoutWidth);
            float fitSecond = fit(roomSecond, layoutWidth);
            if (fitFirst >= min) {
                first = true;
                scale = fitFirst;
            } else if (fitSecond >= min) {
                first = false;
                scale = fitSecond;
            } else {
                return null;
            }
        }
        int roomHeight = screen.height - 4 - reserve;
        if (Math.ceil(layoutHeight * scale) > roomHeight) {
            float fitted = fit(roomHeight, layoutHeight);
            if (fitted < MIN_SCALE) {
                return null;
            }
            scale = Math.min(scale, fitted);
        }
        int width = (int) Math.ceil(layoutWidth * scale);
        int height = (int) Math.ceil(layoutHeight * scale);
        boolean onRight = first == config.dockRight;
        int x = onRight ? right + 4 : left - 4 - width;
        int y = Math.max(2 + reserve, Math.min(bounds.litematicaplus$topPos() + reserve, screen.height - height - 2));
        return new Dock(x, y, scale, button);
    }

    private static void place(AbstractContainerScreen<?> screen, FlatButton transferButton, FlatButton autoButton) {
        if (transferButton == null || autoButton == null) {
            return;
        }
        Dock dock = dockOf(screen);
        boolean shown = dock != null && dock.button();
        transferButton.visible = shown;
        autoButton.visible = shown;
        if (!shown) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        int width = (int) Math.ceil(HudPanel.width(minecraft.font, state) * dock.scale());
        int autoWidth = Math.max(36, minecraft.font.width(autoButton.getMessage()) + 12);
        int y = dock.y() - Theme.CONTROL_HEIGHT - 2;
        transferButton.setX(dock.x());
        transferButton.setY(y);
        transferButton.setWidth(Math.max(40, width - autoWidth - 2));
        autoButton.setX(dock.x() + width - autoWidth);
        autoButton.setY(y);
        autoButton.setWidth(autoWidth);
        transferButton.active = !ContainerHelper.busy() && !neededOnPage().isEmpty();
    }

    private static void dock(AbstractContainerScreen<?> screen, GuiGraphicsExtractor graphics) {
        Dock dock = dockOf(screen);
        if (dock == null) {
            return;
        }
        HudPanel.draw(graphics, Minecraft.getInstance().font, state, dock.x(), dock.y(), dock.scale());
    }

    private static void highlightSlots(AbstractContainerScreen<?> screen, GuiGraphicsExtractor graphics) {
        if (!MaterialHudConfig.current().highlightInContainer || StorageSearch.get().empty()) {
            return;
        }
        AbstractContainerScreenAccessor bounds = (AbstractContainerScreenAccessor) screen;
        int left = bounds.litematicaplus$leftPos();
        int top = bounds.litematicaplus$topPos();
        for (Slot slot : screen.getMenu().slots) {
            if (!slot.hasItem()) {
                continue;
            }
            int x = left + slot.x;
            int y = top + slot.y;
            Integer color = StorageSearch.get().colorFor(slot.getItem().getItem());
            if (color != null) {
                graphics.fill(x, y, x + 16, y + 16, 0x88 << 24 | (color & 0xFFFFFF));
                graphics.outline(x - 1, y - 1, 18, 18, color);
                continue;
            }
            Integer inner = insideColor(slot.getItem());
            if (inner != null) {
                graphics.outline(x - 1, y - 1, 18, 18, inner);
            }
        }
    }

    private static Integer insideColor(ItemStack stack) {
        ItemContainerContents contents = stack.get(DataComponents.CONTAINER);
        if (contents == null) {
            return null;
        }
        for (ItemStack inner : contents.nonEmptyItemCopyStream().toList()) {
            Integer color = StorageSearch.get().colorFor(inner.getItem());
            if (color != null) {
                return color;
            }
        }
        return null;
    }

    private static Set<Item> neededOnPage() {
        Set<Item> items = new LinkedHashSet<>();
        for (MaterialList.Entry entry : pageEntries()) {
            if (!state.pinned() && entry.shortfall() > 0L) {
                items.add(entry.item());
            }
        }
        return items;
    }

    private static void transfer(AbstractContainerScreen<?> screen, boolean loud) {
        Set<Item> items = neededOnPage();
        if (items.isEmpty()) {
            if (loud) {
                Notify.actionBar("litematicaplus.mhud.transfer_none");
            }
            return;
        }
        if (!ContainerHelper.takeFor(screen, items) && loud) {
            Notify.actionBar("litematicaplus.panel.busy");
        }
    }

    private static void autoTick(Minecraft minecraft) {
        AbstractContainerScreen<?> screen = autoScreen;
        if (screen == null || autoDone || minecraft.gui.screen() != screen) {
            return;
        }
        if (++autoTicks < AUTO_DELAY_TICKS || screen.getMenu().getStateId() == 0) {
            return;
        }
        autoDone = true;
        refreshNow = true;
        state = compute(minecraft);
        transfer(screen, false);
    }

}
