package org.lts.litematicaplus.tool;

import net.minecraft.world.Container;
import net.minecraft.world.level.block.AbstractChestBlock;
import net.minecraft.world.level.block.AbstractFurnaceBlock;
import net.minecraft.world.level.block.AnvilBlock;
import net.minecraft.world.level.block.BarrelBlock;
import net.minecraft.world.level.block.BeaconBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.BrewingStandBlock;
import net.minecraft.world.level.block.CartographyTableBlock;
import net.minecraft.world.level.block.ConduitBlock;
import net.minecraft.world.level.block.CraftingTableBlock;
import net.minecraft.world.level.block.EnchantingTableBlock;
import net.minecraft.world.level.block.GrindstoneBlock;
import net.minecraft.world.level.block.JukeboxBlock;
import net.minecraft.world.level.block.LecternBlock;
import net.minecraft.world.level.block.LoomBlock;
import net.minecraft.world.level.block.ShulkerBoxBlock;
import net.minecraft.world.level.block.SpawnerBlock;
import net.minecraft.world.level.block.StonecutterBlock;
import net.minecraft.world.level.block.TrialSpawnerBlock;
import net.minecraft.world.level.block.VaultBlock;
import net.minecraft.world.level.block.entity.BlockEntity;

public final class UtilityBlocks {
    private UtilityBlocks() {
    }

    public static boolean workstation(Block block) {
        return block instanceof ShulkerBoxBlock || block instanceof AbstractChestBlock<?> || block instanceof BarrelBlock
                || block instanceof CraftingTableBlock || block instanceof AbstractFurnaceBlock || block instanceof AnvilBlock
                || block instanceof StonecutterBlock || block instanceof GrindstoneBlock || block instanceof LoomBlock
                || block instanceof CartographyTableBlock || block instanceof EnchantingTableBlock
                || block instanceof BrewingStandBlock;
    }

    public static boolean keep(Block block, BlockEntity entity) {
        return workstation(block) || entity instanceof Container || block instanceof BeaconBlock
                || block instanceof LecternBlock || block instanceof JukeboxBlock || block instanceof SpawnerBlock
                || block instanceof TrialSpawnerBlock || block instanceof VaultBlock || block instanceof ConduitBlock;
    }
}
