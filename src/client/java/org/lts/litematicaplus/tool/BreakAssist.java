package org.lts.litematicaplus.tool;

import java.util.HashSet;
import java.util.Set;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.SlabType;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.lts.litematicaplus.LitematicaPlusClient;
import org.lts.litematicaplus.LitematicaPlusConfig;
import org.lts.litematicaplus.engine.GhostEngine;
import org.lts.litematicaplus.engine.PlacementRuntime;
import org.lts.litematicaplus.util.Notify;
import org.lts.litematicaplus.verify.MismatchType;
import org.lts.litematicaplus.verify.StateComparator;

public final class BreakAssist {
    private static final Set<Block> TERRAIN = Set.of(Blocks.DIRT, Blocks.GRASS_BLOCK, Blocks.COARSE_DIRT, Blocks.DIRT_PATH);
    private static final int TERRAIN_MAX_TICKS = 40;
    private static final int GENERAL_MAX_TICKS = 600;
    private static final int GENERAL_GRACE_TICKS = 20;
    private static final Set<BlockPos> CLEARING = new HashSet<>();
    private static boolean bypassGuard;
    private static boolean manual;

    private BreakAssist() {
    }

    public static void clear() {
        CLEARING.clear();
        bypassGuard = false;
    }

    public static boolean clearing(BlockPos pos) {
        return CLEARING.contains(pos);
    }

    public static boolean eligible(ClientLevel level, BlockPos pos, BlockState world, BlockState target,
            LitematicaPlusConfig config) {
        if (target.isAir() || world.isAir() || world.getBlock() instanceof LiquidBlock) {
            return false;
        }
        if (UtilityBlocks.keep(world.getBlock(), level.getBlockEntity(pos))) {
            return false;
        }
        if (config.easyPlaceSubstitutes && Substitutes.accepts(target, world)) {
            return false;
        }
        if (config.soilInterchangeable && StateComparator.isSoil(PlacementSolver.baseState(target).getBlock())
                && StateComparator.isSoil(world.getBlock())) {
            return false;
        }
        if (config.looseBlocks && StateComparator.get().sameGroup(PlacementSolver.baseState(target).getBlock(),
                world.getBlock())) {
            target = StateComparator.transferProperties(target, world.getBlock());
        }
        Block wanted = PlacementSolver.baseState(target).getBlock();
        if (config.terrainAutoReplace && world.getBlock() != wanted && TERRAIN.contains(world.getBlock())
                && TERRAIN.contains(wanted)) {
            return true;
        }
        if (!config.breakWrongBlocks || !structurallyWrong(target, world)) {
            return false;
        }
        return world.getDestroySpeed(level, pos) >= 0.0F && !UtilityBlocks.keep(world.getBlock(), level.getBlockEntity(pos));
    }

    static boolean structurallyWrong(BlockState target, BlockState world) {
        Block wanted = PlacementSolver.baseState(target).getBlock();
        if (wanted != world.getBlock()) {
            return true;
        }
        if (wanted instanceof SlabBlock) {
            SlabType type = target.getValue(BlockStateProperties.SLAB_TYPE);
            return type != SlabType.DOUBLE && world.getValue(BlockStateProperties.SLAB_TYPE) != type;
        }
        return !PlacementSolver.orientationMatches(world, PlacementSolver.baseState(target));
    }

    public static boolean active() {
        return !CLEARING.isEmpty();
    }

    public static boolean busy(Minecraft minecraft) {
        if (active()) {
            return true;
        }
        return minecraft.gameMode != null && minecraft.gameMode.isDestroying() && minecraft.options.keyAttack.isDown();
    }

    public static void yieldTo(Minecraft minecraft) {
        if (CLEARING.isEmpty()) {
            return;
        }
        boolean same = minecraft.hitResult instanceof BlockHitResult hit && hit.getType() == HitResult.Type.BLOCK
                && CLEARING.contains(hit.getBlockPos());
        CLEARING.clear();
        bypassGuard = false;
        if (!same && minecraft.gameMode != null) {
            minecraft.gameMode.stopDestroyBlock();
        }
    }

    public static void start(Minecraft minecraft, BlockPos pos, boolean byEasyPlace) {
        BlockPos key = pos.immutable();
        if (!CLEARING.isEmpty() || !CLEARING.add(key)) {
            return;
        }
        manual = byEasyPlace;
        try {
            begin(minecraft, key);
        } catch (RuntimeException exception) {
            abort(minecraft, key, exception);
        }
    }

    private static void begin(Minecraft minecraft, BlockPos key) {
        LocalPlayer player = minecraft.player;
        ClientLevel level = minecraft.level;
        BlockState world = level.getBlockState(key);
        boolean terrain = TERRAIN.contains(world.getBlock());
        Direction face = Direction.getApproximateNearest(player.getEyePosition().subtract(Vec3.atCenterOf(key)));
        if (!player.hasInfiniteMaterials()) {
            ToolSwitcher.switchFor(minecraft, key, true);
        }
        bypassGuard = true;
        try {
            minecraft.gameMode.startDestroyBlock(key, face);
        } finally {
            bypassGuard = false;
        }
        int maxTicks = TERRAIN_MAX_TICKS;
        if (!terrain) {
            float progress = level.getBlockState(key).getDestroyProgress(player, level, key);
            maxTicks = progress <= 0.0F ? GENERAL_MAX_TICKS
                    : Math.min(GENERAL_MAX_TICKS, (int) Math.ceil(1.0F / progress) + GENERAL_GRACE_TICKS);
        }
        Notify.actionBar("litematicaplus.message.clearing", world.getBlock().getName());
        int budget = maxTicks;
        if (!TickQueue.after(1, () -> step(minecraft, key, face, budget))) {
            minecraft.gameMode.stopDestroyBlock();
            CLEARING.remove(key);
        }
    }

    private static void abort(Minecraft minecraft, BlockPos pos, RuntimeException exception) {
        CLEARING.remove(pos);
        bypassGuard = false;
        LitematicaPlusClient.LOGGER.error("Could not clear the wrong block at {}", pos, exception);
        if (minecraft.gameMode != null) {
            try {
                minecraft.gameMode.stopDestroyBlock();
            } catch (RuntimeException stopFailure) {
                LitematicaPlusClient.LOGGER.debug("Could not stop breaking after a failure", stopFailure);
            }
        }
    }

    public static boolean bypassGuard() {
        return bypassGuard;
    }

    private static void step(Minecraft minecraft, BlockPos pos, Direction face, int ticksLeft) {
        if (!CLEARING.contains(pos)) {
            return;
        }
        try {
            advance(minecraft, pos, face, ticksLeft);
        } catch (RuntimeException exception) {
            abort(minecraft, pos, exception);
        }
    }

    private static void advance(Minecraft minecraft, BlockPos pos, Direction face, int ticksLeft) {
        ClientLevel level = minecraft.level;
        LocalPlayer player = minecraft.player;
        if (level == null || player == null || minecraft.gameMode == null) {
            CLEARING.remove(pos);
            return;
        }
        LitematicaPlusConfig config = LitematicaPlusConfig.current();
        if (!TickQueue.worldReady(minecraft) || manual && !config.easyPlaceEnabled || !config.renderEnabled) {
            minecraft.gameMode.stopDestroyBlock();
            CLEARING.remove(pos);
            return;
        }
        BlockState current = level.getBlockState(pos);
        if (current.isAir() || current.canBeReplaced()) {
            CLEARING.remove(pos);
            EasyPlace.retry(minecraft, pos);
            return;
        }
        if (ticksLeft <= 0 || !player.isWithinBlockInteractionRange(pos, 1.0)) {
            minecraft.gameMode.stopDestroyBlock();
            CLEARING.remove(pos);
            Notify.actionBar("litematicaplus.message.clear_timeout");
            return;
        }
        bypassGuard = true;
        try {
            minecraft.gameMode.continueDestroyBlock(pos, face);
        } finally {
            bypassGuard = false;
        }
        player.swing(InteractionHand.MAIN_HAND, player.getMainHandItem().getInteractAnimation(), false);
        if (!TickQueue.after(1, () -> step(minecraft, pos, face, ticksLeft - 1))) {
            minecraft.gameMode.stopDestroyBlock();
            CLEARING.remove(pos);
        }
    }

    public static boolean guardBlocks(Minecraft minecraft, BlockPos pos) {
        LitematicaPlusConfig config = LitematicaPlusConfig.current();
        if (bypassGuard || !config.excavationGuard || !config.easyPlaceEnabled || !config.renderEnabled
                || minecraft.level == null) {
            return false;
        }
        GhostEngine engine = GhostEngine.get();
        boolean anyEnabled = false;
        boolean inside = false;
        for (PlacementRuntime runtime : engine.runtimes()) {
            if (runtime.volume() == null || !runtime.placement().enabled()) {
                continue;
            }
            anyEnabled = true;
            if (runtime.volume().paletteIndexAt(pos.getX(), pos.getY(), pos.getZ()) >= 0) {
                inside = true;
            }
        }
        if (!inside) {
            if (config.excavationGuardOutside && anyEnabled
                    && !EasyPlace.isInteractable(minecraft.level, pos, minecraft.level.getBlockState(pos))) {
                hint(config, "litematicaplus.message.guard_outside");
                return true;
            }
            return false;
        }
        if (config.excavationGuardCorrect) {
            BlockState expected = engine.schematicStateAt(pos.getX(), pos.getY(), pos.getZ());
            if (expected != null && !expected.isAir()
                    && engine.categoryAt(pos.getX(), pos.getY(), pos.getZ()) == MismatchType.CORRECT) {
                hint(config, "litematicaplus.message.guard_correct");
                return true;
            }
        }
        return false;
    }

    private static void hint(LitematicaPlusConfig config, String key) {
        if (config.excavationGuardHint) {
            Notify.actionBar(key);
        }
    }

    static boolean aboutToBreak(ItemStack stack) {
        return stack.isDamageableItem() && stack.getMaxDamage() - stack.getDamageValue() <= 1;
    }

    static int bestToolSlot(Inventory inventory, BlockState state) {
        ItemStack current = inventory.getSelectedItem();
        float bestSpeed = speed(current, state);
        int best = -1;
        for (int slot = 0; slot < 36; slot++) {
            ItemStack stack = inventory.getItem(slot);
            if (stack.isEmpty() || stack == current || aboutToBreak(stack)) {
                continue;
            }
            float speed = speed(stack, state);
            if (speed > bestSpeed) {
                bestSpeed = speed;
                best = slot;
            }
        }
        return best;
    }

    private static float speed(ItemStack stack, BlockState state) {
        if (state.requiresCorrectToolForDrops() && !stack.isCorrectToolForDrops(state)) {
            return -1.0F;
        }
        return stack.getDestroySpeed(state);
    }
}
