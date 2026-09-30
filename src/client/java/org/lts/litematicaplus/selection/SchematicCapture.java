package org.lts.litematicaplus.selection;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import net.minecraft.client.Minecraft;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.DoubleTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.TagValueOutput;
import net.minecraft.world.phys.AABB;
import org.lts.litematicaplus.schematic.RegionBuilder;
import org.lts.litematicaplus.schematic.Schematic;
import org.lts.litematicaplus.schematic.SchematicMetadata;
import org.lts.litematicaplus.schematic.SchematicRegion;

public final class SchematicCapture {
    public static final long MAX_CLIENT_VOLUME = 16L * 1024L * 1024L;

    public record Box(String name, int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
        long volume() {
            return (long) (this.maxX - this.minX + 1) * (this.maxY - this.minY + 1) * (this.maxZ - this.minZ + 1);
        }
    }

    public record Result(Schematic schematic, BlockPos origin) {
    }

    private SchematicCapture() {
    }

    public static List<Box> freeze(List<AreaBox> boxes) {
        List<Box> frozen = new ArrayList<>();
        int counter = 1;
        for (AreaBox box : boxes) {
            if (box.isEmpty()) {
                continue;
            }
            String name = box.name().isBlank() ? "Box " + counter : box.name();
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
            return CompletableFuture.failedFuture(new IllegalStateException("Nothing selected"));
        }
        IntegratedServer server = minecraft.getSingleplayerServer();
        if (server != null) {
            ResourceKey<Level> dimension = minecraft.level.dimension();
            return server.submit(() -> {
                ServerLevel level = server.getLevel(dimension);
                if (level == null) {
                    throw new IllegalStateException("Dimension is not loaded");
                }
                return read(level, boxes, name, author, description, true);
            });
        }
        if (volume(boxes) > MAX_CLIENT_VOLUME) {
            return CompletableFuture.failedFuture(new IllegalStateException("Selection is too large"));
        }
        try {
            return CompletableFuture.completedFuture(read(minecraft.level, boxes, name, author, description, false));
        } catch (RuntimeException exception) {
            return CompletableFuture.failedFuture(exception);
        }
    }

    private static Result read(Level level, List<Box> boxes, String name, String author, String description,
            boolean withEntities) {
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
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        for (Box box : boxes) {
            int sizeX = box.maxX() - box.minX() + 1;
            int sizeY = box.maxY() - box.minY() + 1;
            int sizeZ = box.maxZ() - box.minZ() + 1;
            RegionBuilder builder = new RegionBuilder(box.name(), box.minX() - originX, box.minY() - originY,
                    box.minZ() - originZ, sizeX, sizeY, sizeZ);
            for (int y = 0; y < sizeY; y++) {
                for (int z = 0; z < sizeZ; z++) {
                    for (int x = 0; x < sizeX; x++) {
                        cursor.set(box.minX() + x, box.minY() + y, box.minZ() + z);
                        BlockState state = level.getBlockState(cursor);
                        if (state.isAir()) {
                            continue;
                        }
                        builder.set(x, y, z, state);
                        blocks++;
                        if (state.hasBlockEntity()) {
                            BlockEntity blockEntity = level.getBlockEntity(cursor);
                            if (blockEntity != null) {
                                CompoundTag tag = blockEntity.saveWithFullMetadata(level.registryAccess());
                                tag.remove("x");
                                tag.remove("y");
                                tag.remove("z");
                                builder.blockEntity(x, y, z, tag);
                            }
                        }
                    }
                }
            }
            if (withEntities) {
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
                    builder.entity(tag);
                }
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
        return new Result(new Schematic(metadata, regions), new BlockPos(originX, originY, originZ));
    }
}
