package org.lts.litematicaplus.gametest;

import com.mojang.blaze3d.platform.InputConstants;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.CrafterBlockEntity;
import net.minecraft.world.level.block.entity.SignBlockEntity;
import net.minecraft.world.level.block.entity.SignTextSlot;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;
import org.lts.litematicaplus.LitematicaPlusConfig;
import org.lts.litematicaplus.engine.GhostEngine;
import org.lts.litematicaplus.engine.PlacementRuntime;
import org.lts.litematicaplus.placement.PlacementManager;
import org.lts.litematicaplus.placement.SchematicPlacement;
import org.lts.litematicaplus.schematic.BlockStateNbt;
import org.lts.litematicaplus.schematic.RegionBuilder;
import org.lts.litematicaplus.schematic.Schematic;
import org.lts.litematicaplus.schematic.SchematicIo;
import org.lts.litematicaplus.schematic.SchematicMetadata;

final class EasyPlaceSuite {
    private static final BlockPos BASE = new BlockPos(60, -60, 60);
    private static final int SPACING = 3;
    private static final String[] TARGETS = {
        "minecraft:oak_stairs[facing=west,half=top]",
        "minecraft:piston[facing=up]",
        "minecraft:piston[facing=east]",
        "minecraft:observer[facing=south]",
        "minecraft:repeater[facing=north,delay=3]",
        "minecraft:comparator[facing=east,mode=subtract]",
        "minecraft:oak_trapdoor[facing=south,half=top,open=true]",
        "minecraft:lever[face=floor,facing=east,powered=true]",
        "minecraft:note_block[note=5]",
        "minecraft:candle[candles=3]",
        "minecraft:oak_slab[type=double]",
        "minecraft:chest[facing=west]",
        "minecraft:oak_sign[rotation=6]",
        "minecraft:oak_door[facing=north,hinge=right,half=lower]",
        "minecraft:oak_log[axis=x]",
        "minecraft:hopper[facing=west]",
        "minecraft:potted_poppy",
        "minecraft:oak_fence_gate[facing=east,open=true]",
        "minecraft:crafter[orientation=up_north]",
        "minecraft:dispenser[facing=down]",
        "minecraft:barrel[facing=north]",
        "minecraft:quartz_pillar[axis=z]",
        "minecraft:furnace[facing=east]",
        "minecraft:oak_stairs[facing=north,half=bottom]",
        "minecraft:wall_torch[facing=north]",
        "minecraft:torch",
        "minecraft:oak_wall_sign[facing=north]",
        "minecraft:ladder[facing=north]",
        "minecraft:stone_button[face=wall,facing=north]",
        "minecraft:chest[facing=north,type=left]",
        "minecraft:chest[facing=north,type=right]",
        "minecraft:chest[facing=south]",
        "minecraft:chest[facing=south]"
    };
    private static final int FIRST_SUPPORTED = 24;
    private static final int LAST_SUPPORTED = 28;
    private static final int CHEST_LEFT = 29;
    private static final int SINGLE_PAIR = 31;

    private static int offset(int index) {
        if (index == CHEST_LEFT + 1) {
            return CHEST_LEFT * SPACING + 1;
        }
        if (index == SINGLE_PAIR + 1) {
            return SINGLE_PAIR * SPACING + 1;
        }
        return index * SPACING;
    }

    private EasyPlaceSuite() {
    }

    static void run(ClientGameTestContext context, TestSingleplayerContext world) {
        RegionBuilder builder = new RegionBuilder("Gallery", 0, 0, 0, TARGETS.length * SPACING + 2, 2, 1);
        List<BlockState> states = new ArrayList<>();
        for (int i = 0; i < TARGETS.length; i++) {
            BlockState state = BlockStateNbt.parse(TARGETS[i]);
            LitematicaPlusGameTest.require(!state.isAir(), "unknown test block " + TARGETS[i]);
            states.add(state);
            builder.set(offset(i), 0, 0, state);
            if (TARGETS[i].contains("oak_door")) {
                builder.set(offset(i), 1, 0, BlockStateNbt.parse("minecraft:oak_door[facing=north,hinge=right,half=upper]"));
            }
            if (TARGETS[i].contains("oak_sign")) {
                CompoundTag sign = new CompoundTag();
                sign.putString("id", "minecraft:sign");
                CompoundTag front = new CompoundTag();
                ListTag messages = new ListTag();
                messages.add(StringTag.valueOf("Hello"));
                messages.add(StringTag.valueOf(""));
                messages.add(StringTag.valueOf(""));
                messages.add(StringTag.valueOf(""));
                front.put("messages", messages);
                sign.put("front_text", front);
                builder.blockEntity(offset(i), 0, 0, sign);
            }
            if (TARGETS[i].contains("crafter")) {
                CompoundTag crafter = new CompoundTag();
                crafter.putString("id", "minecraft:crafter");
                crafter.putIntArray("disabled_slots", new int[]{0, 4});
                builder.blockEntity(offset(i), 0, 0, crafter);
            }
        }
        SchematicMetadata metadata = new SchematicMetadata();
        metadata.name = "Gallery";
        Schematic schematic = new Schematic(metadata, Map.of("Gallery", builder.build()));
        Path file = context.computeOnClient(client -> {
            Path target = PlacementManager.schematicsDirectory().resolve("gametest/gallery.litematic");
            try {
                SchematicIo.write(target, schematic);
            } catch (IOException exception) {
                throw new UncheckedIOException(exception);
            }
            return target;
        });
        world.getServer().runCommand("tp @p " + (BASE.getX() + 0.5) + " -60 " + (BASE.getZ() - 1.5) + " 0 40");
        world.getConnection().waitForClientboundPackets();
        world.getConnection().waitForChunksRender();
        world.getServer().runCommand("item replace entity @p weapon.mainhand with minecraft:air");
        SchematicPlacement placement = context.computeOnClient(client -> PlacementManager.get().create(file, BASE));
        context.waitFor(client -> {
            PlacementRuntime runtime = GhostEngine.get().runtime(placement.id());
            return runtime != null && runtime.volume() != null && runtime.pendingSections() == 0
                    && GhostEngine.get().inFlight() == 0 && runtime.verifiedSections() == runtime.slotCount();
        }, 400);
        context.runOnClient(client -> {
            LitematicaPlusConfig config = LitematicaPlusConfig.currentCopy();
            config.easyPlaceEnabled = true;
            LitematicaPlusConfig.save(config);
        });
        for (int i = FIRST_SUPPORTED; i <= LAST_SUPPORTED; i++) {
            BlockPos support = BASE.offset(offset(i), 0, 1);
            world.getServer().runCommand("setblock " + support.getX() + " " + support.getY() + " " + support.getZ()
                    + " minecraft:stone");
        }
        List<String> failures = new ArrayList<>();
        for (int i = 0; i < TARGETS.length; i++) {
            BlockPos pos = BASE.offset(offset(i), 0, 0);
            BlockState expected = i == CHEST_LEFT ? BlockStateNbt.parse("minecraft:chest[facing=north]") : states.get(i);
            world.getServer().runCommand("tp @p " + (pos.getX() + 0.5) + " -60 " + (pos.getZ() - 1.4) + " 0 38");
            world.getConnection().waitForClientboundPackets();
            context.waitTicks(6);
            boolean done = false;
            for (int attempt = 0; attempt < 4 && !done; attempt++) {
                context.getInput().pressMouse(InputConstants.MOUSE_BUTTON_RIGHT);
                for (int wait = 0; wait < 30 && !done; wait++) {
                    context.waitTicks(2);
                    BlockState actual = world.getServer().computeOnServer(server -> server.overworld().getBlockState(pos));
                    done = matches(expected, actual);
                }
            }
            context.waitTicks(8);
            BlockState actual = world.getServer().computeOnServer(server -> server.overworld().getBlockState(pos));
            if (!matches(expected, actual)) {
                failures.add(TARGETS[i] + " -> " + BlockStateNbt.format(actual));
            }
        }
        for (int i = 0; i < TARGETS.length; i++) {
            BlockPos pos = BASE.offset(offset(i), 0, 0);
            BlockState actual = world.getServer().computeOnServer(server -> server.overworld().getBlockState(pos));
            if (!matches(states.get(i), actual)) {
                failures.add("final " + TARGETS[i] + " -> " + BlockStateNbt.format(actual));
            }
        }
        context.takeScreenshot("10_easy_place_gallery");
        LitematicaPlusGameTest.require(failures.isEmpty(), "easy place produced wrong states: " + failures);
        int signIndex = indexOf("oak_sign");
        String line = world.getServer().computeOnServer(server -> {
            BlockEntity entity = server.overworld().getBlockEntity(BASE.offset(offset(signIndex), 0, 0));
            return entity instanceof SignBlockEntity sign ? sign.getText(SignTextSlot.FRONT).getMessages(false).get(0).getString() : "<none>";
        });
        LitematicaPlusGameTest.require("Hello".equals(line), "sign text should come from the schematic, got '" + line + "'");
        int crafterIndex = indexOf("crafter");
        context.waitTicks(20);
        boolean disabled = world.getServer().computeOnServer(server -> {
            BlockEntity entity = server.overworld().getBlockEntity(BASE.offset(offset(crafterIndex), 0, 0));
            return entity instanceof CrafterBlockEntity crafter && crafter.isSlotDisabled(0) && crafter.isSlotDisabled(4)
                    && !crafter.isSlotDisabled(1);
        });
        LitematicaPlusGameTest.require(disabled, "crafter slots from the schematic should be disabled");
        boolean screenOpen = context.computeOnClient(client -> client.gui.screen() != null);
        LitematicaPlusGameTest.require(!screenOpen, "no container or sign screen may stay open after easy place");

        strayBlockCheck(context, world);
        interactBehindGhost(context, world);
        supportUnderSnow(context, world);
        survivalBreakWrong(context, world);
        lockedSlotKeepsTool(context, world);

        context.runOnClient(client -> {
            LitematicaPlusConfig config = LitematicaPlusConfig.currentCopy();
            config.easyPlaceEnabled = false;
            LitematicaPlusConfig.save(config);
            PlacementManager.get().remove(placement);
        });
        world.getServer().runCommand("fill " + (BASE.getX() - 2) + " -60 " + (BASE.getZ() - 3) + " "
                + (BASE.getX() + TARGETS.length * SPACING + 2) + " -56 " + (BASE.getZ() + 3) + " minecraft:air");
        world.getServer().runCommand("item replace entity @p weapon.mainhand with minecraft:air");
        world.getConnection().waitForClientboundPackets();
        context.waitTicks(5);
    }

    private static void strayBlockCheck(ClientGameTestContext context, TestSingleplayerContext world) {
        int index = indexOf("quartz_pillar");
        BlockPos pos = BASE.offset(offset(index), 0, 0);
        world.getServer().runCommand("tp @p " + (pos.getX() + 0.5) + " -60 " + (pos.getZ() - 1.4) + " 0 38");
        world.getServer().runCommand("item replace entity @p weapon.mainhand with minecraft:quartz_pillar");
        world.getConnection().waitForClientboundPackets();
        context.waitTicks(6);
        context.getInput().holdMouseFor(InputConstants.MOUSE_BUTTON_RIGHT, 30);
        world.getConnection().waitForServerboundPackets();
        context.waitTicks(10);
        int extra = world.getServer().computeOnServer(server -> {
            int count = 0;
            for (BlockPos check : BlockPos.betweenClosed(pos.offset(-1, 0, -2), pos.offset(1, 2, 1))) {
                if (!check.equals(pos) && !server.overworld().getBlockState(check).isAir()) {
                    count++;
                }
            }
            return count;
        });
        LitematicaPlusGameTest.require(extra == 0, "holding use on a finished schematic block placed " + extra
                + " stray blocks next to it");
    }

    private static void interactBehindGhost(ClientGameTestContext context, TestSingleplayerContext world) {
        BlockPos ghost = BASE.offset(-6, 1, 0);
        BlockPos table = ghost.south();
        RegionBuilder builder = new RegionBuilder("Ghost", 0, 0, 0, 1, 1, 1);
        builder.set(0, 0, 0, net.minecraft.world.level.block.Blocks.STONE.defaultBlockState());
        SchematicMetadata metadata = new SchematicMetadata();
        metadata.name = "Ghost";
        Schematic schematic = new Schematic(metadata, Map.of("Ghost", builder.build()));
        Path file = context.computeOnClient(client -> {
            Path target = PlacementManager.schematicsDirectory().resolve("gametest/ghost.litematic");
            try {
                SchematicIo.write(target, schematic);
            } catch (IOException exception) {
                throw new UncheckedIOException(exception);
            }
            return target;
        });
        world.getServer().runCommand("setblock " + table.getX() + " " + table.getY() + " " + table.getZ()
                + " minecraft:crafting_table");
        world.getServer().runCommand("tp @p " + (ghost.getX() + 0.5) + " " + (ghost.getY() - 1) + " "
                + (ghost.getZ() - 1.5) + " 0 0");
        world.getServer().runCommand("item replace entity @p weapon.mainhand with minecraft:stone");
        world.getConnection().waitForClientboundPackets();
        SchematicPlacement placement = context.computeOnClient(client -> PlacementManager.get().create(file, ghost));
        context.waitFor(client -> GhostEngine.get().categoryAt(ghost.getX(), ghost.getY(), ghost.getZ())
                == org.lts.litematicaplus.verify.MismatchType.MISSING, 400);
        context.waitTicks(6);
        context.getInput().pressMouse(InputConstants.MOUSE_BUTTON_RIGHT);
        context.waitFor(client -> client.gui.screen() instanceof net.minecraft.client.gui.screens.inventory.CraftingScreen,
                100);
        boolean stillEmpty = world.getServer().computeOnServer(server -> server.overworld().getBlockState(ghost).isAir());
        context.takeScreenshot("10b_interact_behind_ghost");
        context.setScreen(() -> null);
        context.runOnClient(client -> PlacementManager.get().remove(placement));
        world.getServer().runCommand("setblock " + table.getX() + " " + table.getY() + " " + table.getZ()
                + " minecraft:air");
        world.getServer().runCommand("setblock " + ghost.getX() + " " + ghost.getY() + " " + ghost.getZ()
                + " minecraft:air");
        LitematicaPlusGameTest.require(stillEmpty, "a click on a crafting table must open it, not place the ghost in front");
    }

    private static void supportUnderSnow(ClientGameTestContext context, TestSingleplayerContext world) {
        BlockPos support = BASE.offset(-12, 0, 0);
        BlockPos snow = support.above();
        RegionBuilder builder = new RegionBuilder("Snow", 0, 0, 0, 1, 2, 1);
        builder.set(0, 0, 0, net.minecraft.world.level.block.Blocks.DIRT.defaultBlockState());
        builder.set(0, 1, 0, net.minecraft.world.level.block.Blocks.SNOW.defaultBlockState());
        SchematicMetadata metadata = new SchematicMetadata();
        metadata.name = "Snow";
        Schematic schematic = new Schematic(metadata, Map.of("Snow", builder.build()));
        Path file = context.computeOnClient(client -> {
            Path target = PlacementManager.schematicsDirectory().resolve("gametest/snow.litematic");
            try {
                SchematicIo.write(target, schematic);
            } catch (IOException exception) {
                throw new UncheckedIOException(exception);
            }
            return target;
        });
        for (BlockPos side : new BlockPos[]{support.north(), support.south(), support.east(), support.west()}) {
            world.getServer().runCommand("setblock " + side.getX() + " " + side.getY() + " " + side.getZ()
                    + " minecraft:stone");
        }
        world.getServer().runCommand("setblock " + support.getX() + " " + support.getY() + " " + support.getZ()
                + " minecraft:air");
        world.getServer().runCommand("tp @p " + (support.getX() + 0.5) + " " + (support.getY() + 1) + " "
                + (support.getZ() - 0.5) + " 0 67");
        world.getConnection().waitForClientboundPackets();
        SchematicPlacement placement = context.computeOnClient(client -> PlacementManager.get().create(file, support));
        context.waitFor(client -> GhostEngine.get().categoryAt(support.getX(), support.getY(), support.getZ())
                == org.lts.litematicaplus.verify.MismatchType.MISSING
                && GhostEngine.get().categoryAt(snow.getX(), snow.getY(), snow.getZ())
                == org.lts.litematicaplus.verify.MismatchType.MISSING, 400);
        context.waitTicks(6);
        boolean dirt = false;
        for (int attempt = 0; attempt < 4 && !dirt; attempt++) {
            context.getInput().pressMouse(InputConstants.MOUSE_BUTTON_RIGHT);
            for (int wait = 0; wait < 20 && !dirt; wait++) {
                context.waitTicks(2);
                dirt = world.getServer().computeOnServer(server -> server.overworld().getBlockState(support)
                        .is(net.minecraft.world.level.block.Blocks.DIRT));
            }
        }
        context.waitTicks(10);
        boolean layered = false;
        for (int attempt = 0; attempt < 4 && dirt && !layered; attempt++) {
            context.getInput().pressMouse(InputConstants.MOUSE_BUTTON_RIGHT);
            for (int wait = 0; wait < 20 && !layered; wait++) {
                context.waitTicks(2);
                layered = world.getServer().computeOnServer(server -> server.overworld().getBlockState(snow)
                        .is(net.minecraft.world.level.block.Blocks.SNOW));
            }
        }
        context.takeScreenshot("10c_support_under_snow");
        context.runOnClient(client -> PlacementManager.get().remove(placement));
        world.getServer().runCommand("fill " + (support.getX() - 1) + " " + support.getY() + " " + (support.getZ() - 1)
                + " " + (support.getX() + 1) + " " + (support.getY() + 1) + " " + (support.getZ() + 1) + " minecraft:air");
        LitematicaPlusGameTest.require(dirt, "easy place should reach the enclosed support block under a snow ghost");
        LitematicaPlusGameTest.require(layered, "the snow layer should go on once its support is placed");
    }

    private static void survivalBreakWrong(ClientGameTestContext context, TestSingleplayerContext world) {
        BlockPos spot = BASE.offset(-18, 0, 0);
        RegionBuilder builder = new RegionBuilder("Wrong", 0, 0, 0, 1, 1, 1);
        builder.set(0, 0, 0, net.minecraft.world.level.block.Blocks.DIRT.defaultBlockState());
        SchematicMetadata metadata = new SchematicMetadata();
        metadata.name = "Wrong";
        Schematic schematic = new Schematic(metadata, Map.of("Wrong", builder.build()));
        Path file = context.computeOnClient(client -> {
            Path target = PlacementManager.schematicsDirectory().resolve("gametest/wrong.litematic");
            try {
                SchematicIo.write(target, schematic);
            } catch (IOException exception) {
                throw new UncheckedIOException(exception);
            }
            return target;
        });
        world.getServer().runCommand("setblock " + spot.getX() + " " + spot.getY() + " " + spot.getZ() + " minecraft:stone");
        world.getServer().runCommand("gamemode survival @p");
        world.getServer().runCommand("clear @p");
        world.getServer().runCommand("item replace entity @p hotbar.1 with minecraft:diamond_pickaxe");
        world.getServer().runCommand("item replace entity @p hotbar.2 with minecraft:dirt 4");
        world.getServer().runCommand("tp @p " + (spot.getX() + 0.5) + " " + spot.getY() + " " + (spot.getZ() - 1.5) + " 0 37");
        world.getConnection().waitForClientboundPackets();
        context.runOnClient(client -> {
            client.player.getInventory().setSelectedSlot(0);
            LitematicaPlusConfig config = LitematicaPlusConfig.currentCopy();
            config.breakWrongBlocks = true;
            config.autoToolSwitch = true;
            LitematicaPlusConfig.save(config);
        });
        SchematicPlacement placement = context.computeOnClient(client -> PlacementManager.get().create(file, spot));
        context.waitFor(client -> GhostEngine.get().categoryAt(spot.getX(), spot.getY(), spot.getZ())
                == org.lts.litematicaplus.verify.MismatchType.WRONG_BLOCK, 400);
        context.waitTicks(6);
        boolean dirt = false;
        for (int attempt = 0; attempt < 6 && !dirt; attempt++) {
            context.getInput().holdMouseFor(InputConstants.MOUSE_BUTTON_RIGHT, 20);
            for (int wait = 0; wait < 15 && !dirt; wait++) {
                context.waitTicks(2);
                dirt = world.getServer().computeOnServer(server -> server.overworld().getBlockState(spot)
                        .is(net.minecraft.world.level.block.Blocks.DIRT));
            }
        }
        context.takeScreenshot("10d_survival_break_wrong");
        context.runOnClient(client -> {
            LitematicaPlusConfig config = LitematicaPlusConfig.currentCopy();
            config.breakWrongBlocks = false;
            config.autoToolSwitch = false;
            LitematicaPlusConfig.save(config);
            PlacementManager.get().remove(placement);
        });
        world.getServer().runCommand("gamemode creative @p");
        world.getServer().runCommand("clear @p");
        world.getServer().runCommand("setblock " + spot.getX() + " " + spot.getY() + " " + spot.getZ() + " minecraft:air");
        world.getConnection().waitForClientboundPackets();
        LitematicaPlusGameTest.require(dirt, "in survival the wrong stone should be mined out and replaced with dirt");
    }

    private static void lockedSlotKeepsTool(ClientGameTestContext context, TestSingleplayerContext world) {
        BlockPos spot = BASE.offset(-24, 0, 0);
        RegionBuilder builder = new RegionBuilder("Locked", 0, 0, 0, 1, 1, 1);
        builder.set(0, 0, 0, net.minecraft.world.level.block.Blocks.DIRT.defaultBlockState());
        SchematicMetadata metadata = new SchematicMetadata();
        metadata.name = "Locked";
        Schematic schematic = new Schematic(metadata, Map.of("Locked", builder.build()));
        Path file = context.computeOnClient(client -> {
            Path target = PlacementManager.schematicsDirectory().resolve("gametest/locked.litematic");
            try {
                SchematicIo.write(target, schematic);
            } catch (IOException exception) {
                throw new UncheckedIOException(exception);
            }
            return target;
        });
        world.getServer().runCommand("setblock " + spot.getX() + " " + spot.getY() + " " + spot.getZ() + " minecraft:air");
        world.getServer().runCommand("gamemode survival @p");
        world.getServer().runCommand("clear @p");
        world.getServer().runCommand("item replace entity @p hotbar.0 with minecraft:diamond_pickaxe");
        world.getServer().runCommand("item replace entity @p inventory.5 with minecraft:dirt 4");
        world.getServer().runCommand("tp @p " + (spot.getX() + 0.5) + " " + spot.getY() + " " + (spot.getZ() - 1.5) + " 0 37");
        world.getConnection().waitForClientboundPackets();
        context.runOnClient(client -> {
            client.player.getInventory().setSelectedSlot(0);
            LitematicaPlusConfig config = LitematicaPlusConfig.currentCopy();
            config.lockedHotbarSlots = 1;
            LitematicaPlusConfig.save(config);
        });
        SchematicPlacement placement = context.computeOnClient(client -> PlacementManager.get().create(file, spot));
        context.waitFor(client -> GhostEngine.get().categoryAt(spot.getX(), spot.getY(), spot.getZ())
                == org.lts.litematicaplus.verify.MismatchType.MISSING, 400);
        context.waitTicks(6);
        boolean dirt = false;
        for (int attempt = 0; attempt < 4 && !dirt; attempt++) {
            context.getInput().pressMouse(InputConstants.MOUSE_BUTTON_RIGHT);
            for (int wait = 0; wait < 15 && !dirt; wait++) {
                context.waitTicks(2);
                dirt = world.getServer().computeOnServer(server -> server.overworld().getBlockState(spot)
                        .is(net.minecraft.world.level.block.Blocks.DIRT));
            }
        }
        world.getConnection().waitForClientboundPackets();
        boolean toolKept = world.getServer().computeOnServer(server -> server.getPlayerList().getPlayers().getFirst()
                .getInventory().getItem(0).is(net.minecraft.world.item.Items.DIAMOND_PICKAXE));
        context.runOnClient(client -> {
            LitematicaPlusConfig config = LitematicaPlusConfig.currentCopy();
            config.lockedHotbarSlots = 0;
            LitematicaPlusConfig.save(config);
            PlacementManager.get().remove(placement);
        });
        world.getServer().runCommand("gamemode creative @p");
        world.getServer().runCommand("clear @p");
        world.getServer().runCommand("setblock " + spot.getX() + " " + spot.getY() + " " + spot.getZ() + " minecraft:air");
        world.getConnection().waitForClientboundPackets();
        LitematicaPlusGameTest.require(dirt, "easy place should take dirt from the main inventory into a free hotbar slot");
        LitematicaPlusGameTest.require(toolKept, "the pickaxe in the locked hotbar slot must stay where it is");
    }

    private static int indexOf(String name) {
        if (name.equals("oak_sign")) {
            return 12;
        }
        for (int i = 0; i < TARGETS.length; i++) {
            if (TARGETS[i].contains(name)) {
                return i;
            }
        }
        throw new IllegalStateException(name);
    }

    private static boolean matches(BlockState expected, BlockState actual) {
        if (expected.getBlock() != actual.getBlock()) {
            return false;
        }
        String text = BlockStateNbt.format(expected);
        int bracket = text.indexOf('[');
        if (bracket < 0) {
            return true;
        }
        for (Property<?> property : expected.getProperties()) {
            String name = property.getName();
            if (name.equals("powered") && !text.contains("lever") || name.equals("waterlogged") || name.equals("triggered")
                    || name.equals("lit") || name.equals("enabled") || name.equals("extended") || name.equals("locked")
                    || name.equals("crafting") || name.equals("shape") || name.equals("in_wall")
                    || name.equals("occupied") || name.equals("instrument")) {
                continue;
            }
            if (!expected.getValue(property).equals(actual.getValue(property))) {
                return false;
            }
        }
        return true;
    }
}
