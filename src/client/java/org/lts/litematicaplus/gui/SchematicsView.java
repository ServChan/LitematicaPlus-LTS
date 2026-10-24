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
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.network.chat.Component;
import org.lts.litematicaplus.LitematicaPlusClient;
import org.lts.litematicaplus.Session;
import org.lts.litematicaplus.compat.AxiomBridge;
import org.lts.litematicaplus.placement.PlacementManager;
import org.lts.litematicaplus.placement.SchematicPlacement;
import org.lts.litematicaplus.preview.PreviewService;
import org.lts.litematicaplus.schematic.EmbeddedPlacement;
import org.lts.litematicaplus.schematic.SchematicExport;
import org.lts.litematicaplus.schematic.SchematicIo;
import org.lts.litematicaplus.schematic.SchematicMetadata;
import org.lts.litematicaplus.util.Notify;
import org.lts.litematicaplus.util.Workers;

final class SchematicsView extends TabView {
    private record Entry(Path path, boolean directory, boolean parent, String name, long size, long modified,
            String sizeText) {
    }

    private enum Sort {
        NAME(false),
        SIZE(true),
        WEIGHT(true),
        DATE(true);

        private final boolean descendingByDefault;

        Sort(boolean descendingByDefault) {
            this.descendingByDefault = descendingByDefault;
        }

        Sort next() {
            return values()[(ordinal() + 1) % values().length];
        }

        String key() {
            return "litematicaplus.schematics.sort." + name().toLowerCase(Locale.ROOT);
        }
    }

    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("yyyy-MM-dd").withZone(ZoneId.systemDefault());
    private static final DateTimeFormatter DAY_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
            .withZone(ZoneId.systemDefault());
    private static final Map<String, SchematicMetadata> METADATA = new ConcurrentHashMap<>();
    private static final Map<String, Long> VOLUMES = new ConcurrentHashMap<>();
    private static final long UNKNOWN_VOLUME = -1L;
    private static Path currentDirectory;
    private static String filter = "";
    private static Sort sort = Sort.NAME;
    private static boolean descending;

    private ListPanel<Entry> list;
    private FlatButton placeButton;
    private FlatButton placeSavedButton;
    private int placeRowWidth;
    private FlatButton exportSponge;
    private FlatButton exportStructure;
    private FlatButton exportBlueprint;
    private FlatButton deleteButton;
    private Entry deleteArmed;
    private int deleteTicks;
    private boolean deleting;
    private Component status;
    private int statusTicks;
    private boolean exporting;
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
    private final TextMemo parentText = new TextMemo();
    private volatile int volumeRequest;
    private boolean readingVolumes;

    SchematicsView(HubScreen screen) {
        super(screen);
    }

    @Override
    Component summary() {
        if (this.status != null) {
            return this.status;
        }
        if (this.readingVolumes) {
            return tr("litematicaplus.schematics.sort.reading");
        }
        Path root = PlacementManager.schematicsDirectory();
        Path directory = directory();
        Path axiom = axiomRoot();
        if (axiom != null && directory.startsWith(axiom) && !directory.startsWith(root)) {
            String inside = axiom.relativize(directory).toString().replace('\\', '/');
            return Component.literal("Axiom" + (inside.isEmpty() ? "" : "/" + inside));
        }
        String relative = directory.startsWith(root) ? root.relativize(directory).toString().replace('\\', '/') : "";
        return Component.literal(root.getFileName() + (relative.isEmpty() ? "" : "/" + relative));
    }

    private static Path axiomRoot() {
        return AxiomBridge.installed() ? AxiomBridge.blueprintsDirectory() : null;
    }

    private static Path directory() {
        Path root = PlacementManager.schematicsDirectory();
        Path axiom = axiomRoot();
        if (currentDirectory == null || !currentDirectory.startsWith(root)
                && (axiom == null || !currentDirectory.startsWith(axiom))) {
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
        int sortWidth = 0;
        for (Sort entry : Sort.values()) {
            sortWidth = Math.max(sortWidth, this.font.width(tr(entry.key())) + 12);
        }
        int directionWidth = 24;
        int searchWidth = listWidth - sortWidth - directionWidth - Theme.GAP * 2;
        FlatButton sortButton = add(FlatButton.secondary(this.left + searchWidth + Theme.GAP, this.top, sortWidth,
                tr(sort.key()), button -> {
                    sort = sort.next();
                    descending = sort.descendingByDefault;
                    this.screen.refresh();
                    loadVolumes();
                }));
        sortButton.setTooltip(Tooltip.create(tr("litematicaplus.schematics.sort.tip", tr(sort.key()))));
        FlatButton directionButton = add(FlatButton.secondary(this.left + listWidth - directionWidth, this.top,
                directionWidth, Component.literal(descending ? "\u2193" : "\u2191"), button -> {
                    descending = !descending;
                    this.screen.refresh();
                }));
        directionButton.setTooltip(Tooltip.create(tr(descending ? "litematicaplus.schematics.sort.descending"
                : "litematicaplus.schematics.sort.ascending")));
        EditBox search = field(this.left, this.top, searchWidth, filter, tr("litematicaplus.schematics.search"), 64);
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
        int third = (this.detailWidth - Theme.GAP * 2) / 3;
        int exportY = buttonY - Theme.CONTROL_HEIGHT - 4;
        this.exportSponge = add(FlatButton.secondary(this.detailX, exportY, third,
                tr("litematicaplus.schematics.export_sponge"), button -> export(SchematicExport.Format.SPONGE)));
        this.exportStructure = add(FlatButton.secondary(this.detailX + third + Theme.GAP, exportY, third,
                tr("litematicaplus.schematics.export_structure"), button -> export(SchematicExport.Format.STRUCTURE)));
        this.exportBlueprint = add(FlatButton.secondary(this.detailX + (third + Theme.GAP) * 2, exportY,
                this.detailWidth - (third + Theme.GAP) * 2, tr("litematicaplus.schematics.export_blueprint"),
                button -> export(SchematicExport.Format.BLUEPRINT)));
        this.exportBlueprint.setTooltip(Tooltip.create(tr(AxiomBridge.installed()
                ? "litematicaplus.schematics.export_blueprint.tip_axiom" : "litematicaplus.schematics.export_blueprint.tip")));
        int placeY = exportY - Theme.CONTROL_HEIGHT - 4;
        int deleteWidth = Math.max(half / 2, Math.min(half, this.font.width(tr("litematicaplus.schematics.delete_confirm")) + 12));
        this.placeRowWidth = this.detailWidth - deleteWidth - Theme.GAP;
        this.placeSavedButton = add(FlatButton.primary(this.detailX, placeY, this.placeRowWidth,
                tr("litematicaplus.schematics.place_saved"), button -> placeSaved()));
        this.placeButton = add(FlatButton.primary(this.detailX, placeY, this.placeRowWidth,
                tr("litematicaplus.schematics.place"), button -> place()));
        this.deleteButton = add(FlatButton.danger(this.detailX + this.detailWidth - deleteWidth, placeY, deleteWidth,
                deleteLabel(), button -> delete()));
        add(FlatButton.secondary(this.detailX, buttonY, half, tr("litematicaplus.schematics.open_folder"),
                button -> openFolder()));
        add(FlatButton.secondary(this.detailX + half + Theme.GAP, buttonY, this.detailWidth - half - Theme.GAP,
                tr("litematicaplus.schematics.refresh"), button -> {
                    PreviewService.retryFailed();
                    reload();
                }));
        int panelHeight = this.height - (Theme.CONTROL_HEIGHT + 4) * 3;
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
            boolean placeable = this.selected != null && !this.selected.directory() && this.minecraft.player != null;
            EmbeddedPlacement saved = savedPlacement();
            this.placeButton.active = placeable;
            this.placeSavedButton.active = placeable;
            this.placeSavedButton.visible = saved != null;
            if (saved != null) {
                int savedWidth = (this.placeRowWidth - Theme.GAP) / 2;
                this.placeSavedButton.setWidth(savedWidth);
                this.placeSavedButton.setTooltip(Tooltip.create(savedTooltip(saved)));
                this.placeButton.setX(this.detailX + savedWidth + Theme.GAP);
                this.placeButton.setWidth(this.placeRowWidth - savedWidth - Theme.GAP);
                this.placeButton.setMessage(tr("litematicaplus.schematics.place_here"));
            } else {
                this.placeButton.setX(this.detailX);
                this.placeButton.setWidth(this.placeRowWidth);
                this.placeButton.setMessage(tr("litematicaplus.schematics.place"));
            }
        }
        boolean file = this.selected != null && !this.selected.directory() && !this.exporting;
        if (this.exportSponge != null) {
            this.exportSponge.active = file;
            this.exportStructure.active = file;
            this.exportBlueprint.active = file;
        }
        if (this.deleteButton != null) {
            this.deleteButton.active = file && !this.deleting;
            this.deleteButton.setMessage(deleteLabel());
        }
    }

    private Component deleteLabel() {
        return tr(this.deleteArmed != null && this.deleteArmed == this.selected
                ? "litematicaplus.schematics.delete_confirm" : "litematicaplus.schematics.delete");
    }

    private void disarmDelete() {
        this.deleteArmed = null;
        this.deleteTicks = 0;
    }

    private void delete() {
        Entry entry = this.selected;
        if (entry == null || entry.directory() || this.deleting) {
            return;
        }
        if (this.deleteArmed != entry) {
            this.deleteArmed = entry;
            this.deleteTicks = 80;
            status(tr("litematicaplus.schematics.delete_hint", entry.name()));
            updateButtons();
            return;
        }
        disarmDelete();
        this.deleting = true;
        updateButtons();
        try {
            Workers.io().execute(() -> {
                Component message;
                try {
                    boolean removed = Files.deleteIfExists(entry.path());
                    message = tr(removed ? "litematicaplus.schematics.deleted" : "litematicaplus.schematics.delete_gone",
                            entry.name());
                } catch (IOException | RuntimeException exception) {
                    LitematicaPlusClient.LOGGER.warn("Could not delete schematic {}", entry.path(), exception);
                    message = tr("litematicaplus.schematics.delete_failed", Notify.describe(exception));
                }
                Component result = message;
                this.minecraft.execute(() -> {
                    this.deleting = false;
                    if (this.selected == entry) {
                        select(null);
                        if (this.list != null) {
                            this.list.selectItem(null);
                        }
                    }
                    status(result);
                    updateButtons();
                    reload();
                });
            });
        } catch (RejectedExecutionException exception) {
            this.deleting = false;
            status(tr("litematicaplus.schematics.delete_failed", Notify.describe(exception)));
            updateButtons();
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
                    loadVolumes();
                });
            });
        } catch (RejectedExecutionException exception) {
            this.loading = false;
        }
    }

    private static List<Entry> listDirectory(Path directory) {
        List<Entry> result = new ArrayList<>();
        Path root = PlacementManager.schematicsDirectory();
        Path axiom = axiomRoot();
        try {
            Files.createDirectories(directory);
            if (!directory.equals(root)) {
                result.add(new Entry(directory.equals(axiom) ? root : directory.getParent(), true, true, "..", 0L, 0L, ""));
            } else if (axiom != null && !axiom.startsWith(root)) {
                result.add(new Entry(axiom, true, false,
                        Component.translatable("litematicaplus.schematics.axiom_folder").getString(), 0L,
                        Files.isDirectory(axiom) ? Files.getLastModifiedTime(axiom).toMillis() : 0L, ""));
            }
            List<Entry> found = new ArrayList<>();
            try (Stream<Path> stream = Files.list(directory)) {
                stream.forEach(path -> {
                    try {
                        if (Files.isDirectory(path)) {
                            found.add(new Entry(path, true, false, path.getFileName().toString(), 0L,
                                    Files.getLastModifiedTime(path).toMillis(), ""));
                        } else if (SchematicIo.isSupported(path)) {
                            long size = Files.size(path);
                            found.add(new Entry(path, false, false, path.getFileName().toString(), size,
                                    Files.getLastModifiedTime(path).toMillis(), formatSize(size)));
                        }
                    } catch (IOException exception) {
                        LitematicaPlusClient.LOGGER.debug("Could not read {}; skipping it", path, exception);
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

    private static String key(Entry entry) {
        return entry.path() + "|" + entry.modified() + "|" + entry.size();
    }

    private static long volume(Entry entry) {
        return VOLUMES.getOrDefault(key(entry), UNKNOWN_VOLUME);
    }

    private static long volumeOf(SchematicMetadata metadata) {
        long volume = (long) metadata.enclosingX * metadata.enclosingY * metadata.enclosingZ;
        return volume > 0L ? volume : UNKNOWN_VOLUME;
    }

    private void loadVolumes() {
        if (sort != Sort.SIZE) {
            this.readingVolumes = false;
            return;
        }
        List<Entry> missing = new ArrayList<>();
        for (Entry entry : this.entries) {
            if (!entry.directory() && !VOLUMES.containsKey(key(entry))) {
                missing.add(entry);
            }
        }
        if (missing.isEmpty()) {
            this.readingVolumes = false;
            return;
        }
        int id = ++this.volumeRequest;
        this.readingVolumes = true;
        try {
            Workers.io().execute(() -> {
                for (Entry entry : missing) {
                    if (id != this.volumeRequest) {
                        return;
                    }
                    String key = key(entry);
                    long volume = UNKNOWN_VOLUME;
                    try {
                        SchematicMetadata loaded = SchematicIo.readMetadata(entry.path());
                        volume = volumeOf(loaded);
                        if (METADATA.size() > 512) {
                            METADATA.clear();
                        }
                        METADATA.put(key, loaded);
                    } catch (IOException | RuntimeException exception) {
                        LitematicaPlusClient.LOGGER.debug("Could not read the size of {}", entry.path(), exception);
                    }
                    if (VOLUMES.size() > 8192) {
                        VOLUMES.clear();
                    }
                    VOLUMES.put(key, volume);
                }
                this.minecraft.execute(() -> {
                    if (id == this.volumeRequest) {
                        this.readingVolumes = false;
                        applyFilter();
                    }
                });
            });
        } catch (RejectedExecutionException exception) {
            this.readingVolumes = false;
        }
    }

    private static Comparator<Entry> order() {
        Comparator<Entry> byName = Comparator.comparing(entry -> entry.name().toLowerCase(Locale.ROOT));
        Comparator<Entry> files = switch (sort) {
            case NAME -> byName;
            case SIZE -> Comparator.comparingLong(SchematicsView::volume);
            case WEIGHT -> Comparator.comparingLong(Entry::size);
            case DATE -> Comparator.comparingLong(Entry::modified);
        };
        if (descending) {
            files = files.reversed();
        }
        Comparator<Entry> folders = sort == Sort.DATE ? Comparator.comparingLong(Entry::modified) : byName;
        if (descending && (sort == Sort.NAME || sort == Sort.DATE)) {
            folders = folders.reversed();
        }
        Comparator<Entry> filesThenName = files.thenComparing(byName);
        Comparator<Entry> foldersThenName = folders.thenComparing(byName);
        return Comparator.comparing(Entry::parent, Comparator.reverseOrder())
                .thenComparing(Entry::directory, Comparator.reverseOrder())
                .thenComparing(entry -> sort == Sort.SIZE && !entry.directory() && volume(entry) == UNKNOWN_VOLUME)
                .thenComparing((a, b) -> a.directory() ? foldersThenName.compare(a, b) : filesThenName.compare(a, b));
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
        shown.sort(order());
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
        if (entry != this.deleteArmed) {
            disarmDelete();
        }
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
        String key = key(entry);
        SchematicMetadata cached = METADATA.get(key);
        if (cached != null) {
            this.metadata = cached;
            updateButtons();
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
                        updateButtons();
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
            select(null);
            this.entries = List.of();
            if (this.list != null) {
                this.list.setItems(List.of());
                this.list.setScroll(0);
            }
            reload();
        } else if (savedPlacement() != null) {
            placeSaved();
        } else {
            place();
        }
    }

    private EmbeddedPlacement savedPlacement() {
        return this.selected == null || this.selected.directory() || this.metadata == null ? null
                : this.metadata.placement;
    }

    private Component savedTooltip(EmbeddedPlacement saved) {
        Component text = tr("litematicaplus.schematics.place_saved.tip", saved.x(), saved.y(), saved.z(),
                tr("litematicaplus.rotation." + saved.rotation().name().toLowerCase(Locale.ROOT)),
                tr("litematicaplus.mirror." + saved.mirror().name().toLowerCase(Locale.ROOT)));
        Component warning = savedWarning(saved);
        return warning == null ? text : text.copy().append("\n").append(warning);
    }

    private Component savedWarning(EmbeddedPlacement saved) {
        if (this.minecraft.level == null) {
            return null;
        }
        if (!saved.matchesDimension(Session.dimension(this.minecraft))) {
            return tr("litematicaplus.schematics.place_saved.other_dimension", saved.dimension());
        }
        if (!saved.matchesWorld(Session.world(this.minecraft))) {
            return tr("litematicaplus.schematics.place_saved.other_world", saved.world());
        }
        return null;
    }

    private void placeSaved() {
        EmbeddedPlacement saved = savedPlacement();
        if (saved == null || this.minecraft.player == null) {
            return;
        }
        SchematicPlacement placement = PlacementManager.get().createEmbedded(this.selected.path(), saved);
        Component warning = savedWarning(saved);
        if (warning != null) {
            Notify.chat(warning);
        }
        Notify.actionBar("litematicaplus.message.placement_restored", placement.name(), saved.x(), saved.y(), saved.z());
        this.minecraft.gui.setScreen(new PlacementEditorScreen(null, placement));
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

    @Override
    void tick() {
        if (this.statusTicks > 0 && --this.statusTicks == 0) {
            this.status = null;
        }
        if (this.deleteTicks > 0 && --this.deleteTicks == 0) {
            disarmDelete();
            updateButtons();
        }
    }

    private void status(Component message) {
        this.status = message;
        this.statusTicks = 160;
    }

    private void export(SchematicExport.Format format) {
        Entry entry = this.selected;
        if (entry == null || entry.directory() || this.exporting) {
            return;
        }
        this.exporting = true;
        updateButtons();
        status(tr("litematicaplus.schematics.exporting", entry.name()));
        try {
            Workers.io().execute(() -> {
                Component message;
                try {
                    Path target = format == SchematicExport.Format.BLUEPRINT && AxiomBridge.installed()
                            ? SchematicIo.export(entry.path(), format, AxiomBridge.blueprintsDirectory())
                            : SchematicIo.export(entry.path(), format);
                    message = tr("litematicaplus.schematics.exported", target.getFileName().toString());
                } catch (IOException | RuntimeException exception) {
                    LitematicaPlusClient.LOGGER.warn("Could not export schematic {}", entry.path(), exception);
                    message = tr("litematicaplus.schematics.export_failed", Notify.describe(exception));
                }
                Component result = message;
                this.minecraft.execute(() -> {
                    this.exporting = false;
                    status(result);
                    updateButtons();
                    reload();
                });
            });
        } catch (RejectedExecutionException exception) {
            this.exporting = false;
            status(tr("litematicaplus.schematics.export_failed", Notify.describe(exception)));
            updateButtons();
        }
    }

    private void openFolder() {
        try {
            Blaze3D.openPath(directory());
        } catch (RuntimeException exception) {
            LitematicaPlusClient.LOGGER.warn("Could not open the schematics folder {}", directory(), exception);
        }
    }

    private void renderRow(GuiGraphicsExtractor graphics, net.minecraft.client.gui.Font rowFont, Entry entry, int x, int y,
            int rowWidth, int rowHeight, boolean isSelected, boolean hovered) {
        int textY = y + (rowHeight - 8) / 2;
        if (entry.directory()) {
            graphics.fill(x + 8, y + 7, x + 18, y + rowHeight - 6, Theme.WARNING);
            graphics.fill(x + 8, y + 5, x + 13, y + 7, Theme.WARNING);
            Ui.clipped(graphics, rowFont, entry.parent() ? parentLabel() : entry.name(),
                    x + 24, textY, rowWidth - 30, Theme.TEXT_BRIGHT);
            return;
        }
        graphics.fill(x + 9, y + 5, x + 17, y + rowHeight - 5, Theme.ACCENT_DARK);
        graphics.fill(x + 11, y + 8, x + 15, y + 9, Theme.TEXT_BRIGHT);
        graphics.fill(x + 11, y + 11, x + 15, y + 12, Theme.TEXT_BRIGHT);
        String size = rowValue(entry);
        int sizeWidth = rowFont.width(size);
        Ui.clipped(graphics, rowFont, entry.name(), x + 24, textY, rowWidth - 36 - sizeWidth, Theme.TEXT);
        Ui.right(graphics, rowFont, size, x + rowWidth - 6, textY, Theme.TEXT_DIM);
    }

    private static String rowValue(Entry entry) {
        return switch (sort) {
            case SIZE -> {
                SchematicMetadata meta = METADATA.get(key(entry));
                yield meta != null && meta.enclosingX > 0
                        ? meta.enclosingX + "x" + meta.enclosingY + "x" + meta.enclosingZ
                        : VOLUMES.containsKey(key(entry)) ? "?" : "...";
            }
            case DATE -> DAY_TIME.format(Instant.ofEpochMilli(entry.modified()));
            default -> entry.sizeText();
        };
    }

    private String parentLabel() {
        return this.parentText.stale(null) ? this.parentText.set(tr("litematicaplus.schematics.parent").getString())
                : this.parentText.text();
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
        int panelHeight = this.height - (Theme.CONTROL_HEIGHT + 4) * 3;
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
            if (meta.totalBlocks > 0L) {
                y = row(graphics, x, y, inner, limit, "litematicaplus.schematics.blocks", Long.toString(meta.totalBlocks));
            }
            if (meta.regionCount > 1 || meta.totalBlocks > 0L) {
                y = row(graphics, x, y, inner, limit, "litematicaplus.schematics.regions", Integer.toString(meta.regionCount));
            }
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
        y = row(graphics, x, y, inner, limit, "litematicaplus.schematics.file_size", this.selected.sizeText());
        if (meta.placement != null) {
            y = row(graphics, x, y, inner, limit, "litematicaplus.schematics.saved_place",
                    meta.placement.x() + " " + meta.placement.y() + " " + meta.placement.z());
            Component warning = savedWarning(meta.placement);
            if (warning != null && y + 10 <= limit) {
                Ui.clipped(graphics, this.font, warning.getString(), x, y, inner, Theme.WARNING);
                y += 12;
            }
        }
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
