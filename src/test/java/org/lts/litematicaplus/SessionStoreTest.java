package org.lts.litematicaplus;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SessionStoreTest {
    private static SessionStore.ScopeData sample() {
        SessionStore.ScopeData data = new SessionStore.ScopeData();
        SessionStore.PlacementData placement = new SessionStore.PlacementData();
        placement.id = "5b0f9c43-5d5c-4a39-9c39-0a1d1f5d2c11";
        placement.name = "Castle";
        placement.file = "builds/castle.litematic";
        placement.x = 12;
        placement.y = 70;
        placement.z = -8;
        placement.rotation = "CLOCKWISE_90";
        placement.disabledRegions.add("Roof");
        data.placements.add(placement);
        data.selectedPlacement = placement.id;
        data.layer.mode = "SINGLE";
        data.layer.single = 71;
        SessionStore.BoxData box = new SessionStore.BoxData();
        box.name = "Box 1";
        box.a = new int[]{1, 2, 3};
        box.b = null;
        data.area.boxes.add(box);
        return data;
    }

    @Test
    void scopesRoundTripThroughTheFile(@TempDir Path directory) {
        Path file = directory.resolve("session.json");
        SessionStore store = new SessionStore(file);
        store.saveBlocking("server|minecraft:overworld", sample());
        SessionStore reopened = new SessionStore(file);
        reopened.load();
        SessionStore.ScopeData data = reopened.scope("server|minecraft:overworld");
        assertEquals(1, data.placements.size());
        assertEquals("Castle", data.placements.get(0).name);
        assertEquals(-8, data.placements.get(0).z);
        assertEquals("CLOCKWISE_90", data.placements.get(0).rotation);
        assertEquals("Roof", data.placements.get(0).disabledRegions.get(0));
        assertEquals("SINGLE", data.layer.mode);
        assertEquals(71, data.layer.single);
        assertEquals(1, data.area.boxes.size());
        assertEquals(3, data.area.boxes.get(0).a[2]);
        assertTrue(reopened.scope("other|minecraft:the_nether").placements.isEmpty());
    }

    @Test
    void corruptPrimaryIsRecoveredFromBackup(@TempDir Path directory) throws IOException {
        Path file = directory.resolve("session.json");
        SessionStore store = new SessionStore(file);
        store.saveBlocking("a", sample());
        store.saveBlocking("b", sample());
        Files.writeString(file, "{ not json");
        SessionStore reopened = new SessionStore(file);
        reopened.load();
        assertEquals(1, reopened.scope("a").placements.size());
        assertTrue(Files.isRegularFile(file.resolveSibling("session.json.corrupt")));
    }

    @Test
    void newerSchemaIsKeptReadOnly(@TempDir Path directory) throws IOException {
        Path file = directory.resolve("session.json");
        String newer = "{\"schemaVersion\": 99, \"scopes\": {\"a\": {\"placements\": []}}}";
        Files.writeString(file, newer);
        SessionStore store = new SessionStore(file);
        store.load();
        assertTrue(store.readOnly());
        store.saveBlocking("a", sample());
        assertEquals(newer, Files.readString(file));
    }

    @Test
    void invalidRowsAreDroppedOnLoad(@TempDir Path directory) throws IOException {
        Path file = directory.resolve("session.json");
        Files.writeString(file, """
                {"schemaVersion": 1, "scopes": {"a": {
                  "placements": [{"id": "", "file": "x.litematic"}, {"id": "1", "file": ""}, null,
                                 {"id": "ok", "file": "y.litematic", "rotation": null, "disabledRegions": null}],
                  "area": {"boxes": [{"name": "bad", "a": [1, 2]}, {"name": "good", "a": [1, 2, 3], "b": [4, 5, 6]}], "selected": 9},
                  "layer": null}}}
                """);
        SessionStore store = new SessionStore(file);
        store.load();
        SessionStore.ScopeData data = store.scope("a");
        assertEquals(1, data.placements.size());
        assertEquals("NONE", data.placements.get(0).rotation);
        assertTrue(data.placements.get(0).disabledRegions.isEmpty());
        assertEquals(1, data.area.boxes.size());
        assertEquals("good", data.area.boxes.get(0).name);
        assertEquals(0, data.area.selected);
        assertEquals("ALL", data.layer.mode);
        assertFalse(store.readOnly());
    }
}
