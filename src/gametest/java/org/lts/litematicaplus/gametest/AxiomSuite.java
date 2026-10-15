package org.lts.litematicaplus.gametest;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.Nameable;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import org.lts.litematicaplus.compat.AxiomActions;
import org.lts.litematicaplus.compat.AxiomBridge;
import org.lts.litematicaplus.engine.GhostEngine;
import org.lts.litematicaplus.engine.PlacementRuntime;
import org.lts.litematicaplus.placement.PlacementManager;
import org.lts.litematicaplus.placement.SchematicPlacement;
import org.lts.litematicaplus.schematic.BlockStateNbt;
import org.lts.litematicaplus.schematic.RegionBuilder;
import org.lts.litematicaplus.schematic.Schematic;
import org.lts.litematicaplus.schematic.SchematicIo;
import org.lts.litematicaplus.schematic.SchematicMetadata;
import org.lts.litematicaplus.schematic.SchematicRegion;
import org.lts.litematicaplus.world.LayerRange;
import org.lts.litematicaplus.world.PlacementVolume;

final class AxiomSuite {
    private static final BlockPos ORIGIN = new BlockPos(140, -60, 40);

    private AxiomSuite() {
    }

    static void run(ClientGameTestContext context, TestSingleplayerContext world) {
        LitematicaPlusGameTest.require(AxiomBridge.installed(), "Axiom must be loaded for this suite");
        LitematicaPlusGameTest.require(AxiomBridge.available(), "the Axiom bridge did not find Axiom's classes");
        Schematic schematic = schematic();
        Path file = context.computeOnClient(client -> {
            Path target = PlacementManager.schematicsDirectory().resolve("gametest/axiom.litematic");
            try {
                SchematicIo.write(target, schematic);
            } catch (IOException exception) {
                throw new UncheckedIOException(exception);
            }
            return target;
        });
        world.getServer().runCommand("fill " + at(ORIGIN.offset(-3, 0, -3)) + " " + at(ORIGIN.offset(8, 6, 8))
                + " minecraft:air");
        world.getServer().runCommand("tp @p " + (ORIGIN.getX() - 3.5) + " -59 " + (ORIGIN.getZ() - 3.5) + " -45 25");
        world.getConnection().waitForClientboundPackets();
        world.getConnection().waitForChunksRender();
        SchematicPlacement placement = context.computeOnClient(client -> {
            SchematicPlacement created = PlacementManager.get().create(file, ORIGIN);
            PlacementManager.get().rotate(created, Rotation.CLOCKWISE_90);
            return created;
        });
        LitematicaPlusGameTest.settle(context, placement);
        context.waitFor(client -> AxiomBridge.active(), 200);
        LitematicaPlusGameTest.require(!context.computeOnClient(client -> AxiomBridge.editorActive()),
                "the Axiom editor should be closed in normal play");
        PlacementVolume volume = context.computeOnClient(client -> GhostEngine.get().runtime(placement.id()).volume());
        List<BlockPos> cells = new ArrayList<>();
        for (int x = volume.minX(); x <= volume.maxX(); x++) {
            for (int y = volume.minY(); y <= volume.maxY(); y++) {
                for (int z = volume.minZ(); z <= volume.maxZ(); z++) {
                    int index = volume.paletteIndexAt(x, y, z);
                    if (index >= 0 && !volume.palette()[index].is(Blocks.STRUCTURE_VOID)) {
                        cells.add(new BlockPos(x, y, z));
                    }
                }
            }
        }
        LitematicaPlusGameTest.require(cells.size() == 4, "the rotated volume should hold 4 blocks, got " + cells.size());

        Component problem = context.computeOnClient(client -> AxiomActions.sendPlacement(client, placement));
        LitematicaPlusGameTest.require(problem == null, "sendPlacement refused: " + (problem == null ? "" : problem.getString()));
        context.waitFor(client -> !AxiomActions.busy(), 200);
        LitematicaPlusGameTest.require(context.computeOnClient(client -> placing()), "Axiom did not start the placement");
        context.waitTicks(5);
        context.takeScreenshot("axiom_01_placement");
        context.runOnClient(client -> invokePlacement("pastePlacement"));
        List<String> wrong = new ArrayList<>();
        for (int wait = 0; wait < 100; wait++) {
            world.getConnection().waitForClientboundPackets();
            context.waitTicks(2);
            wrong.clear();
            for (BlockPos pos : cells) {
                BlockState expected = volume.stateAt(pos.getX(), pos.getY(), pos.getZ());
                BlockState actual = world.getServer().computeOnServer(server -> server.overworld().getBlockState(pos));
                if (actual != expected) {
                    wrong.add(pos.toShortString() + " " + BlockStateNbt.format(expected) + " -> " + BlockStateNbt.format(actual));
                }
            }
            if (wrong.isEmpty()) {
                break;
            }
        }
        context.takeScreenshot("axiom_02_pasted");
        LitematicaPlusGameTest.require(wrong.isEmpty(), "Axiom pasted blocks in the wrong place: " + wrong);
        BlockPos chest = null;
        for (BlockPos pos : cells) {
            if (volume.stateAt(pos.getX(), pos.getY(), pos.getZ()).is(Blocks.CHEST)) {
                chest = pos;
            }
        }
        BlockPos chestPos = chest;
        String name = world.getServer().computeOnServer(server -> {
            BlockEntity entity = chestPos == null ? null : server.overworld().getBlockEntity(chestPos);
            return entity instanceof Nameable nameable && nameable.getCustomName() != null
                    ? nameable.getCustomName().getString() : "";
        });
        LitematicaPlusGameTest.require("loot".equals(name), "the chest lost its data through Axiom, name '" + name + "'");

        Component selected = context.computeOnClient(client -> AxiomActions.selectPlacement(placement));
        AxiomBridge.Bounds bounds = context.computeOnClient(client -> AxiomBridge.selection());
        LitematicaPlusGameTest.require(bounds != null && bounds.min().equals(new BlockPos(volume.minX(), volume.minY(), volume.minZ()))
                && bounds.max().equals(new BlockPos(volume.maxX(), volume.maxY(), volume.maxZ())),
                "Axiom selection " + bounds + " does not match the placement (" + selected.getString() + ")");

        Schematic copied = context.computeOnClient(client -> {
            try {
                AxiomBridge.Prepared prepared = AxiomBridge.prepare(volume, LayerRange.ALL, true);
                LitematicaPlusGameTest.require(AxiomBridge.toClipboard(prepared, "axiom-test"), "Axiom refused the clipboard");
            } catch (ReflectiveOperationException exception) {
                throw new IllegalStateException(exception);
            }
            AxiomBridge.Copied clipboard = AxiomBridge.copyClipboard();
            LitematicaPlusGameTest.require(clipboard != null, "the Axiom clipboard came back empty");
            return AxiomBridge.toSchematic(clipboard, "copied", "test");
        });
        SchematicRegion region = copied.regions().values().iterator().next();
        List<String> differences = new ArrayList<>();
        for (BlockPos pos : cells) {
            BlockState expected = volume.stateAt(pos.getX(), pos.getY(), pos.getZ());
            BlockState actual = region.state(pos.getX() - volume.minX(), pos.getY() - volume.minY(), pos.getZ() - volume.minZ());
            if (actual != expected) {
                differences.add(pos.toShortString() + " " + BlockStateNbt.format(actual));
            }
            if (expected.is(Blocks.CHEST)) {
                CompoundTag tag = region.blockEntities().get(SchematicRegion.localKey(pos.getX() - volume.minX(),
                        pos.getY() - volume.minY(), pos.getZ() - volume.minZ()));
                if (tag == null || !"minecraft:chest".equals(tag.getStringOr("id", ""))) {
                    differences.add("chest data " + tag);
                }
            }
        }
        LitematicaPlusGameTest.require(differences.isEmpty(), "the Axiom clipboard round trip changed blocks: " + differences);

        context.runOnClient(client -> PlacementManager.get().remove(placement));
        world.getServer().runCommand("fill " + at(ORIGIN.offset(-3, 0, -3)) + " " + at(ORIGIN.offset(8, 6, 8))
                + " minecraft:air");
        world.getConnection().waitForClientboundPackets();
        context.waitTicks(5);
    }

    private static Schematic schematic() {
        RegionBuilder builder = new RegionBuilder("Axiom", 0, 0, 0, 5, 3, 4);
        builder.fill(Blocks.STRUCTURE_VOID.defaultBlockState());
        builder.set(0, 0, 0, Blocks.STONE.defaultBlockState());
        builder.set(1, 1, 2, Blocks.GLASS.defaultBlockState());
        builder.set(4, 2, 3, Blocks.OAK_STAIRS.defaultBlockState()
                .setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.EAST));
        builder.set(2, 0, 1, Blocks.CHEST.defaultBlockState());
        CompoundTag chest = new CompoundTag();
        chest.putString("id", "minecraft:chest");
        chest.putString("CustomName", "\"loot\"");
        builder.blockEntity(2, 0, 1, chest);
        SchematicMetadata metadata = new SchematicMetadata();
        metadata.name = "Axiom";
        return new Schematic(metadata, Map.of("Axiom", builder.build()));
    }

    private static boolean placing() {
        Object result = invokePlacement("isPlacing");
        return Boolean.TRUE.equals(result);
    }

    private static Object invokePlacement(String method) {
        try {
            Class<?> type = Class.forName("com.moulberry.axiom.clipboard.Placement");
            Object instance = type.getField("INSTANCE").get(null);
            return type.getMethod(method).invoke(instance);
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException(exception);
        }
    }

    private static String at(BlockPos pos) {
        return pos.getX() + " " + pos.getY() + " " + pos.getZ();
    }
}
