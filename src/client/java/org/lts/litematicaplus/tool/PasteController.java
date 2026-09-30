package org.lts.litematicaplus.tool;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.TagValueInput;
import org.lts.litematicaplus.LitematicaPlusClient;
import org.lts.litematicaplus.LitematicaPlusConfig;
import org.lts.litematicaplus.engine.PlacementRuntime;
import org.lts.litematicaplus.schematic.BlockStateNbt;
import org.lts.litematicaplus.selection.AreaBox;
import org.lts.litematicaplus.util.Notify;
import org.lts.litematicaplus.world.GhostSection;
import org.lts.litematicaplus.world.PlacementVolume;

public final class PasteController {
    private static final int DIRECT_BLOCKS_PER_TICK = 4096;
    private static final int MAX_COMMAND_LENGTH = 255;
    private static final int MAX_FILL_VOLUME = 32768;
    private static final int PLACE_FLAGS = Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE
            | Block.UPDATE_SUPPRESS_DROPS | Block.UPDATE_SKIP_BLOCK_ENTITY_SIDEEFFECTS | Block.UPDATE_SKIP_ON_PLACE;

    private record Operation(BlockPos pos, BlockState state, CompoundTag data) {
    }

    private static final class Job {
        PlacementVolume volume;
        long[] keys = new long[0];
        int keyIndex;
        int cell;
        ArrayDeque<int[]> deleteBoxes = new ArrayDeque<>();
        int[] deleteCursor;
        boolean direct;
        ResourceKey<Level> dimension;
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
        if (!allowed(minecraft) || runtime == null || runtime.volume() == null) {
            if (runtime == null || runtime.volume() == null) {
                Notify.actionBar("litematicaplus.message.no_placement");
            }
            return;
        }
        PlacementVolume volume = runtime.volume();
        Job created = baseJob(minecraft);
        created.volume = volume;
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
    }

    public static void startDelete(Minecraft minecraft, List<AreaBox> boxes) {
        if (!allowed(minecraft)) {
            return;
        }
        if (boxes.isEmpty()) {
            Notify.actionBar("litematicaplus.message.no_selection");
            return;
        }
        Job created = baseJob(minecraft);
        long total = 0L;
        for (AreaBox box : boxes) {
            for (int[] part : split(box)) {
                created.deleteBoxes.add(part);
                total += (long) (part[3] - part[0] + 1) * (part[4] - part[1] + 1) * (part[5] - part[2] + 1);
            }
        }
        created.total = total;
        job = created;
        Notify.actionBar("litematicaplus.message.delete_started");
    }

    private static boolean allowed(Minecraft minecraft) {
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
        return true;
    }

    private static Job baseJob(Minecraft minecraft) {
        LitematicaPlusConfig config = LitematicaPlusConfig.current();
        Job created = new Job();
        created.direct = minecraft.hasSingleplayerServer();
        created.dimension = minecraft.level.dimension();
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
        if (minecraft.player == null || minecraft.level == null || minecraft.level.dimension() != current.dimension) {
            job = null;
            return;
        }
        try {
            boolean finished = current.volume != null ? tickPaste(minecraft, current) : tickDelete(minecraft, current);
            if (finished) {
                job = null;
                Notify.actionBar("litematicaplus.message.paste_finished", current.changed, current.skipped);
            }
        } catch (RuntimeException exception) {
            job = null;
            LitematicaPlusClient.LOGGER.error("Paste job failed", exception);
            Notify.actionBar("litematicaplus.message.paste_failed");
        }
    }

    private static boolean tickPaste(Minecraft minecraft, Job current) {
        ClientLevel level = minecraft.level;
        PlacementVolume volume = current.volume;
        BlockState[] palette = volume.palette();
        int budget = current.direct ? DIRECT_BLOCKS_PER_TICK : LitematicaPlusConfig.current().pasteCommandsPerTick;
        List<Operation> operations = current.direct ? new ArrayList<>() : null;
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        int scanned = 0;
        while (current.keyIndex < current.keys.length && budget > 0 && scanned < 65536) {
            long key = current.keys[current.keyIndex];
            GhostSection section = volume.section(key);
            int originX = SectionPos.x(key) << 4;
            int originY = SectionPos.y(key) << 4;
            int originZ = SectionPos.z(key) << 4;
            if (section == null || !level.hasChunk(originX >> 4, originZ >> 4)) {
                if (section != null) {
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
                if (value == GhostSection.OUTSIDE) {
                    current.cell++;
                    current.visited++;
                    scanned++;
                    continue;
                }
                BlockState state = palette[value & 0xFFFF];
                int x = originX + (index & 15);
                int y = originY + (index >> 8);
                int z = originZ + (index >> 4 & 15);
                cursor.set(x, y, z);
                if (!wanted(level.getBlockState(cursor), state, current.replace)) {
                    current.cell++;
                    current.visited++;
                    scanned++;
                    continue;
                }
                if (current.direct) {
                    CompoundTag data = current.withData ? volume.blockEntities().get(cursor.asLong()) : null;
                    operations.add(new Operation(cursor.immutable(), state, data));
                    current.cell++;
                    current.visited++;
                    current.changed++;
                    budget--;
                    continue;
                }
                int run = 1;
                while ((index + run & 15) != 0 && run < 16 - (index & 15)) {
                    short next = raw[index + run];
                    if (next != value) {
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
            dispatch(minecraft, current.dimension, operations);
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

    private static boolean tickDelete(Minecraft minecraft, Job current) {
        ClientLevel level = minecraft.level;
        int budget = current.direct ? 8 : Math.max(1, LitematicaPlusConfig.current().pasteCommandsPerTick / 8);
        while (budget-- > 0 && !current.deleteBoxes.isEmpty()) {
            int[] box = current.deleteBoxes.poll();
            long volume = (long) (box[3] - box[0] + 1) * (box[4] - box[1] + 1) * (box[5] - box[2] + 1);
            current.visited += volume;
            if (!level.hasChunk(box[0] >> 4, box[2] >> 4) || volume > MAX_FILL_VOLUME) {
                current.skipped += volume;
                continue;
            }
            if (current.direct) {
                List<Operation> operations = new ArrayList<>();
                BlockState air = Blocks.AIR.defaultBlockState();
                BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
                for (int y = box[4]; y >= box[1]; y--) {
                    for (int z = box[2]; z <= box[5]; z++) {
                        for (int x = box[0]; x <= box[3]; x++) {
                            cursor.set(x, y, z);
                            if (!level.getBlockState(cursor).isAir()) {
                                operations.add(new Operation(cursor.immutable(), air, null));
                            }
                        }
                    }
                }
                current.changed += operations.size();
                if (!operations.isEmpty()) {
                    dispatch(minecraft, current.dimension, operations);
                }
            } else {
                minecraft.player.connection.sendCommand("fill " + box[0] + " " + box[1] + " " + box[2] + " "
                        + box[3] + " " + box[4] + " " + box[5] + " minecraft:air strict");
                current.changed += volume;
            }
        }
        return current.deleteBoxes.isEmpty();
    }

    private static void dispatch(Minecraft minecraft, ResourceKey<Level> dimension, List<Operation> operations) {
        IntegratedServer server = minecraft.getSingleplayerServer();
        if (server == null) {
            return;
        }
        server.execute(() -> {
            ServerLevel level = server.getLevel(dimension);
            if (level == null) {
                return;
            }
            for (Operation operation : operations) {
                try {
                    level.setBlock(operation.pos(), operation.state(), PLACE_FLAGS);
                    if (operation.data() != null) {
                        BlockEntity blockEntity = level.getBlockEntity(operation.pos());
                        if (blockEntity != null) {
                            blockEntity.loadWithComponents(TagValueInput.create(ProblemReporter.DISCARDING,
                                    level.registryAccess(), operation.data()));
                            blockEntity.setChanged();
                        }
                    }
                } catch (RuntimeException exception) {
                    LitematicaPlusClient.LOGGER.warn("Could not paste block at {}", operation.pos(), exception);
                }
            }
        });
    }
}
