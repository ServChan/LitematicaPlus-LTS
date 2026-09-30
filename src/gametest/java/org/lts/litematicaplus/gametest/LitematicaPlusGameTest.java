package org.lts.litematicaplus.gametest;

import com.mojang.blaze3d.platform.InputConstants;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.fabricmc.fabric.api.client.gametest.v1.world.TestWorldSave;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.worldselection.WorldCreationUiState;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import org.lts.litematicaplus.LitematicaPlusConfig;
import org.lts.litematicaplus.engine.GhostEngine;
import org.lts.litematicaplus.engine.PlacementRuntime;
import org.lts.litematicaplus.gui.HubScreen;
import org.lts.litematicaplus.placement.PlacementManager;
import org.lts.litematicaplus.placement.SchematicPlacement;
import org.lts.litematicaplus.render.GhostRenderer;
import org.lts.litematicaplus.schematic.RegionBuilder;
import org.lts.litematicaplus.schematic.Schematic;
import org.lts.litematicaplus.schematic.SchematicIo;
import org.lts.litematicaplus.schematic.SchematicMetadata;
import org.lts.litematicaplus.selection.SchematicCapture;
import org.lts.litematicaplus.selection.SelectionManager;
import org.lts.litematicaplus.tool.PasteController;
import org.lts.litematicaplus.tool.ToolController;
import org.lts.litematicaplus.tool.ToolMode;
import org.lts.litematicaplus.verify.MismatchType;
import org.lts.litematicaplus.world.LayerRange;

public final class LitematicaPlusGameTest implements FabricClientGameTest {
    private static final BlockPos ORIGIN = new BlockPos(4, -60, 4);

    @Override
    public void runTest(ClientGameTestContext context) {
        TestWorldSave save;
        int expectedPlacements;
        try (TestSingleplayerContext world = context.worldBuilder()
                .adjustSettings(settings -> settings.setGameMode(WorldCreationUiState.SelectedGameMode.CREATIVE))
                .create()) {
            world.getConnection().waitForChunksRender();
            world.getServer().runCommand("tp @p -2.5 -59 -2.5 -45 12");
            context.waitTicks(10);
            Schematic house = house();
            long total = house.metadata().totalBlocks;
            Path file = context.computeOnClient(client -> {
                Path target = PlacementManager.schematicsDirectory().resolve("gametest/house.litematic");
                SchematicIo.write(target, house);
                return target;
            });
            SchematicPlacement placement = context.computeOnClient(client -> PlacementManager.get().create(file, ORIGIN));
            settle(context, placement);
            PlacementRuntime runtime = context.computeOnClient(client -> GhostEngine.get().runtime(placement.id()));
            check(runtime.volume().totalBlocks() == total, "volume block count " + runtime.volume().totalBlocks() + " != " + total);
            check(runtime.count(MismatchType.MISSING) == total, "all blocks should be missing, got " + runtime.count(MismatchType.MISSING));
            check(runtime.errors() == total, "errors " + runtime.errors());
            context.waitTicks(5);
            context.takeScreenshot("01_ghost");
            checkHealthy();
            world.getServer().runCommand("tp @p 7.5 -59 9.5 150 15");
            world.getConnection().waitForClientboundPackets();
            context.waitTicks(10);
            context.takeScreenshot("01b_interior");
            world.getServer().runCommand("tp @p -2.5 -59 -2.5 -45 12");
            world.getConnection().waitForClientboundPackets();
            context.waitTicks(10);

            world.getServer().runCommand(set(0, 0, 0, "minecraft:oak_planks"));
            world.getServer().runCommand(set(1, 0, 0, "minecraft:dirt"));
            world.getServer().runCommand(set(5, 1, 5, "minecraft:oak_stairs[facing=south]"));
            world.getServer().runCommand(set(2, 1, 2, "minecraft:gold_block"));
            context.waitTicks(10);
            settle(context, placement);
            check(runtime.count(MismatchType.CORRECT) == 1, "correct " + runtime.count(MismatchType.CORRECT));
            check(runtime.count(MismatchType.WRONG_BLOCK) == 1, "wrong block " + runtime.count(MismatchType.WRONG_BLOCK));
            check(runtime.count(MismatchType.WRONG_STATE) == 1, "wrong state " + runtime.count(MismatchType.WRONG_STATE));
            check(runtime.count(MismatchType.EXTRA) == 1, "extra " + runtime.count(MismatchType.EXTRA));
            check(runtime.count(MismatchType.MISSING) == total - 3, "missing " + runtime.count(MismatchType.MISSING));
            context.takeScreenshot("02_overlay");

            context.runOnClient(client -> ToolController.get().applyLayer(
                    new LayerRange(LayerRange.Mode.SINGLE, Direction.Axis.Y, ORIGIN.getY() + 1, 0, 0)));
            settle(context, placement);
            context.waitTicks(5);
            context.takeScreenshot("03_single_layer");
            context.runOnClient(client -> ToolController.get().applyLayer(LayerRange.ALL));
            settle(context, placement);

            for (HubScreen.Tab tab : HubScreen.Tab.values()) {
                context.setScreen(() -> new HubScreen(null, tab));
                context.waitTicks(tab == HubScreen.Tab.SCHEMATICS ? 25 : 12);
                context.takeScreenshot("gui_" + tab.name().toLowerCase(java.util.Locale.ROOT));
            }
            context.getInput().resizeWindow(1920, 1080);
            context.waitTicks(10);
            for (HubScreen.Tab tab : HubScreen.Tab.values()) {
                context.setScreen(() -> new HubScreen(null, tab));
                context.waitTicks(tab == HubScreen.Tab.SCHEMATICS ? 25 : 12);
                context.takeScreenshot("wide_" + tab.name().toLowerCase(java.util.Locale.ROOT));
            }
            context.setScreen(() -> null);
            context.getInput().resizeWindow(854, 480);
            context.waitTicks(10);

            world.getServer().runCommand("item replace entity @p weapon.mainhand with minecraft:stick");
            world.getConnection().waitForClientboundPackets();
            context.waitTicks(5);
            context.runOnClient(client -> {
                ToolController.get().setMode(ToolMode.AREA_SELECTION);
                check(ToolController.get().holdingTool(client), "stick should count as the tool");
            });
            context.getInput().pressMouse(InputConstants.MOUSE_BUTTON_LEFT);
            context.waitTicks(6);
            context.runOnClient(client -> check(SelectionManager.get().selectedBox() != null
                    && SelectionManager.get().selectedBox().corner1() != null, "left click with the tool should set corner 1"));
            world.getServer().runCommand("tp @p -2.5 -59 -2.5 -45 30");
            world.getConnection().waitForClientboundPackets();
            context.waitTicks(8);
            context.getInput().pressMouse(InputConstants.MOUSE_BUTTON_RIGHT);
            context.waitTicks(6);
            context.runOnClient(client -> check(SelectionManager.get().selectedBox().corner2() != null,
                    "right click with the tool should set corner 2"));
            Block ground = world.getServer().computeOnServer(server -> server.overworld().getBlockState(
                    SelectionManager.get().selectedBox().corner1()).getBlock());
            check(ground != Blocks.AIR, "the tool must not break the block it clicks, found " + ground);
            context.getInput().holdControl();
            context.getInput().scroll(1.0);
            context.waitTicks(2);
            context.getInput().releaseControl();
            context.waitTicks(2);
            context.runOnClient(client -> check(ToolController.get().mode() == ToolMode.PLACEMENT,
                    "ctrl+scroll should switch the tool mode, got " + ToolController.get().mode()));
            int slotBefore = context.computeOnClient(client -> client.player.getInventory().getSelectedSlot());
            context.getInput().scroll(1.0);
            context.waitTicks(2);
            int slotAfter = context.computeOnClient(client -> client.player.getInventory().getSelectedSlot());
            check(slotBefore != slotAfter, "plain scrolling must still change the hotbar slot");
            context.runOnClient(client -> {
                client.player.getInventory().setSelectedSlot(slotBefore);
                ToolController.get().setMode(ToolMode.AREA_SELECTION);
            });
            world.getServer().runCommand("tp @p -2.5 -59 -2.5 -45 12");
            world.getConnection().waitForClientboundPackets();
            context.waitTicks(8);
            context.takeScreenshot("04_tool_hud");
            world.getServer().runCommand("item replace entity @p weapon.mainhand with minecraft:air");
            world.getConnection().waitForClientboundPackets();

            context.runOnClient(client -> PasteController.startPaste(client, GhostEngine.get().runtime(placement.id())));
            context.waitFor(client -> !PasteController.active(), 600);
            world.getConnection().waitForClientboundPackets();
            context.waitTicks(20);
            settle(context, placement);
            check(runtime.errors() == 0, "after paste errors " + runtime.errors() + " missing " + runtime.count(MismatchType.MISSING)
                    + " wrongBlock " + runtime.count(MismatchType.WRONG_BLOCK) + " wrongState "
                    + runtime.count(MismatchType.WRONG_STATE) + " extra " + runtime.count(MismatchType.EXTRA));
            check(runtime.count(MismatchType.CORRECT) == total, "after paste correct " + runtime.count(MismatchType.CORRECT));
            int diamonds = world.getServer().computeOnServer(server -> {
                ServerLevel level = server.overworld();
                BlockEntity chest = level.getBlockEntity(ORIGIN.offset(1, 1, 1));
                return chest instanceof Container container ? container.getItem(0).getCount() : -1;
            });
            check(diamonds == 3, "pasted chest should hold 3 diamonds, got " + diamonds);
            context.takeScreenshot("05_pasted");

            AtomicReference<SchematicCapture.Result> captured = new AtomicReference<>();
            context.runOnClient(client -> {
                SelectionManager selection = SelectionManager.get();
                selection.clear();
                selection.setCorner(true, ORIGIN);
                selection.setCorner(false, ORIGIN.offset(6, 4, 6));
                List<SchematicCapture.Box> boxes = SchematicCapture.freeze(selection.completeBoxes());
                SchematicCapture.capture(client, boxes, "captured", "gametest", "").thenAccept(captured::set);
            });
            context.waitFor(client -> captured.get() != null, 200);
            Schematic copy = captured.get().schematic();
            check(copy.metadata().totalBlocks == total, "captured blocks " + copy.metadata().totalBlocks + " != " + total);
            check(captured.get().origin().equals(ORIGIN), "captured origin " + captured.get().origin());
            CompoundTag copiedChest = copy.regions().values().iterator().next().blockEntities()
                    .get(org.lts.litematicaplus.schematic.SchematicRegion.localKey(1, 1, 1));
            check(copiedChest != null && copiedChest.toString().contains("diamond"), "captured chest data " + copiedChest);
            Schematic reread = context.computeOnClient(client -> {
                Path target = PlacementManager.schematicsDirectory().resolve("gametest/captured.litematic");
                SchematicIo.write(target, copy);
                return SchematicIo.read(target);
            });
            check(reread.metadata().totalBlocks == total, "re-read blocks " + reread.metadata().totalBlocks);

            world.getServer().runCommand(set(0, 1, 3, "minecraft:air"));
            world.getServer().runCommand("tp @p 1.5 -60 7.5 -90 0");
            world.getConnection().waitForClientboundPackets();
            context.waitTicks(10);
            settle(context, placement);
            check(runtime.count(MismatchType.MISSING) == 1, "one block should be missing before easy place");
            context.runOnClient(client -> {
                LitematicaPlusConfig config = LitematicaPlusConfig.currentCopy();
                config.easyPlaceEnabled = true;
                LitematicaPlusConfig.save(config);
            });
            context.waitTicks(3);
            context.getInput().pressMouse(InputConstants.MOUSE_BUTTON_MIDDLE);
            context.waitTicks(4);
            Item picked = context.computeOnClient(client -> client.player.getMainHandItem().getItem());
            check(picked == Items.STONE_BRICKS, "pick block on a ghost should select stone bricks, got " + picked);
            context.runOnClient(client -> client.player.getInventory().setSelectedSlot(
                    (client.player.getInventory().getSelectedSlot() + 1) % 9));
            context.waitTicks(3);
            context.getInput().pressMouse(InputConstants.MOUSE_BUTTON_RIGHT);
            world.getConnection().waitForServerboundPackets();
            context.waitTicks(10);
            Block placed = world.getServer().computeOnServer(server -> server.overworld().getBlockState(ORIGIN.offset(0, 1, 3)).getBlock());
            check(placed == Blocks.STONE_BRICKS, "easy place should put stone bricks, found " + placed);
            settle(context, placement);
            check(runtime.errors() == 0, "errors after easy place " + runtime.errors());

            context.runOnClient(client -> {
                LitematicaPlusConfig config = LitematicaPlusConfig.currentCopy();
                config.easyPlaceEnabled = false;
                config.placementRestriction = true;
                LitematicaPlusConfig.save(config);
            });
            world.getServer().runCommand(set(0, 1, 3, "minecraft:air"));
            world.getServer().runCommand("item replace entity @p weapon.mainhand with minecraft:dirt");
            world.getConnection().waitForClientboundPackets();
            context.waitTicks(10);
            settle(context, placement);
            world.getServer().runCommand("tp @p 3.3 -60 7.5 -90 29.4");
            world.getConnection().waitForClientboundPackets();
            context.waitTicks(10);
            context.getInput().pressMouse(InputConstants.MOUSE_BUTTON_RIGHT);
            world.getConnection().waitForServerboundPackets();
            context.waitTicks(10);
            Block guarded = world.getServer().computeOnServer(server -> server.overworld().getBlockState(ORIGIN.offset(0, 1, 3)).getBlock());
            check(guarded == Blocks.AIR, "placement guard should stop dirt where stone bricks belong, found " + guarded);
            context.runOnClient(client -> {
                LitematicaPlusConfig config = LitematicaPlusConfig.currentCopy();
                config.placementRestriction = false;
                LitematicaPlusConfig.save(config);
            });
            world.getServer().runCommand(set(0, 1, 3, "minecraft:stone_bricks"));
            world.getServer().runCommand("item replace entity @p weapon.mainhand with minecraft:air");
            world.getConnection().waitForClientboundPackets();
            context.waitTicks(10);
            settle(context, placement);

            context.runOnClient(client -> {
                SelectionManager selection = SelectionManager.get();
                selection.clear();
                selection.setCorner(true, ORIGIN.offset(20, 0, 0));
                selection.setCorner(false, ORIGIN.offset(24, 2, 4));
            });
            world.getServer().runCommand("fill 24 -60 4 28 -58 8 minecraft:glass");
            world.getConnection().waitForClientboundPackets();
            context.waitTicks(10);
            context.runOnClient(client -> PasteController.startDelete(client, SelectionManager.get().completeBoxes()));
            context.waitFor(client -> !PasteController.active(), 400);
            context.waitTicks(10);
            Block deleted = world.getServer().computeOnServer(server -> server.overworld().getBlockState(new BlockPos(26, -59, 6)).getBlock());
            check(deleted == Blocks.AIR, "delete tool should clear the selection, found " + deleted);
            context.runOnClient(client -> SelectionManager.get().clear());

            context.runOnClient(client -> PlacementManager.get().rotate(placement, Rotation.CLOCKWISE_90));
            context.waitTicks(5);
            settle(context, placement);
            PlacementRuntime rotated = context.computeOnClient(client -> GhostEngine.get().runtime(placement.id()));
            check(rotated.volume().minX() == ORIGIN.getX() - 6, "rotated min x " + rotated.volume().minX());
            world.getServer().runCommand("tp @p 8.5 -56 -6.5 0 25");
            world.getConnection().waitForClientboundPackets();
            context.waitTicks(15);
            context.takeScreenshot("06_rotated");
            checkHealthy();
            stress(context, world, placement);
            save = world.getWorldSave();
            expectedPlacements = context.computeOnClient(client -> PlacementManager.get().placements().size());
        } catch (IOException exception) {
            throw new AssertionError(exception);
        }
        context.waitTicks(10);
        int afterLeaving = context.computeOnClient(client -> PlacementManager.get().placements().size());
        check(afterLeaving == 0, "placements should be unloaded outside a world, got " + afterLeaving);
        check(context.computeOnClient(client -> GhostEngine.get().runtimes().isEmpty()), "engine should be empty outside a world");
        try (TestSingleplayerContext world = save.open()) {
            world.getConnection().waitForChunksRender();
            context.waitFor(client -> PlacementManager.get().placements().size() == expectedPlacements, 200);
            SchematicPlacement restored = context.computeOnClient(client -> PlacementManager.get().selected());
            check(restored != null, "the selected placement should be restored after rejoining");
            context.waitFor(client -> {
                PlacementRuntime runtime = GhostEngine.get().runtime(restored.id());
                return runtime != null && runtime.volume() != null;
            }, 1200);
            context.waitTicks(40);
            context.takeScreenshot("09_rejoined");
            checkHealthy();
        }
    }

    private static void stress(ClientGameTestContext context, TestSingleplayerContext world, SchematicPlacement previous) {
        String sample = System.getProperty("litematicaplus.perfSample", "");
        if (sample.isBlank()) {
            return;
        }
        Path source = Path.of(sample);
        context.runOnClient(client -> PlacementManager.get().remove(previous));
        world.getServer().runCommand("tp @p 200.5 -20 200.5 -45 35");
        world.getConnection().waitForClientboundPackets();
        world.getConnection().waitForChunksRender();
        int baseline = context.computeOnClient(Minecraft::getFps);
        long started = System.nanoTime();
        SchematicPlacement big = context.computeOnClient(client -> PlacementManager.get().create(source, new BlockPos(200, -60, 200)));
        context.waitFor(client -> {
            PlacementRuntime runtime = GhostEngine.get().runtime(big.id());
            return runtime != null && runtime.volume() != null && !runtime.building();
        }, 2400);
        long volumeMillis = (System.nanoTime() - started) / 1_000_000L;
        PlacementRuntime runtime = context.computeOnClient(client -> GhostEngine.get().runtime(big.id()));
        context.waitFor(client -> runtime.pendingSections() == 0 && GhostEngine.get().inFlight() == 0
                && runtime.verifiedSections() == runtime.slotCount(), 6000);
        long meshMillis = (System.nanoTime() - started) / 1_000_000L;
        context.waitTicks(60);
        int fps = context.computeOnClient(Minecraft::getFps);
        context.takeScreenshot("07_stress");
        world.getServer().runCommand("tp @p 330.5 -30 330.5 135 20");
        world.getConnection().waitForClientboundPackets();
        context.waitTicks(80);
        int fpsInside = context.computeOnClient(Minecraft::getFps);
        context.takeScreenshot("08_stress_inside");
        System.out.println("STRESS blocks=" + runtime.volume().totalBlocks() + " sections=" + runtime.slotCount()
                + " volumeMs=" + volumeMillis + " allSectionsMs=" + meshMillis + " fpsBefore=" + baseline + " fps=" + fps
                + " fpsInside=" + fpsInside + " missing=" + runtime.count(MismatchType.MISSING)
                + " unknown=" + runtime.count(MismatchType.UNKNOWN));
        checkHealthy();
    }

    private static void settle(ClientGameTestContext context, SchematicPlacement placement) {
        context.waitFor(client -> {
            PlacementRuntime runtime = GhostEngine.get().runtime(placement.id());
            return runtime != null && runtime.volume() != null && !runtime.building() && runtime.pendingSections() == 0
                    && GhostEngine.get().inFlight() == 0 && runtime.verifiedSections() == runtime.slotCount();
        }, 600);
        context.waitTicks(2);
    }

    private static void checkHealthy() {
        check(!GhostRenderer.failed(), "schematic renderer reported a failure");
    }

    private static void check(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }

    private static String set(int x, int y, int z, String state) {
        return "setblock " + (ORIGIN.getX() + x) + " " + (ORIGIN.getY() + y) + " " + (ORIGIN.getZ() + z) + " " + state;
    }

    private static Schematic house() {
        RegionBuilder builder = new RegionBuilder("House", 0, 0, 0, 7, 5, 7);
        BlockState planks = Blocks.OAK_PLANKS.defaultBlockState();
        BlockState bricks = Blocks.STONE_BRICKS.defaultBlockState();
        BlockState slab = Blocks.OAK_SLAB.defaultBlockState();
        long count = 0L;
        for (int x = 0; x < 7; x++) {
            for (int z = 0; z < 7; z++) {
                builder.set(x, 0, z, planks);
                builder.set(x, 4, z, slab);
                count += 2;
                boolean wall = x == 0 || x == 6 || z == 0 || z == 6;
                if (!wall) {
                    continue;
                }
                for (int y = 1; y <= 3; y++) {
                    if (x == 3 && z == 6 && y <= 2) {
                        continue;
                    }
                    builder.set(x, y, z, x == 3 && z == 0 && y == 2 ? Blocks.GLASS.defaultBlockState() : bricks);
                    count++;
                }
            }
        }
        builder.set(1, 1, 1, Blocks.CHEST.defaultBlockState().setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.SOUTH));
        CompoundTag chest = new CompoundTag();
        chest.putString("id", "minecraft:chest");
        ListTag items = new ListTag();
        CompoundTag stack = new CompoundTag();
        stack.putByte("Slot", (byte) 0);
        stack.putString("id", "minecraft:diamond");
        stack.putInt("count", 3);
        items.add(stack);
        chest.put("Items", items);
        builder.blockEntity(1, 1, 1, chest);
        builder.set(5, 1, 5, Blocks.OAK_STAIRS.defaultBlockState().setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.NORTH));
        Block bed = BuiltInRegistries.BLOCK.getValue(Identifier.parse("minecraft:red_bed"));
        BlockState foot = bed.defaultBlockState().setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.SOUTH);
        builder.set(5, 1, 1, foot);
        builder.set(5, 1, 2, foot.setValue(BlockStateProperties.BED_PART, BedPart.HEAD));
        builder.set(1, 2, 3, Blocks.WALL_TORCH.defaultBlockState().setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.EAST));
        builder.set(3, 1, 3, Blocks.CRAFTING_TABLE.defaultBlockState());
        count += 6;
        SchematicMetadata metadata = new SchematicMetadata();
        metadata.name = "Test house";
        metadata.author = "gametest";
        metadata.description = "A small house used by the automated client test.";
        metadata.timeCreated = System.currentTimeMillis();
        metadata.totalBlocks = count;
        return new Schematic(metadata, Map.of("House", builder.build()));
    }
}
