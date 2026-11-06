package org.lts.litematicaplus.schematic;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SchematicTrashTest {
    private static final long DAY = 24L * 60L * 60L * 1000L;

    @Test
    void trashedFilesComeBackWhereTheyWere(@TempDir Path directory) throws IOException {
        Path schematics = Files.createDirectories(directory.resolve("schematics/builds"));
        Path file = Files.writeString(schematics.resolve("castle.litematic"), "castle");
        SchematicTrash trash = new SchematicTrash(directory.resolve("trash"));
        SchematicTrash.Item item = trash.trash(file, 1_000_000L);
        assertFalse(Files.exists(file));
        assertEquals(1, trash.items().size());
        assertEquals(SchematicTrash.KEEP_DAYS, item.daysLeft(1_000_000L));
        Files.writeString(file, "new castle");
        Path restored = trash.restore(item, directory.resolve("schematics"));
        assertEquals(schematics.resolve("castle (2).litematic"), restored);
        assertEquals("castle", Files.readString(restored));
        assertTrue(trash.items().isEmpty());
    }

    @Test
    void sameSecondDeletionsDoNotCollide(@TempDir Path directory) throws IOException {
        SchematicTrash trash = new SchematicTrash(directory.resolve("trash"));
        Path a = Files.writeString(directory.resolve("a.litematic"), "a");
        Path b = Files.writeString(directory.resolve("b.litematic"), "b");
        SchematicTrash.Item first = trash.trash(a, 5_000L);
        SchematicTrash.Item second = trash.trash(b, 5_000L);
        assertFalse(first.id.equals(second.id));
        assertEquals(2, new SchematicTrash(directory.resolve("trash")).items().size());
    }

    @Test
    void expiredAndEmptiedItemsAreRemoved(@TempDir Path directory) throws IOException {
        SchematicTrash trash = new SchematicTrash(directory.resolve("trash"));
        trash.trash(Files.writeString(directory.resolve("old.nbt"), "old"), 0L);
        trash.trash(Files.writeString(directory.resolve("fresh.nbt"), "fresh"), 40L * DAY);
        assertEquals(1, trash.purgeExpired(31L * DAY + 1L));
        List<SchematicTrash.Item> left = trash.items();
        assertEquals(1, left.size());
        assertEquals("fresh.nbt", left.getFirst().name);
        assertEquals(1, trash.empty());
        assertTrue(trash.items().isEmpty());
    }

    @Test
    void restoreFallsBackWhenTheFolderIsGone(@TempDir Path directory) throws IOException {
        Path gone = Files.createDirectories(directory.resolve("gone"));
        Path file = Files.writeString(gone.resolve("tower.schem"), "tower");
        SchematicTrash trash = new SchematicTrash(directory.resolve("trash"));
        SchematicTrash.Item item = trash.trash(file, 1L);
        Files.delete(gone);
        Path fallback = directory.resolve("schematics");
        assertEquals(fallback.resolve("tower.schem"), trash.restore(item, fallback));
    }
}
