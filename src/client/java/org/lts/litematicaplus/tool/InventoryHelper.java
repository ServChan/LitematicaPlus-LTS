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
            if (usable(inventory.getItem(slot), item, config.easyPlaceIgnoreItemData)) {
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

    public static boolean hasExactStack(Minecraft minecraft, ItemStack wanted) {
        LocalPlayer player = minecraft.player;
        return player != null && !wanted.isEmpty()
                && find(player.getInventory(), stack -> ItemStack.isSameItemSameComponents(stack, wanted)) >= 0;
    }

    private static int find(Inventory inventory, Predicate<ItemStack> matches) {
        for (int slot = 0; slot < MAIN_SIZE; slot++) {
            ItemStack stack = inventory.getItem(slot);
            if (!stack.isEmpty() && matches.test(stack)) {
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
            inventory.setSelectedSlot(found);
            ((MultiPlayerGameModeInvoker) minecraft.gameMode).litematicaplus$ensureHasSentCarriedItem();
            return true;
        }
        if (found >= HOTBAR_SIZE && player.containerMenu == player.inventoryMenu) {
            minecraft.gameMode.handleContainerInput(player.inventoryMenu.containerId, found, inventory.getSelectedSlot(),
                    ContainerInput.SWAP, player);
            return true;
        }
        if (!player.hasInfiniteMaterials()) {
            return false;
        }
        int slot = inventory.getSuitableHotbarSlot();
        ItemStack displaced = inventory.getItem(slot);
        if (!displaced.isEmpty()) {
            int free = inventory.getFreeSlot();
            if (free >= 0 && free < MAIN_SIZE) {
                ItemStack moved = displaced.copy();
                inventory.setItem(free, moved);
                minecraft.gameMode.handleCreativeModeItemAdd(moved, menuSlot(free));
            }
        }
        ItemStack stack = creative.copy();
        inventory.setSelectedSlot(slot);
        ((MultiPlayerGameModeInvoker) minecraft.gameMode).litematicaplus$ensureHasSentCarriedItem();
        inventory.setItem(slot, stack);
        minecraft.gameMode.handleCreativeModeItemAdd(stack, menuSlot(slot));
        return true;
    }

    private static int menuSlot(int inventorySlot) {
        return inventorySlot < HOTBAR_SIZE ? HOTBAR_MENU_OFFSET + inventorySlot : inventorySlot;
    }
}
