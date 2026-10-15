package org.lts.litematicaplus.materials;

import it.unimi.dsi.fastutil.longs.Long2ObjectLinkedOpenHashMap;
import it.unimi.dsi.fastutil.objects.Reference2LongMap;
import it.unimi.dsi.fastutil.objects.ReferenceOpenHashSet;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.AbstractMountInventoryMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.ChestType;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import org.lts.litematicaplus.LitematicaPlusConfig;
import org.lts.litematicaplus.engine.GhostEngine;
import org.lts.litematicaplus.engine.PlacementRuntime;
import org.lts.litematicaplus.placement.PlacementManager;
import org.lts.litematicaplus.placement.SchematicPlacement;

public final class ContainerMemory {
    public static final double RANGE = 64.0;
    public static final double FOCUS_RANGE = 160.0;
    private static final int OPEN_WINDOW_TICKS = 60;
    private static final int CAPTURE_TICKS = 10;
    private static final int REFRESH_TICKS = 20;
    private static final int FOCUS_TICKS = 20 * 120;
    private static final int LIMIT = 4096;

    public record Stored(BlockPos pos, BlockPos pair, Reference2LongMap<Item> counts) {
        public long count(Item item) {
            return this.counts.getLong(item);
        }
    }

    public record Box(BlockPos pos, BlockPos pair, boolean focused) {
    }

    private static final Map<ResourceKey<Level>, Long2ObjectLinkedOpenHashMap<Stored>> BY_DIMENSION = new HashMap<>();
    private static Long2ObjectLinkedOpenHashMap<Stored> memory = new Long2ObjectLinkedOpenHashMap<>();
    private static Object owner;
    private static ResourceKey<Level> dimension;
    private static BlockPos usedPos;
    private static int usedTick;
    private static int ticks;
    private static Item focus;
    private static int focusUntil;
    private static Set<Item> needed = Set.of();
    private static AbstractContainerMenu lastMenu;
    private static Target lastTarget;
    private static List<Box> boxes = List.of();

    private ContainerMemory() {
    }

    public static void register() {
        ScreenEvents.AFTER_INIT.register((client, screen, width, height) -> {
            if (!(screen instanceof AbstractContainerScreen<?> container) || !ContainerHelper.isStorage(container)
                    || EnderChestMemory.isEnderChest(screen) || !LitematicaPlusConfig.current().containerMemory) {
                return;
            }
            AbstractContainerMenu menu = container.getMenu();
            if (menu instanceof AbstractMountInventoryMenu) {
                return;
            }
            Target target = menu == lastMenu ? lastTarget : target(client, menu);
            if (target == null) {
                return;
            }
            lastMenu = menu;
            lastTarget = target;
            int[] countdown = {0};
            ScreenEvents.afterTick(screen).register(current -> {
                if (++countdown[0] % CAPTURE_TICKS == 0) {
                    capture(client, target, menu);
                }
            });
            ScreenEvents.remove(screen).register(current -> capture(client, target, menu));
        });
    }

    private record Target(BlockPos pos, BlockPos pair) {
    }

    public static void noteUse(Minecraft minecraft) {
        HitResult hit = minecraft.hitResult;
        if (hit instanceof BlockHitResult block && hit.getType() == HitResult.Type.BLOCK) {
            usedPos = block.getBlockPos().immutable();
            usedTick = ticks;
        }
    }

    private static Target target(Minecraft minecraft, AbstractContainerMenu menu) {
        ClientLevel level = minecraft.level;
        if (level == null || minecraft.player == null || usedPos == null || ticks - usedTick > OPEN_WINDOW_TICKS) {
            return null;
        }
        BlockPos pos = usedPos;
        usedPos = null;
        BlockEntity entity = level.getBlockEntity(pos);
        if (!(entity instanceof Container container)) {
            return null;
        }
        int expected = container.getContainerSize();
        BlockPos pair = null;
        BlockState state = level.getBlockState(pos);
        if (state.getBlock() instanceof ChestBlock && state.hasProperty(ChestBlock.TYPE)
                && state.getValue(ChestBlock.TYPE) != ChestType.SINGLE) {
            pair = pos.relative(ChestBlock.getConnectedDirection(state)).immutable();
            if (level.getBlockEntity(pair) instanceof Container other) {
                expected += other.getContainerSize();
            } else {
                pair = null;
            }
        }
        return storageSlots(menu, minecraft.player.getInventory()) == expected ? new Target(pos, pair) : null;
    }

    private static int storageSlots(AbstractContainerMenu menu, Inventory inventory) {
        int count = 0;
        for (Slot slot : menu.slots) {
            if (TransferJob.storageSlot(slot, menu, inventory)) {
                count++;
            }
        }
        return count;
    }

    private static void capture(Minecraft minecraft, Target target, AbstractContainerMenu menu) {
        if (minecraft.player == null || minecraft.level == null || menu.getStateId() == 0 || !ownerMatches(minecraft)) {
            return;
        }
        Inventory inventory = minecraft.player.getInventory();
        List<ItemStack> stacks = new ArrayList<>();
        for (Slot slot : menu.slots) {
            if (TransferJob.storageSlot(slot, menu, inventory)) {
                stacks.add(slot.getItem());
            }
        }
        Stored stored = new Stored(target.pos(), target.pair(), MaterialList.countStacks(stacks));
        memory.remove(target.pos().asLong());
        if (target.pair() != null) {
            memory.remove(target.pair().asLong());
        }
        memory.putAndMoveToLast(target.pos().asLong(), stored);
        while (memory.size() > LIMIT) {
            memory.removeFirst();
        }
        refresh(minecraft);
    }

    private static boolean ownerMatches(Minecraft minecraft) {
        Object current = minecraft.getConnection();
        if (current != owner) {
            owner = current;
            BY_DIMENSION.clear();
            dimension = null;
            memory = new Long2ObjectLinkedOpenHashMap<>();
            focus = null;
        }
        if (current == null || minecraft.level == null) {
            return false;
        }
        ResourceKey<Level> key = minecraft.level.dimension();
        if (key != dimension) {
            dimension = key;
            memory = BY_DIMENSION.computeIfAbsent(key, ignored -> new Long2ObjectLinkedOpenHashMap<>());
        }
        return true;
    }

    public static void tick(Minecraft minecraft) {
        ticks++;
        LitematicaPlusConfig config = LitematicaPlusConfig.current();
        if (!config.containerMemory || !ownerMatches(minecraft) || minecraft.player == null) {
            boxes = List.of();
            if (!config.containerMemory) {
                BY_DIMENSION.clear();
                memory = new Long2ObjectLinkedOpenHashMap<>();
                dimension = null;
                focus = null;
            }
            return;
        }
        if (focus != null && ticks > focusUntil) {
            focus = null;
        }
        if (ticks % REFRESH_TICKS == 0) {
            refresh(minecraft);
        }
    }

    private static void refresh(Minecraft minecraft) {
        LitematicaPlusConfig config = LitematicaPlusConfig.current();
        needed = config.containerHighlight ? neededItems(minecraft) : Set.of();
        ClientLevel level = minecraft.level;
        if (level == null || minecraft.player == null || needed.isEmpty() && focus == null) {
            boxes = List.of();
            return;
        }
        double rangeSquared = RANGE * RANGE;
        double focusSquared = FOCUS_RANGE * FOCUS_RANGE;
        List<Box> next = new ArrayList<>();
        List<Long> gone = new ArrayList<>();
        for (Stored stored : memory.values()) {
            BlockPos pos = stored.pos();
            double distance = minecraft.player.distanceToSqr(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5);
            if (distance > focusSquared) {
                continue;
            }
            if (level.isLoaded(pos) && !(level.getBlockEntity(pos) instanceof Container)) {
                gone.add(pos.asLong());
                continue;
            }
            boolean focused = focus != null && stored.count(focus) > 0L;
            if (focused || distance <= rangeSquared && contains(stored, needed)) {
                next.add(new Box(pos, stored.pair(), focused));
            }
        }
        for (long key : gone) {
            memory.remove(key);
        }
        boxes = List.copyOf(next);
    }

    private static boolean contains(Stored stored, Set<Item> items) {
        for (Item item : items) {
            if (stored.count(item) > 0L) {
                return true;
            }
        }
        return false;
    }

    private static Set<Item> neededItems(Minecraft minecraft) {
        SchematicPlacement placement = PlacementManager.get().visibleSelection();
        if (placement == null || minecraft.player == null) {
            return Set.of();
        }
        PlacementRuntime runtime = GhostEngine.get().runtime(placement.id());
        Set<Item> items = new ReferenceOpenHashSet<>();
        for (MaterialList.Entry entry : MaterialList.compute(runtime, minecraft.player.getInventory(),
                MaterialList.Sort.MISSING, false)) {
            if (entry.missing() <= 0L) {
                break;
            }
            if (entry.ready() < entry.missing()) {
                items.add(entry.item());
            }
        }
        return items;
    }

    public static List<Box> boxes() {
        return LitematicaPlusConfig.current().containerMemory ? boxes : List.of();
    }

    public static Item focus() {
        return focus;
    }

    public static int toggleFocus(Minecraft minecraft, Item item) {
        if (item == null || item == focus) {
            focus = null;
            refresh(minecraft);
            return -1;
        }
        focus = item;
        focusUntil = ticks + FOCUS_TICKS;
        refresh(minecraft);
        return countWith(item);
    }

    public static int countWith(Item item) {
        int count = 0;
        for (Stored stored : memory.values()) {
            if (stored.count(item) > 0L) {
                count++;
            }
        }
        return count;
    }

    public static boolean any() {
        return !memory.isEmpty();
    }
}
