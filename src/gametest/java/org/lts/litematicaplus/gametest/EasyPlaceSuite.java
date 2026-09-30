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
