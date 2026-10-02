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

    @Test
    void clipLimitsEveryModeAndSurvivesLayerChanges() {
        LayerRange.Clip clip = new LayerRange.Clip(true, true, 10, 0, true, -4, 4);
        assertEquals(0, clip.minX());
        assertEquals(10, clip.maxX());
        LayerRange all = LayerRange.ALL.withClip(clip);
        assertTrue(all.isAll());
        assertFalse(all.unrestricted());
        assertTrue(all.contains(5, 300, 0));
        assertFalse(all.contains(11, 0, 0));
        assertFalse(all.contains(5, 0, 5));
        assertTrue(all.intersects(-16, 0, -16, 15, 15, -1));
        assertFalse(all.intersects(16, 0, 0, 31, 15, 15));
        assertTrue(all.encloses(0, 0, -4, 10, 99, 4));
        assertFalse(all.encloses(0, 0, -4, 11, 99, 4));
        LayerRange single = all.withMode(LayerRange.Mode.SINGLE).shifted(64);
        assertEquals(clip, single.clip());
        assertTrue(single.contains(3, 64, 1));
        assertFalse(single.contains(3, 65, 1));
        assertFalse(single.contains(30, 64, 1));
    }

    @Test
    void disabledClipOrNoAxisRestrictsNothing() {
        assertTrue(LayerRange.ALL.withClip(new LayerRange.Clip(false, true, 0, 1, true, 0, 1)).unrestricted());
        assertTrue(LayerRange.ALL.withClip(new LayerRange.Clip(true, false, 0, 1, false, 0, 1)).unrestricted());
        LayerRange zOnly = LayerRange.ALL.withClip(new LayerRange.Clip(true, false, 0, 0, true, 2, 3));
        assertTrue(zOnly.contains(-999, 0, 2));
        assertFalse(zOnly.contains(0, 0, 4));
    }
}
