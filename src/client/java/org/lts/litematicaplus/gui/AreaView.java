package org.lts.litematicaplus.gui;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.RejectedExecutionException;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import org.lts.litematicaplus.LitematicaPlusClient;
import org.lts.litematicaplus.LitematicaPlusConfig;
import org.lts.litematicaplus.placement.PlacementManager;
import org.lts.litematicaplus.schematic.SchematicIo;
import org.lts.litematicaplus.selection.AreaBox;
import org.lts.litematicaplus.selection.SchematicCapture;
import org.lts.litematicaplus.selection.SelectionManager;
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
        } else {
            this.cornerTop = 0;
        }
        this.saveTop = this.top + this.height - Theme.CONTROL_HEIGHT * 2 - 4 - 22;
        int saveWidth = Math.min(110, this.detailWidth / 3);
        EditBox file = field(x, this.saveTop + 11, this.detailWidth, fileName, tr("litematicaplus.area.file_name"), 80);
        file.setResponder(value -> {
            fileName = value;
            this.overwriteArmed = false;
            updateSave();
        });
        this.saveButton = add(FlatButton.primary(x + this.detailWidth - saveWidth,
                this.top + this.height - Theme.CONTROL_HEIGHT, saveWidth, tr("litematicaplus.area.save"), button -> save()));
        updateSave();
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

    private static String cleanName(String raw) {
        String clean = raw.strip().replaceAll("[\\\\/:*?\"<>|\\p{Cntrl}]", "_");
        if (clean.toLowerCase(java.util.Locale.ROOT).endsWith(SchematicIo.LITEMATIC_EXTENSION)) {
            clean = clean.substring(0, clean.length() - SchematicIo.LITEMATIC_EXTENSION.length());
        }
        while (clean.endsWith(".") || clean.endsWith(" ")) {
            clean = clean.substring(0, clean.length() - 1);
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
            setStatus(tr("litematicaplus.area.error_exists", target.getFileName().toString()), Theme.WARNING);
            return;
        }
        this.overwriteArmed = false;
        this.saving = true;
        updateSave();
        setStatus(tr("litematicaplus.area.saving"), Theme.TEXT_DIM);
        String author = LitematicaPlusConfig.current().defaultAuthor.isBlank() && this.minecraft.player != null
                ? this.minecraft.player.getGameProfile().name() : LitematicaPlusConfig.current().defaultAuthor;
        SchematicCapture.capture(this.minecraft, boxes, name, author, "").whenComplete((result, failure) -> {
            if (failure != null || result == null) {
                finish(tr("litematicaplus.area.error_capture", message(failure)), Theme.DANGER);
                return;
            }
            try {
                Workers.io().execute(() -> {
                    try {
                        SchematicIo.write(target, result.schematic());
                        long blocks = result.schematic().metadata().totalBlocks;
                        finish(tr("litematicaplus.area.saved", blocks, target.getFileName().toString()), Theme.SUCCESS);
                    } catch (IOException | RuntimeException exception) {
                        LitematicaPlusClient.LOGGER.error("Could not save schematic {}", target, exception);
                        finish(tr("litematicaplus.area.error_write", String.valueOf(exception.getMessage())), Theme.DANGER);
                    }
                });
            } catch (RejectedExecutionException exception) {
                finish(tr("litematicaplus.area.error_write", String.valueOf(exception.getMessage())), Theme.DANGER);
            }
        });
    }

    private static String message(Throwable failure) {
        if (failure == null) {
            return "";
        }
        Throwable cause = failure.getCause() != null ? failure.getCause() : failure;
        return String.valueOf(cause.getMessage());
    }

    private void finish(Component message, int color) {
        this.minecraft.execute(() -> {
            this.saving = false;
            setStatus(message, color);
            updateSave();
        });
    }

    private void renderRow(GuiGraphicsExtractor graphics, Font rowFont, AreaBox box, int x, int y, int rowWidth,
            int rowHeight, boolean isSelected, boolean hovered) {
        int textY = y + (rowHeight - 8) / 2;
        String size = box.isEmpty() ? "-" : box.sizeX() + "x" + box.sizeY() + "x" + box.sizeZ();
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
            graphics.textWithWordWrap(this.font, tr("litematicaplus.area.tool_hint",
                    LitematicaPlusConfig.current().toolItem), this.detailX, this.top + 2, this.detailWidth, Theme.TEXT_DIM, false);
        }
        Ui.label(graphics, this.font, tr("litematicaplus.area.save_label"), this.detailX, this.saveTop);
        int statusY = this.top + this.height - Theme.CONTROL_HEIGHT + 6;
        int statusWidth = this.detailWidth - Math.min(110, this.detailWidth / 3) - 8;
        Ui.clipped(graphics, this.font, this.status.getString(), this.detailX, statusY, statusWidth, this.statusColor);
    }
}
