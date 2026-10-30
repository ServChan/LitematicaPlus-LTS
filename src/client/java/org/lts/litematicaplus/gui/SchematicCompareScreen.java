package org.lts.litematicaplus.gui;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.RejectedExecutionException;
import java.util.stream.Stream;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.Block;
import org.lts.litematicaplus.LitematicaPlusClient;
import org.lts.litematicaplus.placement.PlacementManager;
import org.lts.litematicaplus.schematic.SchematicDiff;
import org.lts.litematicaplus.schematic.SchematicIo;
import org.lts.litematicaplus.util.Notify;
import org.lts.litematicaplus.util.Workers;

final class SchematicCompareScreen extends Screen {
    private static final int MARGIN = 16;
    private static final int MAX_WIDTH = 760;

    private final Screen parent;
    private final Path file;
    private ListPanel<Path> files;
    private ListPanel<SchematicDiff.Row> rows;
    private FlatButton saveButton;
    private List<Path> candidates = List.of();
    private Path other;
    private SchematicDiff.Result result;
    private Component status = Component.empty();
    private int statusColor = Theme.TEXT_DIM;
    private boolean working;
    private int request;
    private int panelX;
    private int panelWidth;
    private int summaryY;

    SchematicCompareScreen(Screen parent, Path file) {
        super(Component.translatable("litematicaplus.compare.title", SchematicIo.baseName(file)));
        this.parent = parent;
        this.file = file;
        listFiles();
    }

    private void listFiles() {
        Path directory = this.file.getParent();
        try {
            Workers.io().execute(() -> {
                List<Path> found = new ArrayList<>();
                try (Stream<Path> stream = Files.list(directory)) {
                    stream.filter(path -> Files.isRegularFile(path) && SchematicIo.isSupported(path) && !path.equals(this.file))
                            .forEach(found::add);
                } catch (IOException | RuntimeException exception) {
                    LitematicaPlusClient.LOGGER.warn("Could not list schematics in {}", directory, exception);
                }
                found.sort(Comparator.comparing(path -> path.getFileName().toString().toLowerCase(java.util.Locale.ROOT)));
                Minecraft.getInstance().execute(() -> {
                    this.candidates = found;
                    if (this.files != null) {
                        this.files.setItems(found);
                    }
                });
            });
        } catch (RejectedExecutionException exception) {
            this.status = Notify.describe(exception);
        }
    }

    @Override
    protected void init() {
        this.panelWidth = Math.min(MAX_WIDTH, this.width - MARGIN * 2);
        this.panelX = (this.width - this.panelWidth) / 2;
        int y = MARGIN + 14;
        int half = (this.panelWidth - Theme.GAP) / 2;
        addRenderableWidget(FlatButton.secondary(this.panelX, y, half, Component.translatable("gui.back"),
                button -> onClose()));
        this.saveButton = addRenderableWidget(FlatButton.primary(this.panelX + half + Theme.GAP, y,
                this.panelWidth - half - Theme.GAP, Component.translatable("litematicaplus.compare.save"), button -> saveChanges()));
        this.saveButton.withHint(Component.translatable("litematicaplus.compare.save.tip"));
        y += Theme.CONTROL_HEIGHT + 6;
        this.summaryY = y;
        y += 22;
        int listHeight = this.height - y - MARGIN;
        int leftWidth = Math.max(120, this.panelWidth * 2 / 5);
        this.files = addRenderableWidget(new ListPanel<Path>(this.panelX, y, leftWidth, listHeight, Theme.ROW_HEIGHT,
                Component.translatable("litematicaplus.compare.pick"), this::renderFile)
                .describe(path -> path.getFileName().toString())
                .onSelect(this::compareWith)
                .empty(Component.translatable("litematicaplus.compare.no_files"),
                        Component.translatable("litematicaplus.compare.no_files_hint")));
        this.files.setItems(this.candidates);
        this.files.selectItem(this.other);
        this.rows = addRenderableWidget(new ListPanel<SchematicDiff.Row>(this.panelX + leftWidth + Theme.GAP, y,
                this.panelWidth - leftWidth - Theme.GAP, listHeight, Theme.ROW_HEIGHT, this.title, this::renderChange)
                .describe(row -> change(row).getString() + " " + row.count())
                .empty(Component.translatable("litematicaplus.compare.pick"),
                        Component.translatable("litematicaplus.compare.pick_hint")));
        this.rows.setItems(this.result == null ? List.of() : this.result.rows());
        updateButtons();
    }

    private void updateButtons() {
        if (this.saveButton != null) {
            this.saveButton.active = this.result != null && !this.working
                    && this.result.added() + this.result.replaced() + this.result.restated() > 0L;
        }
    }

    private void compareWith(Path path) {
        if (path == null || path.equals(this.other)) {
            return;
        }
        this.other = path;
        this.result = null;
        this.working = true;
        this.rows.setItems(List.of());
        this.status = Component.translatable("litematicaplus.schematics.loading");
        this.statusColor = Theme.TEXT_DIM;
        updateButtons();
        int id = ++this.request;
        String name = SchematicIo.baseName(this.file) + "_to_" + SchematicIo.baseName(path);
        try {
            Workers.io().execute(() -> {
                SchematicDiff.Result computed = null;
                Component error = null;
                try {
                    computed = SchematicDiff.compare(SchematicIo.read(this.file), SchematicIo.read(path), name);
                } catch (IOException | RuntimeException | OutOfMemoryError exception) {
                    LitematicaPlusClient.LOGGER.warn("Could not compare {} with {}", this.file, path, exception);
                    error = Notify.describe(exception);
                }
                SchematicDiff.Result done = computed;
                Component failure = error;
                Minecraft.getInstance().execute(() -> {
                    if (id == this.request) {
                        finish(done, failure);
                    }
                });
            });
        } catch (RejectedExecutionException exception) {
            finish(null, Notify.describe(exception));
        }
    }

    private void finish(SchematicDiff.Result done, Component failure) {
        this.working = false;
        this.result = done;
        if (failure != null) {
            this.status = Component.translatable("litematicaplus.schematics.unreadable", failure);
            this.statusColor = Theme.DANGER;
        } else {
            this.status = Component.translatable(done.total() == 0L ? "litematicaplus.compare.identical"
                    : "litematicaplus.compare.summary", done.added(), done.removed(), done.replaced(), done.restated());
            this.statusColor = Theme.TEXT;
            this.rows.setItems(done.rows());
        }
        updateButtons();
    }

    private void saveChanges() {
        SchematicDiff.Result current = this.result;
        if (current == null) {
            return;
        }
        Path directory = this.file.getParent();
        String base = current.changes().metadata().name;
        Path target = directory.resolve(base + SchematicIo.LITEMATIC_EXTENSION);
        for (int suffix = 2; Files.exists(target); suffix++) {
            target = directory.resolve(base + "_" + suffix + SchematicIo.LITEMATIC_EXTENSION);
        }
        Path output = target;
        this.working = true;
        updateButtons();
        try {
            Workers.save().execute(() -> {
                Component message;
                int color;
                try {
                    SchematicIo.write(output, current.changes());
                    message = Component.translatable("litematicaplus.compare.saved", output.getFileName().toString());
                    color = Theme.SUCCESS;
                } catch (IOException | RuntimeException exception) {
                    LitematicaPlusClient.LOGGER.warn("Could not save {}", output, exception);
                    message = Component.translatable("litematicaplus.area.error_write", Notify.describe(exception));
                    color = Theme.DANGER;
                }
                Component shown = message;
                int shade = color;
                Minecraft.getInstance().execute(() -> {
                    PlacementManager.get().invalidate(output);
                    this.working = false;
                    this.status = shown;
                    this.statusColor = shade;
                    updateButtons();
                });
            });
        } catch (RejectedExecutionException exception) {
            this.working = false;
            this.status = Component.translatable("litematicaplus.area.error_write", Notify.describe(exception));
            this.statusColor = Theme.DANGER;
            updateButtons();
        }
    }

    private void renderFile(GuiGraphicsExtractor graphics, Font rowFont, Path path, int x, int y, int rowWidth,
            int rowHeight, boolean isSelected, boolean hovered) {
        Ui.clipped(graphics, rowFont, path.getFileName().toString(), x + 6, y + (rowHeight - 8) / 2, rowWidth - 12, Theme.TEXT);
    }

    private static Component change(SchematicDiff.Row row) {
        Block from = row.from();
        Block to = row.to();
        return switch (row.change()) {
            case ADDED -> Component.translatable("litematicaplus.compare.row.added", to.getName());
            case REMOVED -> Component.translatable("litematicaplus.compare.row.removed", from.getName());
            case REPLACED -> Component.translatable("litematicaplus.compare.row.replaced", from.getName(), to.getName());
            case RESTATED -> Component.translatable("litematicaplus.compare.row.restated", to.getName());
        };
    }

    private void renderChange(GuiGraphicsExtractor graphics, Font rowFont, SchematicDiff.Row row, int x, int y,
            int rowWidth, int rowHeight, boolean isSelected, boolean hovered) {
        int textY = y + (rowHeight - 8) / 2;
        String count = Long.toString(row.count());
        Ui.right(graphics, rowFont, count, x + rowWidth - 6, textY, Theme.TEXT_BRIGHT);
        Ui.clipped(graphics, rowFont, change(row).getString(), x + 6, textY, rowWidth - 18 - rowFont.width(count), Theme.TEXT);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        graphics.fill(0, 0, this.width, this.height, Theme.SIDEBAR);
        Ui.clipped(graphics, this.font, this.title.getString(), this.panelX, MARGIN, this.panelWidth, Theme.TEXT_BRIGHT);
        Ui.wrapped(graphics, this.font, this.status, this.panelX, this.summaryY, this.panelWidth, 2, this.statusColor,
                mouseX, mouseY);
        super.extractRenderState(graphics, mouseX, mouseY, delta);
    }

    @Override
    public void onClose() {
        this.minecraft.gui.setScreen(this.parent);
    }
}
