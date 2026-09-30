package org.lts.litematicaplus.schematic;

import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

public final class LitematicCodec {
    public static final int SCHEMATIC_VERSION = 7;
    public static final int SCHEMATIC_SUBVERSION = 1;
    public static final long MAX_REGION_VOLUME = 1L << 31;

    private LitematicCodec() {
    }

    public static Schematic read(CompoundTag root) throws IOException {
        int version = root.getIntOr("Version", 0);
        if (version < 1) {
            throw new IOException("Not a litematic file: missing Version");
        }
        int dataVersion = root.getIntOr("MinecraftDataVersion", 0);
        SchematicMetadata metadata = readMetadata(root.getCompoundOrEmpty("Metadata"));
        metadata.dataVersion = dataVersion;
        metadata.schematicVersion = version;
        metadata.format = "litematic";
        CompoundTag regionsTag = root.getCompoundOrEmpty("Regions");
        Map<String, SchematicRegion> regions = new LinkedHashMap<>();
        for (String name : regionsTag.keySet()) {
            CompoundTag regionTag = regionsTag.getCompoundOrEmpty(name);
            SchematicRegion region = readRegion(name, regionTag, dataVersion);
            if (region != null) {
                regions.put(name, region);
            }
        }
        if (regions.isEmpty()) {
            throw new IOException("Litematic file contains no readable regions");
        }
        return new Schematic(metadata, regions);
    }

    public static SchematicMetadata readMetadataOnly(CompoundTag root) {
        SchematicMetadata metadata = readMetadata(root.getCompoundOrEmpty("Metadata"));
        metadata.dataVersion = root.getIntOr("MinecraftDataVersion", 0);
        metadata.schematicVersion = root.getIntOr("Version", 0);
        metadata.format = "litematic";
        return metadata;
    }

    public static CompoundTag write(Schematic schematic) {
        CompoundTag root = new CompoundTag();
        root.putInt("MinecraftDataVersion", BlockStateNbt.currentDataVersion());
        root.putInt("Version", SCHEMATIC_VERSION);
        root.putInt("SubVersion", SCHEMATIC_SUBVERSION);
        root.put("Metadata", writeMetadata(schematic.metadata()));
        CompoundTag regions = new CompoundTag();
        for (SchematicRegion region : schematic.regions().values()) {
            regions.put(region.name(), writeRegion(region));
        }
        root.put("Regions", regions);
        return root;
    }

    private static SchematicMetadata readMetadata(CompoundTag tag) {
        SchematicMetadata metadata = new SchematicMetadata();
        metadata.name = tag.getStringOr("Name", "");
        metadata.author = tag.getStringOr("Author", "");
        metadata.description = tag.getStringOr("Description", "");
        metadata.timeCreated = tag.getLongOr("TimeCreated", 0L);
        metadata.timeModified = tag.getLongOr("TimeModified", 0L);
        metadata.regionCount = tag.getIntOr("RegionCount", 0);
        metadata.totalBlocks = tag.getLongOr("TotalBlocks", tag.getIntOr("TotalBlocks", 0));
        metadata.totalVolume = tag.getLongOr("TotalVolume", tag.getIntOr("TotalVolume", 0));
        CompoundTag size = tag.getCompoundOrEmpty("EnclosingSize");
        metadata.enclosingX = size.getIntOr("x", 0);
        metadata.enclosingY = size.getIntOr("y", 0);
        metadata.enclosingZ = size.getIntOr("z", 0);
        return metadata;
    }

    private static CompoundTag writeMetadata(SchematicMetadata metadata) {
        CompoundTag tag = new CompoundTag();
        tag.putString("Name", metadata.name);
        tag.putString("Author", metadata.author);
        tag.putString("Description", metadata.description);
        tag.putLong("TimeCreated", metadata.timeCreated);
        tag.putLong("TimeModified", metadata.timeModified);
        tag.putInt("RegionCount", metadata.regionCount);
        tag.putLong("TotalBlocks", metadata.totalBlocks);
        tag.putLong("TotalVolume", metadata.totalVolume);
        tag.put("EnclosingSize", vector(metadata.enclosingX, metadata.enclosingY, metadata.enclosingZ));
        return tag;
    }

    private static SchematicRegion readRegion(String name, CompoundTag tag, int dataVersion) throws IOException {
        CompoundTag position = tag.getCompoundOrEmpty("Position");
        CompoundTag size = tag.getCompoundOrEmpty("Size");
        int sizeX = size.getIntOr("x", 0);
        int sizeY = size.getIntOr("y", 0);
        int sizeZ = size.getIntOr("z", 0);
        if (sizeX == 0 || sizeY == 0 || sizeZ == 0) {
            return null;
        }
        long volume = Math.abs((long) sizeX) * Math.abs((long) sizeY) * Math.abs((long) sizeZ);
        if (volume > MAX_REGION_VOLUME) {
            throw new IOException("Region '" + name + "' is too large: " + volume + " blocks");
        }
        ListTag paletteTag = tag.getListOrEmpty("BlockStatePalette");
        BlockState[] palette = new BlockState[Math.max(1, paletteTag.size())];
        palette[0] = Blocks.AIR.defaultBlockState();
        for (int i = 0; i < paletteTag.size(); i++) {
            palette[i] = BlockStateNbt.read(paletteTag.getCompoundOrEmpty(i), dataVersion);
        }
        int bits = BitPackedArray.bitsFor(palette.length);
        long[] raw = tag.getLongArray("BlockStates").orElse(null);
        int expected = BitPackedArray.requiredLongs(bits, volume);
        if (raw == null || raw.length != expected) {
            throw new IOException("Region '" + name + "' has a block array of "
                    + (raw == null ? 0 : raw.length) + " longs, expected " + expected);
        }
        BitPackedArray states = new BitPackedArray(bits, volume, raw);
        Long2ObjectMap<CompoundTag> blockEntities = new Long2ObjectOpenHashMap<>();
        ListTag tiles = tag.getListOrEmpty("TileEntities");
        int absX = Math.abs(sizeX);
        int absY = Math.abs(sizeY);
        int absZ = Math.abs(sizeZ);
        for (int i = 0; i < tiles.size(); i++) {
            CompoundTag tile = tiles.getCompoundOrEmpty(i).copy();
            int x = tile.getIntOr("x", -1);
            int y = tile.getIntOr("y", -1);
            int z = tile.getIntOr("z", -1);
            if (x < 0 || y < 0 || z < 0 || x >= absX || y >= absY || z >= absZ) {
                continue;
            }
            tile.remove("x");
            tile.remove("y");
            tile.remove("z");
            blockEntities.put(SchematicRegion.localKey(x, y, z), tile);
        }
        List<CompoundTag> entities = new ArrayList<>();
        ListTag entitiesTag = tag.getListOrEmpty("Entities");
        for (int i = 0; i < entitiesTag.size(); i++) {
            entities.add(entitiesTag.getCompoundOrEmpty(i).copy());
        }
        return new SchematicRegion(name,
                position.getIntOr("x", 0), position.getIntOr("y", 0), position.getIntOr("z", 0),
                sizeX, sizeY, sizeZ, palette, states, blockEntities, entities,
                tag.getListOrEmpty("PendingBlockTicks").copy(), tag.getListOrEmpty("PendingFluidTicks").copy());
    }

    private static CompoundTag writeRegion(SchematicRegion region) {
        CompoundTag tag = new CompoundTag();
        tag.put("Position", vector(region.posX(), region.posY(), region.posZ()));
        tag.put("Size", vector(region.sizeX(), region.sizeY(), region.sizeZ()));
        ListTag palette = new ListTag();
        for (BlockState state : region.palette()) {
            palette.add(BlockStateNbt.write(state));
        }
        tag.put("BlockStatePalette", palette);
        tag.putLongArray("BlockStates", region.states().raw().clone());
        ListTag tiles = new ListTag();
        for (Long2ObjectMap.Entry<CompoundTag> entry : region.blockEntities().long2ObjectEntrySet()) {
            CompoundTag tile = entry.getValue().copy();
            tile.putInt("x", SchematicRegion.localKeyX(entry.getLongKey()));
            tile.putInt("y", SchematicRegion.localKeyY(entry.getLongKey()));
            tile.putInt("z", SchematicRegion.localKeyZ(entry.getLongKey()));
            tiles.add(tile);
        }
        tag.put("TileEntities", tiles);
        ListTag entities = new ListTag();
        for (CompoundTag entity : region.entities()) {
            entities.add(entity.copy());
        }
        tag.put("Entities", entities);
        tag.put("PendingBlockTicks", region.pendingBlockTicks().copy());
        tag.put("PendingFluidTicks", region.pendingFluidTicks().copy());
        return tag;
    }

    private static Tag vector(int x, int y, int z) {
        CompoundTag tag = new CompoundTag();
        tag.putInt("x", x);
        tag.putInt("y", y);
        tag.putInt("z", z);
        return tag;
    }
}
