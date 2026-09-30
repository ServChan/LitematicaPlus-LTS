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
        for (int slot = 0; slot < 9; slot++) {
            if (inventory.getItem(slot).is(item)) {
                inventory.setSelectedSlot(slot);
                return true;
            }
        }
        if (player.hasInfiniteMaterials()) {
            int slot = emptyHotbarSlot(inventory);
            ItemStack stack = new ItemStack(item);
            inventory.setSelectedSlot(slot);
            inventory.setItem(slot, stack);
            minecraft.gameMode.handleCreativeModeItemAdd(stack, 36 + slot);
            return true;
        }
        if (player.containerMenu != player.inventoryMenu) {
            return false;
        }
        for (int slot = 9; slot < 36; slot++) {
            if (inventory.getItem(slot).is(item)) {
                minecraft.gameMode.handleContainerInput(player.inventoryMenu.containerId, slot,
                        inventory.getSelectedSlot(), ContainerInput.SWAP, player);
                return true;
            }
        }
        return false;
    }

    private static int emptyHotbarSlot(Inventory inventory) {
        if (inventory.getSelectedItem().isEmpty()) {
            return inventory.getSelectedSlot();
        }
        for (int slot = 0; slot < 9; slot++) {
            if (inventory.getItem(slot).isEmpty()) {
                return slot;
            }
        }
        return inventory.getSelectedSlot();
    }
}
