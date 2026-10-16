package org.lts.litematicaplus.verify;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import org.junit.jupiter.api.Test;
import org.lts.litematicaplus.TestBootstrap;
import org.lts.litematicaplus.tool.Substitutes;

class LooseBlocksTest {
    private static MismatchType compare(BlockState schematic, BlockState world, boolean loose) {
        return StateComparator.get().compare(Block.getId(schematic), Block.getId(world), true, loose);
    }

    @Test
    void similarFullBlocksCountOnlyWhenLoose() {
        TestBootstrap.ensure();
        BlockState quartz = Blocks.QUARTZ_BLOCK.defaultBlockState();
        BlockState stone = Blocks.STONE.defaultBlockState();
        assertEquals(MismatchType.WRONG_BLOCK, compare(quartz, stone, false));
        assertEquals(MismatchType.CORRECT, compare(quartz, stone, true));
        assertEquals(MismatchType.CORRECT,
                compare(BuiltInRegistries.BLOCK.getValue(Identifier.withDefaultNamespace("white_stained_glass")).defaultBlockState(),
                        Blocks.GLASS.defaultBlockState(), true));
    }

    @Test
    void groupedBlocksStillCompareTheirState() {
        TestBootstrap.ensure();
        BlockState oak = Blocks.OAK_STAIRS.defaultBlockState().setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.EAST);
        BlockState stoneEast = Blocks.STONE_STAIRS.defaultBlockState()
                .setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.EAST);
        BlockState stoneWest = stoneEast.setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.WEST);
        assertEquals(MismatchType.CORRECT, compare(oak, stoneEast, true));
        assertEquals(MismatchType.WRONG_STATE, compare(oak, stoneWest, true));
    }

    @Test
    void redstoneFallingAndDifferentShapesStayStrict() {
        TestBootstrap.ensure();
        assertEquals(MismatchType.WRONG_BLOCK,
                compare(Blocks.STONE.defaultBlockState(), Blocks.SAND.defaultBlockState(), true));
        assertEquals(MismatchType.WRONG_BLOCK,
                compare(Blocks.STONE.defaultBlockState(), Blocks.OAK_SLAB.defaultBlockState(), true));
        assertEquals(MismatchType.WRONG_BLOCK,
                compare(Blocks.REDSTONE_LAMP.defaultBlockState(), Blocks.STONE.defaultBlockState(), true));
        assertEquals(MismatchType.WRONG_BLOCK,
                compare(Blocks.IRON_DOOR.defaultBlockState(), Blocks.OAK_DOOR.defaultBlockState(), true));
        assertFalse(StateComparator.get().sameGroup(Blocks.CHEST, Blocks.BARREL));
        assertTrue(StateComparator.get().sameGroup(Blocks.OAK_PLANKS, Blocks.STONE));
    }

    @Test
    void wallsNeverStandInForEachOther() {
        TestBootstrap.ensure();
        assertFalse(StateComparator.get().sameGroup(Blocks.COBBLESTONE_WALL, Blocks.STONE_BRICK_WALL));
        assertEquals(MismatchType.WRONG_BLOCK,
                compare(Blocks.COBBLESTONE_WALL.defaultBlockState(), Blocks.MOSSY_COBBLESTONE_WALL.defaultBlockState(), true));
        assertFalse(Substitutes.accepts(Blocks.COBBLESTONE_WALL.defaultBlockState(),
                Blocks.STONE_BRICK_WALL.defaultBlockState()));
    }

    @Test
    void dirtAndGrassSwapOnlyWithTheSoilRule() {
        TestBootstrap.ensure();
        StateComparator comparator = StateComparator.get();
        int grass = Block.getId(Blocks.GRASS_BLOCK.defaultBlockState());
        int dirt = Block.getId(Blocks.DIRT.defaultBlockState());
        int path = Block.getId(Blocks.DIRT_PATH.defaultBlockState());
        assertEquals(MismatchType.WRONG_BLOCK, comparator.compare(grass, dirt, true, false, false));
        assertEquals(MismatchType.CORRECT, comparator.compare(grass, dirt, true, false, true));
        assertEquals(MismatchType.CORRECT, comparator.compare(dirt, grass, true, false, true));
        assertEquals(MismatchType.WRONG_BLOCK, comparator.compare(grass, path, true, true, true));
    }

    @Test
    void transferKeepsMatchingProperties() {
        TestBootstrap.ensure();
        BlockState stairs = Blocks.OAK_STAIRS.defaultBlockState().setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.SOUTH);
        BlockState moved = StateComparator.transferProperties(stairs, Blocks.BRICK_STAIRS);
        assertEquals(Blocks.BRICK_STAIRS, moved.getBlock());
        assertEquals(Direction.SOUTH, moved.getValue(BlockStateProperties.HORIZONTAL_FACING));
        assertEquals(Blocks.STONE.defaultBlockState(), StateComparator.transferProperties(stairs, Blocks.STONE));
    }
}
