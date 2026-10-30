package org.lts.litematicaplus.gui;

import it.unimi.dsi.fastutil.objects.Reference2LongMap;
import it.unimi.dsi.fastutil.objects.Reference2LongOpenHashMap;
import java.io.IOException;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.RejectedExecutionException;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import org.lts.litematicaplus.LitematicaPlusClient;
import org.lts.litematicaplus.hud.InfoHud;
import org.lts.litematicaplus.materials.MaterialExport;
import org.lts.litematicaplus.materials.MaterialList;
import org.lts.litematicaplus.materials.MaterialReport;
import org.lts.litematicaplus.schematic.Schematic;
import org.lts.litematicaplus.schematic.SchematicIo;
import org.lts.litematicaplus.schematic.SchematicRegion;
import org.lts.litematicaplus.util.Notify;
import org.lts.litematicaplus.util.Workers;

final class SchematicMaterialsScreen extends Screen {
    private static final int MARGIN = 16;
    private static final int MAX_WIDTH = 760;

    private static boolean byName;

    private final Screen parent;
    private final Path file;
    private final String schematicName;
    private List<MaterialList.Entry> all = List.of();
    private List<MaterialList.Entry> shown = List.of();
    private boolean loading = true;
    private Component failure;
    private long blockCount;
    private ListPanel<MaterialList.Entry> list;
    private EditBox search;
    private String query = "";
    private boolean exporting;
    private Component status;
    private int statusTicks;
    private int panelX;
    private int panelWidth;
    private int headerY;
    private int columnWidth;
    private boolean showStacks;

    SchematicMaterialsScreen(Screen parent, Path file, String schematicName) {
        super(Component.translatable("litematicaplus.schematics.materials.title", schematicName));
        this.parent = parent;
        this.file = file;
        this.schematicName = schematicName;
        load();
    }

    private void load() {
        Path path = this.file;
        try {
            Workers.io().execute(() -> {
                List<MaterialList.Entry> result = null;
                long blocks = 0L;
                Component error = null;
                try {
                    Schematic schematic = SchematicIo.read(path);
                    Reference2LongOpenHashMap<Item> totals = new Reference2LongOpenHashMap<>();
                    for (SchematicRegion region : schematic.regions().values()) {
                        BlockState[] palette = region.palette();
                        long[] counts = new long[palette.length];
                        long volume = region.volume();
                        for (long i = 0L; i < volume; i++) {
                            int id = region.states().get(i);
                            if (id >= 0 && id < counts.length) {
                                counts[id]++;
                            }
                        }
                        for (int i = 0; i < palette.length; i++) {
                            long count = counts[i];
                            if (count <= 0L || palette[i].isAir()) {
                                continue;
                            }
                            MaterialList.requirements(palette[i], (item, multiplier) -> totals.addTo(item, count * multiplier));
                        }
                        blocks += region.countNonAir();
                    }
                    result = new ArrayList<>(totals.size());
                    for (Reference2LongMap.Entry<Item> entry : totals.reference2LongEntrySet()) {
                        result.add(new MaterialList.Entry(entry.getKey(), entry.getLongValue(), entry.getLongValue(), 0L));
                    }
                } catch (IOException | RuntimeException | OutOfMemoryError exception) {
                    LitematicaPlusClient.LOGGER.warn("Could not read the materials of {}", path, exception);
                    error = Notify.describe(exception);
                }
                List<MaterialList.Entry> entries = result;
                long total = blocks;
                Component message = error;
                Minecraft.getInstance().execute(() -> {
                    this.loading = false;
                    this.failure = message;
                    this.all = entries == null ? List.of() : entries;
                    this.blockCount = total;
                    if (this.list != null) {
                        refilter();
                    }
                });
            });
        } catch (RejectedExecutionException exception) {
            this.loading = false;
            this.failure = Notify.describe(exception);
        }
    }

    @Override
    protected void init() {
        this.panelWidth = Math.min(MAX_WIDTH, this.width - MARGIN * 2);
        this.panelX = (this.width - this.panelWidth) / 2;
        int y = MARGIN + 14;
        if (this.exporting) {
            exportRow(y);
        } else {
            int quarter = (this.panelWidth - Theme.GAP * 2) / 4;
            addRenderableWidget(FlatButton.secondary(this.panelX, y, quarter, Component.translatable(
                    "litematicaplus.materials.sort", Component.translatable(byName ? "litematicaplus.materials.sort.name"
                            : "litematicaplus.materials.sort.total")), button -> {
                                byName = !byName;
                                rebuild();
                            }));
            addRenderableWidget(FlatButton.secondary(this.panelX + quarter + Theme.GAP, y, quarter,
                    Component.translatable("litematicaplus.materials.export"), button -> {
                        this.exporting = true;
                        rebuild();
                    }));
            addRenderableWidget(FlatButton.secondary(this.panelX + (quarter + Theme.GAP) * 2, y, quarter,
                    Component.translatable("litematicaplus.materials.export_clipboard"), button -> copy()));
            addRenderableWidget(FlatButton.primary(this.panelX + (quarter + Theme.GAP) * 3, y,
                    this.panelWidth - (quarter + Theme.GAP) * 3, Component.translatable("gui.back"), button -> onClose()));
        }
        y += Theme.CONTROL_HEIGHT + 4;
        this.search = new InkField(this.font, this.panelX, y, this.panelWidth, Theme.CONTROL_HEIGHT,
                Component.translatable("litematicaplus.materials.search"));
        this.search.setMaxLength(64);
        this.search.setValue(this.query);
        this.search.setHint(Component.translatable("litematicaplus.materials.search"));
        this.search.setTextColor(Theme.TEXT);
        this.search.setResponder(value -> {
            if (!value.equals(this.query)) {
                this.query = value;
                refilter();
                this.list.setScroll(0);
            }
        });
        addRenderableWidget(this.search);
        this.headerY = y + Theme.CONTROL_HEIGHT + 5;
        int listTop = y + Theme.CONTROL_HEIGHT + 16;
        this.columnWidth = Math.max(60, Math.min(140, this.panelWidth / 4));
        this.showStacks = this.panelWidth >= 400;
        this.list = addRenderableWidget(new ListPanel<MaterialList.Entry>(this.panelX, listTop, this.panelWidth,
                this.height - listTop - MARGIN, Theme.ROW_HEIGHT, this.title, this::renderRow)
                .describe(entry -> InfoHud.stackOf(entry.item()).getHoverName().getString() + " " + entry.total())
                .empty(Component.translatable("litematicaplus.materials.empty_title"),
                        Component.translatable("litematicaplus.schematics.materials.empty_hint")));
        refilter();
    }

    private void rebuild() {
        this.rebuildWidgets();
    }

    private void exportRow(int y) {
        MaterialExport.Format[] formats = MaterialExport.Format.values();
        int back = 22;
        int count = formats.length;
        int cell = (this.panelWidth - back - Theme.GAP * count) / count;
        int x = this.panelX;
        addRenderableWidget(FlatButton.secondary(x, y, back, Component.literal("<"), button -> {
            this.exporting = false;
            rebuild();
        }));
        x += back + Theme.GAP;
        for (int i = 0; i < formats.length; i++) {
            MaterialExport.Format format = formats[i];
            int width = i == formats.length - 1 ? this.panelX + this.panelWidth - x : cell;
            addRenderableWidget(FlatButton.secondary(x, y, width,
                    Component.literal(format.extension().toUpperCase(Locale.ROOT)), button -> {
                        export(format);
                        this.exporting = false;
                        rebuild();
                    }));
            x += cell + Theme.GAP;
        }
    }

    private void refilter() {
        List<MaterialList.Entry> next = new ArrayList<>();
        String needle = this.query.strip();
        for (MaterialList.Entry entry : this.all) {
            if (needle.isEmpty() || SettingsView.matches(InfoHud.stackOf(entry.item()).getHoverName().getString() + " "
                    + BuiltInRegistries.ITEM.getKey(entry.item()), needle)) {
                next.add(entry);
            }
        }
        Comparator<MaterialList.Entry> byTitle = Comparator
                .comparing((MaterialList.Entry entry) -> InfoHud.stackOf(entry.item()).getHoverName().getString());
        next.sort(byName ? byTitle : Comparator.comparingLong(MaterialList.Entry::total).reversed().thenComparing(byTitle));
        this.shown = next;
        if (this.list != null) {
            this.list.empty(this.failure != null ? Component.translatable("litematicaplus.schematics.unreadable", this.failure)
                    : Component.translatable(this.loading ? "litematicaplus.schematics.loading"
                            : "litematicaplus.materials.empty_title"),
                    Component.translatable(needle.isEmpty() ? "litematicaplus.schematics.materials.empty_hint"
                            : "litematicaplus.materials.search_empty_hint"));
            this.list.setItems(next);
        }
    }

    private void copy() {
        StringBuilder builder = new StringBuilder();
        builder.append(Component.translatable("litematicaplus.materials.column_item").getString()).append('\t')
                .append(Component.translatable("litematicaplus.materials.column_total").getString()).append('\n');
        for (MaterialList.Entry entry : this.shown) {
            builder.append(InfoHud.stackOf(entry.item()).getHoverName().getString()).append('\t').append(entry.total())
                    .append('\n');
        }
        this.minecraft.keyboardHandler.setClipboard(builder.toString());
        status(Component.translatable("litematicaplus.materials.copied"));
    }

    private void export(MaterialExport.Format format) {
        if (this.all.isEmpty()) {
            status(Component.translatable("litematicaplus.materials.export_empty"));
            return;
        }
        LocalDateTime now = LocalDateTime.now();
        MaterialReport report = MaterialReport.of(this.schematicName, this.all, now);
        MaterialExport.export(report, MaterialExport.Labels.capture(), format, now).whenComplete((path, error) ->
                this.minecraft.execute(() -> exported(path, error)));
    }

    private void exported(Path path, Throwable error) {
        if (error != null || path == null) {
            LitematicaPlusClient.LOGGER.warn("Could not export the material list", error);
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
    }

    private void renderRow(GuiGraphicsExtractor graphics, Font rowFont, MaterialList.Entry entry, int x, int y,
            int rowWidth, int rowHeight, boolean isSelected, boolean hovered) {
        ItemStack stack = InfoHud.stackOf(entry.item());
        graphics.fakeItem(stack, x + 5, y + 3);
        int textY = y + (rowHeight - 8) / 2;
        int right = x + rowWidth - 6;
        Ui.right(graphics, rowFont, Long.toString(entry.total()), right, textY, Theme.TEXT_BRIGHT);
        if (this.showStacks) {
            Ui.right(graphics, rowFont, stacks(entry.total(), stack.getMaxStackSize()), right - this.columnWidth, textY,
                    Theme.TEXT_DIM);
        }
        int nameWidth = rowWidth - 30 - (this.showStacks ? this.columnWidth * 2 : this.columnWidth) - 16;
        Ui.clipped(graphics, rowFont, stack.getHoverName().getString(), x + 26, textY, nameWidth, Theme.TEXT);
    }

    private static String stacks(long count, int stackSize) {
        if (stackSize <= 1 || count < stackSize) {
            return "";
        }
        long full = count / stackSize;
        long rest = count % stackSize;
        return Component.translatable(rest == 0L ? "litematicaplus.schematics.materials.stacks"
                : "litematicaplus.schematics.materials.stacks_rest", full, rest).getString();
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        graphics.fill(0, 0, this.width, this.height, Theme.SIDEBAR);
        Component summary = this.status != null ? this.status
                : this.loading ? Component.translatable("litematicaplus.schematics.loading")
                        : Component.translatable("litematicaplus.schematics.materials.summary", this.all.size(), this.blockCount);
        Ui.clipped(graphics, this.font, this.title.getString(), this.panelX, MARGIN, this.panelWidth * 2 / 3, Theme.TEXT_BRIGHT);
        Ui.right(graphics, this.font, summary.getString(), this.panelX + this.panelWidth, MARGIN, Theme.TEXT_DIM);
        int right = this.panelX + this.panelWidth - 7 - (this.list != null && this.list.items().size() * Theme.ROW_HEIGHT
                > this.list.getHeight() ? 5 : 0);
        graphics.text(this.font, Component.translatable("litematicaplus.materials.column_item"), this.panelX + 27,
                this.headerY, Theme.ACCENT, false);
        Ui.right(graphics, this.font, Component.translatable("litematicaplus.materials.column_total").getString(), right,
                this.headerY, Theme.ACCENT);
        super.extractRenderState(graphics, mouseX, mouseY, delta);
    }

    @Override
    public void onClose() {
        this.minecraft.gui.setScreen(this.parent);
    }
}
