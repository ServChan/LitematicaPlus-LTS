package org.lts.litematicaplus.tool;

import java.util.HashMap;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.AbstractCandleBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.level.block.CandleCakeBlock;
import net.minecraft.world.level.block.ComparatorBlock;
import net.minecraft.world.level.block.DaylightDetectorBlock;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.EndPortalFrameBlock;
import net.minecraft.world.level.block.FenceGateBlock;
import net.minecraft.world.level.block.FlowerPotBlock;
import net.minecraft.world.level.block.LeverBlock;
import net.minecraft.world.level.block.NoteBlock;
import net.minecraft.world.level.block.RedstoneWireBlock;
import net.minecraft.world.level.block.RepeaterBlock;
import net.minecraft.world.level.block.RespawnAnchorBlock;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.TrapDoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.block.state.properties.RedstoneSide;
import net.minecraft.world.level.block.state.properties.SlabType;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.lts.litematicaplus.LitematicaPlusConfig;
import org.lts.litematicaplus.engine.GhostEngine;
import org.lts.litematicaplus.materials.MaterialList;
import org.lts.litematicaplus.util.Notify;

public final class FollowUps {
    public enum Kind {
        NONE,
        INTERACT,
        USE_ITEM,
        TUNE,
        IGNITE,
        DOUSE,
        EMPTY_HAND,
        BUCKET
    }

    public record Action(Kind kind, Item item, Direction face, Direction look) {
        static final Action NONE = new Action(Kind.NONE, Items.AIR, Direction.UP, null);
    }

    private static final IntegerProperty[] STACKABLE = {
        BlockStateProperties.CANDLES, BlockStateProperties.PICKLES, BlockStateProperties.EGGS,
        BlockStateProperties.LAYERS, BlockStateProperties.FLOWER_AMOUNT, BlockStateProperties.SEGMENT_AMOUNT
    };
    private static final int STEP_TICKS = 2;
    private static final int MAX_STEPS = 32;
    private static final int ACK_TICKS = 10;
    private static final int MAX_ACTIVE = 128;
    private static final Map<BlockPos, Chain> ACTIVE = new HashMap<>();

    private static final class Chain {
        private BlockState awaiting;
        private long deadlineTick;
    }

    private FollowUps() {
    }

    public static int latencyTicks(Minecraft minecraft) {
        LocalPlayer player = minecraft.player;
        if (player == null || minecraft.hasSingleplayerServer()) {
            return 0;
        }
        PlayerInfo info = player.connection.getPlayerInfo(player.getUUID());
        return info == null ? 2 : Math.min(40, Math.max(0, info.getLatency()) / 50);
    }

    public static Action next(ClientLevel level, BlockPos pos, BlockState target, BlockState world) {
        Block block = target.getBlock();
        if (block instanceof FlowerPotBlock pot && world.is(Blocks.FLOWER_POT) && block != Blocks.FLOWER_POT) {
            Item flower = pot.getPotted().asItem();
            return flower == Items.AIR ? Action.NONE : new Action(Kind.USE_ITEM, flower, Direction.UP, null);
        }
        if (block instanceof CandleCakeBlock && world.is(Blocks.CAKE)
                && world.getValue(BlockStateProperties.BITES) == 0) {
            Item candle = MaterialList.candleOf(block);
            return candle == Items.AIR ? Action.NONE : new Action(Kind.USE_ITEM, candle, Direction.UP, null);
        }
        if (world.getBlock() != block) {
            return Action.NONE;
        }
        if (block instanceof NoteBlock) {
            return target.getValue(BlockStateProperties.NOTE).equals(world.getValue(BlockStateProperties.NOTE))
                    ? Action.NONE : new Action(Kind.TUNE, Items.AIR, Direction.UP, null);
        }
        if (block instanceof RepeaterBlock) {
            return differs(target, world, BlockStateProperties.DELAY) ? interact() : Action.NONE;
        }
        if (block instanceof ComparatorBlock) {
            return target.getValue(BlockStateProperties.MODE_COMPARATOR) != world.getValue(BlockStateProperties.MODE_COMPARATOR)
                    ? interact() : Action.NONE;
        }
        if (block instanceof LeverBlock) {
            return target.getValue(BlockStateProperties.POWERED) != world.getValue(BlockStateProperties.POWERED)
                    ? interact() : Action.NONE;
        }
        if (block instanceof DaylightDetectorBlock) {
            return target.getValue(BlockStateProperties.INVERTED) != world.getValue(BlockStateProperties.INVERTED)
                    ? interact() : Action.NONE;
        }
        if (block instanceof TrapDoorBlock && block != Blocks.IRON_TRAPDOOR) {
            return openDiffers(level, pos, target, world) ? interact() : stateActions(target, world);
        }
        if (block instanceof DoorBlock door && door.type().canOpenByHand()) {
            return openDiffers(level, pos, target, world) ? interact() : Action.NONE;
        }
        if (block instanceof FenceGateBlock) {
            if (!openDiffers(level, pos, target, world)) {
                return Action.NONE;
            }
            return new Action(Kind.INTERACT, Items.AIR, Direction.UP,
                    target.getValue(BlockStateProperties.HORIZONTAL_FACING));
        }
        if (block instanceof RedstoneWireBlock) {
            boolean togglable = (isDot(target) || isCross(target)) && (isDot(world) || isCross(world));
            return togglable && isDot(target) != isDot(world) ? interact() : Action.NONE;
        }
        if (block instanceof EndPortalFrameBlock) {
            return target.getValue(BlockStateProperties.EYE) && !world.getValue(BlockStateProperties.EYE)
                    ? new Action(Kind.USE_ITEM, Items.ENDER_EYE, Direction.UP, null) : Action.NONE;
        }
        if (block instanceof RespawnAnchorBlock) {
            return target.getValue(BlockStateProperties.RESPAWN_ANCHOR_CHARGES)
                    > world.getValue(BlockStateProperties.RESPAWN_ANCHOR_CHARGES)
                    ? new Action(Kind.USE_ITEM, Items.GLOWSTONE, Direction.UP, null) : Action.NONE;
        }
        if (block instanceof SlabBlock) {
            SlabType wanted = target.getValue(BlockStateProperties.SLAB_TYPE);
            SlabType present = world.getValue(BlockStateProperties.SLAB_TYPE);
            if (wanted == SlabType.DOUBLE && present != SlabType.DOUBLE) {
                return new Action(Kind.USE_ITEM, block.asItem(), present == SlabType.BOTTOM ? Direction.UP : Direction.DOWN,
                        null);
            }
            return stateActions(target, world);
        }
        for (IntegerProperty property : STACKABLE) {
            if (target.hasProperty(property)) {
                if (target.getValue(property) > world.getValue(property)) {
                    return new Action(Kind.USE_ITEM, block.asItem(), Direction.UP, null);
                }
                break;
            }
        }
        return stateActions(target, world);
    }

    static Action stateActions(BlockState target, BlockState world) {
        LitematicaPlusConfig config = LitematicaPlusConfig.current();
        boolean wet = world.hasProperty(BlockStateProperties.WATERLOGGED) && world.getValue(BlockStateProperties.WATERLOGGED);
        if (config.easyPlaceWaterlog && target.hasProperty(BlockStateProperties.WATERLOGGED)
                && world.hasProperty(BlockStateProperties.WATERLOGGED)) {
            boolean wanted = target.getValue(BlockStateProperties.WATERLOGGED);
            if (wanted != wet) {
                return new Action(Kind.BUCKET, wanted ? Items.WATER_BUCKET : Items.BUCKET, Direction.UP, null);
            }
        }
        Block block = target.getBlock();
        if (config.easyPlaceIgnite && target.hasProperty(BlockStateProperties.LIT) && world.hasProperty(BlockStateProperties.LIT)
                && (block instanceof AbstractCandleBlock || block instanceof CampfireBlock)) {
            boolean wanted = target.getValue(BlockStateProperties.LIT);
            boolean lit = world.getValue(BlockStateProperties.LIT);
            if (wanted && !lit && !wet) {
                return new Action(Kind.IGNITE, Items.FLINT_AND_STEEL, Direction.UP, null);
            }
            if (!wanted && lit && block instanceof CampfireBlock) {
                return new Action(Kind.DOUSE, Items.AIR, Direction.UP, null);
            }
            if (!wanted && lit && !(block instanceof CandleCakeBlock)) {
                return new Action(Kind.EMPTY_HAND, Items.AIR, Direction.UP, null);
            }
        }
        return Action.NONE;
    }

    private static Action interact() {
        return new Action(Kind.INTERACT, Items.AIR, Direction.UP, null);
    }

    private static boolean differs(BlockState target, BlockState world, IntegerProperty property) {
        return !target.getValue(property).equals(world.getValue(property));
    }

    private static boolean openDiffers(ClientLevel level, BlockPos pos, BlockState target, BlockState world) {
        if (target.getValue(BlockStateProperties.OPEN) == world.getValue(BlockStateProperties.OPEN)) {
            return false;
        }
        boolean powered = world.hasProperty(BlockStateProperties.POWERED) && world.getValue(BlockStateProperties.POWERED);
        return !powered && !level.hasNeighborSignal(pos);
    }

    private static boolean isCross(BlockState state) {
        return side(state, BlockStateProperties.NORTH_REDSTONE) && side(state, BlockStateProperties.SOUTH_REDSTONE)
                && side(state, BlockStateProperties.EAST_REDSTONE) && side(state, BlockStateProperties.WEST_REDSTONE);
    }

    private static boolean side(BlockState state, EnumProperty<RedstoneSide> property) {
        return state.getValue(property) != RedstoneSide.NONE;
    }

    private static boolean isDot(BlockState state) {
        return !side(state, BlockStateProperties.NORTH_REDSTONE) && !side(state, BlockStateProperties.SOUTH_REDSTONE)
                && !side(state, BlockStateProperties.EAST_REDSTONE) && !side(state, BlockStateProperties.WEST_REDSTONE);
    }

    public static void clear() {
        ACTIVE.clear();
    }

    public static boolean busy(BlockPos pos) {
        return ACTIVE.containsKey(pos);
    }

    public static void schedule(Minecraft minecraft, BlockPos pos) {
        BlockPos key = pos.immutable();
        if (ACTIVE.containsKey(key) || ACTIVE.size() >= MAX_ACTIVE) {
            return;
        }
        ACTIVE.put(key, new Chain());
        if (!TickQueue.after(STEP_TICKS, () -> step(minecraft, key, MAX_STEPS))) {
            ACTIVE.remove(key);
        }
    }

    public static boolean start(Minecraft minecraft, BlockPos pos) {
        BlockPos key = pos.immutable();
        if (ACTIVE.containsKey(key) || ACTIVE.size() >= MAX_ACTIVE) {
            return false;
        }
        ACTIVE.put(key, new Chain());
        step(minecraft, key, MAX_STEPS);
        return true;
    }

    private static void step(Minecraft minecraft, BlockPos pos, int budget) {
        Chain chain = ACTIVE.get(pos);
        ClientLevel level = minecraft.level;
        LocalPlayer player = minecraft.player;
        if (chain == null || level == null || player == null || budget <= 0
                || !LitematicaPlusConfig.current().easyPlaceFinishStates || !LitematicaPlusConfig.current().renderEnabled) {
            ACTIVE.remove(pos);
            return;
        }
        BlockState target = GhostEngine.get().schematicStateAt(pos.getX(), pos.getY(), pos.getZ());
        BlockState world = level.getBlockState(pos);
        Action action = target == null || target.isAir() ? Action.NONE : next(level, pos, target, world);
        if (action.kind() == Kind.NONE) {
            ACTIVE.remove(pos);
            return;
        }
        if (action.kind() == Kind.TUNE) {
            ACTIVE.remove(pos);
            NoteTuner.tune(minecraft, pos, target.getValue(BlockStateProperties.NOTE));
            return;
        }
        if (chain.awaiting == world && TickQueue.now() < chain.deadlineTick) {
            if (!TickQueue.after(1, () -> step(minecraft, pos, budget))) {
                ACTIVE.remove(pos);
            }
            return;
        }
        boolean sneakBlocked = player.isSecondaryUseActive() && (action.kind() == Kind.INTERACT
                || action.kind() == Kind.EMPTY_HAND
                || action.kind() == Kind.USE_ITEM && action.item() != net.minecraft.world.item.Items.ENDER_EYE);
        if (!TickQueue.worldReady(minecraft) || sneakBlocked || BreakAssist.busy(minecraft) || EasyPlace.cooling()) {
            int remaining = TickQueue.worldReady(minecraft) && !sneakBlocked ? budget : budget - 1;
            if (!TickQueue.after(STEP_TICKS, () -> step(minecraft, pos, remaining))) {
                ACTIVE.remove(pos);
            }
            return;
        }
        if (!player.isWithinBlockInteractionRange(pos, 1.0) || !perform(minecraft, pos, action)) {
            ACTIVE.remove(pos);
            return;
        }
        chain.awaiting = world;
        chain.deadlineTick = TickQueue.now() + ACK_TICKS + latencyTicks(minecraft) * 2L;
        EasyPlace.markPlacement(minecraft, pos);
        if (!TickQueue.after(STEP_TICKS, () -> step(minecraft, pos, budget - 1))) {
            ACTIVE.remove(pos);
        }
    }

    private static boolean perform(Minecraft minecraft, BlockPos pos, Action action) {
        LocalPlayer player = minecraft.player;
        if (action.kind() == Kind.INTERACT && player.isSecondaryUseActive()) {
            return false;
        }
        if (action.kind() == Kind.USE_ITEM && !InventoryHelper.ensureInHand(minecraft, action.item())) {
            Notify.actionBar("litematicaplus.message.item_missing", new ItemStack(action.item()).getHoverName());
            return false;
        }
        if (action.kind() == Kind.BUCKET) {
            return useBucket(minecraft, pos, action.item());
        }
        if (action.kind() == Kind.IGNITE && !InventoryHelper.ensureMatching(minecraft,
                stack -> stack.is(Items.FLINT_AND_STEEL) || stack.is(Items.FIRE_CHARGE), new ItemStack(Items.FLINT_AND_STEEL))) {
            Notify.actionBar("litematicaplus.message.item_missing", new ItemStack(Items.FLINT_AND_STEEL).getHoverName());
            return false;
        }
        if (action.kind() == Kind.DOUSE && !InventoryHelper.ensureMatching(minecraft,
                stack -> stack.is(ItemTags.SHOVELS), new ItemStack(Items.WOODEN_SHOVEL))) {
            Notify.actionBar("litematicaplus.message.shovel_missing");
            return false;
        }
        if (action.kind() == Kind.EMPTY_HAND && !InventoryHelper.selectEmptyHand(minecraft)) {
            Notify.actionBar("litematicaplus.message.no_empty_hand");
            return false;
        }
        Direction face = action.kind() == Kind.INTERACT
                ? Direction.getApproximateNearest(player.getEyePosition().subtract(Vec3.atCenterOf(pos))) : action.face();
        BlockHitResult hit = new BlockHitResult(new Vec3(pos.getX() + 0.5 + face.getStepX() * 0.5,
                pos.getY() + 0.5 + face.getStepY() * 0.5, pos.getZ() + 0.5 + face.getStepZ() * 0.5), face, pos, false);
        boolean rotate = action.look() != null && LitematicaPlusConfig.current().easyPlaceFakeRotation;
        float yaw = rotate ? action.look().toYRot() : player.getYRot();
        PlacementSolver.Plan plan = new PlacementSolver.Plan(hit, rotate, yaw, player.getXRot(), false, true);
        if (rotate) {
            LookLock.sendRotation(minecraft, yaw, player.getXRot());
        }
        InteractionResult result = PlacementSolver.withPlan(player, plan,
                () -> minecraft.gameMode.useItemOn(player, InteractionHand.MAIN_HAND, hit));
        if (result instanceof InteractionResult.Success) {
            player.swing(InteractionHand.MAIN_HAND, player.getMainHandItem().getInteractAnimation(), false);
        }
        if (rotate && LitematicaPlusConfig.current().easyPlaceRotationRevert) {
            LookLock.sendRealRotation(minecraft);
        }
        return true;
    }

    private static boolean useBucket(Minecraft minecraft, BlockPos pos, Item item) {
        LocalPlayer player = minecraft.player;
        Vec3 eye = player.getEyePosition();
        Vec3 center = Vec3.atCenterOf(pos);
        ClipContext.Fluid fluid = item == Items.BUCKET ? ClipContext.Fluid.SOURCE_ONLY : ClipContext.Fluid.NONE;
        BlockHitResult clip = minecraft.level.clip(new ClipContext(eye, center.add(center.subtract(eye).normalize()),
                ClipContext.Block.OUTLINE, fluid, player));
        if (clip.getType() != HitResult.Type.BLOCK || !clip.getBlockPos().equals(pos)) {
            return false;
        }
        if (!InventoryHelper.ensureInHand(minecraft, item)) {
            Notify.actionBar("litematicaplus.message.item_missing", new ItemStack(item).getHoverName());
            return false;
        }
        Vec3 delta = center.subtract(eye);
        float yaw = (float) (Math.toDegrees(Math.atan2(delta.z, delta.x)) - 90.0);
        float pitch = (float) -Math.toDegrees(Math.atan2(delta.y, Math.sqrt(delta.x * delta.x + delta.z * delta.z)));
        PlacementSolver.Plan plan = new PlacementSolver.Plan(clip, true, yaw, pitch, false, true);
        LookLock.sendRotation(minecraft, yaw, pitch);
        InteractionResult result = PlacementSolver.withPlan(player, plan,
                () -> minecraft.gameMode.useItem(player, InteractionHand.MAIN_HAND));
        if (result instanceof InteractionResult.Success) {
            player.swing(InteractionHand.MAIN_HAND, player.getMainHandItem().getInteractAnimation(), false);
        }
        if (LitematicaPlusConfig.current().easyPlaceRotationRevert) {
            LookLock.sendRealRotation(minecraft);
        }
        return true;
    }
}
