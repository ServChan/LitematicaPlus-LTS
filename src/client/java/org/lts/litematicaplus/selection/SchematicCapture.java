package org.lts.litematicaplus.selection;

import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import net.minecraft.client.Minecraft;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.DoubleTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.storage.TagValueOutput;
import net.minecraft.world.phys.AABB;
import org.lts.litematicaplus.schematic.LitematicCodec;
import org.lts.litematicaplus.schematic.RegionBuilder;
import org.lts.litematicaplus.schematic.Schematic;
import org.lts.litematicaplus.schematic.SchematicMetadata;
import org.lts.litematicaplus.schematic.SchematicRegion;
import org.lts.litematicaplus.util.LocalizedException;
import org.lts.litematicaplus.util.Workers;

public final class SchematicCapture {
    public static final long MAX_CLIENT_VOLUME = 16L * 1024L * 1024L;
    public static final long MAX_SERVER_VOLUME = 128L * 1024L * 1024L;

    public record Box(String name, int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
        long volume() {
            return (long) (this.maxX - this.minX + 1) * (this.maxY - this.minY + 1) * (this.maxZ - this.minZ + 1);
        }
    }

    public record Result(Schematic schematic, BlockPos origin, int unloadedChunks) {
    }

    private SchematicCapture() {
    }

    public static List<Box> freeze(List<AreaBox> boxes) {
        List<Box> frozen = new ArrayList<>();
        int counter = 1;
        for (AreaBox box : boxes) {
            if (!box.isComplete()) {
                continue;
            }
            String name = box.name().isBlank()
                    ? Component.translatable("litematicaplus.area.default_box", counter).getString() : box.name();
            String unique = name;
            int suffix = 2;
            while (contains(frozen, unique)) {
                unique = name + " " + suffix++;
            }
            frozen.add(new Box(unique, box.minX(), box.minY(), box.minZ(), box.maxX(), box.maxY(), box.maxZ()));
            counter++;
        }
        return frozen;
    }

    private static boolean contains(List<Box> boxes, String name) {
        for (Box box : boxes) {
            if (box.name().equals(name)) {
                return true;
            }
        }
        return false;
    }

    public static long volume(List<Box> boxes) {
        long total = 0L;
        for (Box box : boxes) {
            total += box.volume();
        }
        return total;
    }

    public static CompletableFuture<Result> capture(Minecraft minecraft, List<Box> boxes, String name, String author,
            String description) {
        if (boxes.isEmpty() || minecraft.level == null) {
            return CompletableFuture.failedFuture(new LocalizedException("litematicaplus.area.error_nothing",
                    "Nothing selected"));
        }
        for (Box box : boxes) {
            if (LitematicCodec.tooLong((long) box.maxX() - box.minX() + 1L, (long) box.maxY() - box.minY() + 1L,
                    (long) box.maxZ() - box.minZ() + 1L) || box.volume() > LitematicCodec.MAX_REGION_VOLUME) {
                return CompletableFuture.failedFuture(new LocalizedException(
                        "litematicaplus.error.selection_too_large", "Selection box '" + box.name() + "' is too large"));
            }
        }
        IntegratedServer server = minecraft.getSingleplayerServer();
        if (server != null) {
            if (volume(boxes) > MAX_SERVER_VOLUME) {
                return CompletableFuture.failedFuture(new LocalizedException(
                        "litematicaplus.error.selection_too_large", "Selection is too large"));
            }
            ResourceKey<Level> dimension = minecraft.level.dimension();
            return server.submit(() -> {
                ServerLevel level = server.getLevel(dimension);
                if (level == null) {
                    throw new CompletionException(new LocalizedException(
                            "litematicaplus.error.dimension_unloaded", "Dimension is not loaded"));
                }
                return snapshot(level, boxes, true);
            }).thenApplyAsync(snapshot -> build(snapshot, boxes, name, author, description), Workers.save());
        }
        if (volume(boxes) > MAX_CLIENT_VOLUME) {
            return CompletableFuture.failedFuture(new LocalizedException(
                    "litematicaplus.error.selection_too_large", "Selection is too large"));
        }
        try {
            Snapshot snapshot = snapshot(minecraft.level, boxes, false);
            return CompletableFuture.supplyAsync(() -> build(snapshot, boxes, name, author, description), Workers.save());
        } catch (RuntimeException exception) {
            return CompletableFuture.failedFuture(exception);
        }
    }

    private record Snapshot(Long2ObjectMap<LevelChunkSection> sections, LongSet loadedChunks,
            Map<BlockPos, CompoundTag> blockEntities, Map<String, List<CompoundTag>> entities) {
    }

    private static boolean inside(List<Box> boxes, BlockPos pos) {
        for (Box box : boxes) {
            if (pos.getX() >= box.minX() && pos.getX() <= box.maxX() && pos.getY() >= box.minY()
                    && pos.getY() <= box.maxY() && pos.getZ() >= box.minZ() && pos.getZ() <= box.maxZ()) {
                return true;
            }
        }
        return false;
    }

    private static Snapshot snapshot(Level level, List<Box> boxes, boolean withEntities) {
        Long2ObjectMap<LevelChunkSection> sections = new Long2ObjectOpenHashMap<>();
        LongSet loaded = new LongOpenHashSet();
        LongSet visited = new LongOpenHashSet();
        Map<BlockPos, CompoundTag> blockEntities = new HashMap<>();
        Map<String, List<CompoundTag>> entities = new HashMap<>();
        for (Box box : boxes) {
            for (int chunkZ = box.minZ() >> 4; chunkZ <= box.maxZ() >> 4; chunkZ++) {
                for (int chunkX = box.minX() >> 4; chunkX <= box.maxX() >> 4; chunkX++) {
                    if (!level.hasChunk(chunkX, chunkZ)) {
                        continue;
                    }
                    long chunkKey = ChunkPos.pack(chunkX, chunkZ);
                    loaded.add(chunkKey);
                    LevelChunk chunk = level.getChunk(chunkX, chunkZ);
                    for (int sectionY = Math.max(box.minY() >> 4, chunk.getMinSectionY());
                            sectionY <= Math.min(box.maxY() >> 4, chunk.getMaxSectionY()); sectionY++) {
                        long key = SectionPos.asLong(chunkX, sectionY, chunkZ);
                        if (sections.containsKey(key)) {
                            continue;
                        }
                        LevelChunkSection section = chunk.getSection(chunk.getSectionIndexFromSectionY(sectionY));
                        if (section != null && !section.hasOnlyAir()) {
                            sections.put(key, section.copy());
                        }
                    }
                    if (!visited.add(chunkKey)) {
                        continue;
                    }
                    for (Map.Entry<BlockPos, BlockEntity> entry : chunk.getBlockEntities().entrySet()) {
                        if (!inside(boxes, entry.getKey())) {
                            continue;
                        }
                        CompoundTag tag = entry.getValue().saveWithFullMetadata(level.registryAccess());
                        tag.remove("x");
                        tag.remove("y");
                        tag.remove("z");
                        blockEntities.put(entry.getKey().immutable(), tag);
                    }
                }
            }
            if (withEntities) {
                entities.put(box.name(), entities(level, box));
            }
        }
        return new Snapshot(sections, loaded, blockEntities, entities);
    }

    private static List<CompoundTag> entities(Level level, Box box) {
        List<CompoundTag> result = new ArrayList<>();
        AABB bounds = new AABB(box.minX(), box.minY(), box.minZ(), box.maxX() + 1.0, box.maxY() + 1.0,
                box.maxZ() + 1.0);
        for (Entity entity : level.getEntities((Entity) null, bounds, candidate -> !(candidate instanceof Player))) {
            if (entity.isPassenger()) {
                continue;
            }
            TagValueOutput output = TagValueOutput.createWithContext(ProblemReporter.DISCARDING, level.registryAccess());
            if (!entity.save(output)) {
                continue;
            }
            CompoundTag tag = output.buildResult();
            ListTag position = new ListTag();
            position.add(DoubleTag.valueOf(entity.getX() - box.minX()));
            position.add(DoubleTag.valueOf(entity.getY() - box.minY()));
            position.add(DoubleTag.valueOf(entity.getZ() - box.minZ()));
            tag.put("Pos", position);
            tag.remove("UUID");
            result.add(tag);
        }
        return result;
    }

    private static Result build(Snapshot snapshot, List<Box> boxes, String name, String author, String description) {
        int originX = Integer.MAX_VALUE;
        int originY = Integer.MAX_VALUE;
        int originZ = Integer.MAX_VALUE;
        for (Box box : boxes) {
            originX = Math.min(originX, box.minX());
            originY = Math.min(originY, box.minY());
            originZ = Math.min(originZ, box.minZ());
        }
        Map<String, SchematicRegion> regions = new LinkedHashMap<>();
        long blocks = 0L;
        LongSet unloaded = new LongOpenHashSet();
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        for (Box box : boxes) {
            int sizeX = box.maxX() - box.minX() + 1;
            int sizeY = box.maxY() - box.minY() + 1;
            int sizeZ = box.maxZ() - box.minZ() + 1;
            for (int chunkZ = box.minZ() >> 4; chunkZ <= box.maxZ() >> 4; chunkZ++) {
                for (int chunkX = box.minX() >> 4; chunkX <= box.maxX() >> 4; chunkX++) {
                    long chunkKey = ChunkPos.pack(chunkX, chunkZ);
                    if (!snapshot.loadedChunks().contains(chunkKey)) {
                        unloaded.add(chunkKey);
                    }
                }
            }
            RegionBuilder builder = new RegionBuilder(box.name(), box.minX() - originX, box.minY() - originY,
                    box.minZ() - originZ, sizeX, sizeY, sizeZ);
            for (int y = 0; y < sizeY; y++) {
                int worldY = box.minY() + y;
                for (int z = 0; z < sizeZ; z++) {
                    int worldZ = box.minZ() + z;
                    LevelChunkSection section = null;
                    long sectionKey = Long.MIN_VALUE;
                    for (int x = 0; x < sizeX; x++) {
                        int worldX = box.minX() + x;
                        long key = SectionPos.asLong(worldX >> 4, worldY >> 4, worldZ >> 4);
                        if (key != sectionKey) {
                            sectionKey = key;
                            section = snapshot.sections().get(key);
                        }
                        if (section == null) {
                            continue;
                        }
                        BlockState state = section.getBlockState(worldX & 15, worldY & 15, worldZ & 15);
                        if (state.isAir()) {
                            continue;
                        }
                        builder.set(x, y, z, state);
                        blocks++;
                        if (state.hasBlockEntity()) {
                            CompoundTag tag = snapshot.blockEntities().get(cursor.set(worldX, worldY, worldZ));
                            if (tag != null) {
                                builder.blockEntity(x, y, z, tag.copy());
                            }
                        }
                    }
                }
            }
            for (CompoundTag entity : snapshot.entities().getOrDefault(box.name(), List.of())) {
                builder.entity(entity);
            }
            regions.put(box.name(), builder.build());
        }
        SchematicMetadata metadata = new SchematicMetadata();
        metadata.name = name;
        metadata.author = author;
        metadata.description = description;
        metadata.timeCreated = System.currentTimeMillis();
        metadata.timeModified = metadata.timeCreated;
        metadata.totalBlocks = Math.max(blocks, 0L);
        metadata.format = "litematic";
        return new Result(new Schematic(metadata, regions), new BlockPos(originX, originY, originZ), unloaded.size());
    }
}
