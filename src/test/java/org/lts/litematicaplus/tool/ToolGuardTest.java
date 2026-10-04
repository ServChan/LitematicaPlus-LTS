package org.lts.litematicaplus.tool;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import net.minecraft.world.level.block.Blocks;
import org.junit.jupiter.api.Test;
import org.lts.litematicaplus.TestBootstrap;

class ToolGuardTest {
    @Test
    void toolsMayOnlyMakeWhatTheSchematicAsksFor() {
        TestBootstrap.ensure();
        assertFalse(ToolGuard.wanted(Blocks.GRASS_BLOCK, Blocks.GRASS_BLOCK));
        assertFalse(ToolGuard.wanted(Blocks.DIRT, Blocks.GRASS_BLOCK));
        assertFalse(ToolGuard.wanted(Blocks.OAK_LOG, Blocks.OAK_LOG));
        assertTrue(ToolGuard.wanted(Blocks.GRASS_BLOCK, Blocks.DIRT_PATH));
        assertTrue(ToolGuard.wanted(Blocks.DIRT, Blocks.FARMLAND));
        assertTrue(ToolGuard.wanted(Blocks.OAK_LOG, Blocks.STRIPPED_OAK_LOG));
        assertTrue(ToolGuard.wanted(Blocks.COARSE_DIRT, Blocks.DIRT));
        assertTrue(ToolGuard.wanted(Blocks.CAMPFIRE, Blocks.CAMPFIRE));
    }
}
