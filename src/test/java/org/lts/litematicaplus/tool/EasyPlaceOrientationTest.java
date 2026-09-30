package org.lts.litematicaplus.tool;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraft.world.level.block.state.properties.RailShape;
import net.minecraft.world.level.block.state.properties.SlabType;
import net.minecraft.world.level.block.state.properties.StairsShape;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.lts.litematicaplus.TestBootstrap;

class EasyPlaceOrientationTest {
    @BeforeAll
    static void bootstrap() {
        TestBootstrap.ensure();
    }

    @Test
    void facingAndHalfMustMatch() {
        BlockState target = Blocks.OAK_STAIRS.defaultBlockState()
                .setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.WEST)
                .setValue(BlockStateProperties.HALF, Half.TOP);
        assertTrue(EasyPlace.orientationMatches(target, target));
        assertFalse(EasyPlace.orientationMatches(target.setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.EAST), target));
        assertFalse(EasyPlace.orientationMatches(target.setValue(BlockStateProperties.HALF, Half.BOTTOM), target));
    }

    @Test
    void neighbourDrivenPropertiesAreIgnored() {
        BlockState target = Blocks.OAK_STAIRS.defaultBlockState().setValue(BlockStateProperties.STAIRS_SHAPE, StairsShape.INNER_LEFT);
        assertTrue(EasyPlace.orientationMatches(Blocks.OAK_STAIRS.defaultBlockState(), target));
        BlockState fence = Blocks.OAK_FENCE.defaultBlockState().setValue(BlockStateProperties.NORTH, true);
        assertTrue(EasyPlace.orientationMatches(Blocks.OAK_FENCE.defaultBlockState(), fence));
    }

    @Test
    void substituteBlocksAreComparedBySharedOrientation() {
        assertTrue(EasyPlace.orientationMatches(Blocks.STONE.defaultBlockState(), Blocks.DIRT.defaultBlockState()));
        BlockState oak = Blocks.OAK_TRAPDOOR.defaultBlockState()
                .setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.SOUTH)
                .setValue(BlockStateProperties.HALF, Half.TOP);
        BlockState spruce = Blocks.SPRUCE_TRAPDOOR.defaultBlockState()
                .setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.SOUTH)
                .setValue(BlockStateProperties.HALF, Half.TOP);
        assertTrue(EasyPlace.orientationMatches(spruce, oak));
        assertFalse(EasyPlace.orientationMatches(spruce.setValue(BlockStateProperties.HALF, Half.BOTTOM), oak));
        assertFalse(EasyPlace.orientationMatches(spruce.setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.WEST), oak));
    }

    @Test
    void curvedRailsAreLeftToTheirNeighbours() {
        BlockState straight = Blocks.RAIL.defaultBlockState().setValue(BlockStateProperties.RAIL_SHAPE, RailShape.EAST_WEST);
        BlockState other = Blocks.RAIL.defaultBlockState().setValue(BlockStateProperties.RAIL_SHAPE, RailShape.NORTH_SOUTH);
        BlockState curve = Blocks.RAIL.defaultBlockState().setValue(BlockStateProperties.RAIL_SHAPE, RailShape.SOUTH_EAST);
        assertFalse(EasyPlace.orientationMatches(other, straight));
        assertTrue(EasyPlace.orientationMatches(other, curve));
    }

    @Test
    void anySlabHalfIsAcceptedAsTheFirstStepOfADoubleSlab() {
        BlockState target = Blocks.OAK_SLAB.defaultBlockState().setValue(BlockStateProperties.SLAB_TYPE, SlabType.DOUBLE);
        BlockState bottom = Blocks.OAK_SLAB.defaultBlockState().setValue(BlockStateProperties.SLAB_TYPE, SlabType.BOTTOM);
        BlockState top = bottom.setValue(BlockStateProperties.SLAB_TYPE, SlabType.TOP);
        assertTrue(EasyPlace.orientationMatches(bottom, target));
        assertFalse(EasyPlace.orientationMatches(bottom, top));
    }

    @Test
    void toolModesCycleInBothDirections() {
        assertEquals(ToolMode.PLACEMENT, ToolMode.AREA_SELECTION.cycle(true));
        assertEquals(ToolMode.DELETE, ToolMode.AREA_SELECTION.cycle(false));
        assertEquals(ToolMode.AREA_SELECTION, ToolMode.DELETE.cycle(true));
    }
}
