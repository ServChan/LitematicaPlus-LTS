package org.lts.litematicaplus.gui;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import org.lts.litematicaplus.LitematicaPlusClient;
import org.lts.litematicaplus.LitematicaPlusConfig;
import org.lts.litematicaplus.compat.AxiomActions;
import org.lts.litematicaplus.compat.AxiomBridge;
import org.lts.litematicaplus.placement.PlacementManager;
import org.lts.litematicaplus.schematic.SchematicIo;
import org.lts.litematicaplus.selection.AreaBox;
import org.lts.litematicaplus.selection.SchematicCapture;
import org.lts.litematicaplus.selection.SelectionManager;
import org.lts.litematicaplus.util.Notify;
import org.lts.litematicaplus.util.Workers;

final class AreaView extends TabView {
    private static String fileName = "";

    private ListPanel<AreaBox> list;
    private FlatButton saveButton;
    private Component status = Component.empty();
    private int statusColor = Theme.TEXT_DIM;
    private boolean overwriteArmed;
    private boolean saving;
    private int detailX;
    private int detailWidth;
    private int cornerTop;
    private int saveTop;
    private int axiomTop;
    private int coverageTicks;
    private long missingChunks;
    private long totalChunks;

    @Override
    void tick() {
        if (--this.coverageTicks > 0) {
            return;
        }
        this.coverageTicks = 20;
        this.missingChunks = 0L;
        this.totalChunks = 0L;
        if (this.minecraft.level == null || this.minecraft.hasSingleplayerServer()) {
            return;
        }
        it.unimi.dsi.fastutil.longs.LongSet seen = new it.unimi.dsi.fastutil.longs.LongOpenHashSet();
        for (AreaBox box : SelectionManager.get().completeBoxes()) {
            for (int chunkZ = box.minZ() >> 4; chunkZ <= box.maxZ() >> 4 && seen.size() < 65536; chunkZ++) {
                for (int chunkX = box.minX() >> 4; chunkX <= box.maxX() >> 4 && seen.size() < 65536; chunkX++) {
                    if (seen.add(net.minecraft.world.level.ChunkPos.pack(chunkX, chunkZ))) {
                        this.totalChunks++;
                        if (!this.minecraft.level.hasChunk(chunkX, chunkZ)) {
                            this.missingChunks++;
                        }
                    }
                }
            }
        }
    }

    AreaView(HubScreen screen) {
        super(screen);
    }

    @Override
    Component summary() {
        SelectionManager selection = SelectionManager.get();
        return tr("litematicaplus.area.summary", selection.boxes().size(), selection.totalVolume());
    }

    @Override
    void init() {
        SelectionManager selection = SelectionManager.get();
        int listWidth = Math.max(100, this.width * 34 / 100);
        this.detailX = this.left + listWidth + Theme.GAP;
        this.detailWidth = this.width - listWidth - Theme.GAP;
        int buttonsY = this.top + this.height - Theme.CONTROL_HEIGHT;
        this.list = add(new ListPanel<AreaBox>(this.left, this.top, listWidth, this.height - Theme.CONTROL_HEIGHT - 4,
                Theme.ROW_HEIGHT, tr("litematicaplus.tab.area"), this::renderRow)
                .describe(AreaBox::name)
                .onSelect(box -> {
                    selection.select(selection.boxes().indexOf(box));
                    this.screen.refresh();
                })
                .empty(tr("litematicaplus.area.empty_title"), tr("litematicaplus.area.empty_hint")));
        this.list.setItems(new ArrayList<>(selection.boxes()));
        this.list.select(selection.boxes().isEmpty() ? -1 : selection.selectedIndex());
        int half = (listWidth - Theme.GAP) / 2;
        add(FlatButton.secondary(this.left, buttonsY, half, tr("litematicaplus.area.add_box"), button -> {
            selection.addBox();
            this.screen.refresh();
        }));
        FlatButton remove = add(FlatButton.danger(this.left + half + Theme.GAP, buttonsY, listWidth - half - Theme.GAP,
                tr("litematicaplus.area.remove_box"), button -> {
                    selection.removeSelected();
                    this.screen.refresh();
                }));
        remove.active = selection.selectedBox() != null;
        int x = this.detailX;
        int y = this.top;
        int cornersBottom = this.top;
        AreaBox box = selection.selectedBox();
        if (box != null) {
            EditBox name = field(x, y, this.detailWidth, box.name(), tr("litematicaplus.area.box_name"), 64);
            name.setResponder(value -> {
                box.setName(value.strip());
                selection.notifyChanged();
            });
            y += Theme.CONTROL_HEIGHT + 4;
            this.cornerTop = y;
            y = cornerRow(x, y + 11, box, true);
            y = cornerRow(x, y + 11, box, false);
            cornersBottom = y;
        } else {
            this.cornerTop = 0;
        }
        this.saveTop = this.top + this.height - Theme.CONTROL_HEIGHT * 2 - 4 - 22;
        this.axiomTop = 0;
        int axiomTop = this.saveTop - Theme.CONTROL_HEIGHT - 4 - 11;
        if (AxiomBridge.available() && axiomTop >= cornersBottom + 2) {
            this.axiomTop = axiomTop;
            addAxiomRow(x, axiomTop + 11);
        }
        int saveWidth = Math.min(110, this.detailWidth / 3);
        EditBox file = field(x, this.saveTop + 11, this.detailWidth, fileName, tr("litematicaplus.area.file_name"), 80);
        file.setResponder(value -> {
            fileName = value;
            disarmOverwrite();
            updateSave();
        });
        this.saveButton = add(FlatButton.primary(x + this.detailWidth - saveWidth,
                this.top + this.height - Theme.CONTROL_HEIGHT, saveWidth,
                tr(this.overwriteArmed ? "litematicaplus.area.replace" : "litematicaplus.area.save"), button -> save()));
        updateSave();
    }

    private void addAxiomRow(int x, int y) {
        int third = (this.detailWidth - Theme.GAP * 2) / 3;
        FlatButton take = add(FlatButton.secondary(x, y, third, tr("litematicaplus.axiom.take_selection"), button -> {
            Component message = AxiomActions.importSelection();
            this.screen.refresh();
            setStatus(message, Theme.TEXT_DIM);
        }));
        take.setTooltip(Tooltip.create(tr("litematicaplus.axiom.take_selection.tip")));
        FlatButton give = add(FlatButton.secondary(x + third + Theme.GAP, y, third, tr("litematicaplus.axiom.give_selection"),
                button -> setStatus(AxiomActions.selectAreas(SelectionManager.get().completeBoxes()), Theme.TEXT_DIM)));
        give.setTooltip(Tooltip.create(tr("litematicaplus.axiom.give_selection.tip")));
        give.active = !SelectionManager.get().completeBoxes().isEmpty();
        FlatButton clipboard = add(FlatButton.secondary(x + (third + Theme.GAP) * 2, y,
                this.detailWidth - (third + Theme.GAP) * 2, tr("litematicaplus.axiom.save_clipboard"),
                button -> saveAxiomClipboard()));
        clipboard.setTooltip(Tooltip.create(tr("litematicaplus.axiom.save_clipboard.tip")));
    }

    private void saveAxiomClipboard() {
        if (this.saving) {
            return;
        }
        String name = cleanName(fileName);
        if (name.isEmpty()) {
            name = "axiom_clipboard";
        }
        Path directory = PlacementManager.schematicsDirectory();
        Path target = directory.resolve(name + SchematicIo.LITEMATIC_EXTENSION);
        for (int suffix = 2; Files.exists(target); suffix++) {
            target = directory.resolve(name + "_" + suffix + SchematicIo.LITEMATIC_EXTENSION);
        }
        String author = LitematicaPlusConfig.current().defaultAuthor.isBlank() && this.minecraft.player != null
                ? this.minecraft.player.getGameProfile().name() : LitematicaPlusConfig.current().defaultAuthor;
        this.saving = true;
        updateSave();
        setStatus(tr("litematicaplus.area.saving"), Theme.TEXT_DIM);
        Component problem = AxiomActions.saveClipboard(this.minecraft, target, name, author, (message, saved) -> {
            this.saving = false;
            setStatus(message, saved ? Theme.SUCCESS : Theme.DANGER);
            updateSave();
        });
        if (problem != null) {
            this.saving = false;
            setStatus(problem, Theme.WARNING);
            updateSave();
        }
    }

    private int cornerRow(int x, int y, AreaBox box, boolean first) {
        SelectionManager selection = SelectionManager.get();
        BlockPos corner = first ? box.corner1() : box.corner2();
        int cell = (this.detailWidth - Theme.GAP * 3) / 4;
        EditBox[] fields = new EditBox[3];
        for (int axis = 0; axis < 3; axis++) {
            String value = corner == null ? "" : Integer.toString(axis == 0 ? corner.getX() : axis == 1 ? corner.getY() : corner.getZ());
            fields[axis] = field(x + axis * (cell + Theme.GAP), y, cell, value,
                    Component.literal(axis == 0 ? "X" : axis == 1 ? "Y" : "Z"), 9);
        }
        for (EditBox field : fields) {
            field.setResponder(ignored -> {
                Integer px = parse(fields[0]);
                Integer py = parse(fields[1]);
                Integer pz = parse(fields[2]);
                if (px != null && py != null && pz != null) {
                    BlockPos pos = new BlockPos(px, py, pz);
                    if (first) {
                        box.setCorner1(pos);
                    } else {
                        box.setCorner2(pos);
                    }
                    selection.notifyChanged();
                }
            });
        }
        FlatButton here = add(FlatButton.secondary(x + (cell + Theme.GAP) * 3, y, this.detailWidth - (cell + Theme.GAP) * 3,
                tr("litematicaplus.area.here"), button -> {
                    if (this.minecraft.player != null) {
                        selection.setCorner(first, this.minecraft.player.blockPosition());
                        this.screen.refresh();
                    }
                }));
        here.active = this.minecraft.player != null;
        return y + Theme.CONTROL_HEIGHT + 2;
    }

    private static Integer parse(EditBox field) {
        String text = field.getValue().strip();
        try {
            int value = Integer.parseInt(text);
            if (value < -30000000 || value > 30000000) {
                throw new NumberFormatException();
            }
            field.setTextColor(Theme.TEXT);
            return value;
        } catch (NumberFormatException exception) {
            field.setTextColor(Theme.DANGER);
            return null;
        }
    }

    private void disarmOverwrite() {
        this.overwriteArmed = false;
        if (this.saveButton != null) {
            this.saveButton.setMessage(tr("litematicaplus.area.save"));
        }
    }

    private static String cleanName(String raw) {
        String clean = raw.strip().replaceAll("[\\\\/:*?\"<>|\\p{Cntrl}]", "_");
        if (clean.toLowerCase(java.util.Locale.ROOT).endsWith(SchematicIo.LITEMATIC_EXTENSION)) {
            clean = clean.substring(0, clean.length() - SchematicIo.LITEMATIC_EXTENSION.length());
        }
        while (clean.endsWith(".") || clean.endsWith(" ")) {
            clean = clean.substring(0, clean.length() - 1);
        }
        if (clean.matches("(?i)(con|prn|aux|nul|com[1-9]|lpt[1-9])(\\..*)?")) {
            clean = "_" + clean;
        }
        return clean;
    }

    private void updateSave() {
        if (this.saveButton != null) {
            this.saveButton.active = !this.saving && !cleanName(fileName).isEmpty()
                    && !SelectionManager.get().completeBoxes().isEmpty() && this.minecraft.level != null;
        }
    }

    private void setStatus(Component message, int color) {
        this.status = message;
        this.statusColor = color;
    }

    private void save() {
        String name = cleanName(fileName);
        List<SchematicCapture.Box> boxes = SchematicCapture.freeze(SelectionManager.get().completeBoxes());
        if (name.isEmpty() || boxes.isEmpty() || this.minecraft.level == null) {
            setStatus(tr("litematicaplus.area.error_nothing"), Theme.DANGER);
            return;
        }
        Path target = PlacementManager.schematicsDirectory().resolve(name + SchematicIo.LITEMATIC_EXTENSION);
        if (Files.exists(target) && !this.overwriteArmed) {
            this.overwriteArmed = true;
            this.saveButton.setMessage(tr("litematicaplus.area.replace"));
            setStatus(tr("litematicaplus.area.error_exists", target.getFileName().toString()), Theme.WARNING);
            return;
        }
        disarmOverwrite();
        this.saving = true;
        updateSave();
        setStatus(tr("litematicaplus.area.saving"), Theme.TEXT_DIM);
        String author = LitematicaPlusConfig.current().defaultAuthor.isBlank() && this.minecraft.player != null
                ? this.minecraft.player.getGameProfile().name() : LitematicaPlusConfig.current().defaultAuthor;
        SchematicCapture.capture(this.minecraft, boxes, name, author, "").whenComplete((result, failure) -> {
            if (failure != null || result == null) {
                finish(tr("litematicaplus.area.error_capture", Notify.describe(failure)), Theme.DANGER);
                return;
            }
            try {
                Workers.save().execute(() -> {
                    try {
                        SchematicIo.write(target, result.schematic());
                        long blocks = result.schematic().metadata().totalBlocks;
                        this.minecraft.execute(() -> PlacementManager.get().invalidate(target));
                        if (result.unloadedChunks() > 0) {
                            finish(tr("litematicaplus.area.saved_partial", blocks, target.getFileName().toString(),
                                    result.unloadedChunks()), Theme.WARNING);
                        } else if (result.voxyChunks() > 0) {
                            finish(tr("litematicaplus.area.saved_voxy", blocks, target.getFileName().toString(),
                                    result.voxyChunks()), Theme.SUCCESS);
                        } else {
                            finish(tr("litematicaplus.area.saved", blocks, target.getFileName().toString()), Theme.SUCCESS);
                        }
                    } catch (Throwable exception) {
                        LitematicaPlusClient.LOGGER.error("Could not save schematic {}", target, exception);
                        finish(tr("litematicaplus.area.error_write", Notify.describe(exception)), Theme.DANGER);
                    }
                });
            } catch (Throwable exception) {
                finish(tr("litematicaplus.area.error_write", Notify.describe(exception)), Theme.DANGER);
            }
        });
    }

    private void finish(Component message, int color) {
        this.minecraft.execute(() -> {
            this.saving = false;
            setStatus(message, color);
            updateSave();
        });
    }

    private int step(GuiGraphicsExtractor graphics, int number, Component text, int y, int limit) {
        int textX = this.detailX + 20;
        int textWidth = this.detailWidth - 20;
        int lines = this.font.split(text, textWidth).size();
        if (y + lines * 9 > limit) {
            return limit;
        }
        graphics.fill(this.detailX, y - 2, this.detailX + 13, y + 11, Theme.SURFACE_RAISED);
        graphics.outline(this.detailX, y - 2, 13, 13, Theme.BORDER);
        graphics.centeredText(this.font, Integer.toString(number), this.detailX + 7, y + 1, Theme.AMBER);
        graphics.textWithWordWrap(this.font, text, textX, y, textWidth, Theme.TEXT_DIM, false);
        return y + Math.max(13, lines * 9) + 8;
    }

    private void renderRow(GuiGraphicsExtractor graphics, Font rowFont, AreaBox box, int x, int y, int rowWidth,
            int rowHeight, boolean isSelected, boolean hovered) {
        int textY = y + (rowHeight - 8) / 2;
        String size = box.isEmpty() ? "-" : box.isComplete() ? box.sizeX() + "x" + box.sizeY() + "x" + box.sizeZ()
                : tr("litematicaplus.area.incomplete").getString();
        int sizeWidth = rowFont.width(size);
        Ui.clipped(graphics, rowFont, box.name().isBlank() ? "?" : box.name(), x + 8, textY, rowWidth - sizeWidth - 22,
                Theme.TEXT_BRIGHT);
        Ui.right(graphics, rowFont, size, x + rowWidth - 6, textY, box.isComplete() ? Theme.TEXT_DIM : Theme.WARNING);
    }

    @Override
    void extract(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        if (this.cornerTop > 0) {
            Ui.label(graphics, this.font, tr("litematicaplus.area.corner_first"), this.detailX, this.cornerTop + 1);
            Ui.label(graphics, this.font, tr("litematicaplus.area.corner_second"), this.detailX,
                    this.cornerTop + 12 + Theme.CONTROL_HEIGHT + 2);
        } else {
            int y = this.top + 2;
            int limit = (this.axiomTop > 0 ? this.axiomTop : this.saveTop) - 14;
            Component[] steps = {tr("litematicaplus.area.step1", LitematicaPlusConfig.current().toolItem),
                    tr("litematicaplus.area.step2"), tr("litematicaplus.area.step3")};
            int needed = 0;
            for (Component text : steps) {
                needed += Math.max(13, this.font.split(text, this.detailWidth - 20).size() * 9) + 8;
            }
            int introHeight = this.font.split(tr("litematicaplus.area.intro"), this.detailWidth).size() * 9 + 10;
            if (y + introHeight + needed <= limit) {
                graphics.textWithWordWrap(this.font, tr("litematicaplus.area.intro"), this.detailX, y, this.detailWidth,
                        Theme.TEXT, false);
                y += introHeight;
            }
            y = step(graphics, 1, steps[0], y, limit);
            y = step(graphics, 2, steps[1], y, limit);
            y = step(graphics, 3, steps[2], y, limit);
            if (y + 20 < limit) {
                graphics.textWithWordWrap(this.font, tr("litematicaplus.area.step_note"), this.detailX, y + 4,
                        this.detailWidth, Theme.TEXT_DIM, false);
            }
        }
        if (this.axiomTop > 0) {
            Ui.label(graphics, this.font, tr("litematicaplus.axiom.row_label"), this.detailX, this.axiomTop);
        }
        Ui.label(graphics, this.font, tr("litematicaplus.area.save_label"), this.detailX, this.saveTop);
        int statusY = this.top + this.height - Theme.CONTROL_HEIGHT + 1;
        int statusWidth = this.detailWidth - Math.min(110, this.detailWidth / 3) - 8;
        if (this.status.getString().isEmpty() && this.missingChunks > 0L && !this.saving) {
            boolean voxy = LitematicaPlusConfig.current().useWorldCache && org.lts.litematicaplus.world.WorldCache.available();
            Ui.wrapped(graphics, this.font, tr(voxy ? "litematicaplus.area.unloaded_voxy" : "litematicaplus.area.unloaded_warning",
                    this.missingChunks, this.totalChunks),
                    this.detailX, statusY, statusWidth, 2, Theme.WARNING, mouseX, mouseY);
        } else {
            Ui.wrapped(graphics, this.font, this.status, this.detailX, statusY, statusWidth, 2, this.statusColor, mouseX, mouseY);
        }
    }
}
