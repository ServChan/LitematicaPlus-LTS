package org.lts.litematicaplus.tool;

import java.util.function.Predicate;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.state.BlockState;
import org.lts.litematicaplus.LitematicaPlusConfig;
import org.lts.litematicaplus.compat.InventoryLocks;
import org.lts.litematicaplus.mixin.MultiPlayerGameModeInvoker;

public final class InventoryHelper {
    private static final int HOTBAR_SIZE = 9;
    private static final int MAIN_SIZE = 36;
    private static final int HOTBAR_MENU_OFFSET = 36;

    private InventoryHelper() {
    }

    public static Item itemFor(BlockState state) {
        Item item = PlacementSolver.baseState(state).getBlock().asItem();
        return item == null ? Items.AIR : item;
    }

    private static boolean usable(ItemStack stack, Item item, boolean ignoreData) {
        if (!stack.is(item)) {
            return false;
        }
        return ignoreData || stack.getComponentsPatch().isEmpty();
    }

    public static boolean has(Inventory inventory, Item item, LitematicaPlusConfig config) {
        for (int slot = 0; slot < MAIN_SIZE; slot++) {
            if (reachable(slot) && usable(inventory.getItem(slot), item, config.easyPlaceIgnoreItemData)) {
                return true;
            }
        }
        return false;
    }

    public static boolean ensureInHand(Minecraft minecraft, Item item) {
        LocalPlayer player = minecraft.player;
        if (player == null || item == Items.AIR) {
            return false;
        }
        boolean ignoreData = LitematicaPlusConfig.current().easyPlaceIgnoreItemData || player.hasInfiniteMaterials();
        return ensure(minecraft, stack -> usable(stack, item, ignoreData), new ItemStack(item));
    }

    public static boolean ensureStackInHand(Minecraft minecraft, ItemStack wanted) {
        LocalPlayer player = minecraft.player;
        if (player == null || wanted.isEmpty()) {
            return false;
        }
        ItemStack single = wanted.copyWithCount(1);
        if (find(player.getInventory(), stack -> ItemStack.isSameItemSameComponents(stack, single)) >= 0
                || player.hasInfiniteMaterials() || single.has(DataComponents.MAP_ID)) {
            return ensure(minecraft, stack -> ItemStack.isSameItemSameComponents(stack, single), single);
        }
        return ensure(minecraft, stack -> stack.is(single.getItem()), single);
    }

    public static boolean ensureMatching(Minecraft minecraft, Predicate<ItemStack> matches, ItemStack creative) {
        return minecraft.player != null && ensure(minecraft, matches, creative);
    }

    public static boolean selectEmptyHand(Minecraft minecraft) {
        LocalPlayer player = minecraft.player;
        if (player == null || minecraft.gameMode == null) {
            return false;
        }
        Inventory inventory = player.getInventory();
        if (inventory.getSelectedItem().isEmpty()) {
            return true;
        }
        for (int slot = 0; slot < HOTBAR_SIZE; slot++) {
            if (inventory.getItem(slot).isEmpty()) {
                select(minecraft, inventory, slot);
                return true;
            }
        }
        return false;
    }

    public static boolean hasExactStack(Minecraft minecraft, ItemStack wanted) {
        LocalPlayer player = minecraft.player;
        return player != null && !wanted.isEmpty()
                && find(player.getInventory(), stack -> ItemStack.isSameItemSameComponents(stack, wanted)) >= 0;
    }

    private static boolean reachable(int slot) {
        return slot < HOTBAR_SIZE || !InventoryLocks.locked(slot);
    }

    private static int find(Inventory inventory, Predicate<ItemStack> matches) {
        for (int slot = 0; slot < MAIN_SIZE; slot++) {
            ItemStack stack = inventory.getItem(slot);
            if (!stack.isEmpty() && reachable(slot) && matches.test(stack)) {
                return slot;
            }
        }
        return -1;
    }

    private static boolean ensure(Minecraft minecraft, Predicate<ItemStack> matches, ItemStack creative) {
        LocalPlayer player = minecraft.player;
        if (player == null || minecraft.gameMode == null) {
            return false;
        }
        Inventory inventory = player.getInventory();
        ItemStack selected = inventory.getSelectedItem();
        if (!selected.isEmpty() && matches.test(selected)) {
            return true;
        }
        int found = find(inventory, matches);
        if (found >= 0 && found < HOTBAR_SIZE) {
            select(minecraft, inventory, found);
            return true;
        }
        if (found >= HOTBAR_SIZE && player.containerMenu == player.inventoryMenu) {
            return swapIntoHotbar(minecraft, player, found);
        }
        if (!player.hasInfiniteMaterials()) {
            return false;
        }
        LitematicaPlusConfig config = LitematicaPlusConfig.current();
        int slot = config.lockedHotbarSlots == 0 && !InventoryLocks.available() ? inventory.getSuitableHotbarSlot()
                : freeHotbarSlot(inventory, config);
        if (slot < 0) {
            return false;
        }
        ItemStack displaced = inventory.getItem(slot);
        if (!displaced.isEmpty()) {
                int free = inventory.getFreeSlot();
            if (free >= 0 && free < MAIN_SIZE && !InventoryLocks.locked(free)) {
                ItemStack moved = displaced.copy();
                inventory.setItem(free, moved);
                minecraft.gameMode.handleCreativeModeItemAdd(moved, menuSlot(free));
            }
        }
        ItemStack stack = creative.copy();
        select(minecraft, inventory, slot);
        inventory.setItem(slot, stack);
        minecraft.gameMode.handleCreativeModeItemAdd(stack, menuSlot(slot));
        return true;
    }

    static boolean swapIntoHotbar(Minecraft minecraft, LocalPlayer player, int inventorySlot) {
        Inventory inventory = player.getInventory();
        LitematicaPlusConfig config = LitematicaPlusConfig.current();
        int target = hotbarProtected(config, inventory.getSelectedSlot()) ? freeHotbarSlot(inventory, config)
                : inventory.getSelectedSlot();
        if (target < 0 || !reachable(inventorySlot)) {
            return false;
        }
        select(minecraft, inventory, target);
        minecraft.gameMode.handleContainerInput(player.inventoryMenu.containerId, inventorySlot, target,
                ContainerInput.SWAP, player);
        return true;
    }

    static void select(Minecraft minecraft, Inventory inventory, int slot) {
        inventory.setSelectedSlot(slot);
        ((MultiPlayerGameModeInvoker) minecraft.gameMode).litematicaplus$ensureHasSentCarriedItem();
    }

    static int freeHotbarSlot(Inventory inventory, LitematicaPlusConfig config) {
        int start = inventory.getSelectedSlot();
        int fallback = -1;
        for (int offset = 0; offset < HOTBAR_SIZE; offset++) {
            int slot = (start + offset) % HOTBAR_SIZE;
            if (hotbarProtected(config, slot)) {
                continue;
            }
            if (inventory.getItem(slot).isEmpty()) {
                return slot;
            }
            if (fallback < 0) {
                fallback = slot;
            }
        }
        return fallback;
    }

    private static boolean hotbarProtected(LitematicaPlusConfig config, int slot) {
        return config.hotbarSlotLocked(slot) || InventoryLocks.locked(slot);
    }

    private static int menuSlot(int inventorySlot) {
        return inventorySlot < HOTBAR_SIZE ? HOTBAR_MENU_OFFSET + inventorySlot : inventorySlot;
    }
}
