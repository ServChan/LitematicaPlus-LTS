package org.lts.litematicaplus.tool;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.FireworkRocketItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.PlaceOnWaterBlockItem;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.ButtonBlock;
import net.minecraft.world.level.block.CakeBlock;
import net.minecraft.world.level.block.CandleCakeBlock;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.DragonEggBlock;
import net.minecraft.world.level.block.FenceGateBlock;
import net.minecraft.world.level.block.LeverBlock;
import net.minecraft.world.level.block.NoteBlock;
import net.minecraft.world.level.block.ObserverBlock;
import net.minecraft.world.level.block.RepeaterBlock;
import net.minecraft.world.level.block.RespawnAnchorBlock;
import net.minecraft.world.level.block.TrapDoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import org.lts.litematicaplus.LitematicaPlusConfig;
import org.lts.litematicaplus.engine.GhostEngine;
import org.lts.litematicaplus.engine.PlacementRuntime;
import org.lts.litematicaplus.util.Notify;
import org.lts.litematicaplus.verify.MismatchType;
import org.lts.litematicaplus.verify.StateComparator;
import org.lts.litematicaplus.world.PlacementVolume;

public final class EasyPlace {
    public enum Outcome {
        ACTED,
        SKIPPED,
        FAILED
    }

    private enum Step {
        NONE,
        FOLLOW_UP,
        PLACE,
        BREAK
    }

    private record Unsolved(BlockState target, Item item, long signature, long until) {
    }

    private static final int RESTRICT_MARGIN = 2;
    private static final int POSITION_COOLDOWN_TICKS = 6;
    private static final int HEAD_YAW_LEAD_TICKS = 2;
    private static final int RETRY_ATTEMPTS = 8;
    private static final int RETRY_WAIT_TICKS = 10;
    private static final int UNSOLVED_TICKS = 20;
    private static final int REQUEST_TICKS = 20;
    private static final Map<BlockPos, Long> RECENT = new HashMap<>();
    private static final Map<BlockPos, Unsolved> UNSOLVED = new HashMap<>();
    private static long globalReadyTick;
    private static boolean sessionConsumed;
    private static boolean sessionPlaced;
    private static boolean requested;
    private static long requestedTick;
    private static boolean idle = true;
    private static long lastFakeTick = Long.MIN_VALUE / 2;
    private static ClientLevel lastLevel;

    private EasyPlace() {
    }

    public static void tick(Minecraft minecraft) {
        if (minecraft.level != lastLevel) {
            if (lastLevel != null) {
                reset();
            }
            lastLevel = minecraft.level;
        }
        if (minecraft.level == null || minecraft.player == null) {
            if (!idle) {
                reset();
                idle = true;
            }
            return;
        }
        idle = false;
        LitematicaPlusConfig config = LitematicaPlusConfig.current();
        boolean down = minecraft.options.keyUse.isDown();
        boolean wanted = requested || down && sessionConsumed && config.easyPlaceHold;
        boolean waiting = cooling() || BreakAssist.busy(minecraft);
        boolean keep = requested && waiting && TickQueue.now() - requestedTick < REQUEST_TICKS && active(config)
                && minecraft.gui.screen() == null && !ToolController.get().holdingTool(minecraft);
        requested = false;
        if (wanted && active(config) && !minecraft.player.isFallFlying() && minecraft.gui.screen() == null
                && !waiting && !ToolController.get().holdingTool(minecraft) && (config.easyPlaceHold || !sessionPlaced)) {
            act(minecraft, config);
        }
        requested = keep;
        if (!down) {
            sessionConsumed = false;
            sessionPlaced = false;
        }
    }

    public static void dropRequest() {
        requested = false;
    }

    private static boolean active(LitematicaPlusConfig config) {
        return config.easyPlaceEnabled && config.renderEnabled;
    }

    private static void act(Minecraft minecraft, LitematicaPlusConfig config) {
        if (config.entityPlacement && EntityAssist.tryHandle(minecraft)) {
            sessionPlaced = true;
            return;
        }
        GhostRaycast.Hit hit = GhostRaycast.trace(minecraft, candidate -> isActionable(minecraft, candidate));
        if (hit != null) {
            sessionPlaced = true;
            attempt(minecraft, hit, config, true);
        }
    }

    public static void reset() {
        RECENT.clear();
        UNSOLVED.clear();
        globalReadyTick = 0L;
        sessionConsumed = false;
        sessionPlaced = false;
        requested = false;
        TickQueue.clear();
        LookLock.clear();
        NoteTuner.clear();
        FollowUps.clear();
        BreakAssist.clear();
        BlockEntityAssist.clear();
        EntityAssist.clear();
    }

    public static void markPlacement(Minecraft minecraft, BlockPos pos) {
        LitematicaPlusConfig config = LitematicaPlusConfig.current();
        int jitter = config.easyPlaceJitter ? ThreadLocalRandom.current().nextInt(0, 2) : 0;
        globalReadyTick = Math.max(globalReadyTick, TickQueue.now() + config.placementDelayTicks() + jitter);
        if (RECENT.size() > 256) {
            long now = TickQueue.now();
            RECENT.values().removeIf(until -> until < now);
        }
        RECENT.put(pos.immutable(), TickQueue.now() + POSITION_COOLDOWN_TICKS + FollowUps.latencyTicks(minecraft));
    }

    public static void hold(int ticks) {
        globalReadyTick = Math.max(globalReadyTick, TickQueue.now() + ticks);
    }

    public static boolean cooling() {
        return TickQueue.now() < globalReadyTick;
    }

    private static boolean recentlyClicked(BlockPos pos) {
        Long until = RECENT.get(pos);
        return until != null && until >= TickQueue.now();
    }

    public static boolean onUse(Minecraft minecraft) {
        LitematicaPlusConfig config = LitematicaPlusConfig.current();
        LocalPlayer player = minecraft.player;
        if (player == null || minecraft.level == null || minecraft.gameMode == null) {
            return false;
        }
        if (player.isSpectator() || !player.mayBuild()) {
            return false;
        }
        if (player.isFallFlying()) {
            sessionConsumed = false;
            return config.placementRestriction && restricted(minecraft, 0);
        }
        if (!active(config)) {
            return config.placementRestriction && restricted(minecraft, 0);
        }
        if (config.easyPlaceAllowInteraction && !sessionConsumed && interacting(minecraft) && !itemStateAimed(minecraft)) {
            return false;
        }
        boolean target = config.entityPlacement && EntityAssist.hasTarget(minecraft)
                || GhostRaycast.trace(minecraft, candidate -> isActionable(minecraft, candidate)) != null;
        if (target) {
            sessionConsumed = true;
            requested = true;
            requestedTick = TickQueue.now();
            return true;
        }
        if (sessionConsumed) {
            return true;
        }
        if (interacting(minecraft)) {
            return !config.easyPlaceAllowInteraction && insideAnyPlacement(((BlockHitResult) minecraft.hitResult).getBlockPos());
        }
        return (config.easyPlaceRestrictArea || config.placementRestriction) && restricted(minecraft, RESTRICT_MARGIN);
    }

    public static boolean onPick(Minecraft minecraft) {
        LitematicaPlusConfig config = LitematicaPlusConfig.current();
        if (!config.pickBlockEnabled || !config.renderEnabled || minecraft.player == null
                || minecraft.player.isSpectator()) {
            return false;
        }
        GhostRaycast.Hit hit = GhostRaycast.trace(minecraft, candidate -> candidate.type() == MismatchType.MISSING
                && InventoryHelper.itemFor(candidate.state()) != Items.AIR);
        if (hit == null) {
            return false;
        }
        Item item = InventoryHelper.itemFor(hit.state());
        if (!InventoryHelper.ensureInHand(minecraft, item)) {
            Notify.actionBar("litematicaplus.message.item_missing", new ItemStack(item).getHoverName());
        }
        return true;
    }

    public static boolean isPlaceable(Minecraft minecraft, GhostRaycast.Hit hit) {
        return isActionable(minecraft, hit);
    }

    private static boolean isActionable(Minecraft minecraft, GhostRaycast.Hit hit) {
        ClientLevel level = minecraft.level;
        if (level == null || recentlyClicked(hit.pos()) || BreakAssist.clearing(hit.pos())) {
            return false;
        }
        return classify(minecraft, level, hit.pos(), hit.state(), level.getBlockState(hit.pos()),
                LitematicaPlusConfig.current()) != Step.NONE;
    }

    private static Step classify(Minecraft minecraft, ClientLevel level, BlockPos pos, BlockState target, BlockState world,
            LitematicaPlusConfig config) {
        if (config.easyPlaceFinishStates && FollowUps.next(level, pos, target, world).kind() != FollowUps.Kind.NONE) {
            return FollowUps.busy(pos) || NoteTuner.isTuning(pos) ? Step.NONE : Step.FOLLOW_UP;
        }
        if (!world.canBeReplaced()) {
            return BreakAssist.eligible(level, pos, world, target, config) ? Step.BREAK : Step.NONE;
        }
        BlockState base = PlacementSolver.baseState(target);
        if (world.getBlock() == base.getBlock()) {
            return Step.NONE;
        }
        Item item = InventoryHelper.itemFor(target);
        if (!(item instanceof BlockItem) || item instanceof PlaceOnWaterBlockItem) {
            return Step.NONE;
        }
        if (target.hasProperty(BlockStateProperties.DOUBLE_BLOCK_HALF)
                && target.getValue(BlockStateProperties.DOUBLE_BLOCK_HALF) == DoubleBlockHalf.UPPER) {
            return Step.NONE;
        }
        if (target.hasProperty(BlockStateProperties.BED_PART)
                && target.getValue(BlockStateProperties.BED_PART) == BedPart.HEAD) {
            return Step.NONE;
        }
        if (!supported(level, pos, target)) {
            return Step.NONE;
        }
        return level.isUnobstructed(base, pos, CollisionContext.placementContext(minecraft.player))
                ? Step.PLACE : Step.NONE;
    }

    static boolean supported(ClientLevel level, BlockPos pos, BlockState target) {
        try {
            return target.canSurvive(level, pos);
        } catch (RuntimeException exception) {
            return true;
        }
    }

    public static boolean isInteractable(ClientLevel level, BlockPos pos, BlockState state) {
        Block block = state.getBlock();
        return state.hasBlockEntity() || state.getMenuProvider(level, pos) != null
                || block instanceof DoorBlock || block instanceof TrapDoorBlock || block instanceof FenceGateBlock
                || block instanceof LeverBlock || block instanceof ButtonBlock || block instanceof NoteBlock
                || block instanceof RespawnAnchorBlock || block instanceof CakeBlock || block instanceof CandleCakeBlock
                || block instanceof RepeaterBlock || block instanceof DragonEggBlock;
    }

    private static boolean interacting(Minecraft minecraft) {
        if (!(minecraft.hitResult instanceof BlockHitResult blockHit) || blockHit.getType() != HitResult.Type.BLOCK
                || minecraft.player.isSecondaryUseActive()) {
            return false;
        }
        return isInteractable(minecraft.level, blockHit.getBlockPos(), minecraft.level.getBlockState(blockHit.getBlockPos()));
    }

    private static boolean itemStateAimed(Minecraft minecraft) {
        if (!LitematicaPlusConfig.current().easyPlaceFinishStates || !(minecraft.hitResult instanceof BlockHitResult hit)
                || hit.getType() != HitResult.Type.BLOCK) {
            return false;
        }
        BlockPos pos = hit.getBlockPos();
        BlockState target = GhostEngine.get().schematicStateAt(pos.getX(), pos.getY(), pos.getZ());
        if (target == null || target.isAir()) {
            return false;
        }
        FollowUps.Kind kind = FollowUps.next(minecraft.level, pos, target, minecraft.level.getBlockState(pos)).kind();
        return kind == FollowUps.Kind.IGNITE || kind == FollowUps.Kind.DOUSE || kind == FollowUps.Kind.BUCKET;
    }

    private static boolean insideAnyPlacement(BlockPos pos) {
        return GhostEngine.get().runtimeAt(pos.getX(), pos.getY(), pos.getZ()) != null;
    }

    static void diagnose(LitematicaPlusConfig config, String key, Object... args) {
        if (config.easyPlaceDiagnostics) {
            Notify.actionBar(key, args);
        }
    }

    public static boolean ready(Minecraft minecraft) {
        return TickQueue.worldReady(minecraft) && !cooling() && !BreakAssist.busy(minecraft)
                && !ToolController.get().holdingTool(minecraft);
    }

    public static Outcome actAt(Minecraft minecraft, BlockPos pos) {
        LitematicaPlusConfig config = LitematicaPlusConfig.current();
        if (!config.renderEnabled || !ready(minecraft) || !minecraft.player.mayBuild()
                || minecraft.player.isFallFlying()) {
            return Outcome.SKIPPED;
        }
        BlockState target = GhostEngine.get().schematicStateAt(pos.getX(), pos.getY(), pos.getZ());
        if (target == null || target.isAir()) {
            return Outcome.SKIPPED;
        }
        GhostRaycast.Hit hit = new GhostRaycast.Hit(pos.immutable(), target, MismatchType.MISSING);
        if (!isActionable(minecraft, hit)) {
            return Outcome.SKIPPED;
        }
        return attempt(minecraft, hit, config, false) ? Outcome.ACTED : Outcome.FAILED;
    }

    private static boolean attempt(Minecraft minecraft, GhostRaycast.Hit hit, LitematicaPlusConfig config,
            boolean holdOnFailure) {
        LocalPlayer player = minecraft.player;
        ClientLevel level = minecraft.level;
        BlockPos pos = hit.pos();
        BlockState target = hit.state();
        BlockState world = level.getBlockState(pos);
        Step step = classify(minecraft, level, pos, target, world, config);
        if (step == Step.FOLLOW_UP) {
            if (FollowUps.start(minecraft, pos)) {
                markPlacement(minecraft, pos);
                return true;
            }
            return false;
        }
        if (step == Step.BREAK) {
            markPlacement(minecraft, pos);
            BreakAssist.start(minecraft, pos, holdOnFailure);
            return true;
        }
        if (step != Step.PLACE) {
            return false;
        }
        if (config.easyPlaceObserverDetect && target.getBlock() instanceof ObserverBlock && !observedReady(level, pos, target)) {
            diagnose(config, "litematicaplus.message.observer_wait");
            if (holdOnFailure) {
                hold(3);
            }
            return false;
        }
        Item item = resolveItem(minecraft, target, config);
        if (item == Items.AIR) {
            Item wanted = InventoryHelper.itemFor(target);
            diagnose(config, InventoryHelper.hasWithData(player.getInventory(), wanted)
                    ? "litematicaplus.message.item_has_data" : "litematicaplus.message.item_missing",
                    new ItemStack(wanted).getHoverName());
            if (holdOnFailure) {
                hold(5);
            }
            return false;
        }
        PlacementSolver.Plan plan = solve(player, level, item, pos, target, config);
        if (plan == null) {
            diagnose(config, "litematicaplus.message.easy_place_orientation");
            if (holdOnFailure) {
                hold(3);
            }
            return false;
        }
        markPlacement(minecraft, pos);
        int lead = plan.rotate() ? config.easyPlaceRotationLeadTicks : 0;
        BlockState base = PlacementSolver.baseState(target);
        boolean headYaw = base.hasProperty(BlockStateProperties.FACING) || base.hasProperty(BlockStateProperties.ORIENTATION);
        if (headYaw && (plan.rotate() || TickQueue.now() - lastFakeTick < HEAD_YAW_LEAD_TICKS)) {
            lead = Math.max(lead, HEAD_YAW_LEAD_TICKS);
        }
        if (lead > 0) {
            InventoryHelper.ensureInHand(minecraft, item);
            LookLock.lock(plan.yaw(), plan.pitch(), lead + 2);
            LookLock.sendRotation(minecraft, plan.yaw(), plan.pitch());
            hold(lead);
            BlockPos key = pos.immutable();
            if (!TickQueue.after(lead, () -> execute(minecraft, key, target, item, plan))) {
                LookLock.clear();
                if (config.easyPlaceRotationRevert) {
                    LookLock.sendRealRotation(minecraft);
                }
            }
        } else {
            execute(minecraft, pos.immutable(), target, item, plan);
        }
        return true;
    }

    private static PlacementSolver.Plan solve(LocalPlayer player, ClientLevel level, Item item, BlockPos pos,
            BlockState target, LitematicaPlusConfig config) {
        long signature = solveSignature(player, level, pos, config);
        Unsolved known = UNSOLVED.get(pos);
        if (known != null && known.until() >= TickQueue.now() && known.target() == target && known.item() == item
                && known.signature() == signature) {
            return null;
        }
        PlacementSolver.Plan plan = PlacementSolver.solve(player, level, new ItemStack(item), pos, target, config);
        if (plan != null) {
            UNSOLVED.remove(pos);
            return plan;
        }
        if (UNSOLVED.size() > 256) {
            long now = TickQueue.now();
            UNSOLVED.values().removeIf(entry -> entry.until() < now);
        }
        UNSOLVED.put(pos.immutable(), new Unsolved(target, item, signature, TickQueue.now() + UNSOLVED_TICKS));
        return null;
    }

    private static long solveSignature(LocalPlayer player, ClientLevel level, BlockPos pos, LitematicaPlusConfig config) {
        Vec3 eye = player.getEyePosition();
        long signature = Mth.floor(eye.x * 4.0);
        signature = signature * 31L + Mth.floor(eye.y * 4.0);
        signature = signature * 31L + Mth.floor(eye.z * 4.0);
        signature = signature * 31L + Mth.floor(player.getYRot() / 11.25F);
        signature = signature * 31L + Mth.floor(player.getXRot() / 15.0F);
        signature = signature * 31L + (player.input.keyPresses.shift() ? 1 : 0);
        signature = signature * 31L + (config.easyPlaceFakeRotation ? 1 : 0) + (config.easyPlaceAirPlacement ? 2 : 0)
                + (config.easyPlaceStrictOrientation ? 4 : 0);
        signature = signature * 31L + System.identityHashCode(level.getBlockState(pos));
        for (Direction side : Direction.values()) {
            signature = signature * 31L + System.identityHashCode(level.getBlockState(pos.relative(side)));
        }
        return signature;
    }

    private static Item resolveItem(Minecraft minecraft, BlockState target, LitematicaPlusConfig config) {
        Item item = InventoryHelper.itemFor(target);
        LocalPlayer player = minecraft.player;
        if (player.hasInfiniteMaterials() || InventoryHelper.has(player.getInventory(), item, config)) {
            return item;
        }
        Item alternative = alternativeItem(player.getInventory(), target, config);
        if (alternative != Items.AIR) {
            return alternative;
        }
        if (config.easyPlaceSubstitutes) {
            return Substitutes.find(player.getInventory(), target,
                    candidate -> InventoryHelper.has(player.getInventory(), candidate, config));
        }
        return Items.AIR;
    }

    private static Item alternativeItem(Inventory inventory, BlockState target, LitematicaPlusConfig config) {
        Block wanted = PlacementSolver.baseState(target).getBlock();
        boolean soil = config.soilInterchangeable && StateComparator.isSoil(wanted);
        if (!soil && !config.looseBlocks) {
            return Items.AIR;
        }
        StateComparator comparator = StateComparator.get();
        for (int slot = 0; slot < 36; slot++) {
            if (!(inventory.getItem(slot).getItem() instanceof BlockItem blockItem) || blockItem.getBlock() == wanted) {
                continue;
            }
            Block candidate = blockItem.getBlock();
            boolean fits = soil && StateComparator.isSoil(candidate)
                    || config.looseBlocks && comparator.sameGroup(wanted, candidate);
            if (fits && InventoryHelper.has(inventory, blockItem, config)) {
                return blockItem;
            }
        }
        return Items.AIR;
    }

    private static boolean observedReady(ClientLevel level, BlockPos pos, BlockState target) {
        BlockPos watched = pos.relative(target.getValue(BlockStateProperties.FACING));
        BlockState expected = GhostEngine.get().schematicStateAt(watched.getX(), watched.getY(), watched.getZ());
        return expected == null || expected.isAir() || level.getBlockState(watched).getBlock() == expected.getBlock();
    }

    private static void execute(Minecraft minecraft, BlockPos pos, BlockState target, Item item,
            PlacementSolver.Plan plan) {
        LocalPlayer player = minecraft.player;
        LitematicaPlusConfig config = LitematicaPlusConfig.current();
        try {
            if (player == null || minecraft.level == null || minecraft.gameMode == null
                    || !TickQueue.worldReady(minecraft) || BreakAssist.busy(minecraft)
                    || !minecraft.level.getBlockState(pos).canBeReplaced()) {
                return;
            }
            if (!InventoryHelper.ensureInHand(minecraft, item)) {
                diagnose(config, "litematicaplus.message.item_missing", new ItemStack(item).getHoverName());
                return;
            }
            if (plan.rotate()) {
                LookLock.sendRotation(minecraft, plan.yaw(), plan.pitch());
            }
            if (plan.sneak()) {
                LookLock.sendSneak(minecraft, true);
            }
            InteractionResult result = PlacementSolver.withPlan(player, plan,
                    () -> minecraft.gameMode.useItemOn(player, InteractionHand.MAIN_HAND, plan.click()));
            if (result instanceof InteractionResult.Success) {
                player.swing(InteractionHand.MAIN_HAND, player.getMainHandItem().getInteractAnimation(), false);
            }
            if (plan.sneak()) {
                LookLock.sendSneak(minecraft, false);
            }
            BlockEntityAssist.afterPlace(minecraft, pos, target);
            if (config.easyPlaceFinishStates) {
                FollowUps.schedule(minecraft, pos);
            }
        } finally {
            LookLock.clear();
            if (plan.rotate()) {
                lastFakeTick = TickQueue.now();
            }
            if (plan.rotate() && config.easyPlaceRotationRevert && minecraft.player != null) {
                LookLock.sendRealRotation(minecraft);
            }
        }
    }

    public static void retry(Minecraft minecraft, BlockPos pos) {
        retry(minecraft, pos.immutable(), RETRY_ATTEMPTS);
    }

    private static void retry(Minecraft minecraft, BlockPos pos, int attemptsLeft) {
        ClientLevel level = minecraft.level;
        LitematicaPlusConfig config = LitematicaPlusConfig.current();
        if (level == null || minecraft.player == null || !active(config) || !TickQueue.worldReady(minecraft)
                || ToolController.get().holdingTool(minecraft)) {
            return;
        }
        if (cooling() || BreakAssist.busy(minecraft)) {
            if (attemptsLeft > 1) {
                int wait = (int) Math.max(1L, Math.min(RETRY_WAIT_TICKS, globalReadyTick - TickQueue.now()));
                TickQueue.after(wait, () -> retry(minecraft, pos, attemptsLeft - 1));
            }
            return;
        }
        BlockState target = GhostEngine.get().schematicStateAt(pos.getX(), pos.getY(), pos.getZ());
        if (target == null || target.isAir() || !minecraft.player.isWithinBlockInteractionRange(pos, 1.0)) {
            return;
        }
        RECENT.remove(pos);
        GhostRaycast.Hit hit = new GhostRaycast.Hit(pos, target, MismatchType.MISSING);
        if (isActionable(minecraft, hit)) {
            attempt(minecraft, hit, config, true);
        }
    }

    static boolean orientationMatches(BlockState placed, BlockState target) {
        return PlacementSolver.orientationMatches(placed, target);
    }

    private static boolean restricted(Minecraft minecraft, int margin) {
        HitResult hit = minecraft.hitResult;
        if (!(hit instanceof BlockHitResult blockHit) || hit.getType() != HitResult.Type.BLOCK) {
            return false;
        }
        ItemStack stack = minecraft.player.getMainHandItem();
        if (!(stack.getItem() instanceof BlockItem)) {
            if (stack.getItem() instanceof FireworkRocketItem) {
                return false;
            }
            stack = minecraft.player.getOffhandItem();
            if (!(stack.getItem() instanceof BlockItem)) {
                return false;
            }
        }
        BlockPos clicked = blockHit.getBlockPos();
        BlockState clickedState = minecraft.level.getBlockState(clicked);
        if (!minecraft.player.isShiftKeyDown() && isInteractable(minecraft.level, clicked, clickedState)) {
            return false;
        }
        BlockPos target = clickedState.canBeReplaced() ? clicked : clicked.relative(blockHit.getDirection());
        GhostEngine engine = GhostEngine.get();
        if (!nearPlacement(engine, target, margin)) {
            return false;
        }
        BlockState wanted = engine.schematicStateAt(target.getX(), target.getY(), target.getZ());
        if ((wanted == null || wanted.isAir()) && stack.getItem() instanceof BlockItem blockItem
                && UtilityBlocks.workstation(blockItem.getBlock())) {
            return false;
        }
        if (margin <= 0 && wanted != null && InventoryHelper.itemFor(wanted) == stack.getItem()) {
            return false;
        }
        Notify.actionBar(margin > 0 ? "litematicaplus.message.easy_place_restricted"
                : "litematicaplus.message.placement_restricted");
        return true;
    }

    private static boolean nearPlacement(GhostEngine engine, BlockPos pos, int margin) {
        if (margin <= 0) {
            return engine.runtimeAt(pos.getX(), pos.getY(), pos.getZ()) != null;
        }
        for (PlacementRuntime runtime : engine.runtimes()) {
            PlacementVolume volume = runtime.volume();
            if (volume == null || !runtime.placement().enabled()) {
                continue;
            }
            for (PlacementVolume.RegionBox box : volume.regionBoxes()) {
                if (pos.getX() >= box.minX() - margin && pos.getX() <= box.maxX() + margin
                        && pos.getY() >= box.minY() - margin && pos.getY() <= box.maxY() + margin
                        && pos.getZ() >= box.minZ() - margin && pos.getZ() <= box.maxZ() + margin) {
                    return true;
                }
            }
        }
        return false;
    }
}
