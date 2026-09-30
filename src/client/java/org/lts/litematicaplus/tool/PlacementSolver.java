package org.lts.litematicaplus.tool;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.function.Supplier;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Input;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.block.BaseRailBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CandleCakeBlock;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.FlowerPotBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.ChestType;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.level.block.state.properties.SlabType;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import org.lts.litematicaplus.LitematicaPlusConfig;
import org.lts.litematicaplus.mixin.BlockItemInvoker;

public final class PlacementSolver {
    public record Plan(BlockHitResult click, boolean rotate, float yaw, float pitch, boolean sneak, boolean exact) {
    }

    private record Click(BlockHitResult hit, boolean sneak) {
    }

    private record Look(boolean rotate, float yaw, float pitch) {
    }

    private static final Set<String> ORIENTATION_PROPERTIES = Set.of("facing", "axis", "half", "type", "rotation",
            "face", "hinge", "attachment", "orientation", "vertical_direction", "hanging", "shape");
    private static final Set<String> STRAIGHT_SHAPES = Set.of("north_south", "east_west");
    private static final Direction[] DIRECTIONS = Direction.values();
    private static final double[] HEIGHTS = {0.25, 0.75};
    private static final double[] LATERAL = {0.5, 0.25, 0.75};
    private static final double[][] FLAT = {{0.5, 0.5}, {0.25, 0.25}, {0.75, 0.75}, {0.25, 0.75}, {0.75, 0.25}};
    private static final float[] CARDINAL_YAWS = {0.0F, 90.0F, 180.0F, -90.0F};
    private static final double REACH_TOLERANCE = 0.5;
    private static final int MATCH_NONE = 0;
    private static final int MATCH_PLACEABLE = 1;
    private static final int MATCH_EXACT = 2;

    private PlacementSolver() {
    }

    public static BlockState baseState(BlockState target) {
        if (target.getBlock() instanceof FlowerPotBlock) {
            return Blocks.FLOWER_POT.defaultBlockState();
        }
        if (target.getBlock() instanceof CandleCakeBlock) {
            return Blocks.CAKE.defaultBlockState();
        }
        return target;
    }

    public static Plan solve(LocalPlayer player, ClientLevel level, ItemStack stack, BlockPos pos, BlockState target,
            LitematicaPlusConfig config) {
        if (!(stack.getItem() instanceof BlockItem)) {
            return null;
        }
        BlockState wanted = baseState(target);
        List<Click> clicks = clicks(player, level, pos, config);
        if (clicks.isEmpty()) {
            return null;
        }
        List<Look> looks = looks(player, wanted, config);
        boolean trySneak = wanted.getBlock() instanceof ChestBlock;
        float realYaw = player.getYRot();
        float realPitch = player.getXRot();
        Input realInput = player.input.keyPresses;
        Plan fallback = null;
        try {
            for (Look look : looks) {
                player.setYRot(look.yaw());
                player.setXRot(look.pitch());
                for (int sneakPass = 0; sneakPass < (trySneak ? 2 : 1); sneakPass++) {
                    for (Click click : clicks) {
                        boolean sneak = click.sneak() || sneakPass == 1;
                        player.input.keyPresses = withShift(realInput, sneak || realInput.shift());
                        int match = match(player, level, stack, click.hit(), pos, wanted);
                        if (match == MATCH_NONE) {
                            continue;
                        }
                        Plan plan = new Plan(click.hit(), look.rotate(), look.yaw(), look.pitch(),
                                sneak && !realInput.shift(), match == MATCH_EXACT);
                        if (match == MATCH_EXACT) {
                            return plan;
                        }
                        if (fallback == null) {
                            fallback = plan;
                        }
                    }
                }
            }
        } finally {
            player.setYRot(realYaw);
            player.setXRot(realPitch);
            player.input.keyPresses = realInput;
        }
        return config.easyPlaceStrictOrientation ? null : fallback;
    }

    public static <T> T withPlan(LocalPlayer player, Plan plan, Supplier<T> action) {
        float realYaw = player.getYRot();
        float realPitch = player.getXRot();
        Input realInput = player.input.keyPresses;
        try {
            if (plan.rotate()) {
                player.setYRot(plan.yaw());
                player.setXRot(plan.pitch());
            }
            if (plan.sneak()) {
                player.input.keyPresses = withShift(realInput, true);
            }
            return action.get();
        } finally {
            player.setYRot(realYaw);
            player.setXRot(realPitch);
            player.input.keyPresses = realInput;
        }
    }

    private static Input withShift(Input input, boolean shift) {
        return input.shift() == shift ? input : new Input(input.forward(), input.backward(), input.left(), input.right(),
                input.jump(), shift, input.sprint());
    }

    private static List<Look> looks(LocalPlayer player, BlockState wanted, LitematicaPlusConfig config) {
        List<Look> looks = new ArrayList<>();
        float yaw = player.getYRot();
        float pitch = player.getXRot();
        looks.add(new Look(false, yaw, pitch));
        if (!config.easyPlaceFakeRotation) {
            return looks;
        }
        float level = Math.max(-30.0F, Math.min(30.0F, pitch));
        for (float cardinal : CARDINAL_YAWS) {
            looks.add(new Look(true, cardinal, level));
        }
        for (float cardinal : CARDINAL_YAWS) {
            looks.add(new Look(true, cardinal, 89.0F));
            looks.add(new Look(true, cardinal, -89.0F));
        }
        if (wanted.hasProperty(BlockStateProperties.ROTATION_16)) {
            for (int segment = 0; segment < 16; segment++) {
                looks.add(new Look(true, segment * 22.5F - 180.0F, level));
            }
        }
        return looks;
    }

    private static List<Click> clicks(LocalPlayer player, ClientLevel level, BlockPos pos, LitematicaPlusConfig config) {
        List<Click> clicks = new ArrayList<>();
        Vec3 eye = player.getEyePosition(1.0F);
        for (int pass = 0; pass < 2; pass++) {
            boolean wantVisible = pass == 0;
            if (!wantVisible && !config.easyPlaceAirPlacement) {
                break;
            }
            for (Direction side : DIRECTIONS) {
                BlockPos neighbor = pos.relative(side.getOpposite());
                BlockState neighborState = level.getBlockState(neighbor);
                if (neighborState.canBeReplaced() || !neighborState.isCollisionShapeFullBlock(level, neighbor)
                        || !player.isWithinBlockInteractionRange(neighbor, REACH_TOLERANCE)
                        || faceVisible(eye, neighbor, side) != wantVisible) {
                    continue;
                }
                boolean sneak = EasyPlace.isInteractable(level, neighbor, neighborState);
                int samples = side.getAxis() == Direction.Axis.Y ? FLAT.length : LATERAL.length * HEIGHTS.length;
                for (int sample = 0; sample < samples; sample++) {
                    clicks.add(new Click(new BlockHitResult(faceLocation(neighbor, side, sample), side, neighbor, false),
                            sneak));
                }
            }
        }
        if (config.easyPlaceAirPlacement) {
            for (Direction side : DIRECTIONS) {
                for (double[] offset : FLAT) {
                    for (double height : HEIGHTS) {
                        clicks.add(new Click(new BlockHitResult(new Vec3(pos.getX() + offset[0], pos.getY() + height,
                                pos.getZ() + offset[1]), side, pos, false), false));
                    }
                }
            }
        }
        return clicks;
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

    private static int match(LocalPlayer player, ClientLevel level, ItemStack stack, BlockHitResult click, BlockPos pos,
            BlockState wanted) {
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
            boolean ownItem = item == InventoryHelper.itemFor(wanted);
            if (ownItem && placed.getBlock() != wanted.getBlock()) {
                return MATCH_PLACEABLE;
            }
            return orientationMatches(placed, wanted) || chestHalfPending(level, pos, placed, wanted)
                    ? MATCH_EXACT : MATCH_PLACEABLE;
        } catch (RuntimeException exception) {
            return MATCH_NONE;
        }
    }

    private static boolean chestHalfPending(ClientLevel level, BlockPos pos, BlockState placed, BlockState wanted) {
        if (!(wanted.getBlock() instanceof ChestBlock) || placed.getBlock() != wanted.getBlock()
                || wanted.getValue(BlockStateProperties.CHEST_TYPE) == ChestType.SINGLE
                || placed.getValue(BlockStateProperties.CHEST_TYPE) != ChestType.SINGLE
                || placed.getValue(BlockStateProperties.HORIZONTAL_FACING) != wanted.getValue(BlockStateProperties.HORIZONTAL_FACING)) {
            return false;
        }
        BlockPos partner = pos.relative(ChestBlock.getConnectedDirection(wanted));
        return level.getBlockState(partner).getBlock() != wanted.getBlock();
    }

    public static boolean orientationMatches(BlockState placed, BlockState target) {
        boolean sameBlock = placed.getBlock() == target.getBlock();
        boolean rail = sameBlock && placed.getBlock() instanceof BaseRailBlock;
        for (Property<?> property : placed.getProperties()) {
            String name = property.getName();
            if (!ORIENTATION_PROPERTIES.contains(name) || name.equals("shape") && !rail) {
                continue;
            }
            Property<?> other = sameBlock ? property : target.getBlock().getStateDefinition().getProperty(name);
            if (other == null || !target.hasProperty(other)) {
                continue;
            }
            Object wanted = target.getValue(other);
            Object actual = placed.getValue(property);
            if (wanted == SlabType.DOUBLE) {
                continue;
            }
            if (name.equals("shape") && !STRAIGHT_SHAPES.contains(String.valueOf(wanted))) {
                continue;
            }
            if (!String.valueOf(actual).equals(String.valueOf(wanted))) {
                return false;
            }
        }
        return true;
    }
}
