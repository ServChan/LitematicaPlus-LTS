package org.lts.litematicaplus.schematic;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.lts.litematicaplus.TestBootstrap;
import org.lts.litematicaplus.placement.PlacementTransform;
import org.lts.litematicaplus.world.PlacementVolume;

class SampleLibraryTest {
    @BeforeAll
    static void bootstrap() {
        TestBootstrap.ensure();
    }

    @Test
    void everySampleSchematicLoadsAndRoundTrips() throws IOException {
        String location = System.getProperty("litematicaplus.sampleDir", "");
        assumeTrue(!location.isBlank(), "No sample directory configured");
        Path directory = Path.of(location);
        assumeTrue(Files.isDirectory(directory), "Sample directory is missing");
        List<Path> files;
        try (Stream<Path> stream = Files.walk(directory, 3)) {
            files = stream.filter(Files::isRegularFile).filter(SchematicIo::isSupported).toList();
        }
        assumeTrue(!files.isEmpty(), "Sample directory has no schematics");
        for (Path file : files) {
            long started = System.nanoTime();
            Schematic schematic = SchematicIo.read(file);
            long readMillis = (System.nanoTime() - started) / 1_000_000L;
            long counted = 0L;
            long overlapFreeVolume = 0L;
            for (SchematicRegion region : schematic.regions().values()) {
                counted += region.countNonAir();
                overlapFreeVolume += region.volume();
            }
            SchematicMetadata metadata = schematic.metadata();
            assertTrue(counted > 0L, file + " has no blocks");
            assertEquals(overlapFreeVolume, metadata.totalVolume, file + " volume");
            if (metadata.format.equals("litematic")) {
                assertEquals(metadata.totalBlocks, counted, file + " stored block count");
                SchematicMetadata quick = SchematicIo.readMetadata(file);
                assertEquals(metadata.totalBlocks, quick.totalBlocks, file + " quick metadata");
                assertEquals(metadata.name, quick.name, file + " quick name");
            }
            started = System.nanoTime();
            PlacementVolume volume = PlacementVolume.build(schematic, PlacementTransform.IDENTITY, Set.of());
            long buildMillis = (System.nanoTime() - started) / 1_000_000L;
            assertTrue(volume.totalBlocks() <= counted, file + " volume block count");
            assertTrue(volume.totalBlocks() > 0L, file + " volume is empty");
            Schematic copy = LitematicCodec.read(LitematicCodec.write(schematic));
            long recount = 0L;
            for (SchematicRegion region : copy.regions().values()) {
                recount += region.countNonAir();
            }
            assertEquals(counted, recount, file + " round trip");
            assertEquals(schematic.regions().keySet(), copy.regions().keySet(), file + " region names");
            System.out.println("sample " + file.getFileName() + ": " + counted + " blocks, " + schematic.regions().size()
                    + " regions, data version " + metadata.dataVersion + ", read " + readMillis + " ms, build "
                    + buildMillis + " ms, sections " + volume.sections().size() + ", palette " + volume.palette().length);
        }
    }
}
