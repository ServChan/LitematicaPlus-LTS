package org.lts.litematicaplus.schematic;

import com.mojang.serialization.Codec;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.DoubleTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.PalettedContainer;
import net.minecraft.world.level.chunk.Strategy;
import net.minecraft.world.level.material.MapColor;
import org.lts.litematicaplus.util.LocalizedException;
import org.lts.litematicaplus.util.Png;

public final class AxiomBlueprintCodec {
    public static final String EXTENSION = ".bp";
    public static final int MAGIC = 0x0AE5BB36;
    public static final int HEADER_VERSION = 2;
    private static final long NBT_QUOTA = 1L << 30;
    private static final int HEADER_LIMIT = 1 << 20;
    private static final int THUMBNAIL_LIMIT = 1 << 26;
    private static final int THUMBNAIL_SIZE = 128;

    public record Header(int version, String name, String author, List<String> tags, int blockCount,
            boolean containsAir) {
    }

    private record Section(int x, int y, int z, PalettedContainer<BlockState> blocks) {
    }

    private AxiomBlueprintCodec() {
    }

    public static Codec<PalettedContainer<BlockState>> sectionCodec() {
        return PalettedContainer.codecRW(BlockState.CODEC, Strategy.createForBlockStates(Block.BLOCK_STATE_REGISTRY),
                Blocks.VOID_AIR.defaultBlockState());
    }

    public static Header readHeader(InputStream input) throws IOException {
        DataInputStream data = new DataInputStream(input);
        if (data.readInt() != MAGIC) {
            throw new LocalizedException("litematicaplus.error.not_schematic", "Not an Axiom blueprint");
        }
        int length = data.readInt();
        if (length < 0 || length > HEADER_LIMIT) {
            throw new LocalizedException("litematicaplus.error.corrupt", "Axiom blueprint header is " + length + " bytes");
        }
        CompoundTag tag = NbtIo.read(new DataInputStream(new ByteArrayInputStream(data.readNBytes(length))));
        List<String> tags = new ArrayList<>();
        ListTag list = tag.getListOrEmpty("Tags");
        for (int i = 0; i < list.size(); i++) {
            list.getString(i).ifPresent(tags::add);
        }
        return new Header(tag.getIntOr("Version", 0), tag.getStringOr("Name", ""), tag.getStringOr("Author", ""),
                tags, tag.getIntOr("BlockCount", 0), tag.getBooleanOr("ContainsAir", false));
    }

    public static Schematic read(InputStream input, String fallbackName) throws IOException {
        DataInputStream data = new DataInputStream(input);
        Header header = readHeader(data);
        int thumbnail = data.readInt();
        if (thumbnail < 0 || thumbnail > THUMBNAIL_LIMIT) {
            throw new LocalizedException("litematicaplus.error.corrupt", "Axiom blueprint thumbnail is " + thumbnail + " bytes");
        }
        data.skipNBytes(thumbnail);
        data.readInt();
        CompoundTag body;
        try {
            body = NbtIo.readCompressed(data, NbtAccounter.create(NBT_QUOTA));
        } catch (RuntimeException exception) {
            throw new LocalizedException("litematicaplus.error.unreadable", "Unreadable block data in Axiom blueprint", exception);
        }
        String name = header.name().isBlank() ? fallbackName : header.name();
        try {
            return decode(header, body, name);
        } catch (RuntimeException exception) {
            throw new LocalizedException("litematicaplus.error.corrupt", "Corrupt Axiom blueprint: " + exception.getMessage(), exception);
        }
    }

    private static Schematic decode(Header header, CompoundTag body, String name) throws IOException {
        int dataVersion = body.getIntOr("DataVersion", 0);
        BlockStateNbt.Unresolved unresolved = new BlockStateNbt.Unresolved();
        BlockState voidAir = Blocks.VOID_AIR.defaultBlockState();
        BlockState legacyEmpty = header.version() == 1 ? Blocks.STRUCTURE_VOID.defaultBlockState() : voidAir;
        Codec<PalettedContainer<BlockState>> codec = sectionCodec();
        ListTag regionList = body.getListOrEmpty("BlockRegion");
        List<Section> sections = new ArrayList<>(regionList.size());
        int lowX = Integer.MAX_VALUE;
        int lowY = Integer.MAX_VALUE;
        int lowZ = Integer.MAX_VALUE;
        int highX = Integer.MIN_VALUE;
        int highY = Integer.MIN_VALUE;
        int highZ = Integer.MIN_VALUE;
        for (int i = 0; i < regionList.size(); i++) {
            CompoundTag entry = regionList.getCompoundOrEmpty(i);
            CompoundTag states = canonical(entry.getCompoundOrEmpty("BlockStates"), dataVersion, unresolved);
            PalettedContainer<BlockState> blocks = codec.parse(NbtOps.INSTANCE, states).getOrThrow(
                    message -> new LocalizedException("litematicaplus.error.corrupt", "Axiom blueprint section: " + message));
            Section section = new Section(entry.getIntOr("X", 0), entry.getIntOr("Y", 0), entry.getIntOr("Z", 0), blocks);
            boolean any = false;
            for (int y = 0; y < 16; y++) {
                for (int z = 0; z < 16; z++) {
                    for (int x = 0; x < 16; x++) {
                        BlockState state = blocks.get(x, y, z);
                        if (state == voidAir || state == legacyEmpty) {
                            continue;
                        }
                        any = true;
                        int wx = section.x() * 16 + x;
                        int wy = section.y() * 16 + y;
                        int wz = section.z() * 16 + z;
                        lowX = Math.min(lowX, wx);
                        lowY = Math.min(lowY, wy);
                        lowZ = Math.min(lowZ, wz);
                        highX = Math.max(highX, wx);
                        highY = Math.max(highY, wy);
                        highZ = Math.max(highZ, wz);
                    }
                }
            }
            if (any) {
                sections.add(section);
            }
        }
        if (sections.isEmpty()) {
            throw new LocalizedException("litematicaplus.error.empty_schematic", "Axiom blueprint has no blocks");
        }
        int sizeX = highX - lowX + 1;
        int sizeY = highY - lowY + 1;
        int sizeZ = highZ - lowZ + 1;
        RegionBuilder builder = new RegionBuilder(name, 0, 0, 0, sizeX, sizeY, sizeZ);
        builder.fill(Blocks.STRUCTURE_VOID.defaultBlockState());
        long count = 0L;
        for (Section section : sections) {
            PalettedContainer<BlockState> blocks = section.blocks();
            for (int y = 0; y < 16; y++) {
                for (int z = 0; z < 16; z++) {
                    for (int x = 0; x < 16; x++) {
                        BlockState state = blocks.get(x, y, z);
                        if (state == voidAir || state == legacyEmpty) {
                            continue;
                        }
                        builder.set(section.x() * 16 + x - lowX, section.y() * 16 + y - lowY,
                                section.z() * 16 + z - lowZ, state);
                        if (!state.isAir() && !state.is(Blocks.STRUCTURE_VOID)) {
                            count++;
                        }
                    }
                }
            }
        }
        ListTag tiles = body.getListOrEmpty("BlockEntities");
        for (int i = 0; i < tiles.size(); i++) {
            CompoundTag tile = tiles.getCompoundOrEmpty(i).copy();
            int x = tile.getIntOr("x", Integer.MIN_VALUE) - lowX;
            int y = tile.getIntOr("y", Integer.MIN_VALUE) - lowY;
            int z = tile.getIntOr("z", Integer.MIN_VALUE) - lowZ;
            if (x < 0 || y < 0 || z < 0 || x >= sizeX || y >= sizeY || z >= sizeZ || !tile.contains("id")) {
                continue;
            }
            CompoundTag fixed = BlockStateNbt.fixBlockEntity(tile, dataVersion).copy();
            fixed.remove("x");
            fixed.remove("y");
            fixed.remove("z");
            builder.blockEntity(x, y, z, fixed);
        }
        ListTag entities = body.getListOrEmpty("Entities");
        for (int i = 0; i < entities.size(); i++) {
            CompoundTag entity = entities.getCompoundOrEmpty(i);
            ListTag pos = entity.getListOrEmpty("Pos");
            if (pos.size() < 3 || entity.getStringOr("id", "").isEmpty()) {
                continue;
            }
            CompoundTag fixed = BlockStateNbt.fixEntity(entity, dataVersion).copy();
            fixed.put("Pos", doubles(pos.getDoubleOr(0, 0.0) - lowX, pos.getDoubleOr(1, 0.0) - lowY,
                    pos.getDoubleOr(2, 0.0) - lowZ));
            int[] hanging = fixed.getIntArray("block_pos").orElse(null);
            if (hanging != null && hanging.length == 3) {
                fixed.putIntArray("block_pos", new int[] {hanging[0] - lowX, hanging[1] - lowY, hanging[2] - lowZ});
            }
            fixed.remove("UUID");
            builder.entity(fixed);
        }
        SchematicMetadata metadata = new SchematicMetadata();
        metadata.name = name;
        metadata.author = header.author();
        metadata.description = String.join(", ", header.tags());
        metadata.dataVersion = Math.max(dataVersion, BlockStateNbt.currentDataVersion());
        metadata.schematicVersion = header.version();
        metadata.totalBlocks = count;
        metadata.unknownStates = unresolved.count();
        metadata.format = "axiom";
        return new Schematic(metadata, Map.of(name, builder.build()));
    }

    private static CompoundTag canonical(CompoundTag states, int dataVersion, BlockStateNbt.Unresolved unresolved) {
        ListTag palette = states.getListOrEmpty("palette");
        ListTag rewritten = new ListTag();
        for (int i = 0; i < palette.size(); i++) {
            Tag entry = palette.get(i);
            BlockState state = entry instanceof CompoundTag compound ? BlockStateNbt.read(compound, dataVersion, unresolved)
                    : BlockStateNbt.parse(entry.asString().orElse(""), dataVersion, unresolved);
            rewritten.add(BlockState.CODEC.encodeStart(NbtOps.INSTANCE, state).getOrThrow());
        }
        CompoundTag copy = states.copy();
        copy.put("palette", rewritten);
        return copy;
    }

    public static void write(OutputStream output, Schematic schematic) throws IOException {
        SchematicExport.Merged merged = SchematicExport.merge(schematic, SchematicExport.MAX_EXPORT_VOLUME);
        BlockState[] palette = merged.palette();
        Long2ObjectOpenHashMap<PalettedContainer<BlockState>> sections = new Long2ObjectOpenHashMap<>();
        BlockState voidAir = Blocks.VOID_AIR.defaultBlockState();
        boolean containsAir = false;
        int blockCount = 0;
        int[] cells = merged.cells();
        for (int y = 0; y < merged.sizeY(); y++) {
            for (int z = 0; z < merged.sizeZ(); z++) {
                for (int x = 0; x < merged.sizeX(); x++) {
                    int cell = cells[(int) merged.index(x, y, z)];
                    if (cell == SchematicExport.OUTSIDE) {
                        continue;
                    }
                    BlockState state = palette[cell];
                    if (state.is(Blocks.STRUCTURE_VOID) || state.is(Blocks.VOID_AIR)) {
                        continue;
                    }
                    if (state.isAir()) {
                        containsAir = true;
                    } else {
                        blockCount++;
                    }
                    long key = BlockPos.asLong(x >> 4, y >> 4, z >> 4);
                    PalettedContainer<BlockState> section = sections.get(key);
                    if (section == null) {
                        section = new PalettedContainer<>(voidAir, Strategy.createForBlockStates(Block.BLOCK_STATE_REGISTRY));
                        sections.put(key, section);
                    }
                    section.set(x & 15, y & 15, z & 15, state);
                }
            }
        }
        if (sections.isEmpty()) {
            throw new LocalizedException("litematicaplus.error.empty_schematic", "Nothing to write into the blueprint");
        }
        Codec<PalettedContainer<BlockState>> codec = sectionCodec();
        ListTag regionList = new ListTag();
        for (Long2ObjectMap.Entry<PalettedContainer<BlockState>> entry : sections.long2ObjectEntrySet()) {
            long key = entry.getLongKey();
            CompoundTag section = new CompoundTag();
            section.putInt("X", BlockPos.getX(key));
            section.putInt("Y", BlockPos.getY(key));
            section.putInt("Z", BlockPos.getZ(key));
            Tag states = codec.encodeStart(NbtOps.INSTANCE, entry.getValue()).getOrThrow(
                    message -> new LocalizedException("litematicaplus.error.corrupt", "Could not encode blueprint: " + message));
            section.put("BlockStates", states);
            regionList.add(section);
        }
        ListTag tiles = new ListTag();
        for (CompoundTag entry : merged.blockEntities()) {
            int x = entry.getIntOr("x", 0);
            int y = entry.getIntOr("y", 0);
            int z = entry.getIntOr("z", 0);
            if (x < 0 || y < 0 || z < 0 || x >= merged.sizeX() || y >= merged.sizeY() || z >= merged.sizeZ()) {
                continue;
            }
            int cell = cells[(int) merged.index(x, y, z)];
            if (cell == SchematicExport.OUTSIDE || !palette[cell].hasBlockEntity() || !entry.contains("id")) {
                continue;
            }
            tiles.add(entry.copy());
        }
        ListTag entities = new ListTag();
        for (CompoundTag entity : merged.entities()) {
            if (!entity.getStringOr("id", "").isEmpty()) {
                entities.add(entity.copy());
            }
        }
        CompoundTag body = new CompoundTag();
        body.putInt("DataVersion", BlockStateNbt.currentDataVersion());
        body.put("BlockRegion", regionList);
        body.put("BlockEntities", tiles);
        body.put("Entities", entities);
        SchematicMetadata source = schematic.metadata();
        CompoundTag header = new CompoundTag();
        header.putInt("Version", HEADER_VERSION);
        header.putString("Name", source.name);
        header.putString("Author", source.author);
        header.put("Tags", new ListTag());
        header.putFloat("ThumbnailYaw", 135.0F);
        header.putFloat("ThumbnailPitch", 30.0F);
        header.putBoolean("LockedThumbnail", false);
        header.putInt("BlockCount", blockCount);
        header.putBoolean("ContainsAir", containsAir);
        DataOutputStream data = new DataOutputStream(output);
        data.writeInt(MAGIC);
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        NbtIo.write(header, new DataOutputStream(buffer));
        data.writeInt(buffer.size());
        buffer.writeTo(data);
        byte[] thumbnail = thumbnail(merged);
        data.writeInt(thumbnail.length);
        data.write(thumbnail);
        buffer.reset();
        NbtIo.writeCompressed(body, buffer);
        data.writeInt(buffer.size());
        buffer.writeTo(data);
        data.flush();
    }

    private static byte[] thumbnail(SchematicExport.Merged merged) {
        int sizeX = merged.sizeX();
        int sizeZ = merged.sizeZ();
        int scale = Math.max(1, Math.min(THUMBNAIL_SIZE / Math.max(1, Math.max(sizeX, sizeZ)), 8));
        int stride = Math.max(1, (Math.max(sizeX, sizeZ) + THUMBNAIL_SIZE - 1) / THUMBNAIL_SIZE);
        int width = Math.max(1, Math.min(THUMBNAIL_SIZE, (sizeX + stride - 1) / stride * scale));
        int height = Math.max(1, Math.min(THUMBNAIL_SIZE, (sizeZ + stride - 1) / stride * scale));
        int[] pixels = new int[width * height];
        int[] top = new int[sizeX * sizeZ];
        int[] colors = new int[sizeX * sizeZ];
        BlockState[] palette = merged.palette();
        for (int z = 0; z < sizeZ; z++) {
            for (int x = 0; x < sizeX; x++) {
                top[z * sizeX + x] = -1;
                for (int y = merged.sizeY() - 1; y >= 0; y--) {
                    int cell = merged.cells()[(int) merged.index(x, y, z)];
                    if (cell == SchematicExport.OUTSIDE) {
                        continue;
                    }
                    BlockState state = palette[cell];
                    if (state.isAir() || state.is(Blocks.STRUCTURE_VOID)) {
                        continue;
                    }
                    MapColor color = state.getMapColor(EmptyBlockGetter.INSTANCE, BlockPos.ZERO);
                    if (color == MapColor.NONE) {
                        continue;
                    }
                    top[z * sizeX + x] = y;
                    colors[z * sizeX + x] = color.col;
                    break;
                }
            }
        }
        for (int py = 0; py < height; py++) {
            for (int px = 0; px < width; px++) {
                int x = Math.min(sizeX - 1, px / scale * stride);
                int z = Math.min(sizeZ - 1, py / scale * stride);
                int y = top[z * sizeX + x];
                if (y < 0) {
                    continue;
                }
                int north = z > 0 ? top[(z - 1) * sizeX + x] : y;
                float shade = y > north ? 1.0F : y < north ? 0.71F : 0.86F;
                int rgb = colors[z * sizeX + x];
                int r = Math.round((rgb >> 16 & 0xFF) * shade);
                int g = Math.round((rgb >> 8 & 0xFF) * shade);
                int b = Math.round((rgb & 0xFF) * shade);
                pixels[py * width + px] = 0xFF000000 | r << 16 | g << 8 | b;
            }
        }
        return Png.encode(width, height, pixels);
    }

    private static ListTag doubles(double x, double y, double z) {
        ListTag list = new ListTag();
        list.add(DoubleTag.valueOf(x));
        list.add(DoubleTag.valueOf(y));
        list.add(DoubleTag.valueOf(z));
        return list;
    }
}
