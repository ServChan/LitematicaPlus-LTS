package org.lts.litematicaplus.schematic;

import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.objects.Object2IntLinkedOpenHashMap;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.DoubleTag;
import net.minecraft.nbt.IntTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import org.lts.litematicaplus.util.LocalizedException;

public final class SchematicExport {
    public static final long MAX_EXPORT_VOLUME = 1L << 25;
    public static final long MAX_STRUCTURE_BLOCKS = 1L << 22;
    private static final int OUTSIDE = -1;

    public enum Format {
        SPONGE(".schem"),
        STRUCTURE(".nbt");

        private final String extension;

        Format(String extension) {
            this.extension = extension;
        }

        public String extension() {
            return this.extension;
        }

        public static Format of(String fileName) {
            String name = fileName.toLowerCase(Locale.ROOT);
            for (Format format : values()) {
                if (name.endsWith(format.extension)) {
                    return format;
                }
            }
            return null;
        }
    }

    private record Merged(int sizeX, int sizeY, int sizeZ, BlockState[] palette, int[] cells,
            List<CompoundTag> blockEntities, List<CompoundTag> entities) {
        long index(int x, int y, int z) {
            return ((long) y * this.sizeZ + z) * this.sizeX + x;
        }
    }

    private SchematicExport() {
    }

    public static CompoundTag encode(Schematic schematic, Format format) throws IOException {
        return format == Format.SPONGE ? sponge(schematic) : structure(schematic);
    }

    public static CompoundTag sponge(Schematic schematic) throws IOException {
        Merged merged = merge(schematic, MAX_EXPORT_VOLUME);
        if (merged.sizeX() > 0xFFFF || merged.sizeY() > 0xFFFF || merged.sizeZ() > 0xFFFF) {
            throw new LocalizedException("litematicaplus.error.export_too_large", "Schematic is too large for Sponge");
        }
        int[] spongeIds = new int[merged.palette().length];
        Object2IntLinkedOpenHashMap<String> palette = new Object2IntLinkedOpenHashMap<>();
        palette.put("minecraft:air", 0);
        for (int i = 0; i < merged.palette().length; i++) {
            BlockState state = merged.palette()[i];
            String key = state.is(Blocks.STRUCTURE_VOID) ? "minecraft:air" : BlockStateNbt.format(state);
            int id = palette.getOrDefault(key, -1);
            if (id < 0) {
                id = palette.size();
                palette.put(key, id);
            }
            spongeIds[i] = id;
        }
        ByteArrayOutputStream data = new ByteArrayOutputStream(merged.cells().length);
        for (int cell : merged.cells()) {
            int value = cell == OUTSIDE ? 0 : spongeIds[cell];
            while ((value & ~0x7F) != 0) {
                data.write(value & 0x7F | 0x80);
                value >>>= 7;
            }
            data.write(value);
        }
        CompoundTag paletteTag = new CompoundTag();
        palette.forEach(paletteTag::putInt);
        ListTag blockEntities = new ListTag();
        for (CompoundTag entry : merged.blockEntities()) {
            CompoundTag nbt = entry.copy();
            int[] pos = {nbt.getIntOr("x", 0), nbt.getIntOr("y", 0), nbt.getIntOr("z", 0)};
            nbt.remove("x");
            nbt.remove("y");
            nbt.remove("z");
            String id = nbt.getStringOr("id", "");
            nbt.remove("id");
            CompoundTag tile = new CompoundTag();
            tile.putIntArray("Pos", pos);
            tile.putString("Id", id);
            tile.put("Data", nbt);
            blockEntities.add(tile);
        }
        CompoundTag blocks = new CompoundTag();
        blocks.put("Palette", paletteTag);
        blocks.putByteArray("Data", data.toByteArray());
        blocks.put("BlockEntities", blockEntities);
        ListTag entities = new ListTag();
        for (CompoundTag entity : merged.entities()) {
            CompoundTag nbt = entity.copy();
            ListTag pos = nbt.getListOrEmpty("Pos").copy();
            String id = nbt.getStringOr("id", "");
            nbt.remove("id");
            CompoundTag entry = new CompoundTag();
            entry.put("Pos", pos);
            entry.putString("Id", id);
            entry.put("Data", nbt);
            entities.add(entry);
        }
        SchematicMetadata source = schematic.metadata();
        CompoundTag metadata = new CompoundTag();
        metadata.putString("Name", source.name);
        metadata.putString("Author", source.author);
        metadata.putLong("Date", source.timeCreated);
        CompoundTag body = new CompoundTag();
        body.putInt("Version", 3);
        body.putInt("DataVersion", BlockStateNbt.currentDataVersion());
        body.putShort("Width", (short) merged.sizeX());
        body.putShort("Height", (short) merged.sizeY());
        body.putShort("Length", (short) merged.sizeZ());
        body.putIntArray("Offset", new int[3]);
        body.put("Metadata", metadata);
        body.put("Blocks", blocks);
        body.put("Entities", entities);
        CompoundTag root = new CompoundTag();
        root.put("Schematic", body);
        return root;
    }

    public static CompoundTag structure(Schematic schematic) throws IOException {
        Merged merged = merge(schematic, MAX_EXPORT_VOLUME);
        long present = 0L;
        for (int cell : merged.cells()) {
            if (cell != OUTSIDE && !merged.palette()[cell].is(Blocks.STRUCTURE_VOID)) {
                present++;
            }
        }
        if (present > MAX_STRUCTURE_BLOCKS) {
            throw new LocalizedException("litematicaplus.error.export_too_large", "Schematic is too large for a structure file");
        }
        HashMap<Long, CompoundTag> tiles = new HashMap<>();
        for (CompoundTag entry : merged.blockEntities()) {
            tiles.put(merged.index(entry.getIntOr("x", 0), entry.getIntOr("y", 0), entry.getIntOr("z", 0)), entry);
        }
        ListTag palette = new ListTag();
        int[] structureIds = new int[merged.palette().length];
        Arrays.fill(structureIds, OUTSIDE);
        ListTag blocks = new ListTag();
        for (int y = 0; y < merged.sizeY(); y++) {
            for (int z = 0; z < merged.sizeZ(); z++) {
                for (int x = 0; x < merged.sizeX(); x++) {
                    long index = merged.index(x, y, z);
                    int cell = merged.cells()[(int) index];
                    if (cell == OUTSIDE || merged.palette()[cell].is(Blocks.STRUCTURE_VOID)) {
                        continue;
                    }
                    if (structureIds[cell] == OUTSIDE) {
                        structureIds[cell] = palette.size();
                        palette.add(BlockStateNbt.write(merged.palette()[cell]));
                    }
                    CompoundTag block = new CompoundTag();
                    block.put("pos", ints(x, y, z));
                    block.putInt("state", structureIds[cell]);
                    CompoundTag tile = tiles.get(index);
                    if (tile != null) {
                        CompoundTag nbt = tile.copy();
                        nbt.remove("x");
                        nbt.remove("y");
                        nbt.remove("z");
                        block.put("nbt", nbt);
                    }
                    blocks.add(block);
                }
            }
        }
        ListTag entities = new ListTag();
        for (CompoundTag entity : merged.entities()) {
            ListTag pos = entity.getListOrEmpty("Pos");
            double x = pos.getDoubleOr(0, 0.0);
            double y = pos.getDoubleOr(1, 0.0);
            double z = pos.getDoubleOr(2, 0.0);
            CompoundTag entry = new CompoundTag();
            entry.put("pos", doubles(x, y, z));
            entry.put("blockPos", ints((int) Math.floor(x), (int) Math.floor(y), (int) Math.floor(z)));
            entry.put("nbt", entity.copy());
            entities.add(entry);
        }
        CompoundTag root = new CompoundTag();
        root.putInt("DataVersion", BlockStateNbt.currentDataVersion());
        root.put("size", ints(merged.sizeX(), merged.sizeY(), merged.sizeZ()));
        root.put("palette", palette);
        root.put("blocks", blocks);
        root.put("entities", entities);
        if (!schematic.metadata().author.isBlank()) {
            root.putString("author", schematic.metadata().author);
        }
        return root;
    }

    private static Merged merge(Schematic schematic, long limit) throws IOException {
        int minX = schematic.minX();
        int minY = schematic.minY();
        int minZ = schematic.minZ();
        long sizeX = (long) schematic.maxX() - minX + 1L;
        long sizeY = (long) schematic.maxY() - minY + 1L;
        long sizeZ = (long) schematic.maxZ() - minZ + 1L;
        long volume = sizeX * sizeY * sizeZ;
        if (volume > limit) {
            throw new LocalizedException("litematicaplus.error.export_too_large",
                    "Schematic is too large to export: " + volume + " blocks");
        }
        int[] cells = new int[(int) volume];
        Arrays.fill(cells, OUTSIDE);
        List<BlockState> palette = new ArrayList<>();
        Map<BlockState, Integer> lookup = new IdentityHashMap<>();
        List<CompoundTag> blockEntities = new ArrayList<>();
        List<CompoundTag> entities = new ArrayList<>();
        for (SchematicRegion region : schematic.regions().values()) {
            int[] local = new int[region.palette().length];
            for (int i = 0; i < local.length; i++) {
                BlockState state = region.palette()[i];
                Integer id = lookup.get(state);
                if (id == null) {
                    id = palette.size();
                    palette.add(state);
                    lookup.put(state, id);
                }
                local[i] = id;
            }
            int offsetX = region.minX() - minX;
            int offsetY = region.minY() - minY;
            int offsetZ = region.minZ() - minZ;
            for (int y = 0; y < region.absY(); y++) {
                for (int z = 0; z < region.absZ(); z++) {
                    for (int x = 0; x < region.absX(); x++) {
                        int id = region.paletteIndex(x, y, z);
                        if (id < 0 || id >= local.length) {
                            continue;
                        }
                        int target = (int) ((((long) (y + offsetY)) * sizeZ + (z + offsetZ)) * sizeX + (x + offsetX));
                        if (cells[target] == OUTSIDE || !region.palette()[id].isAir()) {
                            cells[target] = local[id];
                        }
                    }
                }
            }
            for (Long2ObjectMap.Entry<CompoundTag> entry : region.blockEntities().long2ObjectEntrySet()) {
                long key = entry.getLongKey();
                CompoundTag tag = entry.getValue().copy();
                tag.putInt("x", SchematicRegion.localKeyX(key) + offsetX);
                tag.putInt("y", SchematicRegion.localKeyY(key) + offsetY);
                tag.putInt("z", SchematicRegion.localKeyZ(key) + offsetZ);
                blockEntities.add(tag);
            }
            for (CompoundTag entity : region.entities()) {
                CompoundTag tag = entity.copy();
                ListTag pos = tag.getListOrEmpty("Pos");
                tag.put("Pos", doubles(pos.getDoubleOr(0, 0.0) + offsetX, pos.getDoubleOr(1, 0.0) + offsetY,
                        pos.getDoubleOr(2, 0.0) + offsetZ));
                tag.remove("UUID");
                entities.add(tag);
            }
        }
        return new Merged((int) sizeX, (int) sizeY, (int) sizeZ, palette.toArray(BlockState[]::new), cells,
                blockEntities, entities);
    }

    private static ListTag ints(int x, int y, int z) {
        ListTag list = new ListTag();
        list.add(IntTag.valueOf(x));
        list.add(IntTag.valueOf(y));
        list.add(IntTag.valueOf(z));
        return list;
    }

    private static ListTag doubles(double x, double y, double z) {
        ListTag list = new ListTag();
        list.add(DoubleTag.valueOf(x));
        list.add(DoubleTag.valueOf(y));
        list.add(DoubleTag.valueOf(z));
        return list;
    }
}
