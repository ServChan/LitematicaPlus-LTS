package org.lts.litematicaplus.schematic;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import javax.imageio.ImageIO;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.DoubleTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.StringTag;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.chunk.PalettedContainer;
import net.minecraft.world.level.chunk.Strategy;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.lts.litematicaplus.TestBootstrap;

class AxiomBlueprintCodecTest {
    @BeforeAll
    static void bootstrap() {
        TestBootstrap.ensure();
    }

    private static BlockState stairs() {
        return Blocks.OAK_STAIRS.defaultBlockState().setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.EAST);
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
        CompoundTag stand = new CompoundTag();
        stand.putString("id", "minecraft:armor_stand");
        ListTag pos = new ListTag();
        pos.add(DoubleTag.valueOf(0.5));
        pos.add(DoubleTag.valueOf(1.0));
        pos.add(DoubleTag.valueOf(0.5));
        stand.put("Pos", pos);
        main.entity(stand);
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

    private static byte[] encode(Schematic schematic) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        AxiomBlueprintCodec.write(out, schematic);
        return out.toByteArray();
    }

    @Test
    void blueprintReadsBackWithGapsLeftAlone() throws IOException {
        byte[] bytes = encode(sample());
        Schematic copy = AxiomBlueprintCodec.read(new ByteArrayInputStream(bytes), "fallback");
        assertEquals("Sample", copy.metadata().name);
        assertEquals("LTS", copy.metadata().author);
        assertEquals("axiom", copy.metadata().format);
        SchematicRegion region = copy.regions().values().iterator().next();
        assertEquals(5, region.absX());
        assertEquals(3, region.absY());
        assertEquals(2, region.absZ());
        assertEquals(Blocks.STONE.defaultBlockState(), region.state(0, 0, 0));
        assertEquals(stairs(), region.state(2, 1, 1));
        assertTrue(region.state(1, 0, 1).is(Blocks.CHEST));
        assertEquals(Blocks.GLASS.defaultBlockState(), region.state(4, 2, 0));
        assertTrue(region.state(1, 1, 0).isAir());
        assertTrue(region.state(3, 0, 0).is(Blocks.STRUCTURE_VOID));
        CompoundTag chest = region.blockEntities().get(SchematicRegion.localKey(1, 0, 1));
        assertNotNull(chest);
        assertEquals("minecraft:chest", chest.getStringOr("id", ""));
        assertFalse(chest.contains("x"));
        assertEquals(1, region.entities().size());
        assertEquals(0.5, region.entities().get(0).getListOrEmpty("Pos").getDoubleOr(0, 0.0));
        assertEquals(6L, copy.metadata().totalBlocks);
    }

    @Test
    void headerDescribesTheBlueprint() throws IOException {
        byte[] bytes = encode(sample());
        AxiomBlueprintCodec.Header header = AxiomBlueprintCodec.readHeader(new ByteArrayInputStream(bytes));
        assertEquals(AxiomBlueprintCodec.HEADER_VERSION, header.version());
        assertEquals("Sample", header.name());
        assertEquals(6, header.blockCount());
        assertTrue(header.containsAir());
    }

    @Test
    void thumbnailIsAReadablePng() throws IOException {
        DataInputStream data = new DataInputStream(new ByteArrayInputStream(encode(sample())));
        data.readInt();
        data.skipNBytes(data.readInt());
        byte[] png = data.readNBytes(data.readInt());
        BufferedImage image = ImageIO.read(new ByteArrayInputStream(png));
        assertNotNull(image);
        assertEquals(40, image.getWidth());
        assertEquals(16, image.getHeight());
        assertEquals(0xFF, image.getRGB(0, 0) >>> 24);
    }

    @Test
    void rejectsFilesWithoutTheMagic() {
        assertThrows(IOException.class, () -> AxiomBlueprintCodec.read(new ByteArrayInputStream(new byte[] {1, 2, 3, 4, 0, 0, 0, 0}), "x"));
    }

    @Test
    void largePalettesSurviveTheGlobalPaletteEncoding() throws IOException {
        List<BlockState> states = new ArrayList<>();
        for (Block block : BuiltInRegistries.BLOCK) {
            BlockState state = block.defaultBlockState();
            if (!state.isAir() && !state.is(Blocks.STRUCTURE_VOID) && !state.hasBlockEntity()) {
                states.add(state);
            }
            if (states.size() == 600) {
                break;
            }
        }
        RegionBuilder builder = new RegionBuilder("Big", 0, 0, 0, 30, 1, 20);
        for (int i = 0; i < states.size(); i++) {
            builder.set(i % 30, 0, i / 30, states.get(i));
        }
        SchematicMetadata metadata = new SchematicMetadata();
        metadata.name = "Big";
        Schematic copy = AxiomBlueprintCodec.read(new ByteArrayInputStream(encode(
                new Schematic(metadata, Map.of("Big", builder.build())))), "Big");
        SchematicRegion region = copy.regions().values().iterator().next();
        for (int i = 0; i < states.size(); i++) {
            assertEquals(states.get(i), region.state(i % 30, 0, i / 30));
        }
    }

    @Test
    void legacyBlueprintsTreatStructureVoidAsEmptyAndTolerateUnknownBlocks() throws IOException {
        PalettedContainer<BlockState> section = new PalettedContainer<>(Blocks.VOID_AIR.defaultBlockState(),
                Strategy.createForBlockStates(Block.BLOCK_STATE_REGISTRY));
        for (int y = 0; y < 16; y++) {
            for (int z = 0; z < 16; z++) {
                for (int x = 0; x < 16; x++) {
                    section.set(x, y, z, Blocks.STRUCTURE_VOID.defaultBlockState());
                }
            }
        }
        section.set(2, 3, 4, Blocks.STONE.defaultBlockState());
        section.set(5, 3, 4, Blocks.DIRT.defaultBlockState());
        CompoundTag states = (CompoundTag) AxiomBlueprintCodec.sectionCodec().encodeStart(NbtOps.INSTANCE, section)
                .getOrThrow();
        ListTag palette = states.getListOrEmpty("palette");
        int replaced = 0;
        for (int i = 0; i < palette.size(); i++) {
            if (palette.get(i) instanceof CompoundTag compound) {
                for (String key : List.of("Name", "id")) {
                    if (compound.getStringOr(key, "").equals("minecraft:dirt")) {
                        compound.putString(key, "othermod:mystery");
                        replaced++;
                    }
                }
            } else if (palette.get(i).asString().orElse("").equals("minecraft:dirt")) {
                palette.set(i, StringTag.valueOf("othermod:mystery"));
                replaced++;
            }
        }
        assertEquals(1, replaced);
        CompoundTag entry = new CompoundTag();
        entry.putInt("X", -1);
        entry.putInt("Y", 4);
        entry.putInt("Z", 0);
        entry.put("BlockStates", states);
        ListTag region = new ListTag();
        region.add(entry);
        CompoundTag body = new CompoundTag();
        body.putInt("DataVersion", BlockStateNbt.currentDataVersion());
        body.put("BlockRegion", region);
        CompoundTag header = new CompoundTag();
        header.putInt("Version", 1);
        header.putString("Name", "Old");
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        DataOutputStream data = new DataOutputStream(out);
        data.writeInt(AxiomBlueprintCodec.MAGIC);
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        NbtIo.write(header, new DataOutputStream(buffer));
        data.writeInt(buffer.size());
        buffer.writeTo(data);
        data.writeInt(0);
        buffer.reset();
        NbtIo.writeCompressed(body, buffer);
        data.writeInt(buffer.size());
        buffer.writeTo(data);
        Schematic copy = AxiomBlueprintCodec.read(new ByteArrayInputStream(out.toByteArray()), "Old");
        SchematicRegion read = copy.regions().values().iterator().next();
        assertEquals(1, read.absX());
        assertEquals(1, read.absY());
        assertEquals(1, read.absZ());
        assertEquals(Blocks.STONE.defaultBlockState(), read.state(0, 0, 0));
        assertEquals(1, copy.metadata().unknownStates);
    }

    @Test
    void exportWritesABlueprintNextToTheSource(@TempDir Path directory) throws IOException {
        Path source = directory.resolve("house.litematic");
        SchematicIo.write(source, sample());
        Path target = SchematicIo.export(source, SchematicExport.Format.BLUEPRINT);
        assertEquals("house.bp", target.getFileName().toString());
        assertTrue(SchematicIo.isSupported(target));
        assertTrue(Files.size(target) > 0L);
        assertEquals(5, SchematicIo.readMetadata(target).enclosingX);
        assertEquals(Blocks.GLASS.defaultBlockState(),
                SchematicIo.read(target).regions().values().iterator().next().state(4, 2, 0));
    }
}
