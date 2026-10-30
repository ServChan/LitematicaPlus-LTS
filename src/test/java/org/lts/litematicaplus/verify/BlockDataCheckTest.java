package org.lts.litematicaplus.verify;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import it.unimi.dsi.fastutil.objects.Reference2LongOpenHashMap;
import java.util.Arrays;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.lts.litematicaplus.TestBootstrap;
import org.lts.litematicaplus.world.GhostSection;

class BlockDataCheckTest {
    @BeforeAll
    static void bootstrap() {
        TestBootstrap.ensure();
    }

    private static CompoundTag item(String id, String countKey, int count) {
        CompoundTag tag = new CompoundTag();
        tag.putString("id", id);
        if (countKey.equals("Count")) {
            tag.putByte(countKey, (byte) count);
        } else {
            tag.putInt(countKey, count);
        }
        return tag;
    }

    @Test
    void readsNewAndOldItemCounts() {
        ListTag items = new ListTag();
        items.add(item("minecraft:stone", "count", 3));
        items.add(item("minecraft:stone", "Count", 2));
        items.add(item("minecraft:dirt", "count", 1));
        items.add(item("minecraft:no_such_item", "count", 1));
        CompoundTag tag = new CompoundTag();
        tag.put("Items", items);
        Reference2LongOpenHashMap<Item> totals = new Reference2LongOpenHashMap<>();
        BlockDataCheck.addItems(tag, totals);
        assertEquals(5L, totals.getLong(Items.STONE));
        assertEquals(1L, totals.getLong(Items.DIRT));
        assertEquals(2, totals.size());
    }

    @Test
    void comparesCountsBothWays() {
        Reference2LongOpenHashMap<Item> expected = new Reference2LongOpenHashMap<>();
        Reference2LongOpenHashMap<Item> found = new Reference2LongOpenHashMap<>();
        expected.put(Items.STONE, 5L);
        found.put(Items.STONE, 5L);
        assertTrue(BlockDataCheck.sameCounts(expected, found));
        found.put(Items.DIRT, 1L);
        assertFalse(BlockDataCheck.sameCounts(expected, found));
        found.removeLong(Items.DIRT);
        found.put(Items.STONE, 4L);
        assertFalse(BlockDataCheck.sameCounts(expected, found));
    }

    @Test
    void uniformVerdictsShareOneArray() {
        GhostSection section = new GhostSection();
        Arrays.fill(section.raw(), (short) 1);
        BlockState[] world = new BlockState[GhostSection.VOLUME];
        Arrays.fill(world, Blocks.AIR.defaultBlockState());
        int[] ids = {0, net.minecraft.world.level.block.Block.getId(Blocks.STONE.defaultBlockState())};
        boolean[] solid = {false, true};
        SectionVerdict first = SectionVerdict.compute(section, ids, solid, world, false);
        SectionVerdict second = SectionVerdict.compute(section, ids, solid, world, false);
        assertEquals(GhostSection.VOLUME, first.count(MismatchType.MISSING));
        assertSame(first.categories(), second.categories());
        SectionVerdict unknownFirst = SectionVerdict.compute(section, ids, solid, null, false);
        SectionVerdict unknownSecond = SectionVerdict.compute(section, ids, solid, null, false);
        assertSame(unknownFirst.categories(), unknownSecond.categories());
    }
}
