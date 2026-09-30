package org.lts.litematicaplus.schematic;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import net.minecraft.core.Direction;
import net.minecraft.nbt.ByteArrayTag;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.IntTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.lts.litematicaplus.TestBootstrap;

class SchematicCodecTest {
    @BeforeAll
    static void bootstrap() {
        TestBootstrap.ensure();
    }

    private static Schematic sample() {
        RegionBuilder main = new RegionBuilder("Main", 0, 0, 0, 3, 2, 4);
        BlockState stairs = Blocks.OAK_STAIRS.defaultBlockState().setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.EAST);
        main.set(0, 0, 0, Blocks.STONE.defaultBlockState());
        main.set(2, 1, 3, stairs);
        main.set(1, 0, 2, Blocks.CHEST.defaultBlockState());
        CompoundTag chest = new CompoundTag();
        chest.putString("id", "minecraft:chest");
        chest.putString("CustomName", "loot");
        main.blockEntity(1, 0, 2, chest);
        RegionBuilder tower = new RegionBuilder("Tower", 5, 0, 5, 1, 9, 1);
        for (int y = 0; y < 9; y++) {
            tower.set(0, y, 0, Blocks.GLASS.defaultBlockState());
        }
        Map<String, SchematicRegion> regions = new LinkedHashMap<>();
        regions.put("Main", main.build());
        regions.put("Tower", tower.build());
        SchematicMetadata metadata = new SchematicMetadata();
        metadata.name = "Sample";
        metadata.author = "LTS";
        metadata.timeCreated = 123456789L;
        return new Schematic(metadata, regions);
    }

    @Test
    void derivedMetadataIsComputed() {
        Schematic schematic = sample();
        assertEquals(2, schematic.metadata().regionCount);
        assertEquals(12L, schematic.metadata().totalBlocks);
        assertEquals(24L + 9L, schematic.metadata().totalVolume);
        assertEquals(6, schematic.metadata().enclosingX);
        assertEquals(9, schematic.metadata().enclosingY);
        assertEquals(6, schematic.metadata().enclosingZ);
    }

    @Test
    void litematicRoundTripKeepsBlocksAndBlockEntities() throws IOException {
        Schematic restored = LitematicCodec.read(LitematicCodec.write(sample()));
        assertEquals("Sample", restored.metadata().name);
        assertEquals("LTS", restored.metadata().author);
        assertEquals(12L, restored.metadata().totalBlocks);
        SchematicRegion main = restored.regions().get("Main");
        assertSame(Blocks.STONE.defaultBlockState(), main.state(0, 0, 0));
        assertEquals(Direction.EAST, main.state(2, 1, 3).getValue(BlockStateProperties.HORIZONTAL_FACING));
        assertTrue(main.state(1, 1, 1).isAir());
        CompoundTag chest = main.blockEntities().get(SchematicRegion.localKey(1, 0, 2));
        assertEquals("loot", chest.getStringOr("CustomName", ""));
        assertFalse(chest.contains("x"));
        SchematicRegion tower = restored.regions().get("Tower");
        assertEquals(5, tower.posX());
        assertSame(Blocks.GLASS.defaultBlockState(), tower.state(0, 8, 0));
    }

    @Test
    void writtenFileUsesTheLitematicLayout() {
        CompoundTag root = LitematicCodec.write(sample());
        assertEquals(LitematicCodec.SCHEMATIC_VERSION, root.getIntOr("Version", 0));
        assertTrue(root.getIntOr("MinecraftDataVersion", 0) > 0);
        CompoundTag main = root.getCompoundOrEmpty("Regions").getCompoundOrEmpty("Main");
        assertEquals(3, main.getCompoundOrEmpty("Size").getIntOr("x", 0));
        assertEquals("minecraft:air", main.getListOrEmpty("BlockStatePalette").getCompoundOrEmpty(0).getStringOr("Name", ""));
        assertEquals(1, main.getListOrEmpty("TileEntities").size());
        assertEquals(2, main.getListOrEmpty("TileEntities").getCompoundOrEmpty(0).getIntOr("z", -1));
        assertEquals(BitPackedArray.requiredLongs(2, 24), main.getLongArray("BlockStates").orElseThrow().length);
    }

    @Test
    void negativeSizesAreAnchoredAtTheMinimumCorner() throws IOException {
        CompoundTag root = LitematicCodec.write(sample());
        CompoundTag main = root.getCompoundOrEmpty("Regions").getCompoundOrEmpty("Main");
        CompoundTag size = main.getCompoundOrEmpty("Size");
        size.putInt("x", -3);
        size.putInt("z", -4);
        Schematic restored = LitematicCodec.read(root);
        SchematicRegion region = restored.regions().get("Main");
        assertEquals(-2, region.minX());
        assertEquals(-3, region.minZ());
        assertEquals(3, region.absX());
        assertSame(Blocks.STONE.defaultBlockState(), region.state(0, 0, 0));
    }

    @Test
    void corruptBlockArrayIsRejectedWithAnExplanation() {
        CompoundTag root = LitematicCodec.write(sample());
        root.getCompoundOrEmpty("Regions").getCompoundOrEmpty("Main").putLongArray("BlockStates", new long[]{1L, 2L, 3L});
        IOException failure = assertThrows(IOException.class, () -> LitematicCodec.read(root));
        assertTrue(failure.getMessage().contains("Main"));
    }

    @Test
    void fileRoundTripAndMetadataOnlyRead(@TempDir Path directory) throws IOException {
        Path file = directory.resolve("nested/sample.litematic");
        SchematicIo.write(file, sample());
        assertTrue(Files.isRegularFile(file));
        assertFalse(Files.exists(file.resolveSibling("sample.litematic.tmp")));
        SchematicMetadata metadata = SchematicIo.readMetadata(file);
        assertEquals("Sample", metadata.name);
        assertEquals(12L, metadata.totalBlocks);
        assertEquals(6, metadata.enclosingX);
        Schematic restored = SchematicIo.read(file);
        assertEquals(2, restored.regions().size());
    }

    @Test
    void unknownBlocksBecomeAirInsteadOfFailing() {
        CompoundTag tag = new CompoundTag();
        tag.putString("Name", "othermod:missing_block");
        assertTrue(BlockStateNbt.read(tag).isAir());
        assertTrue(BlockStateNbt.parse("not a block id").isAir());
    }

    @Test
    void blockStateTextRoundTrip() {
        BlockState stairs = Blocks.OAK_STAIRS.defaultBlockState().setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.WEST);
        assertSame(stairs, BlockStateNbt.parse(BlockStateNbt.format(stairs)));
        assertSame(stairs, BlockStateNbt.read(BlockStateNbt.write(stairs)));
        assertEquals("minecraft:stone", BlockStateNbt.format(Blocks.STONE.defaultBlockState()));
    }

    @Test
    void vanillaStructureImport() throws IOException {
        CompoundTag root = new CompoundTag();
        ListTag size = new ListTag();
        size.add(IntTag.valueOf(2));
        size.add(IntTag.valueOf(1));
        size.add(IntTag.valueOf(2));
        root.put("size", size);
        ListTag palette = new ListTag();
        palette.add(BlockStateNbt.write(Blocks.DIRT.defaultBlockState()));
        palette.add(BlockStateNbt.write(Blocks.STRUCTURE_VOID.defaultBlockState()));
        root.put("palette", palette);
        ListTag blocks = new ListTag();
        blocks.add(structureBlock(1, 0, 1, 0));
        blocks.add(structureBlock(0, 0, 0, 1));
        root.put("blocks", blocks);
        assertTrue(VanillaStructureCodec.matches(root));
        Schematic schematic = VanillaStructureCodec.read(root, "house");
        SchematicRegion region = schematic.regions().get("house");
        assertSame(Blocks.DIRT.defaultBlockState(), region.state(1, 0, 1));
        assertTrue(region.state(0, 0, 0).isAir());
        assertEquals(1L, schematic.metadata().totalBlocks);
    }

    private static CompoundTag structureBlock(int x, int y, int z, int state) {
        CompoundTag block = new CompoundTag();
        ListTag pos = new ListTag();
        pos.add(IntTag.valueOf(x));
        pos.add(IntTag.valueOf(y));
        pos.add(IntTag.valueOf(z));
        block.put("pos", pos);
        block.putInt("state", state);
        return block;
    }

    @Test
    void spongeImportDecodesVarintData() throws IOException {
        CompoundTag root = new CompoundTag();
        root.putShort("Width", (short) 2);
        root.putShort("Height", (short) 1);
        root.putShort("Length", (short) 2);
        root.putInt("Version", 2);
        CompoundTag palette = new CompoundTag();
        palette.putInt("minecraft:air", 0);
        palette.putInt("minecraft:oak_stairs[facing=south,half=bottom,shape=straight,waterlogged=false]", 200);
        root.put("Palette", palette);
        root.put("BlockData", new ByteArrayTag(new byte[]{0, (byte) 0xC8, 0x01, 0, 0}));
        assertTrue(SpongeCodec.matches(root));
        Schematic schematic = SpongeCodec.read(root, "sponge");
        SchematicRegion region = schematic.regions().get("sponge");
        assertEquals(Direction.SOUTH, region.state(1, 0, 0).getValue(BlockStateProperties.HORIZONTAL_FACING));
        assertTrue(region.state(0, 0, 1).isAir());
        assertEquals(1L, schematic.metadata().totalBlocks);
    }

    @Test
    void unsupportedNbtIsReportedNotGuessed(@TempDir Path directory) throws IOException {
        Path file = directory.resolve("other.nbt");
        CompoundTag root = new CompoundTag();
        root.putString("hello", "world");
        NbtIo.writeCompressed(root, file);
        assertThrows(IOException.class, () -> SchematicIo.read(file));
    }
}
