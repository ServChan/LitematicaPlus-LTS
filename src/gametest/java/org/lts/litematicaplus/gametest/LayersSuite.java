package org.lts.litematicaplus.gametest;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import org.lts.litematicaplus.engine.GhostEngine;
import org.lts.litematicaplus.engine.PlacementRuntime;
import org.lts.litematicaplus.gui.HubScreen;
import org.lts.litematicaplus.placement.SchematicPlacement;
import org.lts.litematicaplus.preview.PreviewService;
import org.lts.litematicaplus.schematic.Schematic;
import org.lts.litematicaplus.schematic.SchematicRegion;
import org.lts.litematicaplus.tool.ToolController;
import org.lts.litematicaplus.verify.MismatchType;
import org.lts.litematicaplus.world.LayerRange;

final class LayersSuite {
    private interface Cell {
        boolean test(int x, int y, int z);
    }

    private LayersSuite() {
    }

    static void run(ClientGameTestContext context, SchematicPlacement placement, Schematic house, BlockPos origin) {
        PlacementRuntime runtime = context.computeOnClient(client -> GhostEngine.get().runtime(placement.id()));
        int layerY = origin.getY() + 1;
        context.runOnClient(client -> ToolController.get().applyLayer(
                new LayerRange(LayerRange.Mode.SINGLE, Direction.Axis.Y, layerY, 0, 0)));
        LitematicaPlusGameTest.settle(context, placement);
        context.waitFor(client -> runtime.scoped(), 200);
        long layerBlocks = count(house, (x, y, z) -> y == 1);
        LitematicaPlusGameTest.require(runtime.totalBlocks() == layerBlocks,
                "a single layer should count only its blocks, expected " + layerBlocks + " got " + runtime.totalBlocks());
        LitematicaPlusGameTest.require(runtime.count(MismatchType.WRONG_STATE) == 1 && runtime.count(MismatchType.EXTRA) == 1
                        && runtime.count(MismatchType.CORRECT) == 0 && runtime.count(MismatchType.WRONG_BLOCK) == 0,
                "layer counters should see only the stairs and the gold block, got correct "
                        + runtime.count(MismatchType.CORRECT) + " wrong " + runtime.count(MismatchType.WRONG_BLOCK)
                        + " state " + runtime.count(MismatchType.WRONG_STATE) + " extra " + runtime.count(MismatchType.EXTRA));
        LitematicaPlusGameTest.require(runtime.fullCount(MismatchType.WRONG_BLOCK) == 1,
                "the full verdict should still know the dirt below the layer");

        context.runOnClient(client -> {
            ToolController.get().applyLayer(LayerRange.ALL);
            GhostEngine.get().setClip(new LayerRange.Clip(true, true, origin.getX(), origin.getX() + 3, false, 0, 0));
        });
        LitematicaPlusGameTest.settle(context, placement);
        context.waitFor(client -> runtime.scoped(), 200);
        long clipped = count(house, (x, y, z) -> x <= 3);
        LitematicaPlusGameTest.require(runtime.totalBlocks() == clipped,
                "an X bound should count only blocks inside it, expected " + clipped + " got " + runtime.totalBlocks());
        boolean outside = context.computeOnClient(client -> GhostEngine.get().schematicStateAt(origin.getX() + 5,
                origin.getY(), origin.getZ() + 3) == null);
        boolean inside = context.computeOnClient(client -> GhostEngine.get().schematicStateAt(origin.getX() + 2,
                origin.getY(), origin.getZ() + 3) != null);
        LitematicaPlusGameTest.require(outside && inside, "blocks outside the X bound should not count as schematic blocks");
        context.waitTicks(5);
        context.takeScreenshot("03b_clip_x");
        context.setScreen(() -> HubScreen.layers(null));
        context.waitTicks(10);
        context.takeScreenshot("gui_layers_clip");
        context.setScreen(() -> null);
        context.waitTicks(2);

        context.runOnClient(client -> GhostEngine.get().setClip(LayerRange.Clip.NONE));
        LitematicaPlusGameTest.settle(context, placement);
        LitematicaPlusGameTest.require(!runtime.scoped() && runtime.totalBlocks() == runtime.volume().totalBlocks(),
                "without a layer or bounds the counters should cover the whole placement");
    }

    static void preview(ClientGameTestContext context, Path file) {
        PreviewService.Source source;
        try {
            source = PreviewService.Source.file(file, Files.size(file), Files.getLastModifiedTime(file).toMillis());
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
        context.waitFor(client -> PreviewService.get(source).status() != PreviewService.Status.LOADING, 600);
        PreviewService.Status status = context.computeOnClient(client -> PreviewService.get(source).status());
        boolean texture = context.computeOnClient(client -> PreviewService.get(source).texture() != null);
        LitematicaPlusGameTest.require(status == PreviewService.Status.READY && texture,
                "the schematic preview should be ready, got " + status);
    }

    private static long count(Schematic schematic, Cell cell) {
        long total = 0L;
        for (SchematicRegion region : schematic.regions().values()) {
            for (int y = 0; y < region.absY(); y++) {
                for (int z = 0; z < region.absZ(); z++) {
                    for (int x = 0; x < region.absX(); x++) {
                        BlockState state = region.state(x, y, z);
                        if (!state.isAir() && !state.is(Blocks.STRUCTURE_VOID)
                                && cell.test(region.minX() + x, region.minY() + y, region.minZ() + z)) {
                            total++;
                        }
                    }
                }
            }
        }
        return total;
    }
}
