package org.lts.litematicaplus.gametest;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import org.lts.litematicaplus.LitematicaPlusConfig;
import org.lts.litematicaplus.Session;
import org.lts.litematicaplus.SessionStore;
import org.lts.litematicaplus.gui.HubScreen;
import org.lts.litematicaplus.gui.RawMaterialsScreen;
import org.lts.litematicaplus.materials.MaterialExport;
import org.lts.litematicaplus.materials.MaterialList;
import org.lts.litematicaplus.mhud.MaterialHud;
import org.lts.litematicaplus.mhud.StorageIndex;
import org.lts.litematicaplus.mhud.StorageSearch;
import org.lts.litematicaplus.placement.PlacementManager;
import org.lts.litematicaplus.placement.SchematicPlacement;
import org.lts.litematicaplus.recipes.RawPlanner;
import org.lts.litematicaplus.schematic.RegionBuilder;
import org.lts.litematicaplus.schematic.Schematic;
import org.lts.litematicaplus.schematic.SchematicIo;
import org.lts.litematicaplus.schematic.SchematicMetadata;

final class MaterialsSuite {
    private static final BlockPos BASE = new BlockPos(60, -60, 120);

    private MaterialsSuite() {
    }

    static void run(ClientGameTestContext context, TestSingleplayerContext world) {
        RegionBuilder builder = new RegionBuilder("Gold", 0, 0, 0, 3, 1, 1);
        for (int x = 0; x < 3; x++) {
            builder.set(x, 0, 0, Blocks.GOLD_BLOCK.defaultBlockState());
        }
        SchematicMetadata metadata = new SchematicMetadata();
        metadata.name = "Gold";
        metadata.totalBlocks = 3L;
        Schematic schematic = new Schematic(metadata, Map.of("Gold", builder.build()));
        Path file = context.computeOnClient(client -> {
            Path target = PlacementManager.schematicsDirectory().resolve("gametest/gold.litematic");
            try {
                SchematicIo.write(target, schematic);
            } catch (IOException exception) {
                throw new UncheckedIOException(exception);
            }
            return target;
        });
        world.getServer().runCommand("clear @p");
        world.getServer().runCommand("setblock 61 -60 123 minecraft:chest[facing=south]{Items:["
                + "{Slot:0b,id:\"minecraft:gold_block\",count:2},"
                + "{Slot:1b,id:\"minecraft:dirt\",count:5},"
                + "{Slot:2b,id:\"minecraft:shulker_box\",count:1,components:{\"minecraft:container\":"
                + "[{slot:0,item:{id:\"minecraft:gold_block\",count:4}}]}},"
                + "{Slot:3b,id:\"minecraft:gold_block\",count:5}]}");
        TestUi.aim(context, world, 61.5, -60.0, 125.5, 61.5, -59.5, 124.0);
        world.getConnection().waitForChunksRender();
        String scope = context.computeOnClient(client -> Session.scope());
        LitematicaPlusGameTest.require(scope.contains("|seed:"), "the session scope should include the world seed, got " + scope);
        SchematicPlacement placement = context.computeOnClient(client -> PlacementManager.get().create(file, BASE));
        LitematicaPlusGameTest.settle(context, placement);
        context.runOnClient(client -> {
            LitematicaPlusConfig config = LitematicaPlusConfig.currentCopy();
            config.easyPlaceEnabled = false;
            config.materialHelper = true;
            LitematicaPlusConfig.save(config);
        });
        context.waitTicks(5);

        TestUi.use(context, world, 12);
        LitematicaPlusGameTest.require(context.computeOnClient(client -> client.gui.screen() instanceof AbstractContainerScreen),
                "right click should open the chest");
        String take = TestUi.text("litematicaplus.panel.take_all");
        LitematicaPlusGameTest.require(TestUi.hasLabel(context, take), "the chest panel should offer '" + take + "'");
        context.takeScreenshot("12_material_helper");
        TestUi.clickLabel(context, take);
        context.waitTicks(20);
        world.getConnection().waitForClientboundPackets();
        int gold = world.getServer().computeOnServer(server -> server.getPlayerList().getPlayers().get(0).getInventory()
                .countItem(Items.GOLD_BLOCK));
        boolean box = world.getServer().computeOnServer(server -> server.getPlayerList().getPlayers().get(0).getInventory()
                .contains(stack -> stack.is(Items.SHULKER_BOX)));
        int dirt = world.getServer().computeOnServer(server -> server.getPlayerList().getPlayers().get(0).getInventory()
                .countItem(Items.DIRT));
        LitematicaPlusGameTest.require(gold == 3 && !box && dirt == 0,
                "Take needed should move exactly 3 gold blocks and leave the shulker box, got gold " + gold + " box " + box
                        + " dirt " + dirt);
        context.takeScreenshot("12b_material_helper_taken");
        world.getServer().runCommand("give @p minecraft:gold_block 10");
        world.getConnection().waitForClientboundPackets();
        context.waitTicks(15);
        TestUi.clickLabel(context, TestUi.text("litematicaplus.panel.deposit"));
        context.waitTicks(20);
        world.getConnection().waitForClientboundPackets();
        int kept = world.getServer().computeOnServer(server -> server.getPlayerList().getPlayers().get(0).getInventory()
                .countItem(Items.GOLD_BLOCK));
        LitematicaPlusGameTest.require(kept == 3, "Put back extra should leave exactly the 3 needed gold blocks, got " + kept);
        marks(context, world, placement, take);
        context.setScreen(() -> null);
        context.waitTicks(5);
        materialHud(context, world);
        rawMaterials(context, world, placement);
        if (System.getProperty("litematicaplus.materialsOnly") != null) {
            context.runOnClient(client -> PlacementManager.get().remove(placement));
            return;
        }

        Path directory = MaterialExport.directory();
        context.setScreen(() -> new HubScreen(null, HubScreen.Tab.MATERIALS));
        context.waitTicks(12);
        TestUi.clickLabel(context, TestUi.text("litematicaplus.materials.export"));
        context.waitTicks(4);
        context.takeScreenshot("gui_materials_export");
        TestUi.clickLabel(context, "XLSX");
        context.waitFor(client -> exported(directory, ".xlsx"), 200);
        for (String format : List.of("CSV", "MD", "JSON")) {
            TestUi.clickLabel(context, TestUi.text("litematicaplus.materials.export"));
            context.waitTicks(3);
            TestUi.clickLabel(context, format);
            context.waitFor(client -> exported(directory, "." + format.toLowerCase(java.util.Locale.ROOT)), 200);
        }
        context.waitTicks(6);
        context.takeScreenshot("gui_materials_exported");
        context.setScreen(() -> null);
        context.runOnClient(client -> PlacementManager.get().remove(placement));
        world.getServer().runCommand("setblock 61 -60 123 minecraft:air");
        world.getServer().runCommand("clear @p");
        world.getServer().runCommand("kill @e[type=minecraft:item]");
        world.getConnection().waitForClientboundPackets();
        context.waitTicks(5);
    }

    private static int gold(TestSingleplayerContext world) {
        return world.getServer().computeOnServer(server -> server.getPlayerList().getPlayers().get(0).getInventory()
                .countItem(Items.GOLD_BLOCK));
    }

    private static void marks(ClientGameTestContext context, TestSingleplayerContext world, SchematicPlacement placement,
            String take) {
        world.getServer().runCommand("clear @p");
        world.getConnection().waitForClientboundPackets();
        context.runOnClient(client -> PlacementManager.get().setCollected(placement, Items.GOLD_BLOCK,
                MaterialList.Entry.ALL));
        context.waitTicks(15);
        context.takeScreenshot("12c_material_marked");
        TestUi.clickLabel(context, take);
        context.waitTicks(20);
        world.getConnection().waitForClientboundPackets();
        LitematicaPlusGameTest.require(gold(world) == 0, "a marked material must not be taken, got " + gold(world));
        context.runOnClient(client -> PlacementManager.get().setCollected(placement, Items.GOLD_BLOCK, 2L));
        context.waitTicks(15);
        TestUi.clickLabel(context, take);
        context.waitTicks(20);
        world.getConnection().waitForClientboundPackets();
        LitematicaPlusGameTest.require(gold(world) == 1, "with 2 collected by hand only 1 gold block should be taken, got "
                + gold(world));
        boolean saved = context.computeOnClient(client -> {
            SessionStore.ScopeData data = new SessionStore.ScopeData();
            PlacementManager.get().snapshot(data);
            return data.placements.stream().anyMatch(entry -> Long.valueOf(2L).equals(
                    entry.collected.get("minecraft:gold_block")));
        });
        LitematicaPlusGameTest.require(saved, "the hand-entered amount should be saved with the session");
        context.runOnClient(client -> PlacementManager.get().clearCollected(placement));
    }

    private static void materialHud(ClientGameTestContext context, TestSingleplayerContext world) {
        world.getServer().runCommand("clear @p");
        world.getConnection().waitForClientboundPackets();
        context.runOnClient(client -> MaterialHud.setEnabled(true));
        context.waitTicks(25);
        TestUi.use(context, world, 12);
        LitematicaPlusGameTest.require(context.computeOnClient(client -> client.gui.screen() instanceof AbstractContainerScreen),
                "right click should open the chest for Material HUD");
        context.waitTicks(15);
        context.runOnClient(client -> MaterialHud.refresh());
        context.waitTicks(12);
        boolean recorded = context.computeOnClient(client -> {
            StorageIndex.Record record = StorageIndex.get().at(StorageIndex.scope(client), new BlockPos(61, -60, 123));
            return record != null && record.counts.containsKey("minecraft:gold_block");
        });
        LitematicaPlusGameTest.require(recorded, "Material HUD should remember the gold in the opened chest");
        context.runOnClient(client -> StorageSearch.get().toggle(MaterialHud.pageEntries()));
        context.waitTicks(15);
        boolean marked = context.computeOnClient(client -> StorageSearch.get().matches().stream()
                .anyMatch(match -> match.pos().equals(new BlockPos(61, -60, 123))));
        LitematicaPlusGameTest.require(marked, "the storage search should mark the chest holding gold");
        context.takeScreenshot("12d_material_hud_container");
        TestUi.clickLabel(context, TestUi.text("litematicaplus.mhud.transfer"));
        context.waitTicks(25);
        world.getConnection().waitForClientboundPackets();
        LitematicaPlusGameTest.require(gold(world) == 3, "Move everything needed should take the 3 missing gold blocks, got "
                + gold(world));
        int before = context.computeOnClient(client -> StorageSearch.highlightFrames());
        context.setScreen(() -> null);
        context.waitTicks(10);
        int drawn = context.computeOnClient(client -> StorageSearch.highlightFrames()) - before;
        int found = context.computeOnClient(client -> StorageSearch.get().matches().size());
        LitematicaPlusGameTest.require(drawn > 0, "storage highlight should be drawn in the world, frames " + drawn
                + ", matches " + found);
        context.takeScreenshot("12e_material_hud_world");
        hudExports(context);
        context.runOnClient(client -> {
            StorageSearch.get().clear();
            MaterialHud.setEnabled(false);
        });
        world.getServer().runCommand("clear @p");
        world.getConnection().waitForClientboundPackets();
        context.waitTicks(5);
    }

    private static void rawMaterials(ClientGameTestContext context, TestSingleplayerContext world,
            SchematicPlacement placement) {
        world.getServer().runCommand("clear @p");
        world.getServer().runCommand("give @p minecraft:pale_oak_log 4");
        world.getConnection().waitForClientboundPackets();
        Map<String, Long> previous = context.computeOnClient(client -> {
            LitematicaPlusConfig config = LitematicaPlusConfig.currentCopy();
            Map<String, Long> before = new LinkedHashMap<>(config.customMaterials);
            config.customMaterials = new LinkedHashMap<>(Map.of("minecraft:pale_oak_slab", 100L));
            config.rawUseStock = true;
            config.rawSmelting = false;
            config.rawChoices.clear();
            config.rawForced.clear();
            LitematicaPlusConfig.save(config);
            return before;
        });
        context.setScreen(() -> new RawMaterialsScreen(null, RawMaterialsScreen.Source.customList()));
        context.waitFor(client -> client.gui.screen() instanceof RawMaterialsScreen screen && screen.indexReady()
                && !screen.plan().isEmpty(), 400);
        context.waitTicks(25);
        long logs = context.computeOnClient(client -> {
            RawPlanner.Raw<Item> raw = ((RawMaterialsScreen) client.gui.screen()).plan()
                    .rawOf(Items.PALE_OAK_LOG);
            return raw == null ? -1L : raw.toGet();
        });
        long crafts = context.computeOnClient(client -> {
            RawPlanner.Step<Item> step = ((RawMaterialsScreen) client.gui.screen()).plan()
                    .stepOf(Items.PALE_OAK_SLAB);
            return step == null ? -1L : step.crafts();
        });
        context.takeScreenshot("gui_raw_custom");
        LitematicaPlusGameTest.require(crafts == 17L, "100 pale oak slabs need 17 crafts, got " + crafts);
        LitematicaPlusGameTest.require(logs == 9L, "100 pale oak slabs with 4 logs in stock need 9 more logs, got " + logs);
        context.setScreen(() -> new RawMaterialsScreen(null, RawMaterialsScreen.Source.placement(placement)));
        context.waitTicks(25);
        context.takeScreenshot("gui_raw_placement");
        long ingots = context.computeOnClient(client -> {
            RawPlanner.Plan<Item> plan = ((RawMaterialsScreen) client.gui.screen()).plan();
            RawPlanner.Raw<Item> raw = plan.rawOf(Items.GOLD_INGOT);
            return plan.rawOf(Items.GOLD_NUGGET) != null ? -2L : raw == null ? -1L : raw.toGet();
        });
        LitematicaPlusGameTest.require(ingots == 27L, "3 gold blocks should need 27 gold ingots and no nuggets, got " + ingots);
        context.runOnClient(client -> {
            LitematicaPlusConfig config = LitematicaPlusConfig.currentCopy();
            config.customMaterials = new LinkedHashMap<>(previous);
            LitematicaPlusConfig.save(config);
        });
        context.setScreen(() -> null);
        world.getServer().runCommand("clear @p");
        world.getConnection().waitForClientboundPackets();
        context.waitTicks(5);
    }

    private static void hudExports(ClientGameTestContext context) {
        Path directory = MaterialExport.directory();
        long start = System.currentTimeMillis() - 1000L;
        context.setScreen(() -> new HubScreen(null, HubScreen.Tab.MATERIALS));
        context.waitTicks(12);
        TestUi.clickLabel(context, TestUi.text("litematicaplus.materials.export"));
        context.waitTicks(4);
        context.takeScreenshot("12f_material_hud_export");
        for (String label : List.of("TSV", "PNG")) {
            TestUi.clickLabel(context, label);
            String extension = "." + label.toLowerCase(java.util.Locale.ROOT);
            context.waitFor(client -> newest(directory, extension, start) != null, 200);
        }
        TestUi.clickLabel(context, TestUi.text("litematicaplus.mhud.export.button.litematica"));
        context.waitFor(client -> newest(directory, ".json", start) != null, 200);
        TestUi.clickLabel(context, TestUi.text("litematicaplus.mhud.export.button.discord"));
        context.waitTicks(3);
        String clipboard = context.computeOnClient(client -> client.keyboardHandler.getClipboard());
        LitematicaPlusGameTest.require(clipboard.startsWith("**") && clipboard.contains("\n\u2022 "),
                "Discord export should copy a list, got " + clipboard);
        Path card = newest(directory, ".png", start);
        Path list = newest(directory, ".txt", start);
        try {
            java.awt.image.BufferedImage image = javax.imageio.ImageIO.read(card.toFile());
            java.util.Set<Integer> colors = new java.util.HashSet<>();
            for (int y = 146; y < 178; y++) {
                for (int x = 24; x < 56; x++) {
                    colors.add(image.getRGB(x, y));
                }
            }
            LitematicaPlusGameTest.require(image.getWidth() == 800 && colors.size() > 6,
                    "PNG card should show an item icon, colors " + colors.size());
            String text = Files.readString(list);
            LitematicaPlusGameTest.require(text.contains("minecraft:gold_block "), "Litematica list: " + text);
            LitematicaPlusGameTest.require(Files.readString(newest(directory, ".tsv", start)).contains("minecraft:gold_block"),
                    "TSV should list the gold block");
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
        context.setScreen(() -> null);
        context.waitTicks(5);
    }

    private static Path newest(Path directory, String extension, long since) {
        if (!Files.isDirectory(directory)) {
            return null;
        }
        try (Stream<Path> files = Files.list(directory)) {
            return files.filter(path -> {
                String name = path.getFileName().toString();
                try {
                    return name.startsWith("gold_") && name.endsWith(extension) && Files.size(path) > 20L
                            && Files.getLastModifiedTime(path).toMillis() >= since;
                } catch (IOException exception) {
                    return false;
                }
            }).findFirst().orElse(null);
        } catch (IOException exception) {
            return null;
        }
    }

    private static boolean exported(Path directory, String extension) {
        if (!Files.isDirectory(directory)) {
            return false;
        }
        try (Stream<Path> files = Files.list(directory)) {
            return files.anyMatch(path -> {
                String name = path.getFileName().toString();
                try {
                    return name.startsWith("gold_") && name.endsWith(extension) && Files.size(path) > 40L;
                } catch (IOException exception) {
                    return false;
                }
            });
        } catch (IOException exception) {
            return false;
        }
    }
}
