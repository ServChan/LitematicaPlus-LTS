package org.lts.litematicaplus.materials;

import it.unimi.dsi.fastutil.objects.Reference2LongMap;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemContainerContents;
import org.lts.litematicaplus.LitematicaPlusConfig;
import org.lts.litematicaplus.compat.InventoryLocks;

final class TransferJob {
    enum Kind {
        TAKE,
        DEPOSIT
    }

    enum Outcome {
        RUNNING,
        DONE,
        INVENTORY_FULL,
        CONTAINER_FULL,
        INTERRUPTED
    }

    private static final int MAX_STEPS = 600;
    private static final int INVENTORY_SLOTS = 36;

    private final Kind kind;
    private final Set<Item> only;
    private final boolean everything;
    private final int containerId;
    private final Reference2LongMap<Item> startCounts;
    private int steps;

    TransferJob(Kind kind, Set<Item> only, boolean everything, AbstractContainerMenu menu, Inventory inventory) {
        this.kind = kind;
        this.only = only;
        this.everything = everything;
        this.containerId = menu.containerId;
        this.startCounts = MaterialList.countInventory(inventory);
    }

    Kind kind() {
        return this.kind;
    }

    boolean concerns(Item item) {
        return this.only == null || this.only.contains(item);
    }

    long moved(Inventory inventory) {
        Reference2LongMap<Item> now = MaterialList.countInventory(inventory);
        long total = 0L;
        for (Reference2LongMap.Entry<Item> entry : now.reference2LongEntrySet()) {
            long delta = entry.getLongValue() - this.startCounts.getLong(entry.getKey());
            if (this.kind == Kind.TAKE && delta > 0L) {
                total += delta;
            }
        }
        if (this.kind == Kind.DEPOSIT) {
            for (Reference2LongMap.Entry<Item> entry : this.startCounts.reference2LongEntrySet()) {
                long delta = entry.getLongValue() - now.getLong(entry.getKey());
                if (delta > 0L) {
                    total += delta;
                }
            }
        }
        return total;
    }

    static boolean loose(ItemStack stack) {
        ItemContainerContents contents = stack.get(DataComponents.CONTAINER);
        return contents == null || contents.nonEmptyItemCopyStream().findAny().isEmpty();
    }

    static boolean playerSlot(Slot slot, Inventory inventory) {
        return slot.container == inventory;
    }

    Outcome step(Minecraft minecraft, AbstractContainerMenu menu) {
        LocalPlayer player = minecraft.player;
        if (player == null || minecraft.gameMode == null || menu.containerId != this.containerId
                || !menu.getCarried().isEmpty()) {
            return Outcome.INTERRUPTED;
        }
        if (++this.steps > MAX_STEPS) {
            return Outcome.DONE;
        }
        MaterialCache.invalidate();
        MaterialCache.Snapshot snapshot = MaterialCache.get();
        return this.kind == Kind.TAKE ? take(minecraft, menu, snapshot) : deposit(minecraft, menu, snapshot);
    }

    private Outcome take(Minecraft minecraft, AbstractContainerMenu menu, MaterialCache.Snapshot snapshot) {
        Inventory inventory = minecraft.player.getInventory();
        boolean exact = LitematicaPlusConfig.current().materialExactTake;
        for (MaterialList.Entry entry : snapshot.entries()) {
            if (!concerns(entry.item())) {
                continue;
            }
            long need = this.everything ? Long.MAX_VALUE : entry.shortfall();
            if (need <= 0L) {
                continue;
            }
            List<ClickPlanner.Source> sources = new ArrayList<>();
            for (Slot slot : menu.slots) {
                if (!playerSlot(slot, inventory) && slot.hasItem() && slot.getItem().is(entry.item())
                        && loose(slot.getItem())) {
                    sources.add(new ClickPlanner.Source(slot.index, slot.getItem().getCount()));
                }
            }
            if (sources.isEmpty()) {
                continue;
            }
            List<ClickPlanner.Step> plan = ClickPlanner.plan(sources, need);
            ClickPlanner.Step first = plan.isEmpty() ? null : plan.get(0);
            if (first == null) {
                continue;
            }
            if (first instanceof ClickPlanner.Split split && exact) {
                int target = playerTarget(menu, inventory, menu.slots.get(split.slot()).getItem(), split.amount());
                if (target < 0) {
                    return Outcome.INVENTORY_FULL;
                }
                return click(minecraft, menu, ClickPlanner.split(split.slot(), split.count(), split.amount(), target),
                        split.slot()) ? Outcome.RUNNING : Outcome.INTERRUPTED;
            }
            return quickMove(minecraft, menu, first.slot()) ? Outcome.RUNNING : Outcome.INVENTORY_FULL;
        }
        if (this.everything) {
            return Outcome.DONE;
        }
        for (Slot slot : menu.slots) {
            if (playerSlot(slot, inventory) || !slot.hasItem() || loose(slot.getItem())) {
                continue;
            }
            ItemContainerContents contents = slot.getItem().get(DataComponents.CONTAINER);
            boolean useful = contents.nonEmptyItemCopyStream().anyMatch(inner -> {
                MaterialList.Entry entry = snapshot.entry(inner.getItem());
                return entry != null && entry.shortfall() > 0L && concerns(inner.getItem());
            });
            if (useful) {
                return quickMove(minecraft, menu, slot.index) ? Outcome.RUNNING : Outcome.INVENTORY_FULL;
            }
        }
        return Outcome.DONE;
    }

    private Outcome deposit(Minecraft minecraft, AbstractContainerMenu menu, MaterialCache.Snapshot snapshot) {
        Inventory inventory = minecraft.player.getInventory();
        boolean exact = LitematicaPlusConfig.current().materialExactTake;
        int selected = inventory.getSelectedSlot();
        for (MaterialList.Entry entry : snapshot.entries()) {
            if (!concerns(entry.item())) {
                continue;
            }
            long surplus = entry.available() - entry.missing();
            if (surplus <= 0L) {
                continue;
            }
            List<ClickPlanner.Source> sources = new ArrayList<>();
            long loose = 0L;
            for (Slot slot : menu.slots) {
                int index = slot.getContainerSlot();
                if (!playerSlot(slot, inventory) || index >= INVENTORY_SLOTS || index == selected
                        || InventoryLocks.locked(index) || !slot.hasItem() || !slot.getItem().is(entry.item())
                        || !loose(slot.getItem())) {
                    continue;
                }
                sources.add(new ClickPlanner.Source(slot.index, slot.getItem().getCount()));
                loose += slot.getItem().getCount();
            }
            long amount = Math.min(surplus, loose);
            if (amount <= 0L) {
                continue;
            }
            List<ClickPlanner.Step> plan = ClickPlanner.plan(sources, amount);
            ClickPlanner.Step first = null;
            for (ClickPlanner.Step candidate : plan) {
                if (candidate instanceof ClickPlanner.Whole || exact) {
                    first = candidate;
                    break;
                }
            }
            if (first == null) {
                continue;
            }
            if (first instanceof ClickPlanner.Split split) {
                int target = containerTarget(menu, inventory, menu.slots.get(split.slot()).getItem(), split.amount());
                if (target < 0) {
                    return Outcome.CONTAINER_FULL;
                }
                return click(minecraft, menu, ClickPlanner.split(split.slot(), split.count(), split.amount(), target),
                        split.slot()) ? Outcome.RUNNING : Outcome.INTERRUPTED;
            }
            return quickMove(minecraft, menu, first.slot()) ? Outcome.RUNNING : Outcome.CONTAINER_FULL;
        }
        return Outcome.DONE;
    }

    private static int playerTarget(AbstractContainerMenu menu, Inventory inventory, ItemStack like, int amount) {
        int empty = -1;
        int emptyHotbar = -1;
        for (Slot slot : menu.slots) {
            int index = slot.getContainerSlot();
            if (!playerSlot(slot, inventory) || index >= INVENTORY_SLOTS || InventoryLocks.locked(index)) {
                continue;
            }
            ItemStack stack = slot.getItem();
            if (stack.isEmpty()) {
                if (index >= 9 && empty < 0) {
                    empty = slot.index;
                } else if (index < 9 && emptyHotbar < 0) {
                    emptyHotbar = slot.index;
                }
            } else if (ItemStack.isSameItemSameComponents(stack, like)
                    && slot.getMaxStackSize(stack) - stack.getCount() >= amount) {
                return slot.index;
            }
        }
        return empty >= 0 ? empty : emptyHotbar;
    }

    private static int containerTarget(AbstractContainerMenu menu, Inventory inventory, ItemStack like, int amount) {
        int empty = -1;
        for (Slot slot : menu.slots) {
            if (playerSlot(slot, inventory) || !slot.mayPlace(like)) {
                continue;
            }
            ItemStack stack = slot.getItem();
            if (stack.isEmpty()) {
                if (empty < 0) {
                    empty = slot.index;
                }
            } else if (ItemStack.isSameItemSameComponents(stack, like)
                    && slot.getMaxStackSize(stack) - stack.getCount() >= amount) {
                return slot.index;
            }
        }
        return empty;
    }

    private boolean quickMove(Minecraft minecraft, AbstractContainerMenu menu, int slotIndex) {
        Slot slot = menu.slots.get(slotIndex);
        int before = slot.getItem().getCount();
        ItemStack kind = slot.getItem().copy();
        minecraft.gameMode.handleContainerInput(this.containerId, slotIndex, 0, ContainerInput.QUICK_MOVE, minecraft.player);
        ItemStack after = slot.getItem();
        return after.isEmpty() || !ItemStack.isSameItemSameComponents(after, kind) || after.getCount() < before;
    }

    private boolean click(Minecraft minecraft, AbstractContainerMenu menu, List<ClickPlanner.Click> clicks, int source) {
        if (clicks.size() > ClickPlanner.MAX_SPLIT_CLICKS) {
            return quickMove(minecraft, menu, source);
        }
        for (ClickPlanner.Click click : clicks) {
            minecraft.gameMode.handleContainerInput(this.containerId, click.slot(), click.button(), ContainerInput.PICKUP,
                    minecraft.player);
        }
        if (!menu.getCarried().isEmpty()) {
            minecraft.gameMode.handleContainerInput(this.containerId, source, ClickPlanner.LEFT, ContainerInput.PICKUP,
                    minecraft.player);
            return menu.getCarried().isEmpty();
        }
        return true;
    }
}
