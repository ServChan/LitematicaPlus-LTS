package org.lts.litematicaplus.gui;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.RejectedExecutionException;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import org.lts.litematicaplus.LitematicaPlusClient;
import org.lts.litematicaplus.LitematicaPlusConfig;
import org.lts.litematicaplus.engine.GhostEngine;
import org.lts.litematicaplus.engine.PlacementRuntime;
import org.lts.litematicaplus.placement.PlacementBake;
import org.lts.litematicaplus.placement.PlacementManager;
import org.lts.litematicaplus.placement.SchematicPlacement;
import org.lts.litematicaplus.schematic.Schematic;
import org.lts.litematicaplus.schematic.SchematicIo;
import org.lts.litematicaplus.schematic.SchematicTrim;
import org.lts.litematicaplus.util.Notify;
import org.lts.litematicaplus.util.Workers;
import org.lts.litematicaplus.world.LayerRange;
import org.lts.litematicaplus.world.PlacementVolume;

final class SavePlacementScreen extends Screen {
    private static final int WIDTH = 320;

    private static boolean layerOnly;
    private static boolean trim = true;

    private final Screen parent;
    private final SchematicPlacement placement;
    private String name;
    private EditBox nameField;
    private FlatButton saveButton;
    private boolean saving;
    private Component status = Component.empty();
    private int statusColor = Theme.TEXT_DIM;
    private int panelX;
    private int topY;

    SavePlacementScreen(Screen parent, SchematicPlacement placement) {
        super(Component.translatable("litematicaplus.save_placement.title"));
        this.parent = parent;
        this.placement = placement;
        this.name = placement.name() + "_edit";
    }

    @Override
    protected void init() {
        int width = Math.min(WIDTH, this.width - 32);
        this.panelX = (this.width - width) / 2;
        this.topY = Math.max(16, (this.height - 140) / 2);
        int y = this.topY + 26;
        this.nameField = new InkField(this.font, this.panelX, y, width, Theme.CONTROL_HEIGHT,
                Component.translatable("litematicaplus.save_placement.name"));
        this.nameField.setMaxLength(96);
        this.nameField.setValue(this.name);
        this.nameField.setHint(Component.translatable("litematicaplus.save_placement.name"));
        this.nameField.setTextColor(Theme.TEXT);
        this.nameField.setResponder(value -> {
            this.name = value;
            updateButtons();
        });
        addRenderableWidget(this.nameField);
        y += Theme.CONTROL_HEIGHT + 4;
        FlatButton layer = addRenderableWidget(FlatButton.toggle(this.panelX, y, width,
                Component.translatable("litematicaplus.save_placement.layer_only"), () -> layerOnly,
                button -> layerOnly = !layerOnly));
        layer.active = !GhostEngine.get().layer().unrestricted();
        y += Theme.CONTROL_HEIGHT + 4;
        addRenderableWidget(FlatButton.toggle(this.panelX, y, width,
                Component.translatable("litematicaplus.save_placement.trim"), () -> trim, button -> trim = !trim));
        y += Theme.CONTROL_HEIGHT + 8;
        int half = (width - Theme.GAP) / 2;
        addRenderableWidget(FlatButton.secondary(this.panelX, y, half, Component.translatable("gui.back"),
                button -> onClose()));
        this.saveButton = addRenderableWidget(FlatButton.primary(this.panelX + half + Theme.GAP, y, width - half - Theme.GAP,
                Component.translatable("litematicaplus.save_placement.save"), button -> save()));
        updateButtons();
    }

    private void updateButtons() {
        if (this.saveButton != null) {
            this.saveButton.active = !this.saving && !AreaView.cleanName(this.name).isEmpty();
        }
    }

    private void status(Component message, int color) {
        this.status = message;
        this.statusColor = color;
    }

    private void save() {
        PlacementRuntime runtime = GhostEngine.get().runtime(this.placement.id());
        PlacementVolume volume = runtime == null ? null : runtime.volume();
        String clean = AreaView.cleanName(this.name);
        if (volume == null || clean.isEmpty() || this.minecraft.level == null) {
            status(Component.translatable("litematicaplus.save_placement.not_ready"), Theme.DANGER);
            return;
        }
        LayerRange scope = layerOnly ? GhostEngine.get().layer() : LayerRange.ALL;
        boolean trimmed = trim;
        Path directory = PlacementManager.schematicsDirectory();
        Path target = directory.resolve(clean + SchematicIo.LITEMATIC_EXTENSION);
        for (int suffix = 2; Files.exists(target); suffix++) {
            target = directory.resolve(clean + "_" + suffix + SchematicIo.LITEMATIC_EXTENSION);
        }
        Path file = target;
        String author = LitematicaPlusConfig.current().defaultAuthor.isBlank() && this.minecraft.player != null
                ? this.minecraft.player.getGameProfile().name() : LitematicaPlusConfig.current().defaultAuthor;
        List<CompoundTag> entities = PlacementBake.entities(this.minecraft.level, volume, scope);
        this.saving = true;
        updateButtons();
        status(Component.translatable("litematicaplus.area.saving"), Theme.TEXT_DIM);
        try {
            Workers.save().execute(() -> {
                Component message;
                int color;
                try {
                    Schematic schematic = PlacementBake.bake(volume, scope, entities, clean, author);
                    SchematicIo.write(file, trimmed ? SchematicTrim.trim(schematic) : schematic);
                    message = Component.translatable("litematicaplus.save_placement.saved", file.getFileName().toString());
                    color = Theme.SUCCESS;
                } catch (Exception | OutOfMemoryError exception) {
                    LitematicaPlusClient.LOGGER.warn("Could not save placement {}", this.placement.name(), exception);
                    message = Component.translatable("litematicaplus.area.error_write", Notify.describe(exception));
                    color = Theme.DANGER;
                }
                Component shown = message;
                int shade = color;
                this.minecraft.execute(() -> {
                    PlacementManager.get().invalidate(file);
                    this.saving = false;
                    status(shown, shade);
                    updateButtons();
                });
            });
        } catch (RejectedExecutionException exception) {
            this.saving = false;
            status(Component.translatable("litematicaplus.area.error_write", Notify.describe(exception)), Theme.DANGER);
            updateButtons();
        }
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        graphics.fill(0, 0, this.width, this.height, Theme.SIDEBAR);
        int width = Math.min(WIDTH, this.width - 32);
        Ui.clipped(graphics, this.font, this.title.getString(), this.panelX, this.topY, width, Theme.TEXT_BRIGHT);
        Ui.clipped(graphics, this.font, Component.translatable("litematicaplus.save_placement.hint").getString(), this.panelX,
                this.topY + 12, width, Theme.TEXT_DIM);
        Ui.wrapped(graphics, this.font, this.status, this.panelX, this.topY + 26 + (Theme.CONTROL_HEIGHT + 4) * 3 + 8
                + Theme.CONTROL_HEIGHT + 6, width, 2, this.statusColor, mouseX, mouseY);
        super.extractRenderState(graphics, mouseX, mouseY, delta);
    }

    @Override
    public void onClose() {
        this.minecraft.gui.setScreen(this.parent);
    }
}
