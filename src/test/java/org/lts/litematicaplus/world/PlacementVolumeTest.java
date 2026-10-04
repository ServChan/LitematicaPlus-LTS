package org.lts.litematicaplus.world;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.lts.litematicaplus.TestBootstrap;
import org.lts.litematicaplus.placement.PlacementTransform;
import org.lts.litematicaplus.schematic.RegionBuilder;
import org.lts.litematicaplus.schematic.Schematic;
import org.lts.litematicaplus.schematic.SchematicMetadata;
import org.lts.litematicaplus.schematic.SchematicRegion;

class PlacementVolumeTest {
    @BeforeAll
    static void bootstrap() {
        TestBootstrap.ensure();
    }

    private static Schematic schematic() {
        RegionBuilder main = new RegionBuilder("Main", 0, 0, 0, 20, 2, 3);
        main.set(0, 0, 0, Blocks.STONE.defaultBlockState());
        main.set(19, 1, 2, Blocks.OAK_STAIRS.defaultBlockState()
                .setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.NORTH));
        main.set(4, 0, 1, Blocks.CHEST.defaultBlockState());
        CompoundTag chest = new CompoundTag();
        chest.putString("id", "minecraft:chest");
        main.blockEntity(4, 0, 1, chest);
        RegionBuilder extra = new RegionBuilder("Extra", 0, 5, 0, 1, 1, 1);
        extra.set(0, 0, 0, Blocks.GLASS.defaultBlockState());
        Map<String, SchematicRegion> regions = new LinkedHashMap<>();
        regions.put("Main", main.build());
        regions.put("Extra", extra.build());
        return new Schematic(new SchematicMetadata(), regions);
    }

    @Test
    void replacementsSwapBlocksAndKeepTheirShape() {
        PlacementVolume volume = PlacementVolume.build(schematic(), PlacementTransform.IDENTITY, Set.of(),
                Map.of(Blocks.STONE, Blocks.BRICKS, Blocks.OAK_STAIRS, Blocks.STONE_BRICK_STAIRS));
        assertSame(Blocks.BRICKS.defaultBlockState(), volume.stateAt(0, 0, 0));
        BlockState stairs = volume.stateAt(19, 1, 2);
        assertSame(Blocks.STONE_BRICK_STAIRS, stairs.getBlock());
        assertEquals(Direction.NORTH, stairs.getValue(BlockStateProperties.HORIZONTAL_FACING));
        assertSame(Blocks.GLASS.defaultBlockState(), volume.stateAt(0, 5, 0));
    }

    @Test
    void identityPlacementKeepsPositionsAndCountsBlocks() {
        PlacementVolume volume = PlacementVolume.build(schematic(), new PlacementTransform(10, 64, 10, Rotation.NONE, Mirror.NONE), Set.of());
        assertSame(Blocks.STONE.defaultBlockState(), volume.stateAt(10, 64, 10));
        assertSame(Blocks.GLASS.defaultBlockState(), volume.stateAt(10, 69, 10));
        assertTrue(volume.stateAt(11, 64, 10).isAir());
        assertEquals(0, volume.paletteIndexAt(11, 64, 10));
        assertEquals(-1, volume.paletteIndexAt(10, 67, 10));
        assertEquals(4L, volume.totalBlocks());
        assertEquals(10, volume.minX());
        assertEquals(29, volume.maxX());
        assertEquals(69, volume.maxY());
        assertEquals(2, volume.regionBoxes().size());
        assertNotNull(volume.blockEntities().get(BlockPos.asLong(14, 64, 11)));
        assertTrue(volume.sections().size() >= 2);
    }

    @Test
    void rotationMovesBlocksAndRotatesTheirStates() {
        PlacementVolume volume = PlacementVolume.build(schematic(),
                new PlacementTransform(0, 0, 0, Rotation.CLOCKWISE_90, Mirror.NONE), Set.of());
        BlockState stairs = volume.stateAt(-2, 1, 19);
        assertSame(Blocks.OAK_STAIRS, stairs.getBlock());
        assertEquals(Direction.EAST, stairs.getValue(BlockStateProperties.HORIZONTAL_FACING));
        assertEquals(-2, volume.minX());
        assertEquals(0, volume.maxX());
        assertEquals(19, volume.maxZ());
        assertNotNull(volume.blockEntities().get(BlockPos.asLong(-1, 0, 4)));
    }

    @Test
    void disabledRegionsAreLeftOut() {
        PlacementVolume volume = PlacementVolume.build(schematic(), PlacementTransform.IDENTITY, Set.of("Extra"));
        assertEquals(3L, volume.totalBlocks());
        assertEquals(1, volume.regionBoxes().size());
        assertEquals(-1, volume.paletteIndexAt(0, 5, 0));
    }

    @Test
    void totalsPerPaletteEntryMatchTheBlocks() {
        PlacementVolume volume = PlacementVolume.build(schematic(), PlacementTransform.IDENTITY, Set.of());
        long sum = 0L;
        for (int i = 0; i < volume.palette().length; i++) {
            if (volume.palette()[i].isAir()) {
                assertEquals(0L, volume.totalByPalette()[i]);
            }
            sum += volume.totalByPalette()[i];
        }
        assertEquals(volume.totalBlocks(), sum);
    }
}
