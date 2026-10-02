package org.lts.litematicaplus.tool;

import java.util.ArrayList;
import java.util.Collection;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.DoubleTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntitySpawnRequest;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.decoration.GlowItemFrame;
import net.minecraft.world.entity.decoration.HangingEntity;
import net.minecraft.world.entity.decoration.ItemFrame;
import net.minecraft.world.entity.decoration.painting.Painting;
import net.minecraft.world.entity.decoration.painting.PaintingVariant;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.lts.litematicaplus.LitematicaPlusClient;
import org.lts.litematicaplus.LitematicaPlusConfig;
import org.lts.litematicaplus.engine.GhostEngine;
import org.lts.litematicaplus.engine.PlacementRuntime;
import org.lts.litematicaplus.placement.PlacementTransform;
import org.lts.litematicaplus.world.LayerRange;
import org.lts.litematicaplus.world.PlacementVolume;

public final class EntityAssist {
    public enum Kind {
        ITEM_FRAME,
        PAINTING,
        ARMOR_STAND
    }

    public enum Status {
        UNKNOWN,
        MISSING,
        INCOMPLETE,
        BLOCKED,
        DONE
    }

    public static final class Ghost {
        private final Kind kind;
        private final Entity entity;
        private final AABB box;
        private final BlockPos pos;
        private Status status = Status.UNKNOWN;
        private Entity match;
        private long busyUntil;

        private Ghost(Kind kind, Entity entity, BlockPos pos) {
            this.kind = kind;
            this.entity = entity;
            this.box = entity.getBoundingBox();
            this.pos = pos.immutable();
        }

        public Kind kind() {
            return this.kind;
        }

        public AABB box() {
            return this.box;
        }

        public BlockPos pos() {
            return this.pos;
        }

        public Status status() {
            return this.status;
        }

        public Entity entity() {
            return this.entity;
        }
    }

    private record Cache(PlacementVolume volume, ClientLevel level, List<Ghost> ghosts) {
    }

    private static final double RANGE_SQUARED = 64.0 * 64.0;
    private static final double STAND_TOLERANCE = 0.5;
    private static final int REFRESH_INTERVAL = 5;
    private static final float WALL_OFFSET = 0.46875F;
    private static final List<EquipmentSlot> ARMOR = List.of(EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS,
            EquipmentSlot.FEET);
    private static final Map<PlacementRuntime, Cache> CACHES = new IdentityHashMap<>();
    private static final List<Ghost> VISIBLE = new ArrayList<>();
    private static long ticks;

    private EntityAssist() {
    }

    public static void clear() {
        CACHES.clear();
        VISIBLE.clear();
    }

    public static List<Ghost> visible() {
        return VISIBLE;
    }

    public static List<Ghost> ghosts(PlacementRuntime runtime) {
        Cache cache = CACHES.get(runtime);
        return cache == null ? List.of() : cache.ghosts();
    }

    public static void tick(Minecraft minecraft) {
        LitematicaPlusConfig config = LitematicaPlusConfig.current();
        ClientLevel level = minecraft.level;
        LocalPlayer player = minecraft.player;
        if (level == null || player == null || !config.entityPlacement || !config.renderEnabled) {
            if (!CACHES.isEmpty() || !VISIBLE.isEmpty()) {
                clear();
            }
            return;
        }
        ticks++;
        Collection<PlacementRuntime> runtimes = GhostEngine.get().runtimes();
        CACHES.keySet().removeIf(runtime -> !runtimes.contains(runtime));
        VISIBLE.clear();
        LayerRange layer = GhostEngine.get().layer();
        int index = 0;
        for (PlacementRuntime runtime : runtimes) {
            PlacementVolume volume = runtime.volume();
            if (volume == null || !runtime.placement().enabled() || volume.entities().isEmpty()) {
                CACHES.remove(runtime);
                continue;
            }
            Cache cache = CACHES.get(runtime);
            if (cache == null || cache.volume() != volume || cache.level() != level) {
                cache = new Cache(volume, level, create(level, volume));
                CACHES.put(runtime, cache);
            }
            for (Ghost ghost : cache.ghosts()) {
                index++;
                if (!layer.contains(ghost.pos.getX(), ghost.pos.getY(), ghost.pos.getZ())
                        || player.distanceToSqr(ghost.box.getCenter()) > RANGE_SQUARED || !level.isLoaded(ghost.pos)) {
                    ghost.status = Status.UNKNOWN;
                    continue;
                }
                if (ghost.status == Status.UNKNOWN || (ticks + index) % REFRESH_INTERVAL == 0L
                        || ghost.match != null && !ghost.match.isAlive()) {
                    evaluate(level, ghost);
                }
                if (ghost.status != Status.DONE) {
                    VISIBLE.add(ghost);
                }
            }
        }
    }

    private static List<Ghost> create(ClientLevel level, PlacementVolume volume) {
        List<Ghost> ghosts = new ArrayList<>();
        for (PlacementVolume.EntitySeed seed : volume.entities()) {
            try {
                Ghost ghost = build(level, seed, volume.transform());
                if (ghost != null) {
                    ghosts.add(ghost);
                }
            } catch (RuntimeException exception) {
                LitematicaPlusClient.LOGGER.debug("Skipping a schematic entity that could not be read", exception);
            }
        }
        return ghosts;
    }

    private static Ghost build(ClientLevel level, PlacementVolume.EntitySeed seed, PlacementTransform transform) {
        CompoundTag tag = seed.tag().copy();
        tag.remove("UUID");
        tag.remove("Passengers");
        ListTag position = new ListTag();
        position.add(DoubleTag.valueOf(seed.x()));
        position.add(DoubleTag.valueOf(seed.y()));
        position.add(DoubleTag.valueOf(seed.z()));
        tag.put("Pos", position);
        tag.store("block_pos", BlockPos.CODEC, BlockPos.containing(seed.x(), seed.y(), seed.z()));
        Entity entity = EntityType.create(TagValueInput.create(ProblemReporter.DISCARDING, level.registryAccess(), tag), level,
                new EntitySpawnRequest(EntitySpawnReason.LOAD, true)).orElse(null);
        if (entity instanceof HangingEntity hanging) {
            if (hanging instanceof ItemFrame frame && frame.getDirection().getAxis() == Direction.Axis.Y
                    && transform.mirror() == Mirror.NONE) {
                int turn = quarterTurns(transform.rotation()) * 2 * (frame.getDirection() == Direction.UP ? 1 : -1);
                frame.setRotation(Math.floorMod(frame.getRotation() + turn, 8));
            }
            hanging.mirror(transform.mirror());
            hanging.rotate(transform.rotation());
            Direction direction = hanging.getDirection();
            Vec3 anchor = new Vec3(seed.x(), seed.y(), seed.z()).relative(direction, WALL_OFFSET);
            if (hanging instanceof Painting painting && direction.getAxis().isHorizontal()) {
                PaintingVariant variant = painting.getVariant().value();
                anchor = anchor.relative(direction.getCounterClockWise(), variant.width() % 2 == 0 ? -0.5 : 0.0)
                        .relative(Direction.UP, variant.height() % 2 == 0 ? -0.5 : 0.0);
            }
            BlockPos pos = BlockPos.containing(anchor);
            hanging.setPos(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5);
            return new Ghost(hanging instanceof Painting ? Kind.PAINTING : Kind.ITEM_FRAME, hanging, pos);
        }
        if (entity instanceof ArmorStand stand && !stand.isMarker()) {
            stand.setYRot(stand.mirror(transform.mirror()));
            float yaw = Mth.wrapDegrees(stand.rotate(transform.rotation()));
            stand.snapTo(seed.x(), seed.y(), seed.z(), yaw, stand.getXRot());
            return new Ghost(Kind.ARMOR_STAND, stand, stand.blockPosition());
        }
        return null;
    }

    private static int quarterTurns(Rotation rotation) {
        return switch (rotation) {
            case CLOCKWISE_90 -> 1;
            case CLOCKWISE_180 -> 2;
            case COUNTERCLOCKWISE_90 -> 3;
            default -> 0;
        };
    }

    private static void evaluate(ClientLevel level, Ghost ghost) {
        ghost.match = null;
        switch (ghost.kind) {
            case ITEM_FRAME -> evaluateFrame(level, ghost, (ItemFrame) ghost.entity);
            case PAINTING -> evaluatePainting(level, ghost, (Painting) ghost.entity);
            case ARMOR_STAND -> evaluateStand(level, ghost, (ArmorStand) ghost.entity);
        }
    }

    private static void evaluateFrame(ClientLevel level, Ghost ghost, ItemFrame wanted) {
        List<ItemFrame> found = level.getEntitiesOfClass(ItemFrame.class, new AABB(ghost.pos).inflate(0.1),
                frame -> frame.isAlive() && frame.getPos().equals(ghost.pos) && frame.getDirection() == wanted.getDirection());
        if (found.isEmpty()) {
            ghost.status = Status.MISSING;
            return;
        }
        ItemFrame frame = found.get(0);
        ghost.match = frame;
        if (frame instanceof GlowItemFrame != wanted instanceof GlowItemFrame) {
            ghost.status = Status.BLOCKED;
            return;
        }
        ItemStack item = wanted.getItem();
        if (item.isEmpty()) {
            ghost.status = frame.getItem().isEmpty() ? Status.DONE : Status.BLOCKED;
            return;
        }
        ItemStack present = frame.getItem();
        if (present.isEmpty()) {
            ghost.status = Status.INCOMPLETE;
        } else if (!ItemStack.isSameItem(present, item)) {
            ghost.status = Status.BLOCKED;
        } else {
            ghost.status = rotationClicks(wanted, frame) == 0 ? Status.DONE : Status.INCOMPLETE;
        }
    }

    private static int rotationClicks(ItemFrame wanted, ItemFrame frame) {
        int steps = frame.hasFramedMap() ? 4 : 8;
        return Math.floorMod(wanted.getRotation() - frame.getRotation(), steps);
    }

    private static void evaluatePainting(ClientLevel level, Ghost ghost, Painting wanted) {
        List<Painting> found = level.getEntitiesOfClass(Painting.class, ghost.box.inflate(0.05),
                painting -> painting.isAlive() && painting.getDirection() == wanted.getDirection());
        if (found.isEmpty()) {
            ghost.status = Status.MISSING;
            return;
        }
        ghost.match = found.get(0);
        ghost.status = found.get(0).getVariant().equals(wanted.getVariant()) ? Status.DONE : Status.BLOCKED;
    }

    private static void evaluateStand(ClientLevel level, Ghost ghost, ArmorStand wanted) {
        Vec3 position = wanted.position();
        List<ArmorStand> found = level.getEntitiesOfClass(ArmorStand.class, new AABB(position, position).inflate(STAND_TOLERANCE),
                stand -> stand.isAlive() && !stand.isMarker() && stand.position().distanceTo(position) <= STAND_TOLERANCE);
        if (found.isEmpty()) {
            ghost.status = Status.MISSING;
            return;
        }
        ArmorStand stand = found.get(0);
        ghost.match = stand;
        ghost.status = nextArmor(wanted, stand) == null ? Status.DONE : Status.INCOMPLETE;
    }

    private static EquipmentSlot nextArmor(ArmorStand wanted, ArmorStand stand) {
        for (EquipmentSlot slot : ARMOR) {
            ItemStack item = wanted.getItemBySlot(slot);
            if (!item.isEmpty() && stand.getItemBySlot(slot).isEmpty() && stand.getEquipmentSlotForItem(item) == slot) {
                return slot;
            }
        }
        return null;
    }

    private static Ghost target(Minecraft minecraft) {
        LocalPlayer player = minecraft.player;
        if (player == null || minecraft.level == null || VISIBLE.isEmpty()) {
            return null;
        }
        Vec3 eye = player.getEyePosition(1.0F);
        Vec3 end = eye.add(player.getViewVector(1.0F).scale(player.blockInteractionRange()));
        Ghost best = null;
        double bestDistance = Double.MAX_VALUE;
        for (Ghost ghost : VISIBLE) {
            if (ghost.status == Status.UNKNOWN || ghost.status == Status.DONE) {
                continue;
            }
            AABB box = ghost.box.inflate(0.08);
            double distance;
            if (box.contains(eye)) {
                distance = 0.0;
            } else {
                Optional<Vec3> hit = box.clip(eye, end);
                if (hit.isEmpty()) {
                    continue;
                }
                distance = eye.distanceToSqr(hit.get());
            }
            if (distance < bestDistance) {
                best = ghost;
                bestDistance = distance;
            }
        }
        if (best == null) {
            return null;
        }
        HitResult real = minecraft.hitResult;
        if (real == null || real.getType() == HitResult.Type.MISS) {
            return best;
        }
        boolean closer = eye.distanceToSqr(real.getLocation()) + 0.02 < bestDistance;
        if (real instanceof BlockHitResult block && closer && !supports(best, block.getBlockPos())) {
            return null;
        }
        if (real instanceof EntityHitResult entity && closer && entity.getEntity() != best.match) {
            return null;
        }
        return best;
    }

    private static boolean supports(Ghost ghost, BlockPos pos) {
        if (ghost.entity instanceof HangingEntity hanging) {
            return ghost.pos.relative(hanging.getDirection().getOpposite()).equals(pos);
        }
        return ghost.pos.below().equals(pos);
    }

    private static boolean actionable(Minecraft minecraft, Ghost ghost) {
        if (ghost.status == Status.INCOMPLETE) {
            return ghost.match != null && ghost.match.isAlive();
        }
        if (ghost.status != Status.MISSING) {
            return false;
        }
        ClientLevel level = minecraft.level;
        if (ghost.entity instanceof HangingEntity hanging) {
            Direction direction = hanging.getDirection();
            AABB behind = ghost.box.move(direction.getStepX() * -0.5, direction.getStepY() * -0.5,
                    direction.getStepZ() * -0.5).deflate(1.0E-4);
            for (BlockPos pos : BlockPos.betweenClosed(BlockPos.containing(behind.minX, behind.minY, behind.minZ),
                    BlockPos.containing(behind.maxX, behind.maxY, behind.maxZ))) {
                if (level.getBlockState(pos).canBeReplaced()) {
                    return false;
                }
            }
            return true;
        }
        BlockPos cell = ghost.pos;
        if (!level.getBlockState(cell).canBeReplaced() || !level.getBlockState(cell.above()).canBeReplaced()) {
            return false;
        }
        return !level.getBlockState(cell.below()).canBeReplaced() || LitematicaPlusConfig.current().easyPlaceAirPlacement;
    }

    public static boolean hasTarget(Minecraft minecraft) {
        Ghost ghost = target(minecraft);
        return ghost != null && actionable(minecraft, ghost);
    }

    public static Component describe(Minecraft minecraft) {
        Ghost ghost = target(minecraft);
        if (ghost == null) {
            return null;
        }
        Component name = ghost.entity.getType().getDescription();
        if (ghost.status == Status.BLOCKED) {
            return Component.translatable("litematicaplus.hud.entity_blocked", name);
        }
        if (ghost.status == Status.INCOMPLETE) {
            ItemStack item = pendingItem(ghost);
            return item.isEmpty() ? Component.translatable("litematicaplus.hud.entity_rotate", name)
                    : Component.translatable("litematicaplus.hud.target", item.getHoverName());
        }
        return Component.translatable(actionable(minecraft, ghost) ? "litematicaplus.hud.target"
                : "litematicaplus.hud.entity_waiting", name);
    }

    private static ItemStack pendingItem(Ghost ghost) {
        if (ghost.entity instanceof ItemFrame wanted && ghost.match instanceof ItemFrame frame) {
            return frame.getItem().isEmpty() ? wanted.getItem() : ItemStack.EMPTY;
        }
        if (ghost.entity instanceof ArmorStand wanted && ghost.match instanceof ArmorStand stand) {
            EquipmentSlot slot = nextArmor(wanted, stand);
            return slot == null ? ItemStack.EMPTY : wanted.getItemBySlot(slot);
        }
        return ItemStack.EMPTY;
    }

    public static boolean tryHandle(Minecraft minecraft) {
        Ghost ghost = target(minecraft);
        if (ghost == null || minecraft.gameMode == null || !actionable(minecraft, ghost)) {
            return false;
        }
        if (TickQueue.now() < ghost.busyUntil) {
            return true;
        }
        LitematicaPlusConfig config = LitematicaPlusConfig.current();
        if (ghost.status == Status.MISSING) {
            if (ghost.kind == Kind.ARMOR_STAND) {
                placeStand(minecraft, ghost, config);
            } else {
                hang(minecraft, ghost, config);
            }
            return true;
        }
        if (ghost.match instanceof ItemFrame frame && ghost.entity instanceof ItemFrame wanted) {
            if (frame.getItem().isEmpty()) {
                give(minecraft, ghost, frame, wanted.getItem(), config);
            } else {
                rotate(minecraft, ghost, frame, rotationClicks(wanted, frame));
            }
            return true;
        }
        if (ghost.match instanceof ArmorStand stand && ghost.entity instanceof ArmorStand wanted) {
            EquipmentSlot slot = nextArmor(wanted, stand);
            if (slot != null) {
                give(minecraft, ghost, stand, wanted.getItemBySlot(slot), config);
            }
            return true;
        }
        return true;
    }

    private static void busy(Minecraft minecraft, Ghost ghost, int ticksBusy) {
        ghost.busyUntil = TickQueue.now() + ticksBusy + FollowUps.latencyTicks(minecraft) + 4;
        EasyPlace.markPlacement(minecraft, ghost.pos);
    }

    private static boolean take(Minecraft minecraft, ItemStack stack, LitematicaPlusConfig config) {
        if (InventoryHelper.ensureStackInHand(minecraft, stack)) {
            return true;
        }
        EasyPlace.diagnose(config, "litematicaplus.message.item_missing", stack.getHoverName());
        EasyPlace.hold(5);
        return false;
    }

    private static void hang(Minecraft minecraft, Ghost ghost, LitematicaPlusConfig config) {
        LocalPlayer player = minecraft.player;
        HangingEntity wanted = (HangingEntity) ghost.entity;
        Direction direction = wanted.getDirection();
        ItemStack stack;
        if (wanted instanceof Painting painting) {
            stack = new ItemStack(Items.PAINTING);
            stack.set(DataComponents.PAINTING_VARIANT, painting.getVariant());
            if (!player.hasInfiniteMaterials() && !InventoryHelper.hasExactStack(minecraft, stack)) {
                EasyPlace.diagnose(config, "litematicaplus.message.entity_random_painting");
            }
        } else {
            stack = new ItemStack(wanted instanceof GlowItemFrame ? Items.GLOW_ITEM_FRAME : Items.ITEM_FRAME);
        }
        if (!take(minecraft, stack, config)) {
            return;
        }
        BlockPos support = ghost.pos.relative(direction.getOpposite());
        BlockHitResult click = new BlockHitResult(Vec3.atCenterOf(support).relative(direction, 0.5), direction, support, false);
        InteractionResult result = minecraft.gameMode.useItemOn(player, InteractionHand.MAIN_HAND, click);
        if (result instanceof InteractionResult.Success) {
            player.swing(InteractionHand.MAIN_HAND, player.getMainHandItem().getInteractAnimation(), false);
        }
        busy(minecraft, ghost, 4);
    }

    private static void placeStand(Minecraft minecraft, Ghost ghost, LitematicaPlusConfig config) {
        LocalPlayer player = minecraft.player;
        if (!take(minecraft, new ItemStack(Items.ARMOR_STAND), config)) {
            return;
        }
        BlockPos cell = ghost.pos;
        BlockPos below = cell.below();
        BlockHitResult click = minecraft.level.getBlockState(below).canBeReplaced()
                ? new BlockHitResult(Vec3.atBottomCenterOf(cell), Direction.UP, cell, false)
                : new BlockHitResult(Vec3.atCenterOf(below).add(0.0, 0.5, 0.0), Direction.UP, below, false);
        boolean turn = config.easyPlaceFakeRotation;
        if (turn) {
            LookLock.sendRotation(minecraft, Mth.wrapDegrees(ghost.entity.getYRot() + 180.0F), player.getXRot());
        }
        InteractionResult result = minecraft.gameMode.useItemOn(player, InteractionHand.MAIN_HAND, click);
        if (result instanceof InteractionResult.Success) {
            player.swing(InteractionHand.MAIN_HAND, player.getMainHandItem().getInteractAnimation(), false);
        }
        if (turn && config.easyPlaceRotationRevert) {
            LookLock.sendRealRotation(minecraft);
        }
        busy(minecraft, ghost, 4);
    }

    private static void give(Minecraft minecraft, Ghost ghost, Entity entity, ItemStack item, LitematicaPlusConfig config) {
        if (!take(minecraft, item, config)) {
            return;
        }
        interact(minecraft, entity);
        busy(minecraft, ghost, 4);
    }

    private static void rotate(Minecraft minecraft, Ghost ghost, ItemFrame frame, int clicks) {
        if (clicks <= 0) {
            return;
        }
        interact(minecraft, frame);
        for (int i = 1; i < clicks; i++) {
            TickQueue.after(i * 2, () -> {
                if (TickQueue.worldReady(minecraft) && !BreakAssist.busy(minecraft) && frame.isAlive()
                        && minecraft.player.isWithinEntityInteractionRange(frame, 1.0)) {
                    interact(minecraft, frame);
                }
            });
        }
        busy(minecraft, ghost, clicks * 2 + 2);
        EasyPlace.hold(clicks * 2);
    }

    private static void interact(Minecraft minecraft, Entity entity) {
        LocalPlayer player = minecraft.player;
        if (player == null || minecraft.gameMode == null) {
            return;
        }
        minecraft.gameMode.interact(player, entity, new EntityHitResult(entity, entity.getBoundingBox().getCenter()),
                InteractionHand.MAIN_HAND);
        player.swing(InteractionHand.MAIN_HAND, player.getMainHandItem().getInteractAnimation(), false);
    }
}
