package org.lts.litematicaplus.schematic;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.lts.litematicaplus.TestBootstrap;

class SchematicExportTest {
    private static BlockState stairs() {
        return Blocks.OAK_STAIRS.defaultBlockState().setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.EAST);
    }

    @BeforeAll
    static void bootstrap() {
        TestBootstrap.ensure();
    }

    private static Schematic sample() {
        RegionBuilder main = new RegionBuilder("Main", 0, 0, 0, 3, 2, 2);
        main.set(0, 0, 0, Blocks.STONE.defaultBlockState());
        main.set(2, 1, 1, stairs());
        main.set(1, 0, 1, Blocks.CHEST.defaultBlockState());
        CompoundTag chest = new CompoundTag();
        chest.putString("id", "minecraft:chest");
        chest.putString("CustomName", "\"loot\"");
        main.blockEntity(1, 0, 1, chest);
        RegionBuilder tower = new RegionBuilder("Tower", 4, 0, 0, 1, 3, 1);
        for (int y = 0; y < 3; y++) {
            tower.set(0, y, 0, Blocks.GLASS.defaultBlockState());
        }
        Map<String, SchematicRegion> regions = new LinkedHashMap<>();
        regions.put("Main", main.build());
        regions.put("Tower", tower.build());
        SchematicMetadata metadata = new SchematicMetadata();
        metadata.name = "Sample";
        metadata.author = "LTS";
        return new Schematic(metadata, regions);
    }

    private static void assertMerged(SchematicRegion region) {
        assertEquals(5, region.absX());
        assertEquals(3, region.absY());
        assertEquals(2, region.absZ());
        assertEquals(Blocks.STONE.defaultBlockState(), region.state(0, 0, 0));
        assertEquals(stairs(), region.state(2, 1, 1));
        assertTrue(region.state(1, 0, 1).is(Blocks.CHEST));
        assertEquals(Blocks.GLASS.defaultBlockState(), region.state(4, 2, 0));
        CompoundTag chest = region.blockEntities().get(SchematicRegion.localKey(1, 0, 1));
        assertNotNull(chest);
        assertEquals("minecraft:chest", chest.getStringOr("id", ""));
    }

    @Test
    void spongeExportReadsBack() throws IOException {
        Schematic copy = SpongeCodec.read(SchematicExport.sponge(sample()), "Sample");
        assertMerged(copy.regions().get("Sample"));
        assertEquals(6L, copy.metadata().totalBlocks);
        assertEquals("LTS", copy.metadata().author);
    }

    @Test
    void structureExportReadsBackAndLeavesGapsEmpty() throws IOException {
        Schematic copy = VanillaStructureCodec.read(SchematicExport.structure(sample()), "Sample");
        SchematicRegion region = copy.regions().get("Sample");
        assertMerged(region);
        assertTrue(region.state(3, 0, 0).is(Blocks.STRUCTURE_VOID));
        assertTrue(region.state(1, 1, 0).isAir());
        assertEquals(6L, copy.metadata().totalBlocks);
    }

    @Test
    void exportWritesASiblingWithoutOverwriting(@TempDir Path directory) throws IOException {
        Path source = directory.resolve("house.litematic");
        SchematicIo.write(source, sample());
        Path first = SchematicIo.export(source, SchematicExport.Format.SPONGE);
        Path second = SchematicIo.export(source, SchematicExport.Format.SPONGE);
        assertEquals("house.schem", first.getFileName().toString());
        assertEquals("house_2.schem", second.getFileName().toString());
        assertTrue(Files.isRegularFile(second));
        assertMerged(SchematicIo.read(first).regions().values().iterator().next());
        Path structure = SchematicIo.export(source, SchematicExport.Format.STRUCTURE);
        assertMerged(SchematicIo.read(structure).regions().values().iterator().next());
    }
}
