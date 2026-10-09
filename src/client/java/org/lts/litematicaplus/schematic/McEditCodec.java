package org.lts.litematicaplus.schematic;

import com.mojang.serialization.Dynamic;
import java.io.IOException;
import java.util.Map;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.DoubleTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.util.datafix.fixes.BlockStateData;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import org.lts.litematicaplus.util.LocalizedException;

public final class McEditCodec {
    static final int FLATTENING_DATA_VERSION = 1451;
    static final int LEGACY_DATA_VERSION = 99;
    private static final int LEGACY_IDS = 1 << 16;

    private McEditCodec() {
    }

    public static boolean matches(CompoundTag root) {
        return root.getByteArray("Blocks").isPresent() && root.contains("Width") && root.contains("Height")
                && root.contains("Length");
    }

    public static Schematic read(CompoundTag root, String fallbackName) throws IOException {
        int width = root.getShortOr("Width", (short) 0) & 0xFFFF;
        int height = root.getShortOr("Height", (short) 0) & 0xFFFF;
        int length = root.getShortOr("Length", (short) 0) & 0xFFFF;
        if (width == 0 || height == 0 || length == 0) {
            throw new LocalizedException("litematicaplus.error.empty_schematic", "MCEdit schematic has an empty size");
        }
        long volume = (long) width * height * length;
        if (volume > LitematicCodec.MAX_REGION_VOLUME || LitematicCodec.tooLong(width, height, length)) {
            throw new LocalizedException("litematicaplus.error.too_large", "MCEdit schematic is too large: " + volume + " blocks");
        }
        byte[] ids = root.getByteArray("Blocks").orElseThrow();
        byte[] meta = root.getByteArray("Data").orElse(new byte[0]);
        byte[] addNibbles = root.getByteArray("AddBlocks").orElse(null);
        byte[] addBytes = root.getByteArray("Add").orElse(null);
        if (ids.length < volume) {
            throw new LocalizedException("litematicaplus.error.corrupt", "MCEdit schematic block data is truncated: "
                    + ids.length + " bytes for " + volume + " blocks");
        }
        BlockStateNbt.Unresolved unresolved = new BlockStateNbt.Unresolved();
        BlockState[] cache = new BlockState[LEGACY_IDS];
        RegionBuilder builder = new RegionBuilder(fallbackName, 0, 0, 0, width, height, length);
        long count = 0L;
        for (int index = 0; index < volume; index++) {
            int id = ids[index] & 0xFF;
            if (addNibbles != null && (index >> 1) < addNibbles.length) {
                int packed = addNibbles[index >> 1];
                id |= ((index & 1) == 0 ? packed >> 4 & 0xF : packed & 0xF) << 8;
            } else if (addBytes != null && index < addBytes.length) {
                id |= (addBytes[index] & 0xF) << 8;
            }
            if (id == 0) {
                continue;
            }
            int data = index < meta.length ? meta[index] & 0xF : 0;
            int key = id << 4 | data;
            BlockState state = cache[key];
            if (state == null) {
                state = legacyState(key, unresolved);
                cache[key] = state;
            }
            if (state.isAir()) {
                continue;
            }
            builder.set(index % width, index / (width * length), index / width % length, state);
            if (!state.is(Blocks.STRUCTURE_VOID)) {
                count++;
            }
        }
        ListTag tiles = root.getListOrEmpty("TileEntities");
        for (int i = 0; i < tiles.size(); i++) {
            CompoundTag tile = tiles.getCompoundOrEmpty(i).copy();
            int x = tile.getIntOr("x", -1);
            int y = tile.getIntOr("y", -1);
            int z = tile.getIntOr("z", -1);
            if (x < 0 || y < 0 || z < 0 || x >= width || y >= height || z >= length) {
                continue;
            }
            tile.remove("x");
            tile.remove("y");
            tile.remove("z");
            builder.blockEntity(x, y, z, BlockStateNbt.fixBlockEntity(tile, LEGACY_DATA_VERSION));
        }
        ListTag entities = root.getListOrEmpty("Entities");
        for (int i = 0; i < entities.size(); i++) {
            CompoundTag entity = entities.getCompoundOrEmpty(i);
            ListTag pos = entity.getListOrEmpty("Pos");
            double x = pos.getDoubleOr(0, Double.NaN);
            double y = pos.getDoubleOr(1, Double.NaN);
            double z = pos.getDoubleOr(2, Double.NaN);
            if (!(x >= 0.0 && y >= 0.0 && z >= 0.0 && x <= width && y <= height && z <= length)) {
                continue;
            }
            CompoundTag fixed = BlockStateNbt.fixEntity(entity.copy(), LEGACY_DATA_VERSION);
            ListTag relative = new ListTag();
            relative.add(DoubleTag.valueOf(x));
            relative.add(DoubleTag.valueOf(y));
            relative.add(DoubleTag.valueOf(z));
            fixed.put("Pos", relative);
            fixed.remove("UUID");
            fixed.remove("UUIDMost");
            fixed.remove("UUIDLeast");
            builder.entity(fixed);
        }
        SchematicMetadata metadata = new SchematicMetadata();
        metadata.name = fallbackName;
        metadata.dataVersion = BlockStateNbt.currentDataVersion();
        metadata.totalBlocks = count;
        metadata.unknownStates = unresolved.count();
        metadata.format = "mcedit";
        return new Schematic(metadata, Map.of(fallbackName, builder.build()));
    }

    static BlockState legacyState(int key, BlockStateNbt.Unresolved unresolved) {
        Dynamic<?> legacy = BlockStateData.getTag(key);
        if (legacy == null) {
            unresolved.add();
            return Blocks.STRUCTURE_VOID.defaultBlockState();
        }
        Tag tag = legacy.convert(NbtOps.INSTANCE).getValue();
        if (!(tag instanceof CompoundTag compound)) {
            unresolved.add();
            return Blocks.STRUCTURE_VOID.defaultBlockState();
        }
        return BlockStateNbt.read(compound, FLATTENING_DATA_VERSION, unresolved);
    }
}
