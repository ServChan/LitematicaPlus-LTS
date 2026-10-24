package org.lts.litematicaplus.schematic;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.lts.litematicaplus.TestBootstrap;
import org.lts.litematicaplus.placement.PlacementGrid;
import org.lts.litematicaplus.placement.PlacementTransform;
import org.lts.litematicaplus.placement.RegionPlacement;

class EmbeddedPlacementTest {
    @BeforeAll
    static void bootstrap() {
        TestBootstrap.ensure();
    }

    private static Schematic sample(EmbeddedPlacement placement) {
        RegionBuilder main = new RegionBuilder("Main", 0, 0, 0, 2, 2, 2);
        main.set(0, 0, 0, Blocks.STONE.defaultBlockState());
        main.set(1, 1, 1, Blocks.GLASS.defaultBlockState());
        RegionBuilder tower = new RegionBuilder("Tower", 3, 0, 0, 1, 3, 1);
        tower.set(0, 2, 0, Blocks.OAK_PLANKS.defaultBlockState());
        Map<String, SchematicRegion> regions = new LinkedHashMap<>();
        regions.put("Main", main.build());
        regions.put("Tower", tower.build());
        SchematicMetadata metadata = new SchematicMetadata();
        metadata.name = "Embedded";
        metadata.placement = placement;
        return new Schematic(metadata, regions);
    }

    private static EmbeddedPlacement full() {
        return new EmbeddedPlacement(-120, 64, 3000, Rotation.CLOCKWISE_90, Mirror.FRONT_BACK, "minecraft:the_nether",
                "play.example.net", Map.of("Tower", new RegionPlacement(2, -1, 4, Rotation.CLOCKWISE_180, Mirror.LEFT_RIGHT)),
                Set.of("Main"), new PlacementGrid(1, 2, 0, 0, 3, 0, 16, 0, 20),
                Map.of("minecraft:stone", "minecraft:deepslate"), 2, 3, 1);
    }

    @Test
    void nbtRoundTripKeepsEveryField() {
        EmbeddedPlacement placement = full();
        assertEquals(placement, EmbeddedPlacement.read(placement.write()));
        EmbeddedPlacement simple = EmbeddedPlacement.at(1, 2, 3, Rotation.NONE, Mirror.NONE, "", "");
        CompoundTag written = simple.write();
        assertFalse(written.contains("Regions"));
        assertFalse(written.contains("Grid"));
        assertFalse(written.contains("Dimension"));
        assertEquals(simple, EmbeddedPlacement.read(written));
        assertTrue(full().hasWorkSettings());
        assertFalse(simple.hasWorkSettings());
    }

    @Test
    void readingIsForgiving() {
        CompoundTag tag = new CompoundTag();
        assertNull(EmbeddedPlacement.read(tag));
        CompoundTag origin = new CompoundTag();
        origin.putInt("x", 5);
        origin.putInt("y", 99999999);
        tag.put("Origin", origin);
        tag.putString("Rotation", "SIDEWAYS");
        tag.putInt("Version", EmbeddedPlacement.VERSION + 5);
        EmbeddedPlacement read = EmbeddedPlacement.read(tag);
        assertNotNull(read);
        assertEquals(5, read.x());
        assertEquals(30000000, read.y());
        assertEquals(0, read.z());
        assertEquals(Rotation.NONE, read.rotation());
        assertTrue(read.matchesDimension("minecraft:overworld"));
        assertTrue(read.matchesWorld("anything"));
        assertFalse(full().matchesDimension("minecraft:overworld"));
        assertTrue(full().matchesWorld("Play.Example.net"));
        assertFalse(full().matchesWorld("other.example.net"));
    }

    @Test
    void litematicFileKeepsPlacement(@TempDir Path directory) throws IOException {
        Path file = directory.resolve("embedded.litematic");
        SchematicIo.write(file, sample(full()));
        assertEquals(full(), SchematicIo.readMetadata(file).placement);
        assertEquals(full(), SchematicIo.read(file).metadata().placement);
        Path plain = directory.resolve("plain.litematic");
        SchematicIo.write(plain, sample(null));
        assertNull(SchematicIo.readMetadata(plain).placement);
        CompoundTag root = NbtIo.readCompressed(plain, NbtAccounter.unlimitedHeap());
        assertFalse(root.getCompoundOrEmpty("Metadata").contains(EmbeddedPlacement.CONTAINER_KEY));
    }

    @Test
    void embeddingPatchesTheFileWithoutLosingOtherData(@TempDir Path directory) throws IOException {
        Path file = directory.resolve("patched.litematic");
        SchematicIo.write(file, sample(null));
        CompoundTag root = NbtIo.readCompressed(file, NbtAccounter.unlimitedHeap());
        root.getCompoundOrEmpty("Metadata").putIntArray("PreviewImageData", new int[] {1, 2, 3});
        root.putString("ForeignKey", "kept");
        NbtIo.writeCompressed(root, file);
        SchematicIo.embedPlacement(file, full());
        CompoundTag patched = NbtIo.readCompressed(file, NbtAccounter.unlimitedHeap());
        assertEquals("kept", patched.getStringOr("ForeignKey", ""));
        assertArrayEquals(new int[] {1, 2, 3},
                patched.getCompoundOrEmpty("Metadata").getIntArray("PreviewImageData").orElseThrow());
        assertEquals(full(), SchematicIo.readMetadata(file).placement);
        assertEquals(3L, SchematicIo.read(file).metadata().totalBlocks);
        SchematicIo.embedPlacement(file, null);
        assertNull(SchematicIo.readMetadata(file).placement);
        CompoundTag cleared = NbtIo.readCompressed(file, NbtAccounter.unlimitedHeap());
        assertFalse(cleared.getCompoundOrEmpty("Metadata").contains(EmbeddedPlacement.CONTAINER_KEY));
    }

    @Test
    void spongeFilesCarryPlacement(@TempDir Path directory) throws IOException {
        Path source = directory.resolve("source.litematic");
        SchematicIo.write(source, sample(full()));
        Path sponge = SchematicIo.export(source, SchematicExport.Format.SPONGE);
        EmbeddedPlacement merged = full().mergedFrom(0, 0, 0);
        assertEquals(full().x(), merged.x());
        assertEquals(full().grid(), merged.grid());
        assertEquals(full().replacements(), merged.replacements());
        assertTrue(merged.regions().isEmpty());
        assertEquals(merged, SchematicIo.readMetadata(sponge).placement);
        assertEquals(merged, SchematicIo.read(sponge).metadata().placement);
        EmbeddedPlacement moved = EmbeddedPlacement.at(7, 8, 9, Rotation.COUNTERCLOCKWISE_90, Mirror.NONE, "", "");
        SchematicIo.embedPlacement(sponge, moved);
        assertEquals(moved, SchematicIo.readMetadata(sponge).placement);
    }

    @Test
    void spongeExportMovesOriginToTheMergedCorner(@TempDir Path directory) throws IOException {
        RegionBuilder region = new RegionBuilder("Offset", 2, 1, 3, 2, 1, 1);
        region.set(0, 0, 0, Blocks.STONE.defaultBlockState());
        region.set(1, 0, 0, Blocks.GLASS.defaultBlockState());
        SchematicMetadata metadata = new SchematicMetadata();
        metadata.placement = new EmbeddedPlacement(10, 64, 10, Rotation.CLOCKWISE_90, Mirror.NONE, "", "",
                Map.of("Offset", new RegionPlacement(1, 0, 0, Rotation.NONE, Mirror.NONE)), Set.of("Missing"),
                PlacementGrid.NONE, Map.of(), 1, 1, 1);
        Path source = directory.resolve("offset.litematic");
        SchematicIo.write(source, new Schematic(metadata, Map.of("Offset", region.build())));
        Path sponge = SchematicIo.export(source, SchematicExport.Format.SPONGE);
        Schematic exported = SchematicIo.read(sponge);
        EmbeddedPlacement moved = exported.metadata().placement;
        assertEquals(0, exported.minX());
        assertEquals(0, exported.minY());
        assertEquals(0, exported.minZ());
        PlacementTransform before = new PlacementTransform(10, 64, 10, Rotation.CLOCKWISE_90, Mirror.NONE);
        PlacementTransform after = new PlacementTransform(moved.x(), moved.y(), moved.z(), moved.rotation(), moved.mirror());
        for (int localX = 2; localX <= 3; localX++) {
            assertEquals(before.worldX(localX, 3), after.worldX(localX - 2, 0));
            assertEquals(before.worldZ(localX, 3), after.worldZ(localX - 2, 0));
        }
        assertEquals(before.worldY(1), after.worldY(0));
        assertTrue(moved.regions().isEmpty());
        assertTrue(moved.disabledRegions().isEmpty());
    }

    @Test
    void otherFormatsAreRefused(@TempDir Path directory) throws IOException {
        Path source = directory.resolve("source.litematic");
        SchematicIo.write(source, sample(null));
        Path structure = SchematicIo.export(source, SchematicExport.Format.STRUCTURE);
        assertFalse(SchematicIo.canEmbedPlacement(structure));
        byte[] before = Files.readAllBytes(structure);
        assertThrows(IOException.class, () -> SchematicIo.embedPlacement(structure, full()));
        assertArrayEquals(before, Files.readAllBytes(structure));
    }
}
