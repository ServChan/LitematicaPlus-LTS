package org.lts.litematicaplus.gui;

import com.mojang.blaze3d.Blaze3D;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.RejectedExecutionException;
import java.util.stream.Stream;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;
import org.lts.litematicaplus.LitematicaPlusClient;
import org.lts.litematicaplus.placement.PlacementManager;
import org.lts.litematicaplus.placement.SchematicPlacement;
import org.lts.litematicaplus.preview.PreviewService;
import org.lts.litematicaplus.schematic.SchematicIo;
import org.lts.litematicaplus.schematic.SchematicMetadata;
import org.lts.litematicaplus.util.Notify;
import org.lts.litematicaplus.util.Workers;

final class SchematicsView extends TabView {
    private record Entry(Path path, boolean directory, boolean parent, String name, long size, long modified) {
    }

    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("yyyy-MM-dd").withZone(ZoneId.systemDefault());
    private static final Map<String, SchematicMetadata> METADATA = new ConcurrentHashMap<>();
    private static Path currentDirectory;
    private static String filter = "";

    private ListPanel<Entry> list;
    private FlatButton placeButton;
    private List<Entry> entries = List.of();
    private boolean loading;
    private int request;
    private Entry selected;
    private SchematicMetadata metadata;
    private Component metadataError;
    private int detailX;
    private int detailWidth;
    private PreviewWidget preview;
    private int previewSide;

    SchematicsView(HubScreen screen) {
        super(screen);
    }

    @Override
    Component summary() {
        Path root = PlacementManager.schematicsDirectory();
        Path directory = directory();
        String relative = directory.startsWith(root) ? root.relativize(directory).toString().replace('\\', '/') : "";
        return Component.literal(root.getFileName() + (relative.isEmpty() ? "" : "/" + relative));
    }

    private static Path directory() {
        Path root = PlacementManager.schematicsDirectory();
        if (currentDirectory == null || !currentDirectory.startsWith(root)) {
            currentDirectory = root;
        }
        return currentDirectory;
    }

    @Override
    void init() {
        boolean previews = PreviewService.enabled();
        this.detailWidth = Math.max(130, Math.min(previews ? 270 : 230, this.width * (previews ? 46 : 42) / 100));
        int listWidth = this.width - this.detailWidth - Theme.GAP;
        this.detailX = this.left + listWidth + Theme.GAP;
        EditBox search = field(this.left, this.top, listWidth, filter, tr("litematicaplus.schematics.search"), 64);
        search.setResponder(value -> {
            filter = value;
            applyFilter();
        });
        this.list = add(new ListPanel<Entry>(this.left, this.top + Theme.CONTROL_HEIGHT + 4, listWidth,
                this.height - Theme.CONTROL_HEIGHT - 4, Theme.ROW_HEIGHT, tr("litematicaplus.tab.schematics"),
                this::renderRow)
                .describe(Entry::name)
                .onSelect(this::select)
                .onActivate(this::activate)
                .empty(tr("litematicaplus.schematics.empty_title"), tr("litematicaplus.schematics.empty_hint")));
        int buttonY = this.top + this.height - Theme.CONTROL_HEIGHT;
        int half = (this.detailWidth - Theme.GAP) / 2;
        this.placeButton = add(FlatButton.primary(this.detailX, buttonY - Theme.CONTROL_HEIGHT - 4, this.detailWidth,
                tr("litematicaplus.schematics.place"), button -> place()));
        add(FlatButton.secondary(this.detailX, buttonY, half, tr("litematicaplus.schematics.open_folder"),
                button -> openFolder()));
        add(FlatButton.secondary(this.detailX + half + Theme.GAP, buttonY, this.detailWidth - half - Theme.GAP,
                tr("litematicaplus.schematics.refresh"), button -> reload()));
        int panelHeight = this.height - (Theme.CONTROL_HEIGHT + 4) * 2;
        this.previewSide = previews ? Math.min(this.detailWidth - 16, panelHeight - 60) : 0;
        this.preview = null;
        if (this.previewSide >= 64) {
            this.preview = add(new PreviewWidget(this.detailX + 8, this.top + 8, this.detailWidth - 16, this.previewSide,
                    previewSource(this.selected), previewName(this.selected)));
        } else {
            this.previewSide = 0;
        }
        updateButtons();
        if (this.entries.isEmpty() && !this.loading) {
            reload();
        } else {
            applyFilter();
        }
    }

    private void updateButtons() {
        if (this.placeButton != null) {
            this.placeButton.active = this.selected != null && !this.selected.directory() && this.minecraft.player != null;
        }
    }

    private void reload() {
        Path directory = directory();
        int id = ++this.request;
        this.loading = true;
        try {
            Workers.io().execute(() -> {
                List<Entry> result = listDirectory(directory);
                this.minecraft.execute(() -> {
                    if (id != this.request) {
                        return;
                    }
                    this.loading = false;
                    this.entries = result;
                    applyFilter();
                });
            });
        } catch (RejectedExecutionException exception) {
            this.loading = false;
        }
    }

    private static List<Entry> listDirectory(Path directory) {
        List<Entry> result = new ArrayList<>();
        Path root = PlacementManager.schematicsDirectory();
        try {
            Files.createDirectories(directory);
            if (!directory.equals(root)) {
                result.add(new Entry(directory.getParent(), true, true, "..", 0L, 0L));
            }
            List<Entry> found = new ArrayList<>();
            try (Stream<Path> stream = Files.list(directory)) {
                stream.forEach(path -> {
                    try {
                        if (Files.isDirectory(path)) {
                            found.add(new Entry(path, true, false, path.getFileName().toString(), 0L,
                                    Files.getLastModifiedTime(path).toMillis()));
                        } else if (SchematicIo.isSupported(path)) {
                            found.add(new Entry(path, false, false, path.getFileName().toString(), Files.size(path),
                                    Files.getLastModifiedTime(path).toMillis()));
                        }
                    } catch (IOException exception) {
                        LitematicaPlusClient.LOGGER.debug("Skipping unreadable entry {}", path);
                    }
                });
            }
            found.sort(Comparator.comparing((Entry entry) -> !entry.directory())
                    .thenComparing(entry -> entry.name().toLowerCase(Locale.ROOT)));
            result.addAll(found);
        } catch (IOException | RuntimeException exception) {
            LitematicaPlusClient.LOGGER.warn("Could not list schematics in {}", directory, exception);
        }
        return result;
    }

    private void applyFilter() {
        if (this.list == null) {
            return;
        }
        String needle = filter.strip().toLowerCase(Locale.ROOT);
        List<Entry> shown = new ArrayList<>();
        for (Entry entry : this.entries) {
            if (needle.isEmpty() || entry.parent() || entry.name().toLowerCase(Locale.ROOT).contains(needle)) {
                shown.add(entry);
            }
        }
        this.list.setItems(shown);
        if (this.selected != null && !shown.contains(this.selected)) {
            select(null);
        }
        this.list.selectItem(this.selected);
    }

    private static PreviewService.Source previewSource(Entry entry) {
        return entry == null || entry.directory() ? null
                : PreviewService.Source.file(entry.path(), entry.size(), entry.modified());
    }

    private static String previewName(Entry entry) {
        return entry == null ? "" : entry.name();
    }

    private void select(Entry entry) {
        this.selected = entry;
        this.metadata = null;
        this.metadataError = null;
        if (this.preview != null) {
            this.preview.setSource(previewSource(entry), previewName(entry));
        }
        updateButtons();
        if (entry == null || entry.directory()) {
            return;
        }
        String key = entry.path() + "|" + entry.modified() + "|" + entry.size();
        SchematicMetadata cached = METADATA.get(key);
        if (cached != null) {
            this.metadata = cached;
            return;
        }
        try {
            Workers.io().execute(() -> {
                SchematicMetadata loaded = null;
                Component error = null;
                try {
                    loaded = SchematicIo.readMetadata(entry.path());
                    if (METADATA.size() > 512) {
                        METADATA.clear();
                    }
                    METADATA.put(key, loaded);
                } catch (IOException | RuntimeException exception) {
                    error = Notify.describe(exception);
                }
                SchematicMetadata result = loaded;
                Component message = error;
                this.minecraft.execute(() -> {
                    if (this.selected == entry) {
                        this.metadata = result;
                        this.metadataError = message;
                    }
                });
            });
        } catch (RejectedExecutionException exception) {
            this.metadataError = Notify.describe(exception);
        }
    }

    private void activate(Entry entry) {
        if (entry.directory()) {
            currentDirectory = entry.path();
            this.selected = null;
            this.metadata = null;
            this.entries = List.of();
            updateButtons();
            reload();
        } else {
            place();
        }
    }

    private void place() {
        if (this.selected == null || this.selected.directory() || this.minecraft.player == null) {
            return;
        }
        SchematicPlacement placement = PlacementManager.get().create(this.selected.path(),
                this.minecraft.player.blockPosition());
        Notify.actionBar("litematicaplus.message.placement_created", placement.name());
        this.minecraft.gui.setScreen(new PlacementEditorScreen(null, placement));
    }

    private void openFolder() {
        try {
            Blaze3D.openPath(directory());
        } catch (RuntimeException exception) {
            LitematicaPlusClient.LOGGER.warn("Could not open schematics folder", exception);
        }
    }

    private void renderRow(GuiGraphicsExtractor graphics, net.minecraft.client.gui.Font rowFont, Entry entry, int x, int y,
            int rowWidth, int rowHeight, boolean isSelected, boolean hovered) {
        int textY = y + (rowHeight - 8) / 2;
        if (entry.directory()) {
            graphics.fill(x + 8, y + 7, x + 18, y + rowHeight - 6, Theme.WARNING);
            graphics.fill(x + 8, y + 5, x + 13, y + 7, Theme.WARNING);
            Ui.clipped(graphics, rowFont, entry.parent() ? tr("litematicaplus.schematics.parent").getString() : entry.name(),
                    x + 24, textY, rowWidth - 30, Theme.TEXT_BRIGHT);
            return;
        }
        graphics.fill(x + 9, y + 5, x + 17, y + rowHeight - 5, Theme.ACCENT_DARK);
        graphics.fill(x + 11, y + 8, x + 15, y + 9, Theme.TEXT_BRIGHT);
        graphics.fill(x + 11, y + 11, x + 15, y + 12, Theme.TEXT_BRIGHT);
        String size = formatSize(entry.size());
        int sizeWidth = rowFont.width(size);
        Ui.clipped(graphics, rowFont, entry.name(), x + 24, textY, rowWidth - 36 - sizeWidth, Theme.TEXT);
        Ui.right(graphics, rowFont, size, x + rowWidth - 6, textY, Theme.TEXT_DIM);
    }

    private static String formatSize(long bytes) {
        if (bytes < 1024L) {
            return bytes + " B";
        }
        if (bytes < 1024L * 1024L) {
            return String.format(Locale.ROOT, "%.1f KB", bytes / 1024.0);
        }
        return String.format(Locale.ROOT, "%.1f MB", bytes / (1024.0 * 1024.0));
    }

    @Override
    void extract(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        int panelHeight = this.height - (Theme.CONTROL_HEIGHT + 4) * 2;
        Ui.panel(graphics, this.detailX, this.top, this.detailWidth, panelHeight);
        int x = this.detailX + 8;
        int inner = this.detailWidth - 16;
        int y = this.top + 8;
        if (this.preview != null && this.preview.visible) {
            y += this.previewSide + 8;
        }
        if (this.loading && this.entries.isEmpty()) {
            graphics.text(this.font, tr("litematicaplus.schematics.loading"), x, y, Theme.TEXT_DIM, false);
            return;
        }
        if (this.selected == null || this.selected.directory()) {
            graphics.textWithWordWrap(this.font, tr("litematicaplus.schematics.pick_hint"), x, y, inner, Theme.TEXT_DIM, false);
            return;
        }
        String title = this.metadata != null && !this.metadata.name.isBlank() ? this.metadata.name
                : SchematicIo.baseName(this.selected.path());
        Ui.clipped(graphics, this.font, title, x, y, inner, Theme.TEXT_BRIGHT);
        y += 13;
        if (this.metadataError != null) {
            graphics.textWithWordWrap(this.font, tr("litematicaplus.schematics.unreadable", this.metadataError), x, y,
                    inner, Theme.DANGER, false);
            return;
        }
        if (this.metadata == null) {
            graphics.text(this.font, tr("litematicaplus.schematics.loading"), x, y, Theme.TEXT_DIM, false);
            return;
        }
        SchematicMetadata meta = this.metadata;
        if (this.preview == null || !this.preview.visible) {
            Ui.chip(graphics, this.font, x, y, Component.literal(meta.format.toUpperCase(Locale.ROOT)), Theme.ACCENT);
            y += 19;
        }
        int limit = this.top + panelHeight - 10;
        if (meta.enclosingX > 0) {
            y = row(graphics, x, y, inner, limit, "litematicaplus.schematics.size",
                    meta.enclosingX + " x " + meta.enclosingY + " x " + meta.enclosingZ);
            y = row(graphics, x, y, inner, limit, "litematicaplus.schematics.blocks", Long.toString(meta.totalBlocks));
            y = row(graphics, x, y, inner, limit, "litematicaplus.schematics.regions", Integer.toString(meta.regionCount));
        }
        if (!meta.author.isBlank()) {
            y = row(graphics, x, y, inner, limit, "litematicaplus.schematics.author", meta.author);
        }
        if (meta.timeCreated > 0L) {
            y = row(graphics, x, y, inner, limit, "litematicaplus.schematics.created",
                    DATE.format(Instant.ofEpochMilli(meta.timeCreated)));
        }
        if (meta.dataVersion > 0) {
            y = row(graphics, x, y, inner, limit, "litematicaplus.schematics.data_version", Integer.toString(meta.dataVersion));
        }
        y = row(graphics, x, y, inner, limit, "litematicaplus.schematics.file_size", formatSize(this.selected.size()));
        if (!meta.description.isBlank() && y + 12 < limit) {
            graphics.enableScissor(this.detailX + 1, y, this.detailX + this.detailWidth - 1, limit);
            graphics.textWithWordWrap(this.font, Component.literal(meta.description), x, y + 4, inner, Theme.TEXT_DIM, false);
            graphics.disableScissor();
        }
    }

    private int row(GuiGraphicsExtractor graphics, int x, int y, int inner, int limit, String key, String value) {
        if (y + 10 > limit) {
            return y;
        }
        Component label = tr(key);
        graphics.text(this.font, label, x, y, Theme.TEXT_DIM, false);
        int labelWidth = this.font.width(label) + 6;
        String shown = this.font.width(value) > inner - labelWidth
                ? this.font.plainSubstrByWidth(value, Math.max(0, inner - labelWidth - 6)) + ".." : value;
        Ui.right(graphics, this.font, shown, x + inner, y, Theme.TEXT);
        return y + 12;
    }
}
