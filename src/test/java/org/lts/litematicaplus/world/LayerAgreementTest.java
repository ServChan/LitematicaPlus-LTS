package org.lts.litematicaplus.world;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import net.minecraft.core.Direction;
import org.junit.jupiter.api.Test;

class LayerAgreementTest {
    private static LayerRange single(int y) {
        return new LayerRange(LayerRange.Mode.SINGLE, Direction.Axis.Y, y, y, y);
    }

    private static boolean agrees(LayerRange a, LayerRange b, int sectionY) {
        int origin = sectionY << 4;
        return a.agrees(b, -1, origin - 1, -1, 16, origin + 16, 16);
    }

    @Test
    void steppingOneLayerTouchesOnlyNearbySections() {
        assertFalse(agrees(single(32), single(33), 2));
        assertTrue(agrees(single(32), single(33), 0));
        assertTrue(agrees(single(32), single(33), 5));
    }

    @Test
    void borderLayerAlsoTouchesTheNeighbourSection() {
        assertFalse(agrees(single(31), single(32), 1));
        assertFalse(agrees(single(31), single(32), 2));
    }

    @Test
    void belowModeKeepsFullyCoveredSections() {
        LayerRange below = new LayerRange(LayerRange.Mode.ALL_BELOW, Direction.Axis.Y, 64, 0, 0);
        assertTrue(agrees(below, below.shifted(1), 1));
        assertFalse(agrees(below, below.shifted(1), 4));
        assertTrue(agrees(below, below.shifted(1), 8));
    }

    @Test
    void identicalSectionContentIsShared() {
        GhostSection first = new GhostSection();
        GhostSection second = new GhostSection();
        GhostSection third = new GhostSection();
        first.set(5, (short) 1);
        second.set(5, (short) 1);
        third.set(6, (short) 1);
        GhostSection.Interner interner = new GhostSection.Interner();
        interner.share(first);
        interner.share(second);
        interner.share(third);
        assertSame(first.raw(), second.raw());
        assertNotSame(first.raw(), third.raw());
        assertEquals(1, second.get(5));
    }
}
