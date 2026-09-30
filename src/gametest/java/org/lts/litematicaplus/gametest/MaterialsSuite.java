package org.lts.litematicaplus.gametest;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import org.lts.litematicaplus.LitematicaPlusConfig;
import org.lts.litematicaplus.gui.HubScreen;
import org.lts.litematicaplus.materials.MaterialExport;
import org.lts.litematicaplus.placement.PlacementManager;
import org.lts.litematicaplus.placement.SchematicPlacement;
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
        String take = TestUi.text("litematicaplus.materials.take", 2);
        LitematicaPlusGameTest.require(TestUi.hasLabel(context, take), "the chest should offer '" + take + "'");
        context.takeScreenshot("12_material_helper");
        TestUi.clickLabel(context, take);
        context.waitTicks(12);
        int gold = world.getServer().computeOnServer(server -> server.getPlayerList().getPlayers().get(0).getInventory()
                .countItem(Items.GOLD_BLOCK));
        boolean box = world.getServer().computeOnServer(server -> server.getPlayerList().getPlayers().get(0).getInventory()
                .contains(stack -> stack.is(Items.SHULKER_BOX)));
        int dirt = world.getServer().computeOnServer(server -> server.getPlayerList().getPlayers().get(0).getInventory()
                .countItem(Items.DIRT));
        LitematicaPlusGameTest.require(gold == 2 && box && dirt == 0, "Take materials should move 2 gold blocks and the shulker box only, got gold "
                + gold + " box " + box + " dirt " + dirt);
        LitematicaPlusGameTest.require(TestUi.hasLabel(context, TestUi.text("litematicaplus.materials.take_none")),
                "after taking, the chest should hold nothing more that is needed");
        context.setScreen(() -> null);
        context.waitTicks(5);

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
