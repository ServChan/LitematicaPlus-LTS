package org.lts.litematicaplus.schematic;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Path;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.lts.litematicaplus.TestBootstrap;

class McEditCodecTest {
    @BeforeAll
    static void bootstrap() {
        TestBootstrap.ensure();
    }

    private static CompoundTag legacy() {
        CompoundTag root = new CompoundTag();
        root.putShort("Width", (short) 4);
        root.putShort("Height", (short) 1);
        root.putShort("Length", (short) 1);
        root.putString("Materials", "Alpha");
        root.putByteArray("Blocks", new byte[]{1, 35, 53, 54});
        root.putByteArray("Data", new byte[]{0, 1, 0, 2});
        ListTag tiles = new ListTag();
        CompoundTag chest = new CompoundTag();
        chest.putString("id", "Chest");
        chest.putInt("x", 3);
        chest.putInt("y", 0);
        chest.putInt("z", 0);
        chest.putString("CustomName", "loot");
        tiles.add(chest);
        root.put("TileEntities", tiles);
        root.put("Entities", new ListTag());
        return root;
    }

    @Test
    void legacyNumericBlocksBecomeModernStates() throws IOException {
        CompoundTag root = legacy();
        assertTrue(McEditCodec.matches(root));
        Schematic schematic = McEditCodec.read(root, "Old");
        SchematicRegion region = schematic.regions().get("Old");
        assertEquals(Blocks.STONE.defaultBlockState(), region.state(0, 0, 0));
        assertEquals("minecraft:orange_wool", BuiltInRegistries.BLOCK.getKey(region.state(1, 0, 0).getBlock()).toString());
        BlockState stairs = region.state(2, 0, 0);
        assertTrue(stairs.is(Blocks.OAK_STAIRS));
        assertEquals(Direction.EAST, stairs.getValue(BlockStateProperties.HORIZONTAL_FACING));
        BlockState chestState = region.state(3, 0, 0);
        assertTrue(chestState.is(Blocks.CHEST));
        assertEquals(Direction.NORTH, chestState.getValue(BlockStateProperties.HORIZONTAL_FACING));
        assertEquals(4L, schematic.metadata().totalBlocks);
        assertEquals(0, schematic.metadata().unknownStates);
        CompoundTag tile = region.blockEntities().get(SchematicRegion.localKey(3, 0, 0));
        assertNotNull(tile);
        assertEquals("minecraft:chest", tile.getStringOr("id", ""));
    }

    @Test
    void addBlocksUseTheLowNibbleForEvenIndices() throws IOException {
        CompoundTag root = legacy();
        root.putByteArray("AddBlocks", new byte[]{0x10, 0x00});
        Schematic schematic = McEditCodec.read(root, "Old");
        SchematicRegion region = schematic.regions().get("Old");
        assertEquals(Blocks.STONE.defaultBlockState(), region.state(0, 0, 0));
        assertFalse("minecraft:orange_wool".equals(BuiltInRegistries.BLOCK.getKey(region.state(1, 0, 0).getBlock()).toString()));
        assertTrue(region.state(2, 0, 0).is(Blocks.OAK_STAIRS));
    }

    @Test
    void schematicFilesAreReadThroughSchematicIo(@TempDir Path directory) throws IOException {
        Path file = directory.resolve("house.schematic");
        NbtIo.writeCompressed(legacy(), file);
        assertTrue(SchematicIo.isSupported(file));
        Schematic schematic = SchematicIo.read(file);
        assertEquals("mcedit", schematic.metadata().format);
        assertEquals("house", schematic.metadata().name);
        assertEquals(Blocks.STONE.defaultBlockState(), schematic.regions().get("house").state(0, 0, 0));
    }
}
