package org.lts.litematicaplus.gametest;

import com.mojang.blaze3d.platform.InputConstants;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.util.Map;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import org.lts.litematicaplus.LitematicaPlusConfig;
import org.lts.litematicaplus.engine.GhostEngine;
import org.lts.litematicaplus.placement.PlacementManager;
import org.lts.litematicaplus.placement.SchematicPlacement;
import org.lts.litematicaplus.schematic.RegionBuilder;
import org.lts.litematicaplus.schematic.Schematic;
import org.lts.litematicaplus.schematic.SchematicIo;
import org.lts.litematicaplus.schematic.SchematicMetadata;
import org.lts.litematicaplus.tool.InventoryHighlight;
import org.lts.litematicaplus.verify.MismatchType;

final class FeaturesSuite {
    private static final BlockPos BASE = new BlockPos(60, -60, 84);

    private FeaturesSuite() {
    }

    static void run(ClientGameTestContext context, TestSingleplayerContext world) {
        looseAndReplacements(context, world);
        inventoryHighlight(context, world);
        igniteCampfire(context, world);
        dirtForGrassAndShovelGuard(context, world);
        hiddenSchematicIsQuiet(context, world);
    }

    private static Path write(ClientGameTestContext context, String name, RegionBuilder builder) {
        SchematicMetadata metadata = new SchematicMetadata();
        metadata.name = name;
        Schematic schematic = new Schematic(metadata, Map.of(name, builder.build()));
        return context.computeOnClient(client -> {
            Path target = PlacementManager.schematicsDirectory().resolve("gametest/" + name.toLowerCase() + ".litematic");
            try {
                SchematicIo.write(target, schematic);
            } catch (IOException exception) {
                throw new UncheckedIOException(exception);
            }
            return target;
        });
    }

    private static void config(ClientGameTestContext context, java.util.function.Consumer<LitematicaPlusConfig> change) {
        context.runOnClient(client -> {
            LitematicaPlusConfig config = LitematicaPlusConfig.currentCopy();
            change.accept(config);
            LitematicaPlusConfig.save(config);
        });
    }

    private static String at(BlockPos pos) {
        return pos.getX() + " " + pos.getY() + " " + pos.getZ();
    }

    private static void looseAndReplacements(ClientGameTestContext context, TestSingleplayerContext world) {
        BlockPos quartz = BASE;
        BlockPos planks = BASE.east(2);
        RegionBuilder builder = new RegionBuilder("Loose", 0, 0, 0, 3, 1, 1);
        builder.set(0, 0, 0, Blocks.QUARTZ_BLOCK.defaultBlockState());
        builder.set(2, 0, 0, Blocks.OAK_PLANKS.defaultBlockState());
        Path file = write(context, "Loose", builder);
        world.getServer().runCommand("setblock " + at(quartz) + " minecraft:stone");
        world.getServer().runCommand("setblock " + at(planks) + " minecraft:spruce_planks");
        world.getServer().runCommand("tp @p " + (BASE.getX() + 1.5) + " " + BASE.getY() + " " + (BASE.getZ() - 3.5) + " 0 20");
        world.getConnection().waitForClientboundPackets();
        config(context, config -> config.looseBlocks = false);
        SchematicPlacement placement = context.computeOnClient(client -> PlacementManager.get().create(file, BASE));
        context.waitFor(client -> GhostEngine.get().categoryAt(quartz.getX(), quartz.getY(), quartz.getZ())
                == MismatchType.WRONG_BLOCK, 400);
        config(context, config -> config.looseBlocks = true);
        context.waitFor(client -> GhostEngine.get().categoryAt(quartz.getX(), quartz.getY(), quartz.getZ())
                == MismatchType.CORRECT, 400);
        config(context, config -> {
            config.looseBlocks = false;
            config.materialSwap = true;
        });
        context.waitFor(client -> GhostEngine.get().categoryAt(planks.getX(), planks.getY(), planks.getZ())
                == MismatchType.WRONG_BLOCK, 400);
        context.runOnClient(client -> PlacementManager.get().setReplacement(placement, Blocks.OAK_PLANKS, Blocks.SPRUCE_PLANKS));
        context.waitFor(client -> GhostEngine.get().categoryAt(planks.getX(), planks.getY(), planks.getZ())
                == MismatchType.CORRECT, 400);
        boolean replacedGhost = context.computeOnClient(client -> GhostEngine.get().schematicStateAt(planks.getX(),
                planks.getY(), planks.getZ()).is(Blocks.SPRUCE_PLANKS));
        context.takeScreenshot("12a_loose_and_replacements");
        context.runOnClient(client -> PlacementManager.get().remove(placement));
        world.getServer().runCommand("fill " + at(quartz) + " " + at(planks) + " minecraft:air");
        LitematicaPlusGameTest.require(replacedGhost, "the replaced block should be what the ghost asks for");
    }

    private static void inventoryHighlight(ClientGameTestContext context, TestSingleplayerContext world) {
        BlockPos spot = BASE.east(6);
        RegionBuilder builder = new RegionBuilder("Highlight", 0, 0, 0, 1, 1, 1);
        builder.set(0, 0, 0, Blocks.DIRT.defaultBlockState());
        Path file = write(context, "Highlight", builder);
        world.getServer().runCommand("setblock " + at(spot) + " minecraft:air");
        world.getServer().runCommand("gamemode survival @p");
        world.getServer().runCommand("clear @p");
        world.getServer().runCommand("tp @p " + (spot.getX() + 0.5) + " " + spot.getY() + " " + (spot.getZ() - 2.5) + " 0 20");
        world.getConnection().waitForClientboundPackets();
        config(context, config -> config.inventoryHighlight = true);
        SchematicPlacement placement = context.computeOnClient(client -> PlacementManager.get().create(file, spot));
        context.waitFor(client -> GhostEngine.get().categoryAt(spot.getX(), spot.getY(), spot.getZ())
                == MismatchType.MISSING, 400);
        context.waitTicks(25);
        boolean emptyWithout = context.computeOnClient(client -> !InventoryHighlight.positions().contains(spot));
        world.getServer().runCommand("item replace entity @p hotbar.4 with minecraft:dirt 3");
        world.getConnection().waitForClientboundPackets();
        context.waitTicks(25);
        boolean litWith = context.computeOnClient(client -> InventoryHighlight.positions().contains(spot));
        context.takeScreenshot("12b_inventory_highlight");
        config(context, config -> config.inventoryHighlight = false);
        context.runOnClient(client -> PlacementManager.get().remove(placement));
        world.getServer().runCommand("gamemode creative @p");
        world.getServer().runCommand("clear @p");
        world.getConnection().waitForClientboundPackets();
        LitematicaPlusGameTest.require(emptyWithout, "nothing should be highlighted without the item");
        LitematicaPlusGameTest.require(litWith, "the dirt ghost should be highlighted once dirt is in the hotbar");
    }

    private static void dirtForGrassAndShovelGuard(ClientGameTestContext context, TestSingleplayerContext world) {
        BlockPos spot = BASE.east(14);
        RegionBuilder builder = new RegionBuilder("Soil", 0, 0, 0, 1, 1, 1);
        builder.set(0, 0, 0, Blocks.GRASS_BLOCK.defaultBlockState());
        Path file = write(context, "Soil", builder);
        world.getServer().runCommand("setblock " + at(spot) + " minecraft:air");
        world.getServer().runCommand("gamemode survival @p");
        world.getServer().runCommand("clear @p");
        world.getServer().runCommand("item replace entity @p hotbar.0 with minecraft:iron_shovel");
        world.getServer().runCommand("item replace entity @p hotbar.2 with minecraft:dirt 4");
        world.getServer().runCommand("tp @p " + (spot.getX() + 0.5) + " " + spot.getY() + " " + (spot.getZ() - 1.5) + " 0 37");
        world.getConnection().waitForClientboundPackets();
        config(context, config -> {
            config.easyPlaceEnabled = true;
            config.soilInterchangeable = true;
            config.toolUseGuard = true;
        });
        context.runOnClient(client -> client.player.getInventory().setSelectedSlot(0));
        SchematicPlacement placement = context.computeOnClient(client -> PlacementManager.get().create(file, spot));
        context.waitFor(client -> GhostEngine.get().categoryAt(spot.getX(), spot.getY(), spot.getZ())
                == MismatchType.MISSING, 400);
        context.waitTicks(6);
        boolean dirt = false;
        for (int attempt = 0; attempt < 4 && !dirt; attempt++) {
            context.getInput().pressMouse(InputConstants.MOUSE_BUTTON_RIGHT);
            for (int wait = 0; wait < 15 && !dirt; wait++) {
                context.waitTicks(2);
                dirt = world.getServer().computeOnServer(server -> server.overworld().getBlockState(spot).is(Blocks.DIRT));
            }
        }
        context.waitFor(client -> GhostEngine.get().categoryAt(spot.getX(), spot.getY(), spot.getZ())
                == MismatchType.CORRECT, 200);
        context.runOnClient(client -> client.player.getInventory().setSelectedSlot(0));
        context.waitTicks(4);
        for (int attempt = 0; attempt < 3; attempt++) {
            context.getInput().pressMouse(InputConstants.MOUSE_BUTTON_RIGHT);
            context.waitTicks(6);
        }
        boolean untouched = world.getServer().computeOnServer(server -> server.overworld().getBlockState(spot).is(Blocks.DIRT));
        context.takeScreenshot("12d_dirt_for_grass");
        config(context, config -> config.easyPlaceEnabled = false);
        context.runOnClient(client -> PlacementManager.get().remove(placement));
        world.getServer().runCommand("setblock " + at(spot) + " minecraft:air");
        world.getServer().runCommand("gamemode creative @p");
        world.getServer().runCommand("clear @p");
        world.getConnection().waitForClientboundPackets();
        LitematicaPlusGameTest.require(dirt, "easy place should put dirt where the schematic has grass when there is no grass");
        LitematicaPlusGameTest.require(untouched, "a shovel click must not turn the schematic dirt into a path");
    }

    private static void hiddenSchematicIsQuiet(ClientGameTestContext context, TestSingleplayerContext world) {
        BlockPos spot = BASE.east(18);
        RegionBuilder builder = new RegionBuilder("Quiet", 0, 0, 0, 1, 1, 1);
        builder.set(0, 0, 0, Blocks.STONE.defaultBlockState());
        Path file = write(context, "Quiet", builder);
        world.getServer().runCommand("tp @p " + (spot.getX() + 0.5) + " " + spot.getY() + " " + (spot.getZ() - 2.5) + " 0 20");
        world.getConnection().waitForClientboundPackets();
        config(context, config -> {
            config.materialHelperFollowsRender = true;
            config.renderEnabled = true;
        });
        SchematicPlacement placement = context.computeOnClient(client -> PlacementManager.get().create(file, spot));
        context.waitFor(client -> GhostEngine.get().categoryAt(spot.getX(), spot.getY(), spot.getZ())
                == MismatchType.MISSING, 400);
        boolean announcedWhenShown = context.computeOnClient(client -> PlacementManager.get().announcedSelection() == placement
                && org.lts.litematicaplus.materials.ContainerHelper.schematicShown());
        config(context, config -> config.renderEnabled = false);
        context.waitTicks(4);
        boolean quietWhenHidden = context.computeOnClient(client -> PlacementManager.get().announcedSelection() == null
                && !org.lts.litematicaplus.materials.ContainerHelper.schematicShown());
        context.takeScreenshot("12e_hidden_schematic_quiet");
        config(context, config -> config.renderEnabled = true);
        context.runOnClient(client -> PlacementManager.get().setEnabled(placement, false));
        boolean quietWhenDisabled = context.computeOnClient(client -> PlacementManager.get().announcedSelection() == null
                && PlacementManager.get().visibleSelection() == null);
        config(context, config -> config.materialHelperFollowsRender = false);
        boolean hudQuietAnyway = context.computeOnClient(client -> PlacementManager.get().visibleSelection() == null);
        config(context, config -> config.materialHelperFollowsRender = true);
        context.runOnClient(client -> PlacementManager.get().remove(placement));
        LitematicaPlusGameTest.require(announcedWhenShown, "a shown schematic should be reported");
        LitematicaPlusGameTest.require(quietWhenHidden, "a hidden schematic must not be reported on the HUD or at containers");
        LitematicaPlusGameTest.require(quietWhenDisabled, "a disabled placement must not be reported");
        LitematicaPlusGameTest.require(hudQuietAnyway, "the info panel must never show a disabled placement");
    }

    private static void igniteCampfire(ClientGameTestContext context, TestSingleplayerContext world) {
        BlockPos spot = BASE.east(10);
        RegionBuilder builder = new RegionBuilder("Campfire", 0, 0, 0, 1, 1, 1);
        builder.set(0, 0, 0, Blocks.CAMPFIRE.defaultBlockState().setValue(BlockStateProperties.LIT, true));
        Path file = write(context, "Campfire", builder);
        world.getServer().runCommand("setblock " + at(spot) + " minecraft:campfire[lit=false]");
        world.getServer().runCommand("item replace entity @p hotbar.0 with minecraft:stone");
        world.getServer().runCommand("item replace entity @p hotbar.3 with minecraft:flint_and_steel");
        world.getServer().runCommand("tp @p " + (spot.getX() + 0.5) + " " + spot.getY() + " " + (spot.getZ() - 1.5) + " 0 37");
        world.getConnection().waitForClientboundPackets();
        config(context, config -> {
            config.easyPlaceEnabled = true;
            config.easyPlaceFinishStates = true;
            config.easyPlaceIgnite = true;
        });
        context.runOnClient(client -> client.player.getInventory().setSelectedSlot(0));
        SchematicPlacement placement = context.computeOnClient(client -> PlacementManager.get().create(file, spot));
        context.waitFor(client -> GhostEngine.get().categoryAt(spot.getX(), spot.getY(), spot.getZ())
                == MismatchType.WRONG_STATE, 400);
        context.waitTicks(6);
        boolean lit = false;
        for (int attempt = 0; attempt < 4 && !lit; attempt++) {
            context.getInput().pressMouse(InputConstants.MOUSE_BUTTON_RIGHT);
            for (int wait = 0; wait < 15 && !lit; wait++) {
                context.waitTicks(2);
                lit = world.getServer().computeOnServer(server -> {
                    var state = server.overworld().getBlockState(spot);
                    return state.is(Blocks.CAMPFIRE) && state.getValue(BlockStateProperties.LIT);
                });
            }
        }
        context.takeScreenshot("12c_ignite_campfire");
        config(context, config -> config.easyPlaceEnabled = false);
        context.runOnClient(client -> PlacementManager.get().remove(placement));
        world.getServer().runCommand("setblock " + at(spot) + " minecraft:air");
        world.getServer().runCommand("clear @p");
        world.getConnection().waitForClientboundPackets();
        LitematicaPlusGameTest.require(lit, "easy place should light the campfire with flint and steel");
    }
}
