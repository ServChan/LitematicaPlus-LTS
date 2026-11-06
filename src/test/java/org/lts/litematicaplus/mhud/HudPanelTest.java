package org.lts.litematicaplus.mhud;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class HudPanelTest {
    @Test
    void countsShowStacksAndShulkerBoxes() {
        assertEquals("12", HudPanel.count(12L, 64, false));
        assertEquals("64", HudPanel.count(64L, 64, false));
        assertEquals("130 (2x64+2)", HudPanel.count(130L, 64, false));
        assertEquals("128 (2x64)", HudPanel.count(128L, 64, false));
        assertEquals("1728 (1.0 SB)", HudPanel.count(1728L, 64, true));
        assertEquals("100", HudPanel.count(100L, 1, true));
        assertEquals("20 (1x16+4)", HudPanel.count(20L, 16, true));
    }
}
