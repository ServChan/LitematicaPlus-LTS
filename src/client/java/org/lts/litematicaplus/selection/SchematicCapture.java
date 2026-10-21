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
import net.minecraft.nbt.FloatTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.ProblemReporter;
import net.minecraft.core.Rotations;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.storage.TagValueOutput;
import net.minecraft.world.phys.AABB;
import org.lts.litematicaplus.LitematicaPlusConfig;
import org.lts.litematicaplus.schematic.LitematicCodec;
import org.lts.litematicaplus.schematic.RegionBuilder;
import org.lts.litematicaplus.schematic.Schematic;
import org.lts.litematicaplus.schematic.SchematicMetadata;
import org.lts.litematicaplus.schematic.SchematicRegion;
import org.lts.litematicaplus.util.LocalizedException;
import org.lts.litematicaplus.util.Workers;
import org.lts.litematicaplus.world.WorldCache;

public final class SchematicCapture {
    public static final long MAX_TOTAL_VOLUME = LitematicCodec.MAX_TOTAL_VOLUME;
    private static final long SECTION_COPY_BYTES = 8192L;
    private static final long BYTES_PER_CELL = 3L;
    private static final long SNAPSHOT_BUDGET_NANOS = 5_000_000L;

    public record Box(String name, int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
        long volume() {
            return (long) (this.maxX - this.minX + 1) * (this.maxY - this.minY + 1) * (this.maxZ - this.minZ + 1);
        }
    }

    public record Result(Schematic schematic, BlockPos origin, int unloadedChunks, int voxyChunks) {
    }

    private record Snapshot(Long2ObjectMap<LevelChunkSection> sections, LongSet loadedChunks,
            Map<BlockPos, CompoundTag> blockEntities, Map<String, List<CompoundTag>> entities) {
    }

    private record Cached(BlockState[] palette, short[] ids) {
        BlockState get(int x, int y, int z) {
            return this.palette[this.ids[y << 8 | z << 4 | x]];
        }
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

    static long estimatedBytes(List<Box> boxes) {
        long sections = 0L;
        for (Box box : boxes) {
            long columns = (long) ((box.maxX() >> 4) - (box.minX() >> 4) + 1) * ((box.maxZ() >> 4) - (box.minZ() >> 4) + 1);
            sections += columns * ((box.maxY() >> 4) - (box.minY() >> 4) + 1);
        }
        return volume(boxes) * BYTES_PER_CELL + sections * SECTION_COPY_BYTES;
    }

    private static LocalizedException memoryProblem(List<Box> boxes) {
        Runtime runtime = Runtime.getRuntime();
        long free = runtime.maxMemory() - (runtime.totalMemory() - runtime.freeMemory());
        long needed = estimatedBytes(boxes);
        if (needed <= free * 8L / 10L) {
            return null;
        }
        long megabyte = 1024L * 1024L;
        return new LocalizedException("litematicaplus.error.selection_memory",
                "Not enough memory to save the selection: " + needed / megabyte + " MB needed, " + free / megabyte
                        + " MB free", needed / megabyte, free / megabyte);
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
        if (volume(boxes) > MAX_TOTAL_VOLUME) {
            return CompletableFuture.failedFuture(new LocalizedException(
                    "litematicaplus.error.selection_too_large", "Selection is too large"));
        }
        LocalizedException memory = memoryProblem(boxes);
        if (memory != null) {
            return CompletableFuture.failedFuture(memory);
        }
        Level fallback = LitematicaPlusConfig.current().useWorldCache && WorldCache.available() ? minecraft.level : null;
        IntegratedServer server = minecraft.getSingleplayerServer();
        if (server != null) {
            ResourceKey<Level> dimension = minecraft.level.dimension();
            return server.submit(() -> {
                ServerLevel level = server.getLevel(dimension);
                if (level == null) {
                    throw new CompletionException(new LocalizedException(
                            "litematicaplus.error.dimension_unloaded", "Dimension is not loaded"));
                }
                Snapshotter snapshotter = new Snapshotter(level, boxes, true);
                snapshotter.step(Long.MAX_VALUE);
                return snapshotter.result();
            }).thenApplyAsync(snapshot -> build(snapshot, boxes, name, author, description, fallback), Workers.save());
        }
        CompletableFuture<Snapshot> snapshot = new CompletableFuture<>();
        stepClient(minecraft, new Snapshotter(minecraft.level, boxes, true), snapshot);
        return snapshot.thenApplyAsync(taken -> build(taken, boxes, name, author, description, fallback), Workers.save());
    }

    private static void stepClient(Minecraft minecraft, Snapshotter snapshotter, CompletableFuture<Snapshot> result) {
        if (minecraft.level != snapshotter.level) {
            result.completeExceptionally(new LocalizedException("litematicaplus.error.dimension_unloaded",
                    "The world changed while the selection was being read"));
            return;
        }
        try {
            if (snapshotter.step(System.nanoTime() + SNAPSHOT_BUDGET_NANOS)) {
                result.complete(snapshotter.result());
                return;
            }
        } catch (RuntimeException exception) {
            result.completeExceptionally(exception);
            return;
        }
        CompletableFuture.delayedExecutor(10L, java.util.concurrent.TimeUnit.MILLISECONDS)
                .execute(() -> minecraft.execute(() -> stepClient(minecraft, snapshotter, result)));
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

    private static final class Snapshotter {
        private final Level level;
        private final List<Box> boxes;
        private final boolean withEntities;
        private final Long2ObjectMap<LevelChunkSection> sections = new Long2ObjectOpenHashMap<>();
        private final LongSet loaded = new LongOpenHashSet();
        private final LongSet visited = new LongOpenHashSet();
        private final Map<BlockPos, CompoundTag> blockEntities = new HashMap<>();
        private final Map<String, List<CompoundTag>> entities = new HashMap<>();
        private int boxIndex;
        private int chunkX;
        private int chunkZ;
        private boolean started;

        private Snapshotter(Level level, List<Box> boxes, boolean withEntities) {
            this.level = level;
            this.boxes = boxes;
            this.withEntities = withEntities;
        }

        private Snapshot result() {
            return new Snapshot(this.sections, this.loaded, this.blockEntities, this.entities);
        }

        private boolean step(long deadline) {
            while (this.boxIndex < this.boxes.size()) {
                Box box = this.boxes.get(this.boxIndex);
                if (!this.started) {
                    this.started = true;
                    this.chunkX = box.minX() >> 4;
                    this.chunkZ = box.minZ() >> 4;
                }
                while (this.chunkZ <= box.maxZ() >> 4) {
                    column(box, this.chunkX, this.chunkZ);
                    if (++this.chunkX > box.maxX() >> 4) {
                        this.chunkX = box.minX() >> 4;
                        this.chunkZ++;
                    }
                    if (System.nanoTime() > deadline) {
                        return false;
                    }
                }
                if (this.withEntities) {
                    this.entities.put(box.name(), entities(this.level, box));
                }
                this.boxIndex++;
                this.started = false;
            }
            return true;
        }

        private void column(Box box, int chunkX, int chunkZ) {
            if (!this.level.hasChunk(chunkX, chunkZ)) {
                return;
            }
            long chunkKey = ChunkPos.pack(chunkX, chunkZ);
            this.loaded.add(chunkKey);
            LevelChunk chunk = this.level.getChunk(chunkX, chunkZ);
            for (int sectionY = Math.max(box.minY() >> 4, chunk.getMinSectionY());
                    sectionY <= Math.min(box.maxY() >> 4, chunk.getMaxSectionY()); sectionY++) {
                long key = SectionPos.asLong(chunkX, sectionY, chunkZ);
                if (this.sections.containsKey(key)) {
                    continue;
                }
                LevelChunkSection section = chunk.getSection(chunk.getSectionIndexFromSectionY(sectionY));
                if (section != null && !section.hasOnlyAir()) {
                    this.sections.put(key, section.copy());
                }
            }
            if (!this.visited.add(chunkKey)) {
                return;
            }
            for (Map.Entry<BlockPos, BlockEntity> entry : chunk.getBlockEntities().entrySet()) {
                if (!inside(this.boxes, entry.getKey())) {
                    continue;
                }
                CompoundTag tag = entry.getValue().saveWithFullMetadata(this.level.registryAccess());
                tag.remove("x");
                tag.remove("y");
                tag.remove("z");
                this.blockEntities.put(entry.getKey().immutable(), tag);
            }
        }
    }

    private static List<CompoundTag> entities(Level level, Box box) {
        List<CompoundTag> result = new ArrayList<>();
        AABB bounds = new AABB(box.minX(), box.minY(), box.minZ(), box.maxX() + 1.0, box.maxY() + 1.0,
                box.maxZ() + 1.0);
        for (Entity entity : level.getEntities((Entity) null, bounds, candidate -> !(candidate instanceof Player))) {
            if (entity.isPassenger()) {
                continue;
            }
            CompoundTag tag = saved(level, entity);
            if (tag == null) {
                continue;
            }
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

    private static CompoundTag saved(Level level, Entity entity) {
        try {
            TagValueOutput output = TagValueOutput.createWithContext(ProblemReporter.DISCARDING, level.registryAccess());
            return entity.save(output) ? output.buildResult() : null;
        } catch (RuntimeException exception) {
            return outline(level, entity);
        }
    }

    private static CompoundTag outline(Level level, Entity entity) {
        CompoundTag tag = new CompoundTag();
        tag.putString("id", EntityType.getKey(entity.getType()).toString());
        ListTag rotation = new ListTag();
        rotation.add(FloatTag.valueOf(entity.getYRot()));
        rotation.add(FloatTag.valueOf(entity.getXRot()));
        tag.put("Rotation", rotation);
        if (entity.isInvisible()) {
            tag.putBoolean("Invisible", true);
        }
        if (entity instanceof LivingEntity living) {
            CompoundTag equipment = new CompoundTag();
            var ops = level.registryAccess().createSerializationContext(NbtOps.INSTANCE);
            for (EquipmentSlot slot : EquipmentSlot.values()) {
                ItemStack stack = living.getItemBySlot(slot);
                if (stack.isEmpty()) {
                    continue;
                }
                ItemStack.CODEC.encodeStart(ops, stack).result()
                        .ifPresent(item -> equipment.put(slot.getSerializedName(), item));
            }
            if (!equipment.isEmpty()) {
                tag.put("equipment", equipment);
            }
        }
        if (entity instanceof ArmorStand stand) {
            tag.putBoolean("Small", stand.isSmall());
            tag.putBoolean("ShowArms", stand.showArms());
            tag.putBoolean("NoBasePlate", !stand.showBasePlate());
            tag.putBoolean("Marker", stand.isMarker());
            CompoundTag pose = new CompoundTag();
            pose.put("Head", rotations(stand.getHeadPose()));
            pose.put("Body", rotations(stand.getBodyPose()));
            pose.put("LeftArm", rotations(stand.getLeftArmPose()));
            pose.put("RightArm", rotations(stand.getRightArmPose()));
            pose.put("LeftLeg", rotations(stand.getLeftLegPose()));
            pose.put("RightLeg", rotations(stand.getRightLegPose()));
            tag.put("Pose", pose);
        }
        return tag;
    }

    private static Tag rotations(Rotations value) {
        ListTag list = new ListTag();
        list.add(FloatTag.valueOf(value.x()));
        list.add(FloatTag.valueOf(value.y()));
        list.add(FloatTag.valueOf(value.z()));
        return list;
    }

    private static Cached compress(BlockState[] states) {
        List<BlockState> palette = new ArrayList<>();
        java.util.IdentityHashMap<BlockState, Integer> lookup = new java.util.IdentityHashMap<>();
        short[] ids = new short[states.length];
        for (int i = 0; i < states.length; i++) {
            Integer id = lookup.get(states[i]);
            if (id == null) {
                id = palette.size();
                palette.add(states[i]);
                lookup.put(states[i], id);
            }
            ids[i] = (short) (int) id;
        }
        return new Cached(palette.toArray(BlockState[]::new), ids);
    }

    private static Long2ObjectMap<Cached> fromVoxy(Snapshot snapshot, List<Box> boxes, Level fallback, LongSet covered) {
        Long2ObjectMap<Cached> cached = new Long2ObjectOpenHashMap<>();
        if (fallback == null) {
            return cached;
        }
        for (Box box : boxes) {
            for (int chunkZ = box.minZ() >> 4; chunkZ <= box.maxZ() >> 4; chunkZ++) {
                for (int chunkX = box.minX() >> 4; chunkX <= box.maxX() >> 4; chunkX++) {
                    long chunkKey = ChunkPos.pack(chunkX, chunkZ);
                    if (snapshot.loadedChunks().contains(chunkKey)) {
                        continue;
                    }
                    for (int sectionY = box.minY() >> 4; sectionY <= box.maxY() >> 4; sectionY++) {
                        long key = SectionPos.asLong(chunkX, sectionY, chunkZ);
                        if (cached.containsKey(key)) {
                            continue;
                        }
                        WorldCache.Snapshot section = WorldCache.section(fallback, chunkX, sectionY, chunkZ);
                        if (section != null) {
                            cached.put(key, compress(section.world()));
                            covered.add(chunkKey);
                        }
                    }
                }
            }
        }
        return cached;
    }

    private static Result build(Snapshot snapshot, List<Box> boxes, String name, String author, String description,
            Level fallback) {
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
        LongSet covered = new LongOpenHashSet();
        Long2ObjectMap<Cached> cached = fromVoxy(snapshot, boxes, fallback, covered);
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        for (Box box : boxes) {
            int sizeX = box.maxX() - box.minX() + 1;
            int sizeY = box.maxY() - box.minY() + 1;
            int sizeZ = box.maxZ() - box.minZ() + 1;
            for (int chunkZ = box.minZ() >> 4; chunkZ <= box.maxZ() >> 4; chunkZ++) {
                for (int chunkX = box.minX() >> 4; chunkX <= box.maxX() >> 4; chunkX++) {
                    long chunkKey = ChunkPos.pack(chunkX, chunkZ);
                    if (!snapshot.loadedChunks().contains(chunkKey) && !covered.contains(chunkKey)) {
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
                    Cached fromCache = null;
                    long sectionKey = Long.MIN_VALUE;
                    for (int x = 0; x < sizeX; x++) {
                        int worldX = box.minX() + x;
                        long key = SectionPos.asLong(worldX >> 4, worldY >> 4, worldZ >> 4);
                        if (key != sectionKey) {
                            sectionKey = key;
                            section = snapshot.sections().get(key);
                            fromCache = section == null ? cached.get(key) : null;
                        }
                        if (section == null && fromCache == null) {
                            continue;
                        }
                        BlockState state = section != null ? section.getBlockState(worldX & 15, worldY & 15, worldZ & 15)
                                : fromCache.get(worldX & 15, worldY & 15, worldZ & 15);
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
        return new Result(new Schematic(metadata, regions), new BlockPos(originX, originY, originZ), unloaded.size(),
                covered.size());
    }
}
