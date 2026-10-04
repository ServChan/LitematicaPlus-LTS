package org.lts.litematicaplus.tool;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.level.block.state.BlockState;
import org.lts.litematicaplus.LitematicaPlusConfig;
import org.lts.litematicaplus.engine.GhostEngine;

public final class ToolSwitcher {
    private ToolSwitcher() {
    }

    public static void switchFor(Minecraft minecraft, BlockPos pos, boolean force) {
        LitematicaPlusConfig config = LitematicaPlusConfig.current();
        if (!force && (!config.autoToolSwitch
                || !GhostEngine.get().insideAnyVolume(pos.getX(), pos.getY(), pos.getZ()))) {
            return;
        }
        LocalPlayer player = minecraft.player;
        if (player == null || minecraft.level == null || minecraft.gameMode == null || player.hasInfiniteMaterials()
                || ToolController.get().holdingTool(minecraft)) {
            return;
        }
        BlockState state = minecraft.level.getBlockState(pos);
        if (state.isAir()) {
            return;
        }
        Inventory inventory = player.getInventory();
        int slot = BreakAssist.bestToolSlot(inventory, state);
        if (slot < 0) {
            return;
        }
        if (slot < 9) {
            if (slot != inventory.getSelectedSlot()) {
                InventoryHelper.select(minecraft, inventory, slot);
            }
        } else if (player.containerMenu == player.inventoryMenu) {
            InventoryHelper.swapIntoHotbar(minecraft, player, slot);
        }
    }
}
