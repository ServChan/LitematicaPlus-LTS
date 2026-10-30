package org.lts.litematicaplus.tool;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class LayerAdvanceTest {
    @Test
    void staysOnAnUnfinishedLayer() {
        assertEquals(0, LayerAdvance.nextStep(new long[]{3, 4, 0, 4}, 1));
    }

    @Test
    void skipsEmptyAndFinishedLayers() {
        assertEquals(3, LayerAdvance.nextStep(new long[]{4, 4, 0, 0, 2, 2, 0, 9}, 3));
    }

    @Test
    void reportsThatNothingIsLeft() {
        assertEquals(-1, LayerAdvance.nextStep(new long[]{4, 4, 0, 0, 2, 2}, 2));
    }
}
