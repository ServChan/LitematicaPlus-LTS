package org.lts.litematicaplus.tool;

import java.util.Set;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.PlaceOnWaterBlockItem;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.ButtonBlock;
import net.minecraft.world.level.block.CakeBlock;
import net.minecraft.world.level.block.CandleCakeBlock;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.DragonEggBlock;
import net.minecraft.world.level.block.FenceGateBlock;
import net.minecraft.world.level.block.LeverBlock;
import net.minecraft.world.level.block.NoteBlock;
import net.minecraft.world.level.block.RepeaterBlock;
import net.minecraft.world.level.block.RespawnAnchorBlock;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.TrapDoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.level.block.state.properties.SlabType;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import org.lts.litematicaplus.LitematicaPlusConfig;
import org.lts.litematicaplus.engine.GhostEngine;
import org.lts.litematicaplus.mixin.BlockItemInvoker;
import org.lts.litematicaplus.util.Notify;
import org.lts.litematicaplus.verify.MismatchType;

public final class EasyPlace {
    private static final Set<String> ORIENTATION_PROPERTIES = Set.of("facing", "axis", "half", "type", "rotation",
            "face", "hinge", "attachment", "orientation", "vertical_direction", "hanging");
    private static final Direction[] DIRECTIONS = Direction.values();
    private static final double[] HEIGHTS = {0.25, 0.75};
    private static final double[] LATERAL = {0.5, 0.25, 0.75};
    private static final double[][] FLAT = {{0.5, 0.5}, {0.25, 0.25}, {0.75, 0.75}, {0.25, 0.75}, {0.75, 0.25}};
    private static final double REACH_TOLERANCE = 0.5;
    private static final int MATCH_NONE = 0;
    private static final int MATCH_PLACEABLE = 1;
    private static final int MATCH_EXACT = 2;
    private static int cooldown;

    private EasyPlace() {
    }

    public static void tick() {
        if (cooldown > 0) {
            cooldown--;
        }
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
        if (config.easyPlaceEnabled && config.renderEnabled) {
            GhostRaycast.Hit hit = GhostRaycast.trace(minecraft, candidate -> isPlaceable(minecraft, candidate));
            if (hit != null) {
                if (cooldown <= 0) {
                    place(minecraft, hit, config);
                }
                return true;
            }
        }
        return config.placementRestriction && restricted(minecraft);
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
        ClientLevel level = minecraft.level;
        if (level == null) {
            return false;
        }
        BlockState target = hit.state();
        Item item = InventoryHelper.itemFor(target);
        if (!(item instanceof BlockItem) || item instanceof PlaceOnWaterBlockItem) {
            return false;
        }
        if (target.hasProperty(BlockStateProperties.DOUBLE_BLOCK_HALF)
                && target.getValue(BlockStateProperties.DOUBLE_BLOCK_HALF) == DoubleBlockHalf.UPPER) {
            return false;
        }
        if (target.hasProperty(BlockStateProperties.BED_PART)
                && target.getValue(BlockStateProperties.BED_PART) == BedPart.HEAD) {
            return false;
        }
        BlockState world = level.getBlockState(hit.pos());
        if (hit.type() == MismatchType.WRONG_STATE) {
            return isSlabCompletion(world, target);
        }
        return world.canBeReplaced()
                && level.isUnobstructed(target, hit.pos(), CollisionContext.placementContext(minecraft.player));
    }

    public static boolean isInteractable(ClientLevel level, BlockPos pos, BlockState state) {
        Block block = state.getBlock();
        return state.hasBlockEntity() || state.getMenuProvider(level, pos) != null
                || block instanceof DoorBlock || block instanceof TrapDoorBlock || block instanceof FenceGateBlock
                || block instanceof LeverBlock || block instanceof ButtonBlock || block instanceof NoteBlock
                || block instanceof RespawnAnchorBlock || block instanceof CakeBlock || block instanceof CandleCakeBlock
                || block instanceof RepeaterBlock || block instanceof DragonEggBlock;
    }

    private static boolean isSlabCompletion(BlockState world, BlockState target) {
        return target.getBlock() instanceof SlabBlock && world.getBlock() == target.getBlock()
                && target.getValue(BlockStateProperties.SLAB_TYPE) == SlabType.DOUBLE
                && world.getValue(BlockStateProperties.SLAB_TYPE) != SlabType.DOUBLE;
    }

    private static void place(Minecraft minecraft, GhostRaycast.Hit hit, LitematicaPlusConfig config) {
        LocalPlayer player = minecraft.player;
        ClientLevel level = minecraft.level;
        BlockState target = hit.state();
        Item item = InventoryHelper.itemFor(target);
        if (!(item instanceof BlockItem)) {
            return;
        }
        if (!InventoryHelper.ensureInHand(minecraft, item)) {
            Notify.actionBar("litematicaplus.message.item_missing", new ItemStack(item).getHoverName());
            cooldown = 5;
            return;
        }
        ItemStack stack = player.getMainHandItem();
        if (!(stack.getItem() instanceof BlockItem)) {
            cooldown = 2;
            return;
        }
        BlockPos pos = hit.pos();
        BlockHitResult click;
        BlockState world = level.getBlockState(pos);
        if (hit.type() == MismatchType.WRONG_STATE) {
            boolean bottom = world.getValue(BlockStateProperties.SLAB_TYPE) == SlabType.BOTTOM;
            click = new BlockHitResult(new Vec3(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5),
                    bottom ? Direction.UP : Direction.DOWN, pos, false);
        } else {
            click = findClick(player, level, stack, pos, target, config);
        }
        if (click == null) {
            Notify.actionBar("litematicaplus.message.easy_place_orientation");
            cooldown = 3;
            return;
        }
        InteractionResult result = minecraft.gameMode.useItemOn(player, InteractionHand.MAIN_HAND, click);
        if (result instanceof InteractionResult.Success) {
            player.swing(InteractionHand.MAIN_HAND, stack.getInteractAnimation(), false);
        }
        cooldown = Math.max(1, config.easyPlaceDelayTicks);
    }

    private static BlockHitResult findClick(LocalPlayer player, ClientLevel level, ItemStack stack, BlockPos pos,
            BlockState target, LitematicaPlusConfig config) {
        Vec3 eye = player.getEyePosition(1.0F);
        boolean sneaking = player.isShiftKeyDown();
        BlockHitResult fallback = null;
        for (int pass = 0; pass < 2; pass++) {
            boolean wantVisible = pass == 0;
            if (!wantVisible && !config.easyPlaceAirPlacement) {
                break;
            }
            for (Direction side : DIRECTIONS) {
                BlockPos neighbor = pos.relative(side.getOpposite());
                BlockState neighborState = level.getBlockState(neighbor);
                if (!isSupport(level, neighbor, neighborState, sneaking)
                        || !player.isWithinBlockInteractionRange(neighbor, REACH_TOLERANCE)
                        || faceVisible(eye, neighbor, side) != wantVisible) {
                    continue;
                }
                int samples = side.getAxis() == Direction.Axis.Y ? FLAT.length : LATERAL.length * HEIGHTS.length;
                for (int sample = 0; sample < samples; sample++) {
                    BlockHitResult click = new BlockHitResult(faceLocation(neighbor, side, sample), side, neighbor, false);
                    int match = match(player, stack, click, pos, target);
                    if (match == MATCH_EXACT) {
                        return click;
                    }
                    if (match == MATCH_PLACEABLE && fallback == null) {
                        fallback = click;
                    }
                }
            }
        }
        if (config.easyPlaceAirPlacement) {
            for (Direction side : DIRECTIONS) {
                for (double[] offset : FLAT) {
                    for (double height : HEIGHTS) {
                        BlockHitResult click = new BlockHitResult(new Vec3(pos.getX() + offset[0], pos.getY() + height,
                                pos.getZ() + offset[1]), side, pos, false);
                        int match = match(player, stack, click, pos, target);
                        if (match == MATCH_EXACT) {
                            return click;
                        }
                        if (match == MATCH_PLACEABLE && fallback == null) {
                            fallback = click;
                        }
                    }
                }
            }
        }
        return config.easyPlaceStrictOrientation ? null : fallback;
    }

    private static boolean isSupport(ClientLevel level, BlockPos pos, BlockState state, boolean sneaking) {
        if (state.canBeReplaced() || !state.isCollisionShapeFullBlock(level, pos)) {
            return false;
        }
        return sneaking || !isInteractable(level, pos, state);
    }

    private static boolean faceVisible(Vec3 eye, BlockPos block, Direction side) {
        double centerX = block.getX() + 0.5 + side.getStepX() * 0.5;
        double centerY = block.getY() + 0.5 + side.getStepY() * 0.5;
        double centerZ = block.getZ() + 0.5 + side.getStepZ() * 0.5;
        return (eye.x - centerX) * side.getStepX() + (eye.y - centerY) * side.getStepY()
                + (eye.z - centerZ) * side.getStepZ() > 0.0;
    }

    private static Vec3 faceLocation(BlockPos block, Direction side, int sample) {
        if (side.getAxis() == Direction.Axis.Y) {
            return new Vec3(block.getX() + FLAT[sample][0], block.getY() + 0.5 + side.getStepY() * 0.5,
                    block.getZ() + FLAT[sample][1]);
        }
        double lateral = LATERAL[sample / HEIGHTS.length];
        double height = HEIGHTS[sample % HEIGHTS.length];
        if (side.getAxis() == Direction.Axis.X) {
            return new Vec3(block.getX() + 0.5 + side.getStepX() * 0.5, block.getY() + height, block.getZ() + lateral);
        }
        return new Vec3(block.getX() + lateral, block.getY() + height, block.getZ() + 0.5 + side.getStepZ() * 0.5);
    }

    private static int match(LocalPlayer player, ItemStack stack, BlockHitResult click, BlockPos pos, BlockState target) {
        try {
            BlockItem item = (BlockItem) stack.getItem();
            BlockPlaceContext context = item.updatePlacementContext(
                    new BlockPlaceContext(player, InteractionHand.MAIN_HAND, stack, click));
            if (context == null || !context.getClickedPos().equals(pos) || !context.canPlace()) {
                return MATCH_NONE;
            }
            BlockState placed = ((BlockItemInvoker) item).litematicaplus$getPlacementState(context);
            if (placed == null) {
                return MATCH_NONE;
            }
            return orientationMatches(placed, target) ? MATCH_EXACT : MATCH_PLACEABLE;
        } catch (RuntimeException exception) {
            return MATCH_NONE;
        }
    }

    static boolean orientationMatches(BlockState placed, BlockState target) {
        if (placed.getBlock() != target.getBlock()) {
            return false;
        }
        for (Property<?> property : placed.getProperties()) {
            if (!ORIENTATION_PROPERTIES.contains(property.getName())) {
                continue;
            }
            Object wanted = target.getValue(property);
            if (!placed.getValue(property).equals(wanted) && wanted != SlabType.DOUBLE) {
                return false;
            }
        }
        return true;
    }

    private static boolean restricted(Minecraft minecraft) {
        HitResult hit = minecraft.hitResult;
        if (!(hit instanceof BlockHitResult blockHit) || hit.getType() != HitResult.Type.BLOCK) {
            return false;
        }
        ItemStack stack = minecraft.player.getMainHandItem();
        if (!(stack.getItem() instanceof BlockItem)) {
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
        if (engine.runtimeAt(target.getX(), target.getY(), target.getZ()) == null) {
            return false;
        }
        BlockState wanted = engine.schematicStateAt(target.getX(), target.getY(), target.getZ());
        if (wanted != null && InventoryHelper.itemFor(wanted) == stack.getItem()) {
            return false;
        }
        Notify.actionBar("litematicaplus.message.placement_restricted");
        return true;
    }
}
