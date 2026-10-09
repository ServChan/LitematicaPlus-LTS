package org.lts.litematicaplus.materials;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.Block;

import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.block.state.properties.SlabType;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.lts.litematicaplus.TestBootstrap;

class MaterialListTest {
    @BeforeAll
    static void bootstrap() {
        TestBootstrap.ensure();
    }

    @Test
    void packedStockIsShownAfterThePlus() {
        MaterialList.Entry loose = new MaterialList.Entry(Items.STONE, 64L, 20L, 30L);
        assertEquals(0L, loose.packed());
        assertEquals("30", loose.haveText());
        MaterialList.Entry boxed = new MaterialList.Entry(Items.STONE, 64L, 20L, 76L, 12L);
        assertEquals(64L, boxed.packed());
        assertEquals("12+64", boxed.haveText());
        assertEquals(0L, boxed.shortfall());
    }

    @Test
    void ordinaryBlocksMapToTheirItems() {
        assertSame(Items.STONE, MaterialList.itemOf(Blocks.STONE.defaultBlockState()));
        assertSame(Items.TORCH, MaterialList.itemOf(Blocks.WALL_TORCH.defaultBlockState()));
        assertSame(Items.AIR, MaterialList.itemOf(Blocks.AIR.defaultBlockState()));
        assertSame(Items.AIR, MaterialList.itemOf(Blocks.PISTON_HEAD.defaultBlockState()));
    }

    @Test
    void secondHalvesAreNotCountedTwice() {
        BlockState upperDoor = Blocks.OAK_DOOR.defaultBlockState()
                .setValue(BlockStateProperties.DOUBLE_BLOCK_HALF, DoubleBlockHalf.UPPER);
        Block bed = BuiltInRegistries.BLOCK.getValue(Identifier.parse("minecraft:red_bed"));
        BlockState bedHead = bed.defaultBlockState().setValue(BlockStateProperties.BED_PART, BedPart.HEAD);
        assertSame(Items.AIR, MaterialList.itemOf(upperDoor));
        assertSame(Items.AIR, MaterialList.itemOf(bedHead));
        assertSame(Items.OAK_DOOR, MaterialList.itemOf(Blocks.OAK_DOOR.defaultBlockState()));
        assertSame(bed.asItem(), MaterialList.itemOf(bed.defaultBlockState()));
        assertNotSame(Items.AIR, bed.asItem());
    }

    @Test
    void fluidSourcesNeedBucketsAndFlowingFluidNeedsNothing() {
        assertSame(Items.WATER_BUCKET, MaterialList.itemOf(Blocks.WATER.defaultBlockState()));
        assertSame(Items.LAVA_BUCKET, MaterialList.itemOf(Blocks.LAVA.defaultBlockState()));
        assertSame(Items.AIR, MaterialList.itemOf(Blocks.WATER.defaultBlockState().setValue(BlockStateProperties.LEVEL, 3)));
    }

    @Test
    void stackedBlocksCountEveryItem() {
        assertEquals(1, MaterialList.countOf(Blocks.STONE.defaultBlockState()));
        assertEquals(2, MaterialList.countOf(Blocks.OAK_SLAB.defaultBlockState()
                .setValue(BlockStateProperties.SLAB_TYPE, SlabType.DOUBLE)));
        assertEquals(3, MaterialList.countOf(Blocks.CANDLE.defaultBlockState().setValue(BlockStateProperties.CANDLES, 3)));
        assertEquals(4, MaterialList.countOf(Blocks.SEA_PICKLE.defaultBlockState().setValue(BlockStateProperties.PICKLES, 4)));
        assertEquals(5, MaterialList.countOf(Blocks.SNOW.defaultBlockState().setValue(BlockStateProperties.LAYERS, 5)));
    }
}
