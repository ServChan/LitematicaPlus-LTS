package org.lts.litematicaplus.tool;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.level.block.state.BlockState;
import org.lts.litematicaplus.LitematicaPlusConfig;

public final class ToolSwitcher {
    private ToolSwitcher() {
    }

    public static void switchFor(Minecraft minecraft, BlockPos pos, boolean force) {
        LitematicaPlusConfig config = LitematicaPlusConfig.current();
        if (!force && (!config.autoToolSwitch || !config.easyPlaceEnabled)) {
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
            inventory.setSelectedSlot(slot);
        } else if (player.containerMenu == player.inventoryMenu) {
            minecraft.gameMode.handleContainerInput(player.inventoryMenu.containerId, slot, inventory.getSelectedSlot(),
                    ContainerInput.SWAP, player);
        }
    }
}
