package org.lts.litematicaplus.verify;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.lts.litematicaplus.TestBootstrap;
import org.lts.litematicaplus.world.GhostSection;

class SectionVerdictTest {
    @Test
    void enclosedBlocksAreHiddenAndExposedOnesAreNot() {
        TestBootstrap.ensure();
        BlockState[] world = new BlockState[GhostSection.VOLUME];
        java.util.Arrays.fill(world, Blocks.STONE.defaultBlockState());
        int center = 8 | 8 << 4 | 8 << 8;
        assertTrue(SectionVerdict.hidden(world, center));
        assertTrue(SectionVerdict.hidden(world, 0));
        world[center + 1] = Blocks.AIR.defaultBlockState();
        assertFalse(SectionVerdict.hidden(world, center));
        world[center + 1] = Blocks.GLASS.defaultBlockState();
        assertFalse(SectionVerdict.hidden(world, center));
        long[] border = new long[SectionVerdict.BORDER_LONGS];
        assertFalse(SectionVerdict.hidden(world, border, 0));
        java.util.Arrays.fill(border, -1L);
        assertTrue(SectionVerdict.hidden(world, border, 0));
        int top = 3 | 5 << 4 | 15 << 8;
        int bit = 3 + 5 * 16;
        border[SectionVerdict.FACE_UP * 4 + (bit >> 6)] &= ~(1L << (bit & 63));
        assertFalse(SectionVerdict.hidden(world, border, top));
        assertTrue(SectionVerdict.hidden(world, border, top + 1));
    }

    private static final BlockState AIR = Blocks.AIR.defaultBlockState();
    private static final BlockState STONE = Blocks.STONE.defaultBlockState();

    @BeforeAll
    static void bootstrap() {
        TestBootstrap.ensure();
    }

    private static int id(BlockState state) {
        return Block.getId(state);
    }

    @Test
    void coveringNeighboursHideOverlayFacesWhileOnlyOpaqueOnesHideBlocks() {
        assertTrue(SectionVerdict.coversState(null));
        assertTrue(SectionVerdict.coversState(STONE));
        assertTrue(SectionVerdict.coversState(Blocks.WATER.defaultBlockState()));
        assertTrue(SectionVerdict.coversState(Blocks.ICE.defaultBlockState()));
        assertTrue(SectionVerdict.coversState(Blocks.GLASS.defaultBlockState()));
        assertTrue(SectionVerdict.coversState(Blocks.OAK_LEAVES.defaultBlockState()));
        assertFalse(SectionVerdict.coversState(AIR));
        assertFalse(SectionVerdict.coversState(Blocks.SNOW.defaultBlockState()));
        assertFalse(SectionVerdict.coversState(Blocks.OAK_SLAB.defaultBlockState()));
        long[] border = new long[SectionVerdict.BORDER_LONGS];
        SectionVerdict.markBorder(border, SectionVerdict.FACE_UP, 2, 3, Blocks.WATER.defaultBlockState());
        SectionVerdict.markBorder(border, SectionVerdict.FACE_UP, 4, 3, STONE);
        SectionVerdict.markBorder(border, SectionVerdict.FACE_UP, 6, 3, null);
        assertFalse(SectionVerdict.borderSolid(border, SectionVerdict.FACE_UP, 2, 3));
        assertTrue(SectionVerdict.borderCovers(border, SectionVerdict.FACE_UP, 2, 3));
        assertTrue(SectionVerdict.borderSolid(border, SectionVerdict.FACE_UP, 4, 3));
        assertTrue(SectionVerdict.borderCovers(border, SectionVerdict.FACE_UP, 4, 3));
        assertTrue(SectionVerdict.borderSolid(border, SectionVerdict.FACE_UP, 6, 3));
        assertTrue(SectionVerdict.borderCovers(border, SectionVerdict.FACE_UP, 6, 3));
        assertFalse(SectionVerdict.borderCovers(border, SectionVerdict.FACE_UP, 8, 3));
        SectionVerdict.closeBorderFace(border, SectionVerdict.FACE_DOWN);
        assertTrue(SectionVerdict.borderSolid(border, SectionVerdict.FACE_DOWN, 15, 15));
        assertTrue(SectionVerdict.borderCovers(border, SectionVerdict.FACE_DOWN, 15, 15));
        BlockState[] world = new BlockState[GhostSection.VOLUME];
        Arrays.fill(world, AIR);
        world[GhostSection.index(5, 5, 5)] = Blocks.WATER.defaultBlockState();
        assertTrue(SectionVerdict.covers(world, border, 5, 5, 5));
        assertFalse(SectionVerdict.occludes(world, border, 5, 5, 5));
        assertTrue(SectionVerdict.covers(world, border, 2, 16, 3));
        assertFalse(SectionVerdict.covers(world, border, 8, 16, 3));
        assertTrue(SectionVerdict.covers(world, border, 8, -1, 3));
    }

    @Test
    void onlyOpaqueFullBlocksHideTheirNeighbours() {
        assertTrue(SectionVerdict.conceals(null));
        assertTrue(SectionVerdict.conceals(STONE));
        assertTrue(SectionVerdict.conceals(Blocks.SNOW_BLOCK.defaultBlockState()));
        assertFalse(SectionVerdict.conceals(AIR));
        assertFalse(SectionVerdict.conceals(Blocks.GLASS.defaultBlockState()));
        assertFalse(SectionVerdict.conceals(Blocks.ICE.defaultBlockState()));
        assertFalse(SectionVerdict.conceals(Blocks.WATER.defaultBlockState()));
        assertFalse(SectionVerdict.conceals(Blocks.SNOW.defaultBlockState()));
    }

    @Test
    void unknownFacesCanBeFilledFromAnotherBorder() {
        long[] live = new long[SectionVerdict.BORDER_LONGS];
        SectionVerdict.closeBorderFace(live, SectionVerdict.FACE_EAST);
        long[] cached = new long[SectionVerdict.BORDER_LONGS];
        SectionVerdict.markBorder(cached, SectionVerdict.FACE_EAST, 3, 4, STONE);
        SectionVerdict.markBorder(cached, SectionVerdict.FACE_WEST, 3, 4, STONE);
        SectionVerdict.copyBorderFace(cached, live, SectionVerdict.FACE_EAST);
        assertTrue(SectionVerdict.borderSolid(live, SectionVerdict.FACE_EAST, 3, 4));
        assertFalse(SectionVerdict.borderSolid(live, SectionVerdict.FACE_EAST, 5, 4));
        assertFalse(SectionVerdict.borderCovers(live, SectionVerdict.FACE_EAST, 5, 4));
        assertFalse(SectionVerdict.borderSolid(live, SectionVerdict.FACE_WEST, 3, 4));
    }

    @Test
    void comparatorClassifiesEveryCase() {
        StateComparator comparator = StateComparator.get();
        BlockState north = Blocks.OAK_STAIRS.defaultBlockState().setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.NORTH);
        BlockState south = north.setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.SOUTH);
        assertEquals(MismatchType.NONE, comparator.compare(id(AIR), id(Blocks.CAVE_AIR.defaultBlockState()), false));
        assertEquals(MismatchType.MISSING, comparator.compare(id(STONE), id(AIR), false));
        assertEquals(MismatchType.EXTRA, comparator.compare(id(AIR), id(STONE), false));
        assertEquals(MismatchType.CORRECT, comparator.compare(id(STONE), id(STONE), false));
        assertEquals(MismatchType.WRONG_BLOCK, comparator.compare(id(STONE), id(Blocks.DIRT.defaultBlockState()), true));
        assertEquals(MismatchType.WRONG_STATE, comparator.compare(id(north), id(south), true));
    }

    @Test
    void lenientModeIgnoresVolatilePropertiesOnly() {
        StateComparator comparator = StateComparator.get();
        BlockState natural = Blocks.OAK_LEAVES.defaultBlockState().setValue(BlockStateProperties.DISTANCE, 3);
        BlockState placed = Blocks.OAK_LEAVES.defaultBlockState().setValue(BlockStateProperties.PERSISTENT, true);
        assertEquals(MismatchType.CORRECT, comparator.compare(id(natural), id(placed), true));
        assertEquals(MismatchType.WRONG_STATE, comparator.compare(id(natural), id(placed), false));
        BlockState lit = Blocks.REDSTONE_LAMP.defaultBlockState().setValue(BlockStateProperties.LIT, true);
        assertEquals(MismatchType.CORRECT, comparator.compare(id(lit), id(Blocks.REDSTONE_LAMP.defaultBlockState()), true));
        BlockState dry = Blocks.OAK_SLAB.defaultBlockState();
        BlockState wet = dry.setValue(BlockStateProperties.WATERLOGGED, true);
        assertEquals(MismatchType.WRONG_STATE, comparator.compare(id(dry), id(wet), true));
    }

    @Test
    void wrongStateBlocksAreCountedSeparatelyFromCorrectOnes() {
        BlockState north = Blocks.OAK_STAIRS.defaultBlockState().setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.NORTH);
        BlockState south = north.setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.SOUTH);
        int[] globalIds = {id(AIR), id(north)};
        boolean[] solid = {false, true};
        GhostSection section = new GhostSection();
        section.set(GhostSection.index(0, 0, 0), (short) 1);
        section.set(GhostSection.index(1, 0, 0), (short) 1);
        section.set(GhostSection.index(2, 0, 0), (short) 1);
        section.recount(solid);
        BlockState[] world = new BlockState[GhostSection.VOLUME];
        Arrays.fill(world, AIR);
        world[GhostSection.index(0, 0, 0)] = north;
        world[GhostSection.index(1, 0, 0)] = south;
        SectionVerdict verdict = SectionVerdict.compute(section, globalIds, solid, world, true);
        assertEquals(1, verdict.correctFor(1));
        assertEquals(1, verdict.restateIndices().length);
        assertEquals(1, verdict.restateIndices()[0]);
        assertEquals(1, verdict.restateCounts()[0]);
        assertEquals(1, verdict.count(MismatchType.MISSING));
    }

    @Test
    void verdictCountsCategoriesAndCorrectBlocksPerPaletteEntry() {
        BlockState[] palette = {AIR, STONE, Blocks.GLASS.defaultBlockState()};
        int[] globalIds = {id(palette[0]), id(palette[1]), id(palette[2])};
        boolean[] solid = {false, true, true};
        GhostSection section = new GhostSection();
        section.set(GhostSection.index(0, 0, 0), (short) 1);
        section.set(GhostSection.index(1, 0, 0), (short) 1);
        section.set(GhostSection.index(2, 0, 0), (short) 2);
        section.set(GhostSection.index(3, 0, 0), (short) 0);
        section.set(GhostSection.index(4, 0, 0), (short) 0);
        section.recount(solid);
        BlockState[] world = new BlockState[GhostSection.VOLUME];
        Arrays.fill(world, AIR);
        world[GhostSection.index(0, 0, 0)] = STONE;
        world[GhostSection.index(2, 0, 0)] = Blocks.DIRT.defaultBlockState();
        world[GhostSection.index(3, 0, 0)] = STONE;
        world[GhostSection.index(9, 9, 9)] = STONE;
        SectionVerdict verdict = SectionVerdict.compute(section, globalIds, solid, world, true);
        assertTrue(verdict.worldKnown());
        assertEquals(1, verdict.count(MismatchType.CORRECT));
        assertEquals(1, verdict.count(MismatchType.MISSING));
        assertEquals(1, verdict.count(MismatchType.WRONG_BLOCK));
        assertEquals(1, verdict.count(MismatchType.EXTRA));
        assertEquals(3, verdict.errors());
        assertEquals(MismatchType.NONE, verdict.category(GhostSection.index(9, 9, 9)));
        assertEquals(MismatchType.NONE, verdict.category(GhostSection.index(4, 0, 0)));
        assertEquals(1, verdict.correctFor(1));
        assertEquals(0, verdict.correctFor(2));
    }

    @Test
    void unloadedWorldMarksSolidBlocksUnknownWithoutErrors() {
        int[] globalIds = {id(AIR), id(STONE)};
        boolean[] solid = {false, true};
        GhostSection section = new GhostSection();
        section.set(5, (short) 1);
        section.set(6, (short) 0);
        section.recount(solid);
        assertEquals(1, section.solidCount());
        assertEquals(2, section.insideCount());
        SectionVerdict verdict = SectionVerdict.compute(section, globalIds, solid, null, true);
        assertFalse(verdict.worldKnown());
        assertEquals(1, verdict.count(MismatchType.UNKNOWN));
        assertEquals(0, verdict.errors());
    }
}
