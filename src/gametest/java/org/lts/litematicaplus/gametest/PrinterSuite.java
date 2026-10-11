package org.lts.litematicaplus.gametest;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import org.lts.litematicaplus.LitematicaPlusConfig;
import org.lts.litematicaplus.engine.GhostEngine;
import org.lts.litematicaplus.engine.PlacementRuntime;
import org.lts.litematicaplus.placement.PlacementManager;
import org.lts.litematicaplus.placement.SchematicPlacement;
import org.lts.litematicaplus.printer.Printer;
import org.lts.litematicaplus.printer.PrinterConfig;
import org.lts.litematicaplus.schematic.BlockStateNbt;
import org.lts.litematicaplus.schematic.RegionBuilder;
import org.lts.litematicaplus.schematic.Schematic;
import org.lts.litematicaplus.schematic.SchematicIo;
import org.lts.litematicaplus.schematic.SchematicMetadata;
import org.lts.litematicaplus.tool.PlacementSolver;

final class PrinterSuite {
    private static final BlockPos ORIGIN = new BlockPos(100, -60, 60);
    private static final String[] NEAR = {
        "minecraft:stone",
        "minecraft:stone",
        "minecraft:oak_stairs[facing=west,half=bottom]",
        "minecraft:piston[facing=up]",
        "minecraft:oak_log[axis=x]"
    };
    private static final int FAR_OFFSET = 14;
    private static boolean registered;

    private PrinterSuite() {
    }

    static void run(ClientGameTestContext context, TestSingleplayerContext world) {
        RegionBuilder builder = new RegionBuilder("Printer", 0, 0, 0, FAR_OFFSET + 1, 1, 1);
        List<BlockState> states = new ArrayList<>();
        for (int i = 0; i < NEAR.length; i++) {
            BlockState state = BlockStateNbt.parse(NEAR[i]);
            states.add(state);
            builder.set(i, 0, 0, state);
        }
        builder.set(FAR_OFFSET, 0, 0, BlockStateNbt.parse("minecraft:stone"));
        SchematicMetadata metadata = new SchematicMetadata();
        metadata.name = "Printer";
        Schematic schematic = new Schematic(metadata, Map.of("Printer", builder.build()));
        Path file = context.computeOnClient(client -> {
            Path target = PlacementManager.schematicsDirectory().resolve("gametest/printer.litematic");
            try {
                SchematicIo.write(target, schematic);
            } catch (IOException exception) {
                throw new UncheckedIOException(exception);
            }
            return target;
        });
        BlockPos far = ORIGIN.offset(FAR_OFFSET, 0, 0);
        world.getServer().runCommand("fill " + at(ORIGIN.offset(-2, 0, -4)) + " " + at(far.offset(2, 3, 2))
                + " minecraft:air");
        world.getServer().runCommand("gamemode survival @p");
        world.getServer().runCommand("clear @p");
        world.getServer().runCommand("give @p minecraft:stone 8");
        world.getServer().runCommand("give @p minecraft:oak_stairs 2");
        world.getServer().runCommand("give @p minecraft:piston 2");
        world.getServer().runCommand("give @p minecraft:oak_log 2");
        world.getServer().runCommand("tp @p " + (ORIGIN.getX() + 2.5) + " -60 " + (ORIGIN.getZ() - 2.5) + " 0 30");
        world.getConnection().waitForClientboundPackets();
        world.getConnection().waitForChunksRender();
        SchematicPlacement placement = context.computeOnClient(client -> PlacementManager.get().create(file, ORIGIN));
        context.waitFor(client -> {
            PlacementRuntime runtime = GhostEngine.get().runtime(placement.id());
            return runtime != null && runtime.volume() != null && runtime.pendingSections() == 0
                    && GhostEngine.get().inFlight() == 0 && runtime.verifiedSections() == runtime.slotCount();
        }, 400);
        context.runOnClient(client -> {
            LitematicaPlusConfig config = LitematicaPlusConfig.currentCopy();
            config.renderEnabled = true;
            config.easyPlaceEnabled = false;
            config.easyPlaceFakeRotation = true;
            LitematicaPlusConfig.save(config);
            if (!registered) {
                registered = true;
                ClientTickEvents.END_CLIENT_TICK.register(Printer::tick);
            }
            PrinterConfig.update(printer -> {
                printer.enabled = true;
                printer.range = 4;
            });
        });
        for (int wait = 0; wait < 200 && !placedAll(world, states); wait++) {
            context.waitTicks(2);
        }
        context.waitTicks(20);
        context.takeScreenshot("13_printer");
        context.runOnClient(client -> PrinterConfig.update(printer -> printer.enabled = false));
        List<String> failures = new ArrayList<>();
        for (int i = 0; i < NEAR.length; i++) {
            BlockPos pos = ORIGIN.offset(i, 0, 0);
            BlockState actual = world.getServer().computeOnServer(server -> server.overworld().getBlockState(pos));
            if (!matches(states.get(i), actual)) {
                failures.add(NEAR[i] + " -> " + BlockStateNbt.format(actual));
            }
        }
        boolean farEmpty = world.getServer().computeOnServer(server -> server.overworld().getBlockState(far).isAir());
        long actions = context.computeOnClient(client -> Printer.actions());
        context.runOnClient(client -> PlacementManager.get().remove(placement));
        world.getServer().runCommand("fill " + at(ORIGIN.offset(-2, 0, -4)) + " " + at(far.offset(2, 3, 2))
                + " minecraft:air");
        world.getServer().runCommand("clear @p");
        world.getServer().runCommand("gamemode creative @p");
        world.getConnection().waitForClientboundPackets();
        context.waitTicks(5);
        LitematicaPlusGameTest.require(failures.isEmpty(), "the printer placed wrong states: " + failures);
        LitematicaPlusGameTest.require(farEmpty, "the printer must not reach past its range");
        LitematicaPlusGameTest.require(actions >= NEAR.length, "the printer should act once per block, acted " + actions);
    }

    private static boolean placedAll(TestSingleplayerContext world, List<BlockState> states) {
        for (int i = 0; i < states.size(); i++) {
            BlockPos pos = ORIGIN.offset(i, 0, 0);
            BlockState actual = world.getServer().computeOnServer(server -> server.overworld().getBlockState(pos));
            if (!matches(states.get(i), actual)) {
                return false;
            }
        }
        return true;
    }

    private static boolean matches(BlockState expected, BlockState actual) {
        return actual.getBlock() == expected.getBlock() && PlacementSolver.orientationMatches(actual, expected);
    }

    private static String at(BlockPos pos) {
        return pos.getX() + " " + pos.getY() + " " + pos.getZ();
    }
}
