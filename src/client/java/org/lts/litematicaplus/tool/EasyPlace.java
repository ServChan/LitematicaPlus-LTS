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
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.level.block.state.properties.SlabType;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.lts.litematicaplus.LitematicaPlusConfig;
import org.lts.litematicaplus.engine.GhostEngine;
import org.lts.litematicaplus.util.Notify;
import org.lts.litematicaplus.verify.MismatchType;

public final class EasyPlace {
    private static final Set<String> ORIENTATION_PROPERTIES = Set.of("facing", "axis", "half", "type", "rotation",
            "face", "hinge", "attachment", "orientation", "vertical_direction", "hanging");
    private static final Direction[] DIRECTIONS = Direction.values();
    private static final double[] HEIGHTS = {0.25, 0.75};
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
        if (minecraft.player == null || minecraft.level == null || minecraft.gameMode == null) {
            return false;
        }
        if (config.easyPlaceEnabled && config.renderEnabled) {
            GhostRaycast.Hit hit = GhostRaycast.trace(minecraft, true);
            if (hit != null && isPlaceable(minecraft.level, hit)) {
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
        if (!config.pickBlockEnabled || !config.renderEnabled || minecraft.player == null) {
            return false;
        }
        GhostRaycast.Hit hit = GhostRaycast.trace(minecraft, false);
        if (hit == null) {
            return false;
        }
        Item item = InventoryHelper.itemFor(hit.state());
        if (item == Items.AIR) {
            return false;
        }
        if (!InventoryHelper.ensureInHand(minecraft, item)) {
            Notify.actionBar("litematicaplus.message.item_missing", new ItemStack(item).getHoverName());
        }
        return true;
    }

    private static boolean isPlaceable(ClientLevel level, GhostRaycast.Hit hit) {
        BlockState target = hit.state();
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
        return world.canBeReplaced();
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
        BlockHitResult fallback = null;
        for (Direction side : DIRECTIONS) {
            BlockPos neighbor = pos.relative(side.getOpposite());
            BlockState neighborState = level.getBlockState(neighbor);
            if (neighborState.canBeReplaced() || neighborState.hasBlockEntity()) {
                continue;
            }
            for (double height : HEIGHTS) {
                BlockHitResult click = new BlockHitResult(faceLocation(neighbor, side, height), side, neighbor, false);
                if (fallback == null) {
                    fallback = click;
                }
                if (matches(player, level, stack, click, pos, target)) {
                    return click;
                }
                if (side.getAxis() == Direction.Axis.Y) {
                    break;
                }
            }
        }
        if (config.easyPlaceAirPlacement) {
            for (Direction side : DIRECTIONS) {
                for (double height : HEIGHTS) {
                    BlockHitResult click = new BlockHitResult(
                            new Vec3(pos.getX() + 0.5, pos.getY() + height, pos.getZ() + 0.5), side, pos, false);
                    if (fallback == null) {
                        fallback = click;
                    }
                    if (matches(player, level, stack, click, pos, target)) {
                        return click;
                    }
                }
            }
        }
        return config.easyPlaceStrictOrientation ? null : fallback;
    }

    private static Vec3 faceLocation(BlockPos block, Direction side, double height) {
        double x = block.getX() + 0.5 + side.getStepX() * 0.5;
        double z = block.getZ() + 0.5 + side.getStepZ() * 0.5;
        double y = side.getAxis() == Direction.Axis.Y
                ? block.getY() + 0.5 + side.getStepY() * 0.5
                : block.getY() + height;
        return new Vec3(x, y, z);
    }

    private static boolean matches(LocalPlayer player, ClientLevel level, ItemStack stack, BlockHitResult click,
            BlockPos pos, BlockState target) {
        try {
            BlockPlaceContext context = new BlockPlaceContext(player, InteractionHand.MAIN_HAND, stack, click);
            if (!context.getClickedPos().equals(pos) || !context.canPlace()) {
                return false;
            }
            BlockState placed = target.getBlock().getStateForPlacement(context);
            return placed != null && placed.canSurvive(level, pos) && orientationMatches(placed, target);
        } catch (RuntimeException exception) {
            return false;
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
            return false;
        }
        BlockPos clicked = blockHit.getBlockPos();
        BlockState clickedState = minecraft.level.getBlockState(clicked);
        if (clickedState.hasBlockEntity() && !minecraft.player.isShiftKeyDown()) {
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
