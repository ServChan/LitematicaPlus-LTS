package org.lts.litematicaplus.placement;

import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.DoubleTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntitySpawnRequest;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import org.lts.litematicaplus.schematic.BlockStateNbt;
import org.lts.litematicaplus.schematic.RegionBuilder;
import org.lts.litematicaplus.schematic.Schematic;
import org.lts.litematicaplus.schematic.SchematicMetadata;
import org.lts.litematicaplus.selection.SchematicCapture;
import org.lts.litematicaplus.util.LocalizedException;
import org.lts.litematicaplus.world.GhostSection;
import org.lts.litematicaplus.world.LayerRange;
import org.lts.litematicaplus.world.PlacementVolume;

public final class PlacementBake {
    private static final String REGION = "Placement";

    private PlacementBake() {
    }

    public static List<CompoundTag> entities(ClientLevel level, PlacementVolume volume, LayerRange scope) {
        List<CompoundTag> result = new ArrayList<>();
        for (PlacementVolume.EntitySeed seed : volume.entities()) {
            if (!scope.contains(Mth.floor(seed.x()), Mth.floor(seed.y()), Mth.floor(seed.z()))) {
                continue;
            }
            CompoundTag tag = turned(level, seed);
            ListTag position = new ListTag();
            position.add(DoubleTag.valueOf(seed.x()));
            position.add(DoubleTag.valueOf(seed.y()));
            position.add(DoubleTag.valueOf(seed.z()));
            tag.put("Pos", position);
            tag.remove("UUID");
            result.add(tag);
        }
        return result;
    }

    private static CompoundTag turned(ClientLevel level, PlacementVolume.EntitySeed seed) {
        if (seed.rotation() == Rotation.NONE && seed.mirror() == Mirror.NONE) {
            return seed.tag().copy();
        }
        try {
            CompoundTag tag = seed.tag().copy();
            tag.remove("UUID");
            Entity entity = EntityType.loadEntityRecursive(tag, level,
                    new EntitySpawnRequest(EntitySpawnReason.STRUCTURE, false), created -> {
                        float yaw = created.rotate(seed.rotation());
                        yaw += created.mirror(seed.mirror()) - created.getYRot();
                        created.snapTo(seed.x(), seed.y(), seed.z(), yaw, created.getXRot());
                        return created;
                    });
            CompoundTag saved = entity == null ? null : SchematicCapture.saved(level, entity);
            return saved != null ? saved : seed.tag().copy();
        } catch (RuntimeException exception) {
            return seed.tag().copy();
        }
    }

    public static Schematic bake(PlacementVolume volume, LayerRange scope, List<CompoundTag> entities, String name,
            String author) throws LocalizedException {
        int minX = Integer.MAX_VALUE;
        int minY = Integer.MAX_VALUE;
        int minZ = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE;
        int maxY = Integer.MIN_VALUE;
        int maxZ = Integer.MIN_VALUE;
        for (Long2ObjectMap.Entry<GhostSection> entry : volume.sections().long2ObjectEntrySet()) {
            int originX = SectionPos.x(entry.getLongKey()) << 4;
            int originY = SectionPos.y(entry.getLongKey()) << 4;
            int originZ = SectionPos.z(entry.getLongKey()) << 4;
            short[] raw = entry.getValue().raw();
            for (int index = 0; index < GhostSection.VOLUME; index++) {
                if (raw[index] == GhostSection.OUTSIDE) {
                    continue;
                }
                int x = originX + (index & 15);
                int y = originY + (index >> 8);
                int z = originZ + (index >> 4 & 15);
                if (scope.contains(x, y, z)) {
                    minX = Math.min(minX, x);
                    minY = Math.min(minY, y);
                    minZ = Math.min(minZ, z);
                    maxX = Math.max(maxX, x);
                    maxY = Math.max(maxY, y);
                    maxZ = Math.max(maxZ, z);
                }
            }
        }
        if (minX > maxX) {
            throw new LocalizedException("litematicaplus.error.bake_empty", "Nothing to save in this layer");
        }
        RegionBuilder builder = new RegionBuilder(REGION, 0, 0, 0, maxX - minX + 1, maxY - minY + 1, maxZ - minZ + 1);
        builder.fill(Blocks.STRUCTURE_VOID.defaultBlockState());
        for (Long2ObjectMap.Entry<GhostSection> entry : volume.sections().long2ObjectEntrySet()) {
            int originX = SectionPos.x(entry.getLongKey()) << 4;
            int originY = SectionPos.y(entry.getLongKey()) << 4;
            int originZ = SectionPos.z(entry.getLongKey()) << 4;
            short[] raw = entry.getValue().raw();
            for (int index = 0; index < GhostSection.VOLUME; index++) {
                short value = raw[index];
                int x = originX + (index & 15);
                int y = originY + (index >> 8);
                int z = originZ + (index >> 4 & 15);
                if (value != GhostSection.OUTSIDE && scope.contains(x, y, z)) {
                    builder.set(x - minX, y - minY, z - minZ, volume.palette()[value & 0xFFFF]);
                }
            }
        }
        for (Long2ObjectMap.Entry<CompoundTag> entry : volume.blockEntities().long2ObjectEntrySet()) {
            int x = BlockPos.getX(entry.getLongKey());
            int y = BlockPos.getY(entry.getLongKey());
            int z = BlockPos.getZ(entry.getLongKey());
            if (scope.contains(x, y, z) && volume.paletteIndexAt(x, y, z) >= 0) {
                CompoundTag tag = BlockStateNbt.fixBlockEntity(entry.getValue(), volume.dataVersion()).copy();
                tag.putInt("x", x - minX);
                tag.putInt("y", y - minY);
                tag.putInt("z", z - minZ);
                builder.blockEntity(x - minX, y - minY, z - minZ, tag);
            }
        }
        for (CompoundTag entity : entities) {
            ListTag position = entity.getListOrEmpty("Pos");
            ListTag moved = new ListTag();
            moved.add(DoubleTag.valueOf(position.getDoubleOr(0, 0.0) - minX));
            moved.add(DoubleTag.valueOf(position.getDoubleOr(1, 0.0) - minY));
            moved.add(DoubleTag.valueOf(position.getDoubleOr(2, 0.0) - minZ));
            CompoundTag copy = entity.copy();
            copy.put("Pos", moved);
            builder.entity(copy);
        }
        SchematicMetadata metadata = new SchematicMetadata();
        metadata.name = name;
        metadata.author = author;
        metadata.dataVersion = BlockStateNbt.currentDataVersion();
        long now = System.currentTimeMillis();
        metadata.timeCreated = now;
        metadata.timeModified = now;
        return new Schematic(metadata, Map.of(REGION, builder.build()));
    }
}
