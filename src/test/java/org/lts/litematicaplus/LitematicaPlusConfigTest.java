package org.lts.litematicaplus;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class LitematicaPlusConfigTest {
    @Test
    void missingFileYieldsDefaults(@TempDir Path directory) {
        LitematicaPlusConfig config = LitematicaPlusConfig.load(directory.resolve("absent.json"));
        assertTrue(config.renderEnabled);
        assertEquals("minecraft:stick", config.toolItem);
        assertEquals("schematics", config.schematicsDirectory);
        assertEquals(LitematicaPlusConfig.PasteReplace.ALL, config.pasteReplace);
    }

    @Test
    void outOfRangeValuesAreClamped(@TempDir Path directory) throws IOException {
        Path file = directory.resolve("config.json");
        Files.writeString(file, """
                {"ghostAlpha": 9.5, "overlayFillAlpha": -3, "renderDistance": 5, "workerThreads": 900,
                 "easyPlaceDelayTicks": -4, "pasteCommandsPerTick": 0, "hudMaterialLines": 99,
                 "pasteReplace": null, "toolItem": "  Stick ", "schematicsDirectory": "../outside"}
                """);
        LitematicaPlusConfig config = LitematicaPlusConfig.load(file);
        assertEquals(1.0F, config.ghostAlpha);
        assertEquals(0.0F, config.overlayFillAlpha);
        assertEquals(32, config.renderDistance);
        assertEquals(16, config.workerThreads);
        assertEquals(0, config.easyPlaceDelayTicks);
        assertEquals(1, config.pasteCommandsPerTick);
        assertEquals(16, config.hudMaterialLines);
        assertEquals(LitematicaPlusConfig.PasteReplace.ALL, config.pasteReplace);
        assertEquals("minecraft:stick", config.toolItem);
        assertEquals("schematics", config.schematicsDirectory);
    }

    @Test
    void invalidToolItemFallsBackToTheStick(@TempDir Path directory) throws IOException {
        Path file = directory.resolve("config.json");
        Files.writeString(file, "{\"toolItem\": \"not an id!\"}");
        assertEquals("minecraft:stick", LitematicaPlusConfig.load(file).toolItem);
    }

    @Test
    void corruptPrimaryFallsBackToBackup(@TempDir Path directory) throws IOException {
        Path file = directory.resolve("config.json");
        LitematicaPlusConfig custom = new LitematicaPlusConfig();
        custom.renderDistance = 320;
        LitematicaPlusConfig.writeAtomically(file, custom, true);
        custom.renderDistance = 480;
        LitematicaPlusConfig.writeAtomically(file, custom, true);
        assertTrue(Files.isRegularFile(file.resolveSibling("config.json.bak")));
        Files.writeString(file, "{ broken");
        assertEquals(320, LitematicaPlusConfig.load(file).renderDistance);
    }

    @Test
    void rightSideHudMigratesToTheRightEdge(@TempDir Path directory) throws IOException {
        Path file = directory.resolve("config.json");
        Files.writeString(file, "{\"configVersion\": 4, \"hudRightSide\": true}");
        LitematicaPlusConfig config = LitematicaPlusConfig.load(file);
        assertEquals(1.0F, config.hudX);
        assertEquals(0.0F, config.hudY);
        assertFalse(config.hudRightSide);
    }

    @Test
    void hudPositionAndLockedSlotsStayInRange(@TempDir Path directory) throws IOException {
        Path file = directory.resolve("config.json");
        Files.writeString(file, "{\"hudX\": 3.5, \"hudY\": -1, \"lockedHotbarSlots\": -1}");
        LitematicaPlusConfig config = LitematicaPlusConfig.load(file);
        assertEquals(1.0F, config.hudX);
        assertEquals(0.0F, config.hudY);
        assertEquals(0x1FF, config.lockedHotbarSlots);
        assertTrue(config.hotbarSlotLocked(8));
        assertFalse(config.hotbarSlotLocked(9));
    }

    @Test
    void workerThreadsResolveToAtLeastOne() {
        LitematicaPlusConfig config = new LitematicaPlusConfig();
        assertTrue(config.resolvedWorkerThreads() >= 1);
        config.workerThreads = 3;
        assertEquals(3, config.resolvedWorkerThreads());
        assertFalse(config.easyPlaceEnabled);
    }
}
