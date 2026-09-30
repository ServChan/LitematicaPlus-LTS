package org.lts.litematicaplus.world;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import net.minecraft.core.Direction;
import org.junit.jupiter.api.Test;

class LayerRangeTest {
    @Test
    void allContainsEverything() {
        assertTrue(LayerRange.ALL.contains(1, -900, 5));
        assertTrue(LayerRange.ALL.intersects(0, 0, 0, 15, 15, 15));
    }

    @Test
    void singleLayerFollowsItsAxis() {
        LayerRange range = new LayerRange(LayerRange.Mode.SINGLE, Direction.Axis.Z, 12, 0, 0);
        assertTrue(range.contains(99, 99, 12));
        assertFalse(range.contains(12, 12, 13));
        assertTrue(range.intersects(0, 0, 0, 15, 15, 15));
        assertFalse(range.intersects(0, 0, 16, 15, 15, 31));
    }

    @Test
    void belowAndAboveAreInclusive() {
        LayerRange below = new LayerRange(LayerRange.Mode.ALL_BELOW, Direction.Axis.Y, 64, 0, 0);
        assertTrue(below.contains(0, 64, 0));
        assertFalse(below.contains(0, 65, 0));
        LayerRange above = below.withMode(LayerRange.Mode.ALL_ABOVE);
        assertTrue(above.contains(0, 64, 0));
        assertFalse(above.contains(0, 63, 0));
    }

    @Test
    void rangeSwapsReversedBoundsAndShifts() {
        LayerRange range = new LayerRange(LayerRange.Mode.RANGE, Direction.Axis.Y, 0, 10, 4);
        assertEquals(4, range.min());
        assertEquals(10, range.max());
        LayerRange shifted = range.shifted(3);
        assertTrue(shifted.contains(0, 13, 0));
        assertFalse(shifted.contains(0, 6, 0));
    }
}
