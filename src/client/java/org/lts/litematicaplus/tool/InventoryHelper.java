package org.lts.litematicaplus.tool;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.state.BlockState;

public final class InventoryHelper {
    private static final int HOTBAR_SIZE = 9;
    private static final int MAIN_SIZE = 36;
    private static final int HOTBAR_MENU_OFFSET = 36;

    private InventoryHelper() {
    }

    public static Item itemFor(BlockState state) {
        Item item = state.getBlock().asItem();
        return item == null ? Items.AIR : item;
    }

    public static boolean ensureInHand(Minecraft minecraft, Item item) {
        LocalPlayer player = minecraft.player;
        if (player == null || minecraft.gameMode == null || item == Items.AIR) {
            return false;
        }
        Inventory inventory = player.getInventory();
        if (inventory.getSelectedItem().is(item)) {
            return true;
        }
        for (int slot = 0; slot < HOTBAR_SIZE; slot++) {
            if (inventory.getItem(slot).is(item)) {
                inventory.setSelectedSlot(slot);
                return true;
            }
        }
        boolean ownInventory = player.containerMenu == player.inventoryMenu;
        if (ownInventory) {
            for (int slot = HOTBAR_SIZE; slot < MAIN_SIZE; slot++) {
                if (inventory.getItem(slot).is(item)) {
                    minecraft.gameMode.handleContainerInput(player.inventoryMenu.containerId, slot,
                            inventory.getSelectedSlot(), ContainerInput.SWAP, player);
                    return true;
                }
            }
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
        ItemStack stack = new ItemStack(item);
        inventory.setSelectedSlot(slot);
        inventory.setItem(slot, stack);
        minecraft.gameMode.handleCreativeModeItemAdd(stack, menuSlot(slot));
        return true;
    }

    private static int menuSlot(int inventorySlot) {
        return inventorySlot < HOTBAR_SIZE ? HOTBAR_MENU_OFFSET + inventorySlot : inventorySlot;
    }
}
