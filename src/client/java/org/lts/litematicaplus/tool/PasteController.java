package org.lts.litematicaplus.tool;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.DoubleTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.permissions.Permissions;
import net.minecraft.util.Mth;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntitySpawnRequest;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.phys.AABB;
import org.lts.litematicaplus.LitematicaPlusClient;
import org.lts.litematicaplus.LitematicaPlusConfig;
import org.lts.litematicaplus.engine.GhostEngine;
import org.lts.litematicaplus.engine.PlacementRuntime;
import org.lts.litematicaplus.schematic.BlockStateNbt;
import org.lts.litematicaplus.selection.AreaBox;
import org.lts.litematicaplus.util.Notify;
import org.lts.litematicaplus.world.GhostSection;
import org.lts.litematicaplus.world.LayerRange;
import org.lts.litematicaplus.world.PlacementVolume;

public final class PasteController {
    public static final long MAX_DELETE_VOLUME = 64L * 1024L * 1024L;
    private static final int DIRECT_BLOCKS_PER_TICK = 4096;
    private static final java.util.Set<String> REPORTED = java.util.concurrent.ConcurrentHashMap.newKeySet();
    private static final int DIRECT_DELETE_BLOCKS_PER_TICK = 16384;
    private static final int MAX_COMMAND_LENGTH = 255;
    private static final int MAX_FILL_VOLUME = 32768;
    private static final int UNPRIVILEGED_COMMAND_INTERVAL = 22;
    private static final int FILL_COMMAND_INTERVAL = 4;
    private static final int PLACE_FLAGS = Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE
            | Block.UPDATE_SUPPRESS_DROPS | Block.UPDATE_SKIP_BLOCK_ENTITY_SIDEEFFECTS | Block.UPDATE_SKIP_ON_PLACE;

    private record Operation(BlockPos pos, BlockState state, CompoundTag data, Block only) {
    }

    private record EntityPaste(CompoundTag tag, double x, double y, double z, Rotation rotation, Mirror mirror) {
    }

    private static final class Job {
        PlacementVolume volume;
        long[] keys = new long[0];
        int keyIndex;
        int cell;
        ArrayDeque<int[]> deleteBoxes = new ArrayDeque<>();
        BlockState fillState = Blocks.AIR.defaultBlockState();
        Block replaceFrom;
        boolean direct;
        boolean privileged;
        int commandCooldown;
        ResourceKey<Level> dimension;
        ClientLevel level;
        final AtomicInteger inFlight = new AtomicInteger();
        final AtomicLong failed = new AtomicLong();
        LayerRange layer = LayerRange.ALL;
        List<EntityPaste> entities = List.of();
        boolean finishing;
        LitematicaPlusConfig.PasteReplace replace;
        boolean withData;
        long changed;
        long skipped;
        long total;
        long visited;
    }

    private static Job job;

    private PasteController() {
    }

    public static boolean active() {
        return job != null;
    }

    public static double progress() {
        Job current = job;
        if (current == null || current.total <= 0L) {
            return 0.0;
        }
        return Math.min(1.0, (double) current.visited / current.total);
    }

    public static void cancel() {
        if (job != null) {
            job = null;
            Notify.actionBar("litematicaplus.message.paste_cancelled");
        }
    }

    public static void startPaste(Minecraft minecraft, PlacementRuntime runtime) {
        startPaste(minecraft, runtime, minecraft.hasSingleplayerServer());
    }

    public static void startPaste(Minecraft minecraft, PlacementRuntime runtime, boolean direct) {
        if (runtime == null || runtime.volume() == null) {
            Notify.actionBar("litematicaplus.message.no_placement");
            return;
        }
        boolean useDirect = direct && minecraft.hasSingleplayerServer();
        if (!allowed(minecraft, useDirect, "setblock")) {
            return;
        }
        if (!upToDate(runtime)) {
            Notify.actionBar("litematicaplus.message.paste_rebuilding");
            return;
        }
        PlacementVolume volume = runtime.volume();
        Job created = baseJob(minecraft, useDirect);
        created.volume = volume;
        created.layer = GhostEngine.get().layer();
        if (useDirect && LitematicaPlusConfig.current().pasteEntities) {
            created.entities = entitiesOf(volume, created.layer);
        }
        long[] keys = volume.sections().keySet().toLongArray();
        Long[] boxed = new Long[keys.length];
        for (int i = 0; i < keys.length; i++) {
            boxed[i] = keys[i];
        }
        Arrays.sort(boxed, (a, b) -> Integer.compare(SectionPos.y(a), SectionPos.y(b)));
        for (int i = 0; i < keys.length; i++) {
            keys[i] = boxed[i];
        }
        created.keys = keys;
        created.total = (long) keys.length * GhostSection.VOLUME;
        job = created;
        Notify.actionBar("litematicaplus.message.paste_started", runtime.placement().name());
        if (!useDirect && created.withData && !volume.blockEntities().isEmpty()) {
            Notify.chat(Component.translatable("litematicaplus.message.paste_no_data"));
        }
        if (!useDirect && !created.privileged) {
            Notify.chat(Component.translatable("litematicaplus.message.paste_slow"));
        }
    }

    public static boolean upToDate(PlacementRuntime runtime) {
        PlacementVolume volume = runtime == null ? null : runtime.volume();
        return volume != null && volume.layout().equals(runtime.placement().layout());
    }

    public static void startDelete(Minecraft minecraft, List<AreaBox> boxes) {
        startFill(minecraft, boxes, Blocks.AIR.defaultBlockState(), null);
    }

    public static void startFill(Minecraft minecraft, List<AreaBox> boxes, BlockState state, Block replaceFrom) {
        boolean direct = minecraft.hasSingleplayerServer();
        if (!allowed(minecraft, direct, "fill")) {
            return;
        }
        List<AreaBox> complete = new ArrayList<>();
        long volume = 0L;
        for (AreaBox box : boxes) {
            if (box.isComplete()) {
                complete.add(box);
                volume += box.volume();
            }
        }
        if (complete.isEmpty()) {
            Notify.actionBar("litematicaplus.message.no_selection");
            return;
        }
        if (volume > MAX_DELETE_VOLUME) {
            Notify.actionBar("litematicaplus.message.delete_too_large", volume, MAX_DELETE_VOLUME);
            return;
        }
        Job created = baseJob(minecraft, direct);
        created.fillState = state;
        created.replaceFrom = replaceFrom;
        long total = 0L;
        for (AreaBox box : complete) {
            for (int[] part : split(box)) {
                created.deleteBoxes.add(part);
                total += (long) (part[3] - part[0] + 1) * (part[4] - part[1] + 1) * (part[5] - part[2] + 1);
            }
        }
        created.total = total;
        job = created;
        if (replaceFrom != null) {
            Notify.actionBar("litematicaplus.message.replace_started", replaceFrom.getName(), state.getBlock().getName());
        } else if (state.isAir()) {
            Notify.actionBar("litematicaplus.message.delete_started");
        } else {
            Notify.actionBar("litematicaplus.message.fill_started", state.getBlock().getName());
        }
        if (!direct && !created.privileged) {
            Notify.chat(Component.translatable("litematicaplus.message.paste_slow"));
        }
    }

    private static boolean allowed(Minecraft minecraft, boolean direct, String command) {
        if (minecraft.player == null || minecraft.level == null) {
            return false;
        }
        if (!minecraft.player.hasInfiniteMaterials()) {
            Notify.actionBar("litematicaplus.message.creative_required");
            return false;
        }
        if (job != null) {
            Notify.actionBar("litematicaplus.message.paste_busy");
            return false;
        }
        if (!direct && minecraft.player.connection.getCommands().getRoot().getChild(command) == null) {
            Notify.actionBar("litematicaplus.message.no_command_permission", "/" + command);
            return false;
        }
        return true;
    }

    private static Job baseJob(Minecraft minecraft, boolean direct) {
        LitematicaPlusConfig config = LitematicaPlusConfig.current();
        Job created = new Job();
        created.direct = direct;
        created.privileged = minecraft.hasSingleplayerServer()
                || minecraft.player.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER);
        created.dimension = minecraft.level.dimension();
        created.level = minecraft.level;
        created.replace = config.pasteReplace;
        created.withData = config.pasteBlockEntityData;
        return created;
    }

    private static List<int[]> split(AreaBox box) {
        List<int[]> parts = new ArrayList<>();
        int step = 32;
        for (int x = box.minX(); x <= box.maxX(); x += step) {
            for (int y = box.minY(); y <= box.maxY(); y += step) {
                for (int z = box.minZ(); z <= box.maxZ(); z += step) {
                    parts.add(new int[]{x, y, z, Math.min(box.maxX(), x + step - 1),
                            Math.min(box.maxY(), y + step - 1), Math.min(box.maxZ(), z + step - 1)});
                }
            }
        }
        return parts;
    }

    public static void tick(Minecraft minecraft) {
        Job current = job;
        if (current == null) {
            return;
        }
        if (minecraft.player == null || minecraft.level == null || minecraft.level != current.level) {
            job = null;
            return;
        }
        if (!minecraft.player.hasInfiniteMaterials()) {
            job = null;
            Notify.actionBar("litematicaplus.message.creative_required");
            return;
        }
        if (current.direct && current.inFlight.get() > 0) {
            return;
        }
        if (current.finishing) {
            job = null;
            long failed = current.failed.get();
            Notify.actionBar(current.direct ? "litematicaplus.message.paste_finished" : "litematicaplus.message.paste_sent",
                    current.changed - failed, current.skipped + failed);
            return;
        }
        try {
            if (current.volume != null ? tickPaste(minecraft, current) : tickDelete(minecraft, current)) {
                if (!current.entities.isEmpty()) {
                    dispatchEntities(minecraft, current, current.entities);
                    current.entities = List.of();
                }
                current.finishing = true;
            }
        } catch (RuntimeException exception) {
            job = null;
            LitematicaPlusClient.LOGGER.error("Could not continue the paste; it was stopped", exception);
            Notify.actionBar("litematicaplus.message.paste_failed");
        }
    }

    private static int commandBudget(Job current, int perTick, int privilegedInterval) {
        if (current.commandCooldown > 0) {
            current.commandCooldown--;
            return 0;
        }
        if (!current.privileged) {
            current.commandCooldown = UNPRIVILEGED_COMMAND_INTERVAL;
            return 1;
        }
        current.commandCooldown = privilegedInterval;
        return perTick;
    }

    private static boolean tickPaste(Minecraft minecraft, Job current) {
        ClientLevel level = minecraft.level;
        PlacementVolume volume = current.volume;
        BlockState[] palette = volume.palette();
        int budget = current.direct ? DIRECT_BLOCKS_PER_TICK
                : commandBudget(current, LitematicaPlusConfig.current().pasteCommandsPerTick, 0);
        List<Operation> operations = current.direct ? new ArrayList<>() : null;
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        int scanned = 0;
        while (current.keyIndex < current.keys.length && budget > 0 && scanned < 65536) {
            long key = current.keys[current.keyIndex];
            GhostSection section = volume.section(key);
            int originX = SectionPos.x(key) << 4;
            int originY = SectionPos.y(key) << 4;
            int originZ = SectionPos.z(key) << 4;
            boolean inScope = current.layer.unrestricted() || current.layer.intersects(originX, originY, originZ,
                    originX + 15, originY + 15, originZ + 15);
            if (section == null || !inScope || !level.hasChunk(originX >> 4, originZ >> 4)) {
                if (section != null && inScope) {
                    current.skipped += section.solidCount();
                }
                current.visited += GhostSection.VOLUME - current.cell;
                current.keyIndex++;
                current.cell = 0;
                continue;
            }
            short[] raw = section.raw();
            while (current.cell < GhostSection.VOLUME && budget > 0 && scanned < 65536) {
                int index = current.cell;
                short value = raw[index];
                int x = originX + (index & 15);
                int y = originY + (index >> 8);
                int z = originZ + (index >> 4 & 15);
                if (value == GhostSection.OUTSIDE || !current.layer.contains(x, y, z)) {
                    current.cell++;
                    current.visited++;
                    scanned++;
                    continue;
                }
                BlockState state = palette[value & 0xFFFF];
                cursor.set(x, y, z);
                BlockState world = level.getBlockState(cursor);
                if (current.direct) {
                    CompoundTag data = current.withData && state.hasBlockEntity()
                            ? volume.blockEntities().get(cursor.asLong()) : null;
                    if (data != null) {
                        data = BlockStateNbt.fixBlockEntity(data, volume.dataVersion());
                    }
                    boolean refreshData = data != null && world == state
                            && current.replace != LitematicaPlusConfig.PasteReplace.INTO_AIR_ONLY;
                    if (refreshData || wanted(world, state, current.replace)) {
                        operations.add(new Operation(cursor.immutable(), state, data, null));
                        current.changed++;
                        budget--;
                    }
                    current.cell++;
                    current.visited++;
                    scanned++;
                    continue;
                }
                if (!wanted(world, state, current.replace)) {
                    current.cell++;
                    current.visited++;
                    scanned++;
                    continue;
                }
                int run = 1;
                while ((index + run & 15) != 0 && run < 16 - (index & 15)) {
                    short next = raw[index + run];
                    if (next != value || !current.layer.contains(x + run, y, z)) {
                        break;
                    }
                    cursor.set(x + run, y, z);
                    if (!wanted(level.getBlockState(cursor), state, current.replace)) {
                        break;
                    }
                    run++;
                }
                String text = BlockStateNbt.format(state);
                String command = run == 1
                        ? "setblock " + x + " " + y + " " + z + " " + text + " strict"
                        : "fill " + x + " " + y + " " + z + " " + (x + run - 1) + " " + y + " " + z + " " + text + " strict";
                if (command.length() <= MAX_COMMAND_LENGTH) {
                    minecraft.player.connection.sendCommand(command);
                    current.changed += run;
                    budget--;
                } else {
                    current.skipped += run;
                }
                current.cell += run;
                current.visited += run;
                scanned += run;
            }
            if (current.cell >= GhostSection.VOLUME) {
                current.cell = 0;
                current.keyIndex++;
            }
        }
        if (operations != null && !operations.isEmpty()) {
            dispatch(minecraft, current, operations);
        }
        return current.keyIndex >= current.keys.length;
    }

    private static boolean wanted(BlockState world, BlockState state, LitematicaPlusConfig.PasteReplace replace) {
        if (world == state) {
            return false;
        }
        return switch (replace) {
            case ALL -> !(world.isAir() && state.isAir());
            case NON_AIR_ONLY -> !state.isAir();
            case INTO_AIR_ONLY -> world.isAir() && !state.isAir();
        };
    }

    private static boolean chunksLoaded(ClientLevel level, int[] box) {
        for (int chunkX = box[0] >> 4; chunkX <= box[3] >> 4; chunkX++) {
            for (int chunkZ = box[2] >> 4; chunkZ <= box[5] >> 4; chunkZ++) {
                if (!level.hasChunk(chunkX, chunkZ)) {
                    return false;
                }
            }
        }
        return true;
    }

    private static boolean tickDelete(Minecraft minecraft, Job current) {
        ClientLevel level = minecraft.level;
        int commands = current.direct ? 0 : Math.min(1, commandBudget(current, 1, FILL_COMMAND_INTERVAL));
        int blockBudget = DIRECT_DELETE_BLOCKS_PER_TICK;
        List<Operation> operations = current.direct ? new ArrayList<>() : null;
        BlockState fill = current.fillState;
        Block from = current.replaceFrom;
        String text = BlockStateNbt.format(fill);
        String mode = from == null ? " strict" : " replace " + BuiltInRegistries.BLOCK.getKey(from);
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        int scannedBoxes = 0;
        while (!current.deleteBoxes.isEmpty() && (current.direct ? blockBudget > 0 && scannedBoxes < 4 : commands > 0)) {
            int[] box = current.deleteBoxes.poll();
            long volume = (long) (box[3] - box[0] + 1) * (box[4] - box[1] + 1) * (box[5] - box[2] + 1);
            current.visited += volume;
            if (volume > MAX_FILL_VOLUME || !chunksLoaded(level, box)) {
                current.skipped += volume;
                continue;
            }
            if (!current.direct) {
                String command = "fill " + box[0] + " " + box[1] + " " + box[2] + " " + box[3] + " " + box[4] + " "
                        + box[5] + " " + text + mode;
                if (command.length() > MAX_COMMAND_LENGTH) {
                    current.skipped += volume;
                    continue;
                }
                minecraft.player.connection.sendCommand(command);
                current.changed += volume;
                commands--;
                continue;
            }
            scannedBoxes++;
            for (int y = box[4]; y >= box[1]; y--) {
                for (int z = box[2]; z <= box[5]; z++) {
                    for (int x = box[0]; x <= box[3]; x++) {
                        cursor.set(x, y, z);
                        BlockState world = level.getBlockState(cursor);
                        if (world != fill && (from == null ? !(fill.isAir() && world.isAir()) : world.is(from))) {
                            operations.add(new Operation(cursor.immutable(), fill, null, from));
                            blockBudget--;
                        }
                    }
                }
            }
        }
        if (operations != null && !operations.isEmpty()) {
            current.changed += operations.size();
            dispatch(minecraft, current, operations);
        }
        return current.deleteBoxes.isEmpty();
    }

    static List<EntityPaste> entitiesOf(PlacementVolume volume, LayerRange layer) {
        List<EntityPaste> result = new ArrayList<>();
        for (PlacementVolume.EntitySeed seed : volume.entities()) {
            if (layer.contains(Mth.floor(seed.x()), Mth.floor(seed.y()), Mth.floor(seed.z()))) {
                result.add(new EntityPaste(seed.tag(), seed.x(), seed.y(), seed.z(), seed.rotation(), seed.mirror()));
            }
        }
        return result;
    }

    private static void dispatchEntities(Minecraft minecraft, Job owner, List<EntityPaste> entities) {
        IntegratedServer server = minecraft.getSingleplayerServer();
        if (server == null) {
            return;
        }
        ResourceKey<Level> dimension = owner.dimension;
        AtomicInteger inFlight = owner.inFlight;
        AtomicLong failed = owner.failed;
        owner.changed += entities.size();
        inFlight.incrementAndGet();
        server.execute(() -> {
            try {
                ServerLevel level = server.getLevel(dimension);
                if (level == null) {
                    failed.addAndGet(entities.size());
                    return;
                }
                for (EntityPaste paste : entities) {
                    if (!spawn(level, paste)) {
                        failed.incrementAndGet();
                    }
                }
            } finally {
                inFlight.decrementAndGet();
            }
        });
    }

    private static boolean spawn(ServerLevel level, EntityPaste paste) {
        try {
            String id = paste.tag().getStringOr("id", "");
            AABB around = new AABB(paste.x() - 0.5, paste.y() - 0.5, paste.z() - 0.5, paste.x() + 0.5, paste.y() + 0.5,
                    paste.z() + 0.5);
            boolean present = !level.getEntities((Entity) null, around, existing -> !(existing instanceof Player)
                    && id.equals(EntityType.getKey(existing.getType()).toString())).isEmpty();
            if (present) {
                return true;
            }
            CompoundTag tag = paste.tag().copy();
            ListTag position = new ListTag();
            position.add(DoubleTag.valueOf(paste.x()));
            position.add(DoubleTag.valueOf(paste.y()));
            position.add(DoubleTag.valueOf(paste.z()));
            tag.put("Pos", position);
            tag.remove("UUID");
            Entity entity = EntityType.loadEntityRecursive(tag, level,
                    new EntitySpawnRequest(EntitySpawnReason.STRUCTURE, false), created -> {
                        float yaw = created.rotate(paste.rotation());
                        yaw += created.mirror(paste.mirror()) - created.getYRot();
                        created.snapTo(paste.x(), paste.y(), paste.z(), yaw, created.getXRot());
                        return created;
                    });
            return entity != null && level.tryAddFreshEntityWithPassengers(entity);
        } catch (RuntimeException exception) {
            String entityId = paste.tag().getStringOr("id", "?");
            if (REPORTED.add("entity:" + entityId + ":" + exception.getClass().getName())) {
                LitematicaPlusClient.LOGGER.warn("Could not paste entity {}", entityId, exception);
            }
            return false;
        }
    }

    private static void dispatch(Minecraft minecraft, Job owner, List<Operation> operations) {
        IntegratedServer server = minecraft.getSingleplayerServer();
        if (server == null) {
            return;
        }
        ResourceKey<Level> dimension = owner.dimension;
        AtomicInteger inFlight = owner.inFlight;
        AtomicLong failed = owner.failed;
        inFlight.incrementAndGet();
        server.execute(() -> {
            try {
                ServerLevel level = server.getLevel(dimension);
                if (level == null) {
                    failed.addAndGet(operations.size());
                    return;
                }
                for (Operation operation : operations) {
                    try {
                        BlockState current = level.getBlockState(operation.pos());
                        if (operation.only() != null && !current.is(operation.only())) {
                            failed.incrementAndGet();
                            continue;
                        }
                        if (current != operation.state()) {
                            level.setBlock(operation.pos(), operation.state(), PLACE_FLAGS);
                        }
                        if (operation.data() != null) {
                            BlockEntity blockEntity = level.getBlockEntity(operation.pos());
                            if (blockEntity != null) {
                                blockEntity.loadWithComponents(TagValueInput.create(ProblemReporter.DISCARDING,
                                        level.registryAccess(), operation.data()));
                                blockEntity.setChanged();
                            }
                        }
                    } catch (RuntimeException exception) {
                        failed.incrementAndGet();
                        if (REPORTED.add("block:" + operation.state().getBlock() + ":" + exception.getClass().getName())) {
                            LitematicaPlusClient.LOGGER.warn("Could not paste {} at {}", operation.state(), operation.pos(), exception);
                        }
                    }
                }
            } finally {
                inFlight.decrementAndGet();
            }
        });
    }
}
