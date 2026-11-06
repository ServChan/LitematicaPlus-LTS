package org.lts.litematicaplus.gui;

import com.mojang.blaze3d.Blaze3D;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.RejectedExecutionException;
import java.util.function.Consumer;
import java.util.function.Supplier;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.lts.litematicaplus.LitematicaPlusClient;
import org.lts.litematicaplus.placement.PlacementManager;
import org.lts.litematicaplus.schematic.SchematicTrash;
import org.lts.litematicaplus.util.Notify;
import org.lts.litematicaplus.util.Workers;

public final class TrashScreen extends Screen {
    private static final int MARGIN = 16;
    private static final int MAX_WIDTH = 760;
    private static final int CONFIRM_TICKS = 80;

    private final Screen parent;
    private List<SchematicTrash.Item> items = List.of();
    private boolean loading = true;
    private boolean working;
    private ListPanel<SchematicTrash.Item> list;
    private FlatButton restore;
    private FlatButton forever;
    private FlatButton empty;
    private String armed;
    private int armedTicks;
    private Component status;
    private int statusTicks;
    private int panelX;
    private int panelWidth;
    private int headerY;

    public TrashScreen(Screen parent) {
        super(Component.translatable("litematicaplus.trash.title"));
        this.parent = parent;
        reload();
    }

    private void reload() {
        run(() -> SchematicTrash.global().items(), result -> {
            this.items = result;
            this.loading = false;
            if (this.list != null) {
                this.list.setItems(result);
            }
            update();
        });
    }

    private <T> void run(Supplier<T> work, Consumer<T> done) {
        this.working = true;
        update();
        try {
            Workers.io().execute(() -> {
                T result = null;
                Throwable failure = null;
                try {
                    result = work.get();
                } catch (RuntimeException exception) {
                    failure = exception;
                }
                T value = result;
                Throwable error = failure;
                this.minecraft.execute(() -> {
                    this.working = false;
                    if (error != null) {
                        LitematicaPlusClient.LOGGER.warn("Schematic trash operation failed", error);
                        status(Component.translatable("litematicaplus.trash.failed", Notify.describe(error)));
                        update();
                        return;
                    }
                    done.accept(value);
                });
            });
        } catch (RejectedExecutionException exception) {
            this.working = false;
            status(Component.translatable("litematicaplus.trash.failed", Notify.describe(exception)));
        }
    }

    @Override
    protected void init() {
        this.panelWidth = Math.min(MAX_WIDTH, this.width - MARGIN * 2);
        this.panelX = (this.width - this.panelWidth) / 2;
        int y = MARGIN + 14;
        int fifth = (this.panelWidth - Theme.GAP * 4) / 5;
        this.restore = addRenderableWidget(FlatButton.primary(this.panelX, y, fifth,
                Component.translatable("litematicaplus.trash.restore"), button -> restore()));
        this.forever = addRenderableWidget(FlatButton.danger(this.panelX + fifth + Theme.GAP, y, fifth,
                Component.translatable("litematicaplus.trash.delete_forever"), button -> deleteForever()));
        this.empty = addRenderableWidget(FlatButton.danger(this.panelX + (fifth + Theme.GAP) * 2, y, fifth,
                Component.translatable("litematicaplus.trash.empty"), button -> emptyTrash()));
        addRenderableWidget(FlatButton.secondary(this.panelX + (fifth + Theme.GAP) * 3, y, fifth,
                Component.translatable("litematicaplus.schematics.open_folder"), button -> openFolder()));
        addRenderableWidget(FlatButton.secondary(this.panelX + (fifth + Theme.GAP) * 4, y,
                this.panelWidth - (fifth + Theme.GAP) * 4, Component.translatable("gui.back"), button -> onClose()));
        this.headerY = y + Theme.CONTROL_HEIGHT + 5;
        int listTop = this.headerY + 11;
        this.list = addRenderableWidget(new ListPanel<SchematicTrash.Item>(this.panelX, listTop, this.panelWidth,
                this.height - listTop - MARGIN, Theme.ROW_HEIGHT, this.title, this::renderRow)
                .onSelect(item -> {
                    this.armed = null;
                    update();
                })
                .describe(item -> item.name)
                .empty(Component.translatable("litematicaplus.trash.empty_title"),
                        Component.translatable("litematicaplus.trash.empty_hint", SchematicTrash.KEEP_DAYS)));
        this.list.setItems(this.items);
        update();
        Ui.decorate(children());
    }

    private void update() {
        if (this.restore == null) {
            return;
        }
        boolean selected = this.list != null && this.list.selectedItem() != null;
        this.restore.active = selected && !this.working;
        this.forever.active = selected && !this.working;
        this.empty.active = !this.items.isEmpty() && !this.working;
        this.forever.setMessage(Component.translatable("forever".equals(this.armed)
                ? "litematicaplus.trash.confirm" : "litematicaplus.trash.delete_forever"));
        this.empty.setMessage(Component.translatable("empty".equals(this.armed)
                ? "litematicaplus.trash.confirm" : "litematicaplus.trash.empty"));
    }

    private boolean confirm(String what) {
        if (what.equals(this.armed)) {
            this.armed = null;
            return true;
        }
        this.armed = what;
        this.armedTicks = CONFIRM_TICKS;
        status(Component.translatable("litematicaplus.trash.confirm_hint"));
        update();
        return false;
    }

    private void restore() {
        SchematicTrash.Item item = this.list.selectedItem();
        if (item == null) {
            return;
        }
        Path fallback = PlacementManager.schematicsDirectory();
        run(() -> {
            try {
                return SchematicTrash.global().restore(item, fallback);
            } catch (IOException exception) {
                throw new UncheckedIOException(exception);
            }
        }, target -> {
            status(Component.translatable("litematicaplus.trash.restored", target.getFileName().toString()));
            reload();
        });
    }

    private void deleteForever() {
        SchematicTrash.Item item = this.list.selectedItem();
        if (item == null || !confirm("forever")) {
            return;
        }
        run(() -> {
            try {
                SchematicTrash.global().deleteForever(item);
                return item.name;
            } catch (IOException exception) {
                throw new UncheckedIOException(exception);
            }
        }, name -> {
            status(Component.translatable("litematicaplus.trash.deleted", name));
            reload();
        });
    }

    private void emptyTrash() {
        if (!confirm("empty")) {
            return;
        }
        run(() -> {
            try {
                return SchematicTrash.global().empty();
            } catch (IOException exception) {
                throw new UncheckedIOException(exception);
            }
        }, count -> {
            status(Component.translatable("litematicaplus.trash.emptied", count));
            reload();
        });
    }

    private void openFolder() {
        Path root = SchematicTrash.global().root();
        try {
            Files.createDirectories(root);
            Blaze3D.openPath(root);
        } catch (IOException | RuntimeException exception) {
            LitematicaPlusClient.LOGGER.warn("Could not open the trash folder {}", root, exception);
        }
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
        if (this.armedTicks > 0 && --this.armedTicks == 0) {
            this.armed = null;
            update();
        }
    }

    private void renderRow(GuiGraphicsExtractor graphics, Font font, SchematicTrash.Item item, int x, int y, int rowWidth,
            int rowHeight, boolean selected, boolean hovered) {
        int textY = y + (rowHeight - 8) / 2;
        int right = x + rowWidth - 6;
        String days = Component.translatable("litematicaplus.trash.days_left", item.daysLeft(System.currentTimeMillis()))
                .getString();
        Ui.right(graphics, font, days, right, textY, Theme.TEXT_DIM);
        Path original = item.originalPath();
        String from = original == null || original.getParent() == null ? "" : original.getParent().getFileName() == null
                ? original.getParent().toString() : original.getParent().getFileName().toString();
        int fromWidth = Math.min(160, rowWidth / 3);
        Ui.clipped(graphics, font, from, right - font.width(days) - 12 - fromWidth, textY, fromWidth, Theme.TEXT_DIM);
        Ui.clipped(graphics, font, item.name, x + 8, textY, rowWidth - fromWidth - font.width(days) - 32, Theme.TEXT);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        graphics.fill(0, 0, this.width, this.height, Theme.SIDEBAR);
        Ui.clipped(graphics, this.font, this.title.getString(), this.panelX, MARGIN, this.panelWidth / 2, Theme.TEXT_BRIGHT);
        Component summary = this.status != null ? this.status : this.loading
                ? Component.translatable("litematicaplus.schematics.loading")
                : Component.translatable("litematicaplus.trash.summary", this.items.size(), SchematicTrash.KEEP_DAYS);
        Ui.right(graphics, this.font, summary.getString(), this.panelX + this.panelWidth, MARGIN, Theme.TEXT_DIM);
        graphics.text(this.font, Component.translatable("litematicaplus.trash.column.name"), this.panelX + 9, this.headerY,
                Theme.ACCENT, false);
        Ui.right(graphics, this.font, Component.translatable("litematicaplus.trash.column.left").getString(),
                this.panelX + this.panelWidth - 7, this.headerY, Theme.ACCENT);
        super.extractRenderState(graphics, mouseX, mouseY, delta);
    }

    @Override
    public void onClose() {
        this.minecraft.gui.setScreen(this.parent);
    }
}
