package org.lts.litematicaplus;

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
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.lts.litematicaplus.materials.MaterialList;
import org.lts.litematicaplus.placement.PlacementTransform;
import org.lts.litematicaplus.schematic.BlockStateNbt;
import org.lts.litematicaplus.schematic.RegionBuilder;
import org.lts.litematicaplus.schematic.Schematic;
import org.lts.litematicaplus.schematic.SchematicMetadata;
import org.lts.litematicaplus.schematic.SpongeCodec;
import org.lts.litematicaplus.util.LocalizedException;
import org.lts.litematicaplus.verify.MismatchType;
import org.lts.litematicaplus.verify.StateComparator;
import org.lts.litematicaplus.world.GhostSection;
import org.lts.litematicaplus.world.PlacementVolume;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;

class AuditRegressionTest {
    @BeforeAll
    static void bootstrap() {
        TestBootstrap.ensure();
    }

    private static MismatchType compare(BlockState schematic, BlockState world) {
        return StateComparator.get().compare(Block.getId(schematic), Block.getId(world), true);
    }

    private static Map<Item, Integer> requirements(BlockState state) {
        Map<Item, Integer> result = new LinkedHashMap<>();
        MaterialList.requirements(state, (item, count) -> result.merge(item, count, Integer::sum));
        return result;
    }

    @Test
    void leverPositionAndCandleFlameAreNotIgnored() {
        BlockState lever = Blocks.LEVER.defaultBlockState();
        assertEquals(MismatchType.WRONG_STATE, compare(lever, lever.setValue(BlockStateProperties.POWERED, true)));
        BlockState candle = Blocks.CANDLE.defaultBlockState();
        assertEquals(MismatchType.WRONG_STATE, compare(candle, candle.setValue(BlockStateProperties.LIT, true)));
    }

    @Test
    void selfChangingStatesStayLenient() {
        BlockState button = Blocks.STONE_BUTTON.defaultBlockState();
        assertEquals(MismatchType.CORRECT, compare(button, button.setValue(BlockStateProperties.POWERED, true)));
        BlockState furnace = Blocks.FURNACE.defaultBlockState();
        assertEquals(MismatchType.CORRECT, compare(furnace, furnace.setValue(BlockStateProperties.LIT, true)));
        BlockState piston = Blocks.PISTON.defaultBlockState();
        assertEquals(MismatchType.CORRECT, compare(piston, piston.setValue(BlockStateProperties.EXTENDED, true)));
        BlockState barrel = Blocks.BARREL.defaultBlockState();
        assertEquals(MismatchType.CORRECT, compare(barrel, barrel.setValue(BlockStateProperties.OPEN, true)));
        BlockState door = Blocks.OAK_DOOR.defaultBlockState();
        assertEquals(MismatchType.WRONG_STATE, compare(door, door.setValue(BlockStateProperties.OPEN, true)));
    }

    @Test
    void blocksWithoutOwnItemResolveToWhatBuildsThem() {
        assertEquals(Map.of(Items.FLOWER_POT, 1, Items.POPPY, 1), requirements(Blocks.POTTED_POPPY.defaultBlockState()));
        assertEquals(Map.of(Items.FLOWER_POT, 1), requirements(Blocks.FLOWER_POT.defaultBlockState()));
        assertEquals(Map.of(Items.KELP, 1), requirements(Blocks.KELP_PLANT.defaultBlockState()));
        assertEquals(Map.of(Items.CAKE, 1, Items.CANDLE, 1), requirements(Blocks.CANDLE_CAKE.defaultBlockState()));
        assertEquals(Map.of(Items.SEAGRASS, 2), requirements(Blocks.TALL_SEAGRASS.defaultBlockState()));
        assertTrue(requirements(Blocks.BUBBLE_COLUMN.defaultBlockState()).isEmpty());
        assertSame(Items.WATER_BUCKET, MaterialList.itemOf(Blocks.WATER.defaultBlockState()));
    }

    @Test
    void unresolvedBlockNamesAreCounted() {
        BlockStateNbt.Unresolved unresolved = new BlockStateNbt.Unresolved();
        CompoundTag unknown = new CompoundTag();
        unknown.putString("Name", "somemod:missing_block");
        assertTrue(BlockStateNbt.read(unknown, unresolved).isAir());
        CompoundTag known = new CompoundTag();
        known.putString("Name", "minecraft:stone");
        assertSame(Blocks.STONE.defaultBlockState(), BlockStateNbt.read(known, unresolved));
        assertEquals(1, unresolved.count());
    }

    private static CompoundTag sponge(int width, int height, int length, int paletteId, byte[] data) {
        CompoundTag root = new CompoundTag();
        root.putShort("Width", (short) width);
        root.putShort("Height", (short) height);
        root.putShort("Length", (short) length);
        CompoundTag palette = new CompoundTag();
        palette.putInt("minecraft:stone", paletteId);
        root.put("Palette", palette);
        root.putByteArray("BlockData", data);
        return root;
    }

    @Test
    void spongeHeaderCannotForceHugeAllocations() {
        LocalizedException truncated = assertThrows(LocalizedException.class,
                () -> SpongeCodec.read(sponge(1290, 1290, 1290, 0, new byte[8]), "bomb"));
        assertEquals("litematicaplus.error.too_large", truncated.key());
        LocalizedException shortData = assertThrows(LocalizedException.class,
                () -> SpongeCodec.read(sponge(16, 16, 16, 0, new byte[8]), "short"));
        assertEquals("litematicaplus.error.corrupt", shortData.key());
        LocalizedException palette = assertThrows(LocalizedException.class,
                () -> SpongeCodec.read(sponge(1, 1, 1, 2000000000, new byte[1]), "palette"));
        assertEquals("litematicaplus.error.corrupt", palette.key());
    }

    @Test
    void airOnlySectionsShareOneInstance() throws IOException {
        RegionBuilder builder = new RegionBuilder("Main", 0, 0, 0, 48, 16, 16);
        builder.set(0, 0, 0, Blocks.STONE.defaultBlockState());
        Schematic schematic = new Schematic(new SchematicMetadata(), Map.of("Main", builder.build()));
        PlacementVolume volume = PlacementVolume.build(schematic,
                new PlacementTransform(0, 0, 0, Rotation.NONE, Mirror.NONE), null);
        GhostSection first = volume.section(SectionPos.asLong(1, 0, 0));
        GhostSection second = volume.section(SectionPos.asLong(2, 0, 0));
        assertSame(first, second);
        assertTrue(first.isFullAir());
        assertFalse(volume.section(SectionPos.asLong(0, 0, 0)).isFullAir());
        assertEquals(1L, volume.totalBlocks());
        assertEquals(0, volume.paletteIndexAt(40, 3, 3));
        assertSame(Blocks.STONE.defaultBlockState(), volume.stateAt(new BlockPos(0, 0, 0).getX(), 0, 0));
    }

    @Test
    void olderConfigGetsSaferPasteDefaults(@TempDir Path directory) throws IOException {
        Path file = directory.resolve("config.json");
        Files.writeString(file, """
                {"configVersion": 1, "pasteCommandsPerTick": 40, "easyPlaceAirPlacement": false, "easyPlaceDelayTicks": 5,
                 "ghostAlpha": 0.1}
                """);
        LitematicaPlusConfig config = LitematicaPlusConfig.load(file);
        assertEquals(8, config.pasteCommandsPerTick);
        assertTrue(config.easyPlaceAirPlacement);
        assertEquals(LitematicaPlusConfig.PlacementPreset.CUSTOM, config.easyPlacePreset);
        assertEquals(5, config.placementDelayTicks());
        assertEquals(0.2F, config.ghostAlpha);
        assertTrue(config.toolEnabled);
    }

    @Test
    void invalidSchematicsDirectoryFallsBack(@TempDir Path directory) throws IOException {
        Path file = directory.resolve("config.json");
        Files.writeString(file, "{\"schematicsDirectory\": \"bad\\u0000name\"}");
        assertEquals("schematics", LitematicaPlusConfig.load(file).schematicsDirectory);
    }
}
