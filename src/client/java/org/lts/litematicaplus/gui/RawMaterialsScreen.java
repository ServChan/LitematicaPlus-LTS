package org.lts.litematicaplus.gui;

import com.mojang.blaze3d.platform.InputConstants;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.lts.litematicaplus.LitematicaPlusClient;
import org.lts.litematicaplus.LitematicaPlusConfig;
import org.lts.litematicaplus.engine.GhostEngine;
import org.lts.litematicaplus.engine.PlacementRuntime;
import org.lts.litematicaplus.hud.InfoHud;
import org.lts.litematicaplus.materials.CountParser;
import org.lts.litematicaplus.materials.MaterialExport;
import org.lts.litematicaplus.materials.MaterialList;
import org.lts.litematicaplus.materials.MaterialReport;
import org.lts.litematicaplus.placement.SchematicPlacement;
import org.lts.litematicaplus.recipes.RawMaterials;
import org.lts.litematicaplus.recipes.RawPlanner;
import org.lts.litematicaplus.recipes.RecipeGraph;
import org.lts.litematicaplus.recipes.RecipeIndex;
import org.lts.litematicaplus.util.Notify;

public final class RawMaterialsScreen extends Screen {
    private static final int MARGIN = 16;
    private static final int MAX_WIDTH = 980;
    private static final int REPLAN_TICKS = 20;
    private static final int MAX_RECIPE_INPUTS = 3;
    private static final int COLUMN_GAP = 8;
    private static final String ARROW = "\u2192";

    public record Source(Component title, Supplier<List<MaterialList.Entry>> entries, boolean scoped, boolean custom) {
        public static Source placement(SchematicPlacement placement) {
            return new Source(Component.literal(placement.name()), () -> {
                PlacementRuntime runtime = GhostEngine.get().runtime(placement.id());
                Minecraft minecraft = Minecraft.getInstance();
                return MaterialList.compute(runtime, minecraft.player == null ? null : minecraft.player.getInventory(),
                        MaterialList.Sort.NAME);
            }, true, false);
        }

        public static Source fixed(String title, List<MaterialList.Entry> entries) {
            List<MaterialList.Entry> copy = List.copyOf(entries);
            return new Source(Component.literal(title), () -> copy, false, false);
        }

        public static Source customList() {
            return new Source(Component.translatable("litematicaplus.raw.custom_title"), () -> {
                Map<Item, Long> inventory = RawMaterials.inventory();
                List<MaterialList.Entry> entries = new ArrayList<>();
                LitematicaPlusConfig.current().customMaterials.forEach((id, count) -> {
                    Item item = RawMaterials.item(id);
                    if (item != null && count != null && count > 0L) {
                        entries.add(new MaterialList.Entry(item, count, count, inventory.getOrDefault(item, 0L)));
                    }
                });
                return entries;
            }, false, true);
        }
    }

    private record SearchItem(Item item, String name, String id) {
    }

    private final Screen parent;
    private final Source origin;
    private Source source;
    private RawPlanner.Plan<Item> plan = RawPlanner.Plan.empty();
    private List<MaterialList.Entry> entries = List.of();
    private ListPanel<RawPlanner.Raw<Item>> rawList;
    private ListPanel<RawPlanner.Step<Item>> stepList;
    private EditBox nameBox;
    private EditBox countBox;
    private Item match;
    private List<SearchItem> searchIndex;
    private Set<Item> highlight = Set.of();
    private Item selectedRaw;
    private boolean exporting;
    private boolean shiftClick;
    private Component status;
    private int statusTicks;
    private int ticks;
    private int seenGeneration = -1;
    private int panelX;
    private int panelWidth;
    private int headerY;
    private int splitX;
    private int footerY;
    private int matchX;
    private int matchWidth;
    private int addRowY;
    private RawPlanner.Raw<Item> hoveredRaw;
    private FlatButton scopeButton;
    private int haveColumn = 30;
    private int getColumn = 30;
    private boolean showStacks;
    private int slotWidth = 34;
    private int recipeSlots = 1;
    private int outputWidth = 20;
    private int craftsWidth = 20;
    private int footerLines = 1;
    private RawPlanner.Step<Item> hoveredStep;

    public RawMaterialsScreen(Screen parent, Source source) {
        super(Component.translatable("litematicaplus.raw.title", source.title()));
        this.parent = parent;
        this.origin = source;
        this.source = source;
        RecipeIndex.refresh();
    }

    public RawPlanner.Plan<Item> plan() {
        return this.plan;
    }

    public boolean indexReady() {
        return RecipeIndex.state() == RecipeIndex.State.READY && RecipeIndex.graph().size() > 0;
    }

    @Override
    protected void init() {
        this.panelWidth = Math.min(MAX_WIDTH, this.width - MARGIN * 2);
        this.panelX = (this.width - this.panelWidth) / 2;
        int y = MARGIN + 14;
        LitematicaPlusConfig config = LitematicaPlusConfig.current();
        if (this.exporting) {
            exportRow(y);
        } else {
            List<FlatButton> buttons = new ArrayList<>();
            buttons.add(FlatButton.primary(0, y, 10, Component.translatable("gui.back"), button -> onClose()));
            if (this.source.scoped()) {
                buttons.add(this.scopeButton = hint(FlatButton.secondary(0, y, 10, Component.translatable(config.rawScopeAll
                        ? "litematicaplus.raw.scope.all" : "litematicaplus.raw.scope.missing"),
                        button -> update(next -> next.rawScopeAll = !next.rawScopeAll, true)), "litematicaplus.raw.scope.tip"));
            }
            if (this.source.custom()) {
                buttons.add(hint(FlatButton.danger(0, y, 10, Component.translatable("litematicaplus.raw.custom_clear"),
                        button -> clearCustom()), "litematicaplus.raw.custom_clear.tip"));
            }
            buttons.add(hint(FlatButton.toggle(0, y, 10, Component.translatable("litematicaplus.raw.stock"),
                    () -> LitematicaPlusConfig.current().rawUseStock,
                    button -> update(next -> next.rawUseStock = !next.rawUseStock, false)), "litematicaplus.raw.stock.tip"));
            buttons.add(hint(FlatButton.toggle(0, y, 10, Component.translatable("litematicaplus.raw.smelting"),
                    () -> LitematicaPlusConfig.current().rawSmelting,
                    button -> update(next -> next.rawSmelting = !next.rawSmelting, false)), "litematicaplus.raw.smelting.tip"));
            buttons.add(hint(FlatButton.secondary(0, y, 10, Component.translatable("litematicaplus.raw.reset"), button -> {
                update(next -> {
                    next.rawChoices.clear();
                    next.rawForced.clear();
                }, false);
                status(Component.translatable("litematicaplus.raw.reset_done"));
            }), "litematicaplus.raw.reset.tip"));
            if (!this.origin.custom()) {
                buttons.add(hint(FlatButton.secondary(0, y, 10, Component.translatable(this.source.custom()
                        ? "litematicaplus.raw.back_to_source" : "litematicaplus.raw.custom"), button -> {
                            this.source = this.source.custom() ? this.origin : Source.customList();
                            this.selectedRaw = null;
                            this.highlight = Set.of();
                            rebuildWidgets();
                        }), this.source.custom() ? "litematicaplus.raw.back_to_source.tip" : "litematicaplus.raw.custom.tip"));
            }
            buttons.add(hint(FlatButton.secondary(0, y, 10, Component.translatable("litematicaplus.materials.export_clipboard"),
                    button -> copy()), "litematicaplus.raw.copy.tip"));
            buttons.add(FlatButton.secondary(0, y, 10, Component.translatable("litematicaplus.materials.export"), button -> {
                this.exporting = true;
                rebuildWidgets();
            }));
            int totalWeight = 0;
            for (FlatButton button : buttons) {
                totalWeight += weight(button);
            }
            int rows = this.panelWidth / totalWeight < 62 ? 2 : 1;
            int perRow = (buttons.size() + rows - 1) / rows;
            for (int row = 0; row < rows; row++) {
                int first = row * perRow;
                int count = Math.min(perRow, buttons.size() - first);
                int weights = 0;
                for (int i = 0; i < count; i++) {
                    weights += weight(buttons.get(first + i));
                }
                int free = this.panelWidth - Theme.GAP * (count - 1);
                int x = this.panelX;
                for (int i = 0; i < count; i++) {
                    FlatButton button = buttons.get(first + i);
                    int width = i == count - 1 ? this.panelX + this.panelWidth - x : free * weight(button) / weights;
                    button.setX(x);
                    button.setY(y);
                    button.setWidth(width);
                    addRenderableWidget(button);
                    x += width + Theme.GAP;
                }
                if (row < rows - 1) {
                    y += Theme.CONTROL_HEIGHT + 4;
                }
            }
        }
        y += Theme.CONTROL_HEIGHT + 4;
        if (this.source.custom()) {
            this.addRowY = y;
            int nameWidth = this.panelWidth * 36 / 100;
            int countWidth = Math.max(60, this.panelWidth / 9);
            int addWidth = Math.max(70, this.panelWidth / 8);
            String previousName = this.nameBox == null ? "" : this.nameBox.getValue();
            String previousCount = this.countBox == null ? "" : this.countBox.getValue();
            this.nameBox = field(this.panelX, y, nameWidth, previousName, "litematicaplus.raw.custom_item", 64);
            hint(this.nameBox, "litematicaplus.raw.custom_item.tip");
            this.nameBox.setResponder(value -> rematch());
            this.matchX = this.panelX + nameWidth + Theme.GAP;
            this.matchWidth = this.panelWidth - nameWidth - countWidth - addWidth - Theme.GAP * 3;
            this.countBox = field(this.matchX + this.matchWidth + Theme.GAP, y, countWidth, previousCount,
                    "litematicaplus.raw.custom_count", 24);
            hint(this.countBox, "litematicaplus.raw.custom_count.tip");
            addRenderableWidget(hint(FlatButton.primary(this.panelX + this.panelWidth - addWidth, y, addWidth,
                    Component.translatable("litematicaplus.raw.custom_add"), button -> addCustom()),
                    "litematicaplus.raw.custom_add.tip"));
            rematch();
            y += Theme.CONTROL_HEIGHT + 4;
        } else {
            this.nameBox = null;
            this.countBox = null;
        }
        this.headerY = y + 3;
        int listTop = y + 14;
        Component longest = Component.translatable(this.source.custom() ? "litematicaplus.raw.footer_custom"
                : "litematicaplus.raw.footer").copy().append(" \u00b7 ").append(Component.translatable("litematicaplus.raw.partial"));
        this.footerLines = Math.min(2, Math.max(1, this.font.split(longest, this.panelWidth).size()));
        this.footerY = this.height - 6 - this.footerLines * 10;
        int listHeight = Math.max(40, this.footerY - 4 - listTop);
        int leftWidth = this.panelWidth * 42 / 100;
        this.splitX = this.panelX + leftWidth + Theme.GAP;
        this.rawList = addRenderableWidget(new ListPanel<RawPlanner.Raw<Item>>(this.panelX, listTop, leftWidth, listHeight,
                Theme.ROW_HEIGHT, Component.translatable("litematicaplus.raw.column.raw"), this::renderRaw)
                .onPress(this::pressRaw)
                .describe(entry -> RawMaterials.name(entry.item()) + " " + entry.toGet()));
        this.stepList = addRenderableWidget(new ListPanel<RawPlanner.Step<Item>>(this.splitX, listTop,
                this.panelX + this.panelWidth - this.splitX, listHeight, Theme.ROW_HEIGHT,
                Component.translatable("litematicaplus.raw.column.steps"), this::renderStep)
                .onPress(this::pressStep)
                .describe(step -> RawMaterials.name(step.item()) + " " + toMake(step)));
        replan();
    }

    private int weight(FlatButton button) {
        return button.isToggle() || button == this.scopeButton ? 3 : 2;
    }

    private <T extends AbstractWidget> T hint(T widget, String key) {
        return Ui.hint(widget, Component.translatable(key));
    }

    private EditBox field(int x, int y, int width, String value, String hintKey, int maxLength) {
        EditBox box = new InkField(this.font, x, y, width, Theme.CONTROL_HEIGHT, Component.translatable(hintKey));
        box.setMaxLength(maxLength);
        box.setValue(value);
        box.setHint(Component.translatable(hintKey));
        box.setTextColor(Theme.TEXT);
        return addRenderableWidget(box);
    }

    private void exportRow(int y) {
        MaterialExport.Format[] formats = MaterialExport.Format.values();
        int back = 22;
        int count = formats.length;
        int cell = (this.panelWidth - back - Theme.GAP * count) / count;
        int x = this.panelX;
        addRenderableWidget(FlatButton.secondary(x, y, back, Component.literal("<"), button -> {
            this.exporting = false;
            rebuildWidgets();
        }));
        x += back + Theme.GAP;
        for (int i = 0; i < formats.length; i++) {
            MaterialExport.Format format = formats[i];
            int width = i == formats.length - 1 ? this.panelX + this.panelWidth - x : cell;
            addRenderableWidget(FlatButton.secondary(x, y, width,
                    Component.literal(format.extension().toUpperCase(Locale.ROOT)), button -> {
                        export(format);
                        this.exporting = false;
                        rebuildWidgets();
                    }));
            x += cell + Theme.GAP;
        }
    }

    private void update(Consumer<LitematicaPlusConfig> change, boolean rebuild) {
        LitematicaPlusConfig config = LitematicaPlusConfig.currentCopy();
        change.accept(config);
        LitematicaPlusConfig.save(config);
        if (rebuild) {
            rebuildWidgets();
        } else {
            replan();
        }
    }

    private void replan() {
        RecipeIndex.refresh();
        this.seenGeneration = RecipeIndex.generation();
        this.entries = this.source.entries().get();
        RawMaterials.Scope scope = this.source.scoped() && !LitematicaPlusConfig.current().rawScopeAll
                ? RawMaterials.Scope.MISSING : RawMaterials.Scope.ALL;
        this.plan = RecipeIndex.graph().size() == 0 && RecipeIndex.state() != RecipeIndex.State.FAILED
                ? RawPlanner.Plan.empty() : RawMaterials.plan(RawMaterials.targets(this.entries, scope));
        if (this.rawList == null) {
            return;
        }
        RawPlanner.Raw<Item> previousRaw = this.rawList.selectedItem();
        RawPlanner.Step<Item> previousStep = this.stepList.selectedItem();
        int rawScroll = this.rawList.scroll();
        int stepScroll = this.stepList.scroll();
        this.rawList.setItems(this.plan.raw());
        this.stepList.setItems(this.plan.steps());
        measureColumns();
        reselect(this.rawList, this.plan.raw(), previousRaw == null ? null : previousRaw.item(), RawPlanner.Raw::item);
        reselect(this.stepList, this.plan.steps(), previousStep == null ? null : previousStep.item(), RawPlanner.Step::item);
        this.rawList.setScroll(rawScroll);
        this.stepList.setScroll(stepScroll);
        this.highlight = this.selectedRaw == null || this.plan.rawOf(this.selectedRaw) == null ? Set.of()
                : this.plan.users(this.selectedRaw);
        Component waitTitle = Component.translatable("litematicaplus.raw.loading");
        boolean loading = RecipeIndex.graph().size() == 0 && RecipeIndex.state() != RecipeIndex.State.FAILED;
        Component emptyTitle = loading ? waitTitle : Component.translatable(this.source.custom()
                ? "litematicaplus.raw.custom_empty" : "litematicaplus.raw.empty");
        Component emptyHint = Component.translatable(loading ? "litematicaplus.raw.loading_hint" : this.source.custom()
                ? "litematicaplus.raw.custom_empty_hint" : "litematicaplus.raw.empty_hint");
        this.rawList.empty(emptyTitle, emptyHint);
        this.stepList.empty(loading ? waitTitle : Component.translatable("litematicaplus.raw.steps_empty"),
                Component.translatable(loading ? "litematicaplus.raw.loading_hint" : "litematicaplus.raw.steps_empty_hint"));
    }

    private static <T> void reselect(ListPanel<T> list, List<T> items, Item item, Function<T, Item> key) {
        if (item == null) {
            return;
        }
        for (int i = 0; i < items.size(); i++) {
            if (key.apply(items.get(i)) == item) {
                list.select(i);
                return;
            }
        }
        list.select(-1);
    }

    private void pressRaw(RawPlanner.Raw<Item> entry) {
        if (this.shiftClick) {
            if (entry.forced()) {
                String id = RawMaterials.id(entry.item());
                update(next -> next.rawForced.remove(id), false);
                status(Component.translatable("litematicaplus.raw.unforced", RawMaterials.name(entry.item())));
            } else {
                status(Component.translatable(entry.producible() ? "litematicaplus.raw.no_recipe_allowed"
                        : "litematicaplus.raw.no_recipe", RawMaterials.name(entry.item())));
            }
            return;
        }
        this.selectedRaw = this.selectedRaw == entry.item() ? null : entry.item();
        if (this.selectedRaw == null) {
            this.rawList.select(-1);
        }
        this.highlight = this.selectedRaw == null ? Set.of() : this.plan.users(this.selectedRaw);
    }

    private void pressStep(RawPlanner.Step<Item> step) {
        String id = RawMaterials.id(step.item());
        if (this.shiftClick) {
            update(next -> {
                if (!next.rawForced.contains(id)) {
                    next.rawForced.add(id);
                }
            }, false);
            status(Component.translatable("litematicaplus.raw.forced", RawMaterials.name(step.item())));
            return;
        }
        int alternatives = step.choice().alternatives();
        if (alternatives <= 1) {
            status(Component.translatable("litematicaplus.raw.single_recipe", RawMaterials.name(step.item())));
            return;
        }
        int next = (step.choice().index() + 1) % alternatives;
        update(config -> config.rawChoices.put(id, next), false);
        RawPlanner.Step<Item> now = this.plan.stepOf(step.item());
        if (now != null) {
            status(Component.translatable("litematicaplus.raw.recipe_changed", RawMaterials.name(step.item()),
                    now.choice().index() + 1, now.choice().alternatives()));
        }
    }

    private void rematch() {
        if (this.nameBox == null) {
            return;
        }
        String query = this.nameBox.getValue().strip();
        this.match = query.isEmpty() ? null : find(query);
    }

    private Item find(String query) {
        if (this.searchIndex == null) {
            List<SearchItem> index = new ArrayList<>();
            for (Item item : BuiltInRegistries.ITEM) {
                if (item == Items.AIR) {
                    continue;
                }
                index.add(new SearchItem(item, new ItemStack(item).getHoverName().getString().toLowerCase(Locale.ROOT),
                        BuiltInRegistries.ITEM.getKey(item).getPath()));
            }
            this.searchIndex = index;
        }
        String needle = query.toLowerCase(Locale.ROOT);
        String idNeedle = needle.startsWith("minecraft:") ? needle.substring(10) : needle;
        SearchItem best = null;
        int bestScore = Integer.MAX_VALUE;
        for (SearchItem candidate : this.searchIndex) {
            int score;
            if (candidate.name().equals(needle) || candidate.id().equals(idNeedle)) {
                score = 0;
            } else if (candidate.name().startsWith(needle) || candidate.id().startsWith(idNeedle)) {
                score = 1;
            } else if (SettingsView.matches(candidate.name() + " " + candidate.id(), needle)) {
                score = 2;
            } else {
                continue;
            }
            if (score < bestScore || score == bestScore && best != null && candidate.name().length() < best.name().length()) {
                best = candidate;
                bestScore = score;
            }
        }
        return best == null ? null : best.item();
    }

    private void addCustom() {
        if (this.nameBox == null || this.countBox == null) {
            return;
        }
        rematch();
        if (this.match == null) {
            status(Component.translatable(this.nameBox.getValue().isBlank() ? "litematicaplus.raw.custom_need_item"
                    : "litematicaplus.raw.custom_not_found", this.nameBox.getValue().strip()));
            return;
        }
        Item item = this.match;
        long count = CountParser.parse(this.countBox.getValue(), new ItemStack(item).getMaxStackSize());
        if (count <= 0L) {
            status(Component.translatable("litematicaplus.raw.custom_bad_count"));
            setFocused(this.countBox);
            return;
        }
        String id = RawMaterials.id(item);
        Long previous = LitematicaPlusConfig.current().customMaterials.get(id);
        update(config -> {
            Map<String, Long> next = new LinkedHashMap<>(config.customMaterials);
            next.put(id, count);
            config.customMaterials = next;
        }, false);
        status(previous == null ? Component.translatable("litematicaplus.raw.custom_added", RawMaterials.name(item), count)
                : Component.translatable("litematicaplus.raw.custom_updated", RawMaterials.name(item), count, previous));
        this.nameBox.setValue("");
        setFocused(this.nameBox);
    }

    private void removeCustom(Item item) {
        String id = RawMaterials.id(item);
        if (!this.source.custom() || !LitematicaPlusConfig.current().customMaterials.containsKey(id)) {
            return;
        }
        update(config -> {
            Map<String, Long> next = new LinkedHashMap<>(config.customMaterials);
            next.remove(id);
            config.customMaterials = next;
        }, false);
        status(Component.translatable("litematicaplus.raw.custom_removed", RawMaterials.name(item)));
    }

    private void clearCustom() {
        if (LitematicaPlusConfig.current().customMaterials.isEmpty()) {
            status(Component.translatable("litematicaplus.raw.custom_empty"));
            return;
        }
        update(config -> config.customMaterials = new LinkedHashMap<>(), false);
        status(Component.translatable("litematicaplus.raw.custom_cleared"));
    }

    private void copy() {
        StringBuilder builder = new StringBuilder();
        builder.append(Component.translatable("litematicaplus.materials.column_item").getString()).append('\t')
                .append(Component.translatable("litematicaplus.raw.column.to_get").getString()).append('\t')
                .append(Component.translatable("litematicaplus.raw.column.need").getString()).append('\n');
        for (RawPlanner.Raw<Item> entry : this.plan.raw()) {
            builder.append(RawMaterials.name(entry.item())).append('\t').append(entry.toGet()).append('\t')
                    .append(entry.need()).append('\n');
        }
        if (!this.plan.steps().isEmpty()) {
            builder.append('\n');
            for (RawPlanner.Step<Item> step : this.plan.steps()) {
                builder.append(RawMaterials.name(step.item())).append('\t').append(step.crafts()).append('\t')
                        .append(RawMaterials.recipeText(step.choice())).append('\n');
            }
        }
        this.minecraft.keyboardHandler.setClipboard(builder.toString());
        status(Component.translatable("litematicaplus.materials.copied"));
    }

    private void export(MaterialExport.Format format) {
        if (this.plan.isEmpty()) {
            status(Component.translatable("litematicaplus.materials.export_empty"));
            return;
        }
        LocalDateTime now = LocalDateTime.now();
        MaterialReport report = MaterialReport.of(this.source.title().getString(), this.entries, now)
                .withRaw(RawMaterials.section(this.plan));
        MaterialExport.export(report, MaterialExport.Labels.capture(), format, now).whenComplete((path, error) ->
                this.minecraft.execute(() -> exported(path, error)));
    }

    private void exported(Path path, Throwable error) {
        if (error != null || path == null) {
            LitematicaPlusClient.LOGGER.warn("Could not export the raw material list", error);
            Component message = Component.translatable("litematicaplus.materials.export_failed", Notify.describe(error));
            status(message);
            Notify.chat(message.copy().withStyle(ChatFormatting.RED));
            return;
        }
        Path absolute = path.toAbsolutePath();
        status(Component.translatable("litematicaplus.materials.export_saved", absolute.getFileName().toString()));
        MutableComponent link = Component.literal(absolute.getFileName().toString()).withStyle(style -> style
                .withColor(ChatFormatting.AQUA).withUnderlined(true).withClickEvent(new ClickEvent.OpenFile(absolute))
                .withHoverEvent(new HoverEvent.ShowText(Component.literal(absolute.toString()))));
        MutableComponent folder = Component.translatable("litematicaplus.materials.export_folder").copy()
                .withStyle(style -> style.withColor(ChatFormatting.GRAY).withUnderlined(true)
                        .withClickEvent(new ClickEvent.OpenFile(absolute.getParent())));
        Notify.chat(Component.translatable("litematicaplus.materials.export_saved", link).copy().append(" ").append(folder));
    }

    private void status(Component message) {
        this.status = message;
        this.statusTicks = 120;
    }

    @Override
    public void tick() {
        if (this.statusTicks > 0 && --this.statusTicks == 0) {
            this.status = null;
        }
        if (++this.ticks % REPLAN_TICKS == 0 || RecipeIndex.generation() != this.seenGeneration) {
            replan();
        }
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        this.shiftClick = event.hasShiftDown();
        if (event.button() == InputConstants.MOUSE_BUTTON_RIGHT && this.source.custom()) {
            Item item = itemAt(event.x(), event.y());
            if (item != null) {
                removeCustom(item);
                return true;
            }
        }
        try {
            return super.mouseClicked(event, doubleClick);
        } finally {
            this.shiftClick = false;
        }
    }

    private Item itemAt(double mouseX, double mouseY) {
        RawPlanner.Raw<Item> raw = rowAt(this.rawList, mouseX, mouseY);
        if (raw != null) {
            return raw.item();
        }
        RawPlanner.Step<Item> step = rowAt(this.stepList, mouseX, mouseY);
        return step == null ? null : step.item();
    }

    private static <T> T rowAt(ListPanel<T> list, double mouseX, double mouseY) {
        if (list == null || !list.isMouseOver(mouseX, mouseY)) {
            return null;
        }
        int index = (int) ((mouseY - list.getY() - 1 + list.scroll()) / Theme.ROW_HEIGHT);
        return index >= 0 && index < list.items().size() ? list.items().get(index) : null;
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        boolean enter = event.key() == InputConstants.KEY_RETURN || event.key() == InputConstants.KEY_NUMPADENTER;
        if (enter && (getFocused() == this.nameBox || getFocused() == this.countBox) && this.nameBox != null) {
            if (getFocused() == this.nameBox && this.countBox.getValue().isBlank()) {
                setFocused(this.countBox);
            } else {
                addCustom();
            }
            return true;
        }
        if (event.key() == InputConstants.KEY_DELETE && this.source.custom() && !(getFocused() instanceof EditBox)) {
            Item item = getFocused() == this.stepList && this.stepList.selectedItem() != null
                    ? this.stepList.selectedItem().item()
                    : this.rawList.selectedItem() != null ? this.rawList.selectedItem().item() : null;
            if (item != null) {
                removeCustom(item);
                return true;
            }
        }
        return super.keyPressed(event);
    }

    private void renderRaw(GuiGraphicsExtractor graphics, Font rowFont, RawPlanner.Raw<Item> entry, int x, int y,
            int rowWidth, int rowHeight, boolean isSelected, boolean hovered) {
        if (hovered) {
            this.hoveredRaw = entry;
        }
        ItemStack stack = InfoHud.stackOf(entry.item());
        graphics.fakeItem(stack, x + 5, y + 3);
        int textY = y + (rowHeight - 8) / 2;
        int right = x + rowWidth - 6;
        Ui.right(graphics, rowFont, toGetText(entry), right, textY, entry.toGet() > 0L ? Theme.TEXT_BRIGHT : Theme.SUCCESS);
        Ui.right(graphics, rowFont, entry.fromStock() > 0L ? Long.toString(entry.fromStock()) : "-",
                right - this.getColumn - COLUMN_GAP, textY, entry.fromStock() > 0L ? Theme.SUCCESS : Theme.TEXT_DISABLED);
        String name = RawMaterials.name(entry.item());
        if (entry.forced()) {
            name = Component.translatable("litematicaplus.raw.forced_name", name).getString();
        }
        int nameRight = right - this.getColumn - this.haveColumn - COLUMN_GAP * 2;
        Ui.clipped(graphics, rowFont, name, x + 26, textY, nameRight - x - 26,
                entry.target() && this.source.custom() ? Theme.TEXT_BRIGHT : Theme.TEXT);
    }

    private String toGetText(RawPlanner.Raw<Item> entry) {
        return this.showStacks ? compact(entry.toGet(), InfoHud.stackOf(entry.item())) : Long.toString(entry.toGet());
    }

    private void measureColumns() {
        Font font = this.font;
        int have = font.width(Component.translatable("litematicaplus.raw.column.stock"));
        int get = font.width(Component.translatable("litematicaplus.raw.column.to_get"));
        this.showStacks = this.rawList != null && this.rawList.getWidth() >= 300;
        for (RawPlanner.Raw<Item> entry : this.plan.raw()) {
            have = Math.max(have, font.width(Long.toString(entry.fromStock())));
            get = Math.max(get, font.width(toGetText(entry)));
        }
        this.haveColumn = have;
        this.getColumn = get;
        int inputs = 1;
        int counts = font.width("9");
        int outputs = font.width("9");
        int make = font.width(Component.translatable("litematicaplus.raw.column.make"));
        for (RawPlanner.Step<Item> step : this.plan.steps()) {
            RawPlanner.Choice<Item> choice = step.choice();
            inputs = Math.max(inputs, Math.min(MAX_RECIPE_INPUTS, choice.option().inputs().size()));
            for (RecipeGraph.Input<Item> input : choice.option().inputs()) {
                counts = Math.max(counts, font.width(Integer.toString(input.count())));
            }
            outputs = Math.max(outputs, font.width(outputText(step)));
            make = Math.max(make, font.width(Long.toString(toMake(step))));
        }
        this.slotWidth = 17 + counts + 3;
        this.recipeSlots = inputs;
        this.outputWidth = outputs;
        this.craftsWidth = make;
    }

    private static long toMake(RawPlanner.Step<Item> step) {
        return Math.max(0L, step.demand() - step.fromStock());
    }

    private static String outputText(RawPlanner.Step<Item> step) {
        return ARROW + " " + step.choice().option().count() + "  "
                + Component.translatable("litematicaplus.raw.crafts_short", step.crafts()).getString();
    }

    private void renderStep(GuiGraphicsExtractor graphics, Font rowFont, RawPlanner.Step<Item> step, int x, int y,
            int rowWidth, int rowHeight, boolean isSelected, boolean hovered) {
        if (hovered) {
            this.hoveredStep = step;
        }
        if (this.highlight.contains(step.item())) {
            graphics.fill(x, y, x + 2, y + rowHeight, Theme.AMBER);
        }
        ItemStack stack = InfoHud.stackOf(step.item());
        graphics.fakeItem(stack, x + 5, y + 3);
        int textY = y + (rowHeight - 8) / 2;
        int right = x + rowWidth - 6;
        RawPlanner.Choice<Item> choice = step.choice();
        int outputLeft = right - this.outputWidth;
        String output = ARROW + " " + choice.option().count();
        graphics.text(rowFont, output, outputLeft, textY, choice.alternatives() > 1 ? Theme.AMBER : Theme.TEXT_DIM, false);
        graphics.text(rowFont, "  " + Component.translatable("litematicaplus.raw.crafts_short", step.crafts()).getString(),
                outputLeft + rowFont.width(output), textY, Theme.TEXT_DISABLED, false);
        int recipeLeft = outputLeft - 4 - this.slotWidth * this.recipeSlots;
        List<RecipeGraph.Input<Item>> inputs = choice.option().inputs();
        int shown = Math.min(this.recipeSlots, inputs.size());
        for (int i = 0; i < shown; i++) {
            int slotX = recipeLeft + this.slotWidth * i;
            boolean more = i == shown - 1 && inputs.size() > shown;
            graphics.fakeItem(InfoHud.stackOf(choice.inputs().get(i)), slotX, y + 3);
            graphics.text(rowFont, more ? "+" : Integer.toString(inputs.get(i).count()), slotX + 18, textY,
                    more ? Theme.TEXT_DIM : Theme.TEXT, false);
        }
        int craftsRight = recipeLeft - COLUMN_GAP;
        Ui.right(graphics, rowFont, Long.toString(toMake(step)), craftsRight, textY, Theme.TEXT_BRIGHT);
        int nameRight = craftsRight - this.craftsWidth - COLUMN_GAP;
        Ui.clipped(graphics, rowFont, RawMaterials.name(step.item()), x + 26, textY, Math.max(10, nameRight - x - 26),
                step.target() ? Theme.TEXT_BRIGHT : Theme.TEXT);
    }

    private static String compact(long count, ItemStack stack) {
        int size = Math.max(1, stack.getMaxStackSize());
        if (size <= 1 || count < size) {
            return Long.toString(count);
        }
        long stacks = count / size;
        long rest = count % size;
        return Component.translatable(rest == 0L ? "litematicaplus.materials.stacks" : "litematicaplus.materials.stacks_rest",
                count, stacks, rest).getString();
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        graphics.fill(0, 0, this.width, this.height, Theme.SIDEBAR);
        this.hoveredRaw = null;
        this.hoveredStep = null;
        Ui.clipped(graphics, this.font, this.title.getString(), this.panelX, MARGIN, this.panelWidth / 2, Theme.TEXT_BRIGHT);
        Ui.right(graphics, this.font, summary().getString(), this.panelX + this.panelWidth, MARGIN, Theme.TEXT_DIM);
        if (this.rawList != null) {
            int rawRight = this.panelX + this.rawList.getWidth() - 7
                    - (this.plan.raw().size() * Theme.ROW_HEIGHT > this.rawList.getHeight() - 2 ? 5 : 0);
            graphics.text(this.font, Component.translatable("litematicaplus.raw.column.raw"), this.panelX + 27, this.headerY,
                    Theme.ACCENT, false);
            Ui.right(graphics, this.font, Component.translatable("litematicaplus.raw.column.to_get").getString(), rawRight,
                    this.headerY, Theme.ACCENT);
            Ui.right(graphics, this.font, Component.translatable("litematicaplus.raw.column.stock").getString(),
                    rawRight - this.getColumn - COLUMN_GAP, this.headerY, Theme.ACCENT);
            graphics.text(this.font, Component.translatable("litematicaplus.raw.column.steps"), this.splitX + 27,
                    this.headerY, Theme.ACCENT, false);
            int stepRight = this.panelX + this.panelWidth - 7
                    - (this.plan.steps().size() * Theme.ROW_HEIGHT > this.stepList.getHeight() - 2 ? 5 : 0);
            int recipeLeft = stepRight - this.outputWidth - 4 - this.slotWidth * this.recipeSlots;
            graphics.text(this.font, Component.translatable("litematicaplus.raw.column.recipe"), recipeLeft, this.headerY,
                    Theme.ACCENT, false);
            Ui.right(graphics, this.font, Component.translatable("litematicaplus.raw.column.make").getString(),
                    recipeLeft - COLUMN_GAP, this.headerY, Theme.ACCENT);
        }
        if (this.source.custom() && this.nameBox != null) {
            int textY = this.addRowY + (Theme.CONTROL_HEIGHT - 8) / 2;
            if (this.match != null) {
                graphics.fakeItem(InfoHud.stackOf(this.match), this.matchX, this.addRowY + 2);
                Ui.clipped(graphics, this.font, RawMaterials.name(this.match), this.matchX + 20, textY,
                        this.matchWidth - 20, Theme.TEXT_BRIGHT);
            } else {
                Ui.clipped(graphics, this.font, Component.translatable(this.nameBox.getValue().isBlank()
                        ? "litematicaplus.raw.custom_pick" : "litematicaplus.raw.custom_no_match").getString(),
                        this.matchX, textY, this.matchWidth, Theme.TEXT_DIM);
            }
        }
        super.extractRenderState(graphics, mouseX, mouseY, delta);
        Ui.wrapped(graphics, this.font, footer(), this.panelX, this.footerY, this.panelWidth, this.footerLines,
                Theme.TEXT_DIM, mouseX, mouseY);
        if (this.hoveredStep != null) {
            graphics.setComponentTooltipForNextFrame(this.font, stepTooltip(this.hoveredStep), mouseX, mouseY);
        } else if (this.hoveredRaw != null) {
            graphics.setComponentTooltipForNextFrame(this.font, rawTooltip(this.hoveredRaw), mouseX, mouseY);
        }
    }

    private Component summary() {
        if (this.status != null) {
            return this.status;
        }
        if (RecipeIndex.graph().size() == 0) {
            return Component.translatable(RecipeIndex.state() == RecipeIndex.State.FAILED ? "litematicaplus.raw.index_failed"
                    : "litematicaplus.raw.loading");
        }
        return Component.translatable("litematicaplus.raw.summary", this.plan.raw().size(), this.plan.toGet(),
                this.plan.steps().size());
    }

    private Component footer() {
        Component hint = Component.translatable(this.source.custom() ? "litematicaplus.raw.footer_custom"
                : "litematicaplus.raw.footer");
        if (RecipeIndex.graph().size() > 0 && !RecipeIndex.full()) {
            return hint.copy().append(" · ").append(Component.translatable("litematicaplus.raw.partial"));
        }
        return hint;
    }

    private List<Component> stepTooltip(RawPlanner.Step<Item> step) {
        List<Component> lines = new ArrayList<>();
        ItemStack stack = new ItemStack(step.item());
        lines.add(stack.getHoverName().copy().withStyle(ChatFormatting.WHITE));
        lines.add(Component.translatable("litematicaplus.raw.tip.need", step.demand()).withStyle(ChatFormatting.GRAY));
        if (step.fromStock() > 0L) {
            lines.add(Component.translatable("litematicaplus.raw.tip.stock", step.fromStock()).withStyle(ChatFormatting.GREEN));
        }
        lines.add(Component.translatable("litematicaplus.raw.tip.crafts", step.crafts(), step.produced())
                .withStyle(ChatFormatting.GRAY));
        if (step.leftover() > 0L) {
            lines.add(Component.translatable("litematicaplus.raw.tip.leftover", step.leftover())
                    .withStyle(ChatFormatting.GOLD));
        }
        lines.add(Component.translatable("litematicaplus.raw.tip.recipe", RawMaterials.recipeText(step.choice()))
                .withStyle(ChatFormatting.GRAY));
        if (step.choice().alternatives() > 1) {
            lines.add(Component.translatable("litematicaplus.raw.tip.switch", step.choice().index() + 1,
                    step.choice().alternatives()).withStyle(ChatFormatting.DARK_AQUA));
        }
        lines.add(Component.translatable("litematicaplus.raw.tip.force").withStyle(ChatFormatting.DARK_AQUA));
        if (this.source.custom() && step.target()) {
            lines.add(Component.translatable("litematicaplus.raw.tip.remove").withStyle(ChatFormatting.DARK_AQUA));
        }
        return lines;
    }

    private List<Component> rawTooltip(RawPlanner.Raw<Item> entry) {
        List<Component> lines = new ArrayList<>();
        ItemStack stack = new ItemStack(entry.item());
        lines.add(stack.getHoverName().copy().withStyle(ChatFormatting.WHITE));
        lines.add(Component.translatable("litematicaplus.raw.tip.need", entry.need()).withStyle(ChatFormatting.GRAY));
        if (entry.fromStock() > 0L) {
            lines.add(Component.translatable("litematicaplus.raw.tip.stock", entry.fromStock()).withStyle(ChatFormatting.GREEN));
        }
        lines.add(Component.translatable("litematicaplus.raw.tip.to_get", compact(entry.toGet(), stack))
                .withStyle(ChatFormatting.GOLD));
        Set<Item> users = this.plan.users(entry.item());
        if (!users.isEmpty()) {
            StringBuilder names = new StringBuilder();
            int count = 0;
            for (Item user : users) {
                if (count++ >= 4) {
                    names.append(", ...");
                    break;
                }
                if (names.length() > 0) {
                    names.append(", ");
                }
                names.append(RawMaterials.name(user));
            }
            lines.add(Component.translatable("litematicaplus.raw.tip.used_by", names.toString())
                    .withStyle(ChatFormatting.GRAY));
            lines.add(Component.translatable("litematicaplus.raw.tip.select").withStyle(ChatFormatting.DARK_AQUA));
        }
        if (entry.forced()) {
            lines.add(Component.translatable("litematicaplus.raw.tip.unforce").withStyle(ChatFormatting.DARK_AQUA));
        } else if (entry.producible()) {
            lines.add(Component.translatable("litematicaplus.raw.tip.no_allowed").withStyle(ChatFormatting.DARK_GRAY));
        }
        if (this.source.custom() && entry.target()) {
            lines.add(Component.translatable("litematicaplus.raw.tip.remove").withStyle(ChatFormatting.DARK_AQUA));
        }
        return lines;
    }

    @Override
    public void onClose() {
        this.minecraft.gui.setScreen(this.parent);
    }
}
