package org.lts.litematicaplus.schematic;

import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import java.util.LinkedHashMap;
import java.util.Map;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.DoubleTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

public final class SchematicTrim {
    private SchematicTrim() {
    }

    public static Schematic trim(Schematic schematic) {
        int oldX = schematic.minX();
        int oldY = schematic.minY();
        int oldZ = schematic.minZ();
        Map<String, SchematicRegion> regions = new LinkedHashMap<>();
        for (SchematicRegion region : schematic.regions().values()) {
            regions.put(region.name(), trim(region));
        }
        SchematicMetadata metadata = schematic.metadata().copy();
        Schematic trimmed = new Schematic(metadata, regions);
        if (metadata.placement != null) {
            metadata.placement = metadata.placement.mergedFrom(trimmed.minX() - oldX, trimmed.minY() - oldY,
                    trimmed.minZ() - oldZ);
        }
        return trimmed;
    }

    private static boolean empty(BlockState state) {
        return state.isAir() || state.is(Blocks.STRUCTURE_VOID);
    }

    static SchematicRegion trim(SchematicRegion region) {
        BlockState[] palette = region.palette();
        boolean[] blank = new boolean[palette.length];
        for (int i = 0; i < palette.length; i++) {
            blank[i] = empty(palette[i]);
        }
        int lowX = Integer.MAX_VALUE;
        int lowY = Integer.MAX_VALUE;
        int lowZ = Integer.MAX_VALUE;
        int highX = -1;
        int highY = -1;
        int highZ = -1;
        BitPackedArray states = region.states();
        long cell = 0L;
        for (int y = 0; y < region.absY(); y++) {
            for (int z = 0; z < region.absZ(); z++) {
                for (int x = 0; x < region.absX(); x++, cell++) {
                    int id = states.get(cell);
                    if (id < blank.length && !blank[id]) {
                        lowX = Math.min(lowX, x);
                        lowY = Math.min(lowY, y);
                        lowZ = Math.min(lowZ, z);
                        highX = Math.max(highX, x);
                        highY = Math.max(highY, y);
                        highZ = Math.max(highZ, z);
                    }
                }
            }
        }
        if (highX < 0) {
            lowX = 0;
            lowY = 0;
            lowZ = 0;
            highX = 0;
            highY = 0;
            highZ = 0;
        }
        int sizeX = highX - lowX + 1;
        int sizeY = highY - lowY + 1;
        int sizeZ = highZ - lowZ + 1;
        if (sizeX == region.absX() && sizeY == region.absY() && sizeZ == region.absZ()) {
            return region;
        }
        int newX = region.minX() + lowX;
        int newY = region.minY() + lowY;
        int newZ = region.minZ() + lowZ;
        RegionBuilder builder = new RegionBuilder(region.name(), newX, newY, newZ, sizeX, sizeY, sizeZ);
        for (int y = 0; y < sizeY; y++) {
            for (int z = 0; z < sizeZ; z++) {
                for (int x = 0; x < sizeX; x++) {
                    builder.set(x, y, z, region.state(lowX + x, lowY + y, lowZ + z));
                }
            }
        }
        for (Long2ObjectMap.Entry<CompoundTag> entry : region.blockEntities().long2ObjectEntrySet()) {
            int x = SchematicRegion.localKeyX(entry.getLongKey()) - lowX;
            int y = SchematicRegion.localKeyY(entry.getLongKey()) - lowY;
            int z = SchematicRegion.localKeyZ(entry.getLongKey()) - lowZ;
            if (x >= 0 && y >= 0 && z >= 0 && x < sizeX && y < sizeY && z < sizeZ) {
                CompoundTag tag = entry.getValue().copy();
                tag.putInt("x", x);
                tag.putInt("y", y);
                tag.putInt("z", z);
                builder.blockEntity(x, y, z, tag);
            }
        }
        for (CompoundTag entity : region.entities()) {
            builder.entity(shifted(entity, region.posX() - newX, region.posY() - newY, region.posZ() - newZ));
        }
        return builder.build();
    }

    private static CompoundTag shifted(CompoundTag entity, int x, int y, int z) {
        ListTag position = entity.getListOrEmpty("Pos");
        if (position.size() < 3) {
            return entity;
        }
        CompoundTag copy = entity.copy();
        ListTag moved = new ListTag();
        moved.add(DoubleTag.valueOf(position.getDoubleOr(0, 0.0) + x));
        moved.add(DoubleTag.valueOf(position.getDoubleOr(1, 0.0) + y));
        moved.add(DoubleTag.valueOf(position.getDoubleOr(2, 0.0) + z));
        copy.put("Pos", moved);
        return copy;
    }
}
