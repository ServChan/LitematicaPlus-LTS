package org.lts.litematicaplus.tool;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import org.lts.litematicaplus.LitematicaPlusConfig;
import org.lts.litematicaplus.engine.GhostEngine;
import org.lts.litematicaplus.util.Notify;

public final class ToolGuard {
    private ToolGuard() {
    }

    static boolean reshapingTool(ItemStack stack) {
        return stack.is(ItemTags.SHOVELS) || stack.is(ItemTags.HOES) || stack.is(ItemTags.AXES);
    }

    public static boolean blocks(Minecraft minecraft) {
        if (!LitematicaPlusConfig.current().toolUseGuard || minecraft.player == null || minecraft.level == null
                || !(minecraft.hitResult instanceof BlockHitResult hit) || hit.getType() != HitResult.Type.BLOCK) {
            return false;
        }
        boolean main = reshapingTool(minecraft.player.getItemInHand(InteractionHand.MAIN_HAND));
        boolean off = minecraft.player.getItemInHand(InteractionHand.MAIN_HAND).isEmpty()
                && reshapingTool(minecraft.player.getItemInHand(InteractionHand.OFF_HAND));
        if (!main && !off) {
            return false;
        }
        BlockPos pos = hit.getBlockPos();
        BlockState target = GhostEngine.get().schematicStateAnywhere(pos.getX(), pos.getY(), pos.getZ());
        BlockState world = minecraft.level.getBlockState(pos);
        if (target == null || target.isAir() || world.isAir() || wanted(world.getBlock(), target.getBlock())) {
            return false;
        }
        Notify.actionBar("litematicaplus.message.tool_guarded", world.getBlock().getName());
        return true;
    }

    static boolean wanted(Block world, Block target) {
        if (world instanceof CampfireBlock) {
            return true;
        }
        if (world == target) {
            return false;
        }
        if (target == Blocks.DIRT_PATH || target == Blocks.FARMLAND) {
            return true;
        }
        if (target == Blocks.DIRT && (world == Blocks.COARSE_DIRT || world == Blocks.ROOTED_DIRT)) {
            return true;
        }
        String targetId = BuiltInRegistries.BLOCK.getKey(target).getPath();
        String worldId = BuiltInRegistries.BLOCK.getKey(world).getPath();
        return targetId.startsWith("stripped_") || targetId.contains("copper") && worldId.contains("copper");
    }
}
