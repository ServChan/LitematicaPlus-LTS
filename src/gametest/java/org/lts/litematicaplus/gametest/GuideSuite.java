package org.lts.litematicaplus.gametest;

import com.mojang.blaze3d.platform.InputConstants;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.lang.reflect.Field;
import java.nio.file.Path;
import java.util.Map;
import java.util.function.Predicate;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.block.state.properties.SlabType;
import org.lts.litematicaplus.LitematicaPlusConfig;
import org.lts.litematicaplus.gui.HubScreen;
import org.lts.litematicaplus.gui.ListPanel;
import org.lts.litematicaplus.placement.PlacementManager;
import org.lts.litematicaplus.placement.SchematicPlacement;
import org.lts.litematicaplus.schematic.RegionBuilder;
import org.lts.litematicaplus.schematic.Schematic;
import org.lts.litematicaplus.schematic.SchematicIo;
import org.lts.litematicaplus.schematic.SchematicMetadata;
import org.lts.litematicaplus.schematic.SchematicRegion;
import org.lts.litematicaplus.selection.SelectionManager;
import org.lts.litematicaplus.tool.ToolController;
import org.lts.litematicaplus.tool.ToolMode;
import org.lts.litematicaplus.world.LayerRange;
import org.lts.litematicaplus.zones.ZonePlan;

final class GuideSuite {
    private static final int SX = 11;
    private static final int SY = 10;
    private static final int SZ = 9;
    private static final String[] CATEGORIES = {"rendering", "verify", "hud", "tools", "easy_place", "breaking",
            "materials", "hotkeys", "performance"};

    private GuideSuite() {
    }

    static void run(ClientGameTestContext context, TestSingleplayerContext world) {
        context.getInput().resizeWindow(1920, 1080);
        context.runOnClient(client -> {
            client.options.languageCode = "ru_ru";
            client.getLanguageManager().setSelected("ru_ru");
            client.options.guiScale().set(3);
            client.options.renderDistance().set(8);
            client.options.bobView().set(false);
            client.options.save();
            client.reloadResourcePacks();
        });
        context.waitTicks(40);
        context.waitFor(client -> client.gui.overlay() == null, 1200);
        context.runOnClient(client -> client.resizeGui());
        context.waitTicks(10);

        for (String command : new String[] {"time set 1500", "gamerule advance_time false", "gamerule doDaylightCycle false",
                "weather clear", "difficulty peaceful", "gamerule advance_weather false", "gamerule send_command_feedback false",
                "recipe give @p *", "clear @p"}) {
            world.getServer().runCommand(command);
        }
        for (int[] tree : new int[][] {{30, 26}, {35, 14}, {2, 27}, {25, 34}, {-6, 16}, {38, 30}, {14, 33}}) {
            world.getServer().runCommand("place feature minecraft:oak " + tree[0] + " -60 " + tree[1]);
        }
        world.getConnection().waitForClientboundPackets();
        context.waitTicks(10);

        Schematic cottage = cottage();
        Path file = context.computeOnClient(client -> {
            Path target = PlacementManager.schematicsDirectory().resolve("Домик.litematic");
            try {
                SchematicIo.write(target, cottage);
            } catch (IOException exception) {
                throw new UncheckedIOException(exception);
            }
            return target;
        });

        world.getServer().runOnServer(server -> {
            var player = server.getPlayerList().getPlayers().get(0);
            player.getAbilities().flying = true;
            player.onUpdateAbilities();
        });
        world.getServer().runCommand("tp @p 10.5 -60 10.5 -30 10");
        world.getConnection().waitForClientboundPackets();
        context.waitTicks(20);
        context.waitTicks(20);
        hud(context, true);
        shot(context, "a01_world_empty");

        context.setScreen(() -> new HubScreen(null, HubScreen.Tab.SCHEMATICS));
        context.waitTicks(15);
        shot(context, "a02_schematics_empty_select");
        clickListItem(context, item -> item.contains("Домик"));
        context.waitTicks(80);
        shot(context, "a03_schematics_preview");
        TestUi.clickLabel(context, TestUi.text("litematicaplus.schematics.place"));
        context.waitTicks(20);
        SchematicPlacement placement = context.computeOnClient(client -> PlacementManager.get().selected());
        LitematicaPlusGameTest.require(placement != null, "Place here should create a placement");
        LitematicaPlusGameTest.settle(context, placement);
        context.waitTicks(10);
        shot(context, "a04_editor");
        context.getInput().pressKey(InputConstants.KEY_T);
        context.waitTicks(30);
        shot(context, "a05_editor_top");
        context.getInput().pressKey(InputConstants.KEY_T);
        context.waitTicks(10);
        context.getInput().pressKey(InputConstants.KEY_ESCAPE);
        context.waitTicks(10);
        BlockPos origin = placement.origin();

        hud(context, false);
        hero(context, world, origin, "b01_hologram_front", -9.5, 5.0, -14.5);
        hero(context, world, origin, "b02_hologram_side", 22.5, 5.0, -11.5);
        hero(context, world, origin, "b03_hologram_high", -12.5, 15.0, -17.5);
        TestUi.aim(context, world, origin.getX() + 5.5, origin.getY() + 1, origin.getZ() + 4.5,
                origin.getX() + 0.5, origin.getY() + 2.5, origin.getZ() + 8.0);
        shot(context, "b04_hologram_inside");
        hud(context, true);
        hero(context, world, origin, "b05_hologram_hud", -8.5, 3.0, -13.5);

        tab(context, HubScreen.Tab.OVERVIEW, "c01_overview");
        tab(context, HubScreen.Tab.PLACEMENTS, "c02_placements");

        hud(context, false);
        for (int y = 0; y < 3; y++) {
            buildLayer(context, world, cottage, origin, y);
            LitematicaPlusGameTest.settle(context, placement);
            hero(context, world, origin, "d0" + y + "_build_layer", -9.5, 5.0, -14.5);
        }
        setBlock(world, origin.offset(1, 1, 0), Blocks.COBBLESTONE.defaultBlockState());
        setBlock(world, origin.offset(0, 2, 0), Blocks.OAK_LOG.defaultBlockState()
                .setValue(BlockStateProperties.AXIS, Direction.Axis.X));
        setBlock(world, origin.offset(4, 1, 2), Blocks.DIRT.defaultBlockState());
        setBlock(world, origin.offset(6, 1, 4), Blocks.GOLD_BLOCK.defaultBlockState());
        setBlock(world, origin.offset(7, 2, 0), Blocks.AIR.defaultBlockState());
        setBlock(world, origin.offset(8, 1, 0), Blocks.SPRUCE_PLANKS.defaultBlockState());
        LitematicaPlusGameTest.settle(context, placement);
        hud(context, true);
        TestUi.aim(context, world, origin.getX() + 2.5, origin.getY() + 0.5, origin.getZ() - 6.5,
                origin.getX() + 4.0, origin.getY() + 1.5, origin.getZ() + 0.5);
        shot(context, "e01_errors_overlay");
        tab(context, HubScreen.Tab.VERIFIER, "e02_verifier");
        tab(context, HubScreen.Tab.MATERIALS, "e03_materials");

        context.runOnClient(client -> ToolController.get().applyLayer(
                new LayerRange(LayerRange.Mode.SINGLE, Direction.Axis.Y, origin.getY() + 3, 0, 0)));
        LitematicaPlusGameTest.settle(context, placement);
        hero(context, world, origin, "f01_layer_single", -8.5, 8.0, -13.5);
        context.setScreen(() -> HubScreen.layers(null));
        context.waitTicks(12);
        shot(context, "f02_layers_tab");
        context.setScreen(() -> null);
        context.runOnClient(client -> ToolController.get().applyLayer(LayerRange.ALL));
        LitematicaPlusGameTest.settle(context, placement);

        context.runOnClient(client -> {
            LitematicaPlusConfig config = LitematicaPlusConfig.currentCopy();
            config.easyPlaceEnabled = true;
            config.materialHelper = true;
            config.materialPanel = true;
            LitematicaPlusConfig.save(config);
        });
        for (String item : new String[] {"oak_planks 64", "oak_log 32", "glass 16", "spruce_stairs 64", "stone_bricks 16"}) {
            world.getServer().runCommand("give @p minecraft:" + item);
        }
        world.getConnection().waitForClientboundPackets();
        context.runOnClient(client -> client.player.getInventory().setSelectedSlot(8));
        context.waitTicks(5);
        double standX = origin.getX() + 3.5;
        double standZ = origin.getZ() - 4.0;
        TestUi.aim(context, world, standX, origin.getY(), standZ, origin.getX() + 1.5, origin.getY() + 3.5,
                origin.getZ() + 0.0);
        shot(context, "g01_easy_aim");
        int frame = 2;
        for (int x = 2; x <= 5; x++) {
            TestUi.aim(context, world, standX, origin.getY(), standZ, origin.getX() + x + 0.5, origin.getY() + 3.5,
                    origin.getZ() + 0.0);
            TestUi.use(context, world, 8);
            shot(context, "g0" + frame++ + "_easy_place");
        }
        context.setScreen(() -> new HubScreen(null, HubScreen.Tab.SETTINGS));
        context.waitTicks(10);
        TestUi.clickLastLabel(context, TestUi.text("litematicaplus.settings.category.easy_place"));
        context.waitTicks(4);
        hover(context, TestUi.text("litematicaplus.option.easy_place"));
        context.takeScreenshot("guide_g09_easy_setting_hint");
        context.setScreen(() -> null);
        context.waitTicks(4);

        BlockPos chest = origin.offset(-3, 0, 3);
        world.getServer().runCommand("setblock " + chest.getX() + " " + chest.getY() + " " + chest.getZ()
                + " minecraft:chest[facing=west]{Items:[{Slot:0b,id:\"minecraft:spruce_stairs\",count:64},"
                + "{Slot:1b,id:\"minecraft:oak_planks\",count:64},{Slot:2b,id:\"minecraft:glass\",count:12},"
                + "{Slot:3b,id:\"minecraft:dirt\",count:30},{Slot:4b,id:\"minecraft:spruce_slab\",count:16},"
                + "{Slot:5b,id:\"minecraft:shulker_box\",count:1,components:{\"minecraft:container\":"
                + "[{slot:0,item:{id:\"minecraft:oak_log\",count:20}},{slot:1,item:{id:\"minecraft:torch\",count:8}}]}},"
                + "{Slot:9b,id:\"minecraft:bookshelf\",count:4},{Slot:10b,id:\"minecraft:cobblestone\",count:40}]}");
        world.getServer().runCommand("clear @p");
        world.getConnection().waitForClientboundPackets();
        TestUi.aim(context, world, chest.getX() - 2.5, chest.getY(), chest.getZ() + 0.5, chest.getX() + 0.5,
                chest.getY() + 0.5, chest.getZ() + 0.5);
        TestUi.use(context, world, 15);
        shot(context, "h01_container_panel");
        String take = TestUi.text("litematicaplus.panel.take_all");
        if (TestUi.hasLabel(context, take)) {
            TestUi.clickLabel(context, take);
            context.waitTicks(25);
            shot(context, "h02_container_taken");
        }
        context.setScreen(() -> null);
        context.waitTicks(5);

        context.runOnClient(client -> PlacementManager.get().setReplacement(placement, Blocks.OAK_PLANKS,
                Blocks.BIRCH_PLANKS));
        LitematicaPlusGameTest.settle(context, placement);
        tab(context, HubScreen.Tab.REPLACEMENTS, "i01_replacements");
        hud(context, false);
        hero(context, world, origin, "i02_replacement_world", -9.5, 5.0, -14.5);
        context.runOnClient(client -> PlacementManager.get().clearReplacements(placement));
        LitematicaPlusGameTest.settle(context, placement);

        context.runOnClient(client -> PlacementManager.get().setZonePlan(placement, new ZonePlan(2, 2, 1)));
        context.waitTicks(10);
        tab(context, HubScreen.Tab.ZONES, "j01_zones");
        context.runOnClient(client -> PlacementManager.get().setZonePlan(placement, new ZonePlan(1, 1, 1)));

        for (int y = 3; y < SY; y++) {
            buildLayer(context, world, cottage, origin, y);
            if (y == 3) {
                buildLayer(context, world, cottage, origin, 1);
                buildLayer(context, world, cottage, origin, 2);
                setBlock(world, origin.offset(4, 1, 2), Blocks.AIR.defaultBlockState());
                setBlock(world, origin.offset(6, 1, 4), Blocks.AIR.defaultBlockState());
            }
            LitematicaPlusGameTest.settle(context, placement);
            hero(context, world, origin, "k" + y + "_build_layer", -9.5, 5.0, -14.5);
        }
        hud(context, true);
        hero(context, world, origin, "k10_built_hud", -9.5, 4.0, -14.5);
        tab(context, HubScreen.Tab.OVERVIEW, "k11_overview_done");

        world.getServer().runCommand("item replace entity @p weapon.mainhand with minecraft:stick");
        world.getConnection().waitForClientboundPackets();
        context.runOnClient(client -> {
            client.player.getInventory().setSelectedSlot(0);
            ToolController.get().setMode(ToolMode.AREA_SELECTION);
            SelectionManager selection = SelectionManager.get();
            selection.clear();
            selection.setCorner(true, origin.offset(-1, 0, -1));
            selection.setCorner(false, origin.offset(SX, SY - 1, SZ));
            PlacementManager.get().setEnabled(placement, false);
        });
        context.waitTicks(10);
        hero(context, world, origin, "l01_area_selection", -10.5, 5.0, -15.5);
        context.setScreen(() -> new HubScreen(null, HubScreen.Tab.AREA));
        context.waitTicks(10);
        typeInto(context, "my_cottage");
        shot(context, "l02_area_tab");
        context.setScreen(() -> null);
        context.runOnClient(client -> PlacementManager.get().setEnabled(placement, true));
        context.waitTicks(5);

        context.runOnClient(client -> ToolController.get().setMode(ToolMode.PASTE));
        context.waitTicks(10);
        hero(context, world, origin, "l03_tool_paste_mode", -10.5, 5.0, -15.5);
        context.runOnClient(client -> {
            ToolController.get().setMode(ToolMode.AREA_SELECTION);
            SelectionManager.get().clear();
        });

        settings(context);
        tab(context, HubScreen.Tab.ADDONS, "n01_addons");
        tab(context, HubScreen.Tab.SERVER, "n02_server");
        context.setScreen(() -> null);
        context.waitTicks(5);
    }

    private static void settings(ClientGameTestContext context) {
        context.setScreen(() -> new HubScreen(null, HubScreen.Tab.SETTINGS));
        context.waitTicks(12);
        for (String category : CATEGORIES) {
            TestUi.clickLastLabel(context, TestUi.text("litematicaplus.settings.category." + category));
            context.waitTicks(4);
            for (int page = 1; page <= 4; page++) {
                shot(context, "m_" + category + "_" + page);
                if (!nextPage(context)) {
                    break;
                }
            }
        }
        TestUi.clickLastLabel(context, TestUi.text("litematicaplus.settings.category.rendering"));
        context.waitTicks(4);
        context.runOnClient(client -> {
            for (GuiEventListener child : client.gui.screen().children()) {
                if (child instanceof EditBox box) {
                    client.gui.screen().setFocused(box);
                    box.setValue("призрак");
                    return;
                }
            }
        });
        context.waitTicks(6);
        shot(context, "m_search");
        context.runOnClient(client -> {
            for (GuiEventListener child : client.gui.screen().children()) {
                if (child instanceof EditBox box) {
                    box.setValue("");
                    return;
                }
            }
        });
        context.waitTicks(4);
    }

    private static void typeInto(ClientGameTestContext context, String text) {
        context.runOnClient(client -> {
            for (GuiEventListener child : client.gui.screen().children()) {
                if (child instanceof EditBox box && box.isVisible() && box.getValue().isEmpty()) {
                    box.setValue(text);
                    return;
                }
            }
        });
        context.waitTicks(4);
    }

    private static void hero(ClientGameTestContext context, TestSingleplayerContext world, BlockPos origin, String name,
            double dx, double dy, double dz) {
        TestUi.aim(context, world, origin.getX() + dx, origin.getY() + dy, origin.getZ() + dz,
                origin.getX() + SX / 2.0, origin.getY() + 3.5, origin.getZ() + SZ / 2.0);
        context.waitTicks(20);
        context.waitTicks(10);
        shot(context, name);
    }

    private static void tab(ClientGameTestContext context, HubScreen.Tab tab, String name) {
        context.setScreen(() -> new HubScreen(null, tab));
        context.waitTicks(15);
        shot(context, name);
        context.setScreen(() -> null);
        context.waitTicks(4);
    }

    private static void shot(ClientGameTestContext context, String name) {
        context.runOnClient(client -> {
            client.gui.hud.getChat().clearMessages(false);
            client.gui.toastManager().clear();
        });
        if (context.computeOnClient(client -> client.gui.screen() != null)) {
            context.getInput().setCursorPos(1915, 4);
        }
        context.waitTicks(4);
        context.takeScreenshot("guide_" + name);
    }

    private static void hud(ClientGameTestContext context, boolean visible) {
        context.runOnClient(client -> {
            if (client.gui.hud.isHidden() == visible) {
                client.gui.hud.toggle();
            }
        });
        context.waitTicks(2);
    }

    private static void hover(ClientGameTestContext context, String label) {
        int[] center = context.computeOnClient(client -> {
            for (GuiEventListener child : client.gui.screen().children()) {
                if (child instanceof AbstractWidget widget && widget.visible && widget.getMessage().getString().equals(label)) {
                    return new int[] {widget.getX() + widget.getWidth() / 2, widget.getY() + widget.getHeight() / 2};
                }
            }
            return null;
        });
        if (center == null) {
            return;
        }
        int scale = context.computeOnClient(client -> client.getWindow().getGuiScale());
        context.getInput().setCursorPos(center[0] * scale, center[1] * scale);
        context.waitTicks(20);
    }

    private static boolean nextPage(ClientGameTestContext context) {
        Boolean active = context.computeOnClient(client -> {
            for (GuiEventListener child : client.gui.screen().children()) {
                if (child instanceof AbstractWidget widget && widget.visible && widget.getMessage().getString().equals(">")) {
                    return widget.active;
                }
            }
            return false;
        });
        if (!active) {
            return false;
        }
        TestUi.clickLabel(context, ">");
        context.waitTicks(4);
        return true;
    }

    private static void clickListItem(ClientGameTestContext context, Predicate<String> matches) {
        int[] point = context.computeOnClient(client -> {
            for (GuiEventListener child : client.gui.screen().children()) {
                if (child instanceof ListPanel<?> list) {
                    for (int i = 0; i < list.items().size(); i++) {
                        if (matches.test(String.valueOf(list.items().get(i)))) {
                            int row = rowHeight(list);
                            return new int[] {list.getX() + list.getWidth() / 3,
                                    list.getY() + 1 + i * row - list.scroll() + row / 2};
                        }
                    }
                }
            }
            return null;
        });
        LitematicaPlusGameTest.require(point != null, "the schematic list should contain the guide schematic");
        int scale = context.computeOnClient(client -> client.getWindow().getGuiScale());
        context.getInput().setCursorPos(point[0] * scale, point[1] * scale);
        context.waitTicks(2);
        context.getInput().pressMouse(InputConstants.MOUSE_BUTTON_LEFT);
        context.waitTicks(3);
    }

    private static int rowHeight(ListPanel<?> list) {
        try {
            Field field = ListPanel.class.getDeclaredField("rowHeight");
            field.setAccessible(true);
            return field.getInt(list);
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException(exception);
        }
    }

    private static void buildLayer(ClientGameTestContext context, TestSingleplayerContext world, Schematic schematic,
            BlockPos origin, int layer) {
        SchematicRegion region = schematic.regions().get("Cottage");
        world.getServer().runOnServer(server -> {
            for (int z = 0; z < region.absZ(); z++) {
                for (int x = 0; x < region.absX(); x++) {
                    BlockState state = region.state(x, layer, z);
                    if (!state.isAir()) {
                        server.overworld().setBlock(origin.offset(x, layer, z), state, 2);
                    }
                }
            }
        });
        world.getConnection().waitForClientboundPackets();
        context.waitTicks(6);
    }

    private static void setBlock(TestSingleplayerContext world, BlockPos pos, BlockState state) {
        world.getServer().runOnServer(server -> server.overworld().setBlock(pos, state, 2));
        world.getConnection().waitForClientboundPackets();
    }

    static Schematic cottage() {
        RegionBuilder builder = new RegionBuilder("Cottage", 0, 0, 0, SX, SY, SZ);
        BlockState planks = Blocks.OAK_PLANKS.defaultBlockState();
        BlockState log = Blocks.OAK_LOG.defaultBlockState();
        BlockState beam = log.setValue(BlockStateProperties.AXIS, Direction.Axis.X);
        BlockState glass = Blocks.GLASS.defaultBlockState();
        BlockState stairs = Blocks.SPRUCE_STAIRS.defaultBlockState();
        for (int x = 0; x < SX; x++) {
            for (int z = 0; z < SZ; z++) {
                builder.set(x, 0, z, Blocks.STONE_BRICKS.defaultBlockState());
                boolean wall = x == 0 || x == SX - 1 || z == 0 || z == SZ - 1;
                if (!wall) {
                    continue;
                }
                boolean corner = (x == 0 || x == SX - 1) && (z == 0 || z == SZ - 1);
                for (int y = 1; y <= 4; y++) {
                    BlockState state = planks;
                    if (corner) {
                        state = log;
                    } else if (y == 4 && (z == 0 || z == SZ - 1)) {
                        state = beam;
                    } else if ((y == 2 || y == 3) && (z == 0 || z == SZ - 1) && (x == 2 || x == 3 || x == 7 || x == 8)) {
                        state = glass;
                    } else if ((y == 2 || y == 3) && (x == 0 || x == SX - 1) && z == 4) {
                        state = glass;
                    }
                    if (x == 5 && z == 0 && (y == 1 || y == 2)) {
                        state = Blocks.OAK_DOOR.defaultBlockState()
                                .setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.SOUTH)
                                .setValue(BlockStateProperties.DOUBLE_BLOCK_HALF, y == 1 ? DoubleBlockHalf.LOWER
                                        : DoubleBlockHalf.UPPER);
                    }
                    builder.set(x, y, z, state);
                }
            }
        }
        for (int k = 0; k <= 4; k++) {
            int y = 5 + k;
            for (int x = 0; x < SX; x++) {
                if (k < 4) {
                    builder.set(x, y, k, stairs.setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.SOUTH));
                    builder.set(x, y, SZ - 1 - k, stairs.setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.NORTH));
                } else {
                    builder.set(x, y, 4, Blocks.SPRUCE_SLAB.defaultBlockState()
                            .setValue(BlockStateProperties.SLAB_TYPE, SlabType.BOTTOM));
                }
            }
            for (int z = k + 1; z <= SZ - 2 - k; z++) {
                builder.set(0, y, z, planks);
                builder.set(SX - 1, y, z, planks);
            }
        }
        builder.set(1, 1, 7, Blocks.CHEST.defaultBlockState()
                .setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.NORTH));
        CompoundTag chest = new CompoundTag();
        chest.putString("id", "minecraft:chest");
        chest.put("Items", new ListTag());
        builder.blockEntity(1, 1, 7, chest);
        builder.set(2, 1, 7, Blocks.CRAFTING_TABLE.defaultBlockState());
        builder.set(3, 1, 7, Blocks.FURNACE.defaultBlockState()
                .setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.NORTH));
        builder.set(9, 1, 1, Blocks.BOOKSHELF.defaultBlockState());
        builder.set(9, 2, 1, Blocks.BOOKSHELF.defaultBlockState());
        BlockState foot = BuiltInRegistries.BLOCK.getValue(Identifier.parse("minecraft:red_bed")).defaultBlockState()
                .setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.SOUTH);
        builder.set(8, 1, 5, foot);
        builder.set(8, 1, 6, foot.setValue(BlockStateProperties.BED_PART, BedPart.HEAD));
        builder.set(1, 3, 4, Blocks.WALL_TORCH.defaultBlockState()
                .setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.EAST));
        builder.set(9, 3, 4, Blocks.WALL_TORCH.defaultBlockState()
                .setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.WEST));
        SchematicRegion region = builder.build();
        long count = 0L;
        for (int y = 0; y < region.absY(); y++) {
            for (int z = 0; z < region.absZ(); z++) {
                for (int x = 0; x < region.absX(); x++) {
                    if (!region.state(x, y, z).isAir()) {
                        count++;
                    }
                }
            }
        }
        SchematicMetadata metadata = new SchematicMetadata();
        metadata.name = "Уютный домик";
        metadata.author = "Litematica Plus";
        metadata.description = "Домик для видеогайда.";
        metadata.timeCreated = System.currentTimeMillis();
        metadata.totalBlocks = count;
        return new Schematic(metadata, Map.of("Cottage", region));
    }
}
