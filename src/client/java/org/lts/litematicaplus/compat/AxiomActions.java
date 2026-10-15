package org.lts.litematicaplus.compat;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.RejectedExecutionException;
import java.util.function.BiConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import org.lts.litematicaplus.LitematicaPlusClient;
import org.lts.litematicaplus.LitematicaPlusConfig;
import org.lts.litematicaplus.engine.GhostEngine;
import org.lts.litematicaplus.engine.PlacementRuntime;
import org.lts.litematicaplus.placement.PlacementManager;
import org.lts.litematicaplus.placement.SchematicPlacement;
import org.lts.litematicaplus.schematic.Schematic;
import org.lts.litematicaplus.schematic.SchematicIo;
import org.lts.litematicaplus.selection.AreaBox;
import org.lts.litematicaplus.selection.SelectionManager;
import org.lts.litematicaplus.tool.PasteController;
import org.lts.litematicaplus.util.Notify;
import org.lts.litematicaplus.util.Workers;
import org.lts.litematicaplus.world.LayerRange;
import org.lts.litematicaplus.world.PlacementVolume;

public final class AxiomActions {
    private static volatile boolean busy;

    private AxiomActions() {
    }

    public static boolean busy() {
        return busy;
    }

    public static Component sendPlacement(Minecraft minecraft, SchematicPlacement placement) {
        Component problem = placementProblem(placement);
        if (problem != null) {
            return problem;
        }
        PlacementVolume volume = GhostEngine.get().runtime(placement.id()).volume();
        LayerRange layer = GhostEngine.get().layer();
        boolean entities = LitematicaPlusConfig.current().pasteEntities;
        String name = placement.name();
        busy = true;
        try {
            Workers.io().execute(() -> {
                AxiomBridge.Prepared prepared;
                Throwable failure = null;
                try {
                    prepared = AxiomBridge.prepare(volume, layer, entities);
                } catch (Throwable exception) {
                    prepared = null;
                    failure = exception;
                }
                AxiomBridge.Prepared result = prepared;
                Throwable error = failure;
                minecraft.execute(() -> {
                    busy = false;
                    if (error != null) {
                        LitematicaPlusClient.LOGGER.warn("Could not hand {} to Axiom", name, error);
                        report(Component.translatable("litematicaplus.axiom.send_failed", Notify.describe(error)));
                        return;
                    }
                    if (result == null) {
                        report(Component.translatable("litematicaplus.axiom.nothing"));
                        return;
                    }
                    if (!AxiomBridge.startPlacement(result, name)) {
                        report(Component.translatable("litematicaplus.axiom.rejected"));
                        return;
                    }
                    minecraft.gui.setScreen(null);
                    Notify.actionBar(Component.translatable(result.skippedEntities() > 0
                            ? "litematicaplus.axiom.sent_without_entities" : "litematicaplus.axiom.sent",
                            result.blocks(), result.skippedEntities()));
                });
            });
        } catch (RejectedExecutionException exception) {
            busy = false;
            return Component.translatable("litematicaplus.axiom.send_failed", Notify.describe(exception));
        }
        return null;
    }

    public static Component selectPlacement(SchematicPlacement placement) {
        Component problem = placementProblem(placement);
        if (problem != null) {
            return problem;
        }
        List<AxiomBridge.Bounds> boxes = new ArrayList<>();
        for (PlacementVolume.RegionBox box : GhostEngine.get().runtime(placement.id()).volume().regionBoxes()) {
            boxes.add(new AxiomBridge.Bounds(new BlockPos(box.minX(), box.minY(), box.minZ()),
                    new BlockPos(box.maxX(), box.maxY(), box.maxZ())));
        }
        return AxiomBridge.select(boxes) ? Component.translatable("litematicaplus.axiom.selected", boxes.size())
                : Component.translatable("litematicaplus.axiom.rejected");
    }

    public static Component selectAreas(List<AreaBox> areas) {
        if (!AxiomBridge.available()) {
            return Component.translatable("litematicaplus.axiom.missing");
        }
        List<AxiomBridge.Bounds> boxes = new ArrayList<>();
        for (AreaBox box : areas) {
            if (box.isComplete()) {
                boxes.add(new AxiomBridge.Bounds(new BlockPos(box.minX(), box.minY(), box.minZ()),
                        new BlockPos(box.maxX(), box.maxY(), box.maxZ())));
            }
        }
        if (boxes.isEmpty()) {
            return Component.translatable("litematicaplus.area.error_nothing");
        }
        return AxiomBridge.select(boxes) ? Component.translatable("litematicaplus.axiom.selected", boxes.size())
                : Component.translatable("litematicaplus.axiom.rejected");
    }

    public static Component importSelection() {
        if (!AxiomBridge.available()) {
            return Component.translatable("litematicaplus.axiom.missing");
        }
        AxiomBridge.Bounds bounds = AxiomBridge.selection();
        if (bounds == null) {
            return Component.translatable("litematicaplus.axiom.no_selection");
        }
        SelectionManager selection = SelectionManager.get();
        AreaBox box = selection.addBox();
        box.setName("Axiom");
        selection.setCorner(true, bounds.min());
        selection.setCorner(false, bounds.max());
        return Component.translatable("litematicaplus.axiom.selection_imported", box.sizeX(), box.sizeY(), box.sizeZ());
    }

    public static Component saveClipboard(Minecraft minecraft, Path target, String name, String author,
            BiConsumer<Component, Boolean> done) {
        if (!AxiomBridge.available()) {
            return Component.translatable("litematicaplus.axiom.missing");
        }
        AxiomBridge.Copied copied = AxiomBridge.copyClipboard();
        if (copied == null || copied.blocks().isEmpty()) {
            return Component.translatable("litematicaplus.axiom.no_clipboard");
        }
        busy = true;
        try {
            Workers.save().execute(() -> {
                Component message;
                boolean saved = false;
                try {
                    Schematic schematic = AxiomBridge.toSchematic(copied, name, author);
                    SchematicIo.write(target, schematic);
                    minecraft.execute(() -> PlacementManager.get().invalidate(target));
                    message = Component.translatable("litematicaplus.axiom.clipboard_saved",
                            schematic.metadata().totalBlocks, target.getFileName().toString());
                    saved = true;
                } catch (Throwable exception) {
                    LitematicaPlusClient.LOGGER.warn("Could not save the Axiom clipboard to {}", target, exception);
                    message = Component.translatable("litematicaplus.area.error_write", Notify.describe(exception));
                }
                Component result = message;
                boolean success = saved;
                minecraft.execute(() -> {
                    busy = false;
                    done.accept(result, success);
                });
            });
        } catch (RejectedExecutionException exception) {
            busy = false;
            return Component.translatable("litematicaplus.area.error_write", Notify.describe(exception));
        }
        return null;
    }

    private static Component placementProblem(SchematicPlacement placement) {
        if (!AxiomBridge.available()) {
            return Component.translatable("litematicaplus.axiom.missing");
        }
        if (busy) {
            return Component.translatable("litematicaplus.axiom.busy");
        }
        PlacementRuntime runtime = placement == null ? null : GhostEngine.get().runtime(placement.id());
        if (runtime == null || runtime.volume() == null) {
            return Component.translatable("litematicaplus.message.no_placement");
        }
        if (!PasteController.upToDate(runtime)) {
            return Component.translatable("litematicaplus.message.paste_rebuilding");
        }
        if (!AxiomBridge.active()) {
            return Component.translatable("litematicaplus.axiom.inactive");
        }
        return null;
    }

    private static void report(Component message) {
        Notify.actionBar(message);
        Notify.chat(message);
    }
}
