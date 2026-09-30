package org.lts.litematicaplus.tool;

import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.level.block.NoteBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

public final class NoteTuner {
    public static final int NOTE_COUNT = 25;
    private static final int MAX_CLICKS = 60;
    private static final int INTERVAL_TICKS = 2;
    private static final int ACK_GRACE_TICKS = 10;
    private static final int STALE_TICKS = 600;
    private static final ArrayDeque<BlockPos> QUEUE = new ArrayDeque<>();
    private static final Map<BlockPos, Job> JOBS = new HashMap<>();
    private static boolean pumping;

    private static final class Job {
        private int target;
        private int base = -1;
        private int sent;
        private int confirmed;
        private int clicksLeft = MAX_CLICKS;
        private long lastProgressTick = TickQueue.now();

        private Job(int target) {
            this.target = target;
        }

        private void rebase(int current) {
            this.base = current;
            this.sent = 0;
            this.confirmed = 0;
            this.lastProgressTick = TickQueue.now();
        }
    }

    public record Progress(int blocks, int clicks, BlockPos next) {
    }

    private NoteTuner() {
    }

    public static int clicks(int current, int target) {
        return Math.floorMod(target - current, NOTE_COUNT);
    }

    public static boolean isTuning(BlockPos pos) {
        return JOBS.containsKey(pos);
    }

    public static Progress progress(Minecraft minecraft) {
        if (JOBS.isEmpty() || minecraft.level == null) {
            return new Progress(0, 0, null);
        }
        int clicks = 0;
        for (Map.Entry<BlockPos, Job> entry : JOBS.entrySet()) {
            BlockState state = minecraft.level.getBlockState(entry.getKey());
            if (state.getBlock() instanceof NoteBlock) {
                clicks += clicks(state.getValue(BlockStateProperties.NOTE), entry.getValue().target);
            }
        }
        return new Progress(JOBS.size(), clicks, QUEUE.peekFirst());
    }

    public static void tune(Minecraft minecraft, BlockPos pos, int target) {
        if (target < 0 || target >= NOTE_COUNT) {
            return;
        }
        BlockPos key = pos.immutable();
        Job existing = JOBS.get(key);
        if (existing == null) {
            JOBS.put(key, new Job(target));
            QUEUE.addLast(key);
        } else if (existing.target != target) {
            existing.target = target;
            existing.base = -1;
        }
        ensurePump(minecraft);
    }

    public static void clear() {
        QUEUE.clear();
        JOBS.clear();
        pumping = false;
    }

    private static void ensurePump(Minecraft minecraft) {
        if (!QUEUE.isEmpty() && !pumping) {
            pumping = TickQueue.after(INTERVAL_TICKS, () -> pump(minecraft));
        }
    }

    private static void pump(Minecraft minecraft) {
        pumping = false;
        if (minecraft.player == null || minecraft.level == null || minecraft.gameMode == null) {
            clear();
            return;
        }
        if (!TickQueue.worldReady(minecraft)) {
            ensurePump(minecraft);
            return;
        }
        int pending = QUEUE.size();
        while (pending-- > 0) {
            BlockPos pos = QUEUE.pollFirst();
            Job job = pos == null ? null : JOBS.get(pos);
            if (job == null) {
                continue;
            }
            BlockState state = minecraft.level.getBlockState(pos);
            if (!(state.getBlock() instanceof NoteBlock)) {
                JOBS.remove(pos);
                continue;
            }
            int current = state.getValue(BlockStateProperties.NOTE);
            if (current == job.target) {
                JOBS.remove(pos);
                continue;
            }
            if (TickQueue.now() - job.lastProgressTick > STALE_TICKS) {
                JOBS.remove(pos);
                continue;
            }
            if (job.base < 0) {
                job.rebase(current);
            }
            int confirmed = clicks(job.base, current);
            if (confirmed > job.sent) {
                job.rebase(current);
            } else if (confirmed != job.confirmed) {
                job.confirmed = confirmed;
                job.lastProgressTick = TickQueue.now();
            }
            int needed = clicks(job.base, job.target);
            if (job.sent < needed) {
                if (job.clicksLeft <= 0) {
                    JOBS.remove(pos);
                    continue;
                }
                QUEUE.addLast(pos);
                if (click(minecraft, pos)) {
                    job.sent++;
                    job.clicksLeft--;
                    job.lastProgressTick = TickQueue.now();
                    break;
                }
                continue;
            }
            if (TickQueue.now() - job.lastProgressTick > FollowUps.latencyTicks(minecraft) + ACK_GRACE_TICKS) {
                job.rebase(current);
            }
            QUEUE.addLast(pos);
        }
        ensurePump(minecraft);
    }

    private static boolean click(Minecraft minecraft, BlockPos pos) {
        LocalPlayer player = minecraft.player;
        if (player.isSecondaryUseActive() || !player.isWithinBlockInteractionRange(pos, 1.0)) {
            return false;
        }
        Direction face = Direction.getApproximateNearest(player.getEyePosition().subtract(Vec3.atCenterOf(pos)));
        if (face == Direction.UP && player.getMainHandItem().is(ItemTags.NOTE_BLOCK_TOP_INSTRUMENTS)) {
            face = player.getDirection().getOpposite();
        }
        BlockHitResult hit = new BlockHitResult(new Vec3(pos.getX() + 0.5 + 0.5 * face.getStepX(),
                pos.getY() + 0.5 + 0.5 * face.getStepY(), pos.getZ() + 0.5 + 0.5 * face.getStepZ()), face, pos, false);
        minecraft.gameMode.useItemOn(player, InteractionHand.MAIN_HAND, hit);
        player.swing(InteractionHand.MAIN_HAND, player.getMainHandItem().getInteractAnimation(), false);
        return true;
    }
}
