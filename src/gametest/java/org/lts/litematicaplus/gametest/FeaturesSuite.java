package org.lts.litematicaplus.gametest;

import com.mojang.blaze3d.platform.InputConstants;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BannerPatternLayers;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import org.lts.litematicaplus.LitematicaPlusConfig;
import org.lts.litematicaplus.engine.GhostEngine;
import org.lts.litematicaplus.engine.PlacementRuntime;
import org.lts.litematicaplus.placement.PlacementManager;
import org.lts.litematicaplus.placement.SchematicPlacement;
import org.lts.litematicaplus.render.SpecialArguments;
import org.lts.litematicaplus.render.SpecialBlocks;
import org.lts.litematicaplus.schematic.RegionBuilder;
import org.lts.litematicaplus.schematic.Schematic;
import org.lts.litematicaplus.schematic.SchematicIo;
import org.lts.litematicaplus.schematic.SchematicMetadata;
import org.lts.litematicaplus.selection.SchematicCapture;
import org.lts.litematicaplus.selection.SelectionManager;
import org.lts.litematicaplus.tool.InventoryHighlight;
import org.lts.litematicaplus.tool.PasteController;
import org.lts.litematicaplus.tool.ToolController;
import org.lts.litematicaplus.verify.MismatchType;
import org.lts.litematicaplus.world.LayerRange;
import org.lts.litematicaplus.world.PlacementVolume;

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
        bannerPatternSurvivesCapture(context, world);
        pasteRespectsLayer(context, world);
        fillAndReplace(context, world);
        pasteEntitiesOnce(context, world);
    }

    private static int countBlocks(TestSingleplayerContext world, BlockPos from, BlockPos to,
            net.minecraft.world.level.block.Block block) {
        return world.getServer().computeOnServer(server -> {
            int count = 0;
            for (BlockPos pos : BlockPos.betweenClosed(from, to)) {
                if (server.overworld().getBlockState(pos).is(block)) {
                    count++;
                }
            }
            return count;
        });
    }

    private static void fillAndReplace(ClientGameTestContext context, TestSingleplayerContext world) {
        BlockPos from = BASE.south(24);
        BlockPos to = from.offset(2, 1, 2);
        world.getServer().runCommand("fill " + at(from) + " " + at(to) + " minecraft:air");
        world.getServer().runCommand("gamemode creative @p");
        world.getServer().runCommand("tp @p " + (from.getX() + 1.5) + " " + (from.getY() + 1) + " "
                + (from.getZ() - 3.5) + " 0 20");
        world.getConnection().waitForClientboundPackets();
        context.waitTicks(5);
        context.runOnClient(client -> {
            SelectionManager.get().clear();
            SelectionManager.get().setCorner(true, from);
            SelectionManager.get().setCorner(false, to);
            PasteController.startFill(client, SelectionManager.get().completeBoxes(),
                    Blocks.COBBLESTONE.defaultBlockState(), null);
        });
        context.waitFor(client -> !PasteController.active(), 400);
        world.getConnection().waitForClientboundPackets();
        int filled = countBlocks(world, from, to, Blocks.COBBLESTONE);
        world.getServer().runCommand("setblock " + at(from) + " minecraft:dirt");
        context.runOnClient(client -> PasteController.startFill(client, SelectionManager.get().completeBoxes(),
                Blocks.STONE_BRICKS.defaultBlockState(), Blocks.COBBLESTONE));
        context.waitFor(client -> !PasteController.active(), 400);
        world.getConnection().waitForClientboundPackets();
        int replaced = countBlocks(world, from, to, Blocks.STONE_BRICKS);
        int keptDirt = countBlocks(world, from, from, Blocks.DIRT);
        context.takeScreenshot("12h_fill_replace");
        context.runOnClient(client -> SelectionManager.get().clear());
        world.getServer().runCommand("fill " + at(from) + " " + at(to) + " minecraft:air");
        LitematicaPlusGameTest.require(filled == 18, "fill should place 18 cobblestone, got " + filled);
        LitematicaPlusGameTest.require(replaced == 17 && keptDirt == 1,
                "replace should turn 17 cobblestone into bricks and keep the dirt, got " + replaced + " / " + keptDirt);
    }

    private static void pasteEntitiesOnce(ClientGameTestContext context, TestSingleplayerContext world) {
        BlockPos origin = BASE.south(30);
        RegionBuilder builder = new RegionBuilder("Stand", 0, 0, 0, 2, 1, 2);
        builder.set(0, 0, 0, Blocks.STONE.defaultBlockState());
        CompoundTag stand = new CompoundTag();
        stand.putString("id", "minecraft:armor_stand");
        net.minecraft.nbt.ListTag pos = new net.minecraft.nbt.ListTag();
        pos.add(net.minecraft.nbt.DoubleTag.valueOf(1.5));
        pos.add(net.minecraft.nbt.DoubleTag.valueOf(0.0));
        pos.add(net.minecraft.nbt.DoubleTag.valueOf(1.5));
        stand.put("Pos", pos);
        builder.entity(stand);
        Path file = write(context, "Stand", builder);
        world.getServer().runCommand("fill " + at(origin) + " " + at(origin.offset(1, 1, 1)) + " minecraft:air");
        world.getServer().runCommand("kill @e[type=minecraft:armor_stand]");
        world.getServer().runCommand("tp @p " + (origin.getX() + 1.0) + " " + (origin.getY() + 1) + " "
                + (origin.getZ() - 3.5) + " 0 20");
        world.getConnection().waitForClientboundPackets();
        SchematicPlacement placement = context.computeOnClient(client -> PlacementManager.get().create(file, origin));
        context.waitFor(client -> {
            PlacementRuntime runtime = GhostEngine.get().runtime(placement.id());
            return runtime != null && runtime.volume() != null;
        }, 400);
        for (int attempt = 0; attempt < 2; attempt++) {
            context.runOnClient(client -> PasteController.startPaste(client, GhostEngine.get().runtime(placement.id()), true));
            context.waitFor(client -> !PasteController.active(), 400);
            world.getConnection().waitForClientboundPackets();
        }
        int stands = world.getServer().computeOnServer(server -> server.overworld().getEntities(
                (net.minecraft.world.entity.Entity) null, new net.minecraft.world.phys.AABB(origin).inflate(3.0),
                entity -> "minecraft:armor_stand".equals(net.minecraft.world.entity.EntityType.getKey(entity.getType())
                        .toString())).size());
        context.takeScreenshot("12i_paste_entities");
        context.runOnClient(client -> PlacementManager.get().remove(placement));
        world.getServer().runCommand("kill @e[type=minecraft:armor_stand]");
        world.getServer().runCommand("fill " + at(origin) + " " + at(origin.offset(1, 1, 1)) + " minecraft:air");
        LitematicaPlusGameTest.require(stands == 1, "two pastes should leave exactly one armor stand, got " + stands);
    }

    private static void pasteRespectsLayer(ClientGameTestContext context, TestSingleplayerContext world) {
        BlockPos origin = BASE.south(18);
        RegionBuilder builder = new RegionBuilder("LayerPaste", 0, 0, 0, 3, 3, 3);
        builder.fill(Blocks.STONE.defaultBlockState());
        Path file = write(context, "LayerPaste", builder);
        world.getServer().runCommand("fill " + at(origin) + " " + at(origin.offset(2, 2, 2)) + " minecraft:air");
        world.getServer().runCommand("gamemode creative @p");
        world.getServer().runCommand("tp @p " + (origin.getX() + 1.5) + " " + (origin.getY() + 1) + " "
                + (origin.getZ() - 3.5) + " 0 20");
        world.getConnection().waitForClientboundPackets();
        SchematicPlacement placement = context.computeOnClient(client -> PlacementManager.get().create(file, origin));
        context.waitFor(client -> {
            PlacementRuntime runtime = GhostEngine.get().runtime(placement.id());
            return runtime != null && runtime.volume() != null;
        }, 400);
        context.runOnClient(client -> {
            ToolController.get().applyLayer(new LayerRange(LayerRange.Mode.SINGLE, Direction.Axis.Y,
                    origin.getY() + 1, 0, 0));
            GhostEngine.get().setClip(new LayerRange.Clip(true, true, origin.getX(), origin.getX() + 1, false, 0, 0));
            PasteController.startPaste(client, GhostEngine.get().runtime(placement.id()), true);
        });
        context.waitFor(client -> !PasteController.active(), 400);
        world.getConnection().waitForClientboundPackets();
        int[] counts = world.getServer().computeOnServer(server -> {
            int inside = 0;
            int outside = 0;
            for (BlockPos pos : BlockPos.betweenClosed(origin, origin.offset(2, 2, 2))) {
                if (!server.overworld().getBlockState(pos).is(Blocks.STONE)) {
                    continue;
                }
                if (pos.getY() == origin.getY() + 1 && pos.getX() <= origin.getX() + 1) {
                    inside++;
                } else {
                    outside++;
                }
            }
            return new int[]{inside, outside};
        });
        context.takeScreenshot("12g_paste_layer");
        context.runOnClient(client -> {
            ToolController.get().applyLayer(LayerRange.ALL);
            GhostEngine.get().setClip(LayerRange.Clip.NONE);
            PlacementManager.get().remove(placement);
        });
        world.getServer().runCommand("fill " + at(origin) + " " + at(origin.offset(2, 2, 2)) + " minecraft:air");
        LitematicaPlusGameTest.require(counts[0] == 6 && counts[1] == 0,
                "paste should fill only the 2x3 cells inside the layer and X bounds, got inside " + counts[0]
                        + " outside " + counts[1]);
    }

    private static void bannerPatternSurvivesCapture(ClientGameTestContext context, TestSingleplayerContext world) {
        BlockPos banner = BASE.south(12);
        BlockPos copy = banner.east(3);
        world.getServer().runCommand("setblock " + at(banner) + " minecraft:white_banner[rotation=8]"
                + "{patterns:[{pattern:\"minecraft:stripe_top\",color:\"red\"},{pattern:\"minecraft:cross\",color:\"blue\"}]}");
        world.getServer().runCommand("tp @p " + (banner.getX() + 1.5) + " " + banner.getY() + " " + (banner.getZ() + 4.5)
                + " 180 10");
        world.getConnection().waitForClientboundPackets();
        context.waitTicks(10);
        SchematicCapture.Result result = context.computeOnClient(client -> SchematicCapture.capture(client,
                List.of(new SchematicCapture.Box("Banner", banner.getX(), banner.getY(), banner.getZ(), banner.getX(),
                        banner.getY(), banner.getZ())), "Banner", "", "").join());
        CompoundTag captured = result.schematic().regions().get("Banner").blockEntities().values().stream().findFirst()
                .orElse(null);
        LitematicaPlusGameTest.require(captured != null && captured.getListOrEmpty("patterns").size() == 2,
                "captured banner lost its patterns: " + captured);
        Path file = context.computeOnClient(client -> {
            Path target = PlacementManager.schematicsDirectory().resolve("gametest/banner.litematic");
            try {
                SchematicIo.write(target, result.schematic());
            } catch (IOException exception) {
                throw new UncheckedIOException(exception);
            }
            return target;
        });
        SchematicPlacement placement = context.computeOnClient(client -> PlacementManager.get().create(file, copy));
        context.waitFor(client -> {
            PlacementRuntime runtime = GhostEngine.get().runtime(placement.id());
            return runtime != null && runtime.volume() != null;
        }, 400);
        int layers = context.computeOnClient(client -> {
            PlacementVolume volume = GhostEngine.get().runtime(placement.id()).volume();
            BlockState state = GhostEngine.get().schematicStateAt(copy.getX(), copy.getY(), copy.getZ());
            SpecialBlocks.Entry entry = state == null ? null : SpecialBlocks.get(state);
            Object argument = entry == null ? null
                    : SpecialArguments.get(volume, copy.getX(), copy.getY(), copy.getZ(), state, entry.renderer());
            return argument instanceof BannerPatternLayers patterns ? patterns.layers().size() : -1;
        });
        LitematicaPlusGameTest.require(layers == 2, "banner ghost should carry 2 pattern layers, got " + layers);
        context.waitTicks(10);
        context.takeScreenshot("12f_banner_pattern_ghost");
        context.runOnClient(client -> PlacementManager.get().remove(placement));
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
